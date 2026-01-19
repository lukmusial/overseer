package com.hft.bdd.steps;

import com.hft.core.metrics.OrderMetrics;
import com.hft.core.model.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class OrderLifecycleSteps {
    private Symbol symbol;
    private Order currentOrder;
    private List<Order> orders = new ArrayList<>();
    private Position position;
    private OrderMetrics metrics;
    private boolean exchangeConnected = false;

    @Before
    public void setUp() {
        metrics = new OrderMetrics();
        orders.clear();
    }

    @Given("the trading system is initialized")
    public void theTradingSystemIsInitialized() {
        metrics = new OrderMetrics();
        position = null;
    }

    @Given("the exchange connection is established")
    public void theExchangeConnectionIsEstablished() {
        exchangeConnected = true;
    }

    @Given("I have a symbol {string} on exchange {string}")
    public void iHaveASymbolOnExchange(String ticker, String exchangeName) {
        Exchange exchange = Exchange.valueOf(exchangeName);
        symbol = new Symbol(ticker, exchange);
        position = new Position(symbol);
    }

    @When("I submit a market order to buy {int} shares")
    public void iSubmitAMarketOrderToBuyShares(int quantity) {
        currentOrder = new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(quantity);

        long startNanos = System.nanoTime();
        currentOrder.markSubmitted();
        long submitLatency = System.nanoTime() - startNanos;

        metrics.recordOrderSubmitted();
        metrics.recordSubmitLatency(submitLatency);
        orders.add(currentOrder);
    }

    @When("I submit a limit order to buy {int} shares at price {double}")
    public void iSubmitALimitOrderToBuySharesAtPrice(int quantity, double price) {
        currentOrder = new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price((long) (price * 100))
                .quantity(quantity);

        currentOrder.markSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordQuantityOrdered(quantity);
        orders.add(currentOrder);
    }

    @When("the exchange acknowledges the order with ID {string}")
    public void theExchangeAcknowledgesTheOrderWithId(String exchangeId) {
        long ackLatency = System.nanoTime() - currentOrder.getSubmittedAt();
        currentOrder.markAccepted(exchangeId);
        metrics.recordOrderAccepted(ackLatency);
    }

    @Given("the exchange acknowledges the order")
    public void theExchangeAcknowledgesTheOrder() {
        String id = "EX" + System.nanoTime();
        long ackLatency = System.nanoTime() - currentOrder.getSubmittedAt();
        currentOrder.markAccepted(id);
        metrics.recordOrderAccepted(ackLatency);
    }

    @When("the order is filled at price {double}")
    public void theOrderIsFilledAtPrice(double price) {
        long priceInCents = (long) (price * 100);
        long fillLatency = System.nanoTime() - currentOrder.getSubmittedAt();

        currentOrder.markFilled(currentOrder.getQuantity(), priceInCents);
        metrics.recordOrderFilled(fillLatency, currentOrder.getQuantity(),
                priceInCents * currentOrder.getQuantity() / 100);

        // Update position
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(currentOrder.getSide());
        trade.setQuantity(currentOrder.getQuantity());
        trade.setPrice(priceInCents);
        trade.setExecutedAt(System.nanoTime());
        position.applyTrade(trade);
    }

    @When("the order is partially filled with {int} shares at price {double}")
    public void theOrderIsPartiallyFilledWithSharesAtPrice(int quantity, double price) {
        long priceInCents = (long) (price * 100);
        currentOrder.markPartiallyFilled(quantity, priceInCents);
        metrics.recordOrderPartiallyFilled(quantity);
    }

    @When("I cancel the order")
    public void iCancelTheOrder() {
        long cancelStartTime = System.nanoTime();
        currentOrder.markCancelled();
        long cancelLatency = System.nanoTime() - cancelStartTime;
        metrics.recordOrderCancelled(cancelLatency);
    }

    @When("the exchange rejects the order")
    public void theExchangeRejectsTheOrder() {
        currentOrder.markRejected();
        metrics.recordOrderRejected();
    }

    @When("I submit {int} market orders in quick succession")
    public void iSubmitMarketOrdersInQuickSuccession(int count) {
        for (int i = 0; i < count; i++) {
            Order order = new Order()
                    .symbol(symbol)
                    .side(OrderSide.BUY)
                    .type(OrderType.MARKET)
                    .quantity(100);

            long startNanos = System.nanoTime();
            order.markSubmitted();
            long submitLatency = System.nanoTime() - startNanos;

            metrics.recordOrderSubmitted();
            metrics.recordSubmitLatency(submitLatency);
            orders.add(order);
        }
        currentOrder = orders.getLast();
    }

    @Then("the order should be in {string} status")
    public void theOrderShouldBeInStatus(String status) {
        assertEquals(OrderStatus.valueOf(status), currentOrder.getStatus());
    }

    @Then("the order should have a client order ID")
    public void theOrderShouldHaveAClientOrderId() {
        assertTrue(currentOrder.getClientOrderId() > 0);
    }

    @Then("the acknowledgment latency should be recorded")
    public void theAcknowledgmentLatencyShouldBeRecorded() {
        assertTrue(currentOrder.getAckLatencyNanos() >= 0);
        assertEquals(1, metrics.getAckLatencyStats().count());
    }

    @Then("the fill latency should be recorded")
    public void theFillLatencyShouldBeRecorded() {
        assertTrue(currentOrder.getFillLatencyNanos() >= 0);
        assertEquals(1, metrics.getFillLatencyStats().count());
    }

    @Then("the position should show {int} shares")
    public void thePositionShouldShowShares(int quantity) {
        assertEquals(quantity, position.getQuantity());
    }

    @Then("the remaining quantity should be {int}")
    public void theRemainingQuantityShouldBe(int quantity) {
        assertEquals(quantity, currentOrder.getRemainingQuantity());
    }

    @Then("the cancellation latency should be recorded")
    public void theCancellationLatencyShouldBeRecorded() {
        assertEquals(1, metrics.getCancelLatencyStats().count());
    }

    @Then("the rejection should be recorded in metrics")
    public void theRejectionShouldBeRecordedInMetrics() {
        assertEquals(1, metrics.getOrdersRejected());
    }

    @Then("all orders should have unique client IDs")
    public void allOrdersShouldHaveUniqueClientIds() {
        Set<Long> ids = new HashSet<>();
        for (Order order : orders) {
            assertTrue(ids.add(order.getClientOrderId()),
                    "Duplicate client order ID found: " + order.getClientOrderId());
        }
    }

    @Then("the average submit latency should be less than {int} microseconds")
    public void theAverageSubmitLatencyShouldBeLessThanMicroseconds(int maxMicros) {
        long avgNanos = metrics.getSubmitLatencyStats().mean();
        long avgMicros = avgNanos / 1000;
        assertTrue(avgMicros < maxMicros,
                "Average submit latency " + avgMicros + "us exceeds " + maxMicros + "us");
    }

    @Then("the throughput should be recorded")
    public void theThroughputShouldBeRecorded() {
        assertTrue(metrics.getOrdersSubmitted() > 0);
    }
}
