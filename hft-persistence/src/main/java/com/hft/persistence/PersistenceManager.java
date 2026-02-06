package com.hft.persistence;

import com.hft.core.model.Order;
import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import com.hft.persistence.chronicle.ChronicleAuditLog;
import com.hft.persistence.chronicle.ChronicleOrderRepository;
import com.hft.persistence.chronicle.ChroniclePositionSnapshotStore;
import com.hft.persistence.chronicle.ChronicleStrategyRepository;
import com.hft.persistence.chronicle.ChronicleTradeJournal;
import com.hft.persistence.impl.FileAuditLog;
import com.hft.persistence.impl.FileTradeJournal;
import com.hft.persistence.impl.InMemoryOrderRepository;
import com.hft.persistence.impl.InMemoryPositionSnapshotStore;
import com.hft.persistence.impl.InMemoryStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Central persistence manager that coordinates all persistence components.
 */
public class PersistenceManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PersistenceManager.class);

    private final TradeJournal tradeJournal;
    private final OrderRepository orderRepository;
    private final PositionSnapshotStore positionStore;
    private final StrategyRepository strategyRepository;
    private final AuditLog auditLog;

    public PersistenceManager(TradeJournal tradeJournal,
                             OrderRepository orderRepository,
                             PositionSnapshotStore positionStore,
                             StrategyRepository strategyRepository,
                             AuditLog auditLog) {
        this.tradeJournal = tradeJournal;
        this.orderRepository = orderRepository;
        this.positionStore = positionStore;
        this.strategyRepository = strategyRepository;
        this.auditLog = auditLog;
    }

    /**
     * Creates a persistence manager with default in-memory stores.
     */
    public static PersistenceManager inMemory() {
        return new PersistenceManager(
                new InMemoryTradeJournal(),
                new InMemoryOrderRepository(),
                new InMemoryPositionSnapshotStore(),
                new InMemoryStrategyRepository(),
                new InMemoryAuditLog()
        );
    }

    /**
     * Creates a persistence manager with file-based persistence.
     */
    public static PersistenceManager fileBased(Path baseDir) {
        return new PersistenceManager(
                new FileTradeJournal(baseDir.resolve("trades")),
                new InMemoryOrderRepository(), // Orders typically session-scoped
                new ChroniclePositionSnapshotStore(baseDir),
                new InMemoryStrategyRepository(), // Strategies use in-memory for file-based mode
                new FileAuditLog(baseDir.resolve("audit"))
        );
    }

    /**
     * Creates a persistence manager with file-based persistence using default directory.
     */
    public static PersistenceManager fileBased() {
        Path baseDir = Paths.get(System.getProperty("user.home"), ".hft-client", "data");
        return fileBased(baseDir);
    }

    /**
     * Creates a persistence manager with Chronicle Queue based persistence.
     * This is the recommended option for production use with ultra-low latency requirements.
     *
     * Features:
     * - Zero-GC writes
     * - Memory-mapped file persistence
     * - Automatic daily rolling
     * - Sub-microsecond latency
     */
    public static PersistenceManager chronicle(Path baseDir) {
        return new PersistenceManager(
                new ChronicleTradeJournal(baseDir),
                new ChronicleOrderRepository(baseDir),
                new ChroniclePositionSnapshotStore(baseDir),
                new ChronicleStrategyRepository(baseDir),
                new ChronicleAuditLog(baseDir)
        );
    }

    /**
     * Creates a Chronicle-based persistence manager using default directory.
     */
    public static PersistenceManager chronicle() {
        Path baseDir = Paths.get(System.getProperty("user.home"), ".hft-client", "chronicle");
        return chronicle(baseDir);
    }

    // Trade operations

    public void recordTrade(Trade trade) {
        tradeJournal.record(trade);
        auditLog.log(AuditLog.EventType.ORDER_FILLED,
                "Trade executed: " + trade.getSymbol().getTicker() + " " +
                        trade.getSide() + " " + trade.getQuantity() + " @ " + trade.getPrice());
    }

    // Order operations

    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    public void logOrderSubmitted(Order order) {
        auditLog.log(AuditLog.EventType.ORDER_SUBMITTED,
                "Order submitted: " + order.getClientOrderId(),
                order.getSymbol().getTicker() + " " + order.getSide() + " " +
                        order.getQuantity() + " @ " + order.getPrice());
    }

    public void logOrderRejected(Order order, String reason) {
        auditLog.log(AuditLog.EventType.ORDER_REJECTED,
                "Order rejected: " + order.getClientOrderId(),
                "Reason: " + reason);
    }

    // Position operations

    public void snapshotPositions(Map<Symbol, Position> positions) {
        positionStore.saveAllSnapshots(positions, System.nanoTime());
    }

    public void saveEndOfDay(Map<Symbol, Position> positions, int dateYYYYMMDD) {
        positionStore.saveEndOfDayPositions(positions, dateYYYYMMDD);
        auditLog.log(AuditLog.EventType.POSITION_UPDATED,
                "End of day positions saved for " + dateYYYYMMDD);
    }

    // Audit log shortcuts

    public void logEngineStarted() {
        auditLog.log(AuditLog.EventType.ENGINE_STARTED, "Trading engine started");
    }

    public void logEngineStopped() {
        auditLog.log(AuditLog.EventType.ENGINE_STOPPED, "Trading engine stopped");
    }

    public void logStrategyStarted(String strategyId) {
        auditLog.log(AuditLog.EventType.STRATEGY_STARTED, "Strategy started: " + strategyId);
    }

    public void logStrategyStopped(String strategyId) {
        auditLog.log(AuditLog.EventType.STRATEGY_STOPPED, "Strategy stopped: " + strategyId);
    }

    public void logRiskCheckFailed(String orderId, String reason) {
        auditLog.log(AuditLog.EventType.RISK_CHECK_FAILED,
                "Risk check failed for order: " + orderId,
                "Reason: " + reason);
    }

    public void logCircuitBreakerTripped(String reason) {
        auditLog.log(AuditLog.EventType.CIRCUIT_BREAKER_TRIPPED,
                "Circuit breaker tripped",
                "Reason: " + reason);
    }

    public void logError(String message, Exception e) {
        auditLog.log(AuditLog.EventType.ERROR, message,
                e != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : null);
    }

    // Component access

    public TradeJournal getTradeJournal() {
        return tradeJournal;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public PositionSnapshotStore getPositionStore() {
        return positionStore;
    }

    public StrategyRepository getStrategyRepository() {
        return strategyRepository;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    // Lifecycle

    public void flush() {
        tradeJournal.flush();
        orderRepository.flush();
        strategyRepository.flush();
        auditLog.flush();
    }

    @Override
    public void close() {
        log.info("Closing persistence manager");
        try {
            tradeJournal.close();
        } catch (Exception e) {
            log.warn("Error closing trade journal", e);
        }
        try {
            orderRepository.close();
        } catch (Exception e) {
            log.warn("Error closing order repository", e);
        }
        try {
            positionStore.close();
        } catch (Exception e) {
            log.warn("Error closing position store", e);
        }
        try {
            strategyRepository.close();
        } catch (Exception e) {
            log.warn("Error closing strategy repository", e);
        }
        try {
            auditLog.close();
        } catch (Exception e) {
            log.warn("Error closing audit log", e);
        }
    }

    /**
     * Simple in-memory trade journal for testing.
     */
    private static class InMemoryTradeJournal implements TradeJournal {
        private final java.util.Deque<Trade> trades = new java.util.concurrent.ConcurrentLinkedDeque<>();

        @Override
        public void record(Trade trade) {
            trades.addFirst(trade);
        }

        @Override
        public java.util.List<Trade> getTradesForDate(int dateYYYYMMDD) {
            return new java.util.ArrayList<>(trades);
        }

        @Override
        public java.util.List<Trade> getRecentTrades(int count) {
            return trades.stream().limit(count).toList();
        }

        @Override
        public long getTotalTradeCount() {
            return trades.size();
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    /**
     * Simple in-memory audit log for testing.
     */
    private static class InMemoryAuditLog implements AuditLog {
        private final java.util.Deque<AuditEvent> events = new java.util.concurrent.ConcurrentLinkedDeque<>();

        @Override
        public void log(AuditEvent event) {
            events.addFirst(event);
        }

        @Override
        public java.util.List<AuditEvent> getEventsForDate(int dateYYYYMMDD) {
            return new java.util.ArrayList<>(events);
        }

        @Override
        public java.util.List<AuditEvent> getRecentEvents(int count) {
            return events.stream().limit(count).toList();
        }

        @Override
        public java.util.List<AuditEvent> getEventsByType(EventType type, int count) {
            return events.stream().filter(e -> e.type() == type).limit(count).toList();
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
