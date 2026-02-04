package com.hft.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.api.dto.CreateOrderRequest;
import com.hft.api.dto.OrderDto;
import com.hft.api.service.TradingService;
import com.hft.core.model.OrderSide;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.OrderType;
import com.hft.core.model.TimeInForce;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradingService tradingService;

    @Test
    void getActiveOrders_returnsOrderList() throws Exception {
        OrderDto order = createOrderDto(1L, OrderStatus.ACCEPTED);
        when(tradingService.getActiveOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientOrderId").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].side").value("BUY"));
    }

    @Test
    void submitOrder_createsNewOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "AAPL", "ALPACA", OrderSide.BUY, OrderType.LIMIT,
                TimeInForce.DAY, 100, 15000L, 0L, null
        );
        OrderDto createdOrder = createOrderDto(1L, OrderStatus.SUBMITTED);
        when(tradingService.submitOrder(any())).thenReturn(createdOrder);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientOrderId").value(1))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void submitOrder_validatesRequest() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "", "ALPACA", OrderSide.BUY, OrderType.LIMIT,
                TimeInForce.DAY, 0, 15000L, 0L, null
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelOrder_callsTradingService() throws Exception {
        mockMvc.perform(delete("/api/orders/123")
                        .param("symbol", "AAPL")
                        .param("exchange", "ALPACA"))
                .andExpect(status().isOk());

        verify(tradingService).cancelOrder(123L, "AAPL", "ALPACA");
    }

    @Test
    void getOrder_returnsOrder() throws Exception {
        OrderDto order = createOrderDto(123L, OrderStatus.FILLED);
        when(tradingService.getOrder(123L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientOrderId").value(123));
    }

    @Test
    void getOrder_returnsNotFoundWhenMissing() throws Exception {
        when(tradingService.getOrder(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    private OrderDto createOrderDto(long id, OrderStatus status) {
        return new OrderDto(
                id, null, "AAPL", "ALPACA",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                100, 15000L, 0L, 0L, 0L, 100,
                status, null, null,
                System.nanoTime(), System.nanoTime()
        );
    }
}
