package com.hft.persistence.impl;

import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository repository;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        symbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @Test
    void shouldSaveAndFindByClientOrderId() {
        Order order = createOrder();
        long orderId = order.getClientOrderId();
        repository.save(order);

        Optional<Order> found = repository.findByClientOrderId(orderId);

        assertTrue(found.isPresent());
        assertEquals(orderId, found.get().getClientOrderId());
    }

    @Test
    void shouldFindByExchangeOrderId() {
        Order order = createOrder();
        order.markSubmitted();
        order.markAccepted("EX-001");
        repository.save(order);

        Optional<Order> found = repository.findByExchangeOrderId("EX-001");

        assertTrue(found.isPresent());
        assertEquals("EX-001", found.get().getExchangeOrderId());
    }

    @Test
    void shouldFindBySymbol() {
        Order order1 = createOrder();
        Order order2 = createOrder();
        Symbol other = new Symbol("MSFT", Exchange.ALPACA);
        Order order3 = new Order()
                .symbol(other)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(100)
                .price(30000);

        repository.save(order1);
        repository.save(order2);
        repository.save(order3);

        List<Order> found = repository.findBySymbol(symbol);

        assertEquals(2, found.size());
    }

    @Test
    void shouldFindByStatus() {
        Order pending = createOrder();
        Order accepted = createOrder();
        accepted.markSubmitted();
        accepted.markAccepted("EX-002");

        repository.save(pending);
        repository.save(accepted);

        List<Order> foundPending = repository.findByStatus(OrderStatus.PENDING);
        List<Order> foundAccepted = repository.findByStatus(OrderStatus.ACCEPTED);

        assertEquals(1, foundPending.size());
        assertEquals(1, foundAccepted.size());
    }

    @Test
    void shouldGetActiveOrders() {
        Order pending = createOrder();
        Order filled = createOrder();
        filled.markSubmitted();
        filled.markAccepted("EX-002");
        filled.markFilled(100, 15000);

        repository.save(pending);
        repository.save(filled);

        List<Order> active = repository.getActiveOrders();

        assertEquals(1, active.size());
        assertEquals(pending.getClientOrderId(), active.get(0).getClientOrderId());
    }

    @Test
    void shouldGetRecentOrders() {
        for (int i = 0; i < 5; i++) {
            repository.save(createOrder());
        }

        List<Order> recent = repository.getRecentOrders(3);

        assertEquals(3, recent.size());
    }

    @Test
    void shouldDeleteOrder() {
        Order order = createOrder();
        long orderId = order.getClientOrderId();
        repository.save(order);

        repository.delete(orderId);

        assertTrue(repository.findByClientOrderId(orderId).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    void shouldClearAllOrders() {
        repository.save(createOrder());
        repository.save(createOrder());

        repository.clear();

        assertEquals(0, repository.count());
    }

    @Test
    void shouldUpdateExistingOrder() {
        Order order = createOrder();
        long orderId = order.getClientOrderId();
        repository.save(order);

        order.markSubmitted();
        repository.save(order);

        Optional<Order> found = repository.findByClientOrderId(orderId);
        assertTrue(found.isPresent());
        assertEquals(OrderStatus.SUBMITTED, found.get().getStatus());

        // Count should still be 1
        assertEquals(1, repository.count());
    }

    private Order createOrder() {
        return new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(100)
                .price(15000);
    }
}
