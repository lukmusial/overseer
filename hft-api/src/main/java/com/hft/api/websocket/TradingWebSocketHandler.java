package com.hft.api.websocket;

import com.hft.api.dto.EngineStatusDto;
import com.hft.api.dto.OrderDto;
import com.hft.api.dto.PositionDto;
import com.hft.api.service.TradingService;
import com.hft.core.model.Order;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.Position;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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
     * Broadcasts order updates in real-time.
     */
    private void onOrderUpdate(Order order) {
        OrderDto dto = OrderDto.from(order);
        messagingTemplate.convertAndSend("/topic/orders", dto);

        // Dispatch fill to strategy so it can update its stats
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            tradingService.dispatchFillToStrategy(order);
        }
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
