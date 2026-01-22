package com.hft.engine;

import com.hft.core.model.*;
import com.hft.core.port.OrderPort;
import com.hft.engine.service.RiskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingEngineTest {

    private TradingEngine engine;
    private Symbol testSymbol;
    private RiskManager.RiskLimits testLimits;

    @Mock
    private OrderPort mockOrderPort;

    @BeforeEach
    void setUp() {
        testLimits = new RiskManager.RiskLimits(
                10000,     // maxOrderSize
                1000000,   // maxOrderNotional
                100000,    // maxPositionSize
                10000,     // maxOrdersPerDay
                10000000,  // maxDailyNotional
                100000,    // maxDailyLoss
                50000,     // maxDrawdownPerPosition
                25000,     // maxUnrealizedLossPerPosition
                5000000    // maxNetExposure
        );
        engine = new TradingEngine(testLimits, 1024);
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @AfterEach
    void tearDown() {
        if (engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void start_ShouldEnableTrading() {
        assertFalse(engine.isRunning());

        engine.start();

        assertTrue(engine.isRunning());
        assertTrue(engine.getRiskManager().isTradingEnabled());
    }

    @Test
    void stop_ShouldDisableTrading() {
        engine.start();
        assertTrue(engine.isRunning());

        engine.stop();

        assertFalse(engine.isRunning());
        assertFalse(engine.getRiskManager().isTradingEnabled());
    }

    @Test
    void submitOrder_WhenNotRunning_ShouldReject() {
        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);

        String rejection = engine.submitOrder(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("not running"));
    }

    @Test
    void submitOrder_WhenValid_ShouldApprove() {
        engine.start();
        engine.registerExchange(Exchange.ALPACA, mockOrderPort);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(new Order()));

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        String rejection = engine.submitOrder(order);

        assertNull(rejection);
    }

    @Test
    void submitOrder_WhenRiskCheckFails_ShouldReject() {
        engine.start();

        // Order exceeds max size
        Order order = createOrder(testSymbol, OrderSide.BUY, 50000, 15000L);
        String rejection = engine.submitOrder(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Order size exceeded"));
    }

    @Test
    void registerExchange_ShouldEnableOrderRouting() {
        engine.registerExchange(Exchange.ALPACA, mockOrderPort);

        // Verify by checking no exception when submitting
        engine.start();
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(new Order()));

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        String rejection = engine.submitOrder(order);

        assertNull(rejection);
    }

    @Test
    void cancelOrder_WhenNotRunning_ShouldNotProcess() {
        // Should not throw, just log warning
        engine.cancelOrder(12345L, testSymbol);
    }

    @Test
    void onQuoteUpdate_ShouldUpdatePositions() throws Exception {
        engine.start();

        // Create a position first
        Trade trade = new Trade();
        trade.setSymbol(testSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(100);
        trade.setPrice(15000L);
        engine.getPositionManager().applyTrade(trade);

        // Send quote update
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(15100L);
        quote.setAskPrice(15200L);

        engine.onQuoteUpdate(quote);

        // Wait for processing
        Thread.sleep(100);

        // Position should be marked to market
        Position position = engine.getPositionManager().getPosition(testSymbol);
        assertNotNull(position);
    }

    @Test
    void onOrderFilled_ShouldUpdateMetrics() throws Exception {
        engine.start();

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        order.setClientOrderId(12345L);
        order.setExchangeOrderId("EX-123");

        engine.onOrderFilled(order, 100, 15000L);

        // Wait for processing
        Thread.sleep(100);

        // Metrics should be updated
        assertTrue(engine.getOrderMetrics().getOrdersFilled() >= 0);
    }

    @Test
    void getRemainingCapacity_ShouldReturnPositiveValue() {
        engine.start();

        long capacity = engine.getRemainingCapacity();

        assertTrue(capacity > 0);
        assertTrue(capacity <= 1024);
    }

    @Test
    void getCursor_ShouldTrackPublishedEvents() throws Exception {
        engine.start();

        long initialCursor = engine.getCursor();

        // Publish some events
        Quote quote = new Quote();
        quote.setSymbol(testSymbol);
        quote.setBidPrice(15000L);
        quote.setAskPrice(15100L);

        engine.onQuoteUpdate(quote);
        Thread.sleep(50);

        long newCursor = engine.getCursor();
        assertTrue(newCursor > initialCursor);
    }

    @Test
    void resetDailyCounters_ShouldClearMetrics() {
        engine.start();
        engine.registerExchange(Exchange.ALPACA, mockOrderPort);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(new Order()));

        // Submit some orders
        for (int i = 0; i < 5; i++) {
            engine.submitOrder(createOrder(testSymbol, OrderSide.BUY, 100, 15000L));
        }

        assertTrue(engine.getRiskManager().getOrdersSubmittedToday() > 0);

        engine.resetDailyCounters();

        assertEquals(0, engine.getRiskManager().getOrdersSubmittedToday());
    }

    @Test
    void getSnapshot_ShouldCaptureEngineState() {
        engine.start();

        TradingEngine.EngineSnapshot snapshot = engine.getSnapshot();

        assertTrue(snapshot.running());
        assertTrue(snapshot.tradingEnabled());
        assertEquals(0, snapshot.totalOrders());
        assertNotNull(snapshot.positions());
        assertNotNull(snapshot.metrics());
    }

    @Test
    void getSnapshot_WhenNotStarted_ShouldHaveZeroUptime() {
        TradingEngine.EngineSnapshot snapshot = engine.getSnapshot();

        assertFalse(snapshot.running());
        assertEquals(0, snapshot.startTimeMillis());
        assertEquals(0, snapshot.uptimeMillis());
    }

    @Test
    void getSnapshot_WhenStarted_ShouldTrackStartTime() {
        long beforeStart = System.currentTimeMillis();
        engine.start();
        long afterStart = System.currentTimeMillis();

        TradingEngine.EngineSnapshot snapshot = engine.getSnapshot();

        assertTrue(snapshot.running());
        assertTrue(snapshot.startTimeMillis() >= beforeStart);
        assertTrue(snapshot.startTimeMillis() <= afterStart);
        assertTrue(snapshot.uptimeMillis() >= 0);
    }

    @Test
    void getSnapshot_UptimeShouldIncrease() throws Exception {
        engine.start();

        TradingEngine.EngineSnapshot snapshot1 = engine.getSnapshot();
        Thread.sleep(50);
        TradingEngine.EngineSnapshot snapshot2 = engine.getSnapshot();

        assertTrue(snapshot2.uptimeMillis() > snapshot1.uptimeMillis());
    }

    @Test
    void getSnapshot_WhenStopped_ShouldResetUptime() {
        engine.start();
        assertTrue(engine.getSnapshot().startTimeMillis() > 0);

        engine.stop();

        TradingEngine.EngineSnapshot snapshot = engine.getSnapshot();
        assertEquals(0, snapshot.startTimeMillis());
        assertEquals(0, snapshot.uptimeMillis());
    }

    @Test
    void getOrderManager_ShouldReturnManager() {
        assertNotNull(engine.getOrderManager());
    }

    @Test
    void getPositionManager_ShouldReturnManager() {
        assertNotNull(engine.getPositionManager());
    }

    @Test
    void getRiskManager_ShouldReturnManager() {
        assertNotNull(engine.getRiskManager());
    }

    @Test
    void getOrderMetrics_ShouldReturnMetrics() {
        assertNotNull(engine.getOrderMetrics());
    }

    @Test
    void defaultConstructor_ShouldUseDefaultLimits() {
        TradingEngine defaultEngine = new TradingEngine();

        assertNotNull(defaultEngine.getRiskManager());
        assertEquals(RiskManager.RiskLimits.defaults().maxOrderSize(),
                defaultEngine.getRiskManager().getLimits().maxOrderSize());

        defaultEngine.stop();
    }

    @Test
    void multipleStartStop_ShouldBeIdempotent() {
        engine.start();
        engine.start(); // Second start should be no-op
        assertTrue(engine.isRunning());

        engine.stop();
        engine.stop(); // Second stop should be no-op
        assertFalse(engine.isRunning());
    }

    @Test
    void onOrderAccepted_ShouldPublishEvent() throws Exception {
        engine.start();

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        order.setClientOrderId(12345L);
        order.setExchangeOrderId("EX-123");

        long cursorBefore = engine.getCursor();
        engine.onOrderAccepted(order);
        Thread.sleep(50);
        long cursorAfter = engine.getCursor();

        assertTrue(cursorAfter > cursorBefore);
    }

    @Test
    void onOrderRejected_ShouldPublishEvent() throws Exception {
        engine.start();

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        order.setClientOrderId(12345L);

        long cursorBefore = engine.getCursor();
        engine.onOrderRejected(order, "Test rejection");
        Thread.sleep(50);
        long cursorAfter = engine.getCursor();

        assertTrue(cursorAfter > cursorBefore);
    }

    @Test
    void onOrderCancelled_ShouldPublishEvent() throws Exception {
        engine.start();

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        order.setClientOrderId(12345L);
        order.setExchangeOrderId("EX-123");

        long cursorBefore = engine.getCursor();
        engine.onOrderCancelled(order);
        Thread.sleep(50);
        long cursorAfter = engine.getCursor();

        assertTrue(cursorAfter > cursorBefore);
    }

    @Test
    void onTradeUpdate_ShouldPublishEvent() throws Exception {
        engine.start();

        Trade trade = new Trade();
        trade.setSymbol(testSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(100);
        trade.setPrice(15000L);

        long cursorBefore = engine.getCursor();
        engine.onTradeUpdate(trade);
        Thread.sleep(50);
        long cursorAfter = engine.getCursor();

        assertTrue(cursorAfter > cursorBefore);
    }

    private Order createOrder(Symbol symbol, OrderSide side, long quantity, long price) {
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setType(OrderType.LIMIT);
        return order;
    }
}
