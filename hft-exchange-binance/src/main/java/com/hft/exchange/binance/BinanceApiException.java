package com.hft.exchange.binance;

/**
 * Exception thrown when Binance API returns an error.
 */
public class BinanceApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String message;

    public BinanceApiException(int code, String message) {
        super("Binance API error: " + code + " - " + message);
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
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
