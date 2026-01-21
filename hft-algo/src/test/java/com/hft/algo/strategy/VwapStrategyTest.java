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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VwapStrategyTest {

    @Mock
    private AlgorithmContext context;

    private Symbol testSymbol;
    private VwapStrategy strategy;

    @BeforeEach
    void setUp() {
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
        lenient().when(context.getCurrentTimeNanos()).thenReturn(System.nanoTime());
    }

    @Test
    void initialize_ShouldSetState() {
        strategy = createStrategy(null);
        assertEquals(AlgorithmState.INITIALIZED, strategy.getState());
        assertTrue(strategy.getSymbols().contains(testSymbol));
    }

    @Test
    void getName_ShouldReturnVWAP() {
        strategy = createStrategy(null);
        assertEquals("VWAP", strategy.getName());
    }

    @Test
    void getDisplayName_WithCustomName_ShouldReturnCustomName() {
        strategy = createStrategy("My VWAP Strategy");
        assertEquals("My VWAP Strategy", strategy.getDisplayName());
    }

    @Test
    void getDisplayName_WithoutCustomName_ShouldReturnTypeName() {
        strategy = createStrategy(null);
        assertEquals("VWAP", strategy.getDisplayName());
    }

    @Test
    void start_ShouldChangeState() {
        strategy = createStrategy(null);
        strategy.start();
        assertEquals(AlgorithmState.RUNNING, strategy.getState());
    }

    @Test
    void getProgress_ShouldStartAtZero() {
        strategy = createStrategy(null);
        strategy.start();
        assertEquals(0, strategy.getProgress(), 0.01);
    }

    @Test
    void onFill_ShouldUpdateProgress() {
        StrategyParameters params = new StrategyParameters()
                .set("targetQuantity", 100L)
                .set("durationMinutes", 60L);
        strategy = new VwapStrategy(Set.of(testSymbol), params, null);
        strategy.initialize(context);
        strategy.start();

        // Fill half the target
        Trade fill = createFill(OrderSide.BUY, 50, 15000L);
        strategy.onFill(fill);

        assertEquals(50, strategy.getProgress(), 0.01);
    }

    @Test
    void onFill_WhenComplete_ShouldReach100Percent() {
        StrategyParameters params = new StrategyParameters()
                .set("targetQuantity", 100L)
                .set("durationMinutes", 60L);
        strategy = new VwapStrategy(Set.of(testSymbol), params, null);
        strategy.initialize(context);
        strategy.start();

        // Fill entire target
        Trade fill = createFill(OrderSide.BUY, 100, 15000L);
        strategy.onFill(fill);

        assertEquals(100, strategy.getProgress(), 0.01);
    }

    @Test
    void cancel_ShouldStopStrategy() {
        strategy = createStrategy(null);
        strategy.start();
        strategy.cancel();
        assertEquals(AlgorithmState.CANCELLED, strategy.getState());
    }

    private VwapStrategy createStrategy(String customName) {
        StrategyParameters params = new StrategyParameters()
                .set("targetQuantity", 1000L)
                .set("durationMinutes", 60L)
                .set("maxParticipationRate", 0.25);
        VwapStrategy s = new VwapStrategy(Set.of(testSymbol), params, customName);
        s.initialize(context);
        return s;
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
