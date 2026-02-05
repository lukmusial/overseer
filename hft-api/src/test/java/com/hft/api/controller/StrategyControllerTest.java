package com.hft.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.algo.base.AlgorithmState;
import com.hft.api.dto.CreateStrategyRequest;
import com.hft.api.dto.StrategyDto;
import com.hft.api.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradingService tradingService;

    @Test
    void getStrategies_returnsAllStrategies() throws Exception {
        StrategyDto strategy = createStrategyDto("strat-1", AlgorithmState.RUNNING);
        when(tradingService.getStrategies()).thenReturn(List.of(strategy));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("strat-1"))
                .andExpect(jsonPath("$[0].name").value("Test Momentum"))
                .andExpect(jsonPath("$[0].type").value("momentum"));
    }

    @Test
    void createStrategy_createsNewStrategy() throws Exception {
        CreateStrategyRequest request = new CreateStrategyRequest(
                "My Strategy", "momentum", List.of("AAPL"), "ALPACA",
                Map.of("shortPeriod", 10, "longPeriod", 30)
        );
        StrategyDto created = createStrategyDto("new-strat", AlgorithmState.INITIALIZED);
        when(tradingService.createStrategy(any())).thenReturn(created);

        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("strat-1"));
    }

    @Test
    void createStrategy_validatesRequest() throws Exception {
        CreateStrategyRequest invalidRequest = new CreateStrategyRequest(
                null, "", List.of(), "ALPACA", Map.of()
        );

        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startStrategy_callsTradingService() throws Exception {
        mockMvc.perform(post("/api/strategies/strat-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));

        verify(tradingService).startStrategy("strat-1");
    }

    @Test
    void stopStrategy_callsTradingService() throws Exception {
        mockMvc.perform(post("/api/strategies/strat-1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stopped"));

        verify(tradingService).stopStrategy("strat-1");
    }

    @Test
    void deleteStrategy_callsTradingService() throws Exception {
        mockMvc.perform(delete("/api/strategies/strat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        verify(tradingService).deleteStrategy("strat-1");
    }

    @Test
    void getStrategy_returnsSingleStrategy() throws Exception {
        StrategyDto strategy = createStrategyDto("strat-1", AlgorithmState.RUNNING);
        when(tradingService.getStrategy("strat-1")).thenReturn(Optional.of(strategy));

        mockMvc.perform(get("/api/strategies/strat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("strat-1"));
    }

    @Test
    void getStrategy_returnsNotFoundWhenMissing() throws Exception {
        when(tradingService.getStrategy("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/strategies/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStrategyTypes_returnsAvailableTypes() throws Exception {
        mockMvc.perform(get("/api/strategies/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("momentum"))
                .andExpect(jsonPath("$[1].type").value("meanreversion"));
    }

    private StrategyDto createStrategyDto(String id, AlgorithmState state) {
        StrategyDto.StrategyStatsDto stats = new StrategyDto.StrategyStatsDto(
                0, 0, 0, 0, 0, 0, 0, 0, 0
        );
        return new StrategyDto(
                "strat-1", "Test Momentum", "momentum",
                state, List.of("AAPL"),
                Map.of("shortPeriod", 10),
                0.0, 100, stats
        );
    }
}
