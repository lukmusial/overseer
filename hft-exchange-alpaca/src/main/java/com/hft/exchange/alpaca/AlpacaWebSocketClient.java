package com.hft.exchange.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hft.core.util.ListenerSet;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * WebSocket client for Alpaca real-time market data.
 */
public class AlpacaWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(AlpacaWebSocketClient.class);

    private final AlpacaConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ListenerSet<JsonNode> quoteListeners = new ListenerSet<>();
    private final ListenerSet<JsonNode> tradeListeners = new ListenerSet<>();
    private final ListenerSet<JsonNode> barListeners = new ListenerSet<>();

    // Raw string listeners for fast-path parsing (bypass Jackson tree entirely)
    private final ListenerSet<String> rawQuoteListeners = new ListenerSet<>();
    private final ListenerSet<String> rawTradeListeners = new ListenerSet<>();

    private final ListenerSet.ExceptionSink logError =
            (listener, cause) -> log.error("Error in websocket listener", cause);
    private final Set<String> subscribedQuotes = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedTrades = ConcurrentHashMap.newKeySet();

    private WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private CompletableFuture<Void> connectFuture;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AlpacaWebSocketClient(AlpacaConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public CompletableFuture<Void> connect() {
        if (connected && authenticated) {
            return CompletableFuture.completedFuture(null);
        }

        connectFuture = new CompletableFuture<>();

        Request request = new Request.Builder()
                .url(config.getStreamUrl())
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("WebSocket connected to {}", config.getStreamUrl());
                connected = true;
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
                authenticated = false;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("WebSocket failure", t);
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

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
        scheduler.shutdown();
    }

    public CompletableFuture<Void> subscribeQuotes(Collection<String> symbols) {
        return subscribe("quotes", symbols, subscribedQuotes);
    }

    public CompletableFuture<Void> subscribeTrades(Collection<String> symbols) {
        return subscribe("trades", symbols, subscribedTrades);
    }

    public CompletableFuture<Void> unsubscribeQuotes(Collection<String> symbols) {
        return unsubscribe("quotes", symbols, subscribedQuotes);
    }

    public CompletableFuture<Void> unsubscribeTrades(Collection<String> symbols) {
        return unsubscribe("trades", symbols, subscribedTrades);
    }

    private CompletableFuture<Void> subscribe(String channel, Collection<String> symbols, Set<String> subscribed) {
        if (!authenticated) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not authenticated"));
        }

        ObjectNode message = objectMapper.createObjectNode();
        message.put("action", "subscribe");
        ArrayNode symbolsArray = message.putArray(channel);
        symbols.forEach(symbolsArray::add);

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        webSocket.send(json);
        subscribed.addAll(symbols);
        log.debug("Subscribed to {} {}: {}", channel, symbols.size(), symbols);

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> unsubscribe(String channel, Collection<String> symbols, Set<String> subscribed) {
        if (!authenticated) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not authenticated"));
        }

        ObjectNode message = objectMapper.createObjectNode();
        message.put("action", "unsubscribe");
        ArrayNode symbolsArray = message.putArray(channel);
        symbols.forEach(symbolsArray::add);

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        webSocket.send(json);
        subscribed.removeAll(symbols);
        log.debug("Unsubscribed from {} {}: {}", channel, symbols.size(), symbols);

        return CompletableFuture.completedFuture(null);
    }

    public void addQuoteListener(Consumer<JsonNode> listener) {
        quoteListeners.add(listener);
    }

    public void removeQuoteListener(Consumer<JsonNode> listener) {
        quoteListeners.remove(listener);
    }

    /** Register a raw string listener for quote messages (bypasses Jackson tree). */
    public void addRawQuoteListener(Consumer<String> listener) {
        rawQuoteListeners.add(listener);
    }

    public void removeRawQuoteListener(Consumer<String> listener) {
        rawQuoteListeners.remove(listener);
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

    public void addBarListener(Consumer<JsonNode> listener) {
        barListeners.add(listener);
    }

    public void removeBarListener(Consumer<JsonNode> listener) {
        barListeners.remove(listener);
    }

    public Set<String> getSubscribedQuotes() {
        return Collections.unmodifiableSet(subscribedQuotes);
    }

    public Set<String> getSubscribedTrades() {
        return Collections.unmodifiableSet(subscribedTrades);
    }

    public boolean isConnected() {
        return connected && authenticated;
    }

    private void handleMessage(String text) {
        try {
            // Fast path: if raw listeners are registered, try lightweight string routing
            if (!rawQuoteListeners.isEmpty() || !rawTradeListeners.isEmpty()) {
                if (tryFastRoute(text)) {
                    return;
                }
            }

            // Slow path: full Jackson tree parse
            JsonNode root = objectMapper.readTree(text);

            if (root.isArray()) {
                for (JsonNode node : root) {
                    handleSingleMessage(node);
                }
            } else {
                handleSingleMessage(root);
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", text, e);
        }
    }

    /**
     * Lightweight string-based routing for market data messages.
     * Alpaca sends arrays like [{"T":"q",...}] for quotes and [{"T":"t",...}] for trades.
     * Control messages are objects like {"T":"success",...} — we fall through to Jackson for those.
     */
    private boolean tryFastRoute(String text) {
        // Only handle array messages (market data), not objects (control)
        if (text.isEmpty() || text.charAt(0) != '[') {
            return false;
        }

        // Alpaca arrays typically contain one element per message.
        // Extract each element and route based on "T" field.
        // For simplicity, handle single-element arrays (the common case) directly.
        // Multi-element arrays fall through to Jackson.
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace < 0) {
            return false;
        }

        // Check if there's only one element (no comma between braces at top level)
        String inner = text.substring(firstBrace, lastBrace + 1);

        // Detect message type via "T":"q" or "T":"t"
        int typeIdx = inner.indexOf("\"T\":\"");
        if (typeIdx < 0) {
            return false;
        }
        char typeChar = inner.charAt(typeIdx + 5); // character after "T":"

        if (typeChar == 'q' && !rawQuoteListeners.isEmpty()) {
            notifyRawQuoteListeners(inner);
            return true;
        } else if (typeChar == 't' && !rawTradeListeners.isEmpty()) {
            notifyRawTradeListeners(inner);
            return true;
        }

        // Other message types (b=bar, success, error, subscription) fall through
        return false;
    }

    private void handleSingleMessage(JsonNode node) {
        String msgType = node.path("T").asText();

        switch (msgType) {
            case "success" -> handleSuccess(node);
            case "error" -> handleError(node);
            case "subscription" -> handleSubscription(node);
            case "q" -> notifyQuoteListeners(node);
            case "t" -> notifyTradeListeners(node);
            case "b" -> notifyBarListeners(node);
            default -> log.debug("Unknown message type: {}", msgType);
        }
    }

    private void handleSuccess(JsonNode node) {
        String msg = node.path("msg").asText();
        if ("connected".equals(msg)) {
            log.info("WebSocket connected, authenticating...");
            authenticate();
        } else if ("authenticated".equals(msg)) {
            log.info("WebSocket authenticated");
            authenticated = true;
            if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.complete(null);
            }
            // Resubscribe to previously subscribed symbols
            resubscribe();
        }
    }

    private void handleError(JsonNode node) {
        String msg = node.path("msg").asText();
        int code = node.path("code").asInt();
        log.error("WebSocket error: {} (code={})", msg, code);

        if (connectFuture != null && !connectFuture.isDone()) {
            connectFuture.completeExceptionally(new AlpacaApiException(code, msg));
        }
    }

    private void handleSubscription(JsonNode node) {
        JsonNode quotes = node.path("quotes");
        JsonNode trades = node.path("trades");

        if (quotes.isArray()) {
            List<String> symbols = new ArrayList<>();
            quotes.forEach(s -> symbols.add(s.asText()));
            log.debug("Confirmed quote subscriptions: {}", symbols);
        }

        if (trades.isArray()) {
            List<String> symbols = new ArrayList<>();
            trades.forEach(s -> symbols.add(s.asText()));
            log.debug("Confirmed trade subscriptions: {}", symbols);
        }
    }

    private void authenticate() {
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put("action", "auth");
        auth.put("key", config.apiKey());
        auth.put("secret", config.secretKey());

        try {
            String json = objectMapper.writeValueAsString(auth);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Error sending auth message", e);
        }
    }

    private void resubscribe() {
        if (!subscribedQuotes.isEmpty()) {
            subscribeQuotes(subscribedQuotes);
        }
        if (!subscribedTrades.isEmpty()) {
            subscribeTrades(subscribedTrades);
        }
    }

    private void scheduleReconnect() {
        if (!scheduler.isShutdown()) {
            scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    private void notifyQuoteListeners(JsonNode node) {
        quoteListeners.notify(node, logError);
    }

    private void notifyRawQuoteListeners(String text) {
        rawQuoteListeners.notify(text, logError);
    }

    private void notifyTradeListeners(JsonNode node) {
        tradeListeners.notify(node, logError);
    }

    private void notifyRawTradeListeners(String text) {
        rawTradeListeners.notify(text, logError);
    }

    private void notifyBarListeners(JsonNode node) {
        barListeners.notify(node, logError);
    }
}
