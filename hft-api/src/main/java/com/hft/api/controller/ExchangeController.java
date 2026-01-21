package com.hft.api.controller;

import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.service.ExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for exchange connectivity status.
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
}
