package com.hft.exchange.alpaca;

/**
 * Configuration for Alpaca API connection.
 */
public record AlpacaConfig(
        String apiKey,
        String secretKey,
        boolean paperTrading,
        String dataFeed
) {
    // API endpoints
    public static final String LIVE_TRADING_URL = "https://api.alpaca.markets";
    public static final String PAPER_TRADING_URL = "https://paper-api.alpaca.markets";
    public static final String MARKET_DATA_URL = "https://data.alpaca.markets";
    public static final String STREAM_URL = "wss://stream.data.alpaca.markets";

    public AlpacaConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (dataFeed == null) {
            dataFeed = "iex"; // Default to IEX (free) feed
        }
    }

    /**
     * Creates a paper trading configuration.
     */
    public static AlpacaConfig paper(String apiKey, String secretKey) {
        return new AlpacaConfig(apiKey, secretKey, true, "iex");
    }

    /**
     * Creates a live trading configuration.
     */
    public static AlpacaConfig live(String apiKey, String secretKey, String dataFeed) {
        return new AlpacaConfig(apiKey, secretKey, false, dataFeed);
    }

    /**
     * Returns the base URL for trading API.
     */
    public String getTradingUrl() {
        return paperTrading ? PAPER_TRADING_URL : LIVE_TRADING_URL;
    }

    /**
     * Returns the base URL for market data API.
     */
    public String getMarketDataUrl() {
        return MARKET_DATA_URL;
    }

    /**
     * Returns the WebSocket URL for streaming data.
     */
    public String getStreamUrl() {
        return STREAM_URL + "/v2/" + dataFeed;
    }
}
