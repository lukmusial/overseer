import { useEffect, useRef, useState, useCallback } from 'react';
import { createChart, CandlestickSeries, IChartApi, ISeriesApi, CandlestickData, Time, createSeriesMarkers, ISeriesMarkersPluginApi, SeriesMarker } from 'lightweight-charts';
import { useApi } from '../hooks/useApi';
import type { ChartData, TriggerRange, OrderMarker, Strategy, Quote, Order } from '../types/api';

interface CandlestickChartProps {
  exchange: string;
  symbol: string;
  strategies?: Strategy[];
  refreshKey?: number;
  subscribe?: <T>(destination: string, callback: (data: T) => void) => () => void;
}

const INTERVALS = ['1m', '5m', '15m', '30m', '1h', '4h', '1d'];
const PERIOD_OPTIONS = [50, 100, 200, 500];

// Get interval in milliseconds
function getIntervalMs(interval: string): number {
  const match = interval.match(/^(\d+)([mhd])$/);
  if (!match) return 60000; // default 1 minute
  const value = parseInt(match[1]);
  const unit = match[2];
  switch (unit) {
    case 'm': return value * 60 * 1000;
    case 'h': return value * 60 * 60 * 1000;
    case 'd': return value * 24 * 60 * 60 * 1000;
    default: return 60000;
  }
}

// Get candle start time for a given timestamp (returns seconds for lightweight-charts)
// Aligns timestamp to interval boundary (floor division)
// Uses integer math to avoid floating point issues
function getCandleTime(timestampMs: number, intervalMs: number): number {
  const intervalSec = intervalMs / 1000;
  const timestampSec = Math.floor(timestampMs / 1000);
  return Math.floor(timestampSec / intervalSec) * intervalSec;
}

export function CandlestickChart({ exchange, symbol, strategies = [], refreshKey, subscribe }: CandlestickChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candlestickSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const markersPluginRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null);
  const currentCandleRef = useRef<CandlestickData<Time> | null>(null);
  const ordersByTimeRef = useRef<Map<number, OrderMarker[]>>(new Map());

  const [interval, setInterval] = useState('5m');
  const [periods, setPeriods] = useState(100);
  const [chartData, setChartData] = useState<ChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedStrategy, setSelectedStrategy] = useState<string>('all');
  const [lastQuote, setLastQuote] = useState<Quote | null>(null);
  const [showThresholds, setShowThresholds] = useState(true);

  const { getChartData } = useApi();

  // Fetch chart data
  const fetchData = useCallback(async () => {
    if (!exchange || !symbol) return;

    setLoading(true);
    setError(null);

    try {
      const data = await getChartData(exchange, symbol, interval, periods);
      setChartData(data);
      // Set current candle reference to the last candle
      if (data.candles.length > 0) {
        const lastCandle = data.candles[data.candles.length - 1];
        currentCandleRef.current = {
          time: lastCandle.time as Time,
          open: lastCandle.open,
          high: lastCandle.high,
          low: lastCandle.low,
          close: lastCandle.close,
        };
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch chart data');
    } finally {
      setLoading(false);
    }
  }, [exchange, symbol, interval, periods, getChartData, refreshKey]);

  // Initialize chart
  useEffect(() => {
    if (!chartContainerRef.current) return;

    // Pip-Boy theme colors
    const pipGreen = '#18dc18';
    const pipGreenDim = '#18c018';
    const pipGreenDark = '#0a800a';
    const pipBg = '#0a0a0a';
    const pipRed = '#f04848';

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height: 400,
      layout: {
        background: { color: pipBg },
        textColor: pipGreenDim,
      },
      grid: {
        vertLines: { color: pipGreenDark },
        horzLines: { color: pipGreenDark },
      },
      crosshair: {
        mode: 1,
        vertLine: {
          color: pipGreenDim,
          labelBackgroundColor: pipGreenDark,
        },
        horzLine: {
          color: pipGreenDim,
          labelBackgroundColor: pipGreenDark,
        },
      },
      rightPriceScale: {
        borderColor: pipGreenDark,
      },
      timeScale: {
        borderColor: pipGreenDark,
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: pipGreen,
      downColor: pipRed,
      borderVisible: false,
      wickUpColor: pipGreen,
      wickDownColor: pipRed,
    });

    // Create markers plugin
    const markersPlugin = createSeriesMarkers(candlestickSeries, []);

    chartRef.current = chart;
    candlestickSeriesRef.current = candlestickSeries;
    markersPluginRef.current = markersPlugin;

    // Show tooltip on crosshair hover over order markers
    chart.subscribeCrosshairMove((param) => {
      const tooltip = tooltipRef.current;
      if (!tooltip) return;

      if (!param.time || !param.point) {
        tooltip.style.display = 'none';
        return;
      }

      const orders = ordersByTimeRef.current.get(param.time as number);
      if (!orders || orders.length === 0) {
        tooltip.style.display = 'none';
        return;
      }

      tooltip.innerHTML = orders.map(o =>
        `<div>${o.side} ${o.quantity} @ ${o.price.toFixed(2)}<br><span class="order-tooltip-status">${o.status}</span></div>`
      ).join('');
      tooltip.style.display = 'block';

      const containerRect = chartContainerRef.current?.getBoundingClientRect();
      if (containerRect) {
        const tooltipWidth = tooltip.offsetWidth;
        let left = param.point.x + 12;
        if (left + tooltipWidth > containerRect.width) {
          left = param.point.x - tooltipWidth - 12;
        }
        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${param.point.y - 12}px`;
      }
    });

    // Handle resize
    const handleResize = () => {
      if (chartContainerRef.current) {
        chart.applyOptions({ width: chartContainerRef.current.clientWidth });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current = null;
      candlestickSeriesRef.current = null;
      markersPluginRef.current = null;
    };
  }, []);

  // Fetch data when parameters change
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Subscribe to real-time quote updates
  useEffect(() => {
    if (!subscribe || !exchange || !symbol) return;

    const unsubscribe = subscribe<Quote>(`/topic/quotes/${exchange}/${symbol}`, (quote) => {
      setLastQuote(quote);

      // Update the chart with the new quote
      if (!candlestickSeriesRef.current || !currentCandleRef.current) return;

      const intervalMs = getIntervalMs(interval);
      const currentCandleTime = currentCandleRef.current.time as number;

      // Calculate which candle this quote belongs to (aligned to interval boundary)
      const quoteCandleTime = getCandleTime(quote.timestamp, intervalMs);

      if (quoteCandleTime === currentCandleTime) {
        // Quote belongs to current candle - update it
        const updated: CandlestickData<Time> = {
          time: currentCandleTime as Time,
          open: currentCandleRef.current.open,
          high: Math.max(currentCandleRef.current.high, quote.midPrice),
          low: Math.min(currentCandleRef.current.low, quote.midPrice),
          close: quote.midPrice,
        };
        currentCandleRef.current = updated;
        candlestickSeriesRef.current.update(updated);
      } else if (quoteCandleTime > currentCandleTime) {
        // Quote belongs to a new candle - create it
        const newCandle: CandlestickData<Time> = {
          time: quoteCandleTime as Time,
          open: quote.midPrice,
          high: quote.midPrice,
          low: quote.midPrice,
          close: quote.midPrice,
        };
        currentCandleRef.current = newCandle;
        candlestickSeriesRef.current.update(newCandle);
      }
      // Ignore quotes for past candles (shouldn't happen in real-time)
    });

    return unsubscribe;
  }, [subscribe, exchange, symbol, interval]);

  // Subscribe to real-time order updates
  useEffect(() => {
    if (!subscribe || !exchange || !symbol) return;

    const unsubscribe = subscribe<Order>('/topic/orders', (order) => {
      if (order.exchange !== exchange || order.symbol !== symbol) return;

      const scale = order.priceScale > 0 ? order.priceScale : 100;
      const price = (order.averageFilledPrice > 0 ? order.averageFilledPrice : order.price) / scale;
      const timeSeconds = Math.floor(order.createdAt / 1000);

      const marker: OrderMarker = {
        time: timeSeconds,
        price,
        side: order.side,
        quantity: order.quantity,
        status: order.status,
        strategyId: order.strategyId,
        orderId: String(order.clientOrderId),
      };

      setChartData(prev => {
        if (!prev) return prev;
        return { ...prev, orders: [...prev.orders, marker] };
      });
    });

    return unsubscribe;
  }, [subscribe, exchange, symbol]);

  // Update chart when data changes
  useEffect(() => {
    if (!chartData || !candlestickSeriesRef.current || !chartRef.current) return;

    // Convert candles to lightweight-charts format
    const candleData: CandlestickData<Time>[] = chartData.candles.map(c => ({
      time: c.time as Time,
      open: c.open,
      high: c.high,
      low: c.low,
      close: c.close,
    }));

    candlestickSeriesRef.current.setData(candleData);

    // Add order markers
    const filteredOrders = selectedStrategy === 'all'
      ? chartData.orders
      : chartData.orders.filter(o => o.strategyId === selectedStrategy);

    // Build time -> orders lookup for tooltip, keyed by candle time
    // The crosshair reports the candle time, so we snap each order to its nearest candle
    const candleTimes = chartData.candles.map(c => c.time);
    const ordersByTime = new Map<number, OrderMarker[]>();
    filteredOrders.forEach((order: OrderMarker) => {
      // Find the closest candle time <= order time
      let snappedTime = order.time;
      for (let i = candleTimes.length - 1; i >= 0; i--) {
        if (candleTimes[i] <= order.time) {
          snappedTime = candleTimes[i];
          break;
        }
      }
      const existing = ordersByTime.get(snappedTime) || [];
      existing.push(order);
      ordersByTime.set(snappedTime, existing);
    });
    ordersByTimeRef.current = ordersByTime;

    const markers: SeriesMarker<Time>[] = filteredOrders.map((order: OrderMarker) => ({
      time: order.time as Time,
      position: 'inBar' as const,
      color: '#4a90d9',
      shape: 'circle' as const,
      text: '',
    }));

    if (markersPluginRef.current) {
      markersPluginRef.current.setMarkers(markers);
    }

    // Draw trigger ranges as price lines (only when thresholds visible)
    const chart = chartRef.current;
    const pipGreen = '#18dc18';
    const pipRed = '#f04848';

    if (showThresholds) {
      const filteredRanges = selectedStrategy === 'all'
        ? chartData.triggerRanges
        : chartData.triggerRanges.filter(r => r.strategyId === selectedStrategy);

      filteredRanges.forEach((range: TriggerRange) => {
        if (range.buyTriggerLow !== null) {
          candlestickSeriesRef.current?.createPriceLine({
            price: range.buyTriggerLow,
            color: pipGreen,
            lineWidth: 1,
            lineStyle: 2, // Dashed
            axisLabelVisible: true,
            title: `Buy Low (${range.strategyName})`,
          });
        }
        if (range.buyTriggerHigh !== null) {
          candlestickSeriesRef.current?.createPriceLine({
            price: range.buyTriggerHigh,
            color: pipGreen,
            lineWidth: 1,
            lineStyle: 2,
            axisLabelVisible: true,
            title: `Buy High (${range.strategyName})`,
          });
        }
        if (range.sellTriggerLow !== null) {
          candlestickSeriesRef.current?.createPriceLine({
            price: range.sellTriggerLow,
            color: pipRed,
            lineWidth: 1,
            lineStyle: 2,
            axisLabelVisible: true,
            title: `Sell Low (${range.strategyName})`,
          });
        }
        if (range.sellTriggerHigh !== null) {
          candlestickSeriesRef.current?.createPriceLine({
            price: range.sellTriggerHigh,
            color: pipRed,
            lineWidth: 1,
            lineStyle: 2,
            axisLabelVisible: true,
            title: `Sell High (${range.strategyName})`,
          });
        }
      });
    }

    // Fit content
    chart.timeScale().fitContent();
  }, [chartData, selectedStrategy, showThresholds]);

  // Get strategies that have activity on this symbol
  const relevantStrategies = strategies.filter(s => s.symbols.includes(symbol));

  const dataSourceLabel = chartData?.dataSource === 'live'
    ? 'Live Market Data'
    : chartData?.dataSource === 'stub'
    ? 'Simulated Data'
    : chartData?.dataSource
    ? `${chartData.dataSource} Data`
    : null;

  const dataSourceClass = chartData?.dataSource === 'live'
    ? 'data-source-live'
    : chartData?.dataSource === 'stub'
    ? 'data-source-stub'
    : 'data-source-other';

  return (
    <div className="candlestick-chart">
      <div className="chart-header">
        <h3>
          {symbol} ({exchange})
          {dataSourceLabel && (
            <span className={`data-source-badge ${dataSourceClass}`}>
              {dataSourceLabel}
            </span>
          )}
          {lastQuote && (
            <span className="live-price">
              ${lastQuote.midPrice.toFixed(2)}
              <span className="live-indicator" title="Real-time updates active" />
            </span>
          )}
        </h3>
        <div className="chart-controls">
          <div className="control-group">
            <label>Interval:</label>
            <select value={interval} onChange={e => setInterval(e.target.value)}>
              {INTERVALS.map(i => (
                <option key={i} value={i}>{i}</option>
              ))}
            </select>
          </div>
          <div className="control-group">
            <label>Periods:</label>
            <select value={periods} onChange={e => setPeriods(Number(e.target.value))}>
              {PERIOD_OPTIONS.map(p => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div className="control-group">
            <label>Strategy:</label>
            <select value={selectedStrategy} onChange={e => setSelectedStrategy(e.target.value)}>
              <option value="all">All Strategies</option>
              {relevantStrategies.map(s => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <button onClick={fetchData} disabled={loading} className="refresh-btn">
            {loading ? 'Loading...' : 'Refresh'}
          </button>
          <button
            className={`threshold-toggle${showThresholds ? ' active' : ''}`}
            onClick={() => setShowThresholds(prev => !prev)}
          >
            Thresholds
          </button>
        </div>
      </div>

      {error && <div className="chart-error">{error}</div>}

      <div className="chart-body">
        <div className="chart-container" style={{ position: 'relative' }}>
          <div ref={chartContainerRef} />
          <div ref={tooltipRef} className="order-tooltip" />
        </div>

        {showThresholds && chartData && chartData.triggerRanges.length > 0 && (() => {
          const filteredRanges = selectedStrategy === 'all'
            ? chartData.triggerRanges
            : chartData.triggerRanges.filter(r => r.strategyId === selectedStrategy);
          return (
            <div className="thresholds-panel">
              <div className="thresholds-panel-header" onClick={() => setShowThresholds(false)}>
                <h4>Thresholds</h4>
                <span className="panel-close">&times;</span>
              </div>
              {filteredRanges.map((range: TriggerRange) => (
                <div key={range.strategyId} className="threshold-group">
                  <div className="threshold-strategy-name">{range.strategyName}</div>
                  {range.buyTriggerLow !== null && (
                    <div className="threshold-line buy">
                      <span className="threshold-label">Buy Low</span>
                      <span className="threshold-value">${range.buyTriggerLow.toFixed(2)}</span>
                    </div>
                  )}
                  {range.buyTriggerHigh !== null && (
                    <div className="threshold-line buy">
                      <span className="threshold-label">Buy High</span>
                      <span className="threshold-value">${range.buyTriggerHigh.toFixed(2)}</span>
                    </div>
                  )}
                  {range.sellTriggerLow !== null && (
                    <div className="threshold-line sell">
                      <span className="threshold-label">Sell Low</span>
                      <span className="threshold-value">${range.sellTriggerLow.toFixed(2)}</span>
                    </div>
                  )}
                  {range.sellTriggerHigh !== null && (
                    <div className="threshold-line sell">
                      <span className="threshold-label">Sell High</span>
                      <span className="threshold-value">${range.sellTriggerHigh.toFixed(2)}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          );
        })()}
      </div>

      {chartData && chartData.triggerRanges.length > 0 && (
        <div className="trigger-ranges">
          <h4>Strategy Trigger Ranges</h4>
          {(selectedStrategy === 'all' ? chartData.triggerRanges : chartData.triggerRanges.filter(r => r.strategyId === selectedStrategy))
            .map((range: TriggerRange) => (
              <div key={range.strategyId} className="trigger-range-item">
                <strong>{range.strategyName}</strong> ({range.type})
                <p>{range.description}</p>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
