package com.hft.engine.service;

import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class OrderManagerTest {

    private OrderManager orderManager;
    private Symbol testSymbol;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManager(256);
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @Test
    void createOrder_ShouldReturnOrderWithCorrectFields() {
        Order order = orderManager.createOrder(
                testSymbol,
                OrderSide.BUY,
                OrderType.LIMIT,
                100,
                15000L // $150.00 in cents
        );

        assertNotNull(order);
        assertEquals(testSymbol, order.getSymbol());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getType());
        assertEquals(100, order.getQuantity());
        assertEquals(15000L, order.getPrice());
    }

    @Test
    void trackOrder_ShouldStoreOrderForRetrieval() {
        Order order = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.MARKET, 50, 0);
        order.setClientOrderId(12345L);

        orderManager.trackOrder(order);

        Order retrieved = orderManager.getOrder(12345L);
        assertNotNull(retrieved);
        assertEquals(order, retrieved);
    }

    @Test
    void getOrder_WhenNotFound_ShouldReturnNull() {
        Order retrieved = orderManager.getOrder(99999L);
        assertNull(retrieved);
    }

    @Test
    void updateOrder_ShouldModifyExistingOrder() {
        Order order = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        order.setClientOrderId(12345L);
        orderManager.trackOrder(order);

        Order updatedOrder = new Order();
        updatedOrder.setClientOrderId(12345L);
        updatedOrder.setSymbol(testSymbol);
        updatedOrder.setSide(OrderSide.BUY);
        updatedOrder.setType(OrderType.LIMIT);
        updatedOrder.setQuantity(100);
        updatedOrder.setPrice(15000L);
        updatedOrder.setStatus(OrderStatus.ACCEPTED);
        updatedOrder.setExchangeOrderId("EX-123");

        orderManager.updateOrder(updatedOrder);

        Order retrieved = orderManager.getOrder(12345L);
        assertEquals(OrderStatus.ACCEPTED, retrieved.getStatus());
        assertEquals("EX-123", retrieved.getExchangeOrderId());
    }

    @Test
    void rejectOrder_ShouldSetStatusAndReason() {
        Order order = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        order.setClientOrderId(12345L);

        orderManager.rejectOrder(order, "Insufficient funds");

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals("Insufficient funds", order.getRejectReason());
    }

    @Test
    void getActiveOrders_ShouldReturnOnlyNonTerminalOrders() {
        // Create and track some orders with different statuses
        Order pendingOrder = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        pendingOrder.setClientOrderId(1L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        orderManager.trackOrder(pendingOrder);

        Order acceptedOrder = orderManager.createOrder(testSymbol, OrderSide.SELL, OrderType.LIMIT, 50, 15100L);
        acceptedOrder.setClientOrderId(2L);
        acceptedOrder.setStatus(OrderStatus.ACCEPTED);
        orderManager.trackOrder(acceptedOrder);

        Order filledOrder = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.MARKET, 25, 0);
        filledOrder.setClientOrderId(3L);
        filledOrder.setStatus(OrderStatus.FILLED);
        orderManager.trackOrder(filledOrder);

        Order cancelledOrder = orderManager.createOrder(testSymbol, OrderSide.SELL, OrderType.LIMIT, 75, 15200L);
        cancelledOrder.setClientOrderId(4L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        orderManager.trackOrder(cancelledOrder);

        Collection<Order> activeOrders = orderManager.getActiveOrders();

        assertEquals(2, activeOrders.size());
        assertTrue(activeOrders.stream().anyMatch(o -> o.getClientOrderId() == 1L));
        assertTrue(activeOrders.stream().anyMatch(o -> o.getClientOrderId() == 2L));
    }

    @Test
    void getActiveOrdersForSymbol_ShouldFilterBySymbol() {
        Symbol otherSymbol = new Symbol("GOOGL", Exchange.ALPACA);

        Order aaplOrder = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        aaplOrder.setClientOrderId(1L);
        aaplOrder.setStatus(OrderStatus.ACCEPTED);
        orderManager.trackOrder(aaplOrder);

        Order googlOrder = orderManager.createOrder(otherSymbol, OrderSide.SELL, OrderType.LIMIT, 50, 15000L);
        googlOrder.setClientOrderId(2L);
        googlOrder.setStatus(OrderStatus.ACCEPTED);
        orderManager.trackOrder(googlOrder);

        Collection<Order> aaplOrders = orderManager.getActiveOrders(testSymbol);

        assertEquals(1, aaplOrders.size());
        assertEquals(1L, aaplOrders.iterator().next().getClientOrderId());
    }

    @Test
    void getOrderByExchangeId_ShouldFindOrder() {
        Order order = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        order.setClientOrderId(1L);
        order.setExchangeOrderId("EX-12345");
        orderManager.trackOrder(order);

        Order found = orderManager.getOrderByExchangeId("EX-12345");

        assertNotNull(found);
        assertEquals(1L, found.getClientOrderId());
    }

    @Test
    void getOrderByExchangeId_WhenNotFound_ShouldReturnNull() {
        Order found = orderManager.getOrderByExchangeId("NON-EXISTENT");
        assertNull(found);
    }

    @Test
    void purgeCompletedOrders_ShouldRemoveTerminalOrders() {
        Order activeOrder = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        activeOrder.setClientOrderId(1L);
        activeOrder.setStatus(OrderStatus.ACCEPTED);
        orderManager.trackOrder(activeOrder);

        Order filledOrder = orderManager.createOrder(testSymbol, OrderSide.SELL, OrderType.MARKET, 50, 0);
        filledOrder.setClientOrderId(2L);
        filledOrder.setStatus(OrderStatus.FILLED);
        orderManager.trackOrder(filledOrder);

        Order cancelledOrder = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 25, 15000L);
        cancelledOrder.setClientOrderId(3L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        orderManager.trackOrder(cancelledOrder);

        int purged = orderManager.purgeCompletedOrders();

        assertEquals(2, purged);
        assertEquals(1, orderManager.getOrderCount());
        assertNotNull(orderManager.getOrder(1L));
        assertNull(orderManager.getOrder(2L));
        assertNull(orderManager.getOrder(3L));
    }

    @Test
    void clear_ShouldRemoveAllOrders() {
        orderManager.trackOrder(createTrackedOrder(1L, OrderStatus.ACCEPTED));
        orderManager.trackOrder(createTrackedOrder(2L, OrderStatus.FILLED));

        assertEquals(2, orderManager.getOrderCount());

        orderManager.clear();

        assertEquals(0, orderManager.getOrderCount());
    }

    @Test
    void orderListener_ShouldBeNotifiedOnTrack() {
        int[] callCount = {0};
        orderManager.addOrderListener(order -> callCount[0]++);

        orderManager.trackOrder(createTrackedOrder(1L, OrderStatus.PENDING));

        assertEquals(1, callCount[0]);
    }

    @Test
    void orderListener_ShouldBeNotifiedOnUpdate() {
        int[] callCount = {0};
        orderManager.addOrderListener(order -> callCount[0]++);

        Order order = createTrackedOrder(1L, OrderStatus.PENDING);
        orderManager.trackOrder(order);

        Order update = new Order();
        update.setClientOrderId(1L);
        update.setStatus(OrderStatus.ACCEPTED);
        orderManager.updateOrder(update);

        assertEquals(2, callCount[0]); // Once for track, once for update
    }

    @Test
    void removeOrderListener_ShouldStopNotifications() {
        int[] callCount = {0};
        java.util.function.Consumer<Order> listener = order -> callCount[0]++;

        orderManager.addOrderListener(listener);
        orderManager.trackOrder(createTrackedOrder(1L, OrderStatus.PENDING));
        assertEquals(1, callCount[0]);

        orderManager.removeOrderListener(listener);
        orderManager.trackOrder(createTrackedOrder(2L, OrderStatus.PENDING));
        assertEquals(1, callCount[0]); // Should not increase
    }

    private Order createTrackedOrder(long clientOrderId, OrderStatus status) {
        Order order = orderManager.createOrder(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        order.setClientOrderId(clientOrderId);
        order.setStatus(status);
        return order;
    }
}
