package com.hft.bdd.steps;

import com.hft.core.metrics.OrderMetrics;
import com.hft.core.model.*;

/**
 * Shared context for Cucumber step definitions.
 * Uses PicoContainer for dependency injection between step classes.
 */
public class SharedContext {
    private Symbol symbol;
    private Order currentOrder;
    private Position position;
    private OrderMetrics metrics;
    private boolean initialized = false;

    public void initialize() {
        if (!initialized) {
            metrics = new OrderMetrics();
            initialized = true;
        }
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
        if (position == null || !symbol.equals(position.getSymbol())) {
            position = new Position(symbol);
        }
    }

    public Order getCurrentOrder() {
        return currentOrder;
    }

    public void setCurrentOrder(Order currentOrder) {
        this.currentOrder = currentOrder;
    }

    public Position getPosition() {
        if (position == null && symbol != null) {
            position = new Position(symbol);
        }
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public OrderMetrics getMetrics() {
        if (metrics == null) {
            metrics = new OrderMetrics();
        }
        return metrics;
    }

    public void reset() {
        symbol = null;
        currentOrder = null;
        position = null;
        if (metrics != null) {
            metrics.reset();
        }
    }
}
