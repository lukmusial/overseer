package com.hft.exchange.alpaca.dto;

import java.time.Instant;

/**
 * DTO for Alpaca account API response.
 */
public class AlpacaAccount {
    private String id;
    private String accountNumber;
    private String status;
    private String currency;
    private String cash;
    private String portfolioValue;
    private String equity;
    private String lastEquity;
    private String buyingPower;
    private String regtBuyingPower;
    private String daytradingBuyingPower;
    private String initialMargin;
    private String maintenanceMargin;
    private String lastMaintenanceMargin;
    private String longMarketValue;
    private String shortMarketValue;
    private boolean patternDayTrader;
    private boolean tradingBlocked;
    private boolean transfersBlocked;
    private boolean accountBlocked;
    private Instant createdAt;
    private boolean tradeSuspendedByUser;
    private int multiplier;
    private boolean shortingEnabled;
    private String sma;
    private int daytradingCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCash() {
        return cash;
    }

    public void setCash(String cash) {
        this.cash = cash;
    }

    public String getPortfolioValue() {
        return portfolioValue;
    }

    public void setPortfolioValue(String portfolioValue) {
        this.portfolioValue = portfolioValue;
    }

    public String getEquity() {
        return equity;
    }

    public void setEquity(String equity) {
        this.equity = equity;
    }

    public String getLastEquity() {
        return lastEquity;
    }

    public void setLastEquity(String lastEquity) {
        this.lastEquity = lastEquity;
    }

    public String getBuyingPower() {
        return buyingPower;
    }

    public void setBuyingPower(String buyingPower) {
        this.buyingPower = buyingPower;
    }

    public String getRegtBuyingPower() {
        return regtBuyingPower;
    }

    public void setRegtBuyingPower(String regtBuyingPower) {
        this.regtBuyingPower = regtBuyingPower;
    }

    public String getDaytradingBuyingPower() {
        return daytradingBuyingPower;
    }

    public void setDaytradingBuyingPower(String daytradingBuyingPower) {
        this.daytradingBuyingPower = daytradingBuyingPower;
    }

    public String getInitialMargin() {
        return initialMargin;
    }

    public void setInitialMargin(String initialMargin) {
        this.initialMargin = initialMargin;
    }

    public String getMaintenanceMargin() {
        return maintenanceMargin;
    }

    public void setMaintenanceMargin(String maintenanceMargin) {
        this.maintenanceMargin = maintenanceMargin;
    }

    public String getLastMaintenanceMargin() {
        return lastMaintenanceMargin;
    }

    public void setLastMaintenanceMargin(String lastMaintenanceMargin) {
        this.lastMaintenanceMargin = lastMaintenanceMargin;
    }

    public String getLongMarketValue() {
        return longMarketValue;
    }

    public void setLongMarketValue(String longMarketValue) {
        this.longMarketValue = longMarketValue;
    }

    public String getShortMarketValue() {
        return shortMarketValue;
    }

    public void setShortMarketValue(String shortMarketValue) {
        this.shortMarketValue = shortMarketValue;
    }

    public boolean isPatternDayTrader() {
        return patternDayTrader;
    }

    public void setPatternDayTrader(boolean patternDayTrader) {
        this.patternDayTrader = patternDayTrader;
    }

    public boolean isTradingBlocked() {
        return tradingBlocked;
    }

    public void setTradingBlocked(boolean tradingBlocked) {
        this.tradingBlocked = tradingBlocked;
    }

    public boolean isTransfersBlocked() {
        return transfersBlocked;
    }

    public void setTransfersBlocked(boolean transfersBlocked) {
        this.transfersBlocked = transfersBlocked;
    }

    public boolean isAccountBlocked() {
        return accountBlocked;
    }

    public void setAccountBlocked(boolean accountBlocked) {
        this.accountBlocked = accountBlocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isTradeSuspendedByUser() {
        return tradeSuspendedByUser;
    }

    public void setTradeSuspendedByUser(boolean tradeSuspendedByUser) {
        this.tradeSuspendedByUser = tradeSuspendedByUser;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    public boolean isShortingEnabled() {
        return shortingEnabled;
    }

    public void setShortingEnabled(boolean shortingEnabled) {
        this.shortingEnabled = shortingEnabled;
    }

    public String getSma() {
        return sma;
    }

    public void setSma(String sma) {
        this.sma = sma;
    }

    public int getDaytradingCount() {
        return daytradingCount;
    }

    public void setDaytradingCount(int daytradingCount) {
        this.daytradingCount = daytradingCount;
    }
}
