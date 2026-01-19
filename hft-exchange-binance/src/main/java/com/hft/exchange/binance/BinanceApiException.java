package com.hft.exchange.binance;

/**
 * Exception thrown when Binance API returns an error.
 */
public class BinanceApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String apiMessage;

    public BinanceApiException(int code, String apiMessage) {
        super("Binance API error: " + code + " - " + apiMessage);
        this.code = code;
        this.apiMessage = apiMessage;
    }

    public int getCode() {
        return code;
    }

    public String getApiMessage() {
        return apiMessage;
    }

    public boolean isRateLimited() {
        return code == -1015;
    }

    public boolean isInvalidApiKey() {
        return code == -2015;
    }

    public boolean isOrderNotFound() {
        return code == -2013;
    }

    public boolean isInsufficientBalance() {
        return code == -2010;
    }
}
