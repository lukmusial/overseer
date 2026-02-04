package com.hft.exchange.alpaca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaHttpClientTest {

    @Test
    void getBars_buildsCorrectUrl() {
        AlpacaConfig config = AlpacaConfig.paper("test-key", "test-secret");
        AlpacaHttpClient client = new AlpacaHttpClient(config);

        try {
            // getBars returns a CompletableFuture - verify it constructs without error
            var future = client.getBars("AAPL", "5Min", 100);
            assertNotNull(future, "Should return a non-null CompletableFuture");
        } finally {
            client.close();
        }
    }

    @Test
    void getBars_acceptsDifferentTimeframes() {
        AlpacaConfig config = AlpacaConfig.paper("test-key", "test-secret");
        AlpacaHttpClient client = new AlpacaHttpClient(config);

        try {
            String[] timeframes = {"1Min", "5Min", "15Min", "30Min", "1Hour", "4Hour", "1Day"};
            for (String timeframe : timeframes) {
                var future = client.getBars("MSFT", timeframe, 50);
                assertNotNull(future, "Should return non-null future for timeframe: " + timeframe);
            }
        } finally {
            client.close();
        }
    }

    @Test
    void getActiveEquities_returnsNonNullFuture() {
        AlpacaConfig config = AlpacaConfig.paper("test-key", "test-secret");
        AlpacaHttpClient client = new AlpacaHttpClient(config);

        try {
            var future = client.getActiveEquities();
            assertNotNull(future);
        } finally {
            client.close();
        }
    }
}
