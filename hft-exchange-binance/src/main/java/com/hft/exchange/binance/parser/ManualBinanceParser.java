package com.hft.exchange.binance.parser;

import com.hft.core.util.FastDecimalParser;

/**
 * Zero-library manual JSON scanner for Binance WebSocket messages.
 *
 * <p>This is the fastest parser strategy. It scans the raw JSON string character by
 * character using {@link String#indexOf(String)} to locate field markers, then extracts
 * values via {@link String#substring(int, int)}. No JSON library objects are created —
 * the only allocations are the small substrings for each field value.
 *
 * <p>All prices and quantities are converted to scaled longs using
 * {@link FastDecimalParser#parseDecimal(String, int, long)} with 8 decimal places.
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li>Combined stream: {@code {"stream":"btcusdt@bookTicker","data":{"s":"BTCUSDT",...}}}</li>
 *   <li>Direct: {@code {"s":"BTCUSDT","b":"67432.15000000",...}}</li>
 * </ul>
 */
public final class ManualBinanceParser implements BinanceMessageParser {

    private static final int DECIMAL_PLACES = 8;
    private static final long DEFAULT_VALUE = 0L;

    // bookTicker field markers (string-valued fields use "key":"value" format)
    private static final String MARKER_SYMBOL = "\"s\":\"";
    private static final String MARKER_BID_PRICE = "\"b\":\"";
    private static final String MARKER_ASK_PRICE = "\"a\":\"";
    private static final String MARKER_BID_SIZE = "\"B\":\"";
    private static final String MARKER_ASK_SIZE = "\"A\":\"";

    // Trade field markers
    private static final String MARKER_PRICE = "\"p\":\"";
    private static final String MARKER_QUANTITY = "\"q\":\"";
    private static final String MARKER_TRADE_TIME = "\"T\":";
    private static final String MARKER_TRADE_ID = "\"t\":";
    private static final String MARKER_IS_BUYER_MAKER = "\"m\":";

    // Combined stream wrapper marker
    private static final String DATA_MARKER = "\"data\":{";

    @Override
    public TickerFields parseTicker(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            int searchFrom = findDataStart(json);

            // Symbol needs a substring (we need the String value)
            String symbol = extractStringField(json, MARKER_SYMBOL, searchFrom);
            if (symbol == null) {
                return null;
            }

            // Price/size fields: use range-based parsing to avoid substring allocations
            long bidPrice = extractDecimalField(json, MARKER_BID_PRICE, searchFrom);
            long askPrice = extractDecimalField(json, MARKER_ASK_PRICE, searchFrom);
            long bidSize = extractDecimalField(json, MARKER_BID_SIZE, searchFrom);
            long askSize = extractDecimalField(json, MARKER_ASK_SIZE, searchFrom);

            return new TickerFields(symbol, bidPrice, askPrice, bidSize, askSize);
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
            int searchFrom = findDataStart(json);

            String symbol = extractStringField(json, MARKER_SYMBOL, searchFrom);
            if (symbol == null) {
                return null;
            }

            long price = extractDecimalField(json, MARKER_PRICE, searchFrom);
            long quantity = extractDecimalField(json, MARKER_QUANTITY, searchFrom);
            long tradeTimeMs = extractLongField(json, MARKER_TRADE_TIME, searchFrom);
            long tradeId = extractLongField(json, MARKER_TRADE_ID, searchFrom);
            boolean isBuyerMaker = extractBooleanField(json, MARKER_IS_BUYER_MAKER, searchFrom);

            return new TradeFields(symbol, price, quantity, tradeTimeMs, tradeId, isBuyerMaker);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "manual";
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Returns the index from which to start scanning for data fields.
     * If the JSON is a combined-stream message, this skips past the {@code "data":{} wrapper.
     * Otherwise returns 0 to scan from the beginning.
     */
    private static int findDataStart(String json) {
        int dataIdx = json.indexOf(DATA_MARKER);
        if (dataIdx >= 0) {
            // Position right after the opening brace of "data":{
            return dataIdx + DATA_MARKER.length();
        }
        return 0;
    }

    /**
     * Extracts a quoted decimal value and parses it directly without creating a substring.
     * Uses {@link FastDecimalParser#parseDecimal(String, int, int, int, long)} for zero-allocation parsing.
     *
     * @return the parsed decimal value, or {@code 0L} if the marker is not found
     */
    private static long extractDecimalField(String json, String marker, int fromIndex) {
        int markerIdx = json.indexOf(marker, fromIndex);
        if (markerIdx < 0) {
            return DEFAULT_VALUE;
        }
        int valueStart = markerIdx + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return DEFAULT_VALUE;
        }
        return FastDecimalParser.parseDecimal(json, valueStart, valueEnd, DECIMAL_PLACES, DEFAULT_VALUE);
    }

    /**
     * Extracts a quoted string value for the given field marker.
     *
     * <p>Searches for the marker (e.g., {@code "s":"}) starting from {@code fromIndex},
     * then reads characters until the closing quote.
     *
     * @return the field value, or {@code null} if the marker is not found
     */
    private static String extractStringField(String json, String marker, int fromIndex) {
        int markerIdx = json.indexOf(marker, fromIndex);
        if (markerIdx < 0) {
            return null;
        }
        int valueStart = markerIdx + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return json.substring(valueStart, valueEnd);
    }

    /**
     * Extracts an unquoted numeric (long) value for the given field marker.
     *
     * <p>Searches for the marker (e.g., {@code "T":}) then reads digits until a
     * non-digit character (comma, brace, etc.) is encountered.
     *
     * @return the parsed long value, or {@code 0L} if the marker is not found
     */
    private static long extractLongField(String json, String marker, int fromIndex) {
        int markerIdx = json.indexOf(marker, fromIndex);
        if (markerIdx < 0) {
            return 0L;
        }
        int valueStart = markerIdx + marker.length();
        long result = 0L;
        boolean negative = false;
        int pos = valueStart;

        // Skip whitespace
        while (pos < json.length() && json.charAt(pos) == ' ') {
            pos++;
        }

        if (pos < json.length() && json.charAt(pos) == '-') {
            negative = true;
            pos++;
        }

        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c < '0' || c > '9') {
                break;
            }
            result = result * 10 + (c - '0');
            pos++;
        }

        return negative ? -result : result;
    }

    /**
     * Extracts an unquoted boolean value for the given field marker.
     *
     * <p>Searches for the marker (e.g., {@code "m":}) then checks if the next
     * non-whitespace character is {@code 't'} (true) or {@code 'f'} (false).
     *
     * @return the parsed boolean value, or {@code false} if the marker is not found
     */
    private static boolean extractBooleanField(String json, String marker, int fromIndex) {
        int markerIdx = json.indexOf(marker, fromIndex);
        if (markerIdx < 0) {
            return false;
        }
        int valueStart = markerIdx + marker.length();

        // Skip whitespace
        int pos = valueStart;
        while (pos < json.length() && json.charAt(pos) == ' ') {
            pos++;
        }

        return pos < json.length() && json.charAt(pos) == 't';
    }
}
