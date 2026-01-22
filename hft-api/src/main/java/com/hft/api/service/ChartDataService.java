package com.hft.api.service;

import com.hft.api.dto.*;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.TradingStrategy;
import com.hft.core.model.Exchange;
import com.hft.core.model.Order;
import com.hft.core.model.Symbol;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for generating chart data including historical candles,
 * order markers, and strategy trigger ranges.
 */
@Service
public class ChartDataService {

    private final TradingService tradingService;
    private final StubMarketDataService stubMarketDataService;

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

    public ChartDataService(TradingService tradingService, StubMarketDataService stubMarketDataService) {
        this.tradingService = tradingService;
        this.stubMarketDataService = stubMarketDataService;
    }

    /**
     * Get complete chart data for a symbol.
     */
    public ChartDataDto getChartData(String symbolTicker, String exchangeName, String interval, int periods) {
        List<CandleDto> candles = getHistoricalCandles(symbolTicker, exchangeName, interval, periods);
        List<OrderMarkerDto> orders = getOrderMarkers(symbolTicker, exchangeName);
        List<TriggerRangeDto> triggerRanges = getTriggerRanges(symbolTicker, exchangeName);

        return new ChartDataDto(symbolTicker, exchangeName, interval, candles, orders, triggerRanges);
    }

    /**
     * Generate historical candlestick data.
     */
    public List<CandleDto> getHistoricalCandles(String symbolTicker, String exchangeName, String interval, int periods) {
        String cacheKey = symbolTicker + ":" + exchangeName + ":" + interval + ":" + periods;

        // Use cached data if available (regenerate periodically in real app)
        return candleCache.computeIfAbsent(cacheKey, k ->
                generateStubCandles(symbolTicker, interval, periods));
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

        Instant now = Instant.now();
        long intervalSeconds = getIntervalSeconds(interval);

        double currentPrice = basePrice;

        for (int i = periods - 1; i >= 0; i--) {
            long time = now.minus(i * intervalSeconds, ChronoUnit.SECONDS).getEpochSecond();

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

                double price = order.getAverageFilledPrice() > 0
                        ? order.getAverageFilledPrice() / 100.0
                        : order.getPrice() / 100.0;

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
    public List<TriggerRangeDto> getTriggerRanges(String symbolTicker, String exchangeName) {
        List<TriggerRangeDto> ranges = new ArrayList<>();

        // Get current price
        Long currentPriceCents = stubMarketDataService.getCurrentPrice(exchangeName, symbolTicker);
        double currentPrice = currentPriceCents != null ? currentPriceCents / 100.0 : BASE_PRICES.getOrDefault(symbolTicker, 100.0);

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
    }
}
