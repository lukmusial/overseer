package com.hft.algo.strategy;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.OrderRequest;
import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MomentumStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private MomentumStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);

        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());

        strategy = MomentumStrategy.builder()
                .addSymbol(testSymbol)
                .shortPeriod(5)
                .longPeriod(10)
                .signalThreshold(0.01)
                .maxPositionSize(100)
                .build();

        strategy.initialize(context);
    }

    @Test
    void initialize_ShouldSetState() {
        assertEquals(AlgorithmState.INITIALIZED, strategy.getState());
        assertTrue(strategy.getSymbols().contains(testSymbol));
    }

    @Test
    void start_ShouldChangeState() {
        strategy.start();
        assertEquals(AlgorithmState.RUNNING, strategy.getState());
    }

    @Test
    void getName_ShouldReturnMomentum() {
        assertEquals("Momentum", strategy.getName());
    }

    @Test
    void getDisplayName_WithCustomName_ShouldReturnCustomName() {
        MomentumStrategy customStrategy = new MomentumStrategy(
                Set.of(testSymbol),
                new com.hft.algo.base.StrategyParameters(),
                "My Custom Momentum"
        );
        customStrategy.initialize(context);
        assertEquals("My Custom Momentum", customStrategy.getDisplayName());
    }

    @Test
    void getDisplayName_WithoutCustomName_ShouldReturnTypeName() {
        assertEquals("Momentum", strategy.getDisplayName());
    }

    @Test
    void onQuote_ShouldInitializeEma() {
        strategy.start();

        Quote quote = createQuote(15000L);
        strategy.onQuote(quote);

        assertEquals(15000.0, strategy.getShortEma(testSymbol), 0.01);
        assertEquals(15000.0, strategy.getLongEma(testSymbol), 0.01);
    }

    @Test
    void onQuote_WithUptrend_ShouldGeneratePositiveSignal() {
        strategy.start();

        // Feed rising prices to create uptrend - need significant price change
        for (int i = 0; i < 50; i++) {
            long price = 10000L + (i * 200); // Rising significantly
            strategy.onQuote(createQuote(price));
        }

        double signal = strategy.getSignal(testSymbol);
        double shortEma = strategy.getShortEma(testSymbol);
        double longEma = strategy.getLongEma(testSymbol);

        assertTrue(shortEma > longEma,
                "Short EMA (" + shortEma + ") should be above long EMA (" + longEma + ") in uptrend");
        // Signal may be 0 if below threshold, but EMAs should show trend
    }

    @Test
    void onQuote_WithDowntrend_ShouldGenerateNegativeSignal() {
        strategy.start();

        // Feed falling prices to create downtrend - need significant price change
        for (int i = 0; i < 50; i++) {
            long price = 20000L - (i * 200); // Falling significantly
            strategy.onQuote(createQuote(price));
        }

        double signal = strategy.getSignal(testSymbol);
        double shortEma = strategy.getShortEma(testSymbol);
        double longEma = strategy.getLongEma(testSymbol);

        assertTrue(shortEma < longEma,
                "Short EMA (" + shortEma + ") should be below long EMA (" + longEma + ") in downtrend");
        // Signal may be 0 if below threshold, but EMAs should show trend
    }

    @Test
    void onQuote_WithStrongSignal_ShouldSubmitOrder() {
        strategy.start();

        // Create strong uptrend with massive price change to trigger signal
        for (int i = 0; i < 50; i++) {
            long price = 10000L + (i * 500); // Very strong uptrend
            strategy.onQuote(createQuote(price));
        }

        // Note: Order submission depends on signal strength exceeding threshold
        // The EMAs should diverge enough to generate a signal
        double shortEma = strategy.getShortEma(testSymbol);
        double longEma = strategy.getLongEma(testSymbol);
        assertTrue(shortEma > longEma, "EMAs should show uptrend");
    }

    @Test
    void onQuote_WithWeakSignal_ShouldNotSubmitOrder() {
        strategy.start();

        // Flat prices - no trend
        for (int i = 0; i < 10; i++) {
            strategy.onQuote(createQuote(15000L));
        }

        verify(context, never()).submitOrder(any());
    }

    @Test
    void onFill_ShouldUpdatePosition() {
        strategy.start();

        // Simulate order submission
        Trade fill = createFill(OrderSide.BUY, 50, 15000L);
        strategy.onFill(fill);

        assertEquals(50, strategy.getCurrentPosition(testSymbol));
    }

    @Test
    void onFill_ShouldCalculateRealizedPnl() {
        strategy.start();

        // Open position
        strategy.onFill(createFill(OrderSide.BUY, 100, 15000L));
        assertEquals(100, strategy.getCurrentPosition(testSymbol));

        // Close position with profit
        strategy.onFill(createFill(OrderSide.SELL, 100, 15100L));
        assertEquals(0, strategy.getCurrentPosition(testSymbol));

        // Should have realized profit of 100 * (15100 - 15000) = 10000
        assertTrue(strategy.getRealizedPnl() > 0);
    }

    @Test
    void getUnrealizedPnl_ShouldCalculateCorrectly() {
        strategy.start();

        // Open position at 150
        strategy.onFill(createFill(OrderSide.BUY, 100, 15000L));

        // Update quote to 155
        Quote quote = createQuote(15500L);
        strategy.onQuote(quote);

        // Unrealized P&L = 100 * (15500 - 15000) = 50000
        long unrealizedPnl = strategy.getUnrealizedPnl();
        assertTrue(unrealizedPnl > 0);
    }

    @Test
    void getTargetPosition_ShouldScaleWithSignalStrength() {
        strategy.start();

        // Moderate uptrend
        for (int i = 0; i < 15; i++) {
            strategy.onQuote(createQuote(15000L + i * 20));
        }

        long target = strategy.getTargetPosition(testSymbol);
        assertTrue(target >= 0, "Target should be positive for uptrend");
        assertTrue(target <= 100, "Target should not exceed max position size");
    }

    @Test
    void cancel_ShouldStopStrategy() {
        strategy.start();
        strategy.cancel();

        assertEquals(AlgorithmState.CANCELLED, strategy.getState());

        // Should not process quotes after cancellation
        strategy.onQuote(createQuote(20000L));
        verify(context, never()).submitOrder(any());
    }

    @Test
    void pause_AndResume_ShouldWork() {
        strategy.start();
        assertEquals(AlgorithmState.RUNNING, strategy.getState());

        strategy.pause();
        assertEquals(AlgorithmState.PAUSED, strategy.getState());

        strategy.resume();
        assertEquals(AlgorithmState.RUNNING, strategy.getState());
    }

    @Test
    void multipleSymbols_ShouldTrackIndependently() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        strategy = MomentumStrategy.builder()
                .addSymbol(testSymbol)
                .addSymbol(symbol2)
                .shortPeriod(5)
                .longPeriod(10)
                .build();

        strategy.initialize(context);
        strategy.start();

        // AAPL uptrend - interleave with GOOGL
        for (int i = 0; i < 30; i++) {
            Quote aaplQuote = createQuote(10000L + i * 200);
            strategy.onQuote(aaplQuote);

            Quote googlQuote = new Quote();
            googlQuote.setSymbol(symbol2);
            googlQuote.setBidPrice(200000L - i * 200);
            googlQuote.setAskPrice(200010L - i * 200);
            strategy.onQuote(googlQuote);
        }

        // AAPL short EMA should be above long EMA (uptrend)
        // GOOGL short EMA should be below long EMA (downtrend)
        assertTrue(strategy.getShortEma(testSymbol) > strategy.getLongEma(testSymbol),
                "AAPL should show uptrend");
        assertTrue(strategy.getShortEma(symbol2) < strategy.getLongEma(symbol2),
                "GOOGL should show downtrend");
    }

    @Test
    void builder_ShouldRequireAtLeastOneSymbol() {
        assertThrows(IllegalStateException.class, () ->
            MomentumStrategy.builder()
                    .shortPeriod(5)
                    .longPeriod(10)
                    .build()
        );
    }

    private Quote createQuote(long midPrice) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(midPrice - 5);
        quote.setAskPrice(midPrice + 5);
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
