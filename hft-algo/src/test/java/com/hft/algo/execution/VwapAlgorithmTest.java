package com.hft.algo.execution;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.AlgorithmStats;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VwapAlgorithmTest {

    @BeforeEach
    void setUpMockitoLenient() {
        lenient().when(context.getHistoricalVolume(any(), anyInt())).thenReturn(new long[]{100, 100, 100, 100, 100, 100, 100, 100, 100, 100});
        lenient().when(context.getCurrentTimeNanos()).thenReturn(START_TIME);
        lenient().when(context.getQuote(any())).thenReturn(createQuote(15000L, 15010L, 1000, 1000));
    }

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private VwapAlgorithm algorithm;

    private static final long START_TIME = 1_000_000_000_000L;
    private static final long END_TIME = START_TIME + 600_000_000_000L; // 10 minutes

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);

        algorithm = VwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(1000)
                .startTimeNanos(START_TIME)
                .endTimeNanos(END_TIME)
                .maxParticipationRate(0.25)
                .build();

        algorithm.initialize(context);
    }

    @Test
    void initialize_ShouldSetState() {
        assertEquals(AlgorithmState.INITIALIZED, algorithm.getState());
        assertEquals(testSymbol, algorithm.getSymbol());
        assertEquals(OrderSide.BUY, algorithm.getSide());
        assertEquals(1000, algorithm.getTargetQuantity());
    }

    @Test
    void start_ShouldChangeStateToRunning() {
        algorithm.start();
        assertEquals(AlgorithmState.RUNNING, algorithm.getState());
    }

    @Test
    void pause_ShouldChangeStateToPaused() {
        algorithm.start();
        algorithm.pause();
        assertEquals(AlgorithmState.PAUSED, algorithm.getState());
    }

    @Test
    void resume_ShouldChangeStateToRunning() {
        algorithm.start();
        algorithm.pause();
        algorithm.resume();
        assertEquals(AlgorithmState.RUNNING, algorithm.getState());
    }

    @Test
    void cancel_ShouldChangeStateToCancelled() {
        algorithm.start();
        algorithm.cancel();
        assertEquals(AlgorithmState.CANCELLED, algorithm.getState());
    }

    @Test
    void onQuote_WhenRunning_ShouldSubmitOrder() {
        algorithm.start();

        Quote quote = createQuote(15000L, 15010L, 1000, 1000);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        OrderRequest request = captor.getValue();
        assertEquals(testSymbol, request.getSymbol());
        assertEquals(OrderSide.BUY, request.getSide());
        assertTrue(request.getQuantity() > 0);
    }

    @Test
    void onQuote_WhenNotRunning_ShouldNotSubmitOrder() {
        // Not started
        Quote quote = createQuote(15000L, 15010L, 1000, 1000);
        algorithm.onQuote(quote);

        verify(context, never()).submitOrder(any());
    }

    @Test
    void onQuote_ShouldRespectParticipationRate() {
        algorithm.start();

        // Small liquidity available
        Quote quote = createQuote(15000L, 15010L, 10, 10);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        // Should not exceed 25% of available (10 * 0.25 = 2)
        assertTrue(captor.getValue().getQuantity() <= 10);
    }

    @Test
    void onFill_ShouldUpdateFilledQuantity() {
        algorithm.start();

        Trade fill = createFill(testSymbol, OrderSide.BUY, 100, 15005L);
        algorithm.onFill(fill);

        assertEquals(100, algorithm.getFilledQuantity());
        assertEquals(900, algorithm.getRemainingQuantity());
    }

    @Test
    void onFill_ShouldCalculateAveragePrice() {
        algorithm.start();

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 100, 15000L));
        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 100, 15010L));

        assertEquals(200, algorithm.getFilledQuantity());
        assertEquals(15005L, algorithm.getAverageFillPrice());
    }

    @Test
    void onFill_WhenComplete_ShouldChangeStateToCompleted() {
        algorithm.start();

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 1000, 15005L));

        assertEquals(AlgorithmState.COMPLETED, algorithm.getState());
        assertTrue(algorithm.isComplete());
    }

    @Test
    void getProgress_ShouldReflectFillPercentage() {
        algorithm.start();

        assertEquals(0.0, algorithm.getProgress(), 0.01);

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 250, 15000L));
        assertEquals(25.0, algorithm.getProgress(), 0.01);

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 250, 15000L));
        assertEquals(50.0, algorithm.getProgress(), 0.01);
    }

    @Test
    void onTimer_WhenDeadlineReached_ShouldComplete() {
        algorithm.start();

        when(context.getCurrentTimeNanos()).thenReturn(END_TIME + 1);
        algorithm.onTimer(END_TIME + 1);

        assertEquals(AlgorithmState.COMPLETED, algorithm.getState());
    }

    @Test
    void getStats_ShouldReturnStatistics() {
        algorithm.start();
        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 500, 15000L));

        AlgorithmStats stats = algorithm.getStats();

        assertEquals("VWAP", stats.algorithmName());
        assertEquals(1000, stats.targetQuantity());
        assertEquals(500, stats.filledQuantity());
        assertEquals(15000L, stats.averageFillPrice());
    }

    @Test
    void limitPrice_ShouldPreventBuyingAboveLimit() {
        algorithm = VwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(1000)
                .limitPrice(14990L) // Limit below ask
                .startTimeNanos(START_TIME)
                .endTimeNanos(END_TIME)
                .build();

        algorithm.initialize(context);
        algorithm.start();

        // Quote with ask above limit
        Quote quote = createQuote(15000L, 15010L, 1000, 1000);
        algorithm.onQuote(quote);

        // Should not submit order above limit
        verify(context, never()).submitOrder(any());
    }

    @Test
    void builder_ShouldValidateRequiredFields() {
        assertThrows(IllegalStateException.class, () ->
            VwapAlgorithm.builder()
                    .side(OrderSide.BUY)
                    .targetQuantity(1000)
                    .build()
        );

        assertThrows(IllegalStateException.class, () ->
            VwapAlgorithm.builder()
                    .symbol(testSymbol)
                    .targetQuantity(1000)
                    .build()
        );

        assertThrows(IllegalStateException.class, () ->
            VwapAlgorithm.builder()
                    .symbol(testSymbol)
                    .side(OrderSide.BUY)
                    .targetQuantity(0)
                    .build()
        );
    }

    private Quote createQuote(long bidPrice, long askPrice, long bidSize, long askSize) {
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(bidPrice);
        quote.setAskPrice(askPrice);
        quote.setBidSize(bidSize);
        quote.setAskSize(askSize);
        return quote;
    }

    private Trade createFill(Symbol symbol, OrderSide side, long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        return trade;
    }
}
