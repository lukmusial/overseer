package com.hft.api.controller;

import com.hft.api.dto.CreateStrategyRequest;
import com.hft.api.dto.StrategyDto;
import com.hft.api.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final TradingService tradingService;

    public StrategyController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping
    public ResponseEntity<?> createStrategy(@Valid @RequestBody CreateStrategyRequest request) {
        try {
            StrategyDto strategy = tradingService.createStrategy(request);
            return ResponseEntity.ok(strategy);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<StrategyDto>> getStrategies() {
        return ResponseEntity.ok(tradingService.getStrategies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StrategyDto> getStrategy(@PathVariable String id) {
        return tradingService.getStrategy(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> startStrategy(@PathVariable String id) {
        tradingService.startStrategy(id);
        return ResponseEntity.ok(Map.of("status", "started", "id", id));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stopStrategy(@PathVariable String id) {
        tradingService.stopStrategy(id);
        return ResponseEntity.ok(Map.of("status", "stopped", "id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteStrategy(@PathVariable String id) {
        tradingService.deleteStrategy(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllStrategies() {
        int count = tradingService.deleteAllStrategies();
        return ResponseEntity.ok(Map.of("status", "deleted", "count", count));
    }

    @GetMapping("/types")
    public ResponseEntity<List<Map<String, Object>>> getStrategyTypes() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "type", "momentum",
                        "name", "Momentum",
                        "description", "Trend-following strategy using EMA crossovers",
                        "parameters", List.of(
                                Map.of("name", "shortPeriod", "type", "int", "default", 10, "description", "Short EMA period"),
                                Map.of("name", "longPeriod", "type", "int", "default", 30, "description", "Long EMA period"),
                                Map.of("name", "signalThreshold", "type", "double", "default", 0.02, "description", "Minimum signal strength"),
                                Map.of("name", "maxPositionSize", "type", "long", "default", 1000, "description", "Maximum position size")
                        )
                ),
                Map.of(
                        "type", "meanreversion",
                        "name", "Mean Reversion",
                        "description", "Statistical arbitrage strategy using Bollinger Bands",
                        "parameters", List.of(
                                Map.of("name", "lookbackPeriod", "type", "int", "default", 20, "description", "Lookback period for statistics"),
                                Map.of("name", "entryZScore", "type", "double", "default", 2.0, "description", "Z-score threshold for entry"),
                                Map.of("name", "exitZScore", "type", "double", "default", 0.5, "description", "Z-score threshold for exit"),
                                Map.of("name", "maxPositionSize", "type", "long", "default", 1000, "description", "Maximum position size")
                        )
                )
        ));
    }
}
