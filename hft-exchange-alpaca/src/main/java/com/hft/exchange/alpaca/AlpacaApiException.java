package com.hft.exchange.alpaca;

/**
 * Exception thrown when Alpaca API returns an error.
 */
public class AlpacaApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String responseBody;

    public AlpacaApiException(int statusCode, String responseBody) {
        super("Alpaca API error: " + statusCode + " - " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isUnauthorized() {
        return statusCode == 401 || statusCode == 403;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }
}
