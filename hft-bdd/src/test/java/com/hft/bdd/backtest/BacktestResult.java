package com.hft.bdd.backtest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete results from a backtest run.
 */
public record BacktestResult(
        BacktestConfig config,
        double finalPortfolioValue,
        double totalReturnPercent,
        double maxDrawdownPercent,
        int totalTrades,
        int winningTrades,
        double totalCommission,
        double sharpeRatio,
        double profitFactor,
        List<DailySnapshot> dailySnapshots,
        List<TradeRecord> trades,
        long dataPointsProcessed
) {
    public double winRatePercent() {
        return totalTrades > 0 ? (double) winningTrades / totalTrades * 100.0 : 0.0;
    }

    public double avgDailyReturnPercent() {
        if (dailySnapshots.isEmpty()) return 0;
        return dailySnapshots.stream()
                .mapToDouble(DailySnapshot::dailyReturnPercent)
                .average()
                .orElse(0);
    }

    public record DailySnapshot(LocalDate date, double portfolioValue, double dailyReturnPercent) {}

    public record TradeRecord(
            LocalDateTime time,
            String action,
            double price,
            double quantity,
            double positionValueAfter,
            double portfolioValueAfter
    ) {}

    public String summary() {
        return String.format(
                "%-25s | Final: $%,.2f | Return: %+.2f%% | MaxDD: %.2f%% | Trades: %d | WinRate: %.1f%% | Sharpe: %.2f | PF: %.2f | Commission: $%.2f",
                config.label(), finalPortfolioValue, totalReturnPercent, maxDrawdownPercent,
                totalTrades, winRatePercent(), sharpeRatio, profitFactor, totalCommission
        );
    }
}
