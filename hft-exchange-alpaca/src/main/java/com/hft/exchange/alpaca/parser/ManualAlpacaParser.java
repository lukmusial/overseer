package com.hft.exchange.alpaca.parser;

import com.hft.core.util.FastDecimalParser;

/**
 * Zero-library manual byte scanner for Alpaca WebSocket messages.
 *
 * <p>Extracts fields by scanning for known JSON key patterns using
 * {@link String#indexOf(String)}. No JSON library objects are created —
 * the only allocations are the extracted field strings and the result record.
 *
 * <p>This is the fastest parser strategy, suitable for the hot path in
 * latency-sensitive market data processing.
 *
 * <h2>Price Conversion</h2>
 * <p>All prices are converted to cents (2 decimal places) using
 * {@link FastDecimalParser#parseDecimal(String, int, long)}.
 * Quantities are parsed as raw integers with no decimal scaling.
 *
 * <h2>Assumptions</h2>
 * <ul>
 *   <li>Input is a single JSON object (not an array wrapper)</li>
 *   <li>Field keys use double quotes and standard JSON encoding</li>
 *   <li>String values are double-quoted; numeric values are unquoted</li>
 * </ul>
 */
public final class ManualAlpacaParser implements AlpacaMessageParser {

    /** Alpaca price scale: 2 decimal places (cents). */
    private static final int PRICE_SCALE = 2;

    @Override
    public QuoteFields parseQuote(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            String symbol = extractQuotedValue(json, "\"S\":\"", 0);
            long bidSize = extractUnquotedLong(json, "\"bs\":", 0);
            long askSize = extractUnquotedLong(json, "\"as\":", 0);
            String timestamp = extractQuotedValue(json, "\"t\":\"", 0);

            if (symbol == null || timestamp == null) {
                return null;
            }

            // Use range-based parsing to avoid substring allocations for price fields
            long bidPrice = extractDecimalField(json, "\"bp\":\"", 0);
            long askPrice = extractDecimalField(json, "\"ap\":\"", 0);

            return new QuoteFields(symbol, bidPrice, askPrice, bidSize, askSize, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TradeFields parseTrade(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            String symbol = extractQuotedValue(json, "\"S\":\"", 0);
            String timestamp = extractQuotedValue(json, "\"t\":\"", 0);

            if (symbol == null || timestamp == null) {
                return null;
            }

            long price = extractDecimalField(json, "\"p\":\"", 0);

            // For "s" (size) and "i" (trade id), we must be careful not to match
            // "S" (symbol). We search for the lowercase versions after the symbol field.
            int afterSymbol = json.indexOf("\"S\":\"") + 5;
            long quantity = extractUnquotedLong(json, "\"s\":", afterSymbol);
            long tradeId = extractUnquotedLong(json, "\"i\":", 0);

            return new TradeFields(symbol, price, quantity, timestamp, tradeId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "manual";
    }

    /**
     * Extracts a quoted decimal value and parses it directly without creating a substring.
     */
    private static long extractDecimalField(String json, String key, int fromIndex) {
        int start = json.indexOf(key, fromIndex);
        if (start < 0) {
            return 0L;
        }
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return 0L;
        }
        return FastDecimalParser.parseDecimal(json, start, end, PRICE_SCALE, 0);
    }

    /**
     * Extracts a quoted string value following the given key marker.
     *
     * <p>For example, given key {@code "S":"} in the JSON
     * {@code {"T":"q","S":"AAPL","bp":"150.50"}}, this returns {@code "AAPL"}.
     *
     * @param json     the JSON string to scan
     * @param key      the key marker including the opening quote of the value (e.g., {@code "S":"})
     * @param fromIndex position to start scanning from
     * @return the extracted value, or {@code null} if the key is not found
     */
    private static String extractQuotedValue(String json, String key, int fromIndex) {
        int start = json.indexOf(key, fromIndex);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    /**
     * Extracts an unquoted numeric (long) value following the given key marker.
     *
     * <p>Scans digits (and an optional leading minus sign) immediately after the key.
     * Stops at the first non-digit character (comma, brace, bracket, etc.).
     *
     * <p>For example, given key {@code "bs":} in the JSON
     * {@code {"bs":1000,"as":500}}, this returns {@code 1000L}.
     *
     * @param json     the JSON string to scan
     * @param key      the key marker (e.g., {@code "bs":})
     * @param fromIndex position to start scanning from
     * @return the parsed long value, or {@code 0L} if the key is not found
     */
    private static long extractUnquotedLong(String json, String key, int fromIndex) {
        int start = json.indexOf(key, fromIndex);
        if (start < 0) {
            return 0L;
        }
        start += key.length();

        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') {
            start++;
        }

        if (start >= json.length()) {
            return 0L;
        }

        boolean negative = false;
        if (json.charAt(start) == '-') {
            negative = true;
            start++;
        }

        long value = 0;
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
            start++;
        }

        return negative ? -value : value;
    }
}
