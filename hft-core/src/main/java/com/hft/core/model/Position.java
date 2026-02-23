package com.hft.core.model;

/**
 * Position tracking for a symbol.
 * Mutable for real-time updates - thread-safe operations recommended.
 */
public class Position {
    private Symbol symbol;

    // Position size (positive = long, negative = short)
    private long quantity;

    // Cost basis and P&L in minor units
    private long averageEntryPrice;
    private long totalCost;
    private long realizedPnl;
    private long unrealizedPnl;

    // Market value
    private long currentPrice;
    private long marketValue;

    // Risk metrics
    private long maxPositionValue;
    private long maxDrawdown;

    private int priceScale = 100;
    private int quantityScale = 1;

    // Timestamps
    private long openedAt;
    private long lastUpdatedAt;

    public Position() {
        this.lastUpdatedAt = System.nanoTime();
    }

    public Position(Symbol symbol) {
        this.symbol = symbol;
        this.lastUpdatedAt = System.nanoTime();
    }

    public void reset() {
        symbol = null;
        quantity = 0;
        averageEntryPrice = 0;
        totalCost = 0;
        realizedPnl = 0;
        unrealizedPnl = 0;
        currentPrice = 0;
        marketValue = 0;
        maxPositionValue = 0;
        maxDrawdown = 0;
        openedAt = 0;
        lastUpdatedAt = System.nanoTime();
    }

    /**
     * Updates position after a trade execution.
     */
    public void applyTrade(Trade trade) {
        // Update price scale from trade (important for correct P&L display)
        if (trade.getPriceScale() > 0) {
            this.priceScale = trade.getPriceScale();
        }

        long tradeQty = trade.getSide() == OrderSide.BUY ? trade.getQuantity() : -trade.getQuantity();
        long tradePrice = trade.getPrice();
        long tradeCost = (long) ((double) tradePrice * Math.abs(trade.getQuantity()) / priceScale / quantityScale);

        if (quantity == 0) {
            // Opening new position
            openedAt = trade.getExecutedAt();
            quantity = tradeQty;
            averageEntryPrice = tradePrice;
            totalCost = tradeCost;
        } else if ((quantity > 0 && tradeQty > 0) || (quantity < 0 && tradeQty < 0)) {
            // Adding to existing position - use double to avoid overflow
            long totalQty = Math.abs(quantity) + Math.abs(tradeQty);
            averageEntryPrice = (long) ((double) averageEntryPrice * Math.abs(quantity) / totalQty
                    + (double) tradePrice * Math.abs(tradeQty) / totalQty);
            quantity += tradeQty;
            totalCost += tradeCost;
        } else {
            // Reducing or reversing position
            long absTradeQty = Math.abs(tradeQty);
            long absPositionQty = Math.abs(quantity);

            if (absTradeQty <= absPositionQty) {
                // Partial close - P&L in priceScale units, divided by quantityScale
                long closedPnl = (long) ((double) (tradePrice - averageEntryPrice) * absTradeQty / quantityScale);
                if (quantity < 0) {
                    closedPnl = -closedPnl; // Reverse for short positions
                }
                realizedPnl += closedPnl;
                quantity += tradeQty;
                totalCost = (long) ((double) averageEntryPrice * Math.abs(quantity) / priceScale / quantityScale);

                if (quantity == 0) {
                    averageEntryPrice = 0;
                    totalCost = 0;
                }
            } else {
                // Full close and reverse - P&L in priceScale units, divided by quantityScale
                long closedPnl = (long) ((double) (tradePrice - averageEntryPrice) * absPositionQty / quantityScale);
                if (quantity < 0) {
                    closedPnl = -closedPnl;
                }
                realizedPnl += closedPnl;

                // New position in opposite direction
                long newQty = absTradeQty - absPositionQty;
                quantity = tradeQty > 0 ? newQty : -newQty;
                averageEntryPrice = tradePrice;
                totalCost = (long) ((double) tradePrice * newQty / priceScale / quantityScale);
                openedAt = trade.getExecutedAt();
            }
        }

        updateMarketValue(tradePrice);
        lastUpdatedAt = System.nanoTime();
    }

    /**
     * Updates market value and unrealized P&L based on current price.
     */
    public void updateMarketValue(long price) {
        this.currentPrice = price;
        this.marketValue = (long) ((double) price * Math.abs(quantity) / priceScale / quantityScale);

        if (quantity != 0) {
            // Unrealized P&L in priceScale units, divided by quantityScale
            unrealizedPnl = (long) ((double) (price - averageEntryPrice) * quantity / quantityScale);
        } else {
            unrealizedPnl = 0;
        }

        // Track max position value for risk metrics
        if (marketValue > maxPositionValue) {
            maxPositionValue = marketValue;
        }

        // Track drawdown
        long totalPnl = realizedPnl + unrealizedPnl;
        if (totalPnl < maxDrawdown) {
            maxDrawdown = totalPnl;
        }

        lastUpdatedAt = System.nanoTime();
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    public boolean isLong() {
        return quantity > 0;
    }

    public boolean isShort() {
        return quantity < 0;
    }

    public long getTotalPnl() {
        return realizedPnl + unrealizedPnl;
    }

    // Getters and setters
    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getAverageEntryPrice() {
        return averageEntryPrice;
    }

    public void setAverageEntryPrice(long averageEntryPrice) {
        this.averageEntryPrice = averageEntryPrice;
    }

    public long getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(long totalCost) {
        this.totalCost = totalCost;
    }

    public long getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(long realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public long getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public void setUnrealizedPnl(long unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public long getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(long currentPrice) {
        this.currentPrice = currentPrice;
    }

    public long getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(long marketValue) {
        this.marketValue = marketValue;
    }

    public long getMaxPositionValue() {
        return maxPositionValue;
    }

    public long getMaxDrawdown() {
        return maxDrawdown;
    }

    public int getPriceScale() {
        return priceScale;
    }

    public void setPriceScale(int priceScale) {
        this.priceScale = priceScale;
    }

    public int getQuantityScale() {
        return quantityScale;
    }

    public void setQuantityScale(int quantityScale) {
        this.quantityScale = quantityScale;
    }

    public double getQuantityAsDouble() {
        return (double) quantity / quantityScale;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(long openedAt) {
        this.openedAt = openedAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(long lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public double getAverageEntryPriceAsDouble() {
        return (double) averageEntryPrice / priceScale;
    }

    public double getCurrentPriceAsDouble() {
        return (double) currentPrice / priceScale;
    }

    @Override
    public String toString() {
        return String.format("Position{%s qty=%.8g, avgEntry=%.4f, unrealizedPnl=%.2f, realizedPnl=%.2f}",
                symbol, getQuantityAsDouble(), getAverageEntryPriceAsDouble(),
                (double) unrealizedPnl / priceScale, (double) realizedPnl / priceScale);
    }
}
