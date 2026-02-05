import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { StrategyList } from './StrategyList';
import type { Strategy } from '../types/api';

const mockStrategies: Strategy[] = [
  {
    id: 'strat-1',
    name: 'BTC Momentum',
    type: 'Momentum',
    state: 'RUNNING',
    symbols: ['BTCUSDT'],
    parameters: { shortPeriod: 5, longPeriod: 15, signalThreshold: 0.005, maxPositionSize: 100 },
    progress: 0,
    priceScale: 100,
    stats: {
      startTimeNanos: 0,
      endTimeNanos: 0,
      totalOrders: 10,
      filledOrders: 8,
      cancelledOrders: 1,
      rejectedOrders: 1,
      realizedPnl: 500,
      unrealizedPnl: 200,
      maxDrawdown: 100,
    },
  },
  {
    id: 'strat-2',
    name: 'ETH MeanRev',
    type: 'MeanReversion',
    state: 'STOPPED',
    symbols: ['ETHUSDT'],
    parameters: { lookbackPeriod: 20, entryZScore: 2.0, exitZScore: 0.5, maxPositionSize: 500 },
    progress: 0,
    priceScale: 100,
    stats: null,
  },
  {
    id: 'strat-3',
    name: 'AAPL VWAP',
    type: 'VWAP',
    state: 'COMPLETED',
    symbols: ['AAPL'],
    parameters: { targetQuantity: 1000, durationMinutes: 60, maxParticipationRate: 0.25, side: 'BUY' },
    progress: 1.0,
    priceScale: 100,
    stats: {
      startTimeNanos: 0,
      endTimeNanos: 0,
      totalOrders: 10,
      filledOrders: 10,
      cancelledOrders: 0,
      rejectedOrders: 0,
      realizedPnl: -300,
      unrealizedPnl: 0,
      maxDrawdown: 300,
    },
  },
];

describe('StrategyList', () => {
  const defaultProps = {
    strategies: mockStrategies,
    onStart: vi.fn(),
    onStop: vi.fn(),
    onRemove: vi.fn(),
    onInspect: vi.fn(),
  };

  it('renders empty state when no strategies', () => {
    render(<StrategyList {...defaultProps} strategies={[]} />);
    expect(screen.getByText('No strategies configured')).toBeInTheDocument();
  });

  it('renders strategies in table', () => {
    render(<StrategyList {...defaultProps} />);
    expect(screen.getByText('BTC Momentum')).toBeInTheDocument();
    expect(screen.getByText('ETH MeanRev')).toBeInTheDocument();
    expect(screen.getByText('AAPL VWAP')).toBeInTheDocument();
  });

  it('renders strategy count in header', () => {
    render(<StrategyList {...defaultProps} />);
    expect(screen.getByText(`Strategies (${mockStrategies.length})`)).toBeInTheDocument();
  });

  it('renders info buttons for each strategy with known type', () => {
    render(<StrategyList {...defaultProps} />);
    expect(screen.getByLabelText('Info about Momentum')).toBeInTheDocument();
    expect(screen.getByLabelText('Info about MeanReversion')).toBeInTheDocument();
    expect(screen.getByLabelText('Info about VWAP')).toBeInTheDocument();
  });

  it('tooltip content is in DOM with strategy description', () => {
    render(<StrategyList {...defaultProps} />);
    expect(screen.getByText('Momentum Strategy')).toBeInTheDocument();
    expect(screen.getByText('Follows trends using EMA crossover signals.')).toBeInTheDocument();
    expect(screen.getByText('MeanReversion Strategy')).toBeInTheDocument();
    expect(screen.getByText('Trades reversals to the mean using Bollinger Bands / Z-score.')).toBeInTheDocument();
  });

  it('tooltip shows actual parameter values from strategy', () => {
    render(<StrategyList {...defaultProps} />);
    // Momentum strategy has shortPeriod=5
    expect(screen.getByText('= 5')).toBeInTheDocument();
    // Momentum strategy has longPeriod=15
    expect(screen.getByText('= 15')).toBeInTheDocument();
    // MeanReversion has entryZScore=2
    expect(screen.getByText('= 2')).toBeInTheDocument();
  });

  it('renders Stop button for running strategies', () => {
    render(<StrategyList {...defaultProps} />);
    const stopButtons = screen.getAllByText('Stop');
    expect(stopButtons.length).toBe(1); // Only strat-1 is RUNNING
  });

  it('renders Start button for non-running strategies', () => {
    render(<StrategyList {...defaultProps} />);
    const startButtons = screen.getAllByText('Start');
    expect(startButtons.length).toBe(2); // strat-2 (STOPPED) and strat-3 (COMPLETED)
  });

  it('calls onStop when Stop button is clicked', () => {
    const onStop = vi.fn();
    render(<StrategyList {...defaultProps} onStop={onStop} />);
    fireEvent.click(screen.getByText('Stop'));
    expect(onStop).toHaveBeenCalledWith('strat-1');
  });

  it('calls onStart when Start button is clicked', () => {
    const onStart = vi.fn();
    render(<StrategyList {...defaultProps} onStart={onStart} />);
    const startButtons = screen.getAllByText('Start');
    fireEvent.click(startButtons[0]);
    expect(onStart).toHaveBeenCalledWith('strat-2');
  });

  it('calls onRemove when Remove button is clicked', () => {
    const onRemove = vi.fn();
    render(<StrategyList {...defaultProps} onRemove={onRemove} />);
    const removeButtons = screen.getAllByText('Remove');
    fireEvent.click(removeButtons[0]);
    expect(onRemove).toHaveBeenCalledWith('strat-1');
  });

  it('calls onInspect when name link is clicked', () => {
    const onInspect = vi.fn();
    render(<StrategyList {...defaultProps} onInspect={onInspect} />);
    fireEvent.click(screen.getByText('BTC Momentum'));
    expect(onInspect).toHaveBeenCalledWith(mockStrategies[0]);
  });

  it('renders symbols joined by comma', () => {
    render(<StrategyList {...defaultProps} />);
    expect(screen.getByText('BTCUSDT')).toBeInTheDocument();
    expect(screen.getByText('ETHUSDT')).toBeInTheDocument();
  });

  it('renders state badges with correct class', () => {
    render(<StrategyList {...defaultProps} />);
    const runningBadge = screen.getByText('RUNNING');
    expect(runningBadge.className).toContain('running');
    const stoppedBadge = screen.getByText('STOPPED');
    expect(stoppedBadge.className).toContain('stopped');
  });

  it('formats P&L correctly for positive and negative values', () => {
    render(<StrategyList {...defaultProps} />);
    // strat-1: realized 500 + unrealized 200 = 700 cents = $7.00
    expect(screen.getByText('+$7.00')).toBeInTheDocument();
    // strat-3: realized -300 + unrealized 0 = -300 cents = -$3.00
    expect(screen.getByText('-$3.00')).toBeInTheDocument();
  });

  it('shows newly created strategy when list is re-rendered with updated strategies', () => {
    const { rerender } = render(<StrategyList {...defaultProps} strategies={[]} />);

    // Initially empty
    expect(screen.getByText('No strategies configured')).toBeInTheDocument();

    // Simulate a new strategy being created and added to state
    const newStrategy: Strategy = {
      id: 'strat-new',
      name: 'New TWAP Strategy',
      type: 'TWAP',
      state: 'INITIALIZED',
      symbols: ['MSFT'],
      parameters: { targetQuantity: 500, durationMinutes: 30, sliceIntervalSeconds: 30, maxParticipationRate: 0.1 },
      progress: 0,
      priceScale: 100,
      stats: null,
    };

    rerender(<StrategyList {...defaultProps} strategies={[newStrategy]} />);

    // Strategy should now appear in the list
    expect(screen.queryByText('No strategies configured')).not.toBeInTheDocument();
    expect(screen.getByText('Strategies (1)')).toBeInTheDocument();
    expect(screen.getByText('New TWAP Strategy')).toBeInTheDocument();
    expect(screen.getByText('MSFT')).toBeInTheDocument();
    expect(screen.getByText('INITIALIZED')).toBeInTheDocument();

    // Adding another strategy should update the count
    rerender(<StrategyList {...defaultProps} strategies={[newStrategy, ...mockStrategies]} />);
    expect(screen.getByText('Strategies (4)')).toBeInTheDocument();
    expect(screen.getByText('New TWAP Strategy')).toBeInTheDocument();
    expect(screen.getByText('BTC Momentum')).toBeInTheDocument();
  });
});
