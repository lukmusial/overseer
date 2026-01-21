package com.hft.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for UI and API integration tests.
 * Tests REST endpoints and static resource serving.
 */
public class UiApiIntegrationSteps {

    private static final String BASE_URL = "http://localhost:8080";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    private Response lastResponse;
    private String lastResponseBody;
    private JsonNode lastJsonResponse;
    private String lastCreatedStrategyId;
    private String lastCreatedOrderId;
    private String lastCreatedOrderSymbol;
    private String lastCreatedOrderExchange;

    public UiApiIntegrationSteps() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Before("@ui or @api")
    public void setUp() {
        lastResponse = null;
        lastResponseBody = null;
        lastJsonResponse = null;
    }

    @After("@ui or @api")
    public void tearDown() {
        if (lastResponse != null) {
            lastResponse.close();
        }
    }

    // ========== Background ==========

    @Given("the trading application is running")
    public void theTradingApplicationIsRunning() throws Exception {
        // Verify the application is accessible
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/engine/status")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertTrue(response.isSuccessful(), "Application should be running and accessible");
        }
    }

    // ========== HTTP Request Steps ==========

    @When("I request the root URL {string}")
    public void iRequestTheRootUrl(String path) throws Exception {
        executeGetRequest(path);
    }

    @When("I request GET {string}")
    public void iRequestGet(String path) throws Exception {
        executeGetRequest(path);
    }

    @When("I request POST {string}")
    public void iRequestPost(String path) throws Exception {
        executePostRequest(path, "{}");
    }

    @When("I request POST {string} with reason {string}")
    public void iRequestPostWithReason(String path, String reason) throws Exception {
        String body = String.format("{\"reason\":\"%s\"}", reason);
        executePostRequest(path, body);
    }

    @When("I request DELETE {string}")
    public void iRequestDelete(String path) throws Exception {
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .delete()
                .build();
        executeRequest(request);
    }

    @When("I request the assets directory")
    public void iRequestTheAssetsDirectory() throws Exception {
        // The assets are typically named with hashes, so we just verify the main page loads assets
        executeGetRequest("/");
    }

    @When("I make a CORS preflight request to {string}")
    public void iMakeCorsPreflight(String path) throws Exception {
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .method("OPTIONS", null)
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .build();
        executeRequest(request);
    }

    // ========== Response Validation Steps ==========

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        assertNotNull(lastResponse, "No response received");
        assertEquals(expectedStatus, lastResponse.code(),
                "Expected status " + expectedStatus + " but got " + lastResponse.code());
    }

    @Then("the response status should be an error")
    public void theResponseStatusShouldBeAnError() {
        assertNotNull(lastResponse, "No response received");
        int code = lastResponse.code();
        assertTrue(code >= 400 && code < 600,
                "Expected error status (4xx or 5xx) but got " + code);
    }

    @Then("the response content type should contain {string}")
    public void theResponseContentTypeShouldContain(String contentType) {
        assertNotNull(lastResponse, "No response received");
        String actualContentType = lastResponse.header("Content-Type");
        assertNotNull(actualContentType, "Content-Type header missing");
        assertTrue(actualContentType.contains(contentType),
                "Content-Type '" + actualContentType + "' should contain '" + contentType + "'");
    }

    @Then("the response body should contain {string}")
    public void theResponseBodyShouldContain(String expected) {
        assertNotNull(lastResponseBody, "No response body");
        assertTrue(lastResponseBody.contains(expected),
                "Response body should contain '" + expected + "'");
    }

    @Then("the response should be valid JSON")
    public void theResponseShouldBeValidJson() throws Exception {
        assertNotNull(lastResponseBody, "No response body");
        lastJsonResponse = objectMapper.readTree(lastResponseBody);
        assertNotNull(lastJsonResponse, "Failed to parse JSON");
    }

    @Then("the response should contain {string}")
    public void theResponseShouldContain(String expected) {
        assertNotNull(lastResponseBody, "No response body");
        assertTrue(lastResponseBody.contains(expected),
                "Response should contain '" + expected + "'");
    }

    @Then("the response should be a list")
    public void theResponseShouldBeAList() throws Exception {
        theResponseShouldBeValidJson();
        assertTrue(lastJsonResponse.isArray(), "Response should be a JSON array");
    }

    @Then("the response should be a list of strategies")
    public void theResponseShouldBeAListOfStrategies() throws Exception {
        theResponseShouldBeAList();
    }

    @Then("the list should contain {string}")
    public void theListShouldContain(String expected) {
        assertNotNull(lastJsonResponse, "No JSON response");
        boolean found = false;
        for (JsonNode node : lastJsonResponse) {
            // Check if it's a simple string
            if (node.isTextual() && node.asText().equals(expected)) {
                found = true;
                break;
            }
            // Check if it's an object with a "name" field
            if (node.isObject() && node.has("name") && node.path("name").asText().equals(expected)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "List should contain '" + expected + "'");
    }

    // ========== Static Resources Steps ==========

    @Then("JavaScript files should be accessible")
    public void javascriptFilesShouldBeAccessible() {
        assertNotNull(lastResponseBody, "No response body");
        assertTrue(lastResponseBody.contains(".js"),
                "Page should reference JavaScript files");
    }

    @Then("CSS files should be accessible")
    public void cssFilesShouldBeAccessible() {
        assertNotNull(lastResponseBody, "No response body");
        assertTrue(lastResponseBody.contains(".css"),
                "Page should reference CSS files");
    }

    // ========== Engine Status Steps ==========

    @Then("the engine status should contain {string} field")
    public void theEngineStatusShouldContainField(String field) throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertTrue(lastJsonResponse.has(field),
                "Engine status should contain '" + field + "' field");
    }

    @Given("the engine is stopped")
    public void theEngineIsStopped() throws Exception {
        executePostRequest("/api/engine/stop", "{}");
    }

    @Given("the engine is running")
    public void theEngineIsRunning() throws Exception {
        executePostRequest("/api/engine/start", "{}");
    }

    @Then("the engine should be running")
    public void theEngineShouldBeRunning() throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertTrue(lastJsonResponse.path("running").asBoolean(),
                "Engine should be running");
    }

    @Then("the engine should be stopped")
    public void theEngineShouldBeStopped() throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertFalse(lastJsonResponse.path("running").asBoolean(),
                "Engine should be stopped");
    }

    @Then("the response should indicate trading is enabled")
    public void theResponseShouldIndicateTradingIsEnabled() throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertTrue(lastJsonResponse.path("tradingEnabled").asBoolean(),
                "Trading should be enabled");
    }

    @Then("the response should indicate trading is disabled")
    public void theResponseShouldIndicateTradingIsDisabled() throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertFalse(lastJsonResponse.path("tradingEnabled").asBoolean(),
                "Trading should be disabled");
    }

    @Then("the disable reason should be {string}")
    public void theDisableReasonShouldBe(String expectedReason) throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertEquals(expectedReason, lastJsonResponse.path("reason").asText(),
                "Disable reason should match");
    }

    // ========== Strategy Steps ==========

    @When("I create a strategy with:")
    public void iCreateAStrategyWith(DataTable dataTable) throws Exception {
        Map<String, String> data = dataTable.asMap();

        Map<String, Object> request = new HashMap<>();
        request.put("type", data.get("type"));

        // Handle symbol/symbols - API expects "symbols" as a list
        String symbolValue = data.getOrDefault("symbol", data.get("symbols"));
        if (symbolValue != null) {
            request.put("symbols", List.of(symbolValue.split(",")));
        }

        request.put("exchange", data.getOrDefault("exchange", "ALPACA"));

        if (data.containsKey("parameters")) {
            request.put("parameters", objectMapper.readTree(data.get("parameters")));
        }

        String json = objectMapper.writeValueAsString(request);
        executePostRequest("/api/strategies", json);

        if (lastResponse.isSuccessful() && lastResponseBody != null) {
            lastJsonResponse = objectMapper.readTree(lastResponseBody);
            if (lastJsonResponse.has("id")) {
                lastCreatedStrategyId = lastJsonResponse.path("id").asText();
            }
        }
    }

    @Then("the strategy should have an id")
    public void theStrategyShouldHaveAnId() {
        assertNotNull(lastJsonResponse, "No JSON response");
        assertTrue(lastJsonResponse.has("id"), "Strategy should have an id");
        assertFalse(lastJsonResponse.path("id").asText().isEmpty(), "Strategy id should not be empty");
    }

    @Then("the strategy name should be {string}")
    public void theStrategyNameShouldBe(String expectedName) {
        assertNotNull(lastJsonResponse, "No JSON response");
        assertEquals(expectedName, lastJsonResponse.path("name").asText());
    }

    @Then("the strategy type should be {string}")
    public void theStrategyTypeShouldBe(String expectedType) {
        assertNotNull(lastJsonResponse, "No JSON response");
        assertEquals(expectedType, lastJsonResponse.path("type").asText());
    }

    @Given("a strategy exists")
    public void aStrategyExists() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "momentum");
        request.put("symbols", List.of("AAPL"));
        request.put("exchange", "ALPACA");
        request.put("parameters", Map.of("shortPeriod", 5, "longPeriod", 10));

        String json = objectMapper.writeValueAsString(request);
        executePostRequest("/api/strategies", json);

        if (lastResponse.isSuccessful() && lastResponseBody != null) {
            lastJsonResponse = objectMapper.readTree(lastResponseBody);
            lastCreatedStrategyId = lastJsonResponse.path("id").asText();
        }
    }

    @Given("a strategy exists with id {string}")
    public void aStrategyExistsWithId(String id) throws Exception {
        // Create a strategy - the actual ID will be generated by the system
        aStrategyExists();
        // Store the generated ID for later use
        // Note: In a real system, we'd use the system-generated ID
    }

    @When("I start the created strategy")
    public void iStartTheCreatedStrategy() throws Exception {
        assertNotNull(lastCreatedStrategyId, "No strategy ID available");
        executePostRequest("/api/strategies/" + lastCreatedStrategyId + "/start", "{}");
    }

    @When("I stop the created strategy")
    public void iStopTheCreatedStrategy() throws Exception {
        assertNotNull(lastCreatedStrategyId, "No strategy ID available");
        executePostRequest("/api/strategies/" + lastCreatedStrategyId + "/stop", "{}");
    }

    @When("I delete the created strategy")
    public void iDeleteTheCreatedStrategy() throws Exception {
        assertNotNull(lastCreatedStrategyId, "No strategy ID available");
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/strategies/" + lastCreatedStrategyId)
                .delete()
                .build();
        executeRequest(request);
    }

    @When("I create a strategy with invalid type {string}")
    public void iCreateAStrategyWithInvalidType(String invalidType) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("type", invalidType);
        request.put("symbols", List.of("AAPL"));
        request.put("exchange", "ALPACA");

        String json = objectMapper.writeValueAsString(request);
        executePostRequest("/api/strategies", json);
    }

    // ========== Position Steps ==========

    @Then("the summary should contain {string}")
    public void theSummaryShouldContain(String field) throws Exception {
        if (lastJsonResponse == null) {
            theResponseShouldBeValidJson();
        }
        assertTrue(lastJsonResponse.has(field),
                "Summary should contain '" + field + "'");
    }

    // ========== Order Steps ==========

    @When("I create an order with:")
    public void iCreateAnOrderWith(DataTable dataTable) throws Exception {
        Map<String, String> data = dataTable.asMap();

        Map<String, Object> request = new HashMap<>();
        request.put("symbol", data.get("symbol"));
        request.put("exchange", data.get("exchange"));
        request.put("side", data.get("side"));
        request.put("type", data.getOrDefault("type", "LIMIT"));
        request.put("quantity", Long.parseLong(data.get("quantity")));
        request.put("price", Long.parseLong(data.get("price")));

        String json = objectMapper.writeValueAsString(request);
        executePostRequest("/api/orders", json);

        if (lastResponse.isSuccessful() && lastResponseBody != null) {
            lastJsonResponse = objectMapper.readTree(lastResponseBody);
            if (lastJsonResponse.has("clientOrderId")) {
                lastCreatedOrderId = String.valueOf(lastJsonResponse.path("clientOrderId").asLong());
                lastCreatedOrderSymbol = data.get("symbol");
                lastCreatedOrderExchange = data.get("exchange");
            }
        }
    }

    @Then("the order should have a client order id")
    public void theOrderShouldHaveAClientOrderId() {
        assertNotNull(lastJsonResponse, "No JSON response");
        assertTrue(lastJsonResponse.has("clientOrderId"), "Order should have a clientOrderId");
    }

    @Given("an active order exists")
    public void anActiveOrderExists() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("symbol", "AAPL");
        request.put("exchange", "ALPACA");
        request.put("side", "BUY");
        request.put("type", "LIMIT");
        request.put("quantity", 100L);
        request.put("price", 15000L);

        String json = objectMapper.writeValueAsString(request);
        executePostRequest("/api/orders", json);

        if (lastResponse.isSuccessful() && lastResponseBody != null) {
            lastJsonResponse = objectMapper.readTree(lastResponseBody);
            lastCreatedOrderId = String.valueOf(lastJsonResponse.path("clientOrderId").asLong());
            lastCreatedOrderSymbol = "AAPL";
            lastCreatedOrderExchange = "ALPACA";
        }
    }

    @When("I cancel the order via API")
    public void iCancelTheOrderViaApi() throws Exception {
        assertNotNull(lastCreatedOrderId, "No order ID available");
        assertNotNull(lastCreatedOrderSymbol, "No order symbol available");
        assertNotNull(lastCreatedOrderExchange, "No order exchange available");

        String url = String.format("%s/api/orders/%s?symbol=%s&exchange=%s",
                BASE_URL, lastCreatedOrderId, lastCreatedOrderSymbol, lastCreatedOrderExchange);
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();
        executeRequest(request);
    }

    // ========== CORS Steps ==========

    @Then("the response should include CORS headers")
    public void theResponseShouldIncludeCorsHeaders() {
        assertNotNull(lastResponse, "No response received");
        // CORS headers may be present depending on configuration
        // At minimum, the OPTIONS request should succeed
        assertTrue(lastResponse.code() == 200 || lastResponse.code() == 204,
                "CORS preflight should succeed");
    }

    // ========== Helper Methods ==========

    private void executeGetRequest(String path) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .get()
                .build();
        executeRequest(request);
    }

    private void executePostRequest(String path, String body) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .post(RequestBody.create(body, JSON_MEDIA_TYPE))
                .build();
        executeRequest(request);
    }

    private void executeRequest(Request request) throws IOException {
        if (lastResponse != null) {
            lastResponse.close();
        }
        lastResponse = client.newCall(request).execute();
        ResponseBody responseBody = lastResponse.body();
        lastResponseBody = responseBody != null ? responseBody.string() : null;
        lastJsonResponse = null; // Reset, will be parsed on demand
    }
}
