package com.hft.core.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    private Order order;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        order = new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.DAY)
                .price(15000) // $150.00
                .quantity(100);
    }

    @Test
    void shouldCreateOrderWithUniqueId() {
        Order order1 = new Order();
        Order order2 = new Order();

        assertNotEquals(order1.getClientOrderId(), order2.getClientOrderId());
    }

    @Test
    void shouldStartInPendingStatus() {
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void shouldTrackSubmitLatency() {
        // Simulate some delay
        long beforeSubmit = System.nanoTime();
        order.markSubmitted();
        long afterSubmit = System.nanoTime();

        assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        assertTrue(order.getSubmitLatencyNanos() > 0);
        assertTrue(order.getSubmittedAt() >= beforeSubmit);
        assertTrue(order.getSubmittedAt() <= afterSubmit);
    }

    @Test
    void shouldTrackAckLatency() {
        order.markSubmitted();
        order.markAccepted("EX123");

        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals("EX123", order.getExchangeOrderId());
        assertTrue(order.getAckLatencyNanos() >= 0);
    }

    @Test
    void shouldHandlePartialFill() {
        order.markSubmitted();
        order.markAccepted("EX123");
        order.markPartiallyFilled(50, 15010); // 50 shares at $150.10

        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        assertEquals(50, order.getFilledQuantity());
        assertEquals(50, order.getRemainingQuantity());
        assertEquals(15010, order.getAverageFilledPrice());
    }

    @Test
    void shouldCalculateAveragePriceOnMultipleFills() {
        order.markSubmitted();
        order.markAccepted("EX123");
        order.markPartiallyFilled(50, 15000); // 50 shares at $150.00
        order.markPartiallyFilled(30, 15100); // 30 shares at $151.00

        assertEquals(80, order.getFilledQuantity());
        assertEquals(20, order.getRemainingQuantity());
        // Average = (50*15000 + 30*15100) / 80 = (750000 + 453000) / 80 = 15037.5
        assertTrue(order.getAverageFilledPrice() > 15000);
        assertTrue(order.getAverageFilledPrice() < 15100);
    }

    @Test
    void shouldMarkFilled() {
        order.markSubmitted();
        order.markAccepted("EX123");
        order.markFilled(100, 15000);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(100, order.getFilledQuantity());
        assertEquals(0, order.getRemainingQuantity());
        assertTrue(order.getFillLatencyNanos() >= 0);
    }

    @Test
    void shouldMarkCancelled() {
        order.markSubmitted();
        order.markAccepted("EX123");
        order.markCancelled();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getStatus().isTerminal());
    }

    @Test
    void shouldMarkRejected() {
        order.markSubmitted();
        order.markRejected();

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertTrue(order.getStatus().isTerminal());
    }

    @Test
    void shouldResetForReuse() {
        order.markSubmitted();
        order.markAccepted("EX123");
        order.markPartiallyFilled(50, 15000);

        long originalId = order.getClientOrderId();
        order.reset();

        assertNotEquals(originalId, order.getClientOrderId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNull(order.getExchangeOrderId());
        assertNull(order.getSymbol());
        assertEquals(0, order.getFilledQuantity());
    }

    @Test
    void shouldCopyFromAnotherOrder() {
        order.markSubmitted();
        order.markAccepted("EX123");

        Order copy = new Order();
        copy.copyFrom(order);

        assertEquals(order.getClientOrderId(), copy.getClientOrderId());
        assertEquals(order.getExchangeOrderId(), copy.getExchangeOrderId());
        assertEquals(order.getSymbol(), copy.getSymbol());
        assertEquals(order.getStatus(), copy.getStatus());
    }

    @Test
    void shouldConvertPriceToDouble() {
        order.price(15050);
        assertEquals(150.50, order.getPriceAsDouble(), 0.001);
    }

    @Test
    void shouldFluentBuilderPattern() {
        Order built = new Order()
                .symbol(symbol)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .quantity(200)
                .strategyId("VWAP-1")
                .algorithmId("VWAP");

        assertEquals(symbol, built.getSymbol());
        assertEquals(OrderSide.SELL, built.getSide());
        assertEquals(OrderType.MARKET, built.getType());
        assertEquals(200, built.getQuantity());
        assertEquals("VWAP-1", built.getStrategyId());
        assertEquals("VWAP", built.getAlgorithmId());
    }
}
