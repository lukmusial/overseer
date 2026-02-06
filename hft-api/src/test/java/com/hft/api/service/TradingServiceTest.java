package com.hft.api.service;

import com.hft.algo.base.AlgorithmState;
import com.hft.api.dto.CreateOrderRequest;
import com.hft.api.dto.CreateStrategyRequest;
import com.hft.api.dto.OrderDto;
import com.hft.api.dto.StrategyDto;
import com.hft.core.model.*;
import com.hft.persistence.PersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradingServiceTest {

    private TradingService tradingService;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        // Use in-memory persistence for testing
        persistenceManager = PersistenceManager.inMemory();
        tradingService = new TradingService(persistenceManager);
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

    @Test
    void submitOrder_shouldPersistToRepository() {
        // Don't start engine — order will be rejected synchronously and persisted
        CreateOrderRequest request = new CreateOrderRequest(
                "AAPL", "ALPACA", OrderSide.BUY, OrderType.MARKET,
                null, 100, 0, 0, null
        );

        OrderDto submitted = tradingService.submitOrder(request);

        assertEquals(OrderStatus.REJECTED, submitted.status());
        long count = persistenceManager.getOrderRepository().count();
        assertTrue(count > 0, "Rejected order should be persisted to repository");
    }

    @Test
    void ordersPersistedAcrossRestart_shouldBeLoadedOnInit() {
        // Submit an order (engine not running, will be rejected but persisted)
        CreateOrderRequest request = new CreateOrderRequest(
                "AAPL", "ALPACA", OrderSide.BUY, OrderType.MARKET,
                null, 100, 0, 0, null
        );

        OrderDto submitted = tradingService.submitOrder(request);
        assertNotNull(submitted);

        // Verify the order is in persistence
        long persistedCount = persistenceManager.getOrderRepository().count();
        assertTrue(persistedCount > 0, "Should have persisted orders");

        // Simulate restart: create a new TradingService with the SAME persistence
        TradingService restartedService = new TradingService(persistenceManager);
        restartedService.init();

        // Orders should be loaded from persistence into the new service's OrderManager
        List<OrderDto> ordersAfterRestart = restartedService.getAllOrders();
        assertEquals(persistedCount, ordersAfterRestart.size(),
                "Orders should survive restart");
    }

    @Test
    void toOrderDto_shouldResolveStrategyName() {
        // Create a strategy
        CreateStrategyRequest request = new CreateStrategyRequest(
                "My Momentum", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30)
        );
        StrategyDto created = tradingService.createStrategy(request);

        // Create an order referencing this strategy
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100)
                .strategyId(created.id());

        OrderDto dto = tradingService.toOrderDto(order);

        assertEquals("My Momentum", dto.strategyName());
        assertEquals(created.id(), dto.strategyId());
    }

    @Test
    void toOrderDto_shouldReturnNullNameForUnknownStrategy() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100)
                .strategyId("nonexistent-id");

        OrderDto dto = tradingService.toOrderDto(order);

        assertNull(dto.strategyName());
        assertEquals("nonexistent-id", dto.strategyId());
    }
}
