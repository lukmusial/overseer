package com.hft.exchange.alpaca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.core.port.OrderPort;
import com.hft.exchange.alpaca.dto.AlpacaOrder;
import com.hft.exchange.alpaca.dto.AlpacaOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Alpaca implementation of OrderPort for order management.
 */
public class AlpacaOrderPort implements OrderPort {
    private static final Logger log = LoggerFactory.getLogger(AlpacaOrderPort.class);

    private final AlpacaHttpClient httpClient;
    private final List<Consumer<OrderUpdate>> listeners = new CopyOnWriteArrayList<>();

    public AlpacaOrderPort(AlpacaHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Order> submitOrder(Order order) {
        long submitTime = System.nanoTime();
        AlpacaOrderRequest request = AlpacaOrderMapper.toRequest(order);

        return httpClient.post("/v2/orders", request, AlpacaOrder.class)
                .thenApply(alpacaOrder -> {
                    Order result = AlpacaOrderMapper.toOrder(alpacaOrder);
                    result.setClientOrderId(order.getClientOrderId());
                    result.setSubmittedAt(submitTime);
                    result.setAcceptedAt(System.nanoTime());

                    log.debug("Order submitted: {} -> {}", order.getClientOrderId(), result.getExchangeOrderId());
                    notifyListeners(result, OrderStatus.PENDING, result.getStatus());
                    return result;
                })
                .exceptionally(e -> {
                    log.error("Failed to submit order: {}", order.getClientOrderId(), e);
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectReason(e.getMessage());
                    notifyListeners(order, OrderStatus.PENDING, OrderStatus.REJECTED);
                    throw new RuntimeException(e);
                });
    }

    @Override
    public CompletableFuture<Order> cancelOrder(Order order) {
        String orderId = order.getExchangeOrderId();
        if (orderId == null) {
            orderId = String.valueOf(order.getClientOrderId());
        }

        OrderStatus previousStatus = order.getStatus();

        return httpClient.delete("/v2/orders/" + orderId, AlpacaOrder.class)
                .thenApply(alpacaOrder -> {
                    Order result = AlpacaOrderMapper.toOrder(alpacaOrder);
                    result.setClientOrderId(order.getClientOrderId());

                    log.debug("Order cancelled: {}", result.getExchangeOrderId());
                    notifyListeners(result, previousStatus, result.getStatus());
                    return result;
                });
    }

    @Override
    public CompletableFuture<Order> modifyOrder(Order order) {
        String orderId = order.getExchangeOrderId();
        if (orderId == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Exchange order ID required for modification"));
        }

        OrderStatus previousStatus = order.getStatus();
        AlpacaOrderRequest request = AlpacaOrderMapper.toRequest(order);

        return httpClient.patch("/v2/orders/" + orderId, request, AlpacaOrder.class)
                .thenApply(alpacaOrder -> {
                    Order result = AlpacaOrderMapper.toOrder(alpacaOrder);
                    result.setClientOrderId(order.getClientOrderId());

                    log.debug("Order modified: {}", result.getExchangeOrderId());
                    notifyListeners(result, previousStatus, result.getStatus());
                    return result;
                });
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrder(long clientOrderId) {
        return httpClient.get("/v2/orders:by_client_order_id?client_order_id=" + clientOrderId, AlpacaOrder.class)
                .thenApply(alpacaOrder -> {
                    Order order = AlpacaOrderMapper.toOrder(alpacaOrder);
                    order.setClientOrderId(clientOrderId);
                    return Optional.of(order);
                })
                .exceptionally(e -> {
                    if (e.getCause() instanceof AlpacaApiException apiEx && apiEx.isNotFound()) {
                        return Optional.empty();
                    }
                    throw new RuntimeException(e);
                });
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrderByExchangeId(String exchangeOrderId) {
        return httpClient.get("/v2/orders/" + exchangeOrderId, AlpacaOrder.class)
                .thenApply(alpacaOrder -> Optional.of(AlpacaOrderMapper.toOrder(alpacaOrder)))
                .exceptionally(e -> {
                    if (e.getCause() instanceof AlpacaApiException apiEx && apiEx.isNotFound()) {
                        return Optional.empty();
                    }
                    throw new RuntimeException(e);
                });
    }

    @Override
    public CompletableFuture<List<Order>> getOpenOrders() {
        return getOpenOrders(null);
    }

    @Override
    public CompletableFuture<List<Order>> getOpenOrders(Symbol symbol) {
        String path = "/v2/orders?status=open";
        if (symbol != null) {
            path += "&symbols=" + symbol.getTicker();
        }

        return httpClient.get(path, AlpacaOrder[].class)
                .thenApply(alpacaOrders -> {
                    return java.util.Arrays.stream(alpacaOrders)
                            .map(AlpacaOrderMapper::toOrder)
                            .toList();
                });
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders() {
        return httpClient.delete("/v2/orders")
                .thenRun(() -> log.info("All orders cancelled"));
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders(Symbol symbol) {
        // Alpaca doesn't support cancel by symbol directly, need to get orders first
        return getOpenOrders(symbol)
                .thenCompose(orders -> {
                    List<CompletableFuture<Order>> cancellations = orders.stream()
                            .map(this::cancelOrder)
                            .toList();
                    return CompletableFuture.allOf(cancellations.toArray(new CompletableFuture[0]));
                });
    }

    @Override
    public void addOrderListener(Consumer<OrderUpdate> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeOrderListener(Consumer<OrderUpdate> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        OrderUpdate update = new OrderUpdate(order, previousStatus, newStatus, System.nanoTime());
        for (Consumer<OrderUpdate> listener : listeners) {
            try {
                listener.accept(update);
            } catch (Exception e) {
                log.error("Error in order listener", e);
            }
        }
    }
}
