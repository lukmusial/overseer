package com.hft.exchange.binance;

import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.exchange.binance.dto.BinanceTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinanceMarketDataPortTest {

    @Mock
    private BinanceHttpClient httpClient;

    @Mock
    private BinanceWebSocketClient webSocketClient;

    private BinanceMarketDataPort marketDataPort;

    @BeforeEach
    void setUp() {
        marketDataPort = new BinanceMarketDataPort(httpClient, webSocketClient);
    }

    @Test
    void getQuote_shouldUseEpochNanosForTimestamp() throws Exception {
        Symbol btcusdt = new Symbol("BTCUSDT", Exchange.BINANCE);

        BinanceTicker ticker = new BinanceTicker();
        ticker.setSymbol("BTCUSDT");
        ticker.setBidPrice("65000.00");
        ticker.setAskPrice("65001.00");
        ticker.setBidQty("1.5");
        ticker.setAskQty("2.0");

        when(httpClient.publicGet(anyString(), eq(BinanceTicker.class)))
                .thenReturn(CompletableFuture.completedFuture(ticker));

        long beforeMillis = System.currentTimeMillis();
        Quote quote = marketDataPort.getQuote(btcusdt).get();
        long afterMillis = System.currentTimeMillis();

        // Timestamp should be epoch nanos (convertible to valid millis)
        long timestampMillis = quote.getTimestamp() / 1_000_000;
        assertTrue(timestampMillis >= beforeMillis,
                "Quote timestamp should be at or after test start: " + timestampMillis + " >= " + beforeMillis);
        assertTrue(timestampMillis <= afterMillis,
                "Quote timestamp should be at or before test end: " + timestampMillis + " <= " + afterMillis);

        // Epoch nanos for 2025+ should be > 1.7e18
        assertTrue(quote.getTimestamp() > 1_700_000_000_000_000_000L,
                "Timestamp should be valid epoch nanos, not nanoTime: " + quote.getTimestamp());
    }

    @Test
    void getQuote_shouldSetPriceScaleForBinance() throws Exception {
        Symbol btcusdt = new Symbol("BTCUSDT", Exchange.BINANCE);

        BinanceTicker ticker = new BinanceTicker();
        ticker.setSymbol("BTCUSDT");
        ticker.setBidPrice("65000.00");
        ticker.setAskPrice("65001.00");
        ticker.setBidQty("1.5");
        ticker.setAskQty("2.0");

        when(httpClient.publicGet(anyString(), eq(BinanceTicker.class)))
                .thenReturn(CompletableFuture.completedFuture(ticker));

        Quote quote = marketDataPort.getQuote(btcusdt).get();

        // Prices should be in Binance's 8-decimal scale
        assertEquals(6500000000000L, quote.getBidPrice());
        assertEquals(6500100000000L, quote.getAskPrice());
    }
}
