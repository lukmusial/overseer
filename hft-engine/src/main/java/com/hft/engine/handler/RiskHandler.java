package com.hft.engine.handler;

import com.hft.engine.event.EventType;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.service.RiskManager;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles risk-related events from the disruptor.
 * Pre-trade risk checks and position monitoring.
 */
public class RiskHandler implements EventHandler<TradingEvent> {
    private static final Logger log = LoggerFactory.getLogger(RiskHandler.class);

    private final RiskManager riskManager;

    public RiskHandler(RiskManager riskManager) {
        this.riskManager = riskManager;
    }

    @Override
    public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
        switch (event.getType()) {
            case NEW_ORDER -> checkPreTradeRisk(event);
            case ORDER_FILLED -> updateRiskMetrics(event);
            case QUOTE_UPDATE -> checkPositionRisk(event);
            default -> {
                // Not a risk event, ignore
            }
        }
    }

    private void checkPreTradeRisk(TradingEvent event) {
        // Pre-trade risk checks happen synchronously before order submission
        // This handler logs risk decisions for audit purposes
        if (log.isDebugEnabled()) {
            log.debug("Pre-trade risk check for order: {} {} {} @ {}",
                    event.getSymbol(), event.getSide(), event.getQuantity(), event.getPrice());
        }
    }

    private void updateRiskMetrics(TradingEvent event) {
        // Update risk metrics after fill
        riskManager.recordFill(
                event.getSymbol(),
                event.getSide(),
                event.getFilledQuantity(),
                event.getFilledPrice()
        );
    }

    private void checkPositionRisk(TradingEvent event) {
        // Check position-level risk on quote updates
        if (event.getSymbol() != null) {
            riskManager.checkPositionLimits(event.getSymbol());
        }
    }
}
