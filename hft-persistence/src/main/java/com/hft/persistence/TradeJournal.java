package com.hft.persistence;

import com.hft.core.model.Trade;

import java.util.List;

/**
 * Interface for trade journaling.
 * Records all executed trades for audit and analysis.
 */
public interface TradeJournal {

    /**
     * Records a trade to the journal.
     */
    void record(Trade trade);

    /**
     * Gets all trades for a given trading day.
     *
     * @param dateYYYYMMDD date in YYYYMMDD format
     */
    List<Trade> getTradesForDate(int dateYYYYMMDD);

    /**
     * Gets the most recent N trades.
     */
    List<Trade> getRecentTrades(int count);

    /**
     * Gets the total number of trades recorded.
     */
    long getTotalTradeCount();

    /**
     * Flushes any buffered writes to storage.
     */
    void flush();

    /**
     * Closes the journal.
     */
    void close();
}
