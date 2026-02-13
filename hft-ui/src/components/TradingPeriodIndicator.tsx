import { useState, useEffect } from 'react';
import type { TradingPeriodInfo } from '../types/api';
import { useApi } from '../hooks/useApi';

function timeToMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function periodWidthPercent(period: TradingPeriodInfo): number {
  const start = timeToMinutes(period.startTime);
  const end = timeToMinutes(period.endTime);
  const duration = end > start ? end - start : (1440 - start) + end;
  return (duration / 1440) * 100;
}

function periodLeftPercent(period: TradingPeriodInfo): number {
  return (timeToMinutes(period.startTime) / 1440) * 100;
}

export function TradingPeriodIndicator() {
  const { getCurrentTradingPeriod, getTradingPeriods } = useApi();
  const [currentPeriod, setCurrentPeriod] = useState<TradingPeriodInfo | null>(null);
  const [allPeriods, setAllPeriods] = useState<TradingPeriodInfo[]>([]);
  const [error, setError] = useState(false);

  // Fetch all periods once on mount
  useEffect(() => {
    getTradingPeriods()
      .then(setAllPeriods)
      .catch(() => setError(true));
  }, [getTradingPeriods]);

  // Poll current period every 30 seconds
  useEffect(() => {
    let cancelled = false;

    const fetchCurrent = () => {
      getCurrentTradingPeriod()
        .then((period) => {
          if (!cancelled) {
            setCurrentPeriod(period);
            setError(false);
          }
        })
        .catch(() => {
          if (!cancelled) setError(true);
        });
    };

    fetchCurrent();
    const interval = setInterval(fetchCurrent, 30000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [getCurrentTradingPeriod]);

  if (error || !currentPeriod) {
    return null;
  }

  return (
    <div className="trading-period-indicator">
      <div className="period-info">
        <span className="period-name">[{currentPeriod.name}]</span>
        <span className="period-time">{currentPeriod.startTime}-{currentPeriod.endTime} UTC</span>
        <span className="period-multiplier">{currentPeriod.positionMultiplier}x</span>
        {currentPeriod.recommendedStrategies.length > 0 && (
          <span className="period-strategies">
            {currentPeriod.recommendedStrategies.join(', ')}
          </span>
        )}
      </div>
      {allPeriods.length > 0 && (
        <div className="period-timeline" role="img" aria-label="Trading period timeline">
          {allPeriods.map((period) => (
            <div
              key={period.name}
              className={`period-segment ${period.name === currentPeriod.name ? 'active' : ''}`}
              style={{
                left: `${periodLeftPercent(period)}%`,
                width: `${periodWidthPercent(period)}%`,
              }}
              title={`${period.name} (${period.startTime}-${period.endTime})`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
