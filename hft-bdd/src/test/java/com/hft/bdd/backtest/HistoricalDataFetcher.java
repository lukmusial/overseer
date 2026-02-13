package com.hft.bdd.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches historical kline (candlestick) data from the Binance public REST API.
 * No authentication required - uses the public /api/v3/klines endpoint.
 */
public class HistoricalDataFetcher {

    private static final String BINANCE_BASE_URL = "https://api.binance.com";
    private static final int MAX_KLINES_PER_REQUEST = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 200;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HistoricalDataFetcher() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches klines for a symbol over a time range, paginating automatically.
     *
     * @param symbol      Binance symbol (e.g., "BTCUSDT")
     * @param interval    Kline interval (e.g., "1h", "4h", "1d")
     * @param startTimeMs Start time in epoch milliseconds
     * @param endTimeMs   End time in epoch milliseconds
     * @return Sorted list of candles
     */
    public List<Candle> fetchKlines(String symbol, String interval, long startTimeMs, long endTimeMs)
            throws IOException {
        List<Candle> allCandles = new ArrayList<>();
        long currentStart = startTimeMs;

        while (currentStart < endTimeMs) {
            String url = String.format(
                    "%s/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    BINANCE_BASE_URL, symbol, interval, currentStart, endTimeMs, MAX_KLINES_PER_REQUEST
            );

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Binance API error: " + response.code() + " " + response.message());
                }

                String body = response.body().string();
                JsonNode klines = objectMapper.readTree(body);

                if (!klines.isArray() || klines.isEmpty()) {
                    break;
                }

                for (JsonNode kline : klines) {
                    long openTimeMs = kline.get(0).asLong();
                    double open = Double.parseDouble(kline.get(1).asText());
                    double high = Double.parseDouble(kline.get(2).asText());
                    double low = Double.parseDouble(kline.get(3).asText());
                    double close = Double.parseDouble(kline.get(4).asText());
                    long volume = (long) Double.parseDouble(kline.get(5).asText());

                    allCandles.add(new Candle(openTimeMs / 1000, open, high, low, close, volume));
                }

                // Advance past last candle to avoid duplicates
                long lastTime = klines.get(klines.size() - 1).get(0).asLong();
                currentStart = lastTime + 1;

                // Respect rate limits
                if (currentStart < endTimeMs) {
                    Thread.sleep(RATE_LIMIT_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching klines", e);
            }
        }

        return allCandles;
    }

    /**
     * Fetches the most recent N klines for a symbol.
     */
    public List<Candle> fetchRecentKlines(String symbol, String interval, int count) throws IOException {
        long endMs = System.currentTimeMillis();
        // Estimate start time based on interval
        long intervalMs = parseIntervalMs(interval);
        long startMs = endMs - (long) count * intervalMs;
        return fetchKlines(symbol, interval, startMs, endMs);
    }

    private long parseIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 4 * 3_600_000L;
            case "1d" -> 86_400_000L;
            default -> 3_600_000L;
        };
    }
}
