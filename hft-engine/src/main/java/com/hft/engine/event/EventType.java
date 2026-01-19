package com.hft.engine.event;

/**
 * Types of events processed by the trading engine.
 */
public enum EventType {
    // Order events
    NEW_ORDER,          // Request to submit new order
    CANCEL_ORDER,       // Request to cancel order
    MODIFY_ORDER,       // Request to modify order
    ORDER_ACCEPTED,     // Order accepted by exchange
    ORDER_REJECTED,     // Order rejected by exchange
    ORDER_FILLED,       // Order fully or partially filled
    ORDER_CANCELLED,    // Order cancelled
    ORDER_EXPIRED,      // Order expired

    // Market data events
    QUOTE_UPDATE,       // Best bid/ask update
    TRADE_UPDATE,       // Market trade occurred
    DEPTH_UPDATE,       // Order book depth update

    // System events
    RISK_BREACH,        // Risk limit breached
    POSITION_UPDATE,    // Position changed
    HEARTBEAT,          // System heartbeat
    SHUTDOWN            // Graceful shutdown signal
}
