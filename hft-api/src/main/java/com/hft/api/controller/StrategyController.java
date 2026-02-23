package com.hft.api.controller;

import com.hft.api.dto.CreateStrategyRequest;
import com.hft.api.dto.StrategyDto;
import com.hft.api.service.TradingService;
import com.hft.core.model.TradingPeriod;
import com.hft.core.model.TradingPeriodDetector;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final TradingService tradingService;
    private final TradingPeriodDetector tradingPeriodDetector;

    public StrategyController(TradingService tradingService) {
        this.tradingService = tradingService;
        this.tradingPeriodDetector = new TradingPeriodDetector();
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
                                Map.of("name", "maxPositionSize", "type", "double", "default", 1000, "description", "Maximum position size in asset quantity (shares/coins)"),
                                Map.of("name", "maxPositionNotional", "type", "long", "default", 0, "description", "Maximum position value in dollars (0 = no limit)"),
                                Map.of("name", "maxOrderSize", "type", "double", "default", 1000, "description", "Maximum quantity per individual order (shares/coins)"),
                                Map.of("name", "maxOrderNotional", "type", "long", "default", 500000, "description", "Maximum dollar value per individual order")
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
                                Map.of("name", "maxPositionSize", "type", "double", "default", 1000, "description", "Maximum position size in asset quantity (shares/coins)"),
                                Map.of("name", "maxPositionNotional", "type", "long", "default", 0, "description", "Maximum position value in dollars (0 = no limit)"),
                                Map.of("name", "maxOrderSize", "type", "double", "default", 1000, "description", "Maximum quantity per individual order (shares/coins)"),
                                Map.of("name", "maxOrderNotional", "type", "long", "default", 500000, "description", "Maximum dollar value per individual order")
                        )
                ),
                Map.of(
                        "type", "ema_adx_rsi",
                        "name", "EMA + ADX + RSI",
                        "description", "Trend following with EMA crossover, ADX trend strength, and RSI confirmation",
                        "parameters", List.of(
                                Map.of("name", "fastEmaPeriod", "type", "int", "default", 9, "description", "Fast EMA period"),
                                Map.of("name", "slowEmaPeriod", "type", "int", "default", 21, "description", "Slow EMA period"),
                                Map.of("name", "adxPeriod", "type", "int", "default", 14, "description", "ADX smoothing period"),
                                Map.of("name", "adxThreshold", "type", "double", "default", 25.0, "description", "Minimum ADX for entry"),
                                Map.of("name", "rsiPeriod", "type", "int", "default", 14, "description", "RSI lookback period"),
                                Map.of("name", "rsiBullThreshold", "type", "double", "default", 55.0, "description", "RSI threshold for bullish confirmation"),
                                Map.of("name", "rsiBearThreshold", "type", "double", "default", 45.0, "description", "RSI threshold for bearish confirmation"),
                                Map.of("name", "maxPositionSize", "type", "double", "default", 1000, "description", "Maximum position size in asset quantity (shares/coins)"),
                                Map.of("name", "maxPositionNotional", "type", "long", "default", 0, "description", "Maximum position value in dollars (0 = no limit)"),
                                Map.of("name", "maxOrderSize", "type", "double", "default", 1000, "description", "Maximum quantity per individual order (shares/coins)"),
                                Map.of("name", "maxOrderNotional", "type", "long", "default", 500000, "description", "Maximum dollar value per individual order")
                        )
                ),
                Map.of(
                        "type", "bollinger_squeeze",
                        "name", "Bollinger Squeeze",
                        "description", "Detects BB inside KC squeeze, trades breakout direction via MACD histogram",
                        "parameters", List.of(
                                Map.of("name", "bbPeriod", "type", "int", "default", 20, "description", "Bollinger Band SMA period"),
                                Map.of("name", "bbStdDev", "type", "double", "default", 2.5, "description", "BB standard deviation multiplier"),
                                Map.of("name", "kcPeriod", "type", "int", "default", 20, "description", "Keltner Channel EMA period"),
                                Map.of("name", "kcAtrPeriod", "type", "int", "default", 14, "description", "KC ATR period"),
                                Map.of("name", "kcMultiplier", "type", "double", "default", 2.0, "description", "KC ATR multiplier"),
                                Map.of("name", "macdFast", "type", "int", "default", 8, "description", "MACD fast EMA period"),
                                Map.of("name", "macdSlow", "type", "int", "default", 17, "description", "MACD slow EMA period"),
                                Map.of("name", "macdSignal", "type", "int", "default", 9, "description", "MACD signal line period"),
                                Map.of("name", "maxPositionSize", "type", "double", "default", 1000, "description", "Maximum position size in asset quantity (shares/coins)"),
                                Map.of("name", "maxPositionNotional", "type", "long", "default", 0, "description", "Maximum position value in dollars (0 = no limit)"),
                                Map.of("name", "maxOrderSize", "type", "double", "default", 1000, "description", "Maximum quantity per individual order (shares/coins)"),
                                Map.of("name", "maxOrderNotional", "type", "long", "default", 500000, "description", "Maximum dollar value per individual order")
                        )
                ),
                Map.of(
                        "type", "vwap_mean_reversion",
                        "name", "VWAP Mean Reversion",
                        "description", "Mean reversion around session VWAP with sigma deviation bands",
                        "parameters", List.of(
                                Map.of("name", "upperSigma", "type", "double", "default", 2.3, "description", "Upper band sigma multiplier"),
                                Map.of("name", "lowerSigma", "type", "double", "default", 2.3, "description", "Lower band sigma multiplier"),
                                Map.of("name", "exitSigma", "type", "double", "default", 0.5, "description", "Exit threshold sigma"),
                                Map.of("name", "maxHoldMinutes", "type", "long", "default", 240, "description", "Maximum hold time in minutes"),
                                Map.of("name", "volumeFilterMultiplier", "type", "double", "default", 2.0, "description", "Volume filter multiplier"),
                                Map.of("name", "maxPositionSize", "type", "double", "default", 1000, "description", "Maximum position size in asset quantity (shares/coins)"),
                                Map.of("name", "maxPositionNotional", "type", "long", "default", 0, "description", "Maximum position value in dollars (0 = no limit)"),
                                Map.of("name", "maxOrderSize", "type", "double", "default", 1000, "description", "Maximum quantity per individual order (shares/coins)"),
                                Map.of("name", "maxOrderNotional", "type", "long", "default", 500000, "description", "Maximum dollar value per individual order")
                        )
                )
        ));
    }

    @GetMapping("/trading-periods")
    public ResponseEntity<List<Map<String, Object>>> getTradingPeriods() {
        List<Map<String, Object>> periods = Arrays.stream(TradingPeriod.values())
                .map(p -> Map.<String, Object>of(
                        "name", p.name(),
                        "startTime", p.getStartTime().toString(),
                        "endTime", p.getEndTime().toString(),
                        "positionMultiplier", p.getPositionMultiplier(),
                        "recommendedStrategies", p.getRecommendedStrategies()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(periods);
    }

    @GetMapping("/trading-periods/current")
    public ResponseEntity<Map<String, Object>> getCurrentTradingPeriod() {
        TradingPeriod current = tradingPeriodDetector.currentPeriod();
        return ResponseEntity.ok(Map.of(
                "name", current.name(),
                "startTime", current.getStartTime().toString(),
                "endTime", current.getEndTime().toString(),
                "positionMultiplier", current.getPositionMultiplier(),
                "recommendedStrategies", current.getRecommendedStrategies()
        ));
    }
}
