package com.hft.persistence.impl;

import com.hft.core.model.OrderSide;
import com.hft.core.model.Exchange;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import com.hft.persistence.TradeJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * File-based trade journal implementation.
 * Writes trades to daily CSV files for durability and analysis.
 */
public class FileTradeJournal implements TradeJournal {
    private static final Logger log = LoggerFactory.getLogger(FileTradeJournal.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CSV_HEADER = "timestamp_nanos,symbol,exchange,side,quantity,price,trade_id,order_id";

    private final Path baseDir;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicLong tradeCount = new AtomicLong();
    private final Deque<Trade> recentTrades = new ArrayDeque<>();
    private final int maxRecentTrades;

    private BufferedWriter currentWriter;
    private int currentDateYYYYMMDD;

    public FileTradeJournal(Path baseDir) {
        this(baseDir, 1000);
    }

    public FileTradeJournal(Path baseDir, int maxRecentTrades) {
        this.baseDir = baseDir;
        this.maxRecentTrades = maxRecentTrades;

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create journal directory", e);
        }

        log.info("Trade journal initialized at {}", baseDir);
    }

    @Override
    public void record(Trade trade) {
        writeLock.lock();
        try {
            ensureWriterForToday();
            writeTrade(trade);
            tradeCount.incrementAndGet();

            recentTrades.addFirst(trade);
            while (recentTrades.size() > maxRecentTrades) {
                recentTrades.removeLast();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<Trade> getTradesForDate(int dateYYYYMMDD) {
        Path file = getFileForDate(dateYYYYMMDD);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }

        List<Trade> trades = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }
                Trade trade = parseTrade(line);
                if (trade != null) {
                    trades.add(trade);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read trades for date {}", dateYYYYMMDD, e);
        }
        return trades;
    }

    @Override
    public List<Trade> getRecentTrades(int count) {
        writeLock.lock();
        try {
            return recentTrades.stream()
                    .limit(count)
                    .collect(Collectors.toList());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long getTotalTradeCount() {
        return tradeCount.get();
    }

    @Override
    public void flush() {
        writeLock.lock();
        try {
            if (currentWriter != null) {
                currentWriter.flush();
            }
        } catch (IOException e) {
            log.error("Failed to flush trade journal", e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            if (currentWriter != null) {
                currentWriter.close();
                currentWriter = null;
            }
        } catch (IOException e) {
            log.error("Failed to close trade journal", e);
        } finally {
            writeLock.unlock();
        }
    }

    private void ensureWriterForToday() {
        int today = Integer.parseInt(LocalDate.now().format(DATE_FORMAT));
        if (currentWriter == null || today != currentDateYYYYMMDD) {
            closeCurrentWriter();
            currentDateYYYYMMDD = today;
            try {
                Path file = getFileForDate(today);
                boolean newFile = !Files.exists(file);
                currentWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (newFile) {
                    currentWriter.write(CSV_HEADER);
                    currentWriter.newLine();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open journal file", e);
            }
        }
    }

    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                log.warn("Failed to close previous journal writer", e);
            }
            currentWriter = null;
        }
    }

    private void writeTrade(Trade trade) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(trade.getExecutedAt()).append(',');
            sb.append(escapeCSV(trade.getSymbol().getTicker())).append(',');
            sb.append(trade.getSymbol().getExchange().name()).append(',');
            sb.append(trade.getSide().name()).append(',');
            sb.append(trade.getQuantity()).append(',');
            sb.append(trade.getPrice()).append(',');
            // Use exchangeTradeId (String) for external identification
            sb.append(escapeCSV(trade.getExchangeTradeId())).append(',');
            sb.append(trade.getClientOrderId());
            currentWriter.write(sb.toString());
            currentWriter.newLine();
        } catch (IOException e) {
            log.error("Failed to write trade", e);
        }
    }

    private Trade parseTrade(String line) {
        try {
            String[] parts = parseCSVLine(line);
            if (parts.length < 8) {
                return null;
            }

            Trade trade = new Trade();
            trade.setExecutedAt(Long.parseLong(parts[0]));
            trade.setSymbol(new Symbol(parts[1], Exchange.valueOf(parts[2])));
            trade.setSide(OrderSide.valueOf(parts[3]));
            trade.setQuantity(Long.parseLong(parts[4]));
            trade.setPrice(Long.parseLong(parts[5]));
            // Trade ID and ClientOrderId are longs in the model
            if (!parts[6].isEmpty()) {
                trade.setExchangeTradeId(parts[6]); // Use exchange trade ID for string
            }
            if (!parts[7].isEmpty()) {
                trade.setClientOrderId(Long.parseLong(parts[7]));
            }
            return trade;
        } catch (Exception e) {
            log.warn("Failed to parse trade line: {}", line, e);
            return null;
        }
    }

    private Path getFileForDate(int dateYYYYMMDD) {
        return baseDir.resolve("trades_" + dateYYYYMMDD + ".csv");
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCSVLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        return parts.toArray(new String[0]);
    }
}
