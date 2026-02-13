import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { TradingPeriodIndicator } from './TradingPeriodIndicator';
import * as useApiModule from '../hooks/useApi';
import type { TradingPeriodInfo } from '../types/api';

vi.mock('../hooks/useApi');

const mockAllPeriods: TradingPeriodInfo[] = [
  { name: 'LONDON_OPEN', startTime: '08:00', endTime: '09:00', positionMultiplier: 0.75, recommendedStrategies: ['bollinger_squeeze', 'ema_adx_rsi'] },
  { name: 'EU_MORNING', startTime: '09:00', endTime: '11:00', positionMultiplier: 0.75, recommendedStrategies: ['ema_adx_rsi'] },
  { name: 'OVERLAP', startTime: '12:00', endTime: '16:00', positionMultiplier: 1.0, recommendedStrategies: ['ema_adx_rsi', 'bollinger_squeeze'] },
  { name: 'OFF_HOURS', startTime: '18:00', endTime: '08:00', positionMultiplier: 0.25, recommendedStrategies: [] },
];

const mockCurrentPeriod: TradingPeriodInfo = {
  name: 'OVERLAP',
  startTime: '12:00',
  endTime: '16:00',
  positionMultiplier: 1.0,
  recommendedStrategies: ['ema_adx_rsi', 'bollinger_squeeze'],
};

describe('TradingPeriodIndicator', () => {
  const mockGetCurrentTradingPeriod = vi.fn();
  const mockGetTradingPeriods = vi.fn();

  beforeEach(() => {
    mockGetCurrentTradingPeriod.mockReset();
    mockGetTradingPeriods.mockReset();

    mockGetCurrentTradingPeriod.mockResolvedValue(mockCurrentPeriod);
    mockGetTradingPeriods.mockResolvedValue(mockAllPeriods);

    vi.mocked(useApiModule.useApi).mockReturnValue({
      getCurrentTradingPeriod: mockGetCurrentTradingPeriod,
      getTradingPeriods: mockGetTradingPeriods,
      getStrategyTypes: vi.fn(),
      getSymbols: vi.fn(),
      getEngineStatus: vi.fn(),
      startEngine: vi.fn(),
      stopEngine: vi.fn(),
      getOrders: vi.fn(),
      getActiveOrders: vi.fn(),
      getRecentOrders: vi.fn(),
      searchOrders: vi.fn(),
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
      getRiskLimits: vi.fn(),
      updateRiskLimits: vi.fn(),
      getChartData: vi.fn(),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders current trading period info', async () => {
    render(<TradingPeriodIndicator />);

    await waitFor(() => {
      expect(screen.getByText('[OVERLAP]')).toBeInTheDocument();
    });

    expect(screen.getByText('12:00-16:00 UTC')).toBeInTheDocument();
    expect(screen.getByText('1x')).toBeInTheDocument();
    expect(screen.getByText('ema_adx_rsi, bollinger_squeeze')).toBeInTheDocument();
  });

  it('renders timeline bar with all periods', async () => {
    render(<TradingPeriodIndicator />);

    await waitFor(() => {
      expect(screen.getByRole('img', { name: /Trading period timeline/i })).toBeInTheDocument();
    });

    const timeline = screen.getByRole('img', { name: /Trading period timeline/i });
    const segments = timeline.querySelectorAll('.period-segment');
    expect(segments.length).toBe(4);

    // The active segment should have the active class
    const activeSegments = timeline.querySelectorAll('.period-segment.active');
    expect(activeSegments.length).toBe(1);
  });

  it('renders nothing when API fails', async () => {
    mockGetCurrentTradingPeriod.mockRejectedValue(new Error('Network error'));
    mockGetTradingPeriods.mockRejectedValue(new Error('Network error'));

    const { container } = render(<TradingPeriodIndicator />);

    // Wait a bit for the promises to reject
    await waitFor(() => {
      expect(mockGetCurrentTradingPeriod).toHaveBeenCalled();
    });

    // Should render nothing
    expect(container.querySelector('.trading-period-indicator')).not.toBeInTheDocument();
  });

  it('sets up polling interval on mount', () => {
    const setIntervalSpy = vi.spyOn(global, 'setInterval');

    render(<TradingPeriodIndicator />);

    // Should set up a 30-second interval
    expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 30000);

    setIntervalSpy.mockRestore();
  });

  it('does not show recommended strategies when list is empty', async () => {
    mockGetCurrentTradingPeriod.mockResolvedValue({
      name: 'OFF_HOURS',
      startTime: '18:00',
      endTime: '08:00',
      positionMultiplier: 0.25,
      recommendedStrategies: [],
    });

    const { container } = render(<TradingPeriodIndicator />);

    await waitFor(() => {
      expect(screen.getByText('[OFF_HOURS]')).toBeInTheDocument();
    });

    expect(screen.getByText('0.25x')).toBeInTheDocument();
    expect(container.querySelector('.period-strategies')).not.toBeInTheDocument();
  });
});
