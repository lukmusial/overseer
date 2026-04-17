package com.hft.persistence.chronicle;

import com.hft.core.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleOrderRepositoryTest {

    @TempDir
    Path tempDir;

    private ChronicleOrderRepository repository;
    private static final Symbol BTCUSDT = new Symbol("BTCUSDT", Exchange.BINANCE);
    private static final Symbol AAPL = new Symbol("AAPL", Exchange.ALPACA);

    @BeforeEach
    void setUp() {
        repository = new ChronicleOrderRepository(tempDir);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void shouldSaveAndFindOrderByClientId() {
        Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);

        repository.save(order);

        var found = repository.findByClientOrderId(order.getClientOrderId());
        assertTrue(found.isPresent());
        assertEquals(order.getClientOrderId(), found.get().getClientOrderId());
    }

    @Test
    void shouldPersistRejectedStatusAcrossRestart() {
        // Simulate the order lifecycle: SUBMITTED then REJECTED
        Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
        order.markSubmitted();
        repository.save(order);

        // Verify saved as SUBMITTED
        assertEquals(OrderStatus.SUBMITTED,
                repository.findByClientOrderId(order.getClientOrderId()).get().getStatus());

        // Exchange rejects the order
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason("Insufficient balance");
        repository.save(order);

        // Verify in-memory shows REJECTED
        assertEquals(OrderStatus.REJECTED,
                repository.findByClientOrderId(order.getClientOrderId()).get().getStatus());

        // Close and reopen to simulate restart
        long clientOrderId = order.getClientOrderId();
        repository.close();
        repository = new ChronicleOrderRepository(tempDir);

        // Verify REJECTED survives restart
        var restored = repository.findByClientOrderId(clientOrderId);
        assertTrue(restored.isPresent());
        assertEquals(OrderStatus.REJECTED, restored.get().getStatus());
        assertEquals("Insufficient balance", restored.get().getRejectReason());
    }

    @Test
    void shouldPersistFilledStatusAcrossRestart() {
        Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
        order.setPriceScale(100_000_000);
        order.strategyId("test-strat");
        order.markSubmitted();
        repository.save(order);

        // Exchange fills
        order.markAccepted("BINANCE-123");
        order.markFilled(1_000_000, 7_000_000_000_000L);
        repository.save(order);

        long clientOrderId = order.getClientOrderId();
        repository.close();
        repository = new ChronicleOrderRepository(tempDir);

        var restored = repository.findByClientOrderId(clientOrderId);
        assertTrue(restored.isPresent());
        assertEquals(OrderStatus.FILLED, restored.get().getStatus());
        assertEquals("BINANCE-123", restored.get().getExchangeOrderId());
        assertEquals(1_000_000, restored.get().getFilledQuantity());
    }

    @Test
    void shouldHandleConcurrentWritesFromMultipleThreads() throws Exception {
        int threadCount = 4;
        int ordersPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < ordersPerThread; i++) {
                        Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
                        order.setClientOrderId(threadId * 100_000L + i);
                        order.markSubmitted();
                        repository.save(order);

                        // Simulate rejection on same thread
                        order.setStatus(OrderStatus.REJECTED);
                        order.setRejectReason("Thread-" + threadId + " rejection");
                        repository.save(order);
                    }
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Threads should complete within 30s");
        executor.shutdown();

        // All orders should be REJECTED in memory
        assertEquals(threadCount * ordersPerThread, repository.count());
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < ordersPerThread; i++) {
                var found = repository.findByClientOrderId(t * 100_000L + i);
                assertTrue(found.isPresent(), "Order " + t + "/" + i + " should exist");
                assertEquals(OrderStatus.REJECTED, found.get().getStatus(),
                        "Order " + t + "/" + i + " should be REJECTED");
            }
        }

        // Restart and verify persistence
        repository.close();
        repository = new ChronicleOrderRepository(tempDir);

        assertEquals(threadCount * ordersPerThread, repository.count());
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < ordersPerThread; i++) {
                var found = repository.findByClientOrderId(t * 100_000L + i);
                assertTrue(found.isPresent());
                assertEquals(OrderStatus.REJECTED, found.get().getStatus(),
                        "Order " + t + "/" + i + " should still be REJECTED after restart");
            }
        }
    }

    @Test
    void shouldFindOrdersByStatus() {
        Order filled = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
        filled.markSubmitted();
        filled.markAccepted("EX-1");
        filled.markFilled(1_000_000, 7_000_000_000_000L);
        repository.save(filled);

        Order rejected = createOrder(BTCUSDT, OrderSide.SELL, 500_000, 7_100_000_000_000L);
        rejected.markSubmitted();
        rejected.setStatus(OrderStatus.REJECTED);
        rejected.setRejectReason("Filter failure");
        repository.save(rejected);

        List<Order> filledOrders = repository.findByStatus(OrderStatus.FILLED);
        assertEquals(1, filledOrders.size());

        List<Order> rejectedOrders = repository.findByStatus(OrderStatus.REJECTED);
        assertEquals(1, rejectedOrders.size());
        assertEquals("Filter failure", rejectedOrders.get(0).getRejectReason());
    }

    @Test
    void shouldFindOrdersBySymbol() {
        Order btc = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
        repository.save(btc);

        Order aapl = createOrder(AAPL, OrderSide.BUY, 100, 15_000);
        repository.save(aapl);

        assertEquals(1, repository.findBySymbol(BTCUSDT).size());
        assertEquals(1, repository.findBySymbol(AAPL).size());
    }

    @Test
    void shouldReturnRecentOrders() {
        for (int i = 0; i < 5; i++) {
            Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
            repository.save(order);
        }

        List<Order> recent = repository.getRecentOrders(3);
        assertEquals(3, recent.size());
    }

    @Test
    void shouldDeleteOrder() {
        Order order = createOrder(BTCUSDT, OrderSide.BUY, 1_000_000, 7_000_000_000_000L);
        repository.save(order);

        assertEquals(1, repository.count());
        repository.delete(order.getClientOrderId());
        assertEquals(0, repository.count());
    }

    private Order createOrder(Symbol symbol, OrderSide side, long quantity, long price) {
        return new Order()
                .symbol(symbol)
                .side(side)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(price)
                .quantity(quantity);
    }
}
