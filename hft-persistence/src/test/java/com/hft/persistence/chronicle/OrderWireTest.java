package com.hft.persistence.chronicle;

import com.hft.core.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderWireTest {

    @Test
    void shouldRoundTripAlpacaOrderWithPriceScale100() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.DAY)
                .price(15000)
                .quantity(100)
                .strategyId("strat-1");
        order.setPriceScale(100);

        OrderWire wire = OrderWire.from(order);
        Order restored = wire.toOrder();

        assertEquals(order.getSymbol().getTicker(), restored.getSymbol().getTicker());
        assertEquals(order.getSymbol().getExchange(), restored.getSymbol().getExchange());
        assertEquals(order.getSide(), restored.getSide());
        assertEquals(order.getType(), restored.getType());
        assertEquals(order.getTimeInForce(), restored.getTimeInForce());
        assertEquals(order.getPrice(), restored.getPrice());
        assertEquals(order.getQuantity(), restored.getQuantity());
        assertEquals(order.getStrategyId(), restored.getStrategyId());
        assertEquals(100, restored.getPriceScale());
        assertEquals(order.getClientOrderId(), restored.getClientOrderId());
    }

    @Test
    void shouldRoundTripBinanceOrderWithPriceScale100Million() {
        Order order = new Order()
                .symbol(new Symbol("BTCUSDT", Exchange.BINANCE))
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(5_000_000_000_000L) // 50000.00000000 BTC price
                .quantity(10_000_000) // 0.10000000 BTC
                .strategyId("crypto-strat");
        order.setPriceScale(100_000_000);
        order.setStopPrice(4_900_000_000_000L);

        OrderWire wire = OrderWire.from(order);
        Order restored = wire.toOrder();

        assertEquals("BTCUSDT", restored.getSymbol().getTicker());
        assertEquals(Exchange.BINANCE, restored.getSymbol().getExchange());
        assertEquals(OrderSide.SELL, restored.getSide());
        assertEquals(OrderType.LIMIT, restored.getType());
        assertEquals(TimeInForce.GTC, restored.getTimeInForce());
        assertEquals(5_000_000_000_000L, restored.getPrice());
        assertEquals(10_000_000, restored.getQuantity());
        assertEquals(100_000_000, restored.getPriceScale());
        assertEquals(4_900_000_000_000L, restored.getStopPrice());
        assertEquals("crypto-strat", restored.getStrategyId());
        assertEquals(order.getClientOrderId(), restored.getClientOrderId());
    }

    @Test
    void shouldUsDefaultPriceScaleWhenWireHasZero() {
        // Create an order and serialize it via OrderWire
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(50);
        // The default priceScale on Order is 100.
        // Simulate wire data with priceScale=0 by creating a wire and restoring from it.
        // OrderWire.from(order) will capture priceScale=100, but we want to test the
        // toOrder() path when priceScale is 0 (e.g., legacy data without priceScale).
        // Since OrderWire fields are package-private via SelfDescribingMarshallable,
        // we set it to 0 on the order before serialization to test the guard.
        order.setPriceScale(0);

        OrderWire wire = OrderWire.from(order);
        Order restored = wire.toOrder();

        // When priceScale is 0 in wire data, toOrder() should NOT call setPriceScale,
        // so the Order constructor default of 100 is preserved.
        assertEquals(100, restored.getPriceScale(),
                "priceScale=0 in wire data should fall back to Order default of 100");
    }

    @Test
    void shouldPreserveTimestampsOnRoundTrip() {
        long createdAt = System.currentTimeMillis() * 1_000_000L;
        long lastUpdatedAt = createdAt + 5_000_000_000L; // 5 seconds later

        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setCreatedAt(createdAt);
        order.setLastUpdatedAt(lastUpdatedAt);

        OrderWire wire = OrderWire.from(order);
        Order restored = wire.toOrder();

        assertEquals(createdAt, restored.getCreatedAt());
        assertEquals(lastUpdatedAt, restored.getLastUpdatedAt());
    }

    @Test
    void shouldPreserveFilledOrderState() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.DAY)
                .price(15000)
                .quantity(100)
                .strategyId("strat-1");
        order.setPriceScale(100);
        order.markSubmitted();
        order.markAccepted("EX-123");
        order.markFilled(100, 15050);

        OrderWire wire = OrderWire.from(order);
        Order restored = wire.toOrder();

        assertEquals(OrderStatus.FILLED, restored.getStatus());
        assertEquals("EX-123", restored.getExchangeOrderId());
        assertEquals(100, restored.getFilledQuantity());
        assertEquals(15050, restored.getAverageFilledPrice());
        assertEquals(100, restored.getPriceScale());
    }
}
