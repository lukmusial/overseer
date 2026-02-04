import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ChartPanel } from './ChartPanel';
import * as useApiModule from '../hooks/useApi';
import type { ExchangeStatus, Strategy, TradingSymbol } from '../types/api';

// Mock the useApi module
vi.mock('../hooks/useApi');

// Mock the CandlestickChart component to avoid lightweight-charts issues
vi.mock('./CandlestickChart', () => ({
  CandlestickChart: ({ exchange, symbol, strategies }: { exchange: string; symbol: string; strategies?: Strategy[] }) => (
    <div data-testid="candlestick-chart">
      <span data-testid="chart-exchange">{exchange}</span>
      <span data-testid="chart-symbol">{symbol}</span>
      <span data-testid="chart-strategies">{strategies?.length ?? 0}</span>
    </div>
  ),
}));

const mockExchanges: ExchangeStatus[] = [
  {
    exchange: 'ALPACA',
    name: 'Alpaca Markets',
    mode: 'stub',
    connected: true,
    authenticated: true,
    lastHeartbeat: Date.now(),
    errorMessage: null,
  },
  {
    exchange: 'BINANCE',
    name: 'Binance',
    mode: 'stub',
    connected: true,
    authenticated: true,
    lastHeartbeat: Date.now(),
    errorMessage: null,
  },
];

const mockAlpacaSymbols: TradingSymbol[] = [
  {
    symbol: 'AAPL',
    name: 'Apple Inc.',
    exchange: 'ALPACA',
    assetClass: 'equity',
    baseAsset: 'AAPL',
    quoteAsset: 'USD',
    tradable: true,
    marginable: true,
    shortable: true,
  },
  {
    symbol: 'GOOGL',
    name: 'Alphabet Inc.',
    exchange: 'ALPACA',
    assetClass: 'equity',
    baseAsset: 'GOOGL',
    quoteAsset: 'USD',
    tradable: true,
    marginable: true,
    shortable: true,
  },
];

const mockBinanceSymbols: TradingSymbol[] = [
  {
    symbol: 'BTCUSDT',
    name: 'BTC/USDT',
    exchange: 'BINANCE',
    assetClass: 'crypto',
    baseAsset: 'BTC',
    quoteAsset: 'USDT',
    tradable: true,
    marginable: false,
    shortable: false,
  },
  {
    symbol: 'ETHUSDT',
    name: 'ETH/USDT',
    exchange: 'BINANCE',
    assetClass: 'crypto',
    baseAsset: 'ETH',
    quoteAsset: 'USDT',
    tradable: true,
    marginable: false,
    shortable: false,
  },
];

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
];

describe('ChartPanel', () => {
  const mockGetSymbols = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockGetSymbols.mockImplementation((exchange: string) => {
      if (exchange === 'ALPACA') {
        return Promise.resolve(mockAlpacaSymbols);
      } else if (exchange === 'BINANCE') {
        return Promise.resolve(mockBinanceSymbols);
      }
      return Promise.resolve([]);
    });

    vi.mocked(useApiModule.useApi).mockReturnValue({
      getSymbols: mockGetSymbols,
      getChartData: vi.fn(),
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

  it('renders the panel title', () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    expect(screen.getByText('Price Chart')).toBeInTheDocument();
  });

  it('renders exchange and symbol dropdowns', () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    expect(screen.getByText('Exchange')).toBeInTheDocument();
    expect(screen.getByText('Symbol')).toBeInTheDocument();
  });

  it('auto-selects first exchange when exchanges load', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('Alpaca Markets')).toBeInTheDocument();
    });
  });

  it('fetches symbols when exchange is selected', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockGetSymbols).toHaveBeenCalledWith('ALPACA');
    });
  });

  it('populates symbol dropdown after symbols are fetched', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockGetSymbols).toHaveBeenCalled();
    });

    // Wait for symbols to populate - check by looking for symbol text
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Also verify GOOGL is in the dropdown options
    const selects = screen.getAllByRole('combobox');
    const symbolSelect = selects[1] as HTMLSelectElement;
    const options = Array.from(symbolSelect.options).map(o => o.value);
    expect(options).toContain('AAPL');
    expect(options).toContain('GOOGL');
  });

  it('auto-selects first symbol when symbols load', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });
  });

  it('fetches new symbols when exchange changes', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    // Wait for initial load
    await waitFor(() => {
      expect(mockGetSymbols).toHaveBeenCalledWith('ALPACA');
    });

    // Change exchange
    const exchangeSelect = screen.getByDisplayValue('Alpaca Markets');
    await act(async () => {
      fireEvent.change(exchangeSelect, { target: { value: 'BINANCE' } });
    });

    await waitFor(() => {
      expect(mockGetSymbols).toHaveBeenCalledWith('BINANCE');
    });
  });

  it('resets symbol when exchange changes', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    // Wait for initial load with ALPACA symbols
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Change exchange
    const exchangeSelect = screen.getByDisplayValue('Alpaca Markets');
    await act(async () => {
      fireEvent.change(exchangeSelect, { target: { value: 'BINANCE' } });
    });

    // Should now show BINANCE symbols
    await waitFor(() => {
      expect(screen.getByText(/BTCUSDT - BTC\/USDT/i)).toBeInTheDocument();
    });
  });

  it('renders CandlestickChart when exchange and symbol are selected', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument();
    });

    expect(screen.getByTestId('chart-exchange')).toHaveTextContent('ALPACA');
    expect(screen.getByTestId('chart-symbol')).toHaveTextContent('AAPL');
  });

  it('passes strategies to CandlestickChart', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByTestId('chart-strategies')).toHaveTextContent('1');
    });
  });

  it('shows empty message when no exchange or symbol is selected', async () => {
    render(<ChartPanel exchanges={[]} strategies={mockStrategies} />);

    expect(screen.getByText('Select an exchange and symbol to view the chart')).toBeInTheDocument();
  });

  it('shows loading state for symbols', async () => {
    // Make getSymbols slow
    mockGetSymbols.mockImplementation(() => new Promise(resolve => setTimeout(() => resolve(mockAlpacaSymbols), 100)));

    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    // Should show loading option
    expect(screen.getByText('Loading...')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
    });
  });

  it('disables symbol select while loading', async () => {
    // Make getSymbols slow
    mockGetSymbols.mockImplementation(() => new Promise(resolve => setTimeout(() => resolve(mockAlpacaSymbols), 100)));

    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    // Find symbol select
    const selects = screen.getAllByRole('combobox');
    const symbolSelect = selects[1];

    expect(symbolSelect).toBeDisabled();

    await waitFor(() => {
      expect(symbolSelect).not.toBeDisabled();
    });
  });

  it('handles symbol fetch error gracefully', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    mockGetSymbols.mockRejectedValue(new Error('Network error'));

    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch symbols:', expect.any(Error));
    });

    // Symbol select should be empty
    const selects = screen.getAllByRole('combobox');
    const symbolSelect = selects[1] as HTMLSelectElement;
    expect(symbolSelect.options.length).toBe(0);

    consoleSpy.mockRestore();
  });

  it('updates chart when symbol changes', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByTestId('chart-symbol')).toHaveTextContent('AAPL');
    });

    // Change symbol
    const selects = screen.getAllByRole('combobox');
    const symbolSelect = selects[1];
    await act(async () => {
      fireEvent.change(symbolSelect, { target: { value: 'GOOGL' } });
    });

    expect(screen.getByTestId('chart-symbol')).toHaveTextContent('GOOGL');
  });

  it('lists all exchanges in dropdown', async () => {
    render(<ChartPanel exchanges={mockExchanges} strategies={mockStrategies} />);

    const exchangeSelect = screen.getByDisplayValue('Alpaca Markets') as HTMLSelectElement;
    const options = Array.from(exchangeSelect.options).map(o => o.text);

    expect(options).toContain('Alpaca Markets');
    expect(options).toContain('Binance');
  });

  it('does not fetch symbols when no exchange is selected', async () => {
    render(<ChartPanel exchanges={[]} strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockGetSymbols).not.toHaveBeenCalled();
    });
  });
});
