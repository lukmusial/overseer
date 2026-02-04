package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.QuoteDto;
import com.hft.api.dto.SymbolDto;
import com.hft.core.model.Exchange;
import com.hft.core.model.Symbol;
import com.hft.core.port.MarketDataPort;
import com.hft.exchange.alpaca.AlpacaConfig;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.AlpacaMarketDataPort;
import com.hft.exchange.alpaca.AlpacaWebSocketClient;
import com.hft.exchange.alpaca.dto.AlpacaAsset;
import com.hft.exchange.binance.BinanceConfig;
import com.hft.exchange.binance.BinanceHttpClient;
import com.hft.exchange.binance.BinanceMarketDataPort;
import com.hft.exchange.binance.BinanceWebSocketClient;
import com.hft.exchange.binance.dto.BinanceExchangeInfo;
import com.hft.exchange.binance.dto.BinanceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    private final Environment environment;
    private final SimpMessagingTemplate messagingTemplate;
    private final TradingService tradingService;
    private final Map<String, ExchangeConnection> connections = new ConcurrentHashMap<>();

    // HTTP clients for symbol fetching
    private AlpacaHttpClient alpacaClient;
    private BinanceHttpClient binanceClient;

    // WebSocket clients and MarketDataPorts for real-time data
    private BinanceWebSocketClient binanceWsClient;
    private BinanceMarketDataPort binanceMarketDataPort;
    private AlpacaWebSocketClient alpacaWsClient;
    private AlpacaMarketDataPort alpacaMarketDataPort;

    // Tracks which symbols have real (non-stub) data feeds
    private final Set<String> realDataSymbols = ConcurrentHashMap.newKeySet();

    // Cached symbols
    private final Map<String, List<SymbolDto>> symbolCache = new ConcurrentHashMap<>();

    public ExchangeService(ExchangeProperties properties, Environment environment,
                           SimpMessagingTemplate messagingTemplate, @Lazy TradingService tradingService) {
        this.properties = properties;
        this.environment = environment;
        this.messagingTemplate = messagingTemplate;
        this.tradingService = tradingService;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing exchange connections");

        // Initialize Alpaca connection
        if (properties.getAlpaca().isEnabled()) {
            initializeAlpaca();
        } else {
            log.info("Alpaca exchange is disabled");
            connections.put("ALPACA", new ExchangeConnection("ALPACA", "Alpaca Markets", "disabled", false, false, "Disabled in configuration"));
        }

        // Initialize Binance connection
        if (properties.getBinance().isEnabled()) {
            initializeBinance();
        } else {
            log.info("Binance exchange is disabled");
            connections.put("BINANCE", new ExchangeConnection("BINANCE", "Binance", "disabled", false, false, "Disabled in configuration"));
        }
    }

    private void initializeAlpaca() {
        ExchangeProperties.AlpacaProperties alpaca = properties.getAlpaca();
        String mode = alpaca.getMode();
        log.info("Initializing Alpaca in {} mode", mode);

        if (alpaca.isStub()) {
            connections.put("ALPACA", new ExchangeConnection("ALPACA", "Alpaca Markets (Stub)", mode, true, true, null));
            symbolCache.put("ALPACA", getStubAlpacaSymbols());
            log.info("Alpaca running in STUB mode - simulated connection");
        } else {
            if (alpaca.getApiKey().isEmpty() || alpaca.getSecretKey().isEmpty()) {
                connections.put("ALPACA", new ExchangeConnection("ALPACA",
                    "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")",
                    mode, false, false, "API credentials not configured"));
            } else {
                // Create HTTP client for real API calls
                AlpacaConfig config = new AlpacaConfig(
                        alpaca.getApiKey(),
                        alpaca.getSecretKey(),
                        alpaca.isPaperTrading(),
                        alpaca.getDataFeed()
                );
                alpacaClient = new AlpacaHttpClient(config);

                // Create WebSocket client and MarketDataPort for real-time data
                AlpacaWebSocketClient wsClient = new AlpacaWebSocketClient(config);
                AlpacaMarketDataPort mdPort = new AlpacaMarketDataPort(alpacaClient, wsClient);
                mdPort.addQuoteListener(quote -> {
                    tradingService.getTradingEngine().onQuoteUpdate(quote);
                    QuoteDto dto = QuoteDto.from(quote);
                    String exch = quote.getSymbol().getExchange().name();
                    String ticker = quote.getSymbol().getTicker();
                    messagingTemplate.convertAndSend("/topic/quotes/" + exch + "/" + ticker, dto);
                    messagingTemplate.convertAndSend("/topic/quotes", dto);
                });
                wsClient.connect().thenRun(() -> subscribeActiveSymbols("ALPACA", mdPort));
                this.alpacaWsClient = wsClient;
                this.alpacaMarketDataPort = mdPort;

                connections.put("ALPACA", new ExchangeConnection("ALPACA",
                    "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")",
                    mode, true, true, null));
            }
        }
    }

    private void initializeBinance() {
        ExchangeProperties.BinanceProperties binance = properties.getBinance();
        String mode = binance.getMode();
        log.info("Initializing Binance in {} mode", mode);

        if (binance.isStub()) {
            connections.put("BINANCE", new ExchangeConnection("BINANCE", "Binance (Stub)", mode, true, true, null));
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

            // Create WebSocket client and MarketDataPort for real-time data
            BinanceWebSocketClient wsClient = new BinanceWebSocketClient(config);
            BinanceMarketDataPort mdPort = new BinanceMarketDataPort(binanceClient, wsClient);
            mdPort.addQuoteListener(quote -> {
                tradingService.getTradingEngine().onQuoteUpdate(quote);
                QuoteDto dto = QuoteDto.from(quote);
                String exch = quote.getSymbol().getExchange().name();
                String ticker = quote.getSymbol().getTicker();
                messagingTemplate.convertAndSend("/topic/quotes/" + exch + "/" + ticker, dto);
                messagingTemplate.convertAndSend("/topic/quotes", dto);
            });
            wsClient.connect().thenRun(() -> subscribeActiveSymbols("BINANCE", mdPort));
            this.binanceWsClient = wsClient;
            this.binanceMarketDataPort = mdPort;

            if (binance.getApiKey().isEmpty() || binance.getSecretKey().isEmpty()) {
                connections.put("BINANCE", new ExchangeConnection("BINANCE",
                    "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")",
                    mode, true, false, "API credentials not configured (read-only)"));
            } else {
                connections.put("BINANCE", new ExchangeConnection("BINANCE",
                    "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")",
                    mode, true, true, null));
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
                existing.exchange, existing.name, existing.mode, connected, authenticated, error
            ));
        }
    }

    /**
     * Internal representation of an exchange connection.
     */
    private static class ExchangeConnection {
        final String exchange;
        final String name;
        final String mode;
        final boolean connected;
        final boolean authenticated;
        final String errorMessage;
        final long lastHeartbeat;

        ExchangeConnection(String exchange, String name, String mode, boolean connected, boolean authenticated, String errorMessage) {
            this.exchange = exchange;
            this.name = name;
            this.mode = mode;
            this.connected = connected;
            this.authenticated = authenticated;
            this.errorMessage = errorMessage;
            this.lastHeartbeat = connected ? System.currentTimeMillis() : 0;
        }

        ExchangeStatusDto toDto() {
            return new ExchangeStatusDto(
                exchange,
                name,
                mode,
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
        // Top 5 most traded US stocks by market cap and volume
        return List.of(
                SymbolDto.equity("AAPL", "Apple Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("MSFT", "Microsoft Corporation", "ALPACA", true, true, true),
                SymbolDto.equity("NVDA", "NVIDIA Corporation", "ALPACA", true, true, true),
                SymbolDto.equity("TSLA", "Tesla Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("GOOGL", "Alphabet Inc.", "ALPACA", true, true, true)
        );
    }

    private List<SymbolDto> getStubBinanceSymbols() {
        // Top 5 most traded crypto pairs by volume
        return List.of(
                SymbolDto.crypto("BTCUSDT", "BTC/USDT", "BINANCE", "BTC", "USDT", true),
                SymbolDto.crypto("ETHUSDT", "ETH/USDT", "BINANCE", "ETH", "USDT", true),
                SymbolDto.crypto("SOLUSDT", "SOL/USDT", "BINANCE", "SOL", "USDT", true),
                SymbolDto.crypto("BNBUSDT", "BNB/USDT", "BINANCE", "BNB", "USDT", true),
                SymbolDto.crypto("XRPUSDT", "XRP/USDT", "BINANCE", "XRP", "USDT", true)
        );
    }

    /**
     * Switches the runtime mode for an exchange, tearing down the existing connection
     * and reinitializing with the new mode. Loads credentials from application-local.properties
     * if they are not already present in the current properties.
     */
    public synchronized ExchangeStatusDto switchMode(String exchange, String newMode) {
        String key = exchange.toUpperCase();
        boolean isNonStub = !"stub".equalsIgnoreCase(newMode);
        if (isNonStub) {
            loadLocalCredentials();
        }
        return switch (key) {
            case "ALPACA" -> {
                if (alpacaWsClient != null) {
                    alpacaWsClient.disconnect();
                    alpacaWsClient = null;
                }
                alpacaMarketDataPort = null;
                realDataSymbols.removeIf(k -> k.startsWith("ALPACA:"));
                if (alpacaClient != null) {
                    alpacaClient.close();
                    alpacaClient = null;
                }
                symbolCache.remove(key);
                properties.getAlpaca().setMode(newMode);
                initializeAlpaca();
                yield getExchangeStatus(key);
            }
            case "BINANCE" -> {
                if (binanceWsClient != null) {
                    binanceWsClient.disconnect();
                    binanceWsClient = null;
                }
                binanceMarketDataPort = null;
                realDataSymbols.removeIf(k -> k.startsWith("BINANCE:"));
                if (binanceClient != null) {
                    binanceClient.close();
                    binanceClient = null;
                }
                symbolCache.remove(key);
                properties.getBinance().setMode(newMode);
                initializeBinance();
                yield getExchangeStatus(key);
            }
            default -> null;
        };
    }

    /**
     * Loads API credentials from application-local.properties if they are missing
     * from the current properties. This allows switching from stub to live/testnet
     * modes at runtime even when the app was started in stub profile.
     */
    private void loadLocalCredentials() {
        Properties localProps = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application-local.properties")) {
            if (is == null) {
                log.debug("No application-local.properties found, skipping credential loading");
                return;
            }
            localProps.load(is);
        } catch (IOException e) {
            log.warn("Failed to load application-local.properties: {}", e.getMessage());
            return;
        }

        ExchangeProperties.AlpacaProperties alpaca = properties.getAlpaca();
        if (alpaca.getApiKey().isEmpty()) {
            String key = resolveCredential(localProps, "hft.exchanges.alpaca.api-key", "ALPACA_API_KEY");
            if (!key.isEmpty()) {
                alpaca.setApiKey(key);
                log.info("Loaded Alpaca API key from local properties/environment");
            }
        }
        if (alpaca.getSecretKey().isEmpty()) {
            String key = resolveCredential(localProps, "hft.exchanges.alpaca.secret-key", "ALPACA_SECRET_KEY");
            if (!key.isEmpty()) {
                alpaca.setSecretKey(key);
                log.info("Loaded Alpaca secret key from local properties/environment");
            }
        }

        ExchangeProperties.BinanceProperties binance = properties.getBinance();
        if (binance.getApiKey().isEmpty()) {
            String key = resolveCredential(localProps, "hft.exchanges.binance.api-key", "BINANCE_API_KEY");
            if (!key.isEmpty()) {
                binance.setApiKey(key);
                log.info("Loaded Binance API key from local properties/environment");
            }
        }
        if (binance.getSecretKey().isEmpty()) {
            String key = resolveCredential(localProps, "hft.exchanges.binance.secret-key", "BINANCE_SECRET_KEY");
            if (!key.isEmpty()) {
                binance.setSecretKey(key);
                log.info("Loaded Binance secret key from local properties/environment");
            }
        }
    }

    private String resolveCredential(Properties localProps, String propKey, String envKey) {
        // Try local properties file first
        String value = localProps.getProperty(propKey, "");
        if (!value.isEmpty()) {
            return value;
        }
        // Fall back to environment variable
        String envValue = environment.getProperty(envKey, "");
        return envValue;
    }

    /**
     * Subscribes to quotes for symbols used by active strategies on the given exchange.
     */
    private void subscribeActiveSymbols(String exchange, MarketDataPort port) {
        try {
            Set<Symbol> symbols = tradingService.getStrategies().stream()
                    .flatMap(s -> s.symbols().stream())
                    .map(ticker -> new Symbol(ticker, Exchange.valueOf(exchange)))
                    .collect(Collectors.toSet());

            if (!symbols.isEmpty()) {
                port.subscribeQuotes(symbols);
                symbols.forEach(s -> realDataSymbols.add(exchange + ":" + s.getTicker()));
                log.info("Subscribed to real-time quotes for {} on {}", symbols.size(), exchange);
            }
        } catch (Exception e) {
            log.warn("Failed to subscribe active symbols for {}: {}", exchange, e.getMessage());
        }
    }

    /**
     * Returns true if the given symbol has a real (non-stub) data feed.
     */
    public boolean isRealDataSymbol(String exchange, String ticker) {
        return realDataSymbols.contains(exchange + ":" + ticker);
    }

    /**
     * Returns the current Alpaca HTTP client, or null if in stub mode or not initialized.
     */
    public AlpacaHttpClient getAlpacaClient() {
        return alpacaClient;
    }

    /**
     * Returns the current Binance HTTP client, or null if in stub mode or not initialized.
     */
    public BinanceHttpClient getBinanceClient() {
        return binanceClient;
    }

    @PreDestroy
    public void cleanup() {
        if (alpacaWsClient != null) {
            alpacaWsClient.disconnect();
        }
        if (binanceWsClient != null) {
            binanceWsClient.disconnect();
        }
        if (alpacaClient != null) {
            alpacaClient.close();
        }
        if (binanceClient != null) {
            binanceClient.close();
        }
    }
}
