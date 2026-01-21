package com.hft.exchange.alpaca;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.exchange.alpaca.dto.AlpacaAsset;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for Alpaca REST API.
 */
public class AlpacaHttpClient {
    private static final Logger log = LoggerFactory.getLogger(AlpacaHttpClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final AlpacaConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AlpacaHttpClient(AlpacaConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Performs a GET request to the trading API.
     */
    public <T> CompletableFuture<T> get(String path, Class<T> responseType) {
        String url = config.getTradingUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .get()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a GET request to the market data API.
     */
    public <T> CompletableFuture<T> getMarketData(String path, Class<T> responseType) {
        String url = config.getMarketDataUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .get()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a POST request to the trading API.
     */
    public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
        String url = config.getTradingUrl() + path;
        try {
            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .headers(buildHeaders())
                    .post(requestBody)
                    .build();

            return executeAsync(request, responseType);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Performs a PATCH request to the trading API.
     */
    public <T> CompletableFuture<T> patch(String path, Object body, Class<T> responseType) {
        String url = config.getTradingUrl() + path;
        try {
            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .headers(buildHeaders())
                    .patch(requestBody)
                    .build();

            return executeAsync(request, responseType);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Performs a DELETE request to the trading API.
     */
    public <T> CompletableFuture<T> delete(String path, Class<T> responseType) {
        String url = config.getTradingUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .delete()
                .build();

        return executeAsync(request, responseType);
    }

    /**
     * Performs a DELETE request without expecting a response body.
     */
    public CompletableFuture<Void> delete(String path) {
        String url = config.getTradingUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders())
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
                    if (response.isSuccessful()) {
                        future.complete(null);
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No body";
                        future.completeExceptionally(new AlpacaApiException(
                                response.code(), errorBody));
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    private Headers buildHeaders() {
        return new Headers.Builder()
                .add("APCA-API-KEY-ID", config.apiKey())
                .add("APCA-API-SECRET-KEY", config.secretKey())
                .add("Content-Type", "application/json")
                .build();
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
                        log.error("API error: {} {} - {} {}",
                                request.method(), request.url(), response.code(), body);
                        future.completeExceptionally(new AlpacaApiException(response.code(), body));
                    }
                } catch (Exception e) {
                    log.error("Error parsing response", e);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    /**
     * Fetches all available assets from Alpaca.
     * @param assetClass Filter by asset class: "us_equity", "crypto", or null for all
     * @param status Filter by status: "active", "inactive", or null for all
     */
    public CompletableFuture<List<AlpacaAsset>> getAssets(String assetClass, String status) {
        StringBuilder path = new StringBuilder("/v2/assets");
        boolean hasParam = false;

        if (status != null && !status.isBlank()) {
            path.append("?status=").append(status);
            hasParam = true;
        }

        if (assetClass != null && !assetClass.isBlank()) {
            path.append(hasParam ? "&" : "?").append("asset_class=").append(assetClass);
        }

        String url = config.getTradingUrl() + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .get()
                .build();

        CompletableFuture<List<AlpacaAsset>> future = new CompletableFuture<>();

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

                    if (response.isSuccessful() && body != null) {
                        List<AlpacaAsset> assets = objectMapper.readValue(body,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, AlpacaAsset.class));
                        future.complete(assets);
                    } else {
                        log.error("API error: {} {} - {} {}",
                                request.method(), request.url(), response.code(), body);
                        future.completeExceptionally(new AlpacaApiException(response.code(), body));
                    }
                } catch (Exception e) {
                    log.error("Error parsing response", e);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    /**
     * Fetches active tradable equity assets from Alpaca.
     */
    public CompletableFuture<List<AlpacaAsset>> getActiveEquities() {
        return getAssets("us_equity", "active");
    }

    /**
     * Fetches active tradable crypto assets from Alpaca.
     */
    public CompletableFuture<List<AlpacaAsset>> getActiveCrypto() {
        return getAssets("crypto", "active");
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
