package com.hft.engine.handler;

import com.hft.core.metrics.OrderMetrics;
import com.hft.engine.event.EventType;
import com.hft.engine.event.TradingEvent;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles metrics collection from the disruptor.
 * Records latencies and throughput statistics.
 */
public class MetricsHandler implements EventHandler<TradingEvent> {
    private static final Logger log = LoggerFactory.getLogger(MetricsHandler.class);

    private final OrderMetrics orderMetrics;
    private long lastEventTime = System.nanoTime();

    public MetricsHandler(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    @Override
    public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
        long now = System.nanoTime();
        long eventLatency = now - event.getTimestampNanos();

        switch (event.getType()) {
            case NEW_ORDER -> {
                orderMetrics.recordOrderSubmitted();
                orderMetrics.recordSubmitLatency(eventLatency);
            }
            case ORDER_ACCEPTED -> {
                orderMetrics.recordOrderAccepted(eventLatency);
            }
            case ORDER_FILLED -> {
                orderMetrics.recordOrderFilled(
                        eventLatency,
                        event.getFilledQuantity(),
                        event.getFilledPrice()
                );
            }
            case ORDER_REJECTED -> {
                orderMetrics.recordOrderRejected();
            }
            case ORDER_CANCELLED -> {
                orderMetrics.recordOrderCancelled(eventLatency);
            }
            default -> {
                // Non-order events, skip
            }
        }

        lastEventTime = now;
    }

    public OrderMetrics getMetrics() {
        return orderMetrics;
    }
}
