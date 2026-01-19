package com.hft.persistence.chronicle;

import com.hft.core.model.Exchange;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleTradeJournalTest {

    @TempDir
    Path tempDir;

    private ChronicleTradeJournal journal;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        journal = new ChronicleTradeJournal(tempDir);
        symbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @AfterEach
    void tearDown() {
        journal.close();
    }

    @Test
    void shouldRecordTrade() {
        Trade trade = createTrade(100, 15000);

        journal.record(trade);

        assertEquals(1, journal.getTotalTradeCount());
    }

    @Test
    void shouldGetRecentTrades() {
        Trade trade1 = createTrade(100, 15000);
        Trade trade2 = createTrade(200, 15100);

        journal.record(trade1);
        journal.record(trade2);

        List<Trade> recent = journal.getRecentTrades(10);

        assertEquals(2, recent.size());
        assertEquals(200, recent.get(0).getQuantity()); // Most recent first
    }

    @Test
    void shouldPersistTradesToChronicle() {
        Trade trade = createTrade(100, 15000);
        trade.setExchangeTradeId("TRADE-001");
        trade.setClientOrderId(12345L);

        journal.record(trade);
        journal.close();

        // Read back with a new journal instance
        ChronicleTradeJournal journal2 = new ChronicleTradeJournal(tempDir);

        assertEquals(1, journal2.getTotalTradeCount());

        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        List<Trade> trades = journal2.getTradesForDate(today);

        assertEquals(1, trades.size());
        Trade loaded = trades.get(0);
        assertEquals(symbol, loaded.getSymbol());
        assertEquals(OrderSide.BUY, loaded.getSide());
        assertEquals(100, loaded.getQuantity());
        assertEquals(15000, loaded.getPrice());
        assertEquals("TRADE-001", loaded.getExchangeTradeId());
        assertEquals(12345L, loaded.getClientOrderId());

        journal2.close();
    }

    @Test
    void shouldReplayAllTrades() {
        journal.record(createTrade(100, 15000));
        journal.record(createTrade(200, 15100));
        journal.record(createTrade(300, 15200));

        AtomicInteger count = new AtomicInteger();
        journal.replay(trade -> count.incrementAndGet());

        assertEquals(3, count.get());
    }

    @Test
    void shouldHandleMultipleRecords() {
        for (int i = 0; i < 100; i++) {
            journal.record(createTrade(i + 1, 15000 + i));
        }

        assertEquals(100, journal.getTotalTradeCount());
        assertEquals(100, journal.getRecentTrades(200).size());
    }

    private Trade createTrade(long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        // Use epoch-based nanoseconds for correct date filtering
        trade.setExecutedAt(System.currentTimeMillis() * 1_000_000);
        return trade;
    }
}
