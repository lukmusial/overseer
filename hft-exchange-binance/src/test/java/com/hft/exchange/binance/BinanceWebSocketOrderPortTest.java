package com.hft.exchange.binance;

import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketOrderPortTest {

    private static final Symbol BTCUSDT = new Symbol("BTCUSDT", Exchange.BINANCE);

    @Mock
    private BinanceHttpClient httpClient;

    private BinanceConfig config;
    private BinanceWebSocketOrderPort port;

    @BeforeEach
    void setUp() {
        config = BinanceConfig.testnet("test-api-key", "test-secret-key");
        port = new BinanceWebSocketOrderPort(config, httpClient);
    }

    // ==================== submitOrder when not connected ====================

    @Test
    void submitOrder_whenNotConnected_returnsFailedFutureWithIllegalStateException() {
        Order order = createTestOrder();

        CompletableFuture<Order> future = port.submitOrder(order);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Not connected"));
    }

    // ==================== submitOrder with filter validation failures ====================

    @Test
    void submitOrder_whenQuantityRoundsToZero_returnsRejectedOrder() throws Exception {
        setConnected(true);

        // stepSize=0.001 (100_000), so quantity 50_000 (0.0005) rounds to 0
        var filters = new BinanceSymbolFilters(100_000, 100_000, 1, 0);
        when(httpClient.getSymbolFilters("BTCUSDT")).thenReturn(filters);

        Order order = createTestOrder();
        order.setQuantity(50_000); // 0.0005 BTC — below step size

        CompletableFuture<Order> future = port.submitOrder(order);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        Order result = future.get();
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertTrue(result.getRejectReason().contains("rounds to 0"));
    }

    @Test
    void submitOrder_whenBelowMinNotional_returnsRejectedOrder() throws Exception {
        setConnected(true);

        // minNotional = 10 USDT = 1_000_000_000 in 8-decimal format
        var filters = new BinanceSymbolFilters(1, 0, 1, 1_000_000_000L);
        when(httpClient.getSymbolFilters("BTCUSDT")).thenReturn(filters);

        Order order = createTestOrder();
        order.setQuantity(1_000);           // tiny quantity
        order.setPrice(100_000_000);        // price = 1 USDT — notional well below 10 USDT

        CompletableFuture<Order> future = port.submitOrder(order);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        Order result = future.get();
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertTrue(result.getRejectReason().contains("notional"));
    }

    // ==================== handleMessage with success response ====================

    @Test
    void handleMessage_withSuccessResponse_completesFutureAndPreservesPriceScaleAndStrategyId() throws Exception {
        Order order = createTestOrder();
        order.setPriceScale(100_000_000);
        order.strategyId("momentum-1");

        CompletableFuture<Order> future = new CompletableFuture<>();
        addPendingRequest("1", future, order);

        String successJson = """
                {
                    "id": "1",
                    "status": 200,
                    "result": {
                        "orderId": 98765,
                        "clientOrderId": "%d",
                        "symbol": "BTCUSDT",
                        "side": "BUY",
                        "type": "MARKET",
                        "status": "NEW",
                        "origQty": "0.5",
                        "executedQty": "0",
                        "price": "0"
                    }
                }
                """.formatted(order.getClientOrderId());

        port.handleMessage(successJson);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());

        Order result = future.get();
        assertEquals("98765", result.getExchangeOrderId());
        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        assertEquals(order.getClientOrderId(), result.getClientOrderId());
        assertEquals(100_000_000, result.getPriceScale());
        assertEquals("momentum-1", result.getStrategyId());
    }

    // ==================== handleMessage with error response ====================

    @Test
    void handleMessage_withErrorResponse_completesNormallyWithRejectedOrder() throws Exception {
        Order order = createTestOrder();

        CompletableFuture<Order> future = new CompletableFuture<>();
        addPendingRequest("2", future, order);

        String errorJson = """
                {
                    "id": "2",
                    "status": 400,
                    "error": {
                        "code": -1013,
                        "msg": "Filter failure: LOT_SIZE"
                    }
                }
                """;

        port.handleMessage(errorJson);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());

        Order result = future.get();
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertTrue(result.getRejectReason().contains("-1013"));
        assertTrue(result.getRejectReason().contains("LOT_SIZE"));
    }

    @Test
    void handleMessage_withErrorResponse_doesNotThrowCompletionException() throws Exception {
        Order order = createTestOrder();

        CompletableFuture<Order> future = new CompletableFuture<>();
        addPendingRequest("3", future, order);

        String errorJson = """
                {
                    "id": "3",
                    "status": 400,
                    "error": {
                        "code": -2010,
                        "msg": "Insufficient balance"
                    }
                }
                """;

        port.handleMessage(errorJson);

        // Verify future completed normally (not exceptionally) — no CompletionException
        assertDoesNotThrow(() -> future.get());
        assertDoesNotThrow(() -> future.join());

        Order result = future.get();
        assertEquals(OrderStatus.REJECTED, result.getStatus());
    }

    // ==================== disconnect ====================

    @Test
    void disconnect_failsAllPendingRequests() throws Exception {
        CompletableFuture<Order> future1 = new CompletableFuture<>();
        CompletableFuture<Order> future2 = new CompletableFuture<>();
        addPendingRequest("10", future1, createTestOrder());
        addPendingRequest("11", future2, createTestOrder());

        port.disconnect();

        assertTrue(future1.isCompletedExceptionally());
        assertTrue(future2.isCompletedExceptionally());

        ExecutionException ex1 = assertThrows(ExecutionException.class, future1::get);
        assertInstanceOf(IllegalStateException.class, ex1.getCause());
        assertTrue(ex1.getCause().getMessage().contains("disconnected"));

        ExecutionException ex2 = assertThrows(ExecutionException.class, future2::get);
        assertInstanceOf(IllegalStateException.class, ex2.getCause());
    }

    // ==================== Helpers ====================

    private Order createTestOrder() {
        Order order = new Order();
        order.setSymbol(BTCUSDT);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.MARKET);
        order.setQuantity(50_000_000); // 0.5 BTC
        order.setPrice(4500000000000L);
        return order;
    }

    private void setConnected(boolean value) throws Exception {
        Field field = BinanceWebSocketOrderPort.class.getDeclaredField("connected");
        field.setAccessible(true);
        field.set(port, value);
    }

    @SuppressWarnings("unchecked")
    private void addPendingRequest(String id, CompletableFuture<Order> future, Order order) throws Exception {
        // Access the pendingRequests map
        Field mapField = BinanceWebSocketOrderPort.class.getDeclaredField("pendingRequests");
        mapField.setAccessible(true);
        var pendingRequests = (java.util.concurrent.ConcurrentHashMap<String, Object>) mapField.get(port);

        // Create a PendingRequest record instance via reflection
        Class<?> pendingRequestClass = null;
        for (Class<?> inner : BinanceWebSocketOrderPort.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("PendingRequest")) {
                pendingRequestClass = inner;
                break;
            }
        }
        assertNotNull(pendingRequestClass, "PendingRequest inner class not found");

        var constructor = pendingRequestClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object pendingRequest = constructor.newInstance(future, order, System.nanoTime());

        pendingRequests.put(id, pendingRequest);
    }
}
