package com.hft.api.controller;

import com.hft.api.dto.EngineStatusDto;
import com.hft.api.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EngineController.class)
class EngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingService tradingService;

    @Test
    void getStatus_returnsEngineStatus() throws Exception {
        EngineStatusDto.PositionSummaryDto positions = new EngineStatusDto.PositionSummaryDto(
                5, 3, 10000, 5000, 15000, 50000
        );
        EngineStatusDto.MetricsSummaryDto metrics = new EngineStatusDto.MetricsSummaryDto(
                100, 90, 80, 5, 5, 0.8, 0.05, 10.0, 5000, 1000000
        );
        EngineStatusDto status = new EngineStatusDto(
                true,                    // running
                System.currentTimeMillis(), // startTime
                5000L,                   // uptimeMillis
                true,                    // tradingEnabled
                null,                    // tradingDisabledReason
                1000,                    // eventsPublished
                4096,                    // ringBufferCapacity
                100,                     // totalOrders
                10,                      // activeOrders
                90,                      // totalOrdersProcessed
                80,                      // totalTradesExecuted
                5,                       // activeStrategies
                3,                       // openPositions
                10,                      // pendingOrders
                positions,
                metrics
        );
        when(tradingService.getEngineStatus()).thenReturn(status);

        mockMvc.perform(get("/api/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.uptimeMillis").value(5000))
                .andExpect(jsonPath("$.tradingEnabled").value(true))
                .andExpect(jsonPath("$.totalOrders").value(100))
                .andExpect(jsonPath("$.activeOrders").value(10))
                .andExpect(jsonPath("$.totalOrdersProcessed").value(90))
                .andExpect(jsonPath("$.totalTradesExecuted").value(80))
                .andExpect(jsonPath("$.activeStrategies").value(5))
                .andExpect(jsonPath("$.openPositions").value(3))
                .andExpect(jsonPath("$.pendingOrders").value(10));
    }

    @Test
    void startEngine_callsTradingService() throws Exception {
        mockMvc.perform(post("/api/engine/start"))
                .andExpect(status().isOk());

        verify(tradingService).startEngine();
    }

    @Test
    void stopEngine_callsTradingService() throws Exception {
        mockMvc.perform(post("/api/engine/stop"))
                .andExpect(status().isOk());

        verify(tradingService).stopEngine();
    }
}
