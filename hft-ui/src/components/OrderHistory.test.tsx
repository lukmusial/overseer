import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { OrderHistory } from './OrderHistory';
import type { Order, Strategy } from '../types/api';

// Mock useApi
const mockSearchOrders = vi.fn();

vi.mock('../hooks/useApi', () => ({
  useApi: () => ({
    searchOrders: mockSearchOrders,
  }),
}));

const mockStrategies: Strategy[] = [
  {
    id: 'strat-1',
    name: 'Test Strategy',
    type: 'Momentum',
    state: 'RUNNING',
    symbols: ['BTCUSDT'],
    parameters: {},
    progress: 0,
    priceScale: 100,
    stats: null,
  },
];

const mockOrders: Order[] = [
  {
    clientOrderId: 1,
    exchangeOrderId: 'ex-1',
    symbol: 'BTCUSDT',
    exchange: 'BINANCE',
    side: 'BUY',
    type: 'LIMIT',
    timeInForce: 'GTC',
    quantity: 100,
    price: 5000000,
    stopPrice: 0,
    filledQuantity: 100,
    averageFilledPrice: 5000000,
    priceScale: 100,
    status: 'FILLED',
    rejectReason: null,
    strategyId: 'strat-1',
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
];

describe('OrderHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchOrders.mockResolvedValue(mockOrders);
  });

  it('renders the page header', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    expect(screen.getByText('Order History')).toBeInTheDocument();
  });

  it('renders filter controls', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    expect(screen.getByText('Strategy:')).toBeInTheDocument();
    expect(screen.getByText('Status:')).toBeInTheDocument();
    expect(screen.getByText('Symbol:')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reset/i })).toBeInTheDocument();
  });

  it('loads orders on mount', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    await waitFor(() => {
      expect(mockSearchOrders).toHaveBeenCalled();
    });
  });

  it('displays strategy and exchange columns in order table', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    await waitFor(() => {
      expect(screen.getByText('Strategy')).toBeInTheDocument();
      expect(screen.getByText('Exchange')).toBeInTheDocument();
    });
  });

  it('has all order statuses in filter dropdown', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    // Check for status options in dropdown
    expect(screen.getByRole('option', { name: 'All Statuses' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'FILLED' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'REJECTED' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'CANCELLED' })).toBeInTheDocument();
  });

  it('has strategies in strategy filter dropdown', async () => {
    render(<OrderHistory strategies={mockStrategies} />);

    const strategySelect = screen.getAllByRole('combobox')[0];
    expect(strategySelect).toBeInTheDocument();

    expect(screen.getByRole('option', { name: 'All Strategies' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Test Strategy/ })).toBeInTheDocument();
  });
});
