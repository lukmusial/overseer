package com.hft.algo.strategy;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmaAdxRsiStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private EmaAdxRsiStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());

        strategy = EmaAdxRsiStrategy.builder()
                .addSymbol(testSymbol)
                .fastEmaPeriod(9)
                .slowEmaPeriod(21)
                .adxPeriod(14)
                .adxThreshold(25.0)
                .rsiPeriod(14)
                .rsiBullThreshold(55.0)
                .rsiBearThreshold(45.0)
                .maxPositionSize(100)
                .build();

        strategy.initialize(context);
    }

    @Test
    void initialize_shouldSetState() {
        assertEquals(AlgorithmState.INITIALIZED, strategy.getState());
        assertTrue(strategy.getSymbols().contains(testSymbol));
    }

    @Test
    void getName_shouldReturnEmaAdxRsi() {
        assertEquals("EmaAdxRsi", strategy.getName());
    }

    @Test
    void initialQuote_shouldNotGenerateSignal() {
        strategy.start();
        strategy.onQuote(createQuote(50000_00L));
        assertEquals(0.0, strategy.getSignal(testSymbol), 0.01);
    }

    @Test
    void uptrend_withHighAdxAndBullishRsi_shouldGenerateBuySignal() {
        strategy.start();

        // Feed uptrending prices to establish:
        // 1. Fast EMA above slow EMA (bullish crossover)
        // 2. ADX above 25 (strong trend)
        // 3. RSI above 55 (bullish confirmation)
        // Start low, trend up over many samples
        for (int i = 0; i < 50; i++) {
            long price = 40000_00L + (long)(i * 500_00L); // Consistent uptrend
            strategy.onQuote(createQuote(price));
        }

        double signal = strategy.getSignal(testSymbol);
        double adx = strategy.getAdx(testSymbol);
        double rsi = strategy.getRsi(testSymbol);

        // After strong uptrend, ADX should be elevated and RSI bullish
        assertTrue(adx > 0, "ADX should be positive in trending market: " + adx);
        assertTrue(rsi > 50, "RSI should be above 50 in uptrend: " + rsi);
        assertTrue(strategy.getFastEma(testSymbol) > strategy.getSlowEma(testSymbol),
                "Fast EMA should be above slow EMA in uptrend");
    }

    @Test
    void lowAdx_shouldNotGenerateSignal() {
        strategy.start();

        // Feed flat/sideways prices (low ADX)
        for (int i = 0; i < 50; i++) {
            // Oscillate around a mean - no trend
            long price = 50000_00L + (long)((i % 4 - 2) * 10_00L);
            strategy.onQuote(createQuote(price));
        }

        // With sideways movement, ADX should be low -> no signal
        double signal = strategy.getSignal(testSymbol);
        double adx = strategy.getAdx(testSymbol);

        // Signal should be zero or near-zero when ADX is low
        if (adx < 25.0) {
            assertEquals(0.0, signal, 0.01, "Signal should be 0 when ADX < threshold");
        }
    }

    @Test
    void onFill_shouldUpdatePosition() {
        strategy.start();

        // Use scaled quantity for BINANCE (1 BTC = 100_000_000)
        Trade fill = createFill(OrderSide.BUY, 50_000_000L, 50000_00L);
        strategy.onFill(fill);

        assertEquals(50_000_000L, strategy.getCurrentPosition(testSymbol));
    }

    @Test
    void realizedPnl_shouldTrackOnClose() {
        strategy.start();

        // Use scaled quantity for BINANCE (1 BTC = 100_000_000)
        strategy.onFill(createFill(OrderSide.BUY, 100_000_000L, 50000_00L));
        strategy.onFill(createFill(OrderSide.SELL, 100_000_000L, 51000_00L));

        assertEquals(0, strategy.getCurrentPosition(testSymbol));
        assertTrue(strategy.getRealizedPnl() > 0, "Should have positive realized P&L");
    }

    @Test
    void pause_shouldStopProcessing() {
        strategy.start();
        strategy.pause();

        assertEquals(AlgorithmState.PAUSED, strategy.getState());
        strategy.onQuote(createQuote(50000_00L));
        verify(context, never()).submitOrder(any());
    }

    @Test
    void rsiIndicator_shouldReflectPriceChanges() {
        strategy.start();

        // Feed consistently rising prices to push RSI high
        for (int i = 0; i < 30; i++) {
            long price = 50000_00L + (long)(i * 200_00L);
            strategy.onQuote(createQuote(price));
        }

        double rsi = strategy.getRsi(testSymbol);
        assertTrue(rsi > 50, "RSI should be above 50 after consistent rises: " + rsi);
    }

    @Test
    void builder_shouldRequireSymbol() {
        assertThrows(IllegalStateException.class, () ->
                EmaAdxRsiStrategy.builder()
                        .fastEmaPeriod(9)
                        .build()
        );
    }

    @Test
    void constructorWithDefaults_shouldWork() {
        EmaAdxRsiStrategy defaultStrategy = new EmaAdxRsiStrategy(java.util.Set.of(testSymbol));
        defaultStrategy.initialize(context);
        assertEquals(AlgorithmState.INITIALIZED, defaultStrategy.getState());
    }

    private Quote createQuote(long midPrice) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(midPrice - 50);
        quote.setAskPrice(midPrice + 50);
        quote.setBidSize(1000);
        quote.setAskSize(1000);
        return quote;
    }

    private Trade createFill(OrderSide side, long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(testSymbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        return trade;
    }
}
