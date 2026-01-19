package com.hft.persistence.impl;

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

import static org.junit.jupiter.api.Assertions.*;

class FileTradeJournalTest {

    @TempDir
    Path tempDir;

    private FileTradeJournal journal;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        journal = new FileTradeJournal(tempDir);
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
        // Most recent first
        assertEquals(200, recent.get(0).getQuantity());
    }

    @Test
    void shouldPersistTradesToFile() {
        Trade trade = createTrade(100, 15000);
        trade.setExchangeTradeId("TRADE-001");
        trade.setClientOrderId(12345L);

        journal.record(trade);
        journal.flush();

        // Read back with a new journal instance
        FileTradeJournal journal2 = new FileTradeJournal(tempDir);
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
    void shouldHandleCSVSpecialCharacters() {
        Trade trade = createTrade(100, 15000);
        trade.setExchangeTradeId("TRADE,WITH,COMMAS");

        journal.record(trade);
        journal.flush();

        FileTradeJournal journal2 = new FileTradeJournal(tempDir);
        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        List<Trade> trades = journal2.getTradesForDate(today);

        assertEquals(1, trades.size());
        assertEquals("TRADE,WITH,COMMAS", trades.get(0).getExchangeTradeId());

        journal2.close();
    }

    @Test
    void shouldReturnEmptyListForNonExistentDate() {
        List<Trade> trades = journal.getTradesForDate(19000101);

        assertTrue(trades.isEmpty());
    }

    private Trade createTrade(long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
