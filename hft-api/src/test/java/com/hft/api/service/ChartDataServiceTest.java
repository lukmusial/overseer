package com.hft.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.api.dto.CandleDto;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.dto.AlpacaBar;
import com.hft.exchange.alpaca.dto.AlpacaBarsResponse;
import com.hft.exchange.binance.BinanceHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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

    private ChartDataService chartDataService;

    @BeforeEach
    void setUp() {
        chartDataService = new ChartDataService(tradingService, stubMarketDataService, exchangeService);
    }

    @Test
    void getHistoricalCandles_noClient_returnsStubCandles() {
        when(exchangeService.getBinanceClient()).thenReturn(null);

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        assertNotNull(candles);
        assertFalse(candles.isEmpty());
        assertEquals(10, candles.size());
    }

    @Test
    void getHistoricalCandles_noClient_alpaca_returnsStubCandles() {
        when(exchangeService.getAlpacaClient()).thenReturn(null);

        List<CandleDto> candles = chartDataService.getHistoricalCandles("AAPL", "ALPACA", "5m", 10);

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

        when(mockClient.getKlines(eq("BTCUSDT"), eq("5m"), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

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

        List<CandleDto> candles = chartDataService.getHistoricalCandles("AAPL", "ALPACA", "5m", 10);

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
        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);
        // Second call should return same cached instance
        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        assertSame(first, second, "Stub data should be cached permanently");
    }

    @Test
    void getHistoricalCandles_realDataCachedWithinTtl() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        ObjectMapper mapper = new ObjectMapper();
        String klineJson = "[[1700000000000,\"42000.00\",\"42500.00\",\"41900.00\",\"42300.00\",\"1234.5\",1700000300000,\"0\",100,\"0\",\"0\",\"0\"]]";
        JsonNode klineNode = mapper.readTree(klineJson);

        when(mockClient.getKlines(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(klineNode));

        // First call fetches from exchange
        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);
        // Second call should use cache (within TTL)
        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        assertSame(first, second, "Should return cached real data within TTL");
        // Only one call to getKlines because second was cached
        verify(mockClient, times(1)).getKlines(anyString(), anyString(), anyInt());
    }

    @Test
    void getHistoricalCandles_clientThrowsException_fallsBackToStub() throws Exception {
        BinanceHttpClient mockClient = mock(BinanceHttpClient.class);
        when(exchangeService.getBinanceClient()).thenReturn(mockClient);

        when(mockClient.getKlines(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

        List<CandleDto> candles = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        assertNotNull(candles);
        assertEquals(10, candles.size(), "Should fall back to stub candles");
    }

    @Test
    void clearCache_removesAllEntries() {
        when(exchangeService.getBinanceClient()).thenReturn(null);

        List<CandleDto> first = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        chartDataService.clearCache();

        List<CandleDto> second = chartDataService.getHistoricalCandles("BTCUSDT", "BINANCE", "5m", 10);

        assertNotSame(first, second, "Should return new instance after cache clear");
    }

    @Test
    void getHistoricalCandles_unknownExchange_returnsStubCandles() {
        List<CandleDto> candles = chartDataService.getHistoricalCandles("XYZ", "UNKNOWN", "5m", 10);

        assertNotNull(candles);
        assertEquals(10, candles.size());
    }
}
