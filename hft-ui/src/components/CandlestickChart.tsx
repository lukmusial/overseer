import { useEffect, useRef, useState, useCallback } from 'react';
import { createChart, CandlestickSeries, IChartApi, ISeriesApi, CandlestickData, Time, createSeriesMarkers, ISeriesMarkersPluginApi, SeriesMarker } from 'lightweight-charts';
import { useApi } from '../hooks/useApi';
import type { ChartData, TriggerRange, OrderMarker, Strategy } from '../types/api';

interface CandlestickChartProps {
  exchange: string;
  symbol: string;
  strategies?: Strategy[];
}

const INTERVALS = ['1m', '5m', '15m', '30m', '1h', '4h', '1d'];
const PERIOD_OPTIONS = [50, 100, 200, 500];

export function CandlestickChart({ exchange, symbol, strategies = [] }: CandlestickChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candlestickSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const markersPluginRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null);

  const [interval, setInterval] = useState('5m');
  const [periods, setPeriods] = useState(100);
  const [chartData, setChartData] = useState<ChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedStrategy, setSelectedStrategy] = useState<string>('all');

  const { getChartData } = useApi();

  // Fetch chart data
  const fetchData = useCallback(async () => {
    if (!exchange || !symbol) return;

    setLoading(true);
    setError(null);

    try {
      const data = await getChartData(exchange, symbol, interval, periods);
      setChartData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch chart data');
    } finally {
      setLoading(false);
    }
  }, [exchange, symbol, interval, periods, getChartData]);

  // Initialize chart
  useEffect(() => {
    if (!chartContainerRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height: 400,
      layout: {
        background: { color: '#1a1a2e' },
        textColor: '#d1d4dc',
      },
      grid: {
        vertLines: { color: '#2B2B43' },
        horzLines: { color: '#2B2B43' },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: '#2B2B43',
      },
      timeScale: {
        borderColor: '#2B2B43',
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
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

    const markers: SeriesMarker<Time>[] = filteredOrders.map((order: OrderMarker) => ({
      time: order.time as Time,
      position: order.side === 'BUY' ? 'belowBar' as const : 'aboveBar' as const,
      color: order.side === 'BUY' ? '#26a69a' : '#ef5350',
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
          color: '#26a69a',
          lineWidth: 1,
          lineStyle: 2, // Dashed
          axisLabelVisible: true,
          title: `Buy Low (${range.strategyName})`,
        });
      }
      if (range.buyTriggerHigh !== null) {
        candlestickSeriesRef.current?.createPriceLine({
          price: range.buyTriggerHigh,
          color: '#26a69a',
          lineWidth: 1,
          lineStyle: 2,
          axisLabelVisible: true,
          title: `Buy High (${range.strategyName})`,
        });
      }
      if (range.sellTriggerLow !== null) {
        candlestickSeriesRef.current?.createPriceLine({
          price: range.sellTriggerLow,
          color: '#ef5350',
          lineWidth: 1,
          lineStyle: 2,
          axisLabelVisible: true,
          title: `Sell Low (${range.strategyName})`,
        });
      }
      if (range.sellTriggerHigh !== null) {
        candlestickSeriesRef.current?.createPriceLine({
          price: range.sellTriggerHigh,
          color: '#ef5350',
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
