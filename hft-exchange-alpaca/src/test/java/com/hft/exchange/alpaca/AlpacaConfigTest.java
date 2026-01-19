package com.hft.exchange.alpaca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaConfigTest {

    @Test
    void shouldCreatePaperTradingConfig() {
        AlpacaConfig config = AlpacaConfig.paper("test-api-key", "test-secret-key");

        assertEquals("test-api-key", config.apiKey());
        assertEquals("test-secret-key", config.secretKey());
        assertTrue(config.paperTrading());
        assertEquals("iex", config.dataFeed());
    }

    @Test
    void shouldCreateLiveTradingConfig() {
        AlpacaConfig config = AlpacaConfig.live("test-api-key", "test-secret-key", "sip");

        assertEquals("test-api-key", config.apiKey());
        assertEquals("test-secret-key", config.secretKey());
        assertFalse(config.paperTrading());
        assertEquals("sip", config.dataFeed());
    }

    @Test
    void shouldReturnCorrectTradingUrl() {
        AlpacaConfig paper = AlpacaConfig.paper("key", "secret");
        AlpacaConfig live = AlpacaConfig.live("key", "secret", "sip");

        assertEquals(AlpacaConfig.PAPER_TRADING_URL, paper.getTradingUrl());
        assertEquals(AlpacaConfig.LIVE_TRADING_URL, live.getTradingUrl());
    }

    @Test
    void shouldReturnMarketDataUrl() {
        AlpacaConfig config = AlpacaConfig.paper("key", "secret");

        assertEquals(AlpacaConfig.MARKET_DATA_URL, config.getMarketDataUrl());
    }

    @Test
    void shouldReturnStreamUrl() {
        AlpacaConfig config = AlpacaConfig.paper("key", "secret");

        assertEquals(AlpacaConfig.STREAM_URL + "/v2/iex", config.getStreamUrl());
    }

    @Test
    void shouldDefaultToIexDataFeed() {
        AlpacaConfig config = new AlpacaConfig("key", "secret", true, null);

        assertEquals("iex", config.dataFeed());
    }

    @Test
    void shouldThrowOnNullApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlpacaConfig(null, "secret", true, "iex"));
    }

    @Test
    void shouldThrowOnBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlpacaConfig("  ", "secret", true, "iex"));
    }

    @Test
    void shouldThrowOnNullSecretKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlpacaConfig("key", null, true, "iex"));
    }
}
