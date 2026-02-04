package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BinanceWebSocketClientTest {

    private BinanceWebSocketClient client;

    @BeforeEach
    void setUp() {
        BinanceConfig config = new BinanceConfig("dummy", "dummy", true);
        client = new BinanceWebSocketClient(config);
    }

    @Test
    void handleMessage_directBookTicker_notifiesTickerListener() {
        // Binance bookTicker on /ws endpoint sends messages without "e" or "stream" fields
        String message = "{\"u\":12345,\"s\":\"BTCUSDT\",\"b\":\"74100.15000000\",\"B\":\"1.50000000\",\"a\":\"74101.20000000\",\"A\":\"2.00000000\"}";

        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.addTickerListener(received::set);

        client.handleMessage(message);

        assertNotNull(received.get(), "Ticker listener should have been called for direct bookTicker");
        assertEquals("BTCUSDT", received.get().path("s").asText());
        assertEquals("74100.15000000", received.get().path("b").asText());
        assertEquals("74101.20000000", received.get().path("a").asText());
    }

    @Test
    void handleMessage_combinedStreamBookTicker_notifiesTickerListener() {
        // Combined stream format with "stream" wrapper
        String message = "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"s\":\"BTCUSDT\",\"b\":\"74100.15000000\",\"B\":\"1.50000000\",\"a\":\"74101.20000000\",\"A\":\"2.00000000\"}}";

        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.addTickerListener(received::set);

        client.handleMessage(message);

        assertNotNull(received.get(), "Ticker listener should have been called for combined stream");
        assertEquals("BTCUSDT", received.get().path("s").asText());
    }

    @Test
    void handleMessage_eventTypeBookTicker_notifiesTickerListener() {
        // Event format with "e" field
        String message = "{\"e\":\"bookTicker\",\"s\":\"ETHUSDT\",\"b\":\"3200.50000000\",\"B\":\"5.00000000\",\"a\":\"3201.00000000\",\"A\":\"3.00000000\"}";

        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.addTickerListener(received::set);

        client.handleMessage(message);

        assertNotNull(received.get(), "Ticker listener should have been called for event type bookTicker");
        assertEquals("ETHUSDT", received.get().path("s").asText());
    }

    @Test
    void handleMessage_subscriptionResponse_doesNotNotifyListener() {
        String message = "{\"result\":null,\"id\":1}";

        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.addTickerListener(received::set);

        client.handleMessage(message);

        assertNull(received.get(), "Ticker listener should not be called for subscription responses");
    }
}
