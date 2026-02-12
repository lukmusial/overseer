package com.hft.bdd.backtest;

/**
 * Historical OHLCV candle data point.
 *
 * @param time   Unix timestamp in seconds (candle open time)
 * @param open   Open price in dollars
 * @param high   High price in dollars
 * @param low    Low price in dollars
 * @param close  Close price in dollars
 * @param volume Volume in base asset units
 */
public record Candle(long time, double open, double high, double low, double close, long volume) {}
