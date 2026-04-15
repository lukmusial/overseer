package com.hft.exchange.binance.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.core.util.FastDecimalParser;

/**
 * Binance message parser using Jackson's tree model ({@link ObjectMapper#readTree(String)}).
 *
 * <p>This is the baseline/fallback parser strategy. It builds a full {@link JsonNode} tree
 * for each message, which is the most allocation-heavy approach but also the most robust
 * and widely understood. Use this when correctness and debuggability matter more than
 * raw throughput.
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
public final class JacksonBinanceParser implements BinanceMessageParser {

    private static final int DECIMAL_PLACES = 8;
    private static final long DEFAULT_VALUE = 0L;

    private final ObjectMapper objectMapper;

    /**
     * Creates a parser with the given {@link ObjectMapper}.
     *
     * @param objectMapper the Jackson object mapper (should be reused / shared)
     */
    public JacksonBinanceParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a parser with a default {@link ObjectMapper}.
     */
    public JacksonBinanceParser() {
        this(new ObjectMapper());
    }

    @Override
    public TickerFields parseTicker(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = unwrapData(root);

            String symbol = data.path("s").asText(null);
            if (symbol == null) {
                return null;
            }

            long bidPrice = FastDecimalParser.parseDecimal(data.path("b").asText(), DECIMAL_PLACES, DEFAULT_VALUE);
            long askPrice = FastDecimalParser.parseDecimal(data.path("a").asText(), DECIMAL_PLACES, DEFAULT_VALUE);
            long bidSize = FastDecimalParser.parseDecimal(data.path("B").asText(), DECIMAL_PLACES, DEFAULT_VALUE);
            long askSize = FastDecimalParser.parseDecimal(data.path("A").asText(), DECIMAL_PLACES, DEFAULT_VALUE);

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
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = unwrapData(root);

            String symbol = data.path("s").asText(null);
            if (symbol == null) {
                return null;
            }

            long price = FastDecimalParser.parseDecimal(data.path("p").asText(), DECIMAL_PLACES, DEFAULT_VALUE);
            long quantity = FastDecimalParser.parseDecimal(data.path("q").asText(), DECIMAL_PLACES, DEFAULT_VALUE);
            long tradeTimeMs = data.path("T").asLong(0L);
            long tradeId = data.path("t").asLong(0L);
            boolean isBuyerMaker = data.path("m").asBoolean(false);

            return new TradeFields(symbol, price, quantity, tradeTimeMs, tradeId, isBuyerMaker);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "jackson";
    }

    /**
     * Unwraps the combined-stream format if present.
     *
     * <p>If the root node contains a {@code "data"} object field, returns that inner
     * object. Otherwise returns the root node as-is (direct format).
     */
    private static JsonNode unwrapData(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isMissingNode() && data.isObject()) {
            return data;
        }
        return root;
    }
}
