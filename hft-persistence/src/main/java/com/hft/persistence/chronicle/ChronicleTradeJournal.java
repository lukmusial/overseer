package com.hft.persistence.chronicle;

import com.hft.core.model.Trade;
import com.hft.persistence.TradeJournal;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chronicle Queue based trade journal for ultra-low latency persistence.
 *
 * Features:
 * - Zero-garbage collection writes
 * - Memory-mapped file persistence
 * - Automatic daily rolling
 * - Microsecond latency writes
 */
public class ChronicleTradeJournal implements TradeJournal {
    private static final Logger log = LoggerFactory.getLogger(ChronicleTradeJournal.class);

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;
    private final AtomicLong tradeCount = new AtomicLong();
    private final Deque<Trade> recentTrades = new ConcurrentLinkedDeque<>();
    private final int maxRecentTrades;
    private final Path basePath;

    public ChronicleTradeJournal(Path basePath) {
        this(basePath, 1000);
    }

    public ChronicleTradeJournal(Path basePath, int maxRecentTrades) {
        this.basePath = basePath;
        this.maxRecentTrades = maxRecentTrades;

        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("trades"))
                .build();
        this.appender = queue.createAppender();

        // Count existing trades
        try (ExcerptTailer tailer = queue.createTailer()) {
            while (tailer.readDocument(w -> {})) {
                tradeCount.incrementAndGet();
            }
        }

        log.info("Chronicle trade journal initialized at {} with {} existing trades",
                basePath, tradeCount.get());
    }

    @Override
    public void record(Trade trade) {
        TradeWire wire = TradeWire.from(trade);

        appender.writeDocument(w -> w.write("trade").marshallable(wire));

        tradeCount.incrementAndGet();

        // Keep recent trades in memory for quick access
        recentTrades.addFirst(trade);
        while (recentTrades.size() > maxRecentTrades) {
            recentTrades.removeLast();
        }
    }

    @Override
    public List<Trade> getTradesForDate(int dateYYYYMMDD) {
        List<Trade> trades = new ArrayList<>();

        LocalDate date = parseDate(dateYYYYMMDD);
        long startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;
        long endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;

        try (ExcerptTailer tailer = queue.createTailer()) {
            TradeWire wire = new TradeWire();

            while (tailer.readDocument(w -> w.read("trade").marshallable(wire))) {
                if (wire.getExecutedAt() >= startOfDay && wire.getExecutedAt() < endOfDay) {
                    trades.add(wire.toTrade());
                }
            }
        }

        return trades;
    }

    @Override
    public List<Trade> getRecentTrades(int count) {
        List<Trade> result = new ArrayList<>();
        int added = 0;
        for (Trade trade : recentTrades) {
            if (added >= count) break;
            result.add(trade);
            added++;
        }
        return result;
    }

    @Override
    public long getTotalTradeCount() {
        return tradeCount.get();
    }

    @Override
    public void flush() {
        // Chronicle Queue auto-flushes on write
    }

    @Override
    public void close() {
        log.info("Closing Chronicle trade journal");
        queue.close();
    }

    /**
     * Replays all trades through a handler.
     */
    public void replay(TradeHandler handler) {
        try (ExcerptTailer tailer = queue.createTailer()) {
            TradeWire wire = new TradeWire();

            while (tailer.readDocument(w -> w.read("trade").marshallable(wire))) {
                handler.onTrade(wire.toTrade());
            }
        }
    }

    /**
     * Gets trades by symbol.
     */
    public List<Trade> getTradesBySymbol(String ticker, int limit) {
        List<Trade> trades = new ArrayList<>();

        try (ExcerptTailer tailer = queue.createTailer()) {
            TradeWire wire = new TradeWire();

            while (tailer.readDocument(w -> w.read("trade").marshallable(wire))) {
                if (ticker.equals(wire.getTicker())) {
                    trades.add(wire.toTrade());
                }
            }
        }

        // Keep only the last 'limit' trades
        if (trades.size() > limit) {
            trades = trades.subList(trades.size() - limit, trades.size());
        }
        return trades;
    }

    private LocalDate parseDate(int dateYYYYMMDD) {
        int year = dateYYYYMMDD / 10000;
        int month = (dateYYYYMMDD % 10000) / 100;
        int day = dateYYYYMMDD % 100;
        return LocalDate.of(year, month, day);
    }

    /**
     * Handler for trade replay.
     */
    @FunctionalInterface
    public interface TradeHandler {
        void onTrade(Trade trade);
    }
}
