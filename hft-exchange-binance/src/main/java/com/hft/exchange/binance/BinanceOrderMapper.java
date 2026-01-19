package com.hft.exchange.binance;

import com.hft.core.model.*;
import com.hft.exchange.binance.dto.BinanceOrder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps between Binance DTOs and core domain models.
 */
public class BinanceOrderMapper {
    // Binance uses 8 decimal places for most crypto
    private static final int PRICE_SCALE = 100_000_000;

    /**
     * Converts a Binance order response to a core Order.
     */
    public static Order toOrder(BinanceOrder binanceOrder, Symbol symbol) {
        Order order = new Order();
        order.setExchangeOrderId(String.valueOf(binanceOrder.getOrderId()));

        if (binanceOrder.getClientOrderId() != null) {
            try {
                order.setClientOrderId(Long.parseLong(binanceOrder.getClientOrderId()));
            } catch (NumberFormatException e) {
                // Client order ID might be alphanumeric, ignore
            }
        }

        order.setSymbol(symbol);
        order.setSide(parseSide(binanceOrder.getSide()));
        order.setType(parseType(binanceOrder.getType()));
        order.setTimeInForce(parseTimeInForce(binanceOrder.getTimeInForce()));
        order.setStatus(parseStatus(binanceOrder.getStatus()));

        if (binanceOrder.getOrigQty() != null) {
            order.setQuantity(parseQuantity(binanceOrder.getOrigQty()));
        }

        if (binanceOrder.getExecutedQty() != null) {
            order.setFilledQuantity(parseQuantity(binanceOrder.getExecutedQty()));
        }

        if (binanceOrder.getPrice() != null) {
            order.setPrice(parsePrice(binanceOrder.getPrice()));
        }

        if (binanceOrder.getStopPrice() != null) {
            order.setStopPrice(parsePrice(binanceOrder.getStopPrice()));
        }

        // Calculate average price from cumulative quote qty
        if (binanceOrder.getCummulativeQuoteQty() != null && binanceOrder.getExecutedQty() != null) {
            BigDecimal quoteQty = new BigDecimal(binanceOrder.getCummulativeQuoteQty());
            BigDecimal execQty = new BigDecimal(binanceOrder.getExecutedQty());
            if (execQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avgPrice = quoteQty.divide(execQty, 8, java.math.RoundingMode.HALF_UP);
                order.setAverageFilledPrice(avgPrice.multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue());
            }
        }

        return order;
    }

    /**
     * Builds request parameters for a new order.
     */
    public static Map<String, String> toRequestParams(Order order) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", order.getSymbol().getTicker());
        params.put("side", formatSide(order.getSide()));
        params.put("type", formatType(order.getType()));
        params.put("quantity", formatQuantity(order.getQuantity()));
        params.put("newClientOrderId", String.valueOf(order.getClientOrderId()));

        if (order.getTimeInForce() != null && order.getType() == OrderType.LIMIT) {
            params.put("timeInForce", formatTimeInForce(order.getTimeInForce()));
        }

        if (order.getType() == OrderType.LIMIT || order.getType() == OrderType.STOP_LIMIT) {
            params.put("price", formatPrice(order.getPrice()));
        }

        if (order.getType() == OrderType.STOP || order.getType() == OrderType.STOP_LIMIT) {
            params.put("stopPrice", formatPrice(order.getStopPrice()));
        }

        return params;
    }

    private static OrderSide parseSide(String side) {
        if (side == null) return null;
        return switch (side.toUpperCase()) {
            case "BUY" -> OrderSide.BUY;
            case "SELL" -> OrderSide.SELL;
            default -> null;
        };
    }

    private static OrderType parseType(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "MARKET" -> OrderType.MARKET;
            case "LIMIT" -> OrderType.LIMIT;
            case "STOP_LOSS" -> OrderType.STOP;
            case "STOP_LOSS_LIMIT" -> OrderType.STOP_LIMIT;
            case "TAKE_PROFIT" -> OrderType.STOP;
            case "TAKE_PROFIT_LIMIT" -> OrderType.STOP_LIMIT;
            case "TRAILING_STOP_MARKET" -> OrderType.TRAILING_STOP;
            default -> null;
        };
    }

    private static TimeInForce parseTimeInForce(String tif) {
        if (tif == null) return TimeInForce.GTC;
        return switch (tif.toUpperCase()) {
            case "GTC" -> TimeInForce.GTC;
            case "IOC" -> TimeInForce.IOC;
            case "FOK" -> TimeInForce.FOK;
            default -> TimeInForce.GTC;
        };
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null) return OrderStatus.PENDING;
        return switch (status.toUpperCase()) {
            case "NEW" -> OrderStatus.ACCEPTED;
            case "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            case "CANCELED", "EXPIRED", "REJECTED" -> OrderStatus.CANCELLED;
            case "PENDING_CANCEL" -> OrderStatus.CANCELLED;
            default -> OrderStatus.PENDING;
        };
    }

    private static String formatSide(OrderSide side) {
        return side == OrderSide.BUY ? "BUY" : "SELL";
    }

    private static String formatType(OrderType type) {
        return switch (type) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP -> "STOP_LOSS";
            case STOP_LIMIT -> "STOP_LOSS_LIMIT";
            case TRAILING_STOP -> "TRAILING_STOP_MARKET";
        };
    }

    private static String formatTimeInForce(TimeInForce tif) {
        return switch (tif) {
            case DAY, GTC, GTD -> "GTC";
            case IOC -> "IOC";
            case FOK -> "FOK";
            case OPG, CLS -> "GTC";
        };
    }

    private static long parsePrice(String price) {
        if (price == null || price.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(price);
        return bd.multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private static long parseQuantity(String qty) {
        if (qty == null || qty.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(qty);
        // Quantity typically in whole units, scale by 100_000_000 for sub-unit precision
        return bd.multiply(BigDecimal.valueOf(100_000_000)).longValue();
    }

    private static String formatPrice(long price) {
        BigDecimal bd = BigDecimal.valueOf(price).divide(BigDecimal.valueOf(PRICE_SCALE));
        return bd.stripTrailingZeros().toPlainString();
    }

    private static String formatQuantity(long quantity) {
        BigDecimal bd = BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(100_000_000));
        return bd.stripTrailingZeros().toPlainString();
    }
}
