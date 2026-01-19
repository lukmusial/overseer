package com.hft.exchange.alpaca.dto;

/**
 * DTO for Alpaca order submission request.
 */
public class AlpacaOrderRequest {
    private String symbol;
    private String qty;
    private String side;
    private String type;
    private String timeInForce;
    private String limitPrice;
    private String stopPrice;
    private String clientOrderId;
    private boolean extendedHours;

    public static Builder builder() {
        return new Builder();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(String limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(String stopPrice) {
        this.stopPrice = stopPrice;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public boolean isExtendedHours() {
        return extendedHours;
    }

    public void setExtendedHours(boolean extendedHours) {
        this.extendedHours = extendedHours;
    }

    public static class Builder {
        private final AlpacaOrderRequest request = new AlpacaOrderRequest();

        public Builder symbol(String symbol) {
            request.symbol = symbol;
            return this;
        }

        public Builder qty(String qty) {
            request.qty = qty;
            return this;
        }

        public Builder qty(long qty) {
            request.qty = String.valueOf(qty);
            return this;
        }

        public Builder side(String side) {
            request.side = side;
            return this;
        }

        public Builder type(String type) {
            request.type = type;
            return this;
        }

        public Builder timeInForce(String timeInForce) {
            request.timeInForce = timeInForce;
            return this;
        }

        public Builder limitPrice(String limitPrice) {
            request.limitPrice = limitPrice;
            return this;
        }

        public Builder stopPrice(String stopPrice) {
            request.stopPrice = stopPrice;
            return this;
        }

        public Builder clientOrderId(String clientOrderId) {
            request.clientOrderId = clientOrderId;
            return this;
        }

        public Builder extendedHours(boolean extendedHours) {
            request.extendedHours = extendedHours;
            return this;
        }

        public AlpacaOrderRequest build() {
            return request;
        }
    }
}
