package com.hft.persistence.chronicle;

import com.hft.core.model.Exchange;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

/**
 * Chronicle Wire format for Trade serialization.
 * Extends SelfDescribingMarshallable for efficient serialization.
 */
public class TradeWire extends SelfDescribingMarshallable {
    private long tradeId;
    private String exchangeTradeId;
    private long clientOrderId;
    private String exchangeOrderId;
    private String ticker;
    private String exchange;
    private String side;
    private long price;
    private long quantity;
    private long commission;
    private long executedAt;
    private long receivedAt;
    private boolean isMaker;

    public TradeWire() {
    }

    public static TradeWire from(Trade trade) {
        TradeWire wire = new TradeWire();
        wire.tradeId = trade.getTradeId();
        wire.exchangeTradeId = trade.getExchangeTradeId();
        wire.clientOrderId = trade.getClientOrderId();
        wire.exchangeOrderId = trade.getExchangeOrderId();
        wire.ticker = trade.getSymbol().getTicker();
        wire.exchange = trade.getSymbol().getExchange().name();
        wire.side = trade.getSide().name();
        wire.price = trade.getPrice();
        wire.quantity = trade.getQuantity();
        wire.commission = trade.getCommission();
        wire.executedAt = trade.getExecutedAt();
        wire.receivedAt = trade.getReceivedAt();
        wire.isMaker = trade.isMaker();
        return wire;
    }

    public Trade toTrade() {
        Trade trade = new Trade();
        trade.setExchangeTradeId(exchangeTradeId);
        trade.setClientOrderId(clientOrderId);
        trade.setExchangeOrderId(exchangeOrderId);
        trade.setSymbol(new Symbol(ticker, Exchange.valueOf(exchange)));
        trade.setSide(OrderSide.valueOf(side));
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setCommission(commission);
        trade.setExecutedAt(executedAt);
        trade.setReceivedAt(receivedAt);
        trade.setMaker(isMaker);
        return trade;
    }

    // Getters for direct access if needed
    public long getTradeId() { return tradeId; }
    public String getExchangeTradeId() { return exchangeTradeId; }
    public long getClientOrderId() { return clientOrderId; }
    public String getTicker() { return ticker; }
    public String getExchange() { return exchange; }
    public String getSide() { return side; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getExecutedAt() { return executedAt; }
}
