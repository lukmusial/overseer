package com.hft.api.controller;

import com.hft.api.dto.ExchangeModeRequest;
import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.SymbolDto;
import com.hft.api.service.ExchangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for exchange connectivity and symbol information.
 */
@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    /**
     * Returns the connectivity status of all configured exchanges.
     */
    @GetMapping("/status")
    public List<ExchangeStatusDto> getExchangeStatus() {
        return exchangeService.getExchangeStatus();
    }

    /**
     * Returns the connectivity status of a specific exchange.
     */
    @GetMapping("/status/{exchange}")
    public ResponseEntity<ExchangeStatusDto> getExchangeStatus(@PathVariable String exchange) {
        ExchangeStatusDto status = exchangeService.getExchangeStatus(exchange);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Switches the runtime mode for an exchange.
     */
    @PutMapping("/{exchange}/mode")
    public ResponseEntity<ExchangeStatusDto> switchMode(
            @PathVariable String exchange,
            @Valid @RequestBody ExchangeModeRequest request) {
        ExchangeStatusDto status = exchangeService.switchMode(exchange, request.mode());
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Returns available trading symbols for a specific exchange.
     */
    @GetMapping("/{exchange}/symbols")
    public ResponseEntity<List<SymbolDto>> getSymbols(@PathVariable String exchange) {
        List<SymbolDto> symbols = exchangeService.getSymbols(exchange);
        if (symbols.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(symbols);
    }

    /**
     * Refreshes and returns available trading symbols for a specific exchange.
     */
    @PostMapping("/{exchange}/symbols/refresh")
    public ResponseEntity<List<SymbolDto>> refreshSymbols(@PathVariable String exchange) {
        List<SymbolDto> symbols = exchangeService.refreshSymbols(exchange);
        if (symbols.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(symbols);
    }
}
