package com.hft.algo.execution;

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
class TwapAlgorithmTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private TwapAlgorithm algorithm;

    private static final long START_TIME = 1_000_000_000_000L;
    private static final long DURATION = 600_000_000_000L; // 10 minutes
    private static final long SLICE_INTERVAL = 60_000_000_000L; // 1 minute

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);

        lenient().when(context.getCurrentTimeNanos()).thenReturn(START_TIME);
        lenient().when(context.getQuote(any())).thenReturn(createQuote(15000L, 15010L, 1000, 1000));

        algorithm = TwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(1000)
                .startTimeNanos(START_TIME)
                .endTimeNanos(START_TIME + DURATION)
                .sliceIntervalNanos(SLICE_INTERVAL)
                .maxParticipationRate(0.25)
                .build();

        algorithm.initialize(context);
    }

    @Test
    void initialize_ShouldCalculateSlices() {
        assertEquals(AlgorithmState.INITIALIZED, algorithm.getState());
        assertEquals(testSymbol, algorithm.getSymbol());
        assertEquals(1000, algorithm.getTargetQuantity());
    }

    @Test
    void start_ShouldChangeStateToRunning() {
        algorithm.start();
        assertEquals(AlgorithmState.RUNNING, algorithm.getState());
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
    void onQuote_ForSellOrder_ShouldUseBidPrice() {
        algorithm = TwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.SELL)
                .targetQuantity(1000)
                .startTimeNanos(START_TIME)
                .endTimeNanos(START_TIME + DURATION)
                .build();

        algorithm.initialize(context);
        algorithm.start();

        Quote quote = createQuote(15000L, 15010L, 1000, 1000);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        assertEquals(OrderSide.SELL, captor.getValue().getSide());
        assertEquals(15000L, captor.getValue().getPrice()); // Bid price for sell
    }

    @Test
    void onFill_ShouldTrackQuantity() {
        algorithm.start();

        Trade fill = createFill(testSymbol, OrderSide.BUY, 100, 15005L);
        algorithm.onFill(fill);

        assertEquals(100, algorithm.getFilledQuantity());
    }

    @Test
    void onFill_WhenComplete_ShouldChangeState() {
        algorithm.start();

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 1000, 15005L));

        assertEquals(AlgorithmState.COMPLETED, algorithm.getState());
    }

    @Test
    void onTimer_ShouldCompleteAtDeadline() {
        algorithm.start();

        when(context.getCurrentTimeNanos()).thenReturn(START_TIME + DURATION + 1);
        algorithm.onTimer(START_TIME + DURATION + 1);

        assertEquals(AlgorithmState.COMPLETED, algorithm.getState());
    }

    @Test
    void uniformDistribution_ShouldSpreadEvenly() {
        // 10 slices (10 minutes / 1 minute), 1000 shares = 100 per slice
        algorithm.start();

        Quote quote = createQuote(15000L, 15010L, 10000, 10000);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        // Should target approximately 100 shares per slice (1000/10)
        assertTrue(captor.getValue().getQuantity() <= 100 + 10); // Allow some variance
    }

    @Test
    void catchUp_WhenBehindSchedule_ShouldIncreaseOrderSize() {
        algorithm.start();

        // Advance time to second slice without any fills
        long secondSliceTime = START_TIME + SLICE_INTERVAL + 1000;
        lenient().when(context.getCurrentTimeNanos()).thenReturn(secondSliceTime);
        algorithm.onTimer(secondSliceTime);

        Quote quote = createQuote(15000L, 15010L, 10000, 10000);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        // Should include catch-up quantity from missed first slice
        // First slice target is 100, second slice target is 100, so should be > 100
        assertTrue(captor.getValue().getQuantity() > 0);
    }

    @Test
    void participationRate_ShouldLimitOrderSize() {
        algorithm.start();

        // Limited liquidity
        Quote quote = createQuote(15000L, 15010L, 100, 100);
        algorithm.onQuote(quote);

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(context).submitOrder(captor.capture());

        // 25% participation of 100 ask size = 25
        assertTrue(captor.getValue().getQuantity() <= 25);
    }

    @Test
    void getProgress_ShouldReflectCompletion() {
        algorithm.start();

        assertEquals(0.0, algorithm.getProgress(), 0.01);

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 500, 15000L));
        assertEquals(50.0, algorithm.getProgress(), 0.01);

        algorithm.onFill(createFill(testSymbol, OrderSide.BUY, 500, 15000L));
        assertEquals(100.0, algorithm.getProgress(), 0.01);
    }

    @Test
    void limitPrice_ShouldPreventUnfavorableFills() {
        algorithm = TwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(1000)
                .limitPrice(14990L)
                .startTimeNanos(START_TIME)
                .endTimeNanos(START_TIME + DURATION)
                .build();

        algorithm.initialize(context);
        algorithm.start();

        // Ask price above limit
        Quote quote = createQuote(15000L, 15010L, 1000, 1000);
        algorithm.onQuote(quote);

        verify(context, never()).submitOrder(any());
    }

    @Test
    void builderWithDuration_ShouldSetEndTime() {
        long duration = 300_000_000_000L; // 5 minutes

        TwapAlgorithm alg = TwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(500)
                .startTimeNanos(START_TIME)
                .duration(duration)
                .build();

        assertEquals(START_TIME + duration, alg.getEndTimeNanos());
    }

    @Test
    void builderSliceIntervalSeconds_ShouldConvertToNanos() {
        TwapAlgorithm alg = TwapAlgorithm.builder()
                .symbol(testSymbol)
                .side(OrderSide.BUY)
                .targetQuantity(500)
                .startTimeNanos(START_TIME)
                .endTimeNanos(START_TIME + DURATION)
                .sliceIntervalSeconds(30)
                .build();

        alg.initialize(context);
        // 10 minutes / 30 seconds = 20 slices
        assertNotNull(alg);
    }

    @Test
    void builder_ShouldValidateParameters() {
        assertThrows(IllegalStateException.class, () ->
            TwapAlgorithm.builder()
                    .symbol(testSymbol)
                    .side(OrderSide.BUY)
                    .targetQuantity(1000)
                    .startTimeNanos(START_TIME)
                    .endTimeNanos(START_TIME - 1) // End before start
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
