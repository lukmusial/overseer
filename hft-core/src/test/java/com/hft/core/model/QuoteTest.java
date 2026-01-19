package com.hft.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuoteTest {

    @Test
    void shouldCreateQuote() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Quote quote = new Quote(symbol, 14995, 15005, 100, 200, System.nanoTime());

        assertEquals(symbol, quote.getSymbol());
        assertEquals(14995, quote.getBidPrice());
        assertEquals(15005, quote.getAskPrice());
        assertEquals(100, quote.getBidSize());
        assertEquals(200, quote.getAskSize());
    }

    @Test
    void shouldCalculateMidPrice() {
        Quote quote = new Quote(null, 14990, 15010, 100, 100, 0);

        assertEquals(15000, quote.getMidPrice());
    }

    @Test
    void shouldCalculateSpread() {
        Quote quote = new Quote(null, 14990, 15010, 100, 100, 0);

        assertEquals(20, quote.getSpread());
    }

    @Test
    void shouldConvertPriceToDouble() {
        Quote quote = new Quote(null, 14995, 15005, 100, 100, 0);

        assertEquals(149.95, quote.getBidPriceAsDouble(), 0.001);
        assertEquals(150.05, quote.getAskPriceAsDouble(), 0.001);
    }

    @Test
    void shouldResetForReuse() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Quote quote = new Quote(symbol, 14995, 15005, 100, 200, 12345);

        quote.reset();

        assertNull(quote.getSymbol());
        assertEquals(0, quote.getBidPrice());
        assertEquals(0, quote.getAskPrice());
        assertEquals(0, quote.getBidSize());
        assertEquals(0, quote.getAskSize());
        assertEquals(0, quote.getTimestamp());
    }

    @Test
    void shouldCopyFromAnotherQuote() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Quote source = new Quote(symbol, 14995, 15005, 100, 200, 12345);
        source.setSequenceNumber(42);

        Quote target = new Quote();
        target.copyFrom(source);

        assertEquals(source.getSymbol(), target.getSymbol());
        assertEquals(source.getBidPrice(), target.getBidPrice());
        assertEquals(source.getAskPrice(), target.getAskPrice());
        assertEquals(source.getBidSize(), target.getBidSize());
        assertEquals(source.getAskSize(), target.getAskSize());
        assertEquals(source.getTimestamp(), target.getTimestamp());
        assertEquals(source.getSequenceNumber(), target.getSequenceNumber());
    }

    @Test
    void shouldHandleCustomPriceScale() {
        Quote quote = new Quote();
        quote.setPriceScale(10000); // 4 decimal places
        quote.setBidPrice(150012345);

        assertEquals(15001.2345, quote.getBidPriceAsDouble(), 0.0001);
    }
}
