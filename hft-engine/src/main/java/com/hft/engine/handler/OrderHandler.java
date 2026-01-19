package com.hft.engine.handler;

import com.hft.core.model.*;
import com.hft.core.port.OrderPort;
import com.hft.engine.event.EventType;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.service.OrderManager;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles order-related events from the disruptor.
 * Routes orders to appropriate exchange adapters.
 */
public class OrderHandler implements EventHandler<TradingEvent> {
    private static final Logger log = LoggerFactory.getLogger(OrderHandler.class);

    private final OrderManager orderManager;
    private final Map<Exchange, OrderPort> orderPorts;

    public OrderHandler(OrderManager orderManager) {
        this.orderManager = orderManager;
        this.orderPorts = new ConcurrentHashMap<>();
    }

    public void registerOrderPort(Exchange exchange, OrderPort orderPort) {
        orderPorts.put(exchange, orderPort);
        log.info("Registered order port for {}", exchange);
    }

    @Override
    public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
        switch (event.getType()) {
            case NEW_ORDER -> handleNewOrder(event);
            case CANCEL_ORDER -> handleCancelOrder(event);
            case MODIFY_ORDER -> handleModifyOrder(event);
            default -> {
                // Not an order event, ignore
            }
        }
    }

    private void handleNewOrder(TradingEvent event) {
        Order order = orderManager.createOrder(
                event.getSymbol(),
                event.getSide(),
                event.getOrderType(),
                event.getQuantity(),
                event.getPrice()
        );

        if (event.getTimeInForce() != null) {
            order.setTimeInForce(event.getTimeInForce());
        }
        if (event.getStopPrice() > 0) {
            order.setStopPrice(event.getStopPrice());
        }
        if (event.getStrategyId() != null) {
            order.strategyId(event.getStrategyId());
        }

        Exchange exchange = event.getSymbol().getExchange();
        OrderPort orderPort = orderPorts.get(exchange);

        if (orderPort == null) {
            log.error("No order port registered for exchange: {}", exchange);
            orderManager.rejectOrder(order, "No exchange adapter available");
            return;
        }

        order.markSubmitted();
        orderManager.trackOrder(order);

        orderPort.submitOrder(order)
                .thenAccept(submittedOrder -> {
                    log.debug("Order submitted: {}", submittedOrder.getExchangeOrderId());
                    orderManager.updateOrder(submittedOrder);
                })
                .exceptionally(e -> {
                    log.error("Order submission failed: {}", order.getClientOrderId(), e);
                    orderManager.rejectOrder(order, e.getMessage());
                    return null;
                });
    }

    private void handleCancelOrder(TradingEvent event) {
        Order order = orderManager.getOrder(event.getClientOrderId());
        if (order == null) {
            log.warn("Cancel request for unknown order: {}", event.getClientOrderId());
            return;
        }

        Exchange exchange = order.getSymbol().getExchange();
        OrderPort orderPort = orderPorts.get(exchange);

        if (orderPort == null) {
            log.error("No order port registered for exchange: {}", exchange);
            return;
        }

        orderPort.cancelOrder(order)
                .thenAccept(cancelledOrder -> {
                    log.debug("Order cancelled: {}", cancelledOrder.getExchangeOrderId());
                    orderManager.updateOrder(cancelledOrder);
                })
                .exceptionally(e -> {
                    log.error("Order cancellation failed: {}", order.getClientOrderId(), e);
                    return null;
                });
    }

    private void handleModifyOrder(TradingEvent event) {
        Order order = orderManager.getOrder(event.getClientOrderId());
        if (order == null) {
            log.warn("Modify request for unknown order: {}", event.getClientOrderId());
            return;
        }

        // Apply modifications
        if (event.getPrice() > 0) {
            order.setPrice(event.getPrice());
        }
        if (event.getQuantity() > 0) {
            order.setQuantity(event.getQuantity());
        }

        Exchange exchange = order.getSymbol().getExchange();
        OrderPort orderPort = orderPorts.get(exchange);

        if (orderPort == null) {
            log.error("No order port registered for exchange: {}", exchange);
            return;
        }

        orderPort.modifyOrder(order)
                .thenAccept(modifiedOrder -> {
                    log.debug("Order modified: {}", modifiedOrder.getExchangeOrderId());
                    orderManager.updateOrder(modifiedOrder);
                })
                .exceptionally(e -> {
                    log.error("Order modification failed: {}", order.getClientOrderId(), e);
                    return null;
                });
    }
}
