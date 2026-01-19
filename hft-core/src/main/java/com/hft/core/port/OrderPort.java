package com.hft.core.port;

import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Port interface for order management operations.
 */
public interface OrderPort {

    /**
     * Submits an order to the exchange.
     *
     * @param order The order to submit
     * @return Future containing the submitted order with exchange ID
     */
    CompletableFuture<Order> submitOrder(Order order);

    /**
     * Cancels an existing order.
     *
     * @param order The order to cancel
     * @return Future containing the cancelled order
     */
    CompletableFuture<Order> cancelOrder(Order order);

    /**
     * Modifies an existing order (cancel and replace).
     *
     * @param order The order with modified parameters
     * @return Future containing the modified order
     */
    CompletableFuture<Order> modifyOrder(Order order);

    /**
     * Queries the current status of an order.
     *
     * @param clientOrderId The client order ID
     * @return Future containing the order if found
     */
    CompletableFuture<Optional<Order>> getOrder(long clientOrderId);

    /**
     * Queries the current status of an order by exchange ID.
     *
     * @param exchangeOrderId The exchange order ID
     * @return Future containing the order if found
     */
    CompletableFuture<Optional<Order>> getOrderByExchangeId(String exchangeOrderId);

    /**
     * Lists all open orders.
     *
     * @return Future containing list of open orders
     */
    CompletableFuture<List<Order>> getOpenOrders();

    /**
     * Lists all open orders for a specific symbol.
     *
     * @param symbol The symbol to filter by
     * @return Future containing list of open orders
     */
    CompletableFuture<List<Order>> getOpenOrders(Symbol symbol);

    /**
     * Cancels all open orders.
     *
     * @return Future that completes when all orders are cancelled
     */
    CompletableFuture<Void> cancelAllOrders();

    /**
     * Cancels all open orders for a specific symbol.
     *
     * @param symbol The symbol to cancel orders for
     * @return Future that completes when all orders are cancelled
     */
    CompletableFuture<Void> cancelAllOrders(Symbol symbol);

    /**
     * Registers a callback for order updates.
     *
     * @param listener The callback to invoke on order updates
     */
    void addOrderListener(Consumer<OrderUpdate> listener);

    /**
     * Removes an order update callback.
     *
     * @param listener The callback to remove
     */
    void removeOrderListener(Consumer<OrderUpdate> listener);

    /**
     * Order update event from the exchange.
     */
    record OrderUpdate(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            long timestampNanos
    ) {
        public boolean isFill() {
            return newStatus == OrderStatus.FILLED || newStatus == OrderStatus.PARTIALLY_FILLED;
        }

        public boolean isTerminal() {
            return newStatus.isTerminal();
        }
    }
}
