package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.SymbolDto;
import com.hft.exchange.alpaca.AlpacaConfig;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.dto.AlpacaAsset;
import com.hft.exchange.binance.BinanceConfig;
import com.hft.exchange.binance.BinanceHttpClient;
import com.hft.exchange.binance.dto.BinanceExchangeInfo;
import com.hft.exchange.binance.dto.BinanceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing exchange connections and reporting status.
 */
@Service
public class ExchangeService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final ExchangeProperties properties;
    private final Map<String, ExchangeConnection> connections = new ConcurrentHashMap<>();

    // HTTP clients for symbol fetching
    private AlpacaHttpClient alpacaClient;
    private BinanceHttpClient binanceClient;

    // Cached symbols
    private final Map<String, List<SymbolDto>> symbolCache = new ConcurrentHashMap<>();

    public ExchangeService(ExchangeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing exchange connections");

        // Initialize Alpaca connection
        if (properties.getAlpaca().isEnabled()) {
            initializeAlpaca();
        } else {
            log.info("Alpaca exchange is disabled");
            connections.put("ALPACA", new ExchangeConnection("ALPACA", "Alpaca Markets", false, false, "Disabled in configuration"));
        }

        // Initialize Binance connection
        if (properties.getBinance().isEnabled()) {
            initializeBinance();
        } else {
            log.info("Binance exchange is disabled");
            connections.put("BINANCE", new ExchangeConnection("BINANCE", "Binance", false, false, "Disabled in configuration"));
        }
    }

    private void initializeAlpaca() {
        ExchangeProperties.AlpacaProperties alpaca = properties.getAlpaca();
        String mode = alpaca.getMode();
        log.info("Initializing Alpaca in {} mode", mode);

        if (alpaca.isStub()) {
            connections.put("ALPACA", new ExchangeConnection("ALPACA", "Alpaca Markets (Stub)", true, true, null));
            symbolCache.put("ALPACA", getStubAlpacaSymbols());
            log.info("Alpaca running in STUB mode - simulated connection");
        } else {
            if (alpaca.getApiKey().isEmpty() || alpaca.getSecretKey().isEmpty()) {
                connections.put("ALPACA", new ExchangeConnection("ALPACA",
                    "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")",
                    false, false, "API credentials not configured"));
            } else {
                // Create HTTP client for real API calls
                AlpacaConfig config = new AlpacaConfig(
                        alpaca.getApiKey(),
                        alpaca.getSecretKey(),
                        alpaca.isPaperTrading(),
                        alpaca.getDataFeed()
                );
                alpacaClient = new AlpacaHttpClient(config);
                connections.put("ALPACA", new ExchangeConnection("ALPACA",
                    "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")",
                    true, true, null));
            }
        }
    }

    private void initializeBinance() {
        ExchangeProperties.BinanceProperties binance = properties.getBinance();
        String mode = binance.getMode();
        log.info("Initializing Binance in {} mode", mode);

        if (binance.isStub()) {
            connections.put("BINANCE", new ExchangeConnection("BINANCE", "Binance (Stub)", true, true, null));
            symbolCache.put("BINANCE", getStubBinanceSymbols());
            log.info("Binance running in STUB mode - simulated connection");
        } else {
            // Binance exchangeInfo is a public endpoint, create client even without credentials
            BinanceConfig config = new BinanceConfig(
                    binance.getApiKey().isEmpty() ? "dummy" : binance.getApiKey(),
                    binance.getSecretKey().isEmpty() ? "dummy" : binance.getSecretKey(),
                    binance.isTestnet()
            );
            binanceClient = new BinanceHttpClient(config);

            if (binance.getApiKey().isEmpty() || binance.getSecretKey().isEmpty()) {
                connections.put("BINANCE", new ExchangeConnection("BINANCE",
                    "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")",
                    true, false, "API credentials not configured (read-only)"));
            } else {
                connections.put("BINANCE", new ExchangeConnection("BINANCE",
                    "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")",
                    true, true, null));
            }
        }
    }

    /**
     * Returns the status of all configured exchanges.
     */
    public List<ExchangeStatusDto> getExchangeStatus() {
        List<ExchangeStatusDto> statuses = new ArrayList<>();
        for (ExchangeConnection conn : connections.values()) {
            statuses.add(conn.toDto());
        }
        return statuses;
    }

    /**
     * Returns the status of a specific exchange.
     */
    public ExchangeStatusDto getExchangeStatus(String exchange) {
        ExchangeConnection conn = connections.get(exchange.toUpperCase());
        return conn != null ? conn.toDto() : null;
    }

    /**
     * Updates the connection status for an exchange.
     */
    public void updateConnectionStatus(String exchange, boolean connected, boolean authenticated, String error) {
        ExchangeConnection existing = connections.get(exchange.toUpperCase());
        if (existing != null) {
            connections.put(exchange.toUpperCase(), new ExchangeConnection(
                existing.exchange, existing.name, connected, authenticated, error
            ));
        }
    }

    /**
     * Internal representation of an exchange connection.
     */
    private static class ExchangeConnection {
        final String exchange;
        final String name;
        final boolean connected;
        final boolean authenticated;
        final String errorMessage;
        final long lastHeartbeat;

        ExchangeConnection(String exchange, String name, boolean connected, boolean authenticated, String errorMessage) {
            this.exchange = exchange;
            this.name = name;
            this.connected = connected;
            this.authenticated = authenticated;
            this.errorMessage = errorMessage;
            this.lastHeartbeat = connected ? System.currentTimeMillis() : 0;
        }

        ExchangeStatusDto toDto() {
            return new ExchangeStatusDto(
                exchange,
                name,
                connected,
                authenticated,
                connected ? lastHeartbeat : null,
                errorMessage
            );
        }
    }

    /**
     * Returns available symbols for an exchange.
     */
    public List<SymbolDto> getSymbols(String exchange) {
        String key = exchange.toUpperCase();

        // Check cache first
        List<SymbolDto> cached = symbolCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Fetch from exchange
        List<SymbolDto> symbols = fetchSymbols(key);
        if (!symbols.isEmpty()) {
            symbolCache.put(key, symbols);
        }
        return symbols;
    }

    /**
     * Refreshes the symbol cache for an exchange.
     */
    public List<SymbolDto> refreshSymbols(String exchange) {
        String key = exchange.toUpperCase();
        symbolCache.remove(key);
        return getSymbols(key);
    }

    private List<SymbolDto> fetchSymbols(String exchange) {
        try {
            return switch (exchange) {
                case "ALPACA" -> fetchAlpacaSymbols();
                case "BINANCE" -> fetchBinanceSymbols();
                default -> List.of();
            };
        } catch (Exception e) {
            log.error("Error fetching symbols for {}: {}", exchange, e.getMessage());
            return List.of();
        }
    }

    private List<SymbolDto> fetchAlpacaSymbols() {
        if (alpacaClient == null) {
            return getStubAlpacaSymbols();
        }

        try {
            List<AlpacaAsset> assets = alpacaClient.getAssets("us_equity", "active")
                    .get(30, TimeUnit.SECONDS);

            return assets.stream()
                    .filter(AlpacaAsset::tradable)
                    .map(a -> SymbolDto.equity(
                            a.symbol(),
                            a.name(),
                            "ALPACA",
                            a.tradable(),
                            a.marginable(),
                            a.shortable()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching Alpaca assets", e);
            return getStubAlpacaSymbols();
        }
    }

    private List<SymbolDto> fetchBinanceSymbols() {
        if (binanceClient == null) {
            return getStubBinanceSymbols();
        }

        try {
            BinanceExchangeInfo info = binanceClient.getExchangeInfo()
                    .get(30, TimeUnit.SECONDS);

            return info.symbols().stream()
                    .filter(BinanceSymbol::isTrading)
                    .filter(BinanceSymbol::isSpotTradingAllowed)
                    .map(s -> SymbolDto.crypto(
                            s.symbol(),
                            s.baseAsset() + "/" + s.quoteAsset(),
                            "BINANCE",
                            s.baseAsset(),
                            s.quoteAsset(),
                            true
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching Binance symbols", e);
            return getStubBinanceSymbols();
        }
    }

    private List<SymbolDto> getStubAlpacaSymbols() {
        return List.of(
                SymbolDto.equity("AAPL", "Apple Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("GOOGL", "Alphabet Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("MSFT", "Microsoft Corporation", "ALPACA", true, true, true),
                SymbolDto.equity("AMZN", "Amazon.com Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("TSLA", "Tesla Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("META", "Meta Platforms Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("NVDA", "NVIDIA Corporation", "ALPACA", true, true, true),
                SymbolDto.equity("JPM", "JPMorgan Chase & Co.", "ALPACA", true, true, true),
                SymbolDto.equity("V", "Visa Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("JNJ", "Johnson & Johnson", "ALPACA", true, true, true),
                SymbolDto.equity("SPY", "SPDR S&P 500 ETF Trust", "ALPACA", true, true, true),
                SymbolDto.equity("QQQ", "Invesco QQQ Trust", "ALPACA", true, true, true)
        );
    }

    private List<SymbolDto> getStubBinanceSymbols() {
        return List.of(
                SymbolDto.crypto("BTCUSDT", "BTC/USDT", "BINANCE", "BTC", "USDT", true),
                SymbolDto.crypto("ETHUSDT", "ETH/USDT", "BINANCE", "ETH", "USDT", true),
                SymbolDto.crypto("BNBUSDT", "BNB/USDT", "BINANCE", "BNB", "USDT", true),
                SymbolDto.crypto("SOLUSDT", "SOL/USDT", "BINANCE", "SOL", "USDT", true),
                SymbolDto.crypto("XRPUSDT", "XRP/USDT", "BINANCE", "XRP", "USDT", true),
                SymbolDto.crypto("ADAUSDT", "ADA/USDT", "BINANCE", "ADA", "USDT", true),
                SymbolDto.crypto("DOGEUSDT", "DOGE/USDT", "BINANCE", "DOGE", "USDT", true),
                SymbolDto.crypto("AVAXUSDT", "AVAX/USDT", "BINANCE", "AVAX", "USDT", true),
                SymbolDto.crypto("DOTUSDT", "DOT/USDT", "BINANCE", "DOT", "USDT", true),
                SymbolDto.crypto("LINKUSDT", "LINK/USDT", "BINANCE", "LINK", "USDT", true),
                SymbolDto.crypto("BTCBUSD", "BTC/BUSD", "BINANCE", "BTC", "BUSD", true),
                SymbolDto.crypto("ETHBUSD", "ETH/BUSD", "BINANCE", "ETH", "BUSD", true)
        );
    }

    @PreDestroy
    public void cleanup() {
        if (alpacaClient != null) {
            alpacaClient.close();
        }
        if (binanceClient != null) {
            binanceClient.close();
        }
    }
}
