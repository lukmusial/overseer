package com.hft.exchange.alpaca;

import com.hft.core.model.*;
import com.hft.exchange.alpaca.dto.AlpacaOrder;
import com.hft.exchange.alpaca.dto.AlpacaOrderRequest;

import java.math.BigDecimal;

/**
 * Maps between Alpaca DTOs and core domain models.
 */
public class AlpacaOrderMapper {
    private static final int PRICE_SCALE = 100; // cents

    /**
     * Converts an Alpaca order response to a core Order.
     */
    public static Order toOrder(AlpacaOrder alpacaOrder) {
        Order order = new Order();
        order.setExchangeOrderId(alpacaOrder.getId());

        if (alpacaOrder.getClientOrderId() != null) {
            try {
                order.setClientOrderId(Long.parseLong(alpacaOrder.getClientOrderId()));
            } catch (NumberFormatException e) {
                // Client order ID might be a UUID, ignore
            }
        }

        order.setSymbol(new Symbol(alpacaOrder.getSymbol(), Exchange.ALPACA));
        order.setSide(parseSide(alpacaOrder.getSide()));
        order.setType(parseType(alpacaOrder.getType()));
        order.setTimeInForce(parseTimeInForce(alpacaOrder.getTimeInForce()));
        order.setStatus(parseStatus(alpacaOrder.getStatus()));

        if (alpacaOrder.getQty() != null) {
            order.setQuantity(Long.parseLong(alpacaOrder.getQty()));
        }

        if (alpacaOrder.getFilledQty() != null) {
            order.setFilledQuantity(Long.parseLong(alpacaOrder.getFilledQty()));
        }

        if (alpacaOrder.getLimitPrice() != null) {
            order.setPrice(parsePrice(alpacaOrder.getLimitPrice()));
        }

        if (alpacaOrder.getFilledAvgPrice() != null) {
            order.setAverageFilledPrice(parsePrice(alpacaOrder.getFilledAvgPrice()));
        }

        if (alpacaOrder.getStopPrice() != null) {
            order.setStopPrice(parsePrice(alpacaOrder.getStopPrice()));
        }

        return order;
    }

    /**
     * Converts a core Order to an Alpaca order request.
     */
    public static AlpacaOrderRequest toRequest(Order order) {
        AlpacaOrderRequest.Builder builder = AlpacaOrderRequest.builder()
                .symbol(order.getSymbol().getTicker())
                .qty(order.getQuantity())
                .side(formatSide(order.getSide()))
                .type(formatType(order.getType()))
                .timeInForce(formatTimeInForce(order.getTimeInForce()))
                .clientOrderId(String.valueOf(order.getClientOrderId()));

        if (order.getType() == OrderType.LIMIT || order.getType() == OrderType.STOP_LIMIT) {
            builder.limitPrice(formatPrice(order.getPrice()));
        }

        if (order.getType() == OrderType.STOP || order.getType() == OrderType.STOP_LIMIT) {
            builder.stopPrice(formatPrice(order.getStopPrice()));
        }

        return builder.build();
    }

    private static OrderSide parseSide(String side) {
        if (side == null) return null;
        return switch (side.toLowerCase()) {
            case "buy" -> OrderSide.BUY;
            case "sell" -> OrderSide.SELL;
            default -> null;
        };
    }

    private static OrderType parseType(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "market" -> OrderType.MARKET;
            case "limit" -> OrderType.LIMIT;
            case "stop" -> OrderType.STOP;
            case "stop_limit" -> OrderType.STOP_LIMIT;
            default -> null;
        };
    }

    private static TimeInForce parseTimeInForce(String tif) {
        if (tif == null) return TimeInForce.DAY;
        return switch (tif.toLowerCase()) {
            case "day" -> TimeInForce.DAY;
            case "gtc" -> TimeInForce.GTC;
            case "ioc" -> TimeInForce.IOC;
            case "fok" -> TimeInForce.FOK;
            default -> TimeInForce.DAY;
        };
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null) return OrderStatus.PENDING;
        return switch (status.toLowerCase()) {
            case "new", "accepted", "pending_new" -> OrderStatus.ACCEPTED;
            case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
            case "filled" -> OrderStatus.FILLED;
            case "canceled", "expired", "done_for_day", "pending_cancel", "pending_replace" -> OrderStatus.CANCELLED;
            case "rejected" -> OrderStatus.REJECTED;
            default -> OrderStatus.PENDING;
        };
    }

    private static String formatSide(OrderSide side) {
        return side == OrderSide.BUY ? "buy" : "sell";
    }

    private static String formatType(OrderType type) {
        return switch (type) {
            case MARKET -> "market";
            case LIMIT -> "limit";
            case STOP -> "stop";
            case STOP_LIMIT -> "stop_limit";
            case TRAILING_STOP -> "trailing_stop";
        };
    }

    private static String formatTimeInForce(TimeInForce tif) {
        return switch (tif) {
            case DAY -> "day";
            case GTC -> "gtc";
            case IOC -> "ioc";
            case FOK -> "fok";
            case GTD -> "gtd";
            case OPG -> "opg";
            case CLS -> "cls";
        };
    }

    private static long parsePrice(String price) {
        if (price == null || price.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(price);
        return bd.multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private static String formatPrice(long price) {
        BigDecimal bd = BigDecimal.valueOf(price).divide(BigDecimal.valueOf(PRICE_SCALE));
        return bd.toPlainString();
    }
}
