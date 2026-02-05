package com.hft.api.controller;

import com.hft.api.dto.EngineStatusDto;
import com.hft.api.dto.RiskLimitsDto;
import com.hft.api.dto.UpdateRiskLimitsRequest;
import com.hft.api.service.TradingService;
import com.hft.engine.service.RiskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/engine")
public class EngineController {

    private final TradingService tradingService;

    public EngineController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping("/status")
    public ResponseEntity<EngineStatusDto> getStatus() {
        return ResponseEntity.ok(tradingService.getEngineStatus());
    }

    @GetMapping("/risk-limits")
    public ResponseEntity<RiskLimitsDto> getRiskLimits() {
        return ResponseEntity.ok(tradingService.getRiskLimits());
    }

    @PutMapping("/risk-limits")
    public ResponseEntity<RiskLimitsDto> updateRiskLimits(@RequestBody UpdateRiskLimitsRequest request) {
        RiskManager.RiskLimits newLimits = new RiskManager.RiskLimits(
                request.maxOrderSize(),
                request.maxOrderNotional(),
                request.maxPositionSize(),
                request.maxOrdersPerDay(),
                request.maxDailyNotional(),
                request.maxDailyLoss(),
                request.maxDrawdownPerPosition(),
                request.maxUnrealizedLossPerPosition(),
                request.maxNetExposure()
        );
        return ResponseEntity.ok(tradingService.updateRiskLimits(newLimits));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start() {
        tradingService.startEngine();
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stop() {
        tradingService.stopEngine();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @PostMapping("/reset-daily")
    public ResponseEntity<Map<String, String>> resetDailyCounters() {
        tradingService.resetDailyCounters();
        return ResponseEntity.ok(Map.of("status", "reset"));
    }

    @PostMapping("/trading/enable")
    public ResponseEntity<Map<String, Object>> enableTrading() {
        tradingService.enableTrading();
        return ResponseEntity.ok(Map.of("tradingEnabled", true));
    }

    @PostMapping("/trading/disable")
    public ResponseEntity<Map<String, Object>> disableTrading(@RequestBody Map<String, String> request) {
        String reason = request.getOrDefault("reason", "Manual disable");
        tradingService.disableTrading(reason);
        return ResponseEntity.ok(Map.of("tradingEnabled", false, "reason", reason));
    }
}
