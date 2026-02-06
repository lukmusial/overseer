package com.hft.api.service;

import com.hft.core.model.Exchange;
import com.hft.core.model.Order;
import com.hft.core.model.OrderSide;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.OrderType;
import com.hft.core.model.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubOrderPortTest {

    private StubOrderPort stubOrderPort;

    @BeforeEach
    void setUp() {
        stubOrderPort = new StubOrderPort();
    }

    @Test
    void submitOrder_shouldImmediatelyFillOrder() throws Exception {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        Order result = stubOrderPort.submitOrder(order).get();

        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(100, result.getFilledQuantity());
        assertEquals(15000, result.getAverageFilledPrice());
    }

    @Test
    void submitOrder_shouldAssignExchangeOrderId() throws Exception {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        Order result = stubOrderPort.submitOrder(order).get();

        assertTrue(result.getExchangeOrderId().startsWith("STUB-"));
    }

    @Test
    void submitOrder_shouldIncrementOrderIds() throws Exception {
        Order order1 = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        Order order2 = new Order()
                .symbol(new Symbol("GOOG", Exchange.ALPACA))
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .price(28000)
                .quantity(50);

        Order result1 = stubOrderPort.submitOrder(order1).get();
        Order result2 = stubOrderPort.submitOrder(order2).get();

        assertEquals("STUB-1", result1.getExchangeOrderId());
        assertEquals("STUB-2", result2.getExchangeOrderId());
    }

    @Test
    void cancelOrder_shouldSetStatusToCancelled() throws Exception {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        Order result = stubOrderPort.cancelOrder(order).get();

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }

    @Test
    void getOpenOrders_shouldReturnEmptyList() throws Exception {
        List<Order> openOrders = stubOrderPort.getOpenOrders().get();

        assertTrue(openOrders.isEmpty());
    }
}
