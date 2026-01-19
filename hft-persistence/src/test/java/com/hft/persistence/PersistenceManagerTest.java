package com.hft.persistence;

import com.hft.core.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    private PersistenceManager persistence;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        persistence = PersistenceManager.inMemory();
        symbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void shouldRecordTrade() {
        Trade trade = createTrade(100, 15000);

        persistence.recordTrade(trade);

        assertEquals(1, persistence.getTradeJournal().getTotalTradeCount());
    }

    @Test
    void shouldSaveOrder() {
        Order order = createOrder();
        long orderId = order.getClientOrderId();

        persistence.saveOrder(order);

        assertTrue(persistence.getOrderRepository()
                .findByClientOrderId(orderId).isPresent());
    }

    @Test
    void shouldLogOrderSubmitted() {
        Order order = createOrder();

        persistence.logOrderSubmitted(order);

        List<AuditLog.AuditEvent> events = persistence.getAuditLog().getRecentEvents(10);
        assertFalse(events.isEmpty());
        assertEquals(AuditLog.EventType.ORDER_SUBMITTED, events.get(0).type());
    }

    @Test
    void shouldLogOrderRejected() {
        Order order = createOrder();

        persistence.logOrderRejected(order, "Test rejection");

        List<AuditLog.AuditEvent> events = persistence.getAuditLog().getRecentEvents(10);
        assertFalse(events.isEmpty());
        assertEquals(AuditLog.EventType.ORDER_REJECTED, events.get(0).type());
    }

    @Test
    void shouldLogEngineLifecycle() {
        persistence.logEngineStarted();
        persistence.logEngineStopped();

        List<AuditLog.AuditEvent> events = persistence.getAuditLog().getRecentEvents(10);
        assertEquals(2, events.size());

        // Most recent first
        assertEquals(AuditLog.EventType.ENGINE_STOPPED, events.get(0).type());
        assertEquals(AuditLog.EventType.ENGINE_STARTED, events.get(1).type());
    }

    @Test
    void shouldLogStrategyLifecycle() {
        persistence.logStrategyStarted("VWAP-001");
        persistence.logStrategyStopped("VWAP-001");

        List<AuditLog.AuditEvent> events = persistence.getAuditLog().getRecentEvents(10);
        assertEquals(2, events.size());

        assertEquals(AuditLog.EventType.STRATEGY_STOPPED, events.get(0).type());
        assertTrue(events.get(0).message().contains("VWAP-001"));
    }

    @Test
    void shouldLogRiskCheckFailed() {
        persistence.logRiskCheckFailed("123456789", "Position limit exceeded");

        List<AuditLog.AuditEvent> events = persistence.getAuditLog()
                .getEventsByType(AuditLog.EventType.RISK_CHECK_FAILED, 10);

        assertEquals(1, events.size());
        assertTrue(events.get(0).details().contains("Position limit exceeded"));
    }

    @Test
    void shouldLogErrors() {
        persistence.logError("Test error", new RuntimeException("Test exception"));

        List<AuditLog.AuditEvent> events = persistence.getAuditLog()
                .getEventsByType(AuditLog.EventType.ERROR, 10);

        assertEquals(1, events.size());
        assertTrue(events.get(0).details().contains("RuntimeException"));
    }

    private Order createOrder() {
        return new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(100)
                .price(15000);
    }

    private Trade createTrade(long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
