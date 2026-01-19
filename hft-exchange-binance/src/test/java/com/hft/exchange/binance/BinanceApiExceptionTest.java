package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceApiExceptionTest {

    @Test
    void shouldStoreCodeAndMessage() {
        BinanceApiException exception = new BinanceApiException(-1015, "Too many requests");

        assertEquals(-1015, exception.getCode());
        assertEquals("Too many requests", exception.getApiMessage());
        assertTrue(exception.getMessage().contains("-1015"));
        assertTrue(exception.getMessage().contains("Too many requests"));
    }

    @Test
    void shouldDetectRateLimiting() {
        BinanceApiException rateLimited = new BinanceApiException(-1015, "Too many requests");
        BinanceApiException other = new BinanceApiException(-1000, "Unknown error");

        assertTrue(rateLimited.isRateLimited());
        assertFalse(other.isRateLimited());
    }

    @Test
    void shouldDetectInvalidApiKey() {
        BinanceApiException invalidKey = new BinanceApiException(-2015, "Invalid API-key");
        BinanceApiException other = new BinanceApiException(-1000, "Unknown error");

        assertTrue(invalidKey.isInvalidApiKey());
        assertFalse(other.isInvalidApiKey());
    }

    @Test
    void shouldDetectOrderNotFound() {
        BinanceApiException notFound = new BinanceApiException(-2013, "Order does not exist");
        BinanceApiException other = new BinanceApiException(-1000, "Unknown error");

        assertTrue(notFound.isOrderNotFound());
        assertFalse(other.isOrderNotFound());
    }

    @Test
    void shouldDetectInsufficientBalance() {
        BinanceApiException insufficientBalance = new BinanceApiException(-2010, "Account has insufficient balance");
        BinanceApiException other = new BinanceApiException(-1000, "Unknown error");

        assertTrue(insufficientBalance.isInsufficientBalance());
        assertFalse(other.isInsufficientBalance());
    }

    @Test
    void shouldIncludeCodeInExceptionMessage() {
        BinanceApiException exception = new BinanceApiException(-1015, "Too many requests");

        assertTrue(exception.getMessage().contains("-1015"));
        assertTrue(exception.getMessage().contains("Binance"));
    }
}
