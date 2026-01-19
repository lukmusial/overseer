package com.hft.engine.service;

import com.hft.core.model.*;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Manages order lifecycle and tracking.
 * Thread-safe order storage using Agrona primitive collections.
 */
public class OrderManager {
    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);

    private final Long2ObjectHashMap<Order> ordersByClientId;
    private final ObjectPool<Order> orderPool;
    private final AtomicLong orderIdGenerator;
    private final List<Consumer<Order>> orderListeners;

    public OrderManager() {
        this(1024);
    }

    public OrderManager(int initialCapacity) {
        this.ordersByClientId = new Long2ObjectHashMap<>(initialCapacity, 0.65f);
        this.orderPool = new ObjectPool<>(Order::new, initialCapacity);
        this.orderPool.preallocate(initialCapacity / 2);
        this.orderIdGenerator = new AtomicLong(System.nanoTime());
        this.orderListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Creates a new order with auto-generated client order ID.
     */
    public Order createOrder(Symbol symbol, OrderSide side, OrderType type, long quantity, long price) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(type);
        order.setQuantity(quantity);
        order.setPrice(price);
        return order;
    }

    /**
     * Tracks an order in the order book.
     */
    public void trackOrder(Order order) {
        synchronized (ordersByClientId) {
            ordersByClientId.put(order.getClientOrderId(), order);
        }
        log.debug("Tracking order: {}", order.getClientOrderId());
        notifyListeners(order);
    }

    /**
     * Updates an existing order.
     */
    public void updateOrder(Order updatedOrder) {
        synchronized (ordersByClientId) {
            Order existing = ordersByClientId.get(updatedOrder.getClientOrderId());
            if (existing != null) {
                existing.copyFrom(updatedOrder);
                notifyListeners(existing);
            } else {
                ordersByClientId.put(updatedOrder.getClientOrderId(), updatedOrder);
                notifyListeners(updatedOrder);
            }
        }
    }

    /**
     * Marks an order as rejected.
     */
    public void rejectOrder(Order order, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason(reason);
        synchronized (ordersByClientId) {
            ordersByClientId.put(order.getClientOrderId(), order);
        }
        log.warn("Order rejected: {} - {}", order.getClientOrderId(), reason);
        notifyListeners(order);
    }

    /**
     * Gets an order by client order ID.
     */
    public Order getOrder(long clientOrderId) {
        synchronized (ordersByClientId) {
            return ordersByClientId.get(clientOrderId);
        }
    }

    /**
     * Gets an order by exchange order ID.
     */
    public Order getOrderByExchangeId(String exchangeOrderId) {
        if (exchangeOrderId == null) {
            return null;
        }
        synchronized (ordersByClientId) {
            for (Order order : ordersByClientId.values()) {
                if (exchangeOrderId.equals(order.getExchangeOrderId())) {
                    return order;
                }
            }
        }
        return null;
    }

    /**
     * Gets all active (non-terminal) orders.
     */
    public Collection<Order> getActiveOrders() {
        List<Order> active = new java.util.ArrayList<>();
        synchronized (ordersByClientId) {
            for (Order order : ordersByClientId.values()) {
                if (order.getStatus() != null && !order.getStatus().isTerminal()) {
                    active.add(order);
                }
            }
        }
        return active;
    }

    /**
     * Gets all active orders for a symbol.
     */
    public Collection<Order> getActiveOrders(Symbol symbol) {
        List<Order> active = new java.util.ArrayList<>();
        synchronized (ordersByClientId) {
            for (Order order : ordersByClientId.values()) {
                if (order.getSymbol() != null && order.getSymbol().equals(symbol)
                        && order.getStatus() != null && !order.getStatus().isTerminal()) {
                    active.add(order);
                }
            }
        }
        return active;
    }

    /**
     * Removes completed orders from tracking (cleanup).
     */
    public int purgeCompletedOrders() {
        int purged = 0;
        synchronized (ordersByClientId) {
            var iterator = ordersByClientId.values().iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.getStatus() != null && order.getStatus().isTerminal()) {
                    iterator.remove();
                    orderPool.release(order);
                    purged++;
                }
            }
        }
        log.debug("Purged {} completed orders", purged);
        return purged;
    }

    /**
     * Gets total number of tracked orders.
     */
    public int getOrderCount() {
        synchronized (ordersByClientId) {
            return ordersByClientId.size();
        }
    }

    /**
     * Registers a listener for order updates.
     */
    public void addOrderListener(Consumer<Order> listener) {
        orderListeners.add(listener);
    }

    /**
     * Removes an order listener.
     */
    public void removeOrderListener(Consumer<Order> listener) {
        orderListeners.remove(listener);
    }

    private void notifyListeners(Order order) {
        for (Consumer<Order> listener : orderListeners) {
            try {
                listener.accept(order);
            } catch (Exception e) {
                log.error("Error in order listener", e);
            }
        }
    }

    /**
     * Clears all orders (for testing/reset).
     */
    public void clear() {
        synchronized (ordersByClientId) {
            for (Order order : ordersByClientId.values()) {
                orderPool.release(order);
            }
            ordersByClientId.clear();
        }
    }
}
