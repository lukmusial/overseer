package com.hft.exchange.alpaca.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hft.core.util.FastDecimalParser;

/**
 * Alpaca message parser using Jackson's streaming {@link JsonParser} API.
 *
 * <p>Pull-parser approach that reads tokens sequentially without building a tree.
 * Eliminates the JsonNode allocations of {@code readTree()} while remaining more
 * robust than manual string scanning.
 *
 * <h2>Price Conversion</h2>
 * <p>Prices are converted to cents (2 decimal places) using
 * {@link FastDecimalParser#parseDecimal(String, int, long)}. Integer fields
 * (bid size, ask size, trade quantity, trade id) are read directly via
 * {@link JsonParser#getLongValue()}.
 */
public final class StreamingAlpacaParser implements AlpacaMessageParser {

    private static final int PRICE_SCALE = 2;

    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public QuoteFields parseQuote(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try (JsonParser parser = jsonFactory.createParser(json)) {
            String symbol = null, timestamp = null;
            long bidPrice = 0, askPrice = 0, bidSize = 0, askSize = 0;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();
                    parser.nextToken();

                    switch (field) {
                        case "S" -> symbol = parser.getText();
                        case "bp" -> bidPrice = FastDecimalParser.parseDecimal(parser.getText(), PRICE_SCALE, 0);
                        case "ap" -> askPrice = FastDecimalParser.parseDecimal(parser.getText(), PRICE_SCALE, 0);
                        case "bs" -> bidSize = parser.getLongValue();
                        case "as" -> askSize = parser.getLongValue();
                        case "t" -> timestamp = parser.getText();
                    }
                }
            }

            if (symbol == null) {
                return null;
            }
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
        try (JsonParser parser = jsonFactory.createParser(json)) {
            String symbol = null, timestamp = null;
            long price = 0, quantity = 0, tradeId = 0;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();
                    parser.nextToken();

                    switch (field) {
                        case "S" -> symbol = parser.getText();
                        case "p" -> price = FastDecimalParser.parseDecimal(parser.getText(), PRICE_SCALE, 0);
                        case "s" -> quantity = parser.getLongValue();
                        case "t" -> timestamp = parser.getText();
                        case "i" -> tradeId = parser.getLongValue();
                    }
                }
            }

            if (symbol == null) {
                return null;
            }
            return new TradeFields(symbol, price, quantity, timestamp, tradeId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "streaming";
    }
}
