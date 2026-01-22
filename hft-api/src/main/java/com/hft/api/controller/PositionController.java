package com.hft.api.controller;

import com.hft.api.dto.PositionDto;
import com.hft.api.service.TradingService;
import com.hft.engine.service.PositionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final TradingService tradingService;

    public PositionController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping
    public ResponseEntity<List<PositionDto>> getAllPositions() {
        return ResponseEntity.ok(tradingService.getAllPositions());
    }

    @GetMapping("/active")
    public ResponseEntity<List<PositionDto>> getActivePositions() {
        return ResponseEntity.ok(tradingService.getActivePositions());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PositionDto> getPosition(
            @PathVariable String symbol,
            @RequestParam String exchange) {
        return tradingService.getPosition(symbol, exchange)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPositionSummary() {
        PositionManager.PositionSnapshot snapshot = tradingService.getPositionSnapshot();
        return ResponseEntity.ok(Map.of(
                "totalPositions", snapshot.totalPositions(),
                "activePositions", snapshot.activePositions(),
                "realizedPnl", snapshot.realizedPnl(),
                "unrealizedPnl", snapshot.unrealizedPnl(),
                "totalPnl", snapshot.totalPnl(),
                "netExposure", snapshot.netExposure(),
                "grossExposure", Map.of(
                        "long", snapshot.grossExposure().longExposure(),
                        "short", snapshot.grossExposure().shortExposure(),
                        "total", snapshot.grossExposure().total()
                )
        ));
    }
}
