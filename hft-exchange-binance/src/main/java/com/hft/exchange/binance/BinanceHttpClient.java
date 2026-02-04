package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.exchange.binance.dto.BinanceExchangeInfo;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * HTTP client for Binance REST API with HMAC signature support.
 */
public class BinanceHttpClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceHttpClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final BinanceConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BinanceHttpClient(BinanceConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Performs a public GET request (no signature required).
     */
    public <T> CompletableFuture<T> publicGet(String path, Class<T> responseType) {
        String url = config.getBaseUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a signed GET request.
     */
    public <T> CompletableFuture<T> signedGet(String path, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + path + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", config.apiKey())
                .get()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a signed POST request.
     */
    public <T> CompletableFuture<T> signedPost(String path, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + path;

        RequestBody body = RequestBody.create(queryString, MediaType.parse("application/x-www-form-urlencoded"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", config.apiKey())
                .post(body)
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a signed DELETE request.
     */
    public <T> CompletableFuture<T> signedDelete(String path, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + path + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", config.apiKey())
                .delete()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a signed DELETE request without response body.
     */
    public CompletableFuture<Void> signedDelete(String path, Map<String, String> params) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + path + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", config.apiKey())
                .delete()
                .build();

        CompletableFuture<Void> future = new CompletableFuture<>();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Request failed: {} {}", request.method(), request.url(), e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    String body = response.body() != null ? response.body().string() : null;

                    if (response.isSuccessful()) {
                        future.complete(null);
                    } else {
                        BinanceApiException ex = parseError(body);
                        future.completeExceptionally(ex);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    private String buildSignedQueryString(Map<String, String> params) {
        // Add timestamp
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        // Build query string
        String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        // Add signature
        String signature = sign(queryString);
        return queryString + "&signature=" + signature;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    config.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error signing request", e);
        }
    }

    private <T> CompletableFuture<T> executeAsync(Request request, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Request failed: {} {}", request.method(), request.url(), e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    String body = response.body() != null ? response.body().string() : null;

                    if (response.isSuccessful()) {
                        if (body != null && !body.isBlank()) {
                            T result = objectMapper.readValue(body, responseType);
                            future.complete(result);
                        } else {
                            future.complete(null);
                        }
                    } else {
                        log.error("API error: {} {} - {}", request.method(), request.url(), body);
                        BinanceApiException ex = parseError(body);
                        future.completeExceptionally(ex);
                    }
                } catch (Exception e) {
                    log.error("Error parsing response", e);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    private BinanceApiException parseError(String body) {
        if (body == null || body.isBlank()) {
            return new BinanceApiException(-1, "No response body");
        }

        try {
            JsonNode node = objectMapper.readTree(body);
            int code = node.path("code").asInt(-1);
            String msg = node.path("msg").asText("Unknown error");
            return new BinanceApiException(code, msg);
        } catch (Exception e) {
            return new BinanceApiException(-1, body);
        }
    }

    /**
     * Fetches exchange information including all available trading symbols.
     * This is a public endpoint that does not require authentication.
     */
    public CompletableFuture<BinanceExchangeInfo> getExchangeInfo() {
        return publicGet("/api/v3/exchangeInfo", BinanceExchangeInfo.class);
    }

    /**
     * Fetches kline/candlestick data for a symbol.
     * This is a public endpoint that does not require authentication.
     *
     * @param symbol   Trading pair symbol (e.g., "BTCUSDT")
     * @param interval Kline interval (e.g., "1m", "5m", "15m", "1h", "4h", "1d")
     * @param limit    Number of klines to return (max 1000)
     * @return JSON array of arrays: [[openTime, open, high, low, close, volume, ...], ...]
     */
    public CompletableFuture<JsonNode> getKlines(String symbol, String interval, int limit) {
        String path = "/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit;
        return publicGet(path, JsonNode.class);
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
