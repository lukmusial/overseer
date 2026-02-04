package com.hft.api.service;

import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.core.port.OrderPort;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Stub OrderPort that immediately fills orders at their limit price.
 * Used in stub mode so strategies can execute without a real exchange.
 */
public class StubOrderPort implements OrderPort {

    private final AtomicLong exchangeOrderIdSeq = new AtomicLong(1);

    @Override
    public CompletableFuture<Order> submitOrder(Order order) {
        // Simulate immediate fill at the order price
        order.setExchangeOrderId("STUB-" + exchangeOrderIdSeq.getAndIncrement());
        order.setStatus(OrderStatus.FILLED);
        order.setFilledQuantity(order.getQuantity());
        order.setAverageFilledPrice(order.getPrice());
        return CompletableFuture.completedFuture(order);
    }

    @Override
    public CompletableFuture<Order> cancelOrder(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        return CompletableFuture.completedFuture(order);
    }

    @Override
    public CompletableFuture<Order> modifyOrder(Order order) {
        return CompletableFuture.completedFuture(order);
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrder(long clientOrderId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrderByExchangeId(String exchangeOrderId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Order>> getOpenOrders() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Order>> getOpenOrders(Symbol symbol) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders(Symbol symbol) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addOrderListener(Consumer<OrderUpdate> listener) {
        // No-op for stub
    }

    @Override
    public void removeOrderListener(Consumer<OrderUpdate> listener) {
        // No-op for stub
    }
}
