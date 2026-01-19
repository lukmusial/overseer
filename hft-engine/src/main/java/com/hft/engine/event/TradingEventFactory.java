package com.hft.engine.event;

import com.lmax.disruptor.EventFactory;

/**
 * Factory for creating TradingEvent instances in the ring buffer.
 */
public class TradingEventFactory implements EventFactory<TradingEvent> {

    public static final TradingEventFactory INSTANCE = new TradingEventFactory();

    @Override
    public TradingEvent newInstance() {
        return new TradingEvent();
    }
}
