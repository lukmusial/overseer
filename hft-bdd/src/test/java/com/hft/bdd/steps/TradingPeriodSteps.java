package com.hft.bdd.steps;

import com.hft.core.model.TradingPeriod;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class TradingPeriodSteps {

    private TradingPeriod detectedPeriod;

    @When("the UTC time is {string}")
    public void theUtcTimeIs(String timeStr) {
        LocalTime time = LocalTime.parse(timeStr);
        detectedPeriod = TradingPeriod.fromUtcTime(time);
    }

    @Then("the trading period should be {string}")
    public void theTradingPeriodShouldBe(String expectedPeriod) {
        assertEquals(TradingPeriod.valueOf(expectedPeriod), detectedPeriod);
    }

    @Then("the position multiplier should be {double}")
    public void thePositionMultiplierShouldBe(double expectedMultiplier) {
        assertEquals(expectedMultiplier, detectedPeriod.getPositionMultiplier(), 0.001);
    }

    @Then("the recommended strategies should include {string}")
    public void theRecommendedStrategiesShouldInclude(String strategyType) {
        assertTrue(detectedPeriod.getRecommendedStrategies().contains(strategyType),
                "Expected " + detectedPeriod.name() + " to recommend " + strategyType +
                        ", but recommendations are: " + detectedPeriod.getRecommendedStrategies());
    }

    @Then("there should be no recommended strategies")
    public void thereShouldBeNoRecommendedStrategies() {
        assertTrue(detectedPeriod.getRecommendedStrategies().isEmpty(),
                "Expected no recommendations for " + detectedPeriod.name());
    }
}
