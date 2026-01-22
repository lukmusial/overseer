package com.hft.api.controller;

import com.hft.api.dto.CreateOrderRequest;
import com.hft.api.dto.OrderDto;
import com.hft.api.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDto order = tradingService.submitOrder(request);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getActiveOrders() {
        return ResponseEntity.ok(tradingService.getActiveOrders());
    }

    @GetMapping("/{clientOrderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable long clientOrderId) {
        return tradingService.getOrder(clientOrderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{clientOrderId}")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @PathVariable long clientOrderId,
            @RequestParam String symbol,
            @RequestParam String exchange) {
        tradingService.cancelOrder(clientOrderId, symbol, exchange);
        return ResponseEntity.ok(Map.of("status", "cancel_requested"));
    }
}
