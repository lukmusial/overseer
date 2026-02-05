package com.hft.api.controller;

import com.hft.api.dto.PositionDto;
import com.hft.api.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PositionController.class)
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingService tradingService;

    @Test
    void getAllPositions_returnsAllPositions() throws Exception {
        PositionDto position = new PositionDto(
                "AAPL", "ALPACA",
                100, 15000, 15500, 1550000,
                5000, 50000, 2000,
                100, true, false, false
        );
        when(tradingService.getAllPositions()).thenReturn(List.of(position));

        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].isLong").value(true));
    }

    @Test
    void getActivePositions_returnsNonFlatPositions() throws Exception {
        PositionDto openPosition = new PositionDto(
                "AAPL", "ALPACA",
                100, 15000, 15500, 1550000,
                0, 50000, 0,
                100, true, false, false
        );
        when(tradingService.getActivePositions()).thenReturn(List.of(openPosition));

        mockMvc.perform(get("/api/positions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isFlat").value(false));
    }

    @Test
    void getPosition_returnsSinglePosition() throws Exception {
        PositionDto position = new PositionDto(
                "AAPL", "ALPACA",
                100, 15000, 15500, 1550000,
                5000, 50000, 2000,
                100, true, false, false
        );
        when(tradingService.getPosition("AAPL", "ALPACA")).thenReturn(Optional.of(position));

        mockMvc.perform(get("/api/positions/AAPL")
                        .param("exchange", "ALPACA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.exchange").value("ALPACA"));
    }

    @Test
    void getPosition_returnsNotFoundWhenMissing() throws Exception {
        when(tradingService.getPosition("UNKNOWN", "ALPACA")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/positions/UNKNOWN")
                        .param("exchange", "ALPACA"))
                .andExpect(status().isNotFound());
    }
}
