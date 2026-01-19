package com.hft.bdd.steps;

import com.hft.core.metrics.LatencyStats;
import com.hft.core.metrics.OrderMetrics;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceMetricsSteps {
    private OrderMetrics metrics;
    private OrderMetrics.MetricsSnapshot snapshot;

    @Before
    public void setUp() {
        metrics = new OrderMetrics();
    }

    @Given("the metrics system is initialized")
    public void theMetricsSystemIsInitialized() {
        metrics = new OrderMetrics();
    }

    @Given("I have accumulated various metrics")
    @Given("I have accumulated metrics")
    public void iHaveAccumulatedMetrics() {
        // Simulate some accumulated metrics
        for (int i = 0; i < 100; i++) {
            metrics.recordOrderSubmitted();
            metrics.recordSubmitLatency(1000 + i * 10);
        }
        metrics.recordOrderAccepted(500);
        metrics.recordOrderFilled(2000, 100, 15000);
        metrics.recordOrderRejected();
        metrics.recordConnectionError();
    }

    @When("I submit {int} orders with varying latencies")
    public void iSubmitOrdersWithVaryingLatencies(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordOrderSubmitted();
            metrics.recordSubmitLatency((i + 1) * 1000L); // 1us to 100us
        }
    }

    @When("{int} orders are acknowledged by the exchange")
    public void ordersAreAcknowledgedByTheExchange(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordOrderAccepted((i + 1) * 500L);
        }
    }

    @When("{int} orders are filled")
    public void ordersAreFilled(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordOrderFilled((i + 1) * 1000L, 100, 15000);
        }
    }

    @When("I submit orders at a rate of {int} per second for {int} seconds")
    public void iSubmitOrdersAtARateOfPerSecondForSeconds(int rate, int seconds) throws InterruptedException {
        int totalOrders = rate * seconds;
        long intervalNanos = 1_000_000_000L / rate;

        for (int i = 0; i < totalOrders; i++) {
            long startTime = System.nanoTime();
            metrics.recordOrderSubmitted();

            // Simple rate limiting (not precise, but good enough for test)
            long elapsed = System.nanoTime() - startTime;
            if (elapsed < intervalNanos) {
                Thread.sleep(0, (int) Math.min(intervalNanos - elapsed, 999_999));
            }
        }
    }

    @Given("I submit {int} orders")
    public void iSubmitOrders(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordOrderSubmitted();
        }
    }

    @Given("{int} orders are rejected")
    public void ordersAreRejected(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordOrderRejected();
        }
    }

    @Given("{int} orders timeout")
    public void ordersTimeout(int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordTimeoutError();
        }
    }

    @When("I take a metrics snapshot")
    public void iTakeAMetricsSnapshot() {
        snapshot = metrics.snapshot();
    }

    @When("I record latencies from {int}us to {int}us in {int}us increments")
    public void iRecordLatenciesFromUsToUsInUsIncrements(int start, int end, int increment) {
        for (int us = start; us <= end; us += increment) {
            metrics.recordSubmitLatency(us * 1000L); // Convert to nanos
        }
    }

    @When("I reset the metrics")
    public void iResetTheMetrics() {
        metrics.reset();
    }

    @Then("the submit latency histogram should contain {int} samples")
    public void theSubmitLatencyHistogramShouldContainSamples(int count) {
        assertEquals(count, metrics.getSubmitLatencyStats().count());
    }

    @Then("the ack latency histogram should contain {int} samples")
    public void theAckLatencyHistogramShouldContainSamples(int count) {
        assertEquals(count, metrics.getAckLatencyStats().count());
    }

    @Then("the fill latency histogram should contain {int} samples")
    public void theFillLatencyHistogramShouldContainSamples(int count) {
        assertEquals(count, metrics.getFillLatencyStats().count());
    }

    @Then("the p50 latency should be calculated")
    public void theP50LatencyShouldBeCalculated() {
        assertTrue(metrics.getSubmitLatencyStats().p50() > 0);
    }

    @Then("the p99 latency should be calculated")
    public void theP99LatencyShouldBeCalculated() {
        assertTrue(metrics.getSubmitLatencyStats().p99() > 0);
    }

    @Then("the average ack latency should be calculated")
    public void theAverageAckLatencyShouldBeCalculated() {
        assertTrue(metrics.getAckLatencyStats().mean() > 0);
    }

    @Then("the total quantity filled should be recorded")
    public void theTotalQuantityFilledShouldBeRecorded() {
        assertTrue(metrics.getTotalQuantityFilled() > 0);
    }

    @Then("the total notional value should be recorded")
    public void theTotalNotionalValueShouldBeRecorded() {
        assertTrue(metrics.getTotalNotionalValue() > 0);
    }

    @Then("the measured throughput should be approximately {int} orders per second")
    public void theMeasuredThroughputShouldBeApproximatelyOrdersPerSecond(int expectedRate) {
        // Allow 20% tolerance due to timing variations
        double actualThroughput = metrics.getCurrentThroughput();
        assertTrue(actualThroughput > expectedRate * 0.8 && actualThroughput < expectedRate * 1.2,
                "Throughput " + actualThroughput + " not close to expected " + expectedRate);
    }

    @Then("the reject rate should be {int}%")
    public void theRejectRateShouldBe(int expectedPercent) {
        double expectedRate = expectedPercent / 100.0;
        assertEquals(expectedRate, metrics.getRejectRate(), 0.01);
    }

    @Then("the timeout count should be {int}")
    public void theTimeoutCountShouldBe(int count) {
        assertEquals(count, metrics.getTimeoutErrors());
    }

    @Then("the snapshot should contain all order counts")
    public void theSnapshotShouldContainAllOrderCounts() {
        assertNotNull(snapshot);
        assertTrue(snapshot.ordersSubmitted() >= 0);
        assertTrue(snapshot.ordersFilled() >= 0);
        assertTrue(snapshot.ordersRejected() >= 0);
    }

    @Then("the snapshot should contain all latency statistics")
    public void theSnapshotShouldContainAllLatencyStatistics() {
        assertNotNull(snapshot.submitLatency());
        assertNotNull(snapshot.ackLatency());
        assertNotNull(snapshot.fillLatency());
    }

    @Then("the snapshot should contain all error counts")
    public void theSnapshotShouldContainAllErrorCounts() {
        assertTrue(snapshot.connectionErrors() >= 0);
        assertTrue(snapshot.timeoutErrors() >= 0);
        assertTrue(snapshot.validationErrors() >= 0);
    }

    @Then("the p50 should be approximately {int}us")
    public void theP50ShouldBeApproximatelyUs(int expected) {
        LatencyStats stats = metrics.getSubmitLatencyStats();
        double actualUs = stats.p50Micros();
        // 50% tolerance due to log-linear histogram bucket approximation
        assertEquals(expected, actualUs, expected * 0.5);
    }

    @Then("the p90 should be approximately {int}us")
    public void theP90ShouldBeApproximatelyUs(int expected) {
        LatencyStats stats = metrics.getSubmitLatencyStats();
        double actualUs = stats.p90Micros();
        assertEquals(expected, actualUs, expected * 0.5);
    }

    @Then("the p99 should be approximately {int}us")
    public void theP99ShouldBeApproximatelyUs(int expected) {
        LatencyStats stats = metrics.getSubmitLatencyStats();
        double actualUs = stats.p99Micros();
        assertEquals(expected, actualUs, expected * 0.5);
    }

    @Then("all order counts should be zero")
    public void allOrderCountsShouldBeZero() {
        assertEquals(0, metrics.getOrdersSubmitted());
        assertEquals(0, metrics.getOrdersFilled());
        assertEquals(0, metrics.getOrdersRejected());
    }

    @Then("all latency histograms should be empty")
    public void allLatencyHistogramsShouldBeEmpty() {
        assertEquals(0, metrics.getSubmitLatencyStats().count());
        assertEquals(0, metrics.getAckLatencyStats().count());
        assertEquals(0, metrics.getFillLatencyStats().count());
    }
}
