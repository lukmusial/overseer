package com.hft.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hft.api.dto.*;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.TradingStrategy;
import com.hft.core.model.Exchange;
import com.hft.core.model.Order;
import com.hft.core.model.Symbol;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.dto.AlpacaBar;
import com.hft.exchange.alpaca.dto.AlpacaBarsResponse;
import com.hft.exchange.binance.BinanceHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for generating chart data including historical candles,
 * order markers, and strategy trigger ranges.
 */
@Service
public class ChartDataService {

    private static final Logger log = LoggerFactory.getLogger(ChartDataService.class);

    private final TradingService tradingService;
    private final StubMarketDataService stubMarketDataService;
    private final ExchangeService exchangeService;

    // Cache TTL for real exchange data (30 seconds)
    private static final long REAL_DATA_CACHE_TTL_MS = 30_000;

    // Base prices for stub data generation (same as StubMarketDataService)
    private static final Map<String, Double> BASE_PRICES = Map.of(
            "AAPL", 235.0,
            "MSFT", 425.0,
            "NVDA", 148.0,
            "TSLA", 375.0,
            "GOOGL", 192.0,
            "BTCUSDT", 105000.0,
            "ETHUSDT", 3250.0,
            "SOLUSDT", 225.0,
            "BNBUSDT", 650.0,
            "XRPUSDT", 2.25
    );

    // Volatility per symbol (daily standard deviation as percentage)
    private static final Map<String, Double> VOLATILITY = Map.of(
            "AAPL", 0.018,
            "MSFT", 0.015,
            "NVDA", 0.035,
            "TSLA", 0.045,
            "GOOGL", 0.020,
            "BTCUSDT", 0.030,
            "ETHUSDT", 0.040,
            "SOLUSDT", 0.055,
            "BNBUSDT", 0.035,
            "XRPUSDT", 0.050
    );

    // Cache for generated historical data
    private final Map<String, List<CandleDto>> candleCache = new ConcurrentHashMap<>();
    // Timestamps for cache entries (used for time-based expiry of real data)
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    // Track which cache entries are from real exchange data (vs stub)
    private final Map<String, Boolean> cacheIsReal = new ConcurrentHashMap<>();
    // Track the data source for each cache entry
    private final Map<String, String> cacheDataSource = new ConcurrentHashMap<>();

    public ChartDataService(TradingService tradingService, StubMarketDataService stubMarketDataService,
                            ExchangeService exchangeService) {
        this.tradingService = tradingService;
        this.stubMarketDataService = stubMarketDataService;
        this.exchangeService = exchangeService;
    }

    /**
     * Get complete chart data for a symbol.
     */
    public ChartDataDto getChartData(String symbolTicker, String exchangeName, String interval, int periods) {
        List<CandleDto> candles = getHistoricalCandles(symbolTicker, exchangeName, interval, periods);
        List<OrderMarkerDto> orders = getOrderMarkers(symbolTicker, exchangeName);

        // Use last candle close as fallback price for trigger ranges
        Double lastCandlePrice = candles.isEmpty() ? null : candles.get(candles.size() - 1).close();
        List<TriggerRangeDto> triggerRanges = getTriggerRanges(symbolTicker, exchangeName, lastCandlePrice);

        String cacheKey = symbolTicker + ":" + exchangeName + ":" + interval + ":" + periods;
        String dataSource = cacheDataSource.getOrDefault(cacheKey, "stub");

        return new ChartDataDto(symbolTicker, exchangeName, interval, dataSource, candles, orders, triggerRanges);
    }

    /**
     * Get historical candlestick data, fetching from real exchange when available.
     */
    public List<CandleDto> getHistoricalCandles(String symbolTicker, String exchangeName, String interval, int periods) {
        String cacheKey = symbolTicker + ":" + exchangeName + ":" + interval + ":" + periods;

        // Check if cached data exists and is still valid
        List<CandleDto> cached = candleCache.get(cacheKey);
        if (cached != null) {
            Boolean isReal = cacheIsReal.getOrDefault(cacheKey, false);
            if (!isReal && !hasRealClient(exchangeName)) {
                // Stub data never expires when no real client is available
                return cached;
            }
            if (isReal) {
                // Real data expires after TTL
                Long timestamp = cacheTimestamps.get(cacheKey);
                if (timestamp != null && (System.currentTimeMillis() - timestamp) < REAL_DATA_CACHE_TTL_MS) {
                    return cached;
                }
            }
            // Stub data with a real client available, or expired real data — re-fetch
        }

        // Try to fetch real data from exchange
        String[] sourceOut = new String[1];
        List<CandleDto> candles = tryFetchRealCandles(symbolTicker, exchangeName, interval, periods, sourceOut);

        if (candles != null && !candles.isEmpty()) {
            candleCache.put(cacheKey, candles);
            cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            cacheIsReal.put(cacheKey, true);
            cacheDataSource.put(cacheKey, sourceOut[0] != null ? sourceOut[0] : "live");
            return candles;
        }

        // Fall back to stub data
        if (cached != null) {
            return cached;
        }
        List<CandleDto> stubCandles = generateStubCandles(symbolTicker, interval, periods);
        candleCache.put(cacheKey, stubCandles);
        cacheIsReal.put(cacheKey, false);
        cacheDataSource.put(cacheKey, "stub");
        return stubCandles;
    }

    /**
     * Check if a real exchange client is available for the given exchange.
     */
    private boolean hasRealClient(String exchangeName) {
        return switch (exchangeName.toUpperCase()) {
            case "BINANCE" -> exchangeService.getBinanceClient() != null;
            case "ALPACA" -> exchangeService.getAlpacaClient() != null;
            default -> false;
        };
    }

    /**
     * Attempt to fetch real candle data from the exchange.
     * Returns null if no client is available or the fetch fails.
     * Sets sourceOut[0] to the data source label (e.g., "live", "sandbox").
     */
    private List<CandleDto> tryFetchRealCandles(String symbolTicker, String exchangeName, String interval, int periods, String[] sourceOut) {
        try {
            return switch (exchangeName.toUpperCase()) {
                case "BINANCE" -> fetchBinanceCandles(symbolTicker, interval, periods, sourceOut);
                case "ALPACA" -> fetchAlpacaCandles(symbolTicker, interval, periods, sourceOut);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to fetch real candles for {} on {}: {}", symbolTicker, exchangeName, e.getMessage());
            return null;
        }
    }

    private List<CandleDto> fetchBinanceCandles(String symbol, String interval, int periods, String[] sourceOut) throws Exception {
        BinanceHttpClient client = exchangeService.getBinanceClient();
        if (client == null) {
            return null;
        }

        // Always use the live Binance endpoint for market data — testnet is unreliable
        sourceOut[0] = "live";
        JsonNode klines = client.getKlinesLive(symbol, interval, periods)
                .get(15, TimeUnit.SECONDS);

        if (klines == null || !klines.isArray() || klines.isEmpty()) {
            return null;
        }

        List<CandleDto> candles = new ArrayList<>();
        for (JsonNode kline : klines) {
            // Binance kline format: [openTime, open, high, low, close, volume, ...]
            long openTimeMs = kline.get(0).asLong();
            double open = Double.parseDouble(kline.get(1).asText());
            double high = Double.parseDouble(kline.get(2).asText());
            double low = Double.parseDouble(kline.get(3).asText());
            double close = Double.parseDouble(kline.get(4).asText());
            long volume = (long) Double.parseDouble(kline.get(5).asText());

            candles.add(new CandleDto(openTimeMs / 1000, round(open), round(high), round(low), round(close), volume));
        }

        return candles;
    }

    private List<CandleDto> fetchAlpacaCandles(String symbol, String interval, int periods, String[] sourceOut) throws Exception {
        AlpacaHttpClient client = exchangeService.getAlpacaClient();
        if (client == null) {
            return null;
        }

        // Alpaca market data always comes from the same endpoint regardless of paper/live
        sourceOut[0] = "live";
        String alpacaTimeframe = toAlpacaTimeframe(interval);
        AlpacaBarsResponse response = client.getBars(symbol, alpacaTimeframe, periods)
                .get(15, TimeUnit.SECONDS);

        if (response == null || response.getBars() == null || response.getBars().isEmpty()) {
            return null;
        }

        List<CandleDto> candles = new ArrayList<>();
        for (AlpacaBar bar : response.getBars()) {
            long timeSeconds = bar.getT() != null ? bar.getT().getEpochSecond() : 0;
            double open = parseDouble(bar.getO());
            double high = parseDouble(bar.getH());
            double low = parseDouble(bar.getL());
            double close = parseDouble(bar.getC());

            candles.add(new CandleDto(timeSeconds, round(open), round(high), round(low), round(close), bar.getV()));
        }

        return candles;
    }

    /**
     * Convert our interval format to Alpaca's timeframe format.
     */
    private String toAlpacaTimeframe(String interval) {
        return switch (interval) {
            case "1m" -> "1Min";
            case "5m" -> "5Min";
            case "15m" -> "15Min";
            case "30m" -> "30Min";
            case "1h" -> "1Hour";
            case "4h" -> "4Hour";
            case "1d" -> "1Day";
            default -> "1Min";
        };
    }

    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    /**
     * Generate stub historical candles with realistic price movements.
     */
    private List<CandleDto> generateStubCandles(String symbol, String interval, int periods) {
        double basePrice = BASE_PRICES.getOrDefault(symbol, 100.0);
        double volatility = VOLATILITY.getOrDefault(symbol, 0.02);

        // Adjust volatility based on interval
        double intervalVolatility = volatility * getIntervalVolatilityMultiplier(interval);

        List<CandleDto> candles = new ArrayList<>();
        Random random = new Random(symbol.hashCode()); // Deterministic for same symbol

        long nowSeconds = Instant.now().getEpochSecond();
        long intervalSeconds = getIntervalSeconds(interval);

        // Align to interval boundary (floor to nearest interval)
        long alignedNow = (nowSeconds / intervalSeconds) * intervalSeconds;

        double currentPrice = basePrice;

        for (int i = periods - 1; i >= 0; i--) {
            long time = alignedNow - (i * intervalSeconds);

            // Generate OHLC with realistic patterns
            double change = (random.nextGaussian() * intervalVolatility);
            double open = currentPrice;
            double close = currentPrice * (1 + change);

            // High and low with some randomness
            double range = Math.abs(close - open) + (currentPrice * intervalVolatility * random.nextDouble());
            double high = Math.max(open, close) + range * random.nextDouble();
            double low = Math.min(open, close) - range * random.nextDouble();

            // Volume based on volatility (higher volatility = higher volume)
            long volume = (long) (100000 + random.nextInt(900000) * (1 + Math.abs(change) * 10));

            candles.add(new CandleDto(time, round(open), round(high), round(low), round(close), volume));

            currentPrice = close;
        }

        return candles;
    }

    /**
     * Get order markers for orders executed on this symbol.
     */
    public List<OrderMarkerDto> getOrderMarkers(String symbolTicker, String exchangeName) {
        List<OrderMarkerDto> markers = new ArrayList<>();

        // Get orders from the trading engine
        Collection<Order> allOrders = tradingService.getTradingEngine().getOrderManager().getOrders();

        for (Order order : allOrders) {
            if (order.getSymbol().getTicker().equals(symbolTicker) &&
                order.getSymbol().getExchange().name().equals(exchangeName)) {

                // Convert timestamp to seconds
                long timeSeconds = order.getCreatedAt() / 1_000_000_000L;
                if (timeSeconds == 0) {
                    timeSeconds = System.currentTimeMillis() / 1000;
                }

                int scale = order.getPriceScale() > 0 ? order.getPriceScale() : 100;
                double price = order.getAverageFilledPrice() > 0
                        ? order.getAverageFilledPrice() / (double) scale
                        : order.getPrice() / (double) scale;

                markers.add(new OrderMarkerDto(
                        timeSeconds,
                        price,
                        order.getSide().name(),
                        order.getQuantity(),
                        order.getStatus().name(),
                        order.getStrategyId(),
                        String.valueOf(order.getClientOrderId())
                ));
            }
        }

        return markers;
    }

    /**
     * Calculate trigger ranges for active strategies on this symbol.
     */
    public List<TriggerRangeDto> getTriggerRanges(String symbolTicker, String exchangeName, Double lastCandlePrice) {
        List<TriggerRangeDto> ranges = new ArrayList<>();

        // Get current price: prefer real-time quote, then last candle close, then static fallback
        Long currentPriceCents = stubMarketDataService.getCurrentPrice(exchangeName, symbolTicker);
        double currentPrice;
        if (currentPriceCents != null) {
            currentPrice = currentPriceCents / 100.0;
        } else if (lastCandlePrice != null) {
            currentPrice = lastCandlePrice;
        } else {
            currentPrice = BASE_PRICES.getOrDefault(symbolTicker, 100.0);
        }

        // Get active strategies for this symbol
        for (StrategyDto strategy : tradingService.getStrategies()) {
            if (!strategy.symbols().contains(symbolTicker)) {
                continue;
            }

            TriggerRangeDto range = calculateTriggerRange(strategy, symbolTicker, currentPrice);
            if (range != null) {
                ranges.add(range);
            }
        }

        return ranges;
    }

    /**
     * Calculate trigger range based on strategy type and parameters.
     */
    private TriggerRangeDto calculateTriggerRange(StrategyDto strategy, String symbol, double currentPrice) {
        Map<String, Object> params = strategy.parameters();
        String type = strategy.type().toLowerCase();

        Double buyLow = null, buyHigh = null, sellLow = null, sellHigh = null;
        String description;

        switch (type) {
            case "momentum" -> {
                // Momentum triggers on EMA crossovers
                // Approximate: buy when price rises above short EMA, sell when falls below
                double threshold = getDoubleParam(params, "signalThreshold", 0.02);
                buyLow = currentPrice * (1 + threshold * 0.5);
                buyHigh = currentPrice * (1 + threshold * 2);
                sellLow = currentPrice * (1 - threshold * 2);
                sellHigh = currentPrice * (1 - threshold * 0.5);
                description = String.format("Buy signal when price rises %.1f%%-%.1f%% above current; Sell when drops %.1f%%-%.1f%% below",
                        threshold * 50, threshold * 200, threshold * 50, threshold * 200);
            }
            case "meanreversion", "mean_reversion" -> {
                // Mean reversion triggers on z-score deviations
                double entryZ = getDoubleParam(params, "entryZScore", 2.0);
                double exitZ = getDoubleParam(params, "exitZScore", 0.5);
                double stdDev = currentPrice * 0.02; // Assume 2% standard deviation

                // Buy when price is below mean (oversold), sell when above (overbought)
                buyLow = currentPrice - (entryZ * stdDev);
                buyHigh = currentPrice - (exitZ * stdDev);
                sellLow = currentPrice + (exitZ * stdDev);
                sellHigh = currentPrice + (entryZ * stdDev);
                description = String.format("Buy when price drops to $%.2f-$%.2f (oversold); Sell at $%.2f-$%.2f (overbought)",
                        buyLow, buyHigh, sellLow, sellHigh);
            }
            case "vwap" -> {
                // VWAP executes around the volume-weighted average price
                double participationRate = getDoubleParam(params, "maxParticipationRate", 0.25);
                double spread = currentPrice * 0.005; // 0.5% around VWAP
                buyLow = currentPrice - spread;
                buyHigh = currentPrice + spread;
                sellLow = currentPrice - spread;
                sellHigh = currentPrice + spread;
                description = String.format("Executes within $%.2f-$%.2f (±0.5%% of VWAP) at %.0f%% participation",
                        buyLow, buyHigh, participationRate * 100);
            }
            case "twap" -> {
                // TWAP executes at regular intervals regardless of price
                int durationMinutes = getIntParam(params, "durationMinutes", 60);
                int sliceInterval = getIntParam(params, "sliceIntervalSeconds", 60);
                int slices = durationMinutes * 60 / sliceInterval;
                // TWAP is less price-sensitive, show wider range
                double spread = currentPrice * 0.01;
                buyLow = currentPrice - spread;
                buyHigh = currentPrice + spread;
                sellLow = currentPrice - spread;
                sellHigh = currentPrice + spread;
                description = String.format("Executes in %d slices over %d minutes, target range $%.2f-$%.2f",
                        slices, durationMinutes, buyLow, buyHigh);
            }
            default -> {
                description = "Unknown strategy type";
            }
        }

        return new TriggerRangeDto(
                strategy.id(),
                strategy.name(),
                strategy.type(),
                symbol,
                currentPrice,
                buyLow,
                buyHigh,
                sellLow,
                sellHigh,
                description
        );
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private long getIntervalSeconds(String interval) {
        return switch (interval) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "30m" -> 1800;
            case "1h" -> 3600;
            case "4h" -> 14400;
            case "1d" -> 86400;
            default -> 60;
        };
    }

    private double getIntervalVolatilityMultiplier(String interval) {
        // Volatility scales with sqrt of time
        return switch (interval) {
            case "1m" -> 0.1;
            case "5m" -> 0.22;
            case "15m" -> 0.39;
            case "30m" -> 0.55;
            case "1h" -> 0.78;
            case "4h" -> 1.56;
            case "1d" -> 3.0;
            default -> 0.1;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Clear the candle cache (useful when data needs refresh).
     */
    public void clearCache() {
        candleCache.clear();
        cacheTimestamps.clear();
        cacheIsReal.clear();
        cacheDataSource.clear();
    }
}
