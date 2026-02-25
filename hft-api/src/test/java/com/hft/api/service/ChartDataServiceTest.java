package com.hft.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.algo.base.AlgorithmState;
import com.hft.api.dto.*;
import com.hft.engine.service.OrderManager;
import com.hft.engine.TradingEngine;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.dto.AlpacaBar;
import com.hft.exchange.alpaca.dto.AlpacaBarsResponse;
import com.hft.exchange.binance.BinanceHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChartDataServiceTest {

    @Mock
    private TradingService tradingService;

    @Mock
    private StubMarketDataService stubMarketDataService;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChartDataService chartDataService;

    @BeforeEach
    void setUp() {
        chartDataService = new ChartDataService(tradingService, stubMarketDataService, exchangeService, messagingTemplate);
    }

    @Test
    void getHistoricalCandles_noClient_returnsStubCandles() {
        when(exchangeService.getBinanceClient()).thenReturn(null);

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertNotNull(candles);
        assertFalse(candles.isEmpty());
        assertEquals(10, candles.size());
    }

    @Test
    void getHistoricalCandles_noClient_alpaca_returnsStubCandles() {
        when(exchangeService.getAlpacaClient()).thenReturn(null);

        List<CandleDto> candles = chartDataService.getHistoricalCandles("AAPL", "ALPACA", "5m", 10, "live");

        assertNotNull(candles);
        assertFalse(candles.isEmpty());
        assertEquals(10, candles.size());
    }

    @Test
    void getHistoricalCandles_withBinanceClient_returnsRealCandles() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        // Create mock kline data - Binance returns array of arrays
        ObjectMapper mapper = new ObjectMapper();
        String klineJson = "[[1700000000000,\"42000.50\",\"42500.00\",\"41900.00\",\"42300.00\",\"1234.5\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]";
        JsonNode klineNode = mapper.readTree(klineJson);

        when(mockClient.getKlinesLive(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertNotNull(candles);
        assertEquals(1, candles.size());

        CandleDto candle = candles.get(0);
        assertEquals(1700000000L, candle.time());
        assertEquals(42000.50, candle.open(), 0.01);
        assertEquals(42500.00, candle.high(), 0.01);
        assertEquals(41900.00, candle.low(), 0.01);
        assertEquals(42300.00, candle.close(), 0.01);
        assertEquals(1234L, candle.volume());
    }

    @Test
    void getHistoricalCandles_withAlpacaClient_returnsRealCandles() throws Exception {
        AlpacaHttpClient mockClient = mock(AlpacaHttpClient.class);
        when(exchangeService.getAlpacaClient()).thenReturn(mockClient);

        AlpacaBar bar = new AlpacaBar();
        bar.setO("235.50");
        bar.setH("237.00");
        bar.setL("234.00");
        bar.setC("236.25");
        bar.setV(5000000L);
        bar.setT(Instant.ofEpochSecond(1700000000));

        AlpacaBarsResponse response = new AlpacaBarsResponse();
        response.setBars(List.of(bar));

        when(mockClient.getBars(eq("AAPL"), eq("5Min"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(response));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("AAPL", "ALPACA", "5m", 10, "live");

        assertNotNull(candles);
        assertEquals(1, candles.size());

        CandleDto candle = candles.get(0);
        assertEquals(1700000000L, candle.time());
        assertEquals(235.50, candle.open(), 0.01);
        assertEquals(237.00, candle.high(), 0.01);
        assertEquals(234.00, candle.low(), 0.01);
        assertEquals(236.25, candle.close(), 0.01);
        assertEquals(5000000L, candle.volume());
    }

    @Test
    void getHistoricalCandles_stubCacheNeverExpires() {
        when(exchangeService.getBinanceClient()).thenReturn(null);

        // First call
        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");
        // Second call should return same cached instance
        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertSame(first, second, "Stub data should be cached permanently");
    }

    @Test
    void getHistoricalCandles_realDataCachedWithinTtl() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        ObjectMapper mapper = new ObjectMapper();
        String klineJson = "[[1700000000000,\"42000.00\",\"42500.00\",\"41900.00\",\"42300.00\",\"1234.5\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]";
        JsonNode klineNode = mapper.readTree(klineJson);

        when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        // First call fetches from exchange
        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");
        // Second call should use cache (within TTL)
        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertSame(first, second, "Should return cached real data within TTL");
        // Only one call to getKlines because second was cached
        verify(mockClient, times(1)).getKlinesLive(anyString(), anyString(), anyInt());
    }

    @Test
    void getHistoricalCandles_clientThrowsException_fallsBackToStub() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertNotNull(candles);
        assertEquals(10, candles.size(), "Should fall back to stub candles");
    }

    @Test
    void clearCache_removesAllEntries() {
        when(exchangeService.getBinanceClient()).thenReturn(null);

        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        chartDataService.clearCache();

        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertNotSame(first, second, "Should return new instance after cache clear");
    }

    @Test
    void getHistoricalCandles_unknownExchange_returnsStubCandles() {
        List<CandleDto> candles = chartDataService.getHistoricalCandles("XYZ", "UNKNOWN", "5m", 10, "live");

        assertNotNull(candles);
        assertEquals(10, candles.size());
    }

    @Test
    void getHistoricalCandles_sourceExchange_callsGetKlines() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        ObjectMapper mapper = new ObjectMapper();
        String klineJson = "[[1700000000000,\"81000.50\",\"81500.00\",\"80900.00\",\"81300.00\",\"500.0\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]";
        JsonNode klineNode = mapper.readTree(klineJson);

        when(mockClient.getKlines(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "exchange");

        assertNotNull(candles);
        assertEquals(1, candles.size());
        assertEquals(81000.50, candles.get(0).open(), 0.01);

        // Should call getKlines (not getKlinesLive)
        verify(mockClient).getKlines("BTCUSDT", "5m", 10);
        verify(mockClient, never()).getKlinesLive(anyString(), anyString(), anyInt());
    }

    @Test
    void getHistoricalCandles_sourceLive_callsGetKlinesLive() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        ObjectMapper mapper = new ObjectMapper();
        String klineJson = "[[1700000000000,\"63000.50\",\"63500.00\",\"62900.00\",\"63300.00\",\"800.0\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]";
        JsonNode klineNode = mapper.readTree(klineJson);

        when(mockClient.getKlinesLive(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

        assertNotNull(candles);
        assertEquals(1, candles.size());
        assertEquals(63000.50, candles.get(0).open(), 0.01);

        // Should call getKlinesLive (not getKlines)
        verify(mockClient).getKlinesLive("BTCUSDT", "5m", 10);
        verify(mockClient, never()).getKlines(anyString(), anyString(), anyInt());
    }

    @Test
    void getHistoricalCandles_cacheKeyIncludesSource_separateEntries() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode liveKlines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]");
        JsonNode exchangeKlines = mapper.readTree("[[1700000000000,\"81000.00\",\"81500.00\",\"80500.00\",\"81200.00\",\"200.0\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]");

        when(mockClient.getKlinesLive(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(liveKlines));
        when(mockClient.getKlines(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(exchangeKlines));

        List<CandleDto> live = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");
        List<CandleDto> exchange = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "exchange");

        // Should have different data (from different endpoints)
        assertNotSame(live, exchange, "Live and exchange should be cached separately");
        assertEquals(63000.00, live.get(0).open(), 0.01);
        assertEquals(81000.00, exchange.get(0).open(), 0.01);
    }

    // ========================================================================
    // Trigger Range & Price Consistency Tests
    // ========================================================================

    @Nested
    class TriggerRangeTests {

        private static final int BINANCE_PRICE_SCALE = 100_000_000;
        private static final int ALPACA_PRICE_SCALE = 100;

        private StrategyDto createStrategy(String id, String type, String symbol, Map<String, Object> params) {
            return new StrategyDto(id, "test-" + type, type, AlgorithmState.RUNNING,
                    List.of(symbol), params, 0.0, 100, null);
        }

        @Test
        void triggerRanges_binance_fromRawPrice_matchesCandleScale() {
            // Simulate Binance raw price: $65,000 * 100_000_000 = 6_500_000_000_000
            long rawBtcPrice = 65_000L * BINANCE_PRICE_SCALE;
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(rawBtcPrice);

            StrategyDto strategy = createStrategy("s1", "Momentum", "BTCUSDT",
                    Map.of("signalThreshold", 0.02, "maxPositionSize", 100));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", null);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);

            // Thresholds should be in dollar terms near $65,000 (not billions, not fractions)
            assertNotNull(range.buyTriggerLow());
            assertTrue(range.buyTriggerLow() > 60_000 && range.buyTriggerLow() < 70_000,
                    "Buy trigger low should be near $65K, got: " + range.buyTriggerLow());
            assertTrue(range.sellTriggerHigh() > 60_000 && range.sellTriggerHigh() < 70_000,
                    "Sell trigger high should be near $65K, got: " + range.sellTriggerHigh());

            // currentPrice in the DTO should also be in dollars
            assertEquals(65_000.0, range.currentPrice(), 0.01);
        }

        @Test
        void triggerRanges_alpaca_fromRawPrice_matchesCandleScale() {
            // Simulate Alpaca raw price: $235.00 * 100 = 23_500
            long rawAaplPrice = 235_00L; // 23500 in cents
            when(stubMarketDataService.getCurrentPrice("ALPACA", "AAPL")).thenReturn(rawAaplPrice);

            StrategyDto strategy = createStrategy("s1", "Momentum", "AAPL",
                    Map.of("signalThreshold", 0.02, "maxPositionSize", 100));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("AAPL", "ALPACA", null);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);

            // Thresholds should be in dollar terms near $235
            assertTrue(range.buyTriggerLow() > 200 && range.buyTriggerLow() < 270,
                    "Buy trigger low should be near $235, got: " + range.buyTriggerLow());
            assertEquals(235.0, range.currentPrice(), 0.01);
        }

        @Test
        void triggerRanges_fallbackToLastCandlePrice_whenNoRawPrice() {
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(null);

            StrategyDto strategy = createStrategy("s1", "Momentum", "BTCUSDT",
                    Map.of("signalThreshold", 0.02, "maxPositionSize", 100));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            // lastCandlePrice is already in dollars (from candle DTO)
            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", 65_000.0);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);

            assertEquals(65_000.0, range.currentPrice(), 0.01);
            assertTrue(range.buyTriggerLow() > 60_000 && range.buyTriggerLow() < 70_000,
                    "Fallback price should produce valid thresholds near $65K, got: " + range.buyTriggerLow());
        }

        @Test
        void triggerRanges_emaAdxRsi_hasAllThresholds() {
            long rawPrice = 65_000L * BINANCE_PRICE_SCALE;
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(rawPrice);

            StrategyDto strategy = createStrategy("s1", "EmaAdxRsi", "BTCUSDT",
                    Map.of("adxThreshold", 25.0, "rsiBullThreshold", 55.0, "rsiBearThreshold", 45.0));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", null);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);
            assertNotNull(range.buyTriggerLow(), "EmaAdxRsi should have buyTriggerLow");
            assertNotNull(range.buyTriggerHigh(), "EmaAdxRsi should have buyTriggerHigh");
            assertNotNull(range.sellTriggerLow(), "EmaAdxRsi should have sellTriggerLow");
            assertNotNull(range.sellTriggerHigh(), "EmaAdxRsi should have sellTriggerHigh");
            assertNotNull(range.description());
            assertTrue(range.description().contains("ADX"));
        }

        @Test
        void triggerRanges_bollingerSqueeze_hasAllThresholds() {
            long rawPrice = 65_000L * BINANCE_PRICE_SCALE;
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(rawPrice);

            StrategyDto strategy = createStrategy("s1", "BollingerSqueeze", "BTCUSDT",
                    Map.of("bbStdDev", 2.5, "kcMultiplier", 2.0));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", null);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);
            assertNotNull(range.buyTriggerLow(), "BollingerSqueeze should have buyTriggerLow");
            assertNotNull(range.sellTriggerHigh(), "BollingerSqueeze should have sellTriggerHigh");
            assertTrue(range.description().contains("Squeeze"));
        }

        @Test
        void triggerRanges_vwapMeanReversion_hasAllThresholds() {
            long rawPrice = 65_000L * BINANCE_PRICE_SCALE;
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(rawPrice);

            StrategyDto strategy = createStrategy("s1", "VwapMeanReversion", "BTCUSDT",
                    Map.of("upperSigma", 2.3, "lowerSigma", 2.3, "exitSigma", 0.5));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", null);

            assertEquals(1, ranges.size());
            TriggerRangeDto range = ranges.get(0);
            assertNotNull(range.buyTriggerLow(), "VwapMeanReversion should have buyTriggerLow");
            assertNotNull(range.buyTriggerHigh(), "VwapMeanReversion should have buyTriggerHigh");
            assertNotNull(range.sellTriggerLow(), "VwapMeanReversion should have sellTriggerLow");
            assertNotNull(range.sellTriggerHigh(), "VwapMeanReversion should have sellTriggerHigh");
            assertTrue(range.description().contains("VWAP"));
        }

        @Test
        void triggerRanges_thresholdsConsistentWithCandlePrices() {
            // Verify trigger range values are in the same scale as candle prices
            when(exchangeService.getBinanceClient()).thenReturn(null);
            List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");
            double candleClose = candles.get(candles.size() - 1).close();

            // Now get trigger ranges using fallback to candle price
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(null);
            StrategyDto strategy = createStrategy("s1", "Momentum", "BTCUSDT",
                    Map.of("signalThreshold", 0.02, "maxPositionSize", 100));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", candleClose);

            TriggerRangeDto range = ranges.get(0);
            // Threshold values should be within 10% of candle price
            double ratio = range.buyTriggerLow() / candleClose;
            assertTrue(ratio > 0.9 && ratio < 1.1,
                    "Trigger range should be within 10% of candle price. " +
                    "Candle: " + candleClose + ", Trigger: " + range.buyTriggerLow());
        }

        @Test
        void triggerRanges_buyAboveSellBelow_currentPrice() {
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(null);

            StrategyDto strategy = createStrategy("s1", "MeanReversion", "BTCUSDT",
                    Map.of("entryZScore", 2.0, "exitZScore", 0.5, "maxPositionSize", 100));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            double currentPrice = 65_000.0;
            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", currentPrice);

            TriggerRangeDto range = ranges.get(0);
            // Mean reversion: buy low < current price, sell high > current price
            assertTrue(range.buyTriggerLow() < currentPrice,
                    "Mean reversion buy low should be below current price");
            assertTrue(range.sellTriggerHigh() > currentPrice,
                    "Mean reversion sell high should be above current price");
        }

        @Test
        void triggerRanges_noStrategiesForSymbol_returnsEmpty() {
            StrategyDto strategy = createStrategy("s1", "Momentum", "ETHUSDT",
                    Map.of("signalThreshold", 0.02));
            when(tradingService.getStrategies()).thenReturn(List.of(strategy));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", 65_000.0);
            assertTrue(ranges.isEmpty());
        }

        @Test
        void triggerRanges_multipleStrategies_returnsAllRanges() {
            when(stubMarketDataService.getCurrentPrice("BINANCE", "BTCUSDT")).thenReturn(null);

            StrategyDto momentum = createStrategy("s1", "Momentum", "BTCUSDT",
                    Map.of("signalThreshold", 0.02));
            StrategyDto ema = createStrategy("s2", "EmaAdxRsi", "BTCUSDT",
                    Map.of("adxThreshold", 25.0));
            StrategyDto squeeze = createStrategy("s3", "BollingerSqueeze", "BTCUSDT",
                    Map.of("bbStdDev", 2.5));
            when(tradingService.getStrategies()).thenReturn(List.of(momentum, ema, squeeze));

            List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges("BTCUSDT", "BINANCE", 65_000.0);

            assertEquals(3, ranges.size());
            // All should have valid (non-null) thresholds
            for (TriggerRangeDto range : ranges) {
                assertNotNull(range.buyTriggerLow(),
                        range.type() + " missing buyTriggerLow");
                assertNotNull(range.description(),
                        range.type() + " missing description");
                assertTrue(range.buyTriggerLow() > 50_000,
                        range.type() + " threshold too small: " + range.buyTriggerLow());
                assertTrue(range.buyTriggerLow() < 80_000,
                        range.type() + " threshold too large: " + range.buyTriggerLow());
            }
        }
    }

    // ========================================================================
    // Exchange Mode & Live Chart Quote Broadcast Tests
    // ========================================================================

    @Nested
    class ExchangeModeTests {

        private void mockTradingEngine() {
            TradingEngine engine = mock(TradingEngine.class);
            OrderManager orderManager = mock(OrderManager.class);
            when(engine.getOrderManager()).thenReturn(orderManager);
            when(orderManager.getOrders()).thenReturn(List.of());
            when(tradingService.getTradingEngine()).thenReturn(engine);
            when(tradingService.getStrategies()).thenReturn(List.of());
        }

        @Test
        void getChartData_populatesExchangeMode_fromExchangeStatus() {
            when(exchangeService.getBinanceClient()).thenReturn(null);
            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Testnet)", "testnet", true, true, null, null));
            mockTradingEngine();

            ChartDataDto result = chartDataService.getChartData("BTCUSDT", "BINANCE", "5m", 10, "live");

            assertEquals("testnet", result.exchangeMode());
        }

        @Test
        void getChartData_exchangeMode_stubWhenNoStatus() {
            when(exchangeService.getBinanceClient()).thenReturn(null);
            when(exchangeService.getExchangeStatus("BINANCE")).thenReturn(null);
            mockTradingEngine();

            ChartDataDto result = chartDataService.getChartData("BTCUSDT", "BINANCE", "5m", 10, "live");

            assertEquals("stub", result.exchangeMode());
        }

        @Test
        void getChartData_exchangeMode_liveMode() {
            when(exchangeService.getBinanceClient()).thenReturn(null);
            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Live)", "live", true, true, null, null));
            mockTradingEngine();

            ChartDataDto result = chartDataService.getChartData("BTCUSDT", "BINANCE", "5m", 10, "live");

            assertEquals("live", result.exchangeMode());
        }
    }

    @Nested
    class BroadcastLiveChartQuotesTests {

        @Test
        void broadcastLiveChartQuotes_doesNothing_whenNoTrackedSymbols() {
            // No symbols have been fetched, so no tracked symbols exist
            chartDataService.broadcastLiveChartQuotes();

            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void broadcastLiveChartQuotes_doesNothing_inStubMode() throws Exception {
            // Trigger symbol tracking by fetching Binance candles
            BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
            when(exchangeService.getBinanceClient()).thenReturn(mockClient);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode klines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\"]]");
            when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(klines));

            chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

            // Simulate stub mode
            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Stub)", "stub", true, true, null, null));

            chartDataService.broadcastLiveChartQuotes();

            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void broadcastLiveChartQuotes_doesNothing_inLiveMode() throws Exception {
            BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
            when(exchangeService.getBinanceClient()).thenReturn(mockClient);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode klines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\"]]");
            when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(klines));

            chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Live)", "live", true, true, null, null));

            chartDataService.broadcastLiveChartQuotes();

            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void broadcastLiveChartQuotes_broadcastsLivePrice_inTestnetMode() throws Exception {
            BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
            when(exchangeService.getBinanceClient()).thenReturn(mockClient);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode klines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\"]]");
            when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(klines));

            chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Testnet)", "testnet", true, true, null, null));

            JsonNode ticker = mapper.readTree("{\"symbol\":\"BTCUSDT\",\"price\":\"63150.50\"}");
            when(mockClient.getTickerPriceLive("BTCUSDT"))
                    .thenReturn(CompletableFuture.completedFuture(ticker));

            chartDataService.broadcastLiveChartQuotes();

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/chart-quotes/BINANCE/BTCUSDT"),
                    argThat((QuoteDto q) -> q.midPrice() == 63150.50 && q.symbol().equals("BTCUSDT"))
            );
        }

        @Test
        void broadcastLiveChartQuotes_broadcastsLivePrice_inSandboxMode() throws Exception {
            BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
            when(exchangeService.getBinanceClient()).thenReturn(mockClient);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode klines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\"]]");
            when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(klines));

            chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Sandbox)", "sandbox", true, true, null, null));

            JsonNode ticker = mapper.readTree("{\"symbol\":\"BTCUSDT\",\"price\":\"63150.50\"}");
            when(mockClient.getTickerPriceLive("BTCUSDT"))
                    .thenReturn(CompletableFuture.completedFuture(ticker));

            chartDataService.broadcastLiveChartQuotes();

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/chart-quotes/BINANCE/BTCUSDT"),
                    argThat((QuoteDto q) -> q.midPrice() == 63150.50)
            );
        }

        @Test
        void broadcastLiveChartQuotes_handlesTickerFailureGracefully() throws Exception {
            BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
            when(exchangeService.getBinanceClient()).thenReturn(mockClient);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode klines = mapper.readTree("[[1700000000000,\"63000.00\",\"63500.00\",\"62500.00\",\"63200.00\",\"100.0\"]]");
            when(mockClient.getKlinesLive(anyString(), anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(klines));

            chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10, "live");

            when(exchangeService.getExchangeStatus("BINANCE"))
                    .thenReturn(new ExchangeStatusDto("BINANCE", "Binance (Testnet)", "testnet", true, true, null, null));

            when(mockClient.getTickerPriceLive("BTCUSDT"))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

            // Should not throw
            assertDoesNotThrow(() -> chartDataService.broadcastLiveChartQuotes());
            verifyNoInteractions(messagingTemplate);
        }
    }
}
