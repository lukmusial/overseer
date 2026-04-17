package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.AccountBalanceDto;
import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.QuoteDto;
import com.hft.api.dto.SymbolDto;
import com.hft.algo.base.TradingStrategy;
import com.hft.core.model.Exchange;
import com.hft.core.model.Symbol;
import com.hft.core.port.MarketDataPort;
import com.hft.exchange.alpaca.AlpacaConfig;
import com.hft.exchange.alpaca.AlpacaHttpClient;
import com.hft.exchange.alpaca.AlpacaMarketDataPort;
import com.hft.exchange.alpaca.AlpacaOrderMapper;
import com.hft.exchange.alpaca.AlpacaOrderPort;
import com.hft.exchange.alpaca.AlpacaTradingStreamClient;
import com.hft.exchange.alpaca.AlpacaWebSocketClient;
import com.hft.exchange.alpaca.dto.AlpacaAccount;
import com.hft.exchange.alpaca.dto.AlpacaAsset;
import com.hft.exchange.binance.BinanceConfig;
import com.hft.exchange.binance.BinanceHttpClient;
import com.hft.exchange.binance.BinanceMarketDataPort;
import com.hft.exchange.binance.BinanceOrderPort;
import com.hft.exchange.binance.BinanceWebSocketClient;
import com.hft.exchange.binance.BinanceWebSocketOrderPort;
import com.hft.exchange.binance.dto.BinanceAccount;
import com.hft.exchange.binance.dto.BinanceExchangeInfo;
import com.hft.exchange.binance.dto.BinanceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final StubMarketDataService stubMarketDataService;
    private final ChartDataService chartDataService;
    private final Map<String, ExchangeConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService uiBroadcastExecutor = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "ui-broadcast"); t.setDaemon(true); return t; }
    );
    private final Map<String, String> topicCache = new ConcurrentHashMap<>();

    // HTTP clients for symbol fetching
    private AlpacaHttpClient alpacaClient;
    private BinanceHttpClient binanceClient;

    // WebSocket clients and MarketDataPorts for real-time data
    private BinanceWebSocketClient binanceWsClient;
    private BinanceMarketDataPort binanceMarketDataPort;
    private AlpacaWebSocketClient alpacaWsClient;
    private AlpacaMarketDataPort alpacaMarketDataPort;

    // WebSocket order ports and trading streams
    private BinanceWebSocketOrderPort binanceWsOrderPort;
    private AlpacaTradingStreamClient alpacaTradingStream;

    // Tracks which symbols have real (non-stub) data feeds
    private final Set<String> realDataSymbols = ConcurrentHashMap.newKeySet();

    // Cached symbols
    private final Map<String, List<SymbolDto>> symbolCache = new ConcurrentHashMap<>();

    private static final Path MODE_FILE = Paths.get(
            System.getProperty("user.home"), ".hft-client", "exchange-modes.properties");

    public ExchangeService(ExchangeProperties properties, Environment environment,
                           SimpMessagingTemplate messagingTemplate, @Lazy TradingService tradingService,
                           @Lazy StubMarketDataService stubMarketDataService,
                           @Lazy ChartDataService chartDataService) {
        this.properties = properties;
        this.environment = environment;
        this.messagingTemplate = messagingTemplate;
        this.tradingService = tradingService;
        this.stubMarketDataService = stubMarketDataService;
        this.chartDataService = chartDataService;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing exchange connections");

        // Restore persisted exchange modes from previous session
        loadPersistedModes();

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
            tradingService.getTradingEngine().registerExchange(Exchange.ALPACA, new StubOrderPort());
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

                // Verify credentials with a lightweight API call
                String alpacaLabel = "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")";
                boolean authenticated = false;
                String authError = null;
                try {
                    alpacaClient.get("/v2/account", AlpacaAccount.class).get(10, TimeUnit.SECONDS);
                    authenticated = true;
                    log.info("Alpaca authentication verified");
                } catch (Exception e) {
                    authError = "Authentication failed: " + extractErrorMessage(e);
                    log.warn("Alpaca authentication failed: {}", authError);
                }

                if (authenticated) {
                    // Create WebSocket client and MarketDataPort for real-time data
                    AlpacaWebSocketClient wsClient = new AlpacaWebSocketClient(config);
                    AlpacaMarketDataPort mdPort = new AlpacaMarketDataPort(alpacaClient, wsClient);
                    mdPort.addQuoteListener(quote -> {
                        // HOT PATH: trading-critical operations only
                        tradingService.getTradingEngine().onQuoteUpdate(quote);
                        tradingService.dispatchQuoteToStrategies(quote);

                        // COLD PATH: capture values for async UI broadcast
                        String exch = quote.getSymbol().getExchange().name();
                        String ticker = quote.getSymbol().getTicker();
                        long midPrice = quote.getMidPrice();
                        QuoteDto dto = QuoteDto.from(quote);
                        uiBroadcastExecutor.execute(() -> {
                            stubMarketDataService.updatePrice(exch, ticker, midPrice);
                            messagingTemplate.convertAndSend(getQuoteTopic(exch, ticker), dto);
                            messagingTemplate.convertAndSend("/topic/quotes", dto);
                        });
                    });
                    wsClient.connect().thenRun(() -> subscribeActiveSymbols("ALPACA", mdPort));
                    this.alpacaWsClient = wsClient;
                    this.alpacaMarketDataPort = mdPort;

                    // Register REST order port (Alpaca doesn't support WS order placement)
                    AlpacaOrderPort orderPort = new AlpacaOrderPort(alpacaClient);
                    tradingService.getTradingEngine().registerExchange(Exchange.ALPACA, orderPort);
                    log.info("Alpaca REST order port registered");

                    // Connect trading stream for real-time order updates (fills, cancellations)
                    AlpacaTradingStreamClient tradingStream = new AlpacaTradingStreamClient(config);
                    tradingStream.addTradeUpdateListener(update -> {
                        var engine = tradingService.getTradingEngine();
                        var order = AlpacaOrderMapper.toOrder(update.order());
                        switch (update.event()) {
                            case "fill", "partial_fill" -> {
                                long fillQty = update.qty() != null ? Long.parseLong(update.qty()) : 0;
                                long fillPrice = update.price() != null
                                        ? com.hft.core.util.FastDecimalParser.parseDecimal(update.price(), 2, 0) : 0;
                                if (fillQty > 0 && fillPrice > 0) {
                                    engine.onOrderFilled(order, fillQty, fillPrice);
                                }
                            }
                            case "canceled", "expired", "done_for_day" -> engine.onOrderCancelled(order);
                            case "rejected" -> engine.onOrderRejected(order, "Rejected by exchange");
                            case "accepted", "new" -> engine.onOrderAccepted(order);
                            default -> log.debug("Unhandled Alpaca trade update event: {}", update.event());
                        }
                    });
                    tradingStream.connect();
                    this.alpacaTradingStream = tradingStream;
                    log.info("Alpaca trading stream connected and wired to engine");
                }

                connections.put("ALPACA", new ExchangeConnection("ALPACA", alpacaLabel,
                    mode, authenticated, authenticated, authError));
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
            tradingService.getTradingEngine().registerExchange(Exchange.BINANCE, new StubOrderPort());
            log.info("Binance running in STUB mode - simulated connection");
        } else {
            String binanceLabel = "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")";

            if (binance.getApiKey().isEmpty() || binance.getSecretKey().isEmpty()) {
                // No credentials — create client with dummy keys for public endpoints only
                BinanceConfig config = new BinanceConfig("dummy", "dummy", binance.isTestnet());
                binanceClient = new BinanceHttpClient(config);
                connections.put("BINANCE", new ExchangeConnection("BINANCE", binanceLabel,
                    mode, true, false, "API credentials not configured (read-only)"));
            } else {
                BinanceConfig config = new BinanceConfig(
                        binance.getApiKey(), binance.getSecretKey(), binance.isTestnet());
                binanceClient = new BinanceHttpClient(config);

                // Verify credentials with a lightweight signed API call
                boolean authenticated = false;
                String authError = null;
                try {
                    binanceClient.signedGet("/api/v3/account", new java.util.LinkedHashMap<>(), BinanceAccount.class)
                            .get(10, TimeUnit.SECONDS);
                    authenticated = true;
                    log.info("Binance authentication verified");
                } catch (Exception e) {
                    authError = "Authentication failed: " + extractErrorMessage(e);
                    log.warn("Binance authentication failed: {}", authError);
                }

                if (authenticated) {
                    // Create WebSocket client and MarketDataPort for real-time data
                    BinanceWebSocketClient wsClient = new BinanceWebSocketClient(config);
                    BinanceMarketDataPort mdPort = new BinanceMarketDataPort(binanceClient, wsClient);
                    mdPort.addQuoteListener(quote -> {
                        // HOT PATH: trading-critical operations only
                        tradingService.getTradingEngine().onQuoteUpdate(quote);
                        tradingService.dispatchQuoteToStrategies(quote);

                        // COLD PATH: capture values for async UI broadcast
                        String exch = quote.getSymbol().getExchange().name();
                        String ticker = quote.getSymbol().getTicker();
                        long midPrice = quote.getMidPrice();
                        QuoteDto dto = QuoteDto.from(quote);
                        uiBroadcastExecutor.execute(() -> {
                            stubMarketDataService.updatePrice(exch, ticker, midPrice);
                            messagingTemplate.convertAndSend(getQuoteTopic(exch, ticker), dto);
                            messagingTemplate.convertAndSend("/topic/quotes", dto);
                        });
                    });
                    wsClient.connect().thenRun(() -> subscribeActiveSymbols("BINANCE", mdPort));
                    this.binanceWsClient = wsClient;
                    this.binanceMarketDataPort = mdPort;

                    // Register WebSocket order port for low-latency order submission
                    BinanceWebSocketOrderPort wsOrderPort = new BinanceWebSocketOrderPort(config, binanceClient);
                    try {
                        wsOrderPort.connect().get(10, TimeUnit.SECONDS);
                        tradingService.getTradingEngine().registerExchange(Exchange.BINANCE, wsOrderPort);
                        this.binanceWsOrderPort = wsOrderPort;
                        log.info("Binance WebSocket order port registered");
                    } catch (Exception e) {
                        log.warn("Binance WebSocket order port connection failed, falling back to REST order port: {}",
                                extractErrorMessage(e));
                        BinanceOrderPort restOrderPort = new BinanceOrderPort(binanceClient);
                        tradingService.getTradingEngine().registerExchange(Exchange.BINANCE, restOrderPort);
                        log.info("Binance REST order port registered (fallback)");
                    }
                }

                connections.put("BINANCE", new ExchangeConnection("BINANCE", binanceLabel,
                    mode, authenticated, authenticated, authError));
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
                chartDataService.clearCache();
                properties.getAlpaca().setMode(newMode);
                persistMode(key, newMode);
                initializeAlpaca();
                yield getExchangeStatus(key);
            }
            case "BINANCE" -> {
                if (binanceWsOrderPort != null) {
                    binanceWsOrderPort.disconnect();
                    binanceWsOrderPort = null;
                }
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
                chartDataService.clearCache();
                properties.getBinance().setMode(newMode);
                persistMode(key, newMode);
                initializeBinance();
                yield getExchangeStatus(key);
            }
            default -> null;
        };
    }

    /**
     * Restores exchange modes from ~/.hft-client/exchange-modes.properties.
     * If a persisted mode differs from the default (stub), loads credentials
     * and applies the persisted mode so it survives app restarts.
     */
    private void loadPersistedModes() {
        if (!Files.exists(MODE_FILE)) return;

        Properties modes = new Properties();
        try (var is = Files.newInputStream(MODE_FILE)) {
            modes.load(is);
        } catch (IOException e) {
            log.warn("Failed to load persisted exchange modes: {}", e.getMessage());
            return;
        }

        String alpacaMode = modes.getProperty("ALPACA");
        if (alpacaMode != null && !"stub".equals(alpacaMode)) {
            log.info("Restoring Alpaca mode from previous session: {}", alpacaMode);
            loadLocalCredentials();
            properties.getAlpaca().setMode(alpacaMode);
        }

        String binanceMode = modes.getProperty("BINANCE");
        if (binanceMode != null && !"stub".equals(binanceMode)) {
            log.info("Restoring Binance mode from previous session: {}", binanceMode);
            loadLocalCredentials();
            properties.getBinance().setMode(binanceMode);
        }
    }

    /**
     * Persists the exchange mode to disk so it survives app restarts.
     */
    private void persistMode(String exchange, String mode) {
        try {
            // Load existing modes
            Properties modes = new Properties();
            if (Files.exists(MODE_FILE)) {
                try (var is = Files.newInputStream(MODE_FILE)) {
                    modes.load(is);
                }
            }

            modes.setProperty(exchange, mode);

            // Write back
            Files.createDirectories(MODE_FILE.getParent());
            try (var os = Files.newOutputStream(MODE_FILE)) {
                modes.store(os, "Exchange modes - persisted across restarts");
            }
        } catch (IOException e) {
            log.warn("Failed to persist exchange mode for {}: {}", exchange, e.getMessage());
        }
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
     * Returns an Alpaca HTTP client for data-only purposes (e.g., chart candles).
     * If no trading client exists (stub mode), tries to create one from local credentials.
     */
    public AlpacaHttpClient getAlpacaDataClient() {
        if (alpacaClient != null) {
            return alpacaClient;
        }
        // Try to create a data-only client from credentials
        loadLocalCredentials();
        ExchangeProperties.AlpacaProperties alpaca = properties.getAlpaca();
        if (!alpaca.getApiKey().isEmpty() && !alpaca.getSecretKey().isEmpty()) {
            AlpacaConfig config = new AlpacaConfig(
                    alpaca.getApiKey(), alpaca.getSecretKey(), true, alpaca.getDataFeed());
            return new AlpacaHttpClient(config);
        }
        return null;
    }

    /**
     * Returns the current Binance HTTP client, or null if in stub mode or not initialized.
     */
    public BinanceHttpClient getBinanceClient() {
        return binanceClient;
    }

    private String getQuoteTopic(String exchange, String ticker) {
        String key = exchange + ":" + ticker;
        return topicCache.computeIfAbsent(key, k -> "/topic/quotes/" + exchange + "/" + ticker);
    }

    private String extractErrorMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }

    /**
     * Returns account balance information for the specified exchange.
     * Returns null if the exchange is in stub mode or credentials are unavailable.
     */
    public AccountBalanceDto getAccountBalance(String exchange) {
        try {
            return switch (exchange.toUpperCase()) {
                case "ALPACA" -> {
                    if (alpacaClient == null) yield null;
                    AlpacaAccount account = alpacaClient.get("/v2/account", AlpacaAccount.class)
                            .get(10, TimeUnit.SECONDS);
                    List<AccountBalanceDto.BalanceEntry> entries = List.of(
                            new AccountBalanceDto.BalanceEntry("cash", account.getCash(), "0", account.getCash()),
                            new AccountBalanceDto.BalanceEntry("equity", account.getEquity(), "0", account.getEquity()),
                            new AccountBalanceDto.BalanceEntry("buyingPower", account.getBuyingPower(), "0", account.getBuyingPower()),
                            new AccountBalanceDto.BalanceEntry("portfolioValue", account.getPortfolioValue(), "0", account.getPortfolioValue())
                    );
                    yield new AccountBalanceDto("ALPACA", entries);
                }
                case "BINANCE" -> {
                    if (binanceClient == null) yield null;
                    var params = new java.util.LinkedHashMap<String, String>();
                    BinanceAccount account = binanceClient.signedGet("/api/v3/account", params, BinanceAccount.class)
                            .get(10, TimeUnit.SECONDS);

                    // Derive relevant assets from strategy symbols
                    Set<String> relevantAssets = getStrategyAssets("BINANCE");

                    List<AccountBalanceDto.BalanceEntry> entries = account.getBalances().stream()
                            .filter(b -> {
                                if (relevantAssets.isEmpty()) {
                                    // No strategies — show all non-zero balances
                                    double free = Double.parseDouble(b.getFree());
                                    double locked = Double.parseDouble(b.getLocked());
                                    return free != 0 || locked != 0;
                                }
                                return relevantAssets.contains(b.getAsset());
                            })
                            .map(b -> {
                                var free = new java.math.BigDecimal(b.getFree());
                                var locked = new java.math.BigDecimal(b.getLocked());
                                String total = free.add(locked).stripTrailingZeros().toPlainString();
                                return new AccountBalanceDto.BalanceEntry(b.getAsset(), b.getFree(), b.getLocked(), total);
                            })
                            .collect(Collectors.toList());
                    yield new AccountBalanceDto("BINANCE", entries);
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to fetch account balance for {}: {}", exchange, extractErrorMessage(e));
            return null;
        }
    }

    /**
     * Periodically reconciles internal positions with actual exchange balances.
     * Runs every 30 seconds. Only reconciles for non-stub exchanges with active strategies.
     */
    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void reconcilePositions() {
        if (binanceClient == null) return;

        try {
            Set<String> strategyAssets = getStrategyAssets("BINANCE");
            if (strategyAssets.isEmpty()) return;

            var params = new java.util.LinkedHashMap<String, String>();
            BinanceAccount account = binanceClient.signedGet("/api/v3/account", params, BinanceAccount.class)
                    .get(10, TimeUnit.SECONDS);

            // Build asset -> balance map from exchange
            Map<String, Long> exchangeBalances = new java.util.HashMap<>();
            for (BinanceAccount.Balance b : account.getBalances()) {
                if (strategyAssets.contains(b.getAsset())) {
                    var total = new java.math.BigDecimal(b.getFree()).add(new java.math.BigDecimal(b.getLocked()));
                    long scaledQty = total.multiply(java.math.BigDecimal.valueOf(100_000_000))
                            .setScale(0, java.math.RoundingMode.FLOOR).longValue();
                    exchangeBalances.put(b.getAsset(), scaledQty);
                }
            }

            // Reconcile only strategy symbol positions against exchange base asset balances
            List<SymbolDto> symbols = symbolCache.get("BINANCE");
            if (symbols == null) return;

            // Collect strategy tickers
            Set<String> strategyTickers = new java.util.HashSet<>();
            for (var strategy : tradingService.getActiveStrategies()) {
                for (Symbol sym : strategy.getSymbols()) {
                    if (sym.getExchange() == Exchange.BINANCE) {
                        strategyTickers.add(sym.getTicker());
                    }
                }
            }

            var positionManager = tradingService.getTradingEngine().getPositionManager();
            for (SymbolDto sym : symbols) {
                if (strategyTickers.contains(sym.symbol())
                        && sym.baseAsset() != null
                        && exchangeBalances.containsKey(sym.baseAsset())) {
                    Symbol coreSymbol = new Symbol(sym.symbol(), Exchange.BINANCE);
                    long exchangeQty = exchangeBalances.get(sym.baseAsset());
                    var position = positionManager.getPosition(coreSymbol);
                    long internalQty = position != null ? position.getQuantity() : 0;
                    if (internalQty != exchangeQty) {
                        double internalHuman = internalQty / 100_000_000.0;
                        double exchangeHuman = exchangeQty / 100_000_000.0;
                        String msg = String.format(
                                "Position drift on %s: internal=%.8f, exchange=%.8f — reconciled to exchange value",
                                sym.symbol(), internalHuman, exchangeHuman);
                        log.warn(msg);
                        messagingTemplate.convertAndSend("/topic/notifications",
                                Map.of("type", "POSITION_DRIFT", "message", msg, "timestamp", System.currentTimeMillis()));
                    }
                    positionManager.reconcileQuantity(coreSymbol, exchangeQty);
                }
            }
        } catch (Exception e) {
            log.debug("Position reconciliation failed: {}", e.getMessage());
        }
    }

    /**
     * Derives the set of base and quote assets from active strategy symbols for an exchange.
     * E.g., strategy on BTCUSDT returns {"BTC", "USDT"}.
     * Uses the symbol cache to look up baseAsset/quoteAsset.
     */
    private Set<String> getStrategyAssets(String exchange) {
        Set<String> assets = new java.util.HashSet<>();

        // Collect tickers from active strategies for this exchange
        Set<String> tickers = new java.util.HashSet<>();
        Exchange exchangeEnum;
        try {
            exchangeEnum = Exchange.valueOf(exchange);
        } catch (IllegalArgumentException e) {
            return assets;
        }

        for (var strategy : tradingService.getActiveStrategies()) {
            for (Symbol sym : strategy.getSymbols()) {
                if (sym.getExchange() == exchangeEnum) {
                    tickers.add(sym.getTicker());
                }
            }
        }

        if (tickers.isEmpty()) {
            return assets;
        }

        // Look up base/quote assets from the symbol cache (populate if needed)
        List<SymbolDto> symbols = symbolCache.get(exchange);
        if (symbols == null || symbols.isEmpty()) {
            symbols = getSymbols(exchange);
        }
        for (SymbolDto sym : symbols) {
            if (tickers.contains(sym.symbol())) {
                if (sym.baseAsset() != null) assets.add(sym.baseAsset());
                if (sym.quoteAsset() != null) assets.add(sym.quoteAsset());
            }
        }

        return assets;
    }

    @PreDestroy
    public void cleanup() {
        uiBroadcastExecutor.shutdown();
        if (binanceWsOrderPort != null) {
            binanceWsOrderPort.disconnect();
        }
        if (alpacaTradingStream != null) {
            alpacaTradingStream.disconnect();
        }
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
