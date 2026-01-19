package com.hft.core.port;

import com.hft.core.model.Exchange;

import java.util.concurrent.CompletableFuture;

/**
 * Primary port interface for exchange connections.
 * Provides lifecycle management for exchange adapters.
 */
public interface ExchangePort {

    /**
     * Returns the exchange this port connects to.
     */
    Exchange getExchange();

    /**
     * Connects to the exchange.
     *
     * @return Future that completes when connection is established
     */
    CompletableFuture<Void> connect();

    /**
     * Disconnects from the exchange.
     *
     * @return Future that completes when disconnected
     */
    CompletableFuture<Void> disconnect();

    /**
     * Returns true if connected to the exchange.
     */
    boolean isConnected();

    /**
     * Returns the order port for this exchange.
     */
    OrderPort getOrderPort();

    /**
     * Returns the market data port for this exchange.
     */
    MarketDataPort getMarketDataPort();

    /**
     * Returns latency statistics for this connection.
     */
    ConnectionStats getConnectionStats();

    /**
     * Connection statistics for monitoring.
     */
    record ConnectionStats(
            long messagesReceived,
            long messagesSent,
            long bytesReceived,
            long bytesSent,
            long avgLatencyNanos,
            long minLatencyNanos,
            long maxLatencyNanos,
            long p99LatencyNanos,
            long reconnectCount,
            long errorCount
    ) {
        public static ConnectionStats empty() {
            return new ConnectionStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
