package com.hft.bdd.steps;

import com.hft.core.model.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class PositionTrackingSteps {
    private Symbol symbol;
    private Position position;

    @Given("I have a long position of {int} shares at {double}")
    public void iHaveALongPositionOfSharesAt(int quantity, double price) {
        if (symbol == null) {
            symbol = new Symbol("AAPL", Exchange.ALPACA);
        }
        position = new Position(symbol);

        if (quantity > 0) {
            Trade trade = createTrade(OrderSide.BUY, quantity, price);
            position.applyTrade(trade);
        }
    }

    @Given("I have a short position of {int} shares at {double}")
    public void iHaveAShortPositionOfSharesAt(int quantity, double price) {
        if (symbol == null) {
            symbol = new Symbol("AAPL", Exchange.ALPACA);
        }
        position = new Position(symbol);

        if (quantity > 0) {
            Trade trade = createTrade(OrderSide.SELL, quantity, price);
            position.applyTrade(trade);
        }
    }

    @When("I buy {int} shares at price {double}")
    public void iBuySharesAtPrice(int quantity, double price) {
        if (position == null) {
            position = new Position(symbol);
        }
        Trade trade = createTrade(OrderSide.BUY, quantity, price);
        position.applyTrade(trade);
    }

    @When("I buy {int} more shares at price {double}")
    public void iBuyMoreSharesAtPrice(int quantity, double price) {
        Trade trade = createTrade(OrderSide.BUY, quantity, price);
        position.applyTrade(trade);
    }

    @When("I sell {int} shares at price {double}")
    public void iSellSharesAtPrice(int quantity, double price) {
        Trade trade = createTrade(OrderSide.SELL, quantity, price);
        position.applyTrade(trade);
    }

    @When("the market price updates to {double}")
    public void theMarketPriceUpdatesTo(double price) {
        long priceInCents = (long) (price * 100);
        position.updateMarketValue(priceInCents);
    }

    @When("the market price drops to {double}")
    public void theMarketPriceDropsTo(double price) {
        long priceInCents = (long) (price * 100);
        position.updateMarketValue(priceInCents);
    }

    @Then("my position should be long")
    public void myPositionShouldBeLong() {
        assertTrue(position.isLong(), "Position should be long");
    }

    @Then("my position should be short")
    public void myPositionShouldBeShort() {
        assertTrue(position.isShort(), "Position should be short");
    }

    @Then("my position should be flat")
    public void myPositionShouldBeFlat() {
        assertTrue(position.isFlat(), "Position should be flat");
    }

    @Then("my position quantity should be {int}")
    public void myPositionQuantityShouldBe(int quantity) {
        assertEquals(quantity, position.getQuantity());
    }

    @Then("my average entry price should be {double}")
    public void myAverageEntryPriceShouldBe(double expectedPrice) {
        long expectedPriceInCents = (long) (expectedPrice * 100);
        assertEquals(expectedPriceInCents, position.getAverageEntryPrice());
    }

    @Then("my average entry price should be approximately {double}")
    public void myAverageEntryPriceShouldBeApproximately(double expectedPrice) {
        double actualPrice = position.getAverageEntryPriceAsDouble();
        assertEquals(expectedPrice, actualPrice, 0.01);
    }

    @Then("my realized P&L should be positive")
    public void myRealizedPnlShouldBePositive() {
        assertTrue(position.getRealizedPnl() > 0,
                "Realized P&L should be positive, was: " + position.getRealizedPnl());
    }

    @Then("my realized P&L should be {double}")
    public void myRealizedPnlShouldBe(double expectedPnl) {
        long expectedPnlInCents = (long) (expectedPnl * 100);
        assertEquals(expectedPnlInCents, position.getRealizedPnl());
    }

    @Then("my unrealized P&L should be {double}")
    public void myUnrealizedPnlShouldBe(double expectedPnl) {
        long expectedPnlInCents = (long) (expectedPnl * 100);
        assertEquals(expectedPnlInCents, position.getUnrealizedPnl());
    }

    @Then("my total P&L should be {double}")
    public void myTotalPnlShouldBe(double expectedPnl) {
        long expectedPnlInCents = (long) (expectedPnl * 100);
        assertEquals(expectedPnlInCents, position.getTotalPnl());
    }

    @Then("my maximum drawdown should be recorded")
    public void myMaximumDrawdownShouldBeRecorded() {
        assertTrue(position.getMaxDrawdown() < 0,
                "Max drawdown should be negative when price drops");
    }

    private Trade createTrade(OrderSide side, int quantity, double price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice((long) (price * 100));
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
