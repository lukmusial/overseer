import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { StrategyForm } from './StrategyForm';
import * as useApiModule from '../hooks/useApi';

// Mock the useApi module
vi.mock('../hooks/useApi');

const mockAlpacaSymbols = [
  { symbol: 'AAPL', name: 'Apple Inc.', exchange: 'ALPACA', assetClass: 'equity', baseAsset: 'AAPL', quoteAsset: 'USD', tradable: true, marginable: true, shortable: true },
  { symbol: 'GOOGL', name: 'Alphabet Inc.', exchange: 'ALPACA', assetClass: 'equity', baseAsset: 'GOOGL', quoteAsset: 'USD', tradable: true, marginable: true, shortable: true },
  { symbol: 'MSFT', name: 'Microsoft Corporation', exchange: 'ALPACA', assetClass: 'equity', baseAsset: 'MSFT', quoteAsset: 'USD', tradable: true, marginable: true, shortable: true },
];

const mockBinanceSymbols = [
  { symbol: 'BTCUSDT', name: 'BTC/USDT', exchange: 'BINANCE', assetClass: 'crypto', baseAsset: 'BTC', quoteAsset: 'USDT', tradable: true, marginable: false, shortable: false },
  { symbol: 'ETHUSDT', name: 'ETH/USDT', exchange: 'BINANCE', assetClass: 'crypto', baseAsset: 'ETH', quoteAsset: 'USDT', tradable: true, marginable: false, shortable: false },
];

describe('StrategyForm', () => {
  const mockOnSubmit = vi.fn();
  const mockGetSymbols = vi.fn();

  beforeEach(() => {
    mockOnSubmit.mockReset();
    mockGetSymbols.mockReset();

    // Default mock implementation
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

  it('renders the form with default values', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    expect(screen.getByRole('heading', { name: 'Create Strategy' })).toBeInTheDocument();
    expect(screen.getByText(/Name \(optional\)/i)).toBeInTheDocument();
    expect(screen.getByText('Type:')).toBeInTheDocument();
    expect(screen.getByText('Exchange:')).toBeInTheDocument();

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });
  });

  it('shows all strategy types in dropdown', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Find the type select by its value
    const typeSelect = screen.getByDisplayValue('Momentum') as HTMLSelectElement;

    // Check all options are present
    const options = Array.from(typeSelect.options).map(o => o.value);
    expect(options).toContain('momentum');
    expect(options).toContain('meanreversion');
    expect(options).toContain('vwap');
    expect(options).toContain('twap');
  });

  it('loads symbols when exchange is selected', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for ALPACA symbols to load (default exchange)
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    expect(screen.getByText(/GOOGL - Alphabet Inc./i)).toBeInTheDocument();
    expect(screen.getByText(/MSFT - Microsoft Corporation/i)).toBeInTheDocument();
    expect(mockGetSymbols).toHaveBeenCalledWith('ALPACA');
  });

  it('loads different symbols when exchange changes', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for initial ALPACA symbols
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Change to BINANCE using the exchange dropdown
    const exchangeSelect = screen.getByDisplayValue('ALPACA');
    await act(async () => {
      fireEvent.change(exchangeSelect, { target: { value: 'BINANCE' } });
    });

    // Wait for BINANCE symbols
    await waitFor(() => {
      expect(screen.getByText(/BTCUSDT - BTC\/USDT/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/ETHUSDT - ETH\/USDT/i)).toBeInTheDocument();
    // ALPACA symbols should be gone
    expect(screen.queryByText(/AAPL - Apple Inc./i)).not.toBeInTheDocument();
    expect(mockGetSymbols).toHaveBeenCalledWith('BINANCE');
  });

  it('filters symbols based on search input', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Type in the filter
    const filterInput = screen.getByPlaceholderText('Search symbols...');
    await userEvent.type(filterInput, 'GOOG');

    // Should show GOOGL but not AAPL
    expect(screen.getByText(/GOOGL - Alphabet Inc./i)).toBeInTheDocument();
    expect(screen.queryByText(/AAPL - Apple Inc./i)).not.toBeInTheDocument();
  });

  it('shows strategy type description', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Default type is momentum
    expect(screen.getByText(/Follow price trends using EMA crossovers/i)).toBeInTheDocument();
  });

  it('updates parameters when strategy type changes', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Default momentum parameters
    expect(screen.getByDisplayValue('10')).toBeInTheDocument(); // shortPeriod
    expect(screen.getByDisplayValue('30')).toBeInTheDocument(); // longPeriod

    // Change to VWAP using display value
    const typeSelect = screen.getByDisplayValue('Momentum');
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: 'vwap' } });
    });

    // VWAP parameters
    await waitFor(() => {
      expect(screen.getByText(/targetQuantity/i)).toBeInTheDocument();
    });
    expect(screen.getByDisplayValue('1000')).toBeInTheDocument(); // targetQuantity
    expect(screen.getByDisplayValue('60')).toBeInTheDocument(); // durationMinutes
  });

  it('submits form with selected values', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Fill in name using placeholder
    const nameInput = screen.getByPlaceholderText('My Strategy');
    await userEvent.type(nameInput, 'My Test Strategy');

    // Select a symbol from the listbox
    const symbolSelect = screen.getByRole('listbox');
    await act(async () => {
      fireEvent.change(symbolSelect, { target: { value: 'AAPL' } });
    });

    // Submit
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await act(async () => {
      fireEvent.click(submitButton);
    });

    expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: 'My Test Strategy',
      type: 'momentum',
      symbols: ['AAPL'],
      exchange: 'ALPACA',
      parameters: expect.objectContaining({
        shortPeriod: 10,
        longPeriod: 30,
      }),
    }));
  });

  it('allows submitting without a custom name', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Select a symbol (don't fill name)
    const symbolSelect = screen.getByRole('listbox');
    await act(async () => {
      fireEvent.change(symbolSelect, { target: { value: 'AAPL' } });
    });

    // Submit
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await act(async () => {
      fireEvent.click(submitButton);
    });

    expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: undefined,
      symbols: ['AAPL'],
    }));
  });

  it('shows selected symbol indicator', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
    });

    // Select a symbol
    const symbolSelect = screen.getByRole('listbox');
    await act(async () => {
      fireEvent.change(symbolSelect, { target: { value: 'AAPL' } });
    });

    // Should show selected indicator
    expect(screen.getByText(/Selected: AAPL/i)).toBeInTheDocument();
  });

  it('handles symbol fetch error gracefully', async () => {
    mockGetSymbols.mockRejectedValue(new Error('Network error'));

    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for loading to complete (with error)
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Should show empty select option
    expect(screen.getByText(/-- Select a symbol --/i)).toBeInTheDocument();
  });
});
