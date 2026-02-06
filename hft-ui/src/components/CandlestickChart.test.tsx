import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, Mock } from 'vitest';
import { CandlestickChart } from './CandlestickChart';
import * as useApiModule from '../hooks/useApi';
import type { ChartData, Strategy } from '../types/api';

// Mock the useApi module
vi.mock('../hooks/useApi');

// Mock lightweight-charts
const mockSetData = vi.fn();
const mockCreatePriceLine = vi.fn();
const mockSetMarkers = vi.fn();
const mockFitContent = vi.fn();
const mockApplyOptions = vi.fn();
const mockRemove = vi.fn();

vi.mock('lightweight-charts', () => ({
  createChart: vi.fn(() => ({
    addSeries: vi.fn(() => ({
      setData: mockSetData,
      createPriceLine: mockCreatePriceLine,
    })),
    timeScale: vi.fn(() => ({
      fitContent: mockFitContent,
    })),
    subscribeCrosshairMove: vi.fn(),
    applyOptions: mockApplyOptions,
    remove: mockRemove,
  })),
  CandlestickSeries: 'CandlestickSeries',
  createSeriesMarkers: vi.fn(() => ({
    setMarkers: mockSetMarkers,
  })),
}));

const mockChartData: ChartData = {
  symbol: 'AAPL',
  exchange: 'ALPACA',
  interval: '5m',
  dataSource: 'stub',
  candles: [
    { time: 1700000000, open: 150.0, high: 151.0, low: 149.0, close: 150.5, volume: 1000 },
    { time: 1700000300, open: 150.5, high: 152.0, low: 150.0, close: 151.5, volume: 1200 },
    { time: 1700000600, open: 151.5, high: 153.0, low: 151.0, close: 152.0, volume: 1100 },
  ],
  orders: [
    {
      time: 1700000300,
      price: 150.5,
      side: 'BUY',
      quantity: 100,
      status: 'FILLED',
      strategyId: 'strat-1',
      orderId: 'order-1',
    },
    {
      time: 1700000600,
      price: 152.0,
      side: 'SELL',
      quantity: 50,
      status: 'FILLED',
      strategyId: 'strat-1',
      orderId: 'order-2',
    },
  ],
  triggerRanges: [
    {
      strategyId: 'strat-1',
      strategyName: 'My Momentum',
      type: 'momentum',
      symbol: 'AAPL',
      currentPrice: 152.0,
      buyTriggerLow: 148.0,
      buyTriggerHigh: 150.0,
      sellTriggerLow: 154.0,
      sellTriggerHigh: 156.0,
      description: 'Buy below 150, sell above 154',
    },
  ],
};

const mockStrategies: Strategy[] = [
  {
    id: 'strat-1',
    name: 'My Momentum',
    type: 'momentum',
    state: 'RUNNING',
    symbols: ['AAPL'],
    parameters: { shortPeriod: 10, longPeriod: 30 },
    progress: 0.5,
    stats: null,
  },
  {
    id: 'strat-2',
    name: 'Other Strategy',
    type: 'meanreversion',
    state: 'RUNNING',
    symbols: ['GOOGL'],
    parameters: {},
    progress: 0,
    stats: null,
  },
];

describe('CandlestickChart', () => {
  const mockGetChartData = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetChartData.mockResolvedValue(mockChartData);

    vi.mocked(useApiModule.useApi).mockReturnValue({
      getChartData: mockGetChartData,
      getSymbols: vi.fn(),
      getEngineStatus: vi.fn(),
      startEngine: vi.fn(),
      stopEngine: vi.fn(),
      getOrders: vi.fn(),
      getActiveOrders: vi.fn(),
      submitOrder: vi.fn(),
      cancelOrder: vi.fn(),
      getPositions: vi.fn(),
      getStrategies: vi.fn(),
      getStrategy: vi.fn(),
      createStrategy: vi.fn(),
      startStrategy: vi.fn(),
      stopStrategy: vi.fn(),
      removeStrategy: vi.fn(),
      getExchangeStatus: vi.fn(),
    });
  });

  it('renders the chart header with symbol and exchange', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    expect(screen.getByText('AAPL (ALPACA)')).toBeInTheDocument();
  });

  it('renders interval and periods controls', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    expect(screen.getByText('Interval:')).toBeInTheDocument();
    expect(screen.getByText('Periods:')).toBeInTheDocument();
    expect(screen.getByText('Strategy:')).toBeInTheDocument();
  });

  it('renders refresh button', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    // Wait for loading to complete
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });
  });

  it('fetches chart data on mount', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalledWith('ALPACA', 'AAPL', '5m', 100);
    });
  });

  it('fetches data with different interval when changed', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalled();
    });

    // Change interval
    const intervalSelect = screen.getByDisplayValue('5m');
    await act(async () => {
      fireEvent.change(intervalSelect, { target: { value: '15m' } });
    });

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalledWith('ALPACA', 'AAPL', '15m', 100);
    });
  });

  it('fetches data with different periods when changed', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalled();
    });

    // Change periods
    const periodsSelect = screen.getByDisplayValue('100');
    await act(async () => {
      fireEvent.change(periodsSelect, { target: { value: '200' } });
    });

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalledWith('ALPACA', 'AAPL', '5m', 200);
    });
  });

  it('shows loading state on refresh button', async () => {
    // Make the API call slow
    mockGetChartData.mockImplementation(() => new Promise(resolve => setTimeout(() => resolve(mockChartData), 100)));

    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    // Check for loading state
    expect(screen.getByRole('button', { name: /Loading/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });
  });

  it('displays error message when fetch fails', async () => {
    mockGetChartData.mockRejectedValue(new Error('Network error'));

    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(screen.getByText('Network error')).toBeInTheDocument();
    });
  });

  it('refetches data when refresh button is clicked', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalledTimes(1);
    });

    // Click refresh
    const refreshButton = screen.getByRole('button', { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButton);
    });

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalledTimes(2);
    });
  });

  it('displays trigger ranges section when data has trigger ranges', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(screen.getByText('Strategy Trigger Ranges')).toBeInTheDocument();
    });

    expect(screen.getByText('My Momentum')).toBeInTheDocument();
    expect(screen.getByText('(momentum)')).toBeInTheDocument();
    expect(screen.getByText('Buy below 150, sell above 154')).toBeInTheDocument();
  });

  it('does not fetch data when exchange is empty', async () => {
    render(<CandlestickChart exchange="" symbol="AAPL" />);

    // Wait a bit to ensure no fetch happens
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(mockGetChartData).not.toHaveBeenCalled();
  });

  it('does not fetch data when symbol is empty', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="" />);

    // Wait a bit to ensure no fetch happens
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(mockGetChartData).not.toHaveBeenCalled();
  });

  it('filters strategies to show only relevant ones for the symbol', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalled();
    });

    // Open strategy select
    const strategySelect = screen.getByDisplayValue('All Strategies');
    const options = Array.from((strategySelect as HTMLSelectElement).options).map(o => o.text);

    // Should have "All Strategies" and "My Momentum" (which has AAPL)
    // Should NOT have "Other Strategy" (which has GOOGL)
    expect(options).toContain('All Strategies');
    expect(options).toContain('My Momentum');
    expect(options).not.toContain('Other Strategy');
  });

  it('has all interval options', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    const intervalSelect = screen.getByDisplayValue('5m') as HTMLSelectElement;
    const options = Array.from(intervalSelect.options).map(o => o.value);

    expect(options).toContain('1m');
    expect(options).toContain('5m');
    expect(options).toContain('15m');
    expect(options).toContain('30m');
    expect(options).toContain('1h');
    expect(options).toContain('4h');
    expect(options).toContain('1d');
  });

  it('has all period options', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    const periodsSelect = screen.getByDisplayValue('100') as HTMLSelectElement;
    const options = Array.from(periodsSelect.options).map(o => o.value);

    expect(options).toContain('50');
    expect(options).toContain('100');
    expect(options).toContain('200');
    expect(options).toContain('500');
  });

  it('sets chart data when data is loaded', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockSetData).toHaveBeenCalled();
    });

    // Verify the candle data format
    expect(mockSetData).toHaveBeenCalledWith(
      expect.arrayContaining([
        expect.objectContaining({
          time: 1700000000,
          open: 150.0,
          high: 151.0,
          low: 149.0,
          close: 150.5,
        }),
      ])
    );
  });

  it('sets markers for orders', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockSetMarkers).toHaveBeenCalled();
    });

    // Should have markers for the 2 orders (blue circles on the candle)
    expect(mockSetMarkers).toHaveBeenCalledWith(
      expect.arrayContaining([
        expect.objectContaining({
          time: 1700000300,
          position: 'inBar',
          color: '#4a90d9',
          shape: 'circle',
        }),
        expect.objectContaining({
          time: 1700000600,
          position: 'inBar',
          color: '#4a90d9',
          shape: 'circle',
        }),
      ])
    );
  });

  it('creates price lines for trigger ranges', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockCreatePriceLine).toHaveBeenCalled();
    });

    // Should create price lines for buy/sell triggers
    expect(mockCreatePriceLine).toHaveBeenCalledWith(
      expect.objectContaining({
        price: 148.0,
        color: '#18dc18',
      })
    );
    expect(mockCreatePriceLine).toHaveBeenCalledWith(
      expect.objectContaining({
        price: 154.0,
        color: '#f04848',
      })
    );
  });

  it('fits content after setting data', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockFitContent).toHaveBeenCalled();
    });
  });

  it('filters orders when strategy is selected', async () => {
    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockSetMarkers).toHaveBeenCalled();
    });

    // Select specific strategy
    const strategySelect = screen.getByDisplayValue('All Strategies');
    await act(async () => {
      fireEvent.change(strategySelect, { target: { value: 'strat-1' } });
    });

    // Should filter markers to only strat-1
    await waitFor(() => {
      expect(mockSetMarkers).toHaveBeenLastCalledWith(
        expect.arrayContaining([
          expect.objectContaining({
            time: 1700000300,
          }),
          expect.objectContaining({
            time: 1700000600,
          }),
        ])
      );
    });
  });

  it('adds order markers in real-time via WebSocket subscription', async () => {
    let orderCallback: ((order: unknown) => void) | null = null;
    const mockUnsubscribe = vi.fn();
    const mockSubscribe = vi.fn((destination: string, callback: (data: unknown) => void) => {
      if (destination === '/topic/orders') {
        orderCallback = callback;
      }
      return mockUnsubscribe;
    });

    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" subscribe={mockSubscribe} />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalled();
    });

    // Verify subscription was made to /topic/orders
    expect(mockSubscribe).toHaveBeenCalledWith('/topic/orders', expect.any(Function));

    // Simulate receiving a new order via WebSocket
    await act(async () => {
      orderCallback!({
        clientOrderId: 123,
        symbol: 'AAPL',
        exchange: 'ALPACA',
        side: 'BUY',
        quantity: 200,
        price: 15300,
        averageFilledPrice: 15300,
        priceScale: 100,
        status: 'FILLED',
        strategyId: 'strat-1',
        createdAt: 1700000900000,
      });
    });

    // Should re-render markers with the new order appended
    await waitFor(() => {
      const lastCall = mockSetMarkers.mock.calls[mockSetMarkers.mock.calls.length - 1][0];
      expect(lastCall).toHaveLength(3); // 2 original + 1 new
      expect(lastCall[2]).toEqual(expect.objectContaining({
        time: 1700000900,
        position: 'inBar',
        color: '#4a90d9',
        shape: 'circle',
      }));
    });
  });

  it('ignores WebSocket orders for different symbol', async () => {
    let orderCallback: ((order: unknown) => void) | null = null;
    const mockSubscribe = vi.fn((destination: string, callback: (data: unknown) => void) => {
      if (destination === '/topic/orders') {
        orderCallback = callback;
      }
      return vi.fn();
    });

    render(<CandlestickChart exchange="ALPACA" symbol="AAPL" subscribe={mockSubscribe} />);

    await waitFor(() => {
      expect(mockSetMarkers).toHaveBeenCalled();
    });

    const callCountBefore = mockSetMarkers.mock.calls.length;

    // Send order for different symbol
    await act(async () => {
      orderCallback!({
        clientOrderId: 456,
        symbol: 'GOOGL',
        exchange: 'ALPACA',
        side: 'BUY',
        quantity: 50,
        price: 19200,
        averageFilledPrice: 0,
        priceScale: 100,
        status: 'FILLED',
        strategyId: 'strat-2',
        createdAt: 1700001000000,
      });
    });

    // Markers should not have been called again
    expect(mockSetMarkers.mock.calls.length).toBe(callCountBefore);
  });

  it('cleans up chart on unmount', async () => {
    const { unmount } = render(<CandlestickChart exchange="ALPACA" symbol="AAPL" />);

    await waitFor(() => {
      expect(mockGetChartData).toHaveBeenCalled();
    });

    unmount();

    expect(mockRemove).toHaveBeenCalled();
  });
});
