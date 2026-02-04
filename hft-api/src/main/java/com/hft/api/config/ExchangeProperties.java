package com.hft.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for exchange connections.
 * Supports multiple environments: stub, test (testnet/paper), production.
 */
@Component
@ConfigurationProperties(prefix = "hft.exchanges")
public class ExchangeProperties {

    private AlpacaProperties alpaca = new AlpacaProperties();
    private BinanceProperties binance = new BinanceProperties();

    public AlpacaProperties getAlpaca() {
        return alpaca;
    }

    public void setAlpaca(AlpacaProperties alpaca) {
        this.alpaca = alpaca;
    }

    public BinanceProperties getBinance() {
        return binance;
    }

    public void setBinance(BinanceProperties binance) {
        this.binance = binance;
    }

    /**
     * Alpaca exchange configuration.
     */
    public static class AlpacaProperties {
        private boolean enabled = false;
        private String mode = "stub"; // stub, paper, live
        private String apiKey = "";
        private String secretKey = "";
        private String dataFeed = "iex"; // iex (free) or sip (paid)

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getDataFeed() {
            return dataFeed;
        }

        public void setDataFeed(String dataFeed) {
            this.dataFeed = dataFeed;
        }

        public boolean isPaperTrading() {
            return "paper".equalsIgnoreCase(mode) || "sandbox".equalsIgnoreCase(mode);
        }

        public boolean isStub() {
            return "stub".equalsIgnoreCase(mode);
        }

        public boolean isLive() {
            return "live".equalsIgnoreCase(mode);
        }
    }

    /**
     * Binance exchange configuration.
     */
    public static class BinanceProperties {
        private boolean enabled = false;
        private String mode = "stub"; // stub, testnet, live
        private String apiKey = "";
        private String secretKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isTestnet() {
            return "testnet".equalsIgnoreCase(mode);
        }

        public boolean isStub() {
            return "stub".equalsIgnoreCase(mode);
        }

        public boolean isLive() {
            return "live".equalsIgnoreCase(mode);
        }
    }
}
