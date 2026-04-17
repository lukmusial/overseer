package com.hft.exchange.alpaca.parser;

/**
 * Strategy for parsing raw Alpaca WebSocket messages.
 *
 * <p>Implementations extract fields from JSON strings without requiring the caller
 * to create intermediate DOM objects (e.g., Jackson {@code JsonNode} trees). Each
 * strategy offers a different trade-off between speed and maintainability:
 * <ul>
 *   <li>{@link ManualAlpacaParser} — zero-library manual scanning (fastest)</li>
 *   <li>{@link JsoniterAlpacaParser} — json-iterator lazy parsing (fast, readable)</li>
 *   <li>{@link JacksonAlpacaParser} — Jackson tree model (baseline, most compatible)</li>
 * </ul>
 *
 * <p>Alpaca WebSocket messages arrive as JSON arrays (e.g.,
 * {@code [{"T":"q","S":"AAPL","bp":"150.50",...}]}) or single objects for control
 * messages ({@code {"T":"success","msg":"connected"}}). Parsers expect the inner
 * object — callers must strip the array wrapper before invoking parse methods.
 */
public interface AlpacaMessageParser {

    /**
     * Parsed fields from an Alpaca real-time quote message.
     *
     * <p>Prices are scaled longs with 2 decimal places (cents).
     * For example, {@code "150.50"} becomes {@code 15050L}.
     * Sizes are raw integer quantities (no decimal scaling).
     *
     * @param symbol    the ticker symbol (e.g., "AAPL")
     * @param bidPrice  best bid price in cents (scaled by 10^2)
     * @param askPrice  best ask price in cents (scaled by 10^2)
     * @param bidSize   bid quantity at best bid (integer shares)
     * @param askSize   ask quantity at best ask (integer shares)
     * @param timestamp ISO-8601 timestamp string from the exchange
     */
    record QuoteFields(String symbol, long bidPrice, long askPrice, long bidSize, long askSize, String timestamp) {}

    /**
     * Parsed fields from an Alpaca real-time trade message.
     *
     * <p>Price is a scaled long with 2 decimal places (cents).
     * Quantity is a raw integer (no decimal scaling).
     *
     * @param symbol    the ticker symbol (e.g., "AAPL")
     * @param price     trade price in cents (scaled by 10^2)
     * @param quantity  trade quantity (integer shares)
     * @param timestamp ISO-8601 timestamp string from the exchange
     * @param tradeId   unique trade identifier
     */
    record TradeFields(String symbol, long price, long quantity, String timestamp, long tradeId) {}

    /**
     * Parses a quote message (the inner JSON object, not the array wrapper).
     *
     * <p>Expected JSON format:
     * <pre>{@code
     * {"T":"q","S":"AAPL","bp":"150.50","ap":"150.51","bs":1000,"as":500,"t":"2024-01-15T12:34:56.789Z"}
     * }</pre>
     *
     * @param json the raw JSON string for a single quote object
     * @return parsed quote fields, or {@code null} if parsing fails
     */
    QuoteFields parseQuote(String json);

    /**
     * Parses a trade message (the inner JSON object, not the array wrapper).
     *
     * <p>Expected JSON format:
     * <pre>{@code
     * {"T":"t","S":"AAPL","p":"150.50","s":100,"t":"2024-01-15T12:34:56.789Z","i":123456}
     * }</pre>
     *
     * @param json the raw JSON string for a single trade object
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
