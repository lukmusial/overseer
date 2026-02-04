package com.hft.api.controller;

import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.SymbolDto;
import com.hft.api.service.ChartDataService;
import com.hft.api.service.ExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeController.class)
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeService exchangeService;

    @MockBean
    private ChartDataService chartDataService;

    @Test
    void getExchangeStatus_returnsAllExchanges() throws Exception {
        List<ExchangeStatusDto> statuses = List.of(
                new ExchangeStatusDto("ALPACA", "Alpaca Markets", "stub", true, true, System.currentTimeMillis(), null),
                new ExchangeStatusDto("BINANCE", "Binance", "stub", true, false, null, "API credentials not configured")
        );
        when(exchangeService.getExchangeStatus()).thenReturn(statuses);

        mockMvc.perform(get("/api/exchanges/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].exchange").value("ALPACA"))
                .andExpect(jsonPath("$[0].connected").value(true))
                .andExpect(jsonPath("$[1].exchange").value("BINANCE"))
                .andExpect(jsonPath("$[1].authenticated").value(false));
    }

    @Test
    void getExchangeStatus_forSpecificExchange_returnsStatus() throws Exception {
        ExchangeStatusDto status = new ExchangeStatusDto(
                "ALPACA", "Alpaca Markets (Paper)", "paper", true, true, System.currentTimeMillis(), null
        );
        when(exchangeService.getExchangeStatus("ALPACA")).thenReturn(status);

        mockMvc.perform(get("/api/exchanges/status/ALPACA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value("ALPACA"))
                .andExpect(jsonPath("$.name").value("Alpaca Markets (Paper)"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void getExchangeStatus_forUnknownExchange_returnsNotFound() throws Exception {
        when(exchangeService.getExchangeStatus("UNKNOWN")).thenReturn(null);

        mockMvc.perform(get("/api/exchanges/status/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSymbols_returnsSymbolList() throws Exception {
        List<SymbolDto> symbols = List.of(
                SymbolDto.equity("AAPL", "Apple Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("GOOGL", "Alphabet Inc.", "ALPACA", true, true, true),
                SymbolDto.equity("MSFT", "Microsoft Corporation", "ALPACA", true, true, false)
        );
        when(exchangeService.getSymbols("ALPACA")).thenReturn(symbols);

        mockMvc.perform(get("/api/exchanges/ALPACA/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetClass").value("equity"))
                .andExpect(jsonPath("$[0].tradable").value(true))
                .andExpect(jsonPath("$[1].symbol").value("GOOGL"))
                .andExpect(jsonPath("$[2].shortable").value(false));
    }

    @Test
    void getSymbols_forCryptoExchange_returnsSymbols() throws Exception {
        List<SymbolDto> symbols = List.of(
                SymbolDto.crypto("BTCUSDT", "BTC/USDT", "BINANCE", "BTC", "USDT", true),
                SymbolDto.crypto("ETHUSDT", "ETH/USDT", "BINANCE", "ETH", "USDT", true)
        );
        when(exchangeService.getSymbols("BINANCE")).thenReturn(symbols);

        mockMvc.perform(get("/api/exchanges/BINANCE/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$[0].baseAsset").value("BTC"))
                .andExpect(jsonPath("$[0].quoteAsset").value("USDT"))
                .andExpect(jsonPath("$[0].assetClass").value("crypto"));
    }

    @Test
    void getSymbols_forUnknownExchange_returnsNotFound() throws Exception {
        when(exchangeService.getSymbols("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/api/exchanges/UNKNOWN/symbols"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshSymbols_returnsUpdatedSymbols() throws Exception {
        List<SymbolDto> symbols = List.of(
                SymbolDto.equity("AAPL", "Apple Inc.", "ALPACA", true, true, true)
        );
        when(exchangeService.refreshSymbols("ALPACA")).thenReturn(symbols);

        mockMvc.perform(post("/api/exchanges/ALPACA/symbols/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        verify(exchangeService).refreshSymbols("ALPACA");
    }

    @Test
    void refreshSymbols_forUnknownExchange_returnsNotFound() throws Exception {
        when(exchangeService.refreshSymbols("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(post("/api/exchanges/UNKNOWN/symbols/refresh"))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchMode_validRequest_returnsUpdatedStatus() throws Exception {
        ExchangeStatusDto status = new ExchangeStatusDto(
                "BINANCE", "Binance (Testnet)", "testnet", true, true, System.currentTimeMillis(), null
        );
        when(exchangeService.switchMode("BINANCE", "testnet")).thenReturn(status);

        mockMvc.perform(put("/api/exchanges/BINANCE/mode")
                        .contentType("application/json")
                        .content("{\"mode\":\"testnet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value("BINANCE"))
                .andExpect(jsonPath("$.mode").value("testnet"))
                .andExpect(jsonPath("$.connected").value(true));

        verify(exchangeService).switchMode("BINANCE", "testnet");
    }

    @Test
    void switchMode_unknownExchange_returnsNotFound() throws Exception {
        when(exchangeService.switchMode(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(put("/api/exchanges/UNKNOWN/mode")
                        .contentType("application/json")
                        .content("{\"mode\":\"stub\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchMode_emptyMode_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/exchanges/BINANCE/mode")
                        .contentType("application/json")
                        .content("{\"mode\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
