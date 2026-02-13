package com.hft.bdd.steps;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.OrderRequest;
import com.hft.algo.base.StrategyParameters;
import com.hft.algo.base.TradingStrategy;
import com.hft.algo.strategy.BollingerSqueezeStrategy;
import com.hft.algo.strategy.EmaAdxRsiStrategy;
import com.hft.algo.strategy.VwapMeanReversionStrategy;
import com.hft.core.model.*;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedStrategySteps {

    private Symbol symbol;
    private TradingStrategy currentStrategy;
    private EmaAdxRsiStrategy emaAdxRsiStrategy;
    private BollingerSqueezeStrategy bollingerSqueezeStrategy;
    private VwapMeanReversionStrategy vwapMeanReversionStrategy;

    private static final AlgorithmContext STUB_CONTEXT = new AlgorithmContext() {
        @Override public Quote getQuote(Symbol symbol) { return null; }
        @Override public long getCurrentTimeNanos() { return System.nanoTime(); }
        @Override public long getCurrentTimeMillis() { return System.currentTimeMillis(); }
        @Override public void submitOrder(OrderRequest request) { }
        @Override public void cancelOrder(long clientOrderId) { }
        @Override public void onFill(Consumer<Trade> callback) { }
        @Override public long[] getHistoricalVolume(Symbol symbol, int buckets) { return new long[buckets]; }
        @Override public void logInfo(String message) { }
        @Override public void logError(String message, Throwable error) { }
    };

    // --- Shared verification steps ---

    @Then("the advanced strategy should be created successfully")
    public void theAdvancedStrategyShouldBeCreatedSuccessfully() {
        assertNotNull(currentStrategy);
        assertNotNull(currentStrategy.getId());
    }

    @Then("the advanced strategy state should be {string}")
    public void theAdvancedStrategyStateShouldBe(String state) {
        assertEquals(AlgorithmState.valueOf(state), currentStrategy.getState());
    }

    // --- EMA+ADX+RSI steps ---

    @When("I create an ema_adx_rsi strategy for {string} on {string} with parameters:")
    public void iCreateAnEmaAdxRsiStrategyForWithParameters(String ticker, String exchange, DataTable dataTable) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        StrategyParameters params = new StrategyParameters();
        Map<String, String> paramMap = dataTable.asMap(String.class, String.class);
        paramMap.forEach(params::set);

        emaAdxRsiStrategy = new EmaAdxRsiStrategy(Set.of(symbol), params);
        currentStrategy = emaAdxRsiStrategy;
    }

    @Given("I have a running ema_adx_rsi strategy for {string} on {string}")
    public void iHaveARunningEmaAdxRsiStrategy(String ticker, String exchange) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        emaAdxRsiStrategy = EmaAdxRsiStrategy.builder()
                .addSymbol(symbol)
                .fastEmaPeriod(9)
                .slowEmaPeriod(21)
                .adxPeriod(14)
                .adxThreshold(25.0)
                .rsiPeriod(14)
                .maxPositionSize(100)
                .build();
        emaAdxRsiStrategy.initialize(STUB_CONTEXT);
        emaAdxRsiStrategy.start();
        currentStrategy = emaAdxRsiStrategy;
    }

    @When("I feed a strong uptrend of {int} quotes")
    public void iFeedAStrongUptrendOfQuotes(int count) {
        for (int i = 0; i < count; i++) {
            long price = 40000_00L + (long)(i * 500_00L);
            currentStrategy.onQuote(createQuote(price));
        }
    }

    @When("I feed {int} sideways quotes around {int}")
    public void iFeedSidewaysQuotesAround(int count, int basePrice) {
        long base = basePrice * 100L;
        for (int i = 0; i < count; i++) {
            long price = base + (long)((i % 4 - 2) * 10_00L);
            currentStrategy.onQuote(createQuote(price));
        }
    }

    @Then("the fast EMA should be above the slow EMA")
    public void theFastEmaShouldBeAboveTheSlowEma() {
        assertTrue(emaAdxRsiStrategy.getFastEma(symbol) > emaAdxRsiStrategy.getSlowEma(symbol),
                "Fast EMA should be above slow EMA in uptrend");
    }

    @Then("the RSI should be above {int}")
    public void theRsiShouldBeAbove(int threshold) {
        assertTrue(emaAdxRsiStrategy.getRsi(symbol) > threshold,
                "RSI should be above " + threshold + ", actual: " + emaAdxRsiStrategy.getRsi(symbol));
    }

    @Then("the signal should be zero or near-zero")
    public void theSignalShouldBeZeroOrNearZero() {
        double signal = currentStrategy.getSignal(symbol);
        assertTrue(Math.abs(signal) < 0.1,
                "Signal should be near zero in sideways market, actual: " + signal);
    }

    // --- Bollinger Squeeze steps ---

    @When("I create a bollinger_squeeze strategy for {string} on {string} with parameters:")
    public void iCreateABollingerSqueezeStrategyForWithParameters(String ticker, String exchange, DataTable dataTable) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        StrategyParameters params = new StrategyParameters();
        Map<String, String> paramMap = dataTable.asMap(String.class, String.class);
        paramMap.forEach(params::set);

        bollingerSqueezeStrategy = new BollingerSqueezeStrategy(Set.of(symbol), params);
        currentStrategy = bollingerSqueezeStrategy;
    }

    @Given("I have a running bollinger_squeeze strategy for {string} on {string}")
    public void iHaveARunningBollingerSqueezeStrategy(String ticker, String exchange) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        bollingerSqueezeStrategy = BollingerSqueezeStrategy.builder()
                .addSymbol(symbol)
                .bbPeriod(20)
                .bbStdDev(2.5)
                .kcPeriod(20)
                .kcAtrPeriod(14)
                .kcMultiplier(2.0)
                .macdFast(8)
                .macdSlow(17)
                .macdSignal(9)
                .maxPositionSize(100)
                .build();
        bollingerSqueezeStrategy.initialize(STUB_CONTEXT);
        bollingerSqueezeStrategy.start();
        currentStrategy = bollingerSqueezeStrategy;
    }

    @When("I feed {int} stable quotes to create a squeeze")
    public void iFeedStableQuotesToCreateASqueeze(int count) {
        for (int i = 0; i < count; i++) {
            Quote quote = new Quote();
            quote.setSymbol(symbol);
            quote.setBidPrice(3000_00L - 5);
            quote.setAskPrice(3000_00L + 5);
            quote.setBidSize(500);
            quote.setAskSize(500);
            currentStrategy.onQuote(quote);
        }
    }

    @When("I feed {int} breakout quotes trending upward")
    public void iFeedBreakoutQuotesTrendingUpward(int count) {
        for (int i = 0; i < count; i++) {
            long price = 3000_00L + (long)(i * 100_00L);
            Quote quote = new Quote();
            quote.setSymbol(symbol);
            quote.setBidPrice(price - 100);
            quote.setAskPrice(price + 100);
            quote.setBidSize(500);
            quote.setAskSize(500);
            currentStrategy.onQuote(quote);
        }
    }

    @Then("the MACD histogram should be positive")
    public void theMacdHistogramShouldBePositive() {
        double histogram = bollingerSqueezeStrategy.getMacdHistogram(symbol);
        assertTrue(histogram > 0, "MACD histogram should be positive after upward breakout: " + histogram);
    }

    // --- VWAP Mean Reversion steps ---

    @When("I create a vwap_mean_reversion strategy for {string} on {string} with parameters:")
    public void iCreateAVwapMeanReversionStrategyForWithParameters(String ticker, String exchange, DataTable dataTable) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        StrategyParameters params = new StrategyParameters();
        Map<String, String> paramMap = dataTable.asMap(String.class, String.class);
        paramMap.forEach(params::set);

        vwapMeanReversionStrategy = new VwapMeanReversionStrategy(Set.of(symbol), params);
        currentStrategy = vwapMeanReversionStrategy;
    }

    @Given("I have a running vwap_mean_reversion strategy for {string} on {string}")
    public void iHaveARunningVwapMeanReversionStrategy(String ticker, String exchange) {
        symbol = new Symbol(ticker, Exchange.valueOf(exchange));
        vwapMeanReversionStrategy = VwapMeanReversionStrategy.builder()
                .addSymbol(symbol)
                .upperSigma(2.3)
                .lowerSigma(2.3)
                .exitSigma(0.5)
                .maxHoldMinutes(240)
                .volumeFilterMultiplier(0.0)
                .maxPositionSize(100)
                .build();
        vwapMeanReversionStrategy.initialize(STUB_CONTEXT);
        vwapMeanReversionStrategy.start();
        currentStrategy = vwapMeanReversionStrategy;
    }

    @When("I feed {int} quotes around {int} to build VWAP")
    public void iFeedQuotesAroundToBuildVwap(int count, int basePrice) {
        long base = basePrice * 100L;
        for (int i = 0; i < count; i++) {
            long price = base + (i % 5 - 2) * 200_00L;
            Quote quote = new Quote();
            quote.setSymbol(symbol);
            quote.setBidPrice(price - 50);
            quote.setAskPrice(price + 50);
            quote.setBidSize(1000);
            quote.setAskSize(1000);
            currentStrategy.onQuote(quote);
        }
    }

    @When("the price drops far below VWAP")
    public void thePriceDropsFarBelowVwap() {
        // Use a fixed extreme price well below the established VWAP (~50000_00)
        long extremeLow = 44000_00L;
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(extremeLow - 50);
        quote.setAskPrice(extremeLow + 50);
        quote.setBidSize(1000);
        quote.setAskSize(1000);
        currentStrategy.onQuote(quote);
    }

    @Then("the vwap strategy should generate a buy signal")
    public void theVwapStrategyShouldGenerateABuySignal() {
        double signal = currentStrategy.getSignal(symbol);
        assertTrue(signal > 0, "Should generate buy signal below lower band. Signal: " + signal);
    }

    @Given("the strategy has a long position of {int} units")
    public void theStrategyHasALongPositionOfUnits(int quantity) {
        Trade fill = new Trade();
        fill.setSymbol(symbol);
        fill.setSide(OrderSide.BUY);
        fill.setQuantity(quantity);
        fill.setPrice(48000_00L);
        currentStrategy.onFill(fill);
    }

    @When("the price returns to VWAP")
    public void thePriceReturnsToVwap() {
        double vwap = vwapMeanReversionStrategy.getVwap(symbol);
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice((long)(vwap - 50));
        quote.setAskPrice((long)(vwap + 50));
        quote.setBidSize(1000);
        quote.setAskSize(1000);
        currentStrategy.onQuote(quote);
    }

    @Then("the signal should indicate exit")
    public void theSignalShouldIndicateExit() {
        double signal = currentStrategy.getSignal(symbol);
        assertTrue(Math.abs(signal) < 0.6,
                "Signal should indicate exit near VWAP, actual: " + signal);
    }

    // --- Shared helper ---

    private Quote createQuote(long midPrice) {
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(midPrice - 50);
        quote.setAskPrice(midPrice + 50);
        quote.setBidSize(1000);
        quote.setAskSize(1000);
        return quote;
    }
}
