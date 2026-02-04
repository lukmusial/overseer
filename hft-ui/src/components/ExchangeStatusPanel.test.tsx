import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ExchangeStatusPanel } from './ExchangeStatusPanel';
import type { ExchangeStatus } from '../types/api';

const mockExchanges: ExchangeStatus[] = [
  {
    exchange: 'ALPACA',
    name: 'Alpaca Markets (Stub)',
    mode: 'stub',
    connected: true,
    authenticated: true,
    lastHeartbeat: Date.now(),
    errorMessage: null,
  },
  {
    exchange: 'BINANCE',
    name: 'Binance (Stub)',
    mode: 'stub',
    connected: true,
    authenticated: false,
    lastHeartbeat: null,
    errorMessage: 'API credentials not configured',
  },
];

describe('ExchangeStatusPanel', () => {
  it('renders empty state when no exchanges', () => {
    render(<ExchangeStatusPanel exchanges={[]} />);
    expect(screen.getByText('No exchanges configured')).toBeInTheDocument();
  });

  it('renders exchange names', () => {
    render(<ExchangeStatusPanel exchanges={mockExchanges} />);
    expect(screen.getByText('Alpaca Markets (Stub)')).toBeInTheDocument();
    expect(screen.getByText('Binance (Stub)')).toBeInTheDocument();
  });

  it('renders mode dropdowns when onSwitchMode is provided', () => {
    const onSwitchMode = vi.fn().mockResolvedValue(undefined);
    render(<ExchangeStatusPanel exchanges={mockExchanges} onSwitchMode={onSwitchMode} />);

    const selects = screen.getAllByRole('combobox');
    expect(selects).toHaveLength(2);
  });

  it('renders mode badges when onSwitchMode is not provided', () => {
    render(<ExchangeStatusPanel exchanges={mockExchanges} />);

    // No dropdowns should be rendered
    expect(screen.queryAllByRole('combobox')).toHaveLength(0);
    // Mode badges should be present
    const badges = screen.getAllByText('Stub');
    expect(badges.length).toBe(2);
  });

  it('calls onSwitchMode when dropdown changes', async () => {
    const onSwitchMode = vi.fn().mockResolvedValue(undefined);
    render(<ExchangeStatusPanel exchanges={mockExchanges} onSwitchMode={onSwitchMode} />);

    const alpacaSelect = screen.getByLabelText('ALPACA mode');
    fireEvent.change(alpacaSelect, { target: { value: 'sandbox' } });

    await waitFor(() => {
      expect(onSwitchMode).toHaveBeenCalledWith('ALPACA', 'sandbox');
    });
  });

  it('shows error message when switch fails', async () => {
    const onSwitchMode = vi.fn().mockRejectedValue(new Error('Failed'));
    render(<ExchangeStatusPanel exchanges={mockExchanges} onSwitchMode={onSwitchMode} />);

    const alpacaSelect = screen.getByLabelText('ALPACA mode');
    fireEvent.change(alpacaSelect, { target: { value: 'live' } });

    await waitFor(() => {
      expect(screen.getByText('Failed to switch ALPACA mode')).toBeInTheDocument();
    });
  });

  it('shows error messages from exchanges', () => {
    render(<ExchangeStatusPanel exchanges={mockExchanges} />);
    expect(screen.getByText('API credentials not configured')).toBeInTheDocument();
  });

  it('shows correct connection status', () => {
    render(<ExchangeStatusPanel exchanges={mockExchanges} />);
    expect(screen.getByText('Connected & Authenticated')).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
  });
});
