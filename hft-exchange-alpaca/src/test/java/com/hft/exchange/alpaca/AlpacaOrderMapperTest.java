package com.hft.exchange.alpaca;

import com.hft.core.model.*;
import com.hft.exchange.alpaca.dto.AlpacaOrder;
import com.hft.exchange.alpaca.dto.AlpacaOrderRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaOrderMapperTest {

    @Test
    void shouldConvertAlpacaOrderToOrder() {
        AlpacaOrder alpacaOrder = new AlpacaOrder();
        alpacaOrder.setId("order-123");
        alpacaOrder.setClientOrderId("456");
        alpacaOrder.setSymbol("AAPL");
        alpacaOrder.setSide("buy");
        alpacaOrder.setType("limit");
        alpacaOrder.setTimeInForce("day");
        alpacaOrder.setStatus("filled");
        alpacaOrder.setQty("100");
        alpacaOrder.setFilledQty("100");
        alpacaOrder.setLimitPrice("150.50");
        alpacaOrder.setFilledAvgPrice("150.25");

        Order order = AlpacaOrderMapper.toOrder(alpacaOrder);

        assertEquals("order-123", order.getExchangeOrderId());
        assertEquals(456L, order.getClientOrderId());
        assertEquals("AAPL", order.getSymbol().getTicker());
        assertEquals(Exchange.ALPACA, order.getSymbol().getExchange());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getType());
        assertEquals(TimeInForce.DAY, order.getTimeInForce());
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(100L, order.getQuantity());
        assertEquals(100L, order.getFilledQuantity());
        assertEquals(15050L, order.getPrice()); // $150.50 = 15050 cents
        assertEquals(15025L, order.getAverageFilledPrice()); // $150.25 = 15025 cents
    }

    @Test
    void shouldConvertOrderToAlpacaRequest() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setQuantity(100);
        order.setPrice(15050); // $150.50

        AlpacaOrderRequest request = AlpacaOrderMapper.toRequest(order);

        assertEquals("AAPL", request.getSymbol());
        assertEquals("buy", request.getSide());
        assertEquals("limit", request.getType());
        assertEquals("gtc", request.getTimeInForce());
        assertEquals("100", request.getQty());
        assertEquals("150.5", request.getLimitPrice());
    }

    @Test
    void shouldParseAllOrderSides() {
        AlpacaOrder buyOrder = new AlpacaOrder();
        buyOrder.setSide("buy");
        buyOrder.setSymbol("AAPL");

        AlpacaOrder sellOrder = new AlpacaOrder();
        sellOrder.setSide("sell");
        sellOrder.setSymbol("AAPL");

        assertEquals(OrderSide.BUY, AlpacaOrderMapper.toOrder(buyOrder).getSide());
        assertEquals(OrderSide.SELL, AlpacaOrderMapper.toOrder(sellOrder).getSide());
    }

    @Test
    void shouldParseAllOrderTypes() {
        String[] types = {"market", "limit", "stop", "stop_limit"};
        OrderType[] expected = {OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT};

        for (int i = 0; i < types.length; i++) {
            AlpacaOrder alpacaOrder = new AlpacaOrder();
            alpacaOrder.setType(types[i]);
            alpacaOrder.setSymbol("AAPL");

            assertEquals(expected[i], AlpacaOrderMapper.toOrder(alpacaOrder).getType());
        }
    }

    @Test
    void shouldParseAllOrderStatuses() {
        String[][] statusMappings = {
                {"new", "ACCEPTED"},
                {"accepted", "ACCEPTED"},
                {"partially_filled", "PARTIALLY_FILLED"},
                {"filled", "FILLED"},
                {"canceled", "CANCELLED"},
                {"expired", "CANCELLED"},
                {"rejected", "REJECTED"}
        };

        for (String[] mapping : statusMappings) {
            AlpacaOrder alpacaOrder = new AlpacaOrder();
            alpacaOrder.setStatus(mapping[0]);
            alpacaOrder.setSymbol("AAPL");

            Order order = AlpacaOrderMapper.toOrder(alpacaOrder);
            assertEquals(OrderStatus.valueOf(mapping[1]), order.getStatus(),
                    "Status " + mapping[0] + " should map to " + mapping[1]);
        }
    }

    @Test
    void shouldHandleNullValues() {
        AlpacaOrder alpacaOrder = new AlpacaOrder();
        alpacaOrder.setSymbol("AAPL");
        // All other fields null

        Order order = AlpacaOrderMapper.toOrder(alpacaOrder);

        assertNotNull(order);
        assertEquals("AAPL", order.getSymbol().getTicker());
        assertEquals(0L, order.getQuantity());
        assertEquals(0L, order.getPrice());
    }

    @Test
    void shouldIncludeStopPriceForStopOrders() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(OrderSide.SELL);
        order.setType(OrderType.STOP);
        order.setTimeInForce(TimeInForce.GTC);
        order.setQuantity(100);
        order.setStopPrice(14500); // $145.00

        AlpacaOrderRequest request = AlpacaOrderMapper.toRequest(order);

        assertEquals("145", request.getStopPrice());
        assertNull(request.getLimitPrice());
    }

    @Test
    void shouldIncludeBothPricesForStopLimitOrders() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(OrderSide.SELL);
        order.setType(OrderType.STOP_LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setQuantity(100);
        order.setPrice(14400); // $144.00 limit
        order.setStopPrice(14500); // $145.00 stop

        AlpacaOrderRequest request = AlpacaOrderMapper.toRequest(order);

        assertEquals("145", request.getStopPrice());
        assertEquals("144", request.getLimitPrice());
    }
}
