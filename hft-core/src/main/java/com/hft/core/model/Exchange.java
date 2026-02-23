package com.hft.core.model;

/**
 * Supported exchanges.
 */
public enum Exchange {
    ALPACA("alpaca", "Alpaca", AssetClass.STOCK, 1),
    BINANCE("binance", "Binance", AssetClass.CRYPTO, 100_000_000),
    BINANCE_TESTNET("binance-testnet", "Binance Testnet", AssetClass.CRYPTO, 100_000_000);

    private final String id;
    private final String displayName;
    private final AssetClass assetClass;
    private final int quantityScale;

    Exchange(String id, String displayName, AssetClass assetClass, int quantityScale) {
        this.id = id;
        this.displayName = displayName;
        this.assetClass = assetClass;
        this.quantityScale = quantityScale;
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

    public int getQuantityScale() {
        return quantityScale;
    }

    public boolean isTestnet() {
        return this == BINANCE_TESTNET;
    }
}
