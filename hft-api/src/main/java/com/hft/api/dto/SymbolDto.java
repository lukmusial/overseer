package com.hft.api.dto;

/**
 * DTO for tradable symbol information.
 */
public record SymbolDto(
        String symbol,
        String name,
        String exchange,
        String assetClass,
        String baseAsset,
        String quoteAsset,
        boolean tradable,
        boolean marginable,
        boolean shortable
) {
    /**
     * Creates a SymbolDto for an equity symbol.
     */
    public static SymbolDto equity(String symbol, String name, String exchange,
                                    boolean tradable, boolean marginable, boolean shortable) {
        return new SymbolDto(symbol, name, exchange, "equity", symbol, "USD", tradable, marginable, shortable);
    }

    /**
     * Creates a SymbolDto for a crypto symbol.
     */
    public static SymbolDto crypto(String symbol, String name, String exchange,
                                   String baseAsset, String quoteAsset, boolean tradable) {
        return new SymbolDto(symbol, name, exchange, "crypto", baseAsset, quoteAsset, tradable, false, false);
    }
}
