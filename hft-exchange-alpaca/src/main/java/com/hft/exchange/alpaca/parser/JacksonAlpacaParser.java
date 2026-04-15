package com.hft.exchange.alpaca.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.core.util.FastDecimalParser;

/**
 * Alpaca message parser using Jackson's tree model ({@link ObjectMapper#readTree(String)}).
 *
 * <p>This is the baseline parser — most compatible and easiest to maintain, but
 * slower than the manual and jsoniter alternatives because it builds a full
 * {@link JsonNode} tree for every message.
 *
 * <h2>Price Conversion</h2>
 * <p>All prices are converted to cents (2 decimal places) using
 * {@link FastDecimalParser#parseDecimal(String, int, long)}. Integer fields
 * are extracted via {@link JsonNode#asLong()}.
 *
 * <h2>Thread Safety</h2>
 * <p>This parser is thread-safe. The internal {@link ObjectMapper} is immutable
 * after construction and safe for concurrent use.
 */
public final class JacksonAlpacaParser implements AlpacaMessageParser {

    /** Alpaca price scale: 2 decimal places (cents). */
    private static final int PRICE_SCALE = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public QuoteFields parseQuote(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);

            String symbol = node.path("S").asText();
            long bidPrice = FastDecimalParser.parseDecimal(node.path("bp").asText(), PRICE_SCALE, 0);
            long askPrice = FastDecimalParser.parseDecimal(node.path("ap").asText(), PRICE_SCALE, 0);
            long bidSize = node.path("bs").asLong();
            long askSize = node.path("as").asLong();
            String timestamp = node.path("t").asText();

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
            JsonNode node = objectMapper.readTree(json);

            String symbol = node.path("S").asText();
            long price = FastDecimalParser.parseDecimal(node.path("p").asText(), PRICE_SCALE, 0);
            long quantity = node.path("s").asLong();
            String timestamp = node.path("t").asText();
            long tradeId = node.path("i").asLong();

            return new TradeFields(symbol, price, quantity, timestamp, tradeId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "jackson";
    }
}
