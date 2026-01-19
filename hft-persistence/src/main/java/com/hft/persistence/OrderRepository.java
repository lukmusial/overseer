package com.hft.persistence;

import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;

import java.util.List;
import java.util.Optional;

/**
 * Interface for order persistence and retrieval.
 */
public interface OrderRepository {

    /**
     * Saves or updates an order.
     */
    void save(Order order);

    /**
     * Finds an order by its client order ID.
     */
    Optional<Order> findByClientOrderId(long clientOrderId);

    /**
     * Finds an order by its exchange order ID.
     */
    Optional<Order> findByExchangeOrderId(String exchangeOrderId);

    /**
     * Finds all orders for a symbol.
     */
    List<Order> findBySymbol(Symbol symbol);

    /**
     * Finds all orders with a given status.
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Finds all orders for a given trading day.
     *
     * @param dateYYYYMMDD date in YYYYMMDD format
     */
    List<Order> findByDate(int dateYYYYMMDD);

    /**
     * Gets the most recent N orders.
     */
    List<Order> getRecentOrders(int count);

    /**
     * Gets all active (open) orders.
     */
    List<Order> getActiveOrders();

    /**
     * Deletes an order by client order ID.
     */
    void delete(long clientOrderId);

    /**
     * Clears all orders.
     */
    void clear();

    /**
     * Gets the total number of orders.
     */
    long count();

    /**
     * Flushes any buffered writes.
     */
    void flush();

    /**
     * Closes the repository.
     */
    void close();
}
