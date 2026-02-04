package com.hft.exchange.binance;

/**
 * Configuration for Binance API connection.
 */
public record BinanceConfig(
        String apiKey,
        String secretKey,
        boolean testnet
) {
    // API endpoints
    public static final String LIVE_BASE_URL = "https://api.binance.com";
    public static final String TESTNET_BASE_URL = "https://testnet.binance.vision";
    public static final String LIVE_STREAM_URL = "wss://stream.binance.com:9443";
    public static final String TESTNET_STREAM_URL = "wss://stream.testnet.binance.vision";

    public BinanceConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }

    /**
     * Creates a testnet configuration.
     */
    public static BinanceConfig testnet(String apiKey, String secretKey) {
        return new BinanceConfig(apiKey, secretKey, true);
    }

    /**
     * Creates a live trading configuration.
     */
    public static BinanceConfig live(String apiKey, String secretKey) {
        return new BinanceConfig(apiKey, secretKey, false);
    }

    /**
     * Returns the base URL for REST API.
     */
    public String getBaseUrl() {
        return testnet ? TESTNET_BASE_URL : LIVE_BASE_URL;
    }

    /**
     * Returns the WebSocket URL for streaming data.
     */
    public String getStreamUrl() {
        return testnet ? TESTNET_STREAM_URL : LIVE_STREAM_URL;
    }
}
