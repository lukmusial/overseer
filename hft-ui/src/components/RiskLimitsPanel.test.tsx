import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RiskLimitsPanel } from './RiskLimitsPanel';
import type { RiskLimits } from '../types/api';

// Mock useApi
const mockUpdateRiskLimits = vi.fn();

vi.mock('../hooks/useApi', () => ({
  useApi: () => ({
    updateRiskLimits: mockUpdateRiskLimits,
  }),
}));

const mockRiskLimits: RiskLimits = {
  limits: {
    maxOrderSize: 10000,
    maxOrderNotional: 1000000,
    maxPositionSize: 100000,
    maxOrdersPerDay: 10000,
    maxDailyNotional: 10000000,
    maxDailyLoss: 100000,
    maxDrawdownPerPosition: 50000,
    maxUnrealizedLossPerPosition: 25000,
    maxNetExposure: 5000000,
  },
  usage: {
    ordersSubmittedToday: 150,
    notionalTradedToday: 500000,
    currentDailyPnl: 25000,
    currentNetExposure: 1000000,
  },
  tradingEnabled: true,
  disabledReason: null,
};

const mockDisabledRiskLimits: RiskLimits = {
  ...mockRiskLimits,
  tradingEnabled: false,
  disabledReason: 'Daily loss limit exceeded',
};

describe('RiskLimitsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateRiskLimits.mockResolvedValue(mockRiskLimits);
  });

  it('renders loading state when riskLimits is null', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={null} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Risk Limits')).toBeInTheDocument();
    expect(screen.getByText('Loading risk limits...')).toBeInTheDocument();
  });

  it('renders the page header with edit button', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Risk Limits')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /back to dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /edit limits/i })).toBeInTheDocument();
  });

  it('calls onBack when back button is clicked', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    fireEvent.click(screen.getByRole('button', { name: /back to dashboard/i }));
    expect(onBack).toHaveBeenCalled();
  });

  it('shows trading enabled status', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Trading Enabled')).toBeInTheDocument();
  });

  it('shows trading disabled status with reason', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockDisabledRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Trading Disabled')).toBeInTheDocument();
    expect(screen.getByText('Reason: Daily loss limit exceeded')).toBeInTheDocument();
  });

  it('renders daily usage section', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Daily Limits & Usage')).toBeInTheDocument();
    expect(screen.getByText('Orders Today')).toBeInTheDocument();
    expect(screen.getByText('Daily Notional')).toBeInTheDocument();
    expect(screen.getByText('Net Exposure')).toBeInTheDocument();
  });

  it('renders per-order limits section', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Per-Order Limits')).toBeInTheDocument();
    expect(screen.getByText('Max Order Size')).toBeInTheDocument();
    expect(screen.getByText('Max Order Notional')).toBeInTheDocument();
    expect(screen.getByText('Max Position Size')).toBeInTheDocument();
  });

  it('renders per-position limits section', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    expect(screen.getByText('Per-Position Limits')).toBeInTheDocument();
    expect(screen.getByText('Max Drawdown/Position')).toBeInTheDocument();
    expect(screen.getByText('Max Unrealized Loss/Position')).toBeInTheDocument();
  });

  it('enters edit mode when edit button is clicked', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    fireEvent.click(screen.getByRole('button', { name: /edit limits/i }));

    // Should show save and cancel buttons
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();

    // Edit button should be gone
    expect(screen.queryByRole('button', { name: /edit limits/i })).not.toBeInTheDocument();
  });

  it('exits edit mode when cancel is clicked', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    fireEvent.click(screen.getByRole('button', { name: /edit limits/i }));
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

    // Should show edit button again
    expect(screen.getByRole('button', { name: /edit limits/i })).toBeInTheDocument();
  });

  it('calls updateRiskLimits and onUpdate when save is clicked', async () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    fireEvent.click(screen.getByRole('button', { name: /edit limits/i }));
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => {
      expect(mockUpdateRiskLimits).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(onUpdate).toHaveBeenCalledWith(mockRiskLimits);
    });
  });

  it('displays formatted order count values', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    // Order count: 150 / 10,000
    expect(screen.getByText('150 / 10,000')).toBeInTheDocument();
  });

  it('displays formatted currency values', () => {
    const onBack = vi.fn();
    const onUpdate = vi.fn();
    render(<RiskLimitsPanel riskLimits={mockRiskLimits} onBack={onBack} onUpdate={onUpdate} />);

    // maxOrderSize: 10,000
    expect(screen.getByText('10,000')).toBeInTheDocument();
    // maxOrderNotional: $10,000 (1,000,000 cents)
    expect(screen.getByText('$10,000')).toBeInTheDocument();
  });
});
