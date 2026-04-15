package com.hft.exchange.binance.parser;

import com.hft.core.util.FastDecimalParser;
import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

/**
 * Binance message parser using <a href="https://jsoniter.com/">jsoniter</a> (json-iterator).
 *
 * <p>jsoniter's {@link Any} API provides lazy evaluation — field values are only materialized
 * when accessed, making it significantly faster than Jackson's eager tree model for messages
 * where only a subset of fields is needed. Unlike a full DOM parse, unused fields are never
 * converted from raw bytes.
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
public final class JsoniterBinanceParser implements BinanceMessageParser {

    private static final int DECIMAL_PLACES = 8;
    private static final long DEFAULT_VALUE = 0L;

    @Override
    public TickerFields parseTicker(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            Any any = JsonIterator.deserialize(json);
            Any data = unwrapData(any);

            String symbol = data.get("s").toString();
            long bidPrice = FastDecimalParser.parseDecimal(data.get("b").toString(), DECIMAL_PLACES, DEFAULT_VALUE);
            long askPrice = FastDecimalParser.parseDecimal(data.get("a").toString(), DECIMAL_PLACES, DEFAULT_VALUE);
            long bidSize = FastDecimalParser.parseDecimal(data.get("B").toString(), DECIMAL_PLACES, DEFAULT_VALUE);
            long askSize = FastDecimalParser.parseDecimal(data.get("A").toString(), DECIMAL_PLACES, DEFAULT_VALUE);

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
            Any any = JsonIterator.deserialize(json);
            Any data = unwrapData(any);

            String symbol = data.get("s").toString();
            long price = FastDecimalParser.parseDecimal(data.get("p").toString(), DECIMAL_PLACES, DEFAULT_VALUE);
            long quantity = FastDecimalParser.parseDecimal(data.get("q").toString(), DECIMAL_PLACES, DEFAULT_VALUE);
            long tradeTimeMs = data.get("T").toLong();
            long tradeId = data.get("t").toLong();
            boolean isBuyerMaker = data.get("m").toBoolean();

            return new TradeFields(symbol, price, quantity, tradeTimeMs, tradeId, isBuyerMaker);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "jsoniter";
    }

    /**
     * Unwraps the combined-stream format if present.
     *
     * <p>If the message contains a {@code "data"} field (combined stream format),
     * returns the inner data object. Otherwise returns the message as-is.
     */
    private static Any unwrapData(Any any) {
        Any data = any.get("data");
        // Any.get() returns a non-null Any even for missing keys, but its size() is 0
        // and toString() returns empty/null. Check if the data field actually exists.
        if (data.size() > 0 || data.valueType() == com.jsoniter.ValueType.OBJECT) {
            return data;
        }
        return any;
    }
}
