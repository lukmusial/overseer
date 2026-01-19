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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeanReversionStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private MeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);

        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());

        strategy = MeanReversionStrategy.builder()
                .addSymbol(testSymbol)
                .lookbackPeriod(20)
                .entryZScore(2.0)
                .exitZScore(0.5)
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
    void getName_ShouldReturnMeanReversion() {
        assertEquals("MeanReversion", strategy.getName());
    }

    @Test
    void onQuote_ShouldBuildPriceHistory() {
        strategy.start();

        // Feed enough quotes to build history
        for (int i = 0; i < 25; i++) {
            strategy.onQuote(createQuote(15000L));
        }

        assertEquals(15000.0, strategy.getMean(testSymbol), 0.01);
        assertEquals(0.0, strategy.getStdDev(testSymbol), 0.01); // All same price
    }

    @Test
    void onQuote_ShouldCalculateMeanAndStdDev() {
        strategy.start();

        // Feed prices that create variance
        for (int i = 0; i < 20; i++) {
            long price = 15000L + (i % 2 == 0 ? 100 : -100);
            strategy.onQuote(createQuote(price));
        }

        double mean = strategy.getMean(testSymbol);
        double stdDev = strategy.getStdDev(testSymbol);

        assertTrue(Math.abs(mean - 15000.0) < 50, "Mean should be close to 15000");
        assertTrue(stdDev > 0, "StdDev should be positive with variance");
    }

    @Test
    void onQuote_WhenPriceBelowLowerBand_ShouldGenerateBuySignal() {
        strategy.start();

        // Build stable history at 150
        for (int i = 0; i < 20; i++) {
            strategy.onQuote(createQuote(15000L + (i % 5) * 10)); // Small variance
        }

        // Spike down significantly (below 2 std devs)
        strategy.onQuote(createQuote(14000L));

        double signal = strategy.getSignal(testSymbol);
        double zScore = strategy.getZScore(testSymbol);

        assertTrue(zScore < 0, "Z-score should be negative (price below mean)");
        assertTrue(signal > 0, "Signal should be positive (buy) when price below mean");
    }

    @Test
    void onQuote_WhenPriceAboveUpperBand_ShouldGenerateSellSignal() {
        strategy.start();

        // Build stable history at 150
        for (int i = 0; i < 20; i++) {
            strategy.onQuote(createQuote(15000L + (i % 5) * 10));
        }

        // Spike up significantly (above 2 std devs)
        strategy.onQuote(createQuote(16000L));

        double signal = strategy.getSignal(testSymbol);
        double zScore = strategy.getZScore(testSymbol);

        assertTrue(zScore > 0, "Z-score should be positive (price above mean)");
        assertTrue(signal < 0, "Signal should be negative (sell) when price above mean");
    }

    @Test
    void onQuote_WithExtremeMove_ShouldSubmitOrder() {
        strategy.start();

        // Build history with some variance
        for (int i = 0; i < 20; i++) {
            strategy.onQuote(createQuote(15000L + (i % 3 - 1) * 50));
        }

        // Extreme move
        strategy.onQuote(createQuote(14000L));

        verify(context, atLeastOnce()).submitOrder(any());
    }

    @Test
    void onQuote_WithNormalPrice_ShouldNotSubmitOrder() {
        strategy.start();

        // All prices at mean
        for (int i = 0; i < 25; i++) {
            strategy.onQuote(createQuote(15000L));
        }

        verify(context, never()).submitOrder(any());
    }

    @Test
    void getBands_ShouldCalculateCorrectly() {
        strategy.start();

        // Build history with variance
        for (int i = 0; i < 20; i++) {
            long price = 15000L + ((i % 4) - 2) * 50;
            strategy.onQuote(createQuote(price));
        }

        double mean = strategy.getMean(testSymbol);
        double stdDev = strategy.getStdDev(testSymbol);
        double upperBand = strategy.getUpperBand(testSymbol);
        double lowerBand = strategy.getLowerBand(testSymbol);

        assertEquals(mean + 2.0 * stdDev, upperBand, 0.01);
        assertEquals(mean - 2.0 * stdDev, lowerBand, 0.01);
        assertTrue(upperBand > lowerBand);
    }

    @Test
    void onFill_ShouldUpdatePosition() {
        strategy.start();

        Trade fill = createFill(OrderSide.BUY, 50, 15000L);
        strategy.onFill(fill);

        assertEquals(50, strategy.getCurrentPosition(testSymbol));
    }

    @Test
    void exitPosition_WhenPriceReturnsToMean() {
        strategy = MeanReversionStrategy.builder()
                .addSymbol(testSymbol)
                .lookbackPeriod(10)
                .entryZScore(1.5)
                .exitZScore(0.3)
                .maxPositionSize(100)
                .build();

        strategy.initialize(context);
        strategy.start();

        // Build history with variance so we get non-zero stdDev
        for (int i = 0; i < 10; i++) {
            strategy.onQuote(createQuote(15000L + ((i % 3) - 1) * 100));
        }

        // Enter long position manually
        strategy.onFill(createFill(OrderSide.BUY, 100, 14500L));

        // Price at mean should have low z-score
        double mean = strategy.getMean(testSymbol);
        double stdDev = strategy.getStdDev(testSymbol);

        // Verify we have valid statistics
        assertTrue(stdDev > 0, "Should have non-zero stdDev: " + stdDev);

        // Price at mean
        strategy.onQuote(createQuote((long) mean));

        double zScore = strategy.getZScore(testSymbol);
        assertTrue(Math.abs(zScore) < 1.0, "Z-score (" + zScore + ") should be relatively low at mean");
    }

    @Test
    void realizedPnl_ShouldTrackProfitOnClose() {
        strategy.start();

        // Open long at 140
        strategy.onFill(createFill(OrderSide.BUY, 100, 14000L));

        // Close at 150 (profit of 100 * 1000 = 100000)
        strategy.onFill(createFill(OrderSide.SELL, 100, 15000L));

        assertEquals(0, strategy.getCurrentPosition(testSymbol));
        assertEquals(100000, strategy.getRealizedPnl());
    }

    @Test
    void maxDrawdown_ShouldTrack() {
        strategy.start();

        // Open position
        strategy.onFill(createFill(OrderSide.BUY, 100, 15000L));

        // Price drops - creates drawdown
        strategy.onQuote(createQuote(14000L));

        // Drawdown should be tracked
        assertTrue(strategy.getMaxDrawdown() > 0);
    }

    @Test
    void insufficientHistory_ShouldNotGenerateSignal() {
        strategy.start();

        // Only a few quotes (less than lookback period of 20)
        for (int i = 0; i < 5; i++) {
            strategy.onQuote(createQuote(15000L));
        }

        assertEquals(0.0, strategy.getSignal(testSymbol), 0.01);
        verify(context, never()).submitOrder(any());
    }

    @Test
    void pause_ShouldStopProcessing() {
        strategy.start();
        strategy.pause();

        assertEquals(AlgorithmState.PAUSED, strategy.getState());

        strategy.onQuote(createQuote(10000L)); // Extreme price
        verify(context, never()).submitOrder(any());
    }

    @Test
    void multipleSymbols_ShouldTrackIndependently() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        strategy = MeanReversionStrategy.builder()
                .addSymbol(testSymbol)
                .addSymbol(symbol2)
                .lookbackPeriod(20)
                .build();

        strategy.initialize(context);
        strategy.start();

        // Build different histories
        for (int i = 0; i < 20; i++) {
            strategy.onQuote(createQuote(15000L)); // AAPL stable at 150

            Quote googlQuote = new Quote();
            googlQuote.setSymbol(symbol2);
            googlQuote.setBidPrice(200000L);
            googlQuote.setAskPrice(200010L);
            strategy.onQuote(googlQuote); // GOOGL stable at 2000
        }

        assertEquals(15000.0, strategy.getMean(testSymbol), 10);
        assertEquals(200005.0, strategy.getMean(symbol2), 10);
    }

    @Test
    void builder_ShouldRequireSymbol() {
        assertThrows(IllegalStateException.class, () ->
            MeanReversionStrategy.builder()
                    .lookbackPeriod(20)
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
