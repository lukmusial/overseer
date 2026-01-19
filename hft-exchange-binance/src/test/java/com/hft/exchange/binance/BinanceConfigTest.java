package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceConfigTest {

    @Test
    void shouldCreateTestnetConfig() {
        BinanceConfig config = BinanceConfig.testnet("test-api-key", "test-secret-key");

        assertEquals("test-api-key", config.apiKey());
        assertEquals("test-secret-key", config.secretKey());
        assertTrue(config.testnet());
    }

    @Test
    void shouldCreateLiveConfig() {
        BinanceConfig config = BinanceConfig.live("test-api-key", "test-secret-key");

        assertEquals("test-api-key", config.apiKey());
        assertEquals("test-secret-key", config.secretKey());
        assertFalse(config.testnet());
    }

    @Test
    void shouldReturnCorrectBaseUrl() {
        BinanceConfig testnet = BinanceConfig.testnet("key", "secret");
        BinanceConfig live = BinanceConfig.live("key", "secret");

        assertEquals(BinanceConfig.TESTNET_BASE_URL, testnet.getBaseUrl());
        assertEquals(BinanceConfig.LIVE_BASE_URL, live.getBaseUrl());
    }

    @Test
    void shouldReturnCorrectStreamUrl() {
        BinanceConfig testnet = BinanceConfig.testnet("key", "secret");
        BinanceConfig live = BinanceConfig.live("key", "secret");

        assertEquals(BinanceConfig.TESTNET_STREAM_URL, testnet.getStreamUrl());
        assertEquals(BinanceConfig.LIVE_STREAM_URL, live.getStreamUrl());
    }

    @Test
    void shouldThrowOnNullApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new BinanceConfig(null, "secret", true));
    }

    @Test
    void shouldThrowOnBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new BinanceConfig("  ", "secret", true));
    }

    @Test
    void shouldThrowOnNullSecretKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new BinanceConfig("key", null, true));
    }

    @Test
    void shouldThrowOnBlankSecretKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new BinanceConfig("key", "  ", true));
    }
}
