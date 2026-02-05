package com.hft.engine.handler;

import com.hft.core.model.*;
import com.hft.engine.event.EventType;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.service.PositionManager;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles position-related events from the disruptor.
 * Updates positions based on fills and market data.
 */
public class PositionHandler implements EventHandler<TradingEvent> {
    private static final Logger log = LoggerFactory.getLogger(PositionHandler.class);

    private final PositionManager positionManager;

    public PositionHandler(PositionManager positionManager) {
        this.positionManager = positionManager;
    }

    @Override
    public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
        switch (event.getType()) {
            case ORDER_FILLED -> handleFill(event);
            case QUOTE_UPDATE -> handleQuoteUpdate(event);
            default -> {
                // Not a position event, ignore
            }
        }
    }

    private void handleFill(TradingEvent event) {
        Symbol symbol = event.getSymbol();
        if (symbol == null) {
            log.warn("Fill event without symbol");
            return;
        }

        // Create trade from fill event
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(event.getSide());
        trade.setQuantity(event.getFilledQuantity());
        trade.setPrice(event.getFilledPrice());
        trade.setPriceScale(event.getPriceScale());
        trade.setClientOrderId(event.getClientOrderId());
        trade.setExchangeOrderId(event.getExchangeOrderId());
        trade.setCommission(event.getCommission());
        trade.setExecutedAt(event.getTimestampNanos());

        positionManager.applyTrade(trade);

        log.debug("Applied fill to position: {} {} {} @ {}",
                symbol, event.getSide(), event.getFilledQuantity(), event.getFilledPrice());
    }

    private void handleQuoteUpdate(TradingEvent event) {
        Symbol symbol = event.getSymbol();
        if (symbol == null) {
            return;
        }

        // Update position mark-to-market with mid price
        long midPrice = (event.getBidPrice() + event.getAskPrice()) / 2;
        positionManager.updateMarketValue(symbol, midPrice);
    }
}
