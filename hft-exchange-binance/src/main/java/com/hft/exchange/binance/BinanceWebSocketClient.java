package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.core.util.ListenerSet;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * WebSocket client for Binance real-time market data.
 * Uses combined streams for efficient multi-symbol subscriptions.
 */
public class BinanceWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final BinanceConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ListenerSet<JsonNode> tickerListeners = new ListenerSet<>();
    private final ListenerSet<JsonNode> tradeListeners = new ListenerSet<>();
    private final ListenerSet<JsonNode> depthListeners = new ListenerSet<>();

    // Raw string listeners for fast-path parsing (bypass Jackson tree entirely)
    private final ListenerSet<String> rawTickerListeners = new ListenerSet<>();
    private final ListenerSet<String> rawTradeListeners = new ListenerSet<>();

    private final ListenerSet.ExceptionSink logError =
            (listener, cause) -> log.error("Error in websocket listener", cause);
    private final Set<String> subscribedTickers = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedTrades = ConcurrentHashMap.newKeySet();

    private WebSocket webSocket;
    private volatile boolean connected = false;
    private CompletableFuture<Void> connectFuture;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long requestId = 0;

    public BinanceWebSocketClient(BinanceConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<Void> connect() {
        if (connected) {
            return CompletableFuture.completedFuture(null);
        }

        connectFuture = new CompletableFuture<>();

        // Build combined stream URL
        String url = buildStreamUrl();

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("WebSocket connected to Binance");
                connected = true;
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.complete(null);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.info("WebSocket closing: {} - {}", code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("WebSocket closed: {} - {}", code, reason);
                connected = false;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("WebSocket failure", t);
                connected = false;
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(t);
                }
                scheduleReconnect();
            }
        });

        return connectFuture;
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
        scheduler.shutdown();
    }

    public CompletableFuture<Void> subscribeTickers(Collection<String> symbols) {
        subscribedTickers.addAll(symbols);
        return sendSubscription("SUBSCRIBE", symbols, "bookTicker");
    }

    public CompletableFuture<Void> subscribeTrades(Collection<String> symbols) {
        subscribedTrades.addAll(symbols);
        return sendSubscription("SUBSCRIBE", symbols, "trade");
    }

    public CompletableFuture<Void> unsubscribeTickers(Collection<String> symbols) {
        subscribedTickers.removeAll(symbols);
        return sendSubscription("UNSUBSCRIBE", symbols, "bookTicker");
    }

    public CompletableFuture<Void> unsubscribeTrades(Collection<String> symbols) {
        subscribedTrades.removeAll(symbols);
        return sendSubscription("UNSUBSCRIBE", symbols, "trade");
    }

    private CompletableFuture<Void> sendSubscription(String method, Collection<String> symbols, String streamType) {
        if (!connected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected"));
        }

        List<String> streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@" + streamType)
                .collect(Collectors.toList());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("method", method);
        message.put("params", streams);
        message.put("id", ++requestId);

        try {
            String json = objectMapper.writeValueAsString(message);
            webSocket.send(json);
            log.debug("{} {}: {}", method, streamType, symbols);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public void addTickerListener(Consumer<JsonNode> listener) {
        tickerListeners.add(listener);
    }

    public void removeTickerListener(Consumer<JsonNode> listener) {
        tickerListeners.remove(listener);
    }

    /** Register a raw string listener for bookTicker messages (bypasses Jackson tree). */
    public void addRawTickerListener(Consumer<String> listener) {
        rawTickerListeners.add(listener);
    }

    public void removeRawTickerListener(Consumer<String> listener) {
        rawTickerListeners.remove(listener);
    }

    public void addTradeListener(Consumer<JsonNode> listener) {
        tradeListeners.add(listener);
    }

    public void removeTradeListener(Consumer<JsonNode> listener) {
        tradeListeners.remove(listener);
    }

    /** Register a raw string listener for trade messages (bypasses Jackson tree). */
    public void addRawTradeListener(Consumer<String> listener) {
        rawTradeListeners.add(listener);
    }

    public void removeRawTradeListener(Consumer<String> listener) {
        rawTradeListeners.remove(listener);
    }

    public void addDepthListener(Consumer<JsonNode> listener) {
        depthListeners.add(listener);
    }

    public void removeDepthListener(Consumer<JsonNode> listener) {
        depthListeners.remove(listener);
    }

    public Set<String> getSubscribedTickers() {
        return Collections.unmodifiableSet(subscribedTickers);
    }

    public Set<String> getSubscribedTrades() {
        return Collections.unmodifiableSet(subscribedTrades);
    }

    public boolean isConnected() {
        return connected;
    }

    private String buildStreamUrl() {
        // Start with base stream URL
        return config.getStreamUrl() + "/ws";
    }

    void handleMessage(String text) {
        try {
            // Fast path: if raw listeners are registered, try lightweight string routing
            // to avoid Jackson tree allocation entirely for hot-path messages
            if (!rawTickerListeners.isEmpty() || !rawTradeListeners.isEmpty()) {
                if (tryFastRoute(text)) {
                    return;
                }
            }

            // Slow path: full Jackson tree parse for control messages or when no raw listeners
            JsonNode root = objectMapper.readTree(text);

            // Check if it's a subscription response
            if (root.has("result") || root.has("id")) {
                handleSubscriptionResponse(root);
                return;
            }

            // Handle stream data
            if (root.has("stream")) {
                // Combined stream format
                String stream = root.path("stream").asText();
                JsonNode data = root.path("data");
                routeStreamMessage(stream, data);
            } else if (root.has("e")) {
                // Direct event format (has event type field)
                routeEventMessage(root);
            } else if (root.has("s") && root.has("b") && root.has("a")) {
                // Direct bookTicker format (no "e" or "stream" wrapper)
                notifyTickerListeners(root);
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", text, e);
        }
    }

    /**
     * Lightweight string-based message routing that avoids Jackson tree allocation.
     * Returns true if the message was handled via raw listeners, false to fall through to Jackson.
     */
    private boolean tryFastRoute(String text) {
        // Combined stream format: {"stream":"btcusdt@bookTicker","data":{...}}
        int streamIdx = text.indexOf("@bookTicker");
        if (streamIdx > 0 && !rawTickerListeners.isEmpty()) {
            notifyRawTickerListeners(text);
            return true;
        }

        int tradeIdx = text.indexOf("@trade");
        if (tradeIdx > 0 && !rawTradeListeners.isEmpty()) {
            notifyRawTradeListeners(text);
            return true;
        }

        // Direct bookTicker format: {"s":"BTCUSDT","b":"...","a":"...","B":"...","A":"..."}
        // Detect by checking for "b":" and "a":" (bid/ask) without "stream" wrapper
        if (streamIdx < 0 && text.indexOf("\"b\":\"") > 0 && text.indexOf("\"a\":\"") > 0
                && !rawTickerListeners.isEmpty()) {
            notifyRawTickerListeners(text);
            return true;
        }

        // Control messages (result, id, error) — fall through to Jackson
        if (text.indexOf("\"result\"") > 0 || text.indexOf("\"id\"") > 0) {
            return false;
        }

        return false;
    }

    private void handleSubscriptionResponse(JsonNode node) {
        if (node.has("result") && node.path("result").isNull()) {
            log.debug("Subscription successful, id: {}", node.path("id").asLong());
        } else if (node.has("error")) {
            log.error("Subscription error: {}", node.path("error"));
        }
    }

    private void routeStreamMessage(String stream, JsonNode data) {
        if (stream.endsWith("@bookTicker")) {
            notifyTickerListeners(data);
        } else if (stream.endsWith("@trade")) {
            notifyTradeListeners(data);
        } else if (stream.contains("@depth")) {
            notifyDepthListeners(data);
        }
    }

    private void routeEventMessage(JsonNode node) {
        String eventType = node.path("e").asText();
        switch (eventType) {
            case "bookTicker" -> notifyTickerListeners(node);
            case "trade" -> notifyTradeListeners(node);
            case "depthUpdate" -> notifyDepthListeners(node);
            default -> log.debug("Unknown event type: {}", eventType);
        }
    }

    private void scheduleReconnect() {
        if (!scheduler.isShutdown()) {
            scheduler.schedule(() -> {
                connect().thenRun(() -> {
                    // Resubscribe after reconnect
                    if (!subscribedTickers.isEmpty()) {
                        subscribeTickers(subscribedTickers);
                    }
                    if (!subscribedTrades.isEmpty()) {
                        subscribeTrades(subscribedTrades);
                    }
                });
            }, 5, TimeUnit.SECONDS);
        }
    }

    private void notifyTickerListeners(JsonNode node) {
        tickerListeners.notify(node, logError);
    }

    private void notifyRawTickerListeners(String text) {
        rawTickerListeners.notify(text, logError);
    }

    private void notifyTradeListeners(JsonNode node) {
        tradeListeners.notify(node, logError);
    }

    private void notifyRawTradeListeners(String text) {
        rawTradeListeners.notify(text, logError);
    }

    private void notifyDepthListeners(JsonNode node) {
        depthListeners.notify(node, logError);
    }
}
