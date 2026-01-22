package com.hft.api.controller;

import com.hft.api.dto.*;
import com.hft.api.service.ChartDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chart data including historical candles,
 * order markers, and strategy trigger ranges.
 */
@RestController
@RequestMapping("/api/chart")
public class ChartController {

    private final ChartDataService chartDataService;

    public ChartController(ChartDataService chartDataService) {
        this.chartDataService = chartDataService;
    }

    /**
     * Get complete chart data for a symbol.
     *
     * @param exchange Exchange name (ALPACA, BINANCE)
     * @param symbol   Symbol ticker (AAPL, BTCUSDT, etc.)
     * @param interval Candle interval (1m, 5m, 15m, 30m, 1h, 4h, 1d)
     * @param periods  Number of candles to return (default 100)
     */
    @GetMapping("/{exchange}/{symbol}")
    public ResponseEntity<ChartDataDto> getChartData(
            @PathVariable String exchange,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5m") String interval,
            @RequestParam(defaultValue = "100") int periods) {

        // Validate parameters
        if (periods < 1 || periods > 1000) {
            periods = 100;
        }

        ChartDataDto data = chartDataService.getChartData(symbol, exchange, interval, periods);
        return ResponseEntity.ok(data);
    }

    /**
     * Get only historical candles for a symbol.
     */
    @GetMapping("/{exchange}/{symbol}/candles")
    public ResponseEntity<List<CandleDto>> getCandles(
            @PathVariable String exchange,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5m") String interval,
            @RequestParam(defaultValue = "100") int periods) {

        if (periods < 1 || periods > 1000) {
            periods = 100;
        }

        List<CandleDto> candles = chartDataService.getHistoricalCandles(symbol, exchange, interval, periods);
        return ResponseEntity.ok(candles);
    }

    /**
     * Get order markers for a symbol.
     */
    @GetMapping("/{exchange}/{symbol}/orders")
    public ResponseEntity<List<OrderMarkerDto>> getOrderMarkers(
            @PathVariable String exchange,
            @PathVariable String symbol) {

        List<OrderMarkerDto> orders = chartDataService.getOrderMarkers(symbol, exchange);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get strategy trigger ranges for a symbol.
     */
    @GetMapping("/{exchange}/{symbol}/triggers")
    public ResponseEntity<List<TriggerRangeDto>> getTriggerRanges(
            @PathVariable String exchange,
            @PathVariable String symbol) {

        List<TriggerRangeDto> ranges = chartDataService.getTriggerRanges(symbol, exchange);
        return ResponseEntity.ok(ranges);
    }

    /**
     * Clear the historical data cache (forces regeneration).
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Void> clearCache() {
        chartDataService.clearCache();
        return ResponseEntity.ok().build();
    }
}
