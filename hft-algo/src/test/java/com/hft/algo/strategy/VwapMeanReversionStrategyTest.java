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
class VwapMeanReversionStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private VwapMeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());

        strategy = VwapMeanReversionStrategy.builder()
                .addSymbol(testSymbol)
                .upperSigma(2.3)
                .lowerSigma(2.3)
                .exitSigma(0.5)
                .maxHoldMinutes(240)
                .volumeFilterMultiplier(0.0) // Disable volume filter for most tests
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
    void getName_shouldReturnVwapMeanReversion() {
        assertEquals("VwapMeanReversion", strategy.getName());
    }

    @Test
    void vwap_shouldCalculateFromPriceAndVolume() {
        strategy.start();

        // Feed quotes with known prices and volumes
        for (int i = 0; i < 20; i++) {
            strategy.onQuote(createQuoteWithVolume(50000_00L, 1000));
        }

        double vwap = strategy.getVwap(testSymbol);
        assertEquals(50000_00L, vwap, 100, "VWAP should be close to the uniform price");
    }

    @Test
    void priceBelowLowerBand_shouldGenerateBuySignal() {
        strategy.start();

        // Build VWAP around 50000 with many data points so one extreme doesn't shift VWAP much
        for (int i = 0; i < 50; i++) {
            long price = 50000_00L + (i % 5 - 2) * 200_00L;
            strategy.onQuote(createQuoteWithVolume(price, 1000));
        }

        double vwap = strategy.getVwap(testSymbol);
        double sigma = strategy.getVwapSigma(testSymbol);
        assertTrue(sigma > 0, "Sigma should be positive with price variance");

        // Use a fixed extreme price far below the established VWAP
        // With 50 data points at volume 1000, one point with volume 1000 barely shifts VWAP
        long extremeLow = 44000_00L; // ~6000 cents below VWAP of ~50000
        strategy.onQuote(createQuoteWithVolume(extremeLow, 1000));

        double signal = strategy.getSignal(testSymbol);
        assertTrue(signal > 0, "Should generate buy signal below lower band. Signal: " + signal +
                ", VWAP: " + strategy.getVwap(testSymbol) + ", sigma: " + strategy.getVwapSigma(testSymbol));
    }

    @Test
    void priceAboveUpperBand_shouldGenerateSellSignal() {
        strategy.start();

        // Build VWAP around 50000 with many data points
        for (int i = 0; i < 50; i++) {
            long price = 50000_00L + (i % 5 - 2) * 200_00L;
            strategy.onQuote(createQuoteWithVolume(price, 1000));
        }

        double vwap = strategy.getVwap(testSymbol);
        double sigma = strategy.getVwapSigma(testSymbol);
        assertTrue(sigma > 0, "Sigma should be positive");

        // Fixed extreme price far above the established VWAP
        long extremeHigh = 56000_00L; // ~6000 cents above VWAP of ~50000
        strategy.onQuote(createQuoteWithVolume(extremeHigh, 1000));

        double signal = strategy.getSignal(testSymbol);
        assertTrue(signal < 0, "Should generate sell signal above upper band. Signal: " + signal +
                ", VWAP: " + strategy.getVwap(testSymbol) + ", sigma: " + strategy.getVwapSigma(testSymbol));
    }

    @Test
    void priceNearVwap_shouldNotGenerateEntrySignal() {
        strategy.start();

        // Build VWAP
        for (int i = 0; i < 20; i++) {
            long price = 50000_00L + (long)((i % 5 - 2) * 200_00L);
            strategy.onQuote(createQuoteWithVolume(price, 1000));
        }

        double vwap = strategy.getVwap(testSymbol);
        // Price right at VWAP
        strategy.onQuote(createQuoteWithVolume((long) vwap, 1000));

        double signal = strategy.getSignal(testSymbol);
        assertEquals(0.0, signal, 0.01, "No entry signal near VWAP");
    }

    @Test
    void exitWhenPriceReturnsToVwap() {
        strategy.start();

        // Build VWAP with variance
        for (int i = 0; i < 15; i++) {
            long price = 50000_00L + (long)((i % 5 - 2) * 200_00L);
            strategy.onQuote(createQuoteWithVolume(price, 1000));
        }

        // Enter a long position
        strategy.onFill(createFill(OrderSide.BUY, 50, 48000_00L));
        assertEquals(50, strategy.getCurrentPosition(testSymbol));

        // Price returns to VWAP (low deviation) -> exit signal
        double vwap = strategy.getVwap(testSymbol);
        strategy.onQuote(createQuoteWithVolume((long) vwap, 1000));

        double signal = strategy.getSignal(testSymbol);
        // With position and price near VWAP, should signal to exit (signal near 0)
        assertTrue(Math.abs(signal) < 0.6, "Should signal exit near VWAP: " + signal);
    }

    @Test
    void onFill_shouldUpdatePosition() {
        strategy.start();

        Trade fill = createFill(OrderSide.BUY, 50, 50000_00L);
        strategy.onFill(fill);

        assertEquals(50, strategy.getCurrentPosition(testSymbol));
    }

    @Test
    void bands_shouldBeSymmetricAroundVwap() {
        strategy.start();

        for (int i = 0; i < 20; i++) {
            long price = 50000_00L + (long)((i % 5 - 2) * 200_00L);
            strategy.onQuote(createQuoteWithVolume(price, 1000));
        }

        double vwap = strategy.getVwap(testSymbol);
        double upper = strategy.getUpperBand(testSymbol);
        double lower = strategy.getLowerBand(testSymbol);

        assertTrue(upper > vwap, "Upper band should be above VWAP");
        assertTrue(lower < vwap, "Lower band should be below VWAP");
        assertEquals(upper - vwap, vwap - lower, 1.0, "Bands should be symmetric");
    }

    @Test
    void insufficientHistory_shouldNotGenerateSignal() {
        strategy.start();

        for (int i = 0; i < 5; i++) {
            strategy.onQuote(createQuoteWithVolume(50000_00L, 1000));
        }

        assertEquals(0.0, strategy.getSignal(testSymbol), 0.01);
    }

    @Test
    void pause_shouldStopProcessing() {
        strategy.start();
        strategy.pause();

        assertEquals(AlgorithmState.PAUSED, strategy.getState());
        strategy.onQuote(createQuoteWithVolume(50000_00L, 1000));
        verify(context, never()).submitOrder(any());
    }

    @Test
    void builder_shouldRequireSymbol() {
        assertThrows(IllegalStateException.class, () ->
                VwapMeanReversionStrategy.builder()
                        .upperSigma(2.3)
                        .build()
        );
    }

    @Test
    void constructorWithDefaults_shouldWork() {
        VwapMeanReversionStrategy defaultStrategy = new VwapMeanReversionStrategy(java.util.Set.of(testSymbol));
        defaultStrategy.initialize(context);
        assertEquals(AlgorithmState.INITIALIZED, defaultStrategy.getState());
    }

    private Quote createQuoteWithVolume(long midPrice, long volume) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(midPrice - 50);
        quote.setAskPrice(midPrice + 50);
        quote.setBidSize(volume);
        quote.setAskSize(volume);
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
