package com.hft.api.dto;

import com.hft.core.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderDtoTest {

    @Test
    void shouldConvertValidEpochNanosToMillis() {
        long nowMillis = System.currentTimeMillis();
        long epochNanos = nowMillis * 1_000_000L;

        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setCreatedAt(epochNanos);

        OrderDto dto = OrderDto.from(order);

        assertEquals(nowMillis, dto.createdAt(),
                "Valid epoch nanos should convert to epoch millis by dividing by 1,000,000");
    }

    @Test
    void shouldReturnZeroForStaleNanoTimeValue() {
        // System.nanoTime() values are typically ~1e12-1e13, well below the year-2000 threshold
        long staleNanoTime = 3_000_000_000_000L; // ~3 trillion nanos, a typical System.nanoTime()

        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setCreatedAt(staleNanoTime);

        OrderDto dto = OrderDto.from(order);

        assertEquals(0, dto.createdAt(),
                "Stale nanoTime values (below year 2000 epoch nanos threshold) should return 0");
    }

    @Test
    void shouldReturnZeroForZeroCreatedAt() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setCreatedAt(0);

        OrderDto dto = OrderDto.from(order);

        assertEquals(0, dto.createdAt(),
                "createdAt=0 should produce DTO createdAt=0");
    }

    @Test
    void shouldReturnZeroForNegativeCreatedAt() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setCreatedAt(-1_000_000_000L);

        OrderDto dto = OrderDto.from(order);

        assertEquals(0, dto.createdAt(),
                "Negative epochNanos should produce DTO createdAt=0");
    }

    @Test
    void shouldConvertUpdatedAtTimestamp() {
        long nowMillis = System.currentTimeMillis();
        long epochNanos = nowMillis * 1_000_000L;

        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        order.setLastUpdatedAt(epochNanos);

        OrderDto dto = OrderDto.from(order);

        assertEquals(nowMillis, dto.updatedAt(),
                "updatedAt should be converted from lastUpdatedAt epoch nanos to millis");
    }

    @Test
    void shouldPassThroughStrategyName() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100)
                .strategyId("strat-42");

        OrderDto dto = OrderDto.from(order, "My VWAP Strategy");

        assertEquals("strat-42", dto.strategyId());
        assertEquals("My VWAP Strategy", dto.strategyName());
    }

    @Test
    void shouldHaveNullStrategyNameWhenNotProvided() {
        Order order = new Order()
                .symbol(new Symbol("AAPL", Exchange.ALPACA))
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        OrderDto dto = OrderDto.from(order);

        assertNull(dto.strategyName(),
                "strategyName should be null when using the single-arg from() overload");
    }
}
