package com.hft.exchange.binance;

import com.hft.core.model.Exchange;
import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.core.port.OrderPort;
import com.hft.exchange.binance.dto.BinanceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Binance implementation of OrderPort for order management.
 */
public class BinanceOrderPort implements OrderPort {
    private static final Logger log = LoggerFactory.getLogger(BinanceOrderPort.class);

    private final BinanceHttpClient httpClient;
    private final List<Consumer<OrderUpdate>> listeners = new CopyOnWriteArrayList<>();

    public BinanceOrderPort(BinanceHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Order> submitOrder(Order order) {
        long submitTime = System.nanoTime();
        Map<String, String> params = BinanceOrderMapper.toRequestParams(order);

        return httpClient.signedPost("/api/v3/order", params, BinanceOrder.class)
                .thenApply(binanceOrder -> {
                    Order result = BinanceOrderMapper.toOrder(binanceOrder, order.getSymbol());
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", order.getSymbol().getTicker());

        if (order.getExchangeOrderId() != null) {
            params.put("orderId", order.getExchangeOrderId());
        } else {
            params.put("origClientOrderId", String.valueOf(order.getClientOrderId()));
        }

        OrderStatus previousStatus = order.getStatus();

        return httpClient.signedDelete("/api/v3/order", params, BinanceOrder.class)
                .thenApply(binanceOrder -> {
                    Order result = BinanceOrderMapper.toOrder(binanceOrder, order.getSymbol());
                    result.setClientOrderId(order.getClientOrderId());

                    log.debug("Order cancelled: {}", result.getExchangeOrderId());
                    notifyListeners(result, previousStatus, result.getStatus());
                    return result;
                });
    }

    @Override
    public CompletableFuture<Order> modifyOrder(Order order) {
        // Binance doesn't support order modification directly
        // Need to cancel and resubmit
        return cancelOrder(order)
                .thenCompose(cancelled -> {
                    order.setExchangeOrderId(null);
                    return submitOrder(order);
                });
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrder(long clientOrderId) {
        // Need symbol to query order - this is a limitation of Binance API
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Binance requires symbol to query order. Use getOrderByExchangeId with symbol."));
    }

    @Override
    public CompletableFuture<Optional<Order>> getOrderByExchangeId(String exchangeOrderId) {
        // Need symbol to query order - this is a limitation of Binance API
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Binance requires symbol to query order"));
    }

    /**
     * Gets an order by exchange ID with the required symbol.
     */
    public CompletableFuture<Optional<Order>> getOrder(Symbol symbol, String exchangeOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.getTicker());
        params.put("orderId", exchangeOrderId);

        return httpClient.signedGet("/api/v3/order", params, BinanceOrder.class)
                .thenApply(binanceOrder -> Optional.of(BinanceOrderMapper.toOrder(binanceOrder, symbol)))
                .exceptionally(e -> {
                    if (e.getCause() instanceof BinanceApiException apiEx && apiEx.isOrderNotFound()) {
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
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null) {
            params.put("symbol", symbol.getTicker());
        }

        return httpClient.signedGet("/api/v3/openOrders", params, BinanceOrder[].class)
                .thenApply(binanceOrders -> {
                    return Arrays.stream(binanceOrders)
                            .map(bo -> {
                                Symbol orderSymbol = symbol != null ? symbol :
                                        new Symbol(bo.getSymbol(), Exchange.BINANCE);
                                return BinanceOrderMapper.toOrder(bo, orderSymbol);
                            })
                            .toList();
                });
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders() {
        return getOpenOrders()
                .thenCompose(orders -> {
                    // Group by symbol and cancel
                    Map<String, List<Order>> bySymbol = new HashMap<>();
                    for (Order order : orders) {
                        bySymbol.computeIfAbsent(order.getSymbol().getTicker(), k -> new ArrayList<>())
                                .add(order);
                    }

                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (String ticker : bySymbol.keySet()) {
                        futures.add(cancelAllOrdersForSymbol(ticker));
                    }

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                });
    }

    @Override
    public CompletableFuture<Void> cancelAllOrders(Symbol symbol) {
        return cancelAllOrdersForSymbol(symbol.getTicker());
    }

    private CompletableFuture<Void> cancelAllOrdersForSymbol(String ticker) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", ticker);

        return httpClient.signedDelete("/api/v3/openOrders", params)
                .thenRun(() -> log.info("All orders cancelled for {}", ticker));
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
