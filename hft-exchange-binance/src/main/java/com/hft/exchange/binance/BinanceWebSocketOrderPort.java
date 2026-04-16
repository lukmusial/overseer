package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.core.model.Exchange;
import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.core.port.OrderPort;
import com.hft.exchange.binance.dto.BinanceOrder;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * WebSocket-based implementation of {@link OrderPort} for Binance.
 *
 * <p>Uses Binance's WebSocket API (JSON-RPC style) for order placement and cancellation,
 * eliminating HTTP connection overhead that the REST-based {@link BinanceOrderPort} incurs
 * on every request. This is critical for latency-sensitive order submission in HFT workloads.
 *
 * <p>The WebSocket API uses a persistent connection to {@code wss://ws-api.binance.com:443/ws-api/v3}
 * (or the testnet equivalent). Each request carries a unique {@code id} field; responses are
 * matched back to their originating {@link CompletableFuture} via a concurrent pending-request map.
 *
 * <p>Query operations (getOrder, getOpenOrders, etc.) are delegated to the REST
 * {@link BinanceHttpClient} since they are not on the latency-critical path.
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. The pending-request map uses {@link ConcurrentHashMap},
 * request IDs are generated via {@link AtomicLong}, and listener lists use
 * {@link CopyOnWriteArrayList}.
 *
 * @see BinanceOrderPort REST-based alternative
 * @see OrderPort the port interface this class implements
 */
public class BinanceWebSocketOrderPort implements OrderPort {
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketOrderPort.class);

    /** WebSocket API URL for live trading. */
    private static final String LIVE_WS_API_URL = "wss://ws-api.binance.com:443/ws-api/v3";

    /** WebSocket API URL for testnet trading. */
    private static final String TESTNET_WS_API_URL = "wss://testnet.binance.vision/ws-api/v3";

    private final BinanceConfig config;
    private final BinanceHttpClient httpClient;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /** Monotonically increasing request ID generator for JSON-RPC messages. */
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    /**
     * Map of pending request IDs to their corresponding futures.
     * Entries are added when a request is sent and removed when the response arrives
     * (or on timeout/disconnect).
     */
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private final List<Consumer<OrderUpdate>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-ws-order-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean shuttingDown = false;

    /** Pre-initialized HMAC signer to avoid Mac.getInstance() per request. Thread-local for thread safety. */
    private final ThreadLocal<Mac> hmacSigner;

    /**
     * Creates a new WebSocket-based order port.
     *
     * @param config     Binance configuration containing API key, secret key, and testnet flag
     * @param httpClient REST client used for non-latency-critical query operations
     */
    public BinanceWebSocketOrderPort(BinanceConfig config, BinanceHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        // Pre-initialize HMAC signer to avoid Mac.getInstance() + init() per request (~1-5μs saved)
        SecretKeySpec keySpec = new SecretKeySpec(
                config.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.hmacSigner = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(keySpec);
                return mac;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize HMAC signer", e);
            }
        });
        this.okHttpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // no read timeout for WebSocket
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Connection Management ====================

    /**
     * Establishes the WebSocket connection to Binance's WS API endpoint.
     *
     * <p>If already connected, returns a completed future immediately. On connection failure,
     * auto-reconnect is scheduled after a 5-second delay.
     *
     * @return a future that completes when the connection is established
     */
    public CompletableFuture<Void> connect() {
        if (connected) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        String url = getWsApiUrl();

        Request request = new Request.Builder()
                .url(url)
                .build();

        log.info("Connecting to Binance WebSocket API: {}", url);

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("Connected to Binance WebSocket API");
                connected = true;
                connectFuture.complete(null);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                log.info("WebSocket API closing: {} - {}", code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                log.info("WebSocket API closed: {} - {}", code, reason);
                connected = false;
                failAllPending(new IllegalStateException("WebSocket connection closed: " + code + " - " + reason));
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                log.error("WebSocket API failure", t);
                connected = false;
                failAllPending(t);
                if (!connectFuture.isDone()) {
                    connectFuture.completeExceptionally(t);
                }
                scheduleReconnect();
            }
        });

        return connectFuture;
    }

    /**
     * Disconnects the WebSocket and shuts down the reconnect scheduler.
     *
     * <p>All pending requests are failed with an {@link IllegalStateException}.
     * No reconnection will be attempted after this call.
     */
    public void disconnect() {
        shuttingDown = true;
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
        scheduler.shutdown();
        failAllPending(new IllegalStateException("WebSocket order port disconnected"));
    }

    /**
     * Returns whether the WebSocket connection is currently established.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return connected;
    }

    // ==================== OrderPort Implementation ====================

    /**
     * Submits an order via the WebSocket API using the {@code order.place} method.
     *
     * <p>The order parameters are built using {@link BinanceOrderMapper#toRequestParams(Order)},
     * signed with HMAC-SHA256, and sent as a JSON-RPC message. The returned future completes
     * when the exchange responds with either an acknowledgment or an error.
     *
     * @param order the order to submit
     * @return a future containing the submitted order with exchange ID and status
     * @throws IllegalStateException if not connected to the WebSocket API
     */
    @Override
    public CompletableFuture<Order> submitOrder(Order order) {
        if (!connected) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Not connected to Binance WebSocket API"));
        }

        long submitTime = System.nanoTime();
        String requestId = String.valueOf(requestIdGenerator.getAndIncrement());

        // Build params from order with LOT_SIZE/PRICE_FILTER rounding
        BinanceSymbolFilters filters = httpClient.getSymbolFilters(order.getSymbol().getTicker());
        Map<String, String> rawParams = BinanceOrderMapper.toRequestParams(order, filters);
        Map<String, Object> params = new LinkedHashMap<>(rawParams);

        // Sign the params (adds apiKey, timestamp, signature)
        signParams(params);

        // Build JSON-RPC message
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", requestId);
        message.put("method", "order.place");
        message.put("params", params);

        CompletableFuture<Order> future = new CompletableFuture<>();
        pendingRequests.put(requestId, new PendingRequest(future, order, submitTime));

        try {
            String json = objectMapper.writeValueAsString(message);
            boolean sent = webSocket.send(json);
            if (!sent) {
                pendingRequests.remove(requestId);
                future.completeExceptionally(new IllegalStateException("Failed to send order via WebSocket"));
            } else {
                log.debug("Order submitted via WebSocket: id={}, clientOrderId={}", requestId, order.getClientOrderId());
            }
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Cancels an order via the WebSocket API using the {@code order.cancel} method.
     *
     * <p>Uses the exchange order ID if available, otherwise falls back to the client order ID.
     *
     * @param order the order to cancel
     * @return a future containing the cancelled order
     * @throws IllegalStateException if not connected to the WebSocket API
     */
    @Override
    public CompletableFuture<Order> cancelOrder(Order order) {
        if (!connected) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Not connected to Binance WebSocket API"));
        }

        String requestId = String.valueOf(requestIdGenerator.getAndIncrement());
        OrderStatus previousStatus = order.getStatus();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", order.getSymbol().getTicker());

        if (order.getExchangeOrderId() != null) {
            params.put("orderId", Long.parseLong(order.getExchangeOrderId()));
        } else {
            params.put("origClientOrderId", String.valueOf(order.getClientOrderId()));
        }

        signParams(params);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", requestId);
        message.put("method", "order.cancel");
        message.put("params", params);

        CompletableFuture<Order> future = new CompletableFuture<>();
        pendingRequests.put(requestId, new PendingRequest(future, order, System.nanoTime()));

        try {
            String json = objectMapper.writeValueAsString(message);
            boolean sent = webSocket.send(json);
            if (!sent) {
                pendingRequests.remove(requestId);
                future.completeExceptionally(new IllegalStateException("Failed to send cancel via WebSocket"));
            } else {
                log.debug("Cancel submitted via WebSocket: id={}, exchangeOrderId={}", requestId, order.getExchangeOrderId());
            }
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Modifies an order by cancelling and resubmitting.
     *
     * <p>Binance does not support atomic order modification, so this is implemented as
     * a cancel followed by a new submission, both via the WebSocket API.
     *
     * @param order the order with modified parameters
     * @return a future containing the modified order
     */
    @Override
    public CompletableFuture<Order> modifyOrder(Order order) {
        return cancelOrder(order)
                .thenCompose(cancelled -> {
                    order.setExchangeOrderId(null);
                    return submitOrder(order);
                });
    }

    /**
     * Queries an order by client order ID.
     *
     * <p>Delegates to the REST API since query operations are not latency-critical.
     * Note: Binance requires a symbol to query orders, so this method has the same
     * limitation as the REST-based port.
     *
     * @param clientOrderId the client order ID
     * @return a future containing the order if found
     */
    @Override
    public CompletableFuture<Optional<Order>> getOrder(long clientOrderId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Binance requires symbol to query order. Use getOrderByExchangeId with symbol."));
    }

    /**
     * Queries an order by exchange order ID.
     *
     * <p>Delegates to the REST API since query operations are not latency-critical.
     * Note: Binance requires a symbol to query orders.
     *
     * @param exchangeOrderId the exchange order ID
     * @return a future containing the order if found
     */
    @Override
    public CompletableFuture<Optional<Order>> getOrderByExchangeId(String exchangeOrderId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Binance requires symbol to query order"));
    }

    /**
     * Gets an order by exchange ID with the required symbol.
     *
     * <p>Delegates to the REST {@link BinanceHttpClient}.
     *
     * @param symbol          the symbol the order belongs to
     * @param exchangeOrderId the exchange order ID
     * @return a future containing the order if found
     */
    public CompletableFuture<Optional<Order>> getOrder(Symbol symbol, String exchangeOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.getTicker());
        params.put("orderId", exchangeOrderId);

        return httpClient.signedGet("/api/v3/order", params, BinanceOrder.class)
                .thenApply(binanceOrder -> Optional.of(BinanceOrderMapper.toOrder(binanceOrder, symbol)))
                .exceptionally(e -> {
                    if (e.getCause() instanceof BinanceApiException apiEx && apiEx.isOrderNotFound()) {
                        return Optional.empty();
                    }
                    throw new RuntimeException(e);
                });
    }

    /**
     * Lists all open orders across all symbols.
     *
     * <p>Delegates to the REST {@link BinanceHttpClient}.
     *
     * @return a future containing the list of open orders
     */
    @Override
    public CompletableFuture<List<Order>> getOpenOrders() {
        return getOpenOrders(null);
    }

    /**
     * Lists all open orders for a specific symbol.
     *
     * <p>Delegates to the REST {@link BinanceHttpClient}.
     *
     * @param symbol the symbol to filter by, or {@code null} for all symbols
     * @return a future containing the list of open orders
     */
    @Override
    public CompletableFuture<List<Order>> getOpenOrders(Symbol symbol) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null) {
            params.put("symbol", symbol.getTicker());
        }

        return httpClient.signedGet("/api/v3/openOrders", params, BinanceOrder[].class)
                .thenApply(binanceOrders -> Arrays.stream(binanceOrders)
                        .map(bo -> {
                            Symbol orderSymbol = symbol != null ? symbol :
                                    new Symbol(bo.getSymbol(), Exchange.BINANCE);
                            return BinanceOrderMapper.toOrder(bo, orderSymbol);
                        })
                        .toList());
    }

    /**
     * Cancels all open orders across all symbols.
     *
     * <p>Retrieves open orders via REST, then groups by symbol and cancels each group.
     *
     * @return a future that completes when all orders are cancelled
     */
    @Override
    public CompletableFuture<Void> cancelAllOrders() {
        return getOpenOrders()
                .thenCompose(orders -> {
                    Map<String, List<Order>> bySymbol = new HashMap<>();
                    for (Order order : orders) {
                        bySymbol.computeIfAbsent(order.getSymbol().getTicker(), k -> new ArrayList<>())
                                .add(order);
                    }

                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (String ticker : bySymbol.keySet()) {
                        futures.add(cancelAllOrdersForSymbol(ticker));
                    }

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                });
    }

    /**
     * Cancels all open orders for a specific symbol.
     *
     * @param symbol the symbol to cancel orders for
     * @return a future that completes when all orders are cancelled
     */
    @Override
    public CompletableFuture<Void> cancelAllOrders(Symbol symbol) {
        return cancelAllOrdersForSymbol(symbol.getTicker());
    }

    @Override
    public void addOrderListener(Consumer<OrderUpdate> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeOrderListener(Consumer<OrderUpdate> listener) {
        listeners.remove(listener);
    }

    // ==================== Internal: Response Handling ====================

    /**
     * Handles an incoming WebSocket message by parsing the JSON-RPC response,
     * matching it to a pending request by ID, and completing the associated future.
     *
     * @param text the raw JSON message text
     */
    void handleMessage(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);

            // Every response from the WS API has an "id" field matching the request
            if (!root.has("id")) {
                log.debug("Received message without id: {}", text);
                return;
            }

            String responseId = root.path("id").asText();
            PendingRequest pending = pendingRequests.remove(responseId);

            if (pending == null) {
                log.warn("Received response for unknown request id: {}", responseId);
                return;
            }

            int status = root.path("status").asInt(0);

            if (status == 200) {
                handleSuccessResponse(root.path("result"), pending);
            } else if (root.has("error")) {
                handleErrorResponse(root.path("error"), pending);
            } else {
                pending.future.completeExceptionally(
                        new BinanceApiException(status, "Unexpected response status: " + status));
            }
        } catch (Exception e) {
            log.error("Error parsing WebSocket API response: {}", text, e);
        }
    }

    /**
     * Processes a successful (status 200) response from the exchange.
     * Deserializes the result into a {@link BinanceOrder} and completes the pending future.
     */
    private void handleSuccessResponse(JsonNode resultNode, PendingRequest pending) {
        try {
            BinanceOrder binanceOrder = objectMapper.treeToValue(resultNode, BinanceOrder.class);
            Order result = BinanceOrderMapper.toOrder(binanceOrder, pending.originalOrder.getSymbol());
            result.setClientOrderId(pending.originalOrder.getClientOrderId());
            result.setSubmittedAt(pending.submitTime);
            result.setAcceptedAt(System.nanoTime());
            result.setPriceScale(pending.originalOrder.getPriceScale());
            result.strategyId(pending.originalOrder.getStrategyId());

            OrderStatus previousStatus = pending.originalOrder.getStatus();
            log.debug("Order response received: clientOrderId={}, exchangeOrderId={}, status={}",
                    result.getClientOrderId(), result.getExchangeOrderId(), result.getStatus());

            notifyListeners(result, previousStatus, result.getStatus());

            @SuppressWarnings("unchecked")
            CompletableFuture<Order> orderFuture = (CompletableFuture<Order>) (CompletableFuture<?>) pending.future;
            orderFuture.complete(result);
        } catch (Exception e) {
            log.error("Error deserializing order response", e);
            pending.future.completeExceptionally(e);
        }
    }

    /**
     * Processes an error response from the exchange. Creates a {@link BinanceApiException}
     * and completes the pending future exceptionally. If the error is for an order submission,
     * the original order is marked as REJECTED and listeners are notified.
     */
    private void handleErrorResponse(JsonNode errorNode, PendingRequest pending) {
        int code = errorNode.path("code").asInt(0);
        String msg = errorNode.path("msg").asText("Unknown error");

        log.error("Order error from exchange: code={}, msg={}, clientOrderId={}",
                code, msg, pending.originalOrder.getClientOrderId());

        // Mark order as rejected and complete normally — the OrderHandler will
        // process the REJECTED status via updateOrder, preserving the clean reject reason
        Order order = pending.originalOrder;
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason("Binance error " + code + ": " + msg);
        order.setAcceptedAt(System.nanoTime());
        notifyListeners(order, OrderStatus.PENDING, OrderStatus.REJECTED);

        @SuppressWarnings("unchecked")
        CompletableFuture<Order> orderFuture = (CompletableFuture<Order>) (CompletableFuture<?>) pending.future;
        orderFuture.complete(order);
    }

    // ==================== Internal: Signing ====================

    /**
     * Computes an HMAC-SHA256 signature of the given data using the configured secret key.
     *
     * @param data the data to sign
     * @return the hex-encoded signature
     */
    private String sign(String data) {
        try {
            Mac mac = hmacSigner.get();
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error signing request", e);
        }
    }

    /**
     * Adds authentication parameters ({@code apiKey}, {@code timestamp}, {@code signature})
     * to the given params map. The signature is computed over all parameters sorted alphabetically.
     *
     * @param params the mutable parameter map to sign (modified in place)
     * @return the same map with authentication parameters added
     */
    private Map<String, Object> signParams(Map<String, Object> params) {
        params.put("apiKey", config.apiKey());
        params.put("timestamp", System.currentTimeMillis());

        // Build alphabetically sorted query string for signing
        String queryString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        params.put("signature", sign(queryString));
        return params;
    }

    // ==================== Internal: Helpers ====================

    /**
     * Returns the WebSocket API URL based on whether testnet mode is enabled.
     *
     * @return the WS API URL
     */
    private String getWsApiUrl() {
        return config.testnet()
                ? TESTNET_WS_API_URL
                : LIVE_WS_API_URL;
    }

    /**
     * Cancels all open orders for the given symbol ticker via the REST API.
     */
    private CompletableFuture<Void> cancelAllOrdersForSymbol(String ticker) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", ticker);

        return httpClient.signedDelete("/api/v3/openOrders", params)
                .thenRun(() -> log.info("All orders cancelled for {}", ticker));
    }

    /**
     * Schedules a reconnection attempt after a 5-second delay.
     * Does nothing if the port is shutting down or the scheduler has been shut down.
     */
    private void scheduleReconnect() {
        if (shuttingDown || scheduler.isShutdown()) {
            return;
        }
        log.info("Scheduling WebSocket API reconnect in 5 seconds");
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    /**
     * Fails all pending requests with the given exception. Called on disconnect or failure.
     */
    private void failAllPending(Throwable cause) {
        pendingRequests.forEach((id, pending) -> {
            if (!pending.future.isDone()) {
                pending.future.completeExceptionally(cause);
            }
        });
        pendingRequests.clear();
    }

    /**
     * Notifies all registered order listeners of a status change.
     */
    private void notifyListeners(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        OrderUpdate update = new OrderUpdate(order, previousStatus, newStatus, System.nanoTime());
        for (Consumer<OrderUpdate> listener : listeners) {
            try {
                listener.accept(update);
            } catch (Exception e) {
                log.error("Error in order listener", e);
            }
        }
    }

    /**
     * Holds the state of a pending WebSocket API request: the future to complete,
     * the original order for context, and the submit timestamp for latency tracking.
     */
    private record PendingRequest(
            CompletableFuture<?> future,
            Order originalOrder,
            long submitTime
    ) {}
}
