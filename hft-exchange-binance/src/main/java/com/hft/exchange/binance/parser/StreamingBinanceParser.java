package com.hft.exchange.binance.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hft.core.util.FastDecimalParser;

/**
 * Binance message parser using Jackson's streaming {@link JsonParser} API.
 *
 * <p>This parser uses a pull-parser approach — it reads tokens sequentially without
 * building an in-memory tree. This eliminates the {@code JsonNode} / {@code ObjectNode} /
 * {@code TextNode} allocations that Jackson's {@code readTree()} creates (typically 8–15
 * objects per message). The only allocations are the {@link JsonParser} itself (reusable
 * via {@link JsonFactory}) and the extracted string values.
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
public final class StreamingBinanceParser implements BinanceMessageParser {

    private static final int DECIMAL_PLACES = 8;
    private static final long DEFAULT_VALUE = 0L;

    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public TickerFields parseTicker(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try (JsonParser parser = jsonFactory.createParser(json)) {
            String symbol = null;
            long bidPrice = 0, askPrice = 0, bidSize = 0, askSize = 0;
            boolean inData = false;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();
                    parser.nextToken(); // move to value

                    // If we see "data", descend into it (combined stream format)
                    if ("data".equals(field) && parser.currentToken() == JsonToken.START_OBJECT) {
                        inData = true;
                        continue;
                    }

                    // Skip "stream" field value
                    if ("stream".equals(field)) {
                        continue;
                    }

                    switch (field) {
                        case "s" -> symbol = parser.getText();
                        case "b" -> bidPrice = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                        case "a" -> askPrice = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                        case "B" -> bidSize = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                        case "A" -> askSize = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                    }
                }
            }

            if (symbol == null) {
                return null;
            }
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
        try (JsonParser parser = jsonFactory.createParser(json)) {
            String symbol = null;
            long price = 0, quantity = 0, tradeTimeMs = 0, tradeId = 0;
            boolean isBuyerMaker = false;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();
                    parser.nextToken();

                    if ("data".equals(field) && parser.currentToken() == JsonToken.START_OBJECT) {
                        continue;
                    }
                    if ("stream".equals(field)) {
                        continue;
                    }

                    switch (field) {
                        case "s" -> symbol = parser.getText();
                        case "p" -> price = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                        case "q" -> quantity = FastDecimalParser.parseDecimal(parser.getText(), DECIMAL_PLACES, DEFAULT_VALUE);
                        case "T" -> tradeTimeMs = parser.getLongValue();
                        case "t" -> tradeId = parser.getLongValue();
                        case "m" -> isBuyerMaker = parser.getBooleanValue();
                    }
                }
            }

            if (symbol == null) {
                return null;
            }
            return new TradeFields(symbol, price, quantity, tradeTimeMs, tradeId, isBuyerMaker);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "streaming";
    }
}
