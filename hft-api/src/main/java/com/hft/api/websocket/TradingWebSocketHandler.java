package com.hft.api.websocket;

import com.hft.api.dto.EngineStatusDto;

import com.hft.api.dto.PositionDto;
import com.hft.api.dto.StrategyDto;
import com.hft.api.service.TradingService;
import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Position;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Component
public class TradingWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final TradingService tradingService;

    public TradingWebSocketHandler(SimpMessagingTemplate messagingTemplate, TradingService tradingService) {
        this.messagingTemplate = messagingTemplate;
        this.tradingService = tradingService;
    }

    @PostConstruct
    public void init() {
        // Register listeners for real-time updates
        tradingService.getTradingEngine().getOrderManager().addOrderListener(this::onOrderUpdate);
        tradingService.getTradingEngine().getPositionManager().addPositionListener(this::onPositionUpdate);
    }

    /**
     * Broadcasts engine status every second.
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastEngineStatus() {
        EngineStatusDto status = tradingService.getEngineStatus();
        messagingTemplate.convertAndSend("/topic/engine/status", status);
    }

    /**
     * Broadcasts all strategy updates every 2 seconds.
     * This ensures UI stays in sync with strategy stats.
     */
    @Scheduled(fixedRate = 2000)
    public void broadcastStrategies() {
        List<StrategyDto> strategies = tradingService.getStrategies();
        for (StrategyDto strategy : strategies) {
            messagingTemplate.convertAndSend("/topic/strategies", strategy);
        }
    }

    /**
     * Broadcasts order updates in real-time.
     */
    private void onOrderUpdate(Order order) {
        var dto = tradingService.toOrderDto(order);
        messagingTemplate.convertAndSend("/topic/orders", dto);

        // Dispatch fill to strategy so it can update its stats
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            tradingService.dispatchFillToStrategy(order);
            // Broadcast updated strategy stats immediately after fill
            broadcastStrategyUpdate(order.getStrategyId());
        }
    }

    /**
     * Broadcasts a single strategy update.
     */
    private void broadcastStrategyUpdate(String strategyId) {
        if (strategyId == null) {
            return;
        }
        tradingService.getStrategy(strategyId).ifPresent(strategy ->
            messagingTemplate.convertAndSend("/topic/strategies", strategy));
    }

    /**
     * Broadcasts position updates in real-time.
     */
    private void onPositionUpdate(Position position) {
        PositionDto dto = PositionDto.from(position);
        messagingTemplate.convertAndSend("/topic/positions", dto);
    }

    /**
     * Sends a custom notification to all clients.
     */
    public void sendNotification(String type, String message) {
        messagingTemplate.convertAndSend("/topic/notifications",
                new NotificationMessage(type, message, System.currentTimeMillis()));
    }

    public record NotificationMessage(String type, String message, long timestamp) {}
}
