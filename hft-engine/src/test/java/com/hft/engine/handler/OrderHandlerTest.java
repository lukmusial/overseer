package com.hft.engine.handler;

import com.hft.core.model.*;
import com.hft.core.port.OrderPort;
import com.hft.engine.event.EventType;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.handler.OrderHandler.FillCallback;
import com.hft.engine.handler.OrderHandler.OrderResponseCallback;
import com.hft.engine.service.OrderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {

    private OrderHandler orderHandler;
    private OrderManager orderManager;
    private Symbol testSymbol;

    @Mock
    private OrderPort mockOrderPort;

    @Mock
    private FillCallback mockFillCallback;

    @Mock
    private OrderResponseCallback mockOrderResponseCallback;

    @BeforeEach
    void setUp() {
        orderManager = spy(new OrderManager());
        orderHandler = new OrderHandler(orderManager);
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    // --- handleNewOrder: success paths ---

    @Test
    void handleNewOrder_WhenExchangeReturnsAccepted_ShouldCallUpdateOrder() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.ACCEPTED);
        responseOrder.setExchangeOrderId("EX-001");
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).updateOrder(responseOrder);
        verify(orderManager, never()).rejectOrder(any(), anyString());
    }

    @Test
    void handleNewOrder_WhenExchangeReturnsFilled_ShouldCallUpdateOrder() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.FILLED);
        responseOrder.setFilledQuantity(100);
        responseOrder.setAverageFilledPrice(15000L);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).updateOrder(responseOrder);
        verify(orderManager, never()).rejectOrder(any(), anyString());
    }

    // --- handleNewOrder: rejection paths ---

    @Test
    void handleNewOrder_WhenExchangeReturnsRejected_ShouldCallRejectOrder() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order rejectedOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        rejectedOrder.setStatus(OrderStatus.REJECTED);
        rejectedOrder.setRejectReason("Insufficient funds");
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(rejectedOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(rejectedOrder, "Insufficient funds");
        verify(orderManager, never()).updateOrder(any());
    }

    @Test
    void handleNewOrder_WhenExchangeReturnsRejectedWithNullReason_ShouldUseDefaultMessage() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order rejectedOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        rejectedOrder.setStatus(OrderStatus.REJECTED);
        // rejectReason is null
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(rejectedOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(rejectedOrder, "Rejected by exchange");
    }

    @Test
    void handleNewOrder_WhenExchangeFutureFailsExceptionally_ShouldRejectWithUnwrappedMessage() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        RuntimeException rootCause = new RuntimeException("Connection timeout");
        CompletionException wrapped = new CompletionException(rootCause);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.failedFuture(wrapped));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(any(), eq("Connection timeout"));
    }

    @Test
    void handleNewOrder_WithNoRegisteredExchangePort_ShouldRejectWithNoAdapterMessage() {
        // No port registered for ALPACA
        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(any(), eq("No exchange adapter available"));
        verify(orderManager, never()).trackOrder(any());
    }

    // --- OrderResponseCallback tests ---

    @Test
    void orderResponseCallback_ShouldBeInvokedAfterSuccessfulExchangeResponse() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);
        orderHandler.setOrderResponseCallback(mockOrderResponseCallback);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.ACCEPTED);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(mockOrderResponseCallback).onOrderResponse(responseOrder);
    }

    @Test
    void orderResponseCallback_ShouldBeInvokedAfterExchangeRejection() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);
        orderHandler.setOrderResponseCallback(mockOrderResponseCallback);

        Order rejectedOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        rejectedOrder.setStatus(OrderStatus.REJECTED);
        rejectedOrder.setRejectReason("Insufficient margin");
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(rejectedOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(mockOrderResponseCallback).onOrderResponse(rejectedOrder);
    }

    @Test
    void orderResponseCallback_ShouldBeInvokedAfterExceptionalFailure() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);
        orderHandler.setOrderResponseCallback(mockOrderResponseCallback);

        when(mockOrderPort.submitOrder(any())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("Network error")));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        // The callback receives the original order (not the response), since the future failed
        verify(mockOrderResponseCallback).onOrderResponse(any(Order.class));
    }

    // --- unwrapCause tests ---

    @Test
    void handleNewOrder_ShouldUnwrapCompletionExceptionChain() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        RuntimeException root = new RuntimeException("Root cause");
        CompletionException inner = new CompletionException(root);
        CompletionException outer = new CompletionException(inner);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.failedFuture(outer));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(any(), eq("Root cause"));
    }

    @Test
    void handleNewOrder_ShouldUnwrapExecutionException() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        RuntimeException root = new RuntimeException("Execution root");
        ExecutionException execEx = new ExecutionException(root);
        CompletionException wrapped = new CompletionException(execEx);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.failedFuture(wrapped));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).rejectOrder(any(), eq("Execution root"));
    }

    // --- FillCallback tests ---

    @Test
    void fillCallback_ShouldBeInvokedWhenOrderIsImmediatelyFilled() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);
        orderHandler.setFillCallback(mockFillCallback);

        Order filledOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        filledOrder.setStatus(OrderStatus.FILLED);
        filledOrder.setFilledQuantity(100);
        filledOrder.setAverageFilledPrice(15050L);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(filledOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(mockFillCallback).onFill(filledOrder, 100L, 15050L);
    }

    @Test
    void fillCallback_ShouldNotBeInvokedWhenOrderIsAcceptedButNotFilled() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);
        orderHandler.setFillCallback(mockFillCallback);

        Order acceptedOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        acceptedOrder.setStatus(OrderStatus.ACCEPTED);
        when(mockOrderPort.submitOrder(any())).thenReturn(CompletableFuture.completedFuture(acceptedOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(mockFillCallback, never()).onFill(any(), anyLong(), anyLong());
    }

    // --- handleCancelOrder tests ---

    @Test
    void handleCancelOrder_ShouldDelegateToExchangeCancel() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        // First, create and track an order so getOrder will find it
        Order existingOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        long clientOrderId = existingOrder.getClientOrderId();
        orderManager.trackOrder(existingOrder);

        Order cancelledOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        when(mockOrderPort.cancelOrder(any())).thenReturn(CompletableFuture.completedFuture(cancelledOrder));

        TradingEvent event = new TradingEvent();
        event.setType(EventType.CANCEL_ORDER);
        event.setClientOrderId(clientOrderId);
        event.setSymbol(testSymbol);

        orderHandler.onEvent(event, 2L, true);

        verify(mockOrderPort).cancelOrder(existingOrder);
        verify(orderManager).updateOrder(cancelledOrder);
    }

    @Test
    void handleCancelOrder_ForUnknownOrder_ShouldNotCallExchange() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        TradingEvent event = new TradingEvent();
        event.setType(EventType.CANCEL_ORDER);
        event.setClientOrderId(999999L);
        event.setSymbol(testSymbol);

        orderHandler.onEvent(event, 2L, true);

        verify(mockOrderPort, never()).cancelOrder(any());
    }

    // --- Event field propagation tests ---

    @Test
    void handleNewOrder_ShouldPreserveClientOrderIdFromEvent() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.ACCEPTED);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(mockOrderPort.submitOrder(orderCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        long expectedClientOrderId = 42L;
        event.setClientOrderId(expectedClientOrderId);

        orderHandler.onEvent(event, 1L, true);

        Order submittedOrder = orderCaptor.getValue();
        assertEquals(expectedClientOrderId, submittedOrder.getClientOrderId());
    }

    @Test
    void handleNewOrder_ShouldCopyPriceScaleFromEvent() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.ACCEPTED);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(mockOrderPort.submitOrder(orderCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        event.setPriceScale(10000);

        orderHandler.onEvent(event, 1L, true);

        Order submittedOrder = orderCaptor.getValue();
        assertEquals(10000, submittedOrder.getPriceScale());
    }

    @Test
    void handleNewOrder_ShouldCopyOptionalFieldsFromEvent() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        Order responseOrder = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        responseOrder.setStatus(OrderStatus.ACCEPTED);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(mockOrderPort.submitOrder(orderCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(responseOrder));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        event.setTimeInForce(TimeInForce.GTC);
        event.setStopPrice(14500L);
        event.setStrategyId("momentum-1");

        orderHandler.onEvent(event, 1L, true);

        Order submittedOrder = orderCaptor.getValue();
        assertEquals(TimeInForce.GTC, submittedOrder.getTimeInForce());
        assertEquals(14500L, submittedOrder.getStopPrice());
        assertEquals("momentum-1", submittedOrder.getStrategyId());
    }

    @Test
    void handleNewOrder_ShouldMarkOrderAsSubmittedBeforeSending() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(mockOrderPort.submitOrder(orderCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(new Order()));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        Order submittedOrder = orderCaptor.getValue();
        assertEquals(OrderStatus.SUBMITTED, submittedOrder.getStatus());
        assertTrue(submittedOrder.getSubmittedAt() > 0);
    }

    @Test
    void handleNewOrder_ShouldTrackOrderBeforeSending() {
        orderHandler.registerOrderPort(Exchange.ALPACA, mockOrderPort);

        when(mockOrderPort.submitOrder(any()))
                .thenReturn(CompletableFuture.completedFuture(new Order()));

        TradingEvent event = newOrderEvent(testSymbol, OrderSide.BUY, OrderType.LIMIT, 100, 15000L);
        orderHandler.onEvent(event, 1L, true);

        verify(orderManager).trackOrder(any(Order.class));
    }

    // --- Non-order event types should be ignored ---

    @Test
    void onEvent_WithQuoteUpdate_ShouldBeIgnored() {
        TradingEvent event = new TradingEvent();
        event.setType(EventType.QUOTE_UPDATE);

        orderHandler.onEvent(event, 1L, true);

        verify(orderManager, never()).createOrder(any(), any(), any(), anyLong(), anyLong());
    }

    // --- Helper methods ---

    private TradingEvent newOrderEvent(Symbol symbol, OrderSide side, OrderType orderType,
                                       long quantity, long price) {
        TradingEvent event = new TradingEvent();
        event.setType(EventType.NEW_ORDER);
        event.setSymbol(symbol);
        event.setSide(side);
        event.setOrderType(orderType);
        event.setQuantity(quantity);
        event.setPrice(price);
        event.setTimestampNanos(System.nanoTime());
        return event;
    }

    private Order createOrder(Symbol symbol, OrderSide side, long quantity, long price) {
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setType(OrderType.LIMIT);
        return order;
    }
}
