package com.hft.exchange.alpaca.parser;

import com.hft.core.util.FastDecimalParser;
import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

/**
 * Alpaca message parser using <a href="https://jsoniter.com/">jsoniter</a> (json-iterator).
 *
 * <p>jsoniter provides lazy parsing — fields are deserialized on demand rather than
 * building a full DOM tree up front. This makes it faster than Jackson's tree model
 * while remaining more readable and maintainable than manual string scanning.
 *
 * <h2>Price Conversion</h2>
 * <p>All prices are converted to cents (2 decimal places) using
 * {@link FastDecimalParser#parseDecimal(String, int, long)}. Integer fields
 * (bid size, ask size, trade quantity, trade id) are extracted directly via
 * {@link Any#toLong()}.
 *
 * <h2>Thread Safety</h2>
 * <p>This parser is stateless and thread-safe. Each call to
 * {@link JsonIterator#deserialize(String)} creates its own iterator instance.
 */
public final class JsoniterAlpacaParser implements AlpacaMessageParser {

    /** Alpaca price scale: 2 decimal places (cents). */
    private static final int PRICE_SCALE = 2;

    @Override
    public QuoteFields parseQuote(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            Any any = JsonIterator.deserialize(json);

            String symbol = any.get("S").toString();
            long bidPrice = FastDecimalParser.parseDecimal(any.get("bp").toString(), PRICE_SCALE, 0);
            long askPrice = FastDecimalParser.parseDecimal(any.get("ap").toString(), PRICE_SCALE, 0);
            long bidSize = any.get("bs").toLong();
            long askSize = any.get("as").toLong();
            String timestamp = any.get("t").toString();

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
            Any any = JsonIterator.deserialize(json);

            String symbol = any.get("S").toString();
            long price = FastDecimalParser.parseDecimal(any.get("p").toString(), PRICE_SCALE, 0);
            long quantity = any.get("s").toLong();
            String timestamp = any.get("t").toString();
            long tradeId = any.get("i").toLong();

            return new TradeFields(symbol, price, quantity, timestamp, tradeId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "jsoniter";
    }
}
