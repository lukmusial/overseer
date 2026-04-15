package com.hft.exchange.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hft.exchange.alpaca.dto.AlpacaOrder;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * WebSocket client for Alpaca's real-time trading updates stream.
 *
 * <p>Alpaca provides a separate WebSocket endpoint for order status updates (fills,
 * cancellations, rejections, etc.) which delivers updates faster than REST polling.
 * Note that orders are still <em>submitted</em> via REST — this stream is receive-only
 * for order lifecycle events.</p>
 *
 * <h3>Protocol</h3>
 * <ol>
 *   <li>Connect to {@code wss://paper-api.alpaca.markets/stream} (paper) or
 *       {@code wss://api.alpaca.markets/stream} (live)</li>
 *   <li>Authenticate with API key and secret</li>
 *   <li>Subscribe to {@code trade_updates} stream</li>
 *   <li>Receive order update messages as JSON</li>
 * </ol>
 *
 * <h3>Reconnection</h3>
 * <p>On disconnect or failure, the client automatically reconnects after a 5-second delay,
 * re-authenticates, and re-subscribes to trade updates.</p>
 *
 * @see AlpacaWebSocketClient for the market data WebSocket client
 */
public class AlpacaTradingStreamClient {
    private static final Logger log = LoggerFactory.getLogger(AlpacaTradingStreamClient.class);

    private static final String PAPER_STREAM_URL = "wss://paper-api.alpaca.markets/stream";
    private static final String LIVE_STREAM_URL = "wss://api.alpaca.markets/stream";
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final AlpacaConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<Consumer<TradeUpdate>> tradeUpdateListeners = new CopyOnWriteArrayList<>();

    private WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private CompletableFuture<Void> connectFuture;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * A trade update event received from the Alpaca trading stream.
     *
     * @param event       the event type (e.g. "fill", "partial_fill", "canceled", "new",
     *                    "accepted", "rejected", "expired", "replaced", "pending_new",
     *                    "done_for_day")
     * @param order       the full order object from the stream
     * @param timestamp   ISO-8601 timestamp of the event
     * @param positionQty current position quantity after this update (may be null)
     * @param price       fill price for fill events (may be null)
     * @param qty         fill quantity for fill events (may be null)
     */
    public record TradeUpdate(
            String event,
            AlpacaOrder order,
            String timestamp,
            String positionQty,
            String price,
            String qty
    ) {}

    /**
     * Creates a new trading stream client.
     *
     * @param config Alpaca configuration with API credentials and trading mode
     */
    public AlpacaTradingStreamClient(AlpacaConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Connects to the Alpaca trading stream, authenticates, and subscribes to trade updates.
     *
     * <p>If already connected and authenticated, returns immediately. The returned future
     * completes when authentication succeeds, or completes exceptionally on failure.</p>
     *
     * @return a future that completes when the connection is authenticated and subscribed
     */
    public CompletableFuture<Void> connect() {
        if (connected && authenticated) {
            return CompletableFuture.completedFuture(null);
        }

        connectFuture = new CompletableFuture<>();

        String url = getTradingStreamUrl();
        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("Trading stream connected to {}", url);
                connected = true;
                authenticate();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.info("Trading stream closing: {} - {}", code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("Trading stream closed: {} - {}", code, reason);
                connected = false;
                authenticated = false;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Trading stream failure", t);
                connected = false;
                authenticated = false;
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(t);
                }
                scheduleReconnect();
            }
        });

        return connectFuture;
    }

    /**
     * Disconnects from the trading stream and shuts down the reconnect scheduler.
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
        scheduler.shutdown();
    }

    /**
     * Registers a listener for trade update events.
     *
     * @param listener the listener to add
     */
    public void addTradeUpdateListener(Consumer<TradeUpdate> listener) {
        tradeUpdateListeners.add(listener);
    }

    /**
     * Removes a previously registered trade update listener.
     *
     * @param listener the listener to remove
     */
    public void removeTradeUpdateListener(Consumer<TradeUpdate> listener) {
        tradeUpdateListeners.remove(listener);
    }

    /**
     * Returns whether the client is connected and authenticated.
     *
     * @return {@code true} if connected and authenticated
     */
    public boolean isConnected() {
        return connected && authenticated;
    }

    /**
     * Returns the trading stream WebSocket URL based on the trading mode.
     */
    private String getTradingStreamUrl() {
        return config.paperTrading() ? PAPER_STREAM_URL : LIVE_STREAM_URL;
    }

    /**
     * Sends the authentication message to the trading stream.
     */
    private void authenticate() {
        try {
            String authJson = objectMapper.writeValueAsString(new AuthMessage(
                    "authenticate",
                    new AuthData(config.apiKey(), config.secretKey())
            ));
            webSocket.send(authJson);
            log.debug("Sent authentication message to trading stream");
        } catch (Exception e) {
            log.error("Error sending auth message to trading stream", e);
            if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.completeExceptionally(e);
            }
        }
    }

    /**
     * Sends the subscription message for trade updates.
     */
    private void subscribeToTradeUpdates() {
        try {
            String listenJson = objectMapper.writeValueAsString(new ListenMessage(
                    "listen",
                    new ListenData(new String[]{"trade_updates"})
            ));
            webSocket.send(listenJson);
            log.debug("Subscribed to trade_updates stream");
        } catch (Exception e) {
            log.error("Error subscribing to trade updates", e);
        }
    }

    /**
     * Handles an incoming WebSocket message from the trading stream.
     */
    private void handleMessage(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String stream = root.path("stream").asText("");

            switch (stream) {
                case "authorization" -> handleAuthorization(root);
                case "listening" -> handleListening(root);
                case "trade_updates" -> handleTradeUpdate(root);
                default -> log.debug("Unknown trading stream message: {}", text);
            }
        } catch (Exception e) {
            log.error("Error parsing trading stream message: {}", text, e);
        }
    }

    /**
     * Handles the authorization response. On success, subscribes to trade updates.
     */
    private void handleAuthorization(JsonNode root) {
        JsonNode data = root.path("data");
        String status = data.path("status").asText("");

        if ("authorized".equals(status)) {
            log.info("Trading stream authenticated");
            authenticated = true;
            subscribeToTradeUpdates();
            if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.complete(null);
            }
        } else {
            String action = data.path("action").asText("");
            log.error("Trading stream authentication failed: action={}, status={}", action, status);
            if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.completeExceptionally(
                        new AlpacaApiException(401, "Trading stream authentication failed: " + status));
            }
        }
    }

    /**
     * Handles the listening confirmation response.
     */
    private void handleListening(JsonNode root) {
        JsonNode streams = root.path("data").path("streams");
        if (streams.isArray()) {
            log.info("Trading stream now listening to: {}", streams);
        }
    }

    /**
     * Parses a trade update message and notifies all registered listeners.
     */
    private void handleTradeUpdate(JsonNode root) {
        try {
            JsonNode data = root.path("data");
            String event = data.path("event").asText("");
            String timestamp = data.path("timestamp").asText(null);
            String positionQty = data.path("position_qty").asText(null);
            String price = data.path("price").asText(null);
            String qty = data.path("qty").asText(null);

            AlpacaOrder order = objectMapper.treeToValue(data.path("order"), AlpacaOrder.class);

            TradeUpdate update = new TradeUpdate(event, order, timestamp, positionQty, price, qty);

            log.debug("Trade update: event={}, symbol={}, status={}, qty={}",
                    event,
                    order != null ? order.getSymbol() : "unknown",
                    order != null ? order.getStatus() : "unknown",
                    qty);

            notifyListeners(update);
        } catch (Exception e) {
            log.error("Error processing trade update: {}", root, e);
        }
    }

    /**
     * Notifies all registered trade update listeners. Exceptions in individual listeners
     * are caught and logged to prevent one faulty listener from affecting others.
     */
    private void notifyListeners(TradeUpdate update) {
        for (Consumer<TradeUpdate> listener : tradeUpdateListeners) {
            try {
                listener.accept(update);
            } catch (Exception e) {
                log.error("Error in trade update listener", e);
            }
        }
    }

    /**
     * Schedules a reconnection attempt after the configured delay.
     */
    private void scheduleReconnect() {
        if (!scheduler.isShutdown()) {
            log.info("Scheduling trading stream reconnect in {} seconds", RECONNECT_DELAY_SECONDS);
            scheduler.schedule(this::connect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    // --- Internal message DTOs for Jackson serialization ---

    private record AuthMessage(String action, AuthData data) {}

    private record AuthData(String keyId, String secretKey) {}

    private record ListenMessage(String action, ListenData data) {}

    private record ListenData(String[] streams) {}
}
