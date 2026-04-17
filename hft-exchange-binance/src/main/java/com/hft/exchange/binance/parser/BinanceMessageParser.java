package com.hft.exchange.binance.parser;

/**
 * Strategy for parsing raw Binance WebSocket messages.
 *
 * <p>Implementations extract fields from JSON strings without requiring the caller
 * to create intermediate DOM objects (e.g., Jackson {@code JsonNode} trees). Each
 * strategy offers a different trade-off between speed and maintainability:
 * <ul>
 *   <li>{@link ManualBinanceParser} — zero-library manual scanning (fastest)</li>
 *   <li>{@link JsoniterBinanceParser} — json-iterator lazy parsing (fast, readable)</li>
 *   <li>{@link JacksonBinanceParser} — Jackson tree model (baseline, most compatible)</li>
 * </ul>
 *
 * <p>All implementations handle both Binance combined-stream format
 * ({@code {"stream":"...","data":{...}}}) and direct format ({@code {"s":"BTCUSDT",...}}).
 */
public interface BinanceMessageParser {

    /**
     * Parsed fields from a Binance bookTicker message.
     *
     * <p>All prices and sizes are scaled longs with 8 decimal places
     * (e.g., {@code "67432.15000000"} becomes {@code 6743215000000L}).
     *
     * @param symbol   the trading pair (e.g., "BTCUSDT")
     * @param bidPrice best bid price, scaled by 10^8
     * @param askPrice best ask price, scaled by 10^8
     * @param bidSize  bid quantity at best bid, scaled by 10^8
     * @param askSize  ask quantity at best ask, scaled by 10^8
     */
    record TickerFields(String symbol, long bidPrice, long askPrice, long bidSize, long askSize) {}

    /**
     * Parsed fields from a Binance trade message.
     *
     * <p>Price and quantity are scaled longs with 8 decimal places.
     *
     * @param symbol       the trading pair (e.g., "BTCUSDT")
     * @param price        trade price, scaled by 10^8
     * @param quantity     trade quantity, scaled by 10^8
     * @param tradeTimeMs  trade timestamp in epoch milliseconds
     * @param tradeId      unique trade identifier
     * @param isBuyerMaker true if the buyer was the maker (i.e., a sell aggressor)
     */
    record TradeFields(String symbol, long price, long quantity, long tradeTimeMs, long tradeId, boolean isBuyerMaker) {}

    /**
     * Parses a bookTicker message.
     *
     * <p>The input may be either the full combined-stream wrapper
     * ({@code {"stream":"...","data":{...}}}) or just the inner data object.
     * Implementations must handle both formats transparently.
     *
     * @param json the raw JSON string
     * @return parsed ticker fields, or {@code null} if parsing fails
     */
    TickerFields parseTicker(String json);

    /**
     * Parses a trade message.
     *
     * <p>The input may be either the full combined-stream wrapper or just the
     * inner data object. Implementations must handle both formats transparently.
     *
     * @param json the raw JSON string
     * @return parsed trade fields, or {@code null} if parsing fails
     */
    TradeFields parseTrade(String json);

    /**
     * Returns the parser strategy name, suitable for logging and metrics.
     *
     * @return a short, stable identifier (e.g., "manual", "jsoniter", "jackson")
     */
    String name();
}
