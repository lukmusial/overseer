import { useEffect, useRef, useState, useCallback } from 'react';
import { createChart, CandlestickSeries, IChartApi, ISeriesApi, CandlestickData, Time, createSeriesMarkers, ISeriesMarkersPluginApi, SeriesMarker } from 'lightweight-charts';
import { useApi } from '../hooks/useApi';
import type { ChartData, TriggerRange, OrderMarker, Strategy, Quote } from '../types/api';

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
  const chartRef = useRef<IChartApi | null>(null);
  const candlestickSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const markersPluginRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null);
  const currentCandleRef = useRef<CandlestickData<Time> | null>(null);

  const [interval, setInterval] = useState('5m');
  const [periods, setPeriods] = useState(100);
  const [chartData, setChartData] = useState<ChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedStrategy, setSelectedStrategy] = useState<string>('all');
  const [lastQuote, setLastQuote] = useState<Quote | null>(null);

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
    const pipGreen = '#00ff00';
    const pipGreenDim = '#00aa00';
    const pipGreenDark = '#006600';
    const pipBg = '#0a0a0a';
    const pipRed = '#ff3333';

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

    // Pip-Boy theme colors for markers
    const pipGreen = '#00ff00';
    const pipRed = '#ff3333';

    const markers: SeriesMarker<Time>[] = filteredOrders.map((order: OrderMarker) => ({
      time: order.time as Time,
      position: order.side === 'BUY' ? 'belowBar' as const : 'aboveBar' as const,
      color: order.side === 'BUY' ? pipGreen : pipRed,
      shape: order.side === 'BUY' ? 'arrowUp' as const : 'arrowDown' as const,
      text: `${order.side} ${order.quantity} @ ${order.price.toFixed(2)}`,
    }));

    if (markersPluginRef.current) {
      markersPluginRef.current.setMarkers(markers);
    }

    // Draw trigger ranges as price lines
    const chart = chartRef.current;

    // Add trigger range price lines
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

    // Fit content
    chart.timeScale().fitContent();
  }, [chartData, selectedStrategy]);

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
        </div>
      </div>

      {error && <div className="chart-error">{error}</div>}

      <div ref={chartContainerRef} className="chart-container" />

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
