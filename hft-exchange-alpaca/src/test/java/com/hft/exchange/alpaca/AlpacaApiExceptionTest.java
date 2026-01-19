package com.hft.exchange.alpaca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaApiExceptionTest {

    @Test
    void shouldStoreStatusCodeAndBody() {
        AlpacaApiException exception = new AlpacaApiException(401, "Unauthorized");

        assertEquals(401, exception.getStatusCode());
        assertEquals("Unauthorized", exception.getResponseBody());
        assertTrue(exception.getMessage().contains("401"));
        assertTrue(exception.getMessage().contains("Unauthorized"));
    }

    @Test
    void shouldDetectRateLimiting() {
        AlpacaApiException rateLimited = new AlpacaApiException(429, "Too Many Requests");
        AlpacaApiException other = new AlpacaApiException(400, "Bad Request");

        assertTrue(rateLimited.isRateLimited());
        assertFalse(other.isRateLimited());
    }

    @Test
    void shouldDetectUnauthorized() {
        AlpacaApiException unauthorized401 = new AlpacaApiException(401, "Unauthorized");
        AlpacaApiException unauthorized403 = new AlpacaApiException(403, "Forbidden");
        AlpacaApiException other = new AlpacaApiException(400, "Bad Request");

        assertTrue(unauthorized401.isUnauthorized());
        assertTrue(unauthorized403.isUnauthorized());
        assertFalse(other.isUnauthorized());
    }

    @Test
    void shouldDetectNotFound() {
        AlpacaApiException notFound = new AlpacaApiException(404, "Not Found");
        AlpacaApiException other = new AlpacaApiException(400, "Bad Request");

        assertTrue(notFound.isNotFound());
        assertFalse(other.isNotFound());
    }

    @Test
    void shouldDetectServerErrors() {
        AlpacaApiException server500 = new AlpacaApiException(500, "Internal Server Error");
        AlpacaApiException server502 = new AlpacaApiException(502, "Bad Gateway");
        AlpacaApiException server503 = new AlpacaApiException(503, "Service Unavailable");
        AlpacaApiException clientError = new AlpacaApiException(400, "Bad Request");

        assertTrue(server500.isServerError());
        assertTrue(server502.isServerError());
        assertTrue(server503.isServerError());
        assertFalse(clientError.isServerError());
    }
}
