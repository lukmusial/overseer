import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { StrategyForm } from './StrategyForm';
import * as useApiModule from '../hooks/useApi';
import type { StrategyTypeInfo, TradingPeriodInfo } from '../types/api';

// Mock the useApi module
vi.mock('../hooks/useApi');

const mockStrategyTypes: StrategyTypeInfo[] = [
  {
    type: 'momentum',
    name: 'Momentum',
    description: 'Trend-following strategy using EMA crossovers',
    parameters: [
      { name: 'shortPeriod', type: 'int', default: 10, description: 'Short EMA period' },
      { name: 'longPeriod', type: 'int', default: 30, description: 'Long EMA period' },
      { name: 'signalThreshold', type: 'double', default: 0.02, description: 'Minimum signal strength' },
      { name: 'maxPositionSize', type: 'long', default: 1000, description: 'Maximum position size' },
    ],
  },
  {
    type: 'ema_adx_rsi',
    name: 'EMA + ADX + RSI',
    description: 'Triple-indicator trend strategy',
    parameters: [
      { name: 'emaPeriod', type: 'int', default: 21, description: 'EMA period' },
      { name: 'adxPeriod', type: 'int', default: 14, description: 'ADX period' },
      { name: 'rsiPeriod', type: 'int', default: 14, description: 'RSI period' },
      { name: 'adxThreshold', type: 'double', default: 25.0, description: 'ADX trend threshold' },
      { name: 'maxPositionSize', type: 'long', default: 1000, description: 'Maximum position size' },
    ],
  },
  {
    type: 'bollinger_squeeze',
    name: 'Bollinger Squeeze',
    description: 'Volatility breakout using Bollinger Band squeeze',
    parameters: [
      { name: 'bbPeriod', type: 'int', default: 20, description: 'Bollinger Band period' },
      { name: 'bbStdDev', type: 'double', default: 2.0, description: 'Standard deviation multiplier' },
      { name: 'maxPositionSize', type: 'long', default: 1000, description: 'Maximum position size' },
    ],
  },
  {
    type: 'vwap_mean_reversion',
    name: 'VWAP Mean Reversion',
    description: 'Mean reversion around VWAP anchor',
    parameters: [
      { name: 'vwapDeviation', type: 'double', default: 1.5, description: 'VWAP deviation threshold' },
      { name: 'maxPositionSize', type: 'long', default: 1000, description: 'Maximum position size' },
    ],
  },
  {
    type: 'vwap',
    name: 'VWAP',
    description: 'Volume-weighted average price execution',
    parameters: [
      { name: 'targetQuantity', type: 'long', default: 1000, description: 'Target quantity' },
      { name: 'durationMinutes', type: 'int', default: 60, description: 'Duration in minutes' },
      { name: 'maxParticipationRate', type: 'double', default: 0.25, description: 'Max participation rate' },
      { name: 'side', type: 'string', default: 'BUY', description: 'Order side' },
    ],
  },
];

const mockTradingPeriods: TradingPeriodInfo[] = [
  { name: 'LONDON_OPEN', startTime: '08:00', endTime: '09:00', positionMultiplier: 0.75, recommendedStrategies: ['bollinger_squeeze', 'ema_adx_rsi'] },
  { name: 'OVERLAP', startTime: '12:00', endTime: '16:00', positionMultiplier: 1.0, recommendedStrategies: ['ema_adx_rsi', 'bollinger_squeeze'] },
  { name: 'OFF_HOURS', startTime: '18:00', endTime: '08:00', positionMultiplier: 0.25, recommendedStrategies: [] },
];

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
  const mockGetStrategyTypes = vi.fn();
  const mockGetTradingPeriods = vi.fn();

  beforeEach(() => {
    mockOnSubmit.mockReset();
    mockGetSymbols.mockReset();
    mockGetStrategyTypes.mockReset();
    mockGetTradingPeriods.mockReset();

    // Default mock implementations
    mockGetSymbols.mockImplementation((exchange: string) => {
      if (exchange === 'ALPACA') return Promise.resolve(mockAlpacaSymbols);
      if (exchange === 'BINANCE') return Promise.resolve(mockBinanceSymbols);
      return Promise.resolve([]);
    });

    mockGetStrategyTypes.mockResolvedValue(mockStrategyTypes);
    mockGetTradingPeriods.mockResolvedValue(mockTradingPeriods);

    vi.mocked(useApiModule.useApi).mockReturnValue({
      getSymbols: mockGetSymbols,
      getStrategyTypes: mockGetStrategyTypes,
      getTradingPeriods: mockGetTradingPeriods,
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
      switchMode: vi.fn(),
      getRecentOrders: vi.fn(),
      searchOrders: vi.fn(),
      getRiskLimits: vi.fn(),
      updateRiskLimits: vi.fn(),
      getCurrentTradingPeriod: vi.fn(),
      getChartData: vi.fn(),
    });
  });

  it('renders the form with default values', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    expect(screen.getByRole('heading', { name: 'Create Strategy' })).toBeInTheDocument();
    expect(screen.getByText(/Name \(optional\)/i)).toBeInTheDocument();
    expect(screen.getByText('Type:')).toBeInTheDocument();
    expect(screen.getByText('Exchange:')).toBeInTheDocument();

    // Wait for types and symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });
  });

  it('fetches strategy types from API and shows all in dropdown', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for types to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });

    expect(mockGetStrategyTypes).toHaveBeenCalled();

    // Find the type select by the first type's display name
    const typeSelect = screen.getByDisplayValue('Momentum') as HTMLSelectElement;

    // Check all 5 types are present
    const options = Array.from(typeSelect.options).map(o => o.value);
    expect(options).toContain('momentum');
    expect(options).toContain('ema_adx_rsi');
    expect(options).toContain('bollinger_squeeze');
    expect(options).toContain('vwap_mean_reversion');
    expect(options).toContain('vwap');
  });

  it('populates default parameters from API when type changes', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });

    // Default momentum parameters from API
    expect(screen.getByDisplayValue('10')).toBeInTheDocument(); // shortPeriod
    expect(screen.getByDisplayValue('30')).toBeInTheDocument(); // longPeriod

    // Switch to ema_adx_rsi
    const typeSelect = screen.getByDisplayValue('Momentum');
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: 'ema_adx_rsi' } });
    });

    // ema_adx_rsi parameters from API
    await waitFor(() => {
      expect(screen.getByText(/emaPeriod/i)).toBeInTheDocument();
    });
    expect(screen.getByDisplayValue('21')).toBeInTheDocument(); // emaPeriod
    // adxPeriod and rsiPeriod both default to 14
    expect(screen.getAllByDisplayValue('14')).toHaveLength(2);
    expect(screen.getByDisplayValue('25')).toBeInTheDocument(); // adxThreshold
  });

  it('shows strategy type description from API', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });

    // Default type is momentum
    expect(screen.getByText(/Trend-following strategy using EMA crossovers/i)).toBeInTheDocument();
  });

  it('shows trading period selector with all periods', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });

    expect(screen.getByText('Trading Period:')).toBeInTheDocument();

    const periodSelect = screen.getByDisplayValue('All Periods (24h)') as HTMLSelectElement;
    const options = Array.from(periodSelect.options).map(o => o.textContent);
    expect(options).toContain('All Periods (24h)');
    expect(options.some(o => o?.includes('LONDON_OPEN'))).toBe(true);
    expect(options.some(o => o?.includes('OVERLAP'))).toBe(true);
    expect(options.some(o => o?.includes('OFF_HOURS'))).toBe(true);
  });

  it('shows recommended strategies when trading period is selected', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });

    const periodSelect = screen.getByDisplayValue('All Periods (24h)');
    await act(async () => {
      fireEvent.change(periodSelect, { target: { value: 'OVERLAP' } });
    });

    await waitFor(() => {
      expect(screen.getByText(/Recommended: ema_adx_rsi, bollinger_squeeze/i)).toBeInTheDocument();
    });
  });

  it('includes trading period in submitted parameters', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Select a trading period
    const periodSelect = screen.getByDisplayValue('All Periods (24h)');
    await act(async () => {
      fireEvent.change(periodSelect, { target: { value: 'OVERLAP' } });
    });

    // Select a symbol
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');
    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('AAPL').closest('li')!);

    // Submit
    await userEvent.click(screen.getByRole('button', { name: /Create Strategy/i }));

    expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
      parameters: expect.objectContaining({
        tradingPeriod: 'OVERLAP',
      }),
    }));
  });

  it('loads symbols when exchange is selected', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Focus on symbol input to show dropdown
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.click(symbolInput);

    // Should show symbols in dropdown
    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument();
    expect(mockGetSymbols).toHaveBeenCalledWith('ALPACA');
  });

  it('loads different symbols when exchange changes', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for initial load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Change to BINANCE using the exchange dropdown
    const exchangeSelect = screen.getByDisplayValue('ALPACA');
    await act(async () => {
      fireEvent.change(exchangeSelect, { target: { value: 'BINANCE' } });
    });

    // Wait for BINANCE symbols to load
    await waitFor(() => {
      expect(mockGetSymbols).toHaveBeenCalledWith('BINANCE');
    });

    // Click on symbol input to show dropdown
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.click(symbolInput);

    // Should show BINANCE symbols
    await waitFor(() => {
      expect(screen.getByText('BTCUSDT')).toBeInTheDocument();
    });
    expect(screen.getByText('BTC/USDT')).toBeInTheDocument();
  });

  it('filters symbols based on search input', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Type in the symbol input to filter
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'GOOG');

    // Should show only GOOGL in dropdown
    await waitFor(() => {
      expect(screen.getByText('GOOGL')).toBeInTheDocument();
    });
    // AAPL should not be visible
    expect(screen.queryByText('AAPL')).not.toBeInTheDocument();
  });

  it('submits form with selected values', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for types and symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Fill in name
    const nameInput = screen.getByPlaceholderText('My Strategy');
    await userEvent.type(nameInput, 'My Test Strategy');

    // Type in symbol input and select from dropdown
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');

    // Click on AAPL option in dropdown
    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    const aaplOption = screen.getByText('AAPL').closest('li');
    await userEvent.click(aaplOption!);

    // Submit
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

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

  it('allows editing fractional parameter values like signal threshold', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Find the signalThreshold input (default value 0.02)
    const thresholdInput = screen.getByDisplayValue('0.02') as HTMLInputElement;

    // Clear and type a new fractional value
    await userEvent.clear(thresholdInput);
    await userEvent.type(thresholdInput, '0.005');

    // The input should show the full value including decimal
    expect(thresholdInput.value).toBe('0.005');

    // Select a symbol and submit to verify the value is sent as a number
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');
    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('AAPL').closest('li')!);

    await userEvent.click(screen.getByRole('button', { name: /Create Strategy/i }));

    expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
      parameters: expect.objectContaining({
        signalThreshold: 0.005,
      }),
    }));
  });

  it('allows submitting without a custom name', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for types and symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Type and select symbol
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');

    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    const aaplOption = screen.getByText('AAPL').closest('li');
    await userEvent.click(aaplOption!);

    // Submit
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

    expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: undefined,
      symbols: ['AAPL'],
    }));
  });

  it('shows selected symbol confirmation', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Type and select symbol
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');

    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    const aaplOption = screen.getByText('AAPL').closest('li');
    await userEvent.click(aaplOption!);

    // Should show confirmation with symbol name
    expect(screen.getByText(/AAPL - Apple Inc./i)).toBeInTheDocument();
  });

  it('handles symbol fetch error gracefully', async () => {
    mockGetSymbols.mockRejectedValue(new Error('Network error'));

    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for loading to complete (with error)
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Should show error message
    expect(screen.getByText(/No symbols available/i)).toBeInTheDocument();
  });

  it('prevents submission with invalid symbol', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Type an invalid symbol (not selecting from dropdown)
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'INVALID');

    // Close dropdown by clicking elsewhere
    await userEvent.click(document.body);

    // Submit
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

    // Should show error and not call onSubmit
    await waitFor(() => {
      expect(screen.getByText(/is not available/i)).toBeInTheDocument();
    });
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('prevents submission with empty symbol', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Submit without selecting a symbol
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

    // Should show error
    await waitFor(() => {
      expect(screen.getByText(/Please select a symbol/i)).toBeInTheDocument();
    });
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('awaits onSubmit before clearing form fields', async () => {
    let resolveSubmit: () => void;
    const submitPromise = new Promise<void>((resolve) => {
      resolveSubmit = resolve;
    });
    mockOnSubmit.mockReturnValue(submitPromise);

    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for types and symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading strategy types.../i)).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Fill in name
    const nameInput = screen.getByPlaceholderText('My Strategy') as HTMLInputElement;
    await userEvent.type(nameInput, 'Await Test');

    // Select a symbol
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...') as HTMLInputElement;
    await userEvent.type(symbolInput, 'AAPL');
    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    const aaplOption = screen.getByText('AAPL').closest('li');
    await userEvent.click(aaplOption!);

    // Submit form (onSubmit returns a pending promise)
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

    // onSubmit was called but promise hasn't resolved yet
    expect(mockOnSubmit).toHaveBeenCalled();

    // Form fields should NOT be cleared yet (submit is still in progress)
    expect(nameInput.value).toBe('Await Test');
    expect(symbolInput.value).toBe('AAPL');

    // Now resolve the submit promise
    await act(async () => {
      resolveSubmit!();
    });

    // After resolve, form fields should be cleared
    await waitFor(() => {
      expect(nameInput.value).toBe('');
    });
    expect(symbolInput.value).toBe('');
  });

  it('clears error when valid symbol is selected', async () => {
    render(<StrategyForm onSubmit={mockOnSubmit} />);

    // Wait for symbols to load
    await waitFor(() => {
      expect(screen.queryByText(/Loading symbols.../i)).not.toBeInTheDocument();
    });

    // Submit without selecting a symbol to trigger error
    const submitButton = screen.getByRole('button', { name: /Create Strategy/i });
    await userEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/Please select a symbol/i)).toBeInTheDocument();
    });

    // Now select a valid symbol
    const symbolInput = screen.getByPlaceholderText('Type to search symbols...');
    await userEvent.type(symbolInput, 'AAPL');

    await waitFor(() => {
      expect(screen.getByText('AAPL')).toBeInTheDocument();
    });
    const aaplOption = screen.getByText('AAPL').closest('li');
    await userEvent.click(aaplOption!);

    // Error should be cleared
    expect(screen.queryByText(/Please select a symbol/i)).not.toBeInTheDocument();
  });

  it('shows loading indicator while types are fetching', async () => {
    mockGetStrategyTypes.mockReturnValue(new Promise(() => {})); // never resolves

    render(<StrategyForm onSubmit={mockOnSubmit} />);

    expect(screen.getByText(/Loading strategy types.../i)).toBeInTheDocument();
  });
});
