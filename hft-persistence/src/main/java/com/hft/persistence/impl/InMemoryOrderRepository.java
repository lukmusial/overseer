package com.hft.persistence.impl;

import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.persistence.OrderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory implementation of OrderRepository.
 * Suitable for testing and single-session use.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> ordersByClientId = new ConcurrentHashMap<>();
    private final Map<String, Order> ordersByExchangeId = new ConcurrentHashMap<>();
    private final Deque<Order> recentOrders = new ConcurrentLinkedDeque<>();
    private final int maxRecentOrders;

    public InMemoryOrderRepository() {
        this(10_000);
    }

    public InMemoryOrderRepository(int maxRecentOrders) {
        this.maxRecentOrders = maxRecentOrders;
    }

    @Override
    public void save(Order order) {
        long clientOrderId = order.getClientOrderId();
        Order existing = ordersByClientId.put(clientOrderId, order);
        if (existing == null) {
            // New order
            recentOrders.addFirst(order);
            while (recentOrders.size() > maxRecentOrders) {
                recentOrders.removeLast();
            }
        }

        String exchangeOrderId = order.getExchangeOrderId();
        if (exchangeOrderId != null) {
            ordersByExchangeId.put(exchangeOrderId, order);
        }
    }

    @Override
    public Optional<Order> findByClientOrderId(long clientOrderId) {
        return Optional.ofNullable(ordersByClientId.get(clientOrderId));
    }

    @Override
    public Optional<Order> findByExchangeOrderId(String exchangeOrderId) {
        return Optional.ofNullable(ordersByExchangeId.get(exchangeOrderId));
    }

    @Override
    public List<Order> findBySymbol(Symbol symbol) {
        return ordersByClientId.values().stream()
                .filter(o -> symbol.equals(o.getSymbol()))
                .sorted(Comparator.comparingLong(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return ordersByClientId.values().stream()
                .filter(o -> status == o.getStatus())
                .sorted(Comparator.comparingLong(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDate(int dateYYYYMMDD) {
        LocalDate date = parseDate(dateYYYYMMDD);
        long startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;
        long endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;

        return ordersByClientId.values().stream()
                .filter(o -> o.getCreatedAt() >= startOfDay && o.getCreatedAt() < endOfDay)
                .sorted(Comparator.comparingLong(Order::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> getRecentOrders(int count) {
        return recentOrders.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> getActiveOrders() {
        return ordersByClientId.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING ||
                        o.getStatus() == OrderStatus.SUBMITTED ||
                        o.getStatus() == OrderStatus.ACCEPTED ||
                        o.getStatus() == OrderStatus.PARTIALLY_FILLED)
                .sorted(Comparator.comparingLong(Order::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(long clientOrderId) {
        Order order = ordersByClientId.remove(clientOrderId);
        if (order != null && order.getExchangeOrderId() != null) {
            ordersByExchangeId.remove(order.getExchangeOrderId());
        }
        recentOrders.remove(order);
    }

    @Override
    public void clear() {
        ordersByClientId.clear();
        ordersByExchangeId.clear();
        recentOrders.clear();
    }

    @Override
    public long count() {
        return ordersByClientId.size();
    }

    @Override
    public void flush() {
        // No-op for in-memory
    }

    @Override
    public void close() {
        // No-op for in-memory
    }

    private LocalDate parseDate(int dateYYYYMMDD) {
        int year = dateYYYYMMDD / 10000;
        int month = (dateYYYYMMDD % 10000) / 100;
        int day = dateYYYYMMDD % 100;
        return LocalDate.of(year, month, day);
    }
}
