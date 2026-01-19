package com.hft.core.model;

/**
 * Supported exchanges.
 */
public enum Exchange {
    ALPACA("alpaca", "Alpaca", AssetClass.STOCK),
    BINANCE("binance", "Binance", AssetClass.CRYPTO),
    BINANCE_TESTNET("binance-testnet", "Binance Testnet", AssetClass.CRYPTO);

    private final String id;
    private final String displayName;
    private final AssetClass assetClass;

    Exchange(String id, String displayName, AssetClass assetClass) {
        this.id = id;
        this.displayName = displayName;
        this.assetClass = assetClass;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public boolean isTestnet() {
        return this == BINANCE_TESTNET;
    }
}
