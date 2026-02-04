package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.QuoteDto;
import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates simulated market data for stub mode.
 * Uses realistic base prices and random walk price movements.
 */
@Service
public class StubMarketDataService {
    private static final Logger log = LoggerFactory.getLogger(StubMarketDataService.class);

    private final ExchangeProperties properties;
    private final SimpMessagingTemplate messagingTemplate;
    private final TradingService tradingService;
    private final ExchangeService exchangeService;
    private final Random random = new Random();

    // Current prices (in cents/minor units)
    private final Map<String, Long> currentPrices = new ConcurrentHashMap<>();

    // Volatility factors (basis points per tick, e.g., 5 = 0.05%)
    private final Map<String, Integer> volatility = new ConcurrentHashMap<>();

    // Trend state for autocorrelated price movements (makes momentum strategies viable)
    // Drift is an AR(1) process that biases the random walk direction, creating realistic trends
    private final Map<String, Double> drift = new ConcurrentHashMap<>();

    // Base prices based on approximate January 2026 market values (in cents)
    private static final Map<String, Long> ALPACA_BASE_PRICES = Map.of(
            "AAPL", 23500L,    // ~$235
            "MSFT", 42500L,    // ~$425
            "NVDA", 14800L,    // ~$148
            "TSLA", 37500L,    // ~$375
            "GOOGL", 19200L    // ~$192
    );

    // Crypto prices in cents (higher precision for larger values)
    private static final Map<String, Long> BINANCE_BASE_PRICES = Map.of(
            "BTCUSDT", 10500000L,  // ~$105,000
            "ETHUSDT", 325000L,    // ~$3,250
            "SOLUSDT", 22500L,     // ~$225
            "BNBUSDT", 65000L,     // ~$650
            "XRPUSDT", 225L        // ~$2.25
    );

    // Volatility in basis points (100 bp = 1%)
    private static final Map<String, Integer> ALPACA_VOLATILITY = Map.of(
            "AAPL", 15,   // 0.15% per tick
            "MSFT", 12,   // 0.12% per tick
            "NVDA", 25,   // 0.25% per tick (more volatile)
            "TSLA", 30,   // 0.30% per tick (high volatility)
            "GOOGL", 15   // 0.15% per tick
    );

    private static final Map<String, Integer> BINANCE_VOLATILITY = Map.of(
            "BTCUSDT", 20,   // 0.20% per tick
            "ETHUSDT", 25,   // 0.25% per tick
            "SOLUSDT", 35,   // 0.35% per tick (high volatility alt)
            "BNBUSDT", 25,   // 0.25% per tick
            "XRPUSDT", 30    // 0.30% per tick
    );

    private boolean enabled = false;

    public StubMarketDataService(ExchangeProperties properties,
                                  SimpMessagingTemplate messagingTemplate,
                                  TradingService tradingService,
                                  @Lazy ExchangeService exchangeService) {
        this.properties = properties;
        this.messagingTemplate = messagingTemplate;
        this.tradingService = tradingService;
        this.exchangeService = exchangeService;
    }

    @PostConstruct
    public void initialize() {
        // Only enable if at least one exchange is in stub mode
        boolean alpacaStub = properties.getAlpaca().isEnabled() && properties.getAlpaca().isStub();
        boolean binanceStub = properties.getBinance().isEnabled() && properties.getBinance().isStub();

        if (alpacaStub) {
            initializeAlpacaPrices();
        }
        if (binanceStub) {
            initializeBinancePrices();
        }

        enabled = alpacaStub || binanceStub;
        if (enabled) {
            log.info("Stub market data service initialized with {} symbols", currentPrices.size());
        }
    }

    private void initializeAlpacaPrices() {
        ALPACA_BASE_PRICES.forEach((symbol, price) -> {
            String key = "ALPACA:" + symbol;
            currentPrices.put(key, price);
            volatility.put(key, ALPACA_VOLATILITY.getOrDefault(symbol, 15));
        });
    }

    private void initializeBinancePrices() {
        BINANCE_BASE_PRICES.forEach((symbol, price) -> {
            String key = "BINANCE:" + symbol;
            currentPrices.put(key, price);
            volatility.put(key, BINANCE_VOLATILITY.getOrDefault(symbol, 25));
        });
    }

    /**
     * Generates and broadcasts quotes every 500ms.
     */
    @Scheduled(fixedRate = 500)
    public void generateQuotes() {
        if (!enabled) {
            return;
        }

        long timestamp = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : currentPrices.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":");
            String exchangeName = parts[0];
            String ticker = parts[1];

            // Skip symbols that have real (non-stub) data feeds
            if (exchangeService.isRealDataSymbol(exchangeName, ticker)) {
                continue;
            }

            // Update price with trending random walk (AR(1) drift + noise)
            long currentPrice = entry.getValue();
            int vol = volatility.getOrDefault(key, 15);

            // Evolve drift: mean-reverting AR(1) process with random shocks
            // drift(t) = 0.98 * drift(t-1) + noise
            // This creates realistic trending periods that momentum strategies can detect
            double currentDrift = drift.getOrDefault(key, 0.0);
            double driftShock = (random.nextGaussian() * vol * 0.3) / 10000.0;
            currentDrift = 0.98 * currentDrift + driftShock;
            // Clamp drift to prevent runaway trends
            currentDrift = Math.max(-0.005, Math.min(0.005, currentDrift));
            drift.put(key, currentDrift);

            // Price change = drift (trend) + noise (random)
            double noise = (random.nextGaussian() * vol * 0.7) / 10000.0;
            double changePercent = currentDrift + noise;
            long priceChange = (long) (currentPrice * changePercent);
            long newPrice = Math.max(1, currentPrice + priceChange);
            currentPrices.put(key, newPrice);

            // Calculate spread based on price (larger prices have larger spreads)
            long spread = Math.max(1, newPrice / 1000); // 0.1% spread
            long bidPrice = newPrice - spread / 2;
            long askPrice = newPrice + spread / 2;

            // Random sizes between 100 and 10000
            long bidSize = 100 + random.nextInt(9900);
            long askSize = 100 + random.nextInt(9900);

            // Create quote
            Symbol symbol = new Symbol(ticker, Exchange.valueOf(exchangeName));
            Quote quote = new Quote(symbol, bidPrice, askPrice, bidSize, askSize, timestamp);
            quote.setPriceScale(100);

            // Broadcast quote
            QuoteDto dto = QuoteDto.from(quote);
            messagingTemplate.convertAndSend("/topic/quotes/" + exchangeName + "/" + ticker, dto);
            messagingTemplate.convertAndSend("/topic/quotes", dto);

            // Feed to trading engine and dispatch to strategies
            tradingService.getTradingEngine().onQuoteUpdate(quote);
            tradingService.dispatchQuoteToStrategies(quote);
        }
    }

    /**
     * Returns the current price for a symbol.
     */
    public Long getCurrentPrice(String exchange, String ticker) {
        return currentPrices.get(exchange + ":" + ticker);
    }

    /**
     * Updates the current price for a symbol from an external source (e.g., real exchange data).
     * This ensures chart trigger ranges use real prices when available.
     */
    public void updatePrice(String exchange, String ticker, long priceCents) {
        currentPrices.put(exchange + ":" + ticker, priceCents);
    }

    /**
     * Returns all current prices.
     */
    public Map<String, Long> getAllPrices() {
        return Map.copyOf(currentPrices);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
