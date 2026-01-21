package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.ExchangeStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing exchange connections and reporting status.
 */
@Service
public class ExchangeService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final ExchangeProperties properties;
    private final Map<String, ExchangeConnection> connections = new ConcurrentHashMap<>();

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
            log.info("Alpaca running in STUB mode - simulated connection");
        } else {
            // Real connection would be established here
            // For now, check if credentials are configured
            if (alpaca.getApiKey().isEmpty() || alpaca.getSecretKey().isEmpty()) {
                connections.put("ALPACA", new ExchangeConnection("ALPACA",
                    "Alpaca Markets (" + (alpaca.isPaperTrading() ? "Paper" : "Live") + ")",
                    false, false, "API credentials not configured"));
            } else {
                // TODO: Establish real connection
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
            log.info("Binance running in STUB mode - simulated connection");
        } else {
            // Real connection would be established here
            if (binance.getApiKey().isEmpty() || binance.getSecretKey().isEmpty()) {
                connections.put("BINANCE", new ExchangeConnection("BINANCE",
                    "Binance (" + (binance.isTestnet() ? "Testnet" : "Live") + ")",
                    false, false, "API credentials not configured"));
            } else {
                // TODO: Establish real connection
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
}
