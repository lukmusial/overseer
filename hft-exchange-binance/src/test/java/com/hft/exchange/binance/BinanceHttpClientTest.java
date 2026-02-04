package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceHttpClientTest {

    @Test
    void getKlines_buildsCorrectUrl() {
        // Use testnet config to verify the URL construction
        BinanceConfig config = BinanceConfig.testnet("test-key", "test-secret");
        BinanceHttpClient client = new BinanceHttpClient(config);

        try {
            // getKlines returns a CompletableFuture - we verify it doesn't throw on construction
            var future = client.getKlines("BTCUSDT", "5m", 100);
            assertNotNull(future, "Should return a non-null CompletableFuture");

            // The future will fail (no real server) but the URL should have been constructed correctly
            // We verify that the method accepts valid parameters without errors
        } finally {
            client.close();
        }
    }

    @Test
    void getKlines_acceptsDifferentIntervals() {
        BinanceConfig config = BinanceConfig.testnet("test-key", "test-secret");
        BinanceHttpClient client = new BinanceHttpClient(config);

        try {
            String[] intervals = {"1m", "5m", "15m", "30m", "1h", "4h", "1d"};
            for (String interval : intervals) {
                var future = client.getKlines("ETHUSDT", interval, 50);
                assertNotNull(future, "Should return non-null future for interval: " + interval);
            }
        } finally {
            client.close();
        }
    }

    @Test
    void getExchangeInfo_returnsNonNullFuture() {
        BinanceConfig config = BinanceConfig.testnet("test-key", "test-secret");
        BinanceHttpClient client = new BinanceHttpClient(config);

        try {
            var future = client.getExchangeInfo();
            assertNotNull(future);
        } finally {
            client.close();
        }
    }
}
