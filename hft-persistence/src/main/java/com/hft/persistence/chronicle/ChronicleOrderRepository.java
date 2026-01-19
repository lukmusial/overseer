package com.hft.persistence.chronicle;

import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Symbol;
import com.hft.persistence.OrderRepository;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Chronicle Queue based order repository.
 *
 * Maintains an in-memory index for fast lookups while
 * persisting all order state changes to Chronicle Queue.
 */
public class ChronicleOrderRepository implements OrderRepository {
    private static final Logger log = LoggerFactory.getLogger(ChronicleOrderRepository.class);

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;

    // In-memory indices for fast lookups
    private final Map<Long, Order> ordersByClientId = new ConcurrentHashMap<>();
    private final Map<String, Order> ordersByExchangeId = new ConcurrentHashMap<>();
    private final Deque<Order> recentOrders = new ConcurrentLinkedDeque<>();
    private final int maxRecentOrders;

    public ChronicleOrderRepository(Path basePath) {
        this(basePath, 10000);
    }

    public ChronicleOrderRepository(Path basePath, int maxRecentOrders) {
        this.maxRecentOrders = maxRecentOrders;

        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("orders"))
                .build();
        this.appender = queue.createAppender();

        // Rebuild in-memory index from queue
        rebuildIndex();

        log.info("Chronicle order repository initialized at {} with {} orders",
                basePath, ordersByClientId.size());
    }

    private void rebuildIndex() {
        try (ExcerptTailer tailer = queue.createTailer()) {
            OrderWire wire = new OrderWire();

            while (tailer.readDocument(w -> w.read("order").marshallable(wire))) {
                Order order = wire.toOrder();
                ordersByClientId.put(wire.getClientOrderId(), order);
                if (wire.getExchangeOrderId() != null) {
                    ordersByExchangeId.put(wire.getExchangeOrderId(), order);
                }
            }
        }
    }

    @Override
    public void save(Order order) {
        OrderWire wire = OrderWire.from(order);

        appender.writeDocument(w -> w.write("order").marshallable(wire));

        // Update in-memory index
        Order existing = ordersByClientId.put(order.getClientOrderId(), order);
        if (existing == null) {
            recentOrders.addFirst(order);
            while (recentOrders.size() > maxRecentOrders) {
                recentOrders.removeLast();
            }
        }

        if (order.getExchangeOrderId() != null) {
            ordersByExchangeId.put(order.getExchangeOrderId(), order);
        }
    }

    @Override
    public Optional<Order> findByClientOrderId(long clientOrderId) {
        return Optional.ofNullable(ordersByClientId.get(clientOrderId));
    }

    @Override
    public Optional<Order> findByExchangeOrderId(String exchangeOrderId) {
        return Optional.ofNullable(ordersByExchangeId.get(exchangeOrderId));
    }

    @Override
    public List<Order> findBySymbol(Symbol symbol) {
        return ordersByClientId.values().stream()
                .filter(o -> symbol.equals(o.getSymbol()))
                .sorted(Comparator.comparingLong(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return ordersByClientId.values().stream()
                .filter(o -> status == o.getStatus())
                .sorted(Comparator.comparingLong(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDate(int dateYYYYMMDD) {
        LocalDate date = parseDate(dateYYYYMMDD);
        long startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;
        long endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;

        return ordersByClientId.values().stream()
                .filter(o -> o.getCreatedAt() >= startOfDay && o.getCreatedAt() < endOfDay)
                .sorted(Comparator.comparingLong(Order::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> getRecentOrders(int count) {
        return recentOrders.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> getActiveOrders() {
        return ordersByClientId.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING ||
                        o.getStatus() == OrderStatus.SUBMITTED ||
                        o.getStatus() == OrderStatus.ACCEPTED ||
                        o.getStatus() == OrderStatus.PARTIALLY_FILLED)
                .sorted(Comparator.comparingLong(Order::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(long clientOrderId) {
        Order order = ordersByClientId.remove(clientOrderId);
        if (order != null && order.getExchangeOrderId() != null) {
            ordersByExchangeId.remove(order.getExchangeOrderId());
        }
        recentOrders.remove(order);
    }

    @Override
    public void clear() {
        ordersByClientId.clear();
        ordersByExchangeId.clear();
        recentOrders.clear();
        // Note: Chronicle Queue data remains on disk - this just clears in-memory index
    }

    @Override
    public long count() {
        return ordersByClientId.size();
    }

    @Override
    public void flush() {
        // Chronicle auto-flushes
    }

    @Override
    public void close() {
        log.info("Closing Chronicle order repository");
        queue.close();
    }

    /**
     * Replays all orders through a handler.
     */
    public void replay(OrderHandler handler) {
        try (ExcerptTailer tailer = queue.createTailer()) {
            OrderWire wire = new OrderWire();

            while (tailer.readDocument(w -> w.read("order").marshallable(wire))) {
                handler.onOrder(wire.toOrder());
            }
        }
    }

    private LocalDate parseDate(int dateYYYYMMDD) {
        int year = dateYYYYMMDD / 10000;
        int month = (dateYYYYMMDD % 10000) / 100;
        int day = dateYYYYMMDD % 100;
        return LocalDate.of(year, month, day);
    }

    /**
     * Handler for order replay.
     */
    @FunctionalInterface
    public interface OrderHandler {
        void onOrder(Order order);
    }
}
