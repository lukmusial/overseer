package com.hft.exchange.alpaca.dto;

/**
 * DTO for Alpaca position API response.
 */
public class AlpacaPosition {
    private String assetId;
    private String symbol;
    private String exchange;
    private String assetClass;
    private String avgEntryPrice;
    private String qty;
    private String side;
    private String marketValue;
    private String costBasis;
    private String unrealizedPl;
    private String unrealizedPlpc;
    private String unrealizedIntradayPl;
    private String unrealizedIntradayPlpc;
    private String currentPrice;
    private String lastdayPrice;
    private String changeToday;

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getAvgEntryPrice() {
        return avgEntryPrice;
    }

    public void setAvgEntryPrice(String avgEntryPrice) {
        this.avgEntryPrice = avgEntryPrice;
    }

    public String getQty() {
        return qty;
    }

    public void setQty(String qty) {
        this.qty = qty;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(String marketValue) {
        this.marketValue = marketValue;
    }

    public String getCostBasis() {
        return costBasis;
    }

    public void setCostBasis(String costBasis) {
        this.costBasis = costBasis;
    }

    public String getUnrealizedPl() {
        return unrealizedPl;
    }

    public void setUnrealizedPl(String unrealizedPl) {
        this.unrealizedPl = unrealizedPl;
    }

    public String getUnrealizedPlpc() {
        return unrealizedPlpc;
    }

    public void setUnrealizedPlpc(String unrealizedPlpc) {
        this.unrealizedPlpc = unrealizedPlpc;
    }

    public String getUnrealizedIntradayPl() {
        return unrealizedIntradayPl;
    }

    public void setUnrealizedIntradayPl(String unrealizedIntradayPl) {
        this.unrealizedIntradayPl = unrealizedIntradayPl;
    }

    public String getUnrealizedIntradayPlpc() {
        return unrealizedIntradayPlpc;
    }

    public void setUnrealizedIntradayPlpc(String unrealizedIntradayPlpc) {
        this.unrealizedIntradayPlpc = unrealizedIntradayPlpc;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(String currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getLastdayPrice() {
        return lastdayPrice;
    }

    public void setLastdayPrice(String lastdayPrice) {
        this.lastdayPrice = lastdayPrice;
    }

    public String getChangeToday() {
        return changeToday;
    }

    public void setChangeToday(String changeToday) {
        this.changeToday = changeToday;
    }
}
