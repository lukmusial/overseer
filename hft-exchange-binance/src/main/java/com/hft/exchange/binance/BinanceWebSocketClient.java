package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final List<Consumer<JsonNode>> tickerListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<JsonNode>> tradeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<JsonNode>> depthListeners = new CopyOnWriteArrayList<>();
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

    public void addTradeListener(Consumer<JsonNode> listener) {
        tradeListeners.add(listener);
    }

    public void removeTradeListener(Consumer<JsonNode> listener) {
        tradeListeners.remove(listener);
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

    private void handleMessage(String text) {
        try {
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
                // Direct event format
                routeEventMessage(root);
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", text, e);
        }
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
        for (Consumer<JsonNode> listener : tickerListeners) {
            try {
                listener.accept(node);
            } catch (Exception e) {
                log.error("Error in ticker listener", e);
            }
        }
    }

    private void notifyTradeListeners(JsonNode node) {
        for (Consumer<JsonNode> listener : tradeListeners) {
            try {
                listener.accept(node);
            } catch (Exception e) {
                log.error("Error in trade listener", e);
            }
        }
    }

    private void notifyDepthListeners(JsonNode node) {
        for (Consumer<JsonNode> listener : depthListeners) {
            try {
                listener.accept(node);
            } catch (Exception e) {
                log.error("Error in depth listener", e);
            }
        }
    }
}
