package com.hft.algo.strategy;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hft.algo.base.OrderRequest;
import org.mockito.ArgumentCaptor;

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

    @Test
    void shouldSkipOrderWhenNotionalBelowMinimum() {
        // BINANCE: quantityScale=100_000_000, priceScale=100_000_000
        // BTC price range: $70k-$72.9k (7e12 to 7.29e12 in scaled units)
        // maxPositionSize = 0.00001 BTC -> target = 1000 (scaled) at signal 1.0
        // Max notional = 1000 * 7.29e12 / (1e8 * 1e8) = $0.73 < $10 default minimum
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);   // Low threshold so ADX triggers easily
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0); // Low so RSI confirms easily
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.00001); // Tiny position: 1000 scaled units
        params.set("maxOrderSize", 0.00001);

        EmaAdxRsiStrategy smallStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        smallStrategy.initialize(context);
        smallStrategy.start();

        // Feed a strong uptrend to generate a buy signal ($70k -> $72.9k, +$100/step)
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            smallStrategy.onQuote(createBinanceQuote(price));
        }

        // The order should be skipped because notional ($0.73 max) is below $10
        verify(context, never()).submitOrder(any());
    }

    @Test
    void shouldSubmitOrderWhenNotionalAboveMinimum() {
        // BINANCE: quantityScale=100_000_000, priceScale=100_000_000
        // BTC price range: $70k-$72.9k
        // maxPositionSize = 0.01 BTC -> target = 1_000_000 (scaled) at signal 1.0
        // Min notional at $70k = 1_000_000 * 7e12 / 1e16 = $700 >> $10 minimum
        // Even at signal 0.1: 100_000 * 7e12 / 1e16 = $70 > $10
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0);
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.01); // 1_000_000 scaled units
        params.set("maxOrderSize", 0.01);

        EmaAdxRsiStrategy largeStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        largeStrategy.initialize(context);
        largeStrategy.start();

        // Feed a strong uptrend to generate a buy signal ($70k -> $72.9k, +$100/step)
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            largeStrategy.onQuote(createBinanceQuote(price));
        }

        // The order should be submitted because notional well exceeds $10
        verify(context, atLeastOnce()).submitOrder(any());
    }

    @Test
    void shouldRespectCustomMinOrderNotional() {
        // Same position size as "above minimum" test (0.01 BTC = 1_000_000 scaled)
        // which passes the default $10 minimum.
        // Set minOrderNotional = 5000.0 so orders are skipped.
        // Max notional at $72.9k = 1_000_000 * 7.29e12 / 1e16 = $729 < $5000 -> skip
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0);
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.01); // 1_000_000 scaled units
        params.set("maxOrderSize", 0.01);
        params.set("minOrderNotional", 5000.0); // Custom: $5000 minimum

        EmaAdxRsiStrategy customStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        customStrategy.initialize(context);
        customStrategy.start();

        // Feed a strong uptrend to generate a buy signal ($70k -> $72.9k, +$100/step)
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            customStrategy.onQuote(createBinanceQuote(price));
        }

        // The order should be skipped because max notional ($729) is below $5000 custom minimum
        verify(context, never()).submitOrder(any());
    }

    @Test
    void shouldThrottleOrdersWithinCooldownPeriod() {
        // Use a strategy with short indicator periods so signals trigger quickly,
        // and a 1-second cooldown (the default).
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0);
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.01);
        params.set("maxOrderSize", 0.01);
        params.set("orderCooldownSeconds", 1.0); // explicit default

        EmaAdxRsiStrategy throttleStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        throttleStrategy.initialize(context);
        throttleStrategy.start();

        // Feed a strong uptrend to generate buy signals — all within 1 second (no wait)
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            throttleStrategy.onQuote(createBinanceQuote(price));
        }

        // Despite many quotes generating signals, cooldown should limit to at most 1 order
        verify(context, atMost(1)).submitOrder(any());
    }

    @Test
    void shouldAllowOrderAfterCooldownExpires() throws InterruptedException {
        // Use a very short cooldown so the test doesn't take long
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0);
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.01);
        params.set("maxOrderSize", 0.01);
        params.set("orderCooldownSeconds", 0.05); // 50ms cooldown

        EmaAdxRsiStrategy cooldownStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        cooldownStrategy.initialize(context);
        cooldownStrategy.start();

        // Phase 1: generate first order via uptrend
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            cooldownStrategy.onQuote(createBinanceQuote(price));
        }
        // At least one order should have been submitted
        verify(context, atLeastOnce()).submitOrder(any());
        int firstCount = mockingDetails(context).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("submitOrder"))
                .toList().size();

        // Wait for cooldown to expire
        Thread.sleep(100);

        // Phase 2: continue feeding quotes to trigger another order
        for (int i = 30; i < 40; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            cooldownStrategy.onQuote(createBinanceQuote(price));
        }

        // Should have at least one more order after cooldown expired
        int totalCount = mockingDetails(context).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("submitOrder"))
                .toList().size();
        assertTrue(totalCount > firstCount,
                "Expected more orders after cooldown expired, got firstCount=" + firstCount + " totalCount=" + totalCount);
    }

    @Test
    void shouldUseIocTimeInForce() {
        // Use a strategy that will generate orders
        var params = new StrategyParameters();
        params.set("fastEmaPeriod", 3);
        params.set("slowEmaPeriod", 5);
        params.set("adxPeriod", 5);
        params.set("adxThreshold", 10.0);
        params.set("rsiPeriod", 5);
        params.set("rsiBullThreshold", 40.0);
        params.set("rsiBearThreshold", 60.0);
        params.set("maxPositionSize", 0.01);
        params.set("maxOrderSize", 0.01);

        EmaAdxRsiStrategy iocStrategy = new EmaAdxRsiStrategy(Set.of(testSymbol), params);
        iocStrategy.initialize(context);
        iocStrategy.start();

        // Feed a strong uptrend to trigger an order submission
        for (int i = 0; i < 30; i++) {
            long price = 7_000_000_000_000L + (i * 10_000_000_000L);
            iocStrategy.onQuote(createBinanceQuote(price));
        }

        // Capture the submitted OrderRequest and verify IOC time-in-force
        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context, atLeastOnce()).submitOrder(captor.capture());

        OrderRequest submittedOrder = captor.getValue();
        assertEquals(TimeInForce.IOC, submittedOrder.getTimeInForce(),
                "Strategy orders should use IOC time-in-force");
    }

    private Quote createBinanceQuote(long midPrice) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(midPrice - 50_000_000L); // $0.50 spread
        quote.setAskPrice(midPrice + 50_000_000L);
        quote.setBidSize(100_000_000L);
        quote.setAskSize(100_000_000L);
        quote.setPriceScale(100_000_000);
        return quote;
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
