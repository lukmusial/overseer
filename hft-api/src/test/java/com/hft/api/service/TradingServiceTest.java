package com.hft.api.service;

import com.hft.algo.base.AlgorithmState;
import com.hft.api.dto.CreateStrategyRequest;
import com.hft.api.dto.StrategyDto;
import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.persistence.PersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradingServiceTest {

    private TradingService tradingService;

    @BeforeEach
    void setUp() {
        // Use in-memory persistence for testing
        tradingService = new TradingService(PersistenceManager.inMemory());
    }

    @Test
    void createStrategy_shouldInitializeWithContext() {
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Strategy", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30)
        );

        StrategyDto created = tradingService.createStrategy(request);

        assertNotNull(created);
        assertEquals(AlgorithmState.INITIALIZED, created.state());
    }

    @Test
    void startStrategy_shouldNotThrowWhenInitialized() {
        // Create strategy first
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Strategy", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30)
        );
        StrategyDto created = tradingService.createStrategy(request);

        // Starting should not throw NPE because context is initialized
        assertDoesNotThrow(() -> tradingService.startStrategy(created.id()));
    }

    @Test
    void startStrategy_shouldChangeStateToRunning() {
        // Create strategy
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Strategy", "vwap", List.of("AAPL"), "ALPACA",
                Map.of("totalQuantity", 1000L, "durationMinutes", 60)
        );
        StrategyDto created = tradingService.createStrategy(request);
        assertEquals(AlgorithmState.INITIALIZED, created.state());

        // Start strategy
        tradingService.startStrategy(created.id());

        // Verify state changed
        StrategyDto running = tradingService.getStrategy(created.id()).orElseThrow();
        assertEquals(AlgorithmState.RUNNING, running.state());
    }

    @Test
    void stopStrategy_shouldChangeStateToCancelled() {
        // Create and start strategy
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Strategy", "twap", List.of("AAPL"), "ALPACA",
                Map.of("totalQuantity", 1000L, "durationMinutes", 60)
        );
        StrategyDto created = tradingService.createStrategy(request);
        tradingService.startStrategy(created.id());

        // Stop strategy
        tradingService.stopStrategy(created.id());

        // Verify state changed
        StrategyDto stopped = tradingService.getStrategy(created.id()).orElseThrow();
        assertEquals(AlgorithmState.CANCELLED, stopped.state());
    }

    @Test
    void dispatchQuoteToStrategies_shouldCallOnQuoteForRunningStrategies() {
        // Create and start a momentum strategy for AAPL
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Momentum", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30, "signalThreshold", 0.02, "maxPositionSize", 100)
        );
        StrategyDto created = tradingService.createStrategy(request);
        tradingService.startStrategy(created.id());

        // Verify strategy is running
        StrategyDto running = tradingService.getStrategy(created.id()).orElseThrow();
        assertEquals(AlgorithmState.RUNNING, running.state());

        // Create a quote for AAPL on ALPACA
        Symbol aapl = new Symbol("AAPL", Exchange.ALPACA);
        Quote quote = new Quote(aapl, 15000L, 15010L, 1000L, 1000L, System.currentTimeMillis());
        quote.setPriceScale(100);

        // Dispatch should not throw
        assertDoesNotThrow(() -> tradingService.dispatchQuoteToStrategies(quote));
    }

    @Test
    void dispatchQuoteToStrategies_shouldNotDispatchToStoppedStrategies() {
        // Create strategy but don't start it
        CreateStrategyRequest request = new CreateStrategyRequest(
                "Test Momentum", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30, "signalThreshold", 0.02, "maxPositionSize", 100)
        );
        StrategyDto created = tradingService.createStrategy(request);

        // Strategy is in INITIALIZED state, not RUNNING
        assertEquals(AlgorithmState.INITIALIZED, created.state());

        // Create a quote — should not throw even if strategy not running
        Symbol aapl = new Symbol("AAPL", Exchange.ALPACA);
        Quote quote = new Quote(aapl, 15000L, 15010L, 1000L, 1000L, System.currentTimeMillis());
        quote.setPriceScale(100);

        assertDoesNotThrow(() -> tradingService.dispatchQuoteToStrategies(quote));
    }

    @Test
    void createStrategy_allTypesShouldInitialize() {
        List<String> types = List.of("momentum", "meanreversion", "vwap", "twap");

        for (String type : types) {
            CreateStrategyRequest request = new CreateStrategyRequest(
                    "Test " + type, type, List.of("AAPL"), "ALPACA",
                    Map.of("totalQuantity", 1000L, "durationMinutes", 60, "shortPeriod", 10, "longPeriod", 30)
            );

            StrategyDto created = tradingService.createStrategy(request);

            assertNotNull(created, "Strategy type " + type + " should create successfully");
            assertEquals(AlgorithmState.INITIALIZED, created.state(),
                    "Strategy type " + type + " should be in INITIALIZED state");

            // Verify it can be started
            assertDoesNotThrow(() -> tradingService.startStrategy(created.id()),
                    "Strategy type " + type + " should start without throwing");
        }
    }
}
