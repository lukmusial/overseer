package com.hft.exchange.binance;

import com.hft.core.model.*;
import com.hft.exchange.binance.dto.BinanceOrder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BinanceOrderMapperTest {

    private static final Symbol BTCUSDT = new Symbol("BTCUSDT", Exchange.BINANCE);

    @Test
    void shouldConvertBinanceOrderToOrder() {
        BinanceOrder binanceOrder = new BinanceOrder();
        binanceOrder.setOrderId(12345);
        binanceOrder.setClientOrderId("789");
        binanceOrder.setSymbol("BTCUSDT");
        binanceOrder.setSide("BUY");
        binanceOrder.setType("LIMIT");
        binanceOrder.setTimeInForce("GTC");
        binanceOrder.setStatus("FILLED");
        binanceOrder.setOrigQty("0.5");
        binanceOrder.setExecutedQty("0.5");
        binanceOrder.setPrice("45000.00");
        binanceOrder.setCummulativeQuoteQty("22500.00"); // 0.5 * 45000

        Order order = BinanceOrderMapper.toOrder(binanceOrder, BTCUSDT);

        assertEquals("12345", order.getExchangeOrderId());
        assertEquals(789L, order.getClientOrderId());
        assertEquals("BTCUSDT", order.getSymbol().getTicker());
        assertEquals(Exchange.BINANCE, order.getSymbol().getExchange());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getType());
        assertEquals(TimeInForce.GTC, order.getTimeInForce());
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void shouldBuildRequestParamsForMarketOrder() {
        Order order = new Order();
        order.setSymbol(BTCUSDT);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.MARKET);
        order.setQuantity(50_000_000); // 0.5 BTC in satoshis

        Map<String, String> params = BinanceOrderMapper.toRequestParams(order);

        assertEquals("BTCUSDT", params.get("symbol"));
        assertEquals("BUY", params.get("side"));
        assertEquals("MARKET", params.get("type"));
        assertEquals("0.5", params.get("quantity"));
        assertFalse(params.containsKey("price"));
        assertFalse(params.containsKey("timeInForce"));
    }

    @Test
    void shouldBuildRequestParamsForLimitOrder() {
        Order order = new Order();
        order.setSymbol(BTCUSDT);
        order.setSide(OrderSide.SELL);
        order.setType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setQuantity(100_000_000); // 1 BTC
        order.setPrice(4500000000000L); // $45,000 in 8-decimal format

        Map<String, String> params = BinanceOrderMapper.toRequestParams(order);

        assertEquals("BTCUSDT", params.get("symbol"));
        assertEquals("SELL", params.get("side"));
        assertEquals("LIMIT", params.get("type"));
        assertEquals("GTC", params.get("timeInForce"));
        assertEquals("1", params.get("quantity"));
        assertEquals("45000", params.get("price"));
    }

    @Test
    void shouldParseAllOrderSides() {
        BinanceOrder buyOrder = new BinanceOrder();
        buyOrder.setSide("BUY");

        BinanceOrder sellOrder = new BinanceOrder();
        sellOrder.setSide("SELL");

        assertEquals(OrderSide.BUY, BinanceOrderMapper.toOrder(buyOrder, BTCUSDT).getSide());
        assertEquals(OrderSide.SELL, BinanceOrderMapper.toOrder(sellOrder, BTCUSDT).getSide());
    }

    @Test
    void shouldParseAllOrderTypes() {
        String[][] typeMappings = {
                {"MARKET", "MARKET"},
                {"LIMIT", "LIMIT"},
                {"STOP_LOSS", "STOP"},
                {"STOP_LOSS_LIMIT", "STOP_LIMIT"},
                {"TAKE_PROFIT", "STOP"},
                {"TAKE_PROFIT_LIMIT", "STOP_LIMIT"}
        };

        for (String[] mapping : typeMappings) {
            BinanceOrder binanceOrder = new BinanceOrder();
            binanceOrder.setType(mapping[0]);

            Order order = BinanceOrderMapper.toOrder(binanceOrder, BTCUSDT);
            assertEquals(OrderType.valueOf(mapping[1]), order.getType(),
                    "Type " + mapping[0] + " should map to " + mapping[1]);
        }
    }

    @Test
    void shouldParseAllOrderStatuses() {
        String[][] statusMappings = {
                {"NEW", "ACCEPTED"},
                {"PARTIALLY_FILLED", "PARTIALLY_FILLED"},
                {"FILLED", "FILLED"},
                {"CANCELED", "CANCELLED"},
                {"EXPIRED", "CANCELLED"},
                {"REJECTED", "CANCELLED"}
        };

        for (String[] mapping : statusMappings) {
            BinanceOrder binanceOrder = new BinanceOrder();
            binanceOrder.setStatus(mapping[0]);

            Order order = BinanceOrderMapper.toOrder(binanceOrder, BTCUSDT);
            assertEquals(OrderStatus.valueOf(mapping[1]), order.getStatus(),
                    "Status " + mapping[0] + " should map to " + mapping[1]);
        }
    }

    @Test
    void shouldCalculateAverageFillPrice() {
        BinanceOrder binanceOrder = new BinanceOrder();
        binanceOrder.setExecutedQty("2");
        binanceOrder.setCummulativeQuoteQty("90000"); // 2 * 45000

        Order order = BinanceOrderMapper.toOrder(binanceOrder, BTCUSDT);

        // Average price = 90000 / 2 = 45000
        // In 8-decimal format: 45000 * 100_000_000 = 4,500,000,000,000
        assertEquals(4500000000000L, order.getAverageFilledPrice());
    }

    @Test
    void shouldHandleNullValues() {
        BinanceOrder binanceOrder = new BinanceOrder();
        // All fields null except required symbol

        Order order = BinanceOrderMapper.toOrder(binanceOrder, BTCUSDT);

        assertNotNull(order);
        assertEquals(BTCUSDT, order.getSymbol());
        assertEquals(0L, order.getQuantity());
        assertEquals(0L, order.getPrice());
    }

    @Test
    void shouldIncludeStopPriceForStopOrders() {
        Order order = new Order();
        order.setSymbol(BTCUSDT);
        order.setSide(OrderSide.SELL);
        order.setType(OrderType.STOP);
        order.setQuantity(100_000_000);
        order.setStopPrice(4400000000000L); // $44,000

        Map<String, String> params = BinanceOrderMapper.toRequestParams(order);

        assertEquals("44000", params.get("stopPrice"));
    }
}
