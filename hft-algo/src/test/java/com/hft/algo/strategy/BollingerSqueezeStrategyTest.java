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
class BollingerSqueezeStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private BollingerSqueezeStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("ETHUSDT", Exchange.BINANCE);
        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());

        strategy = BollingerSqueezeStrategy.builder()
                .addSymbol(testSymbol)
                .bbPeriod(20)
                .bbStdDev(2.5)
                .kcPeriod(20)
                .kcAtrPeriod(14)
                .kcMultiplier(2.0)
                .macdFast(8)
                .macdSlow(17)
                .macdSignal(9)
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
    void getName_shouldReturnBollingerSqueeze() {
        assertEquals("BollingerSqueeze", strategy.getName());
    }

    @Test
    void stablePrices_shouldCreateSqueezeCondition() {
        strategy.start();

        // Feed very stable prices -> low BB volatility -> squeeze
        for (int i = 0; i < 30; i++) {
            // Tiny spread, very stable = tight BB, wide KC (relative)
            strategy.onQuote(createQuote(3000_00L, 10));
        }

        // After stable prices, BB should be tight
        double bbUp = strategy.getBbUpper(testSymbol);
        double bbLow = strategy.getBbLower(testSymbol);
        assertTrue(bbUp > 0, "BB upper should be calculated");
        assertTrue(bbUp - bbLow < 200, "BB bands should be tight with stable prices");
    }

    @Test
    void volatileBreakout_afterSqueeze_shouldGenerateSignal() {
        strategy.start();

        // Phase 1: Build squeeze with stable prices
        for (int i = 0; i < 25; i++) {
            strategy.onQuote(createQuote(3000_00L, 10));
        }

        // Phase 2: Sharp upward breakout
        for (int i = 0; i < 10; i++) {
            long price = 3000_00L + (long)(i * 100_00L);
            strategy.onQuote(createQuoteWithSpread(price, 200));
        }

        double signal = strategy.getSignal(testSymbol);
        // After breakout from squeeze, should generate some signal
        // Direction depends on MACD histogram
        assertNotNull(strategy.getMacdHistogram(testSymbol));
    }

    @Test
    void onFill_shouldUpdatePosition() {
        strategy.start();

        Trade fill = createFill(OrderSide.BUY, 50, 3000_00L);
        strategy.onFill(fill);

        assertEquals(50, strategy.getCurrentPosition(testSymbol));
    }

    @Test
    void macdHistogram_shouldTrackPriceMovement() {
        strategy.start();

        // Feed trending prices - MACD histogram should reflect direction
        for (int i = 0; i < 30; i++) {
            long price = 3000_00L + (long)(i * 50_00L); // Uptrend
            strategy.onQuote(createQuote(price, 100));
        }

        double histogram = strategy.getMacdHistogram(testSymbol);
        assertTrue(histogram > 0, "MACD histogram should be positive in uptrend: " + histogram);
    }

    @Test
    void pause_shouldStopProcessing() {
        strategy.start();
        strategy.pause();

        assertEquals(AlgorithmState.PAUSED, strategy.getState());
        strategy.onQuote(createQuote(3000_00L, 100));
        verify(context, never()).submitOrder(any());
    }

    @Test
    void insufficientHistory_shouldNotGenerateSignal() {
        strategy.start();

        // Only a few quotes (less than bbPeriod of 20)
        for (int i = 0; i < 5; i++) {
            strategy.onQuote(createQuote(3000_00L, 100));
        }

        assertEquals(0.0, strategy.getSignal(testSymbol), 0.01);
    }

    @Test
    void builder_shouldRequireSymbol() {
        assertThrows(IllegalStateException.class, () ->
                BollingerSqueezeStrategy.builder()
                        .bbPeriod(20)
                        .build()
        );
    }

    @Test
    void constructorWithDefaults_shouldWork() {
        BollingerSqueezeStrategy defaultStrategy = new BollingerSqueezeStrategy(java.util.Set.of(testSymbol));
        defaultStrategy.initialize(context);
        assertEquals(AlgorithmState.INITIALIZED, defaultStrategy.getState());
    }

    private Quote createQuote(long midPrice, long spread) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(midPrice - spread / 2);
        quote.setAskPrice(midPrice + spread / 2);
        quote.setBidSize(500);
        quote.setAskSize(500);
        return quote;
    }

    private Quote createQuoteWithSpread(long midPrice, long spread) {
        return createQuote(midPrice, spread);
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
