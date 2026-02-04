import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useApi } from './useApi';

describe('useApi', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
  });

  describe('getEngineStatus', () => {
    it('should fetch engine status', async () => {
      const mockStatus = {
        running: true,
        startTime: Date.now(),
        uptimeMillis: 1000,
        totalOrdersProcessed: 100,
        totalTradesExecuted: 50,
        activeStrategies: 2,
        openPositions: 3,
        pendingOrders: 5,
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStatus),
      });

      const { result } = renderHook(() => useApi());
      const status = await result.current.getEngineStatus();

      expect(mockFetch).toHaveBeenCalledWith('/api/engine/status', expect.objectContaining({
        headers: { 'Content-Type': 'application/json' },
      }));
      expect(status).toEqual(mockStatus);
    });
  });

  describe('getSymbols', () => {
    it('should fetch symbols for an exchange', async () => {
      const mockSymbols = [
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
          shortable: false,
        },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockSymbols),
      });

      const { result } = renderHook(() => useApi());
      const symbols = await result.current.getSymbols('ALPACA');

      expect(mockFetch).toHaveBeenCalledWith('/api/exchanges/ALPACA/symbols', expect.objectContaining({
        headers: { 'Content-Type': 'application/json' },
      }));
      expect(symbols).toEqual(mockSymbols);
      expect(symbols).toHaveLength(2);
      expect(symbols[0].symbol).toBe('AAPL');
    });

    it('should fetch crypto symbols for Binance', async () => {
      const mockSymbols = [
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
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockSymbols),
      });

      const { result } = renderHook(() => useApi());
      const symbols = await result.current.getSymbols('BINANCE');

      expect(mockFetch).toHaveBeenCalledWith('/api/exchanges/BINANCE/symbols', expect.any(Object));
      expect(symbols[0].assetClass).toBe('crypto');
      expect(symbols[0].baseAsset).toBe('BTC');
    });
  });

  describe('getExchangeStatus', () => {
    it('should fetch exchange status', async () => {
      const mockStatus = [
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
          authenticated: false,
          lastHeartbeat: null,
          errorMessage: 'API credentials not configured',
        },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStatus),
      });

      const { result } = renderHook(() => useApi());
      const status = await result.current.getExchangeStatus();

      expect(mockFetch).toHaveBeenCalledWith('/api/exchanges/status', expect.any(Object));
      expect(status).toHaveLength(2);
      expect(status[0].connected).toBe(true);
    });
  });

  describe('switchMode', () => {
    it('should call PUT with correct exchange and mode', async () => {
      const mockStatus = {
        exchange: 'BINANCE',
        name: 'Binance (Testnet)',
        mode: 'testnet',
        connected: true,
        authenticated: true,
        lastHeartbeat: Date.now(),
        errorMessage: null,
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStatus),
      });

      const { result } = renderHook(() => useApi());
      const status = await result.current.switchMode('BINANCE', 'testnet');

      expect(mockFetch).toHaveBeenCalledWith('/api/exchanges/BINANCE/mode', expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ mode: 'testnet' }),
      }));
      expect(status.mode).toBe('testnet');
    });
  });

  describe('getStrategies', () => {
    it('should fetch strategies', async () => {
      const mockStrategies = [
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

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStrategies),
      });

      const { result } = renderHook(() => useApi());
      const strategies = await result.current.getStrategies();

      expect(mockFetch).toHaveBeenCalledWith('/api/strategies', expect.any(Object));
      expect(strategies).toHaveLength(1);
      expect(strategies[0].name).toBe('My Momentum');
    });
  });

  describe('createStrategy', () => {
    it('should create a strategy', async () => {
      const mockStrategy = {
        id: 'strat-new',
        name: 'Test VWAP',
        type: 'vwap',
        state: 'CREATED',
        symbols: ['AAPL'],
        parameters: { targetQuantity: 1000, durationMinutes: 60 },
        progress: 0,
        stats: null,
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStrategy),
      });

      const { result } = renderHook(() => useApi());
      const strategy = await result.current.createStrategy({
        name: 'Test VWAP',
        type: 'vwap',
        symbols: ['AAPL'],
        exchange: 'ALPACA',
        parameters: { targetQuantity: 1000, durationMinutes: 60 },
      });

      expect(mockFetch).toHaveBeenCalledWith('/api/strategies', expect.objectContaining({
        method: 'POST',
        body: expect.any(String),
      }));
      expect(strategy.name).toBe('Test VWAP');
      expect(strategy.type).toBe('vwap');
    });
  });

  describe('error handling', () => {
    it('should throw error on HTTP error response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
      });

      const { result } = renderHook(() => useApi());

      await expect(result.current.getEngineStatus()).rejects.toThrow('HTTP error! status: 404');
    });

    it('should throw error on network failure', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'));

      const { result } = renderHook(() => useApi());

      await expect(result.current.getEngineStatus()).rejects.toThrow('Network error');
    });
  });

  describe('submitOrder', () => {
    it('should submit an order', async () => {
      const mockOrder = {
        clientOrderId: 123,
        exchangeOrderId: null,
        symbol: 'AAPL',
        exchange: 'ALPACA',
        side: 'BUY',
        type: 'LIMIT',
        timeInForce: 'DAY',
        quantity: 100,
        price: 15000,
        stopPrice: 0,
        filledQuantity: 0,
        averageFilledPrice: 0,
        status: 'PENDING',
        rejectReason: null,
        strategyId: null,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockOrder),
      });

      const { result } = renderHook(() => useApi());
      const order = await result.current.submitOrder({
        symbol: 'AAPL',
        exchange: 'ALPACA',
        side: 'BUY',
        type: 'LIMIT',
        timeInForce: 'DAY',
        quantity: 100,
        price: 15000,
      });

      expect(mockFetch).toHaveBeenCalledWith('/api/orders', expect.objectContaining({
        method: 'POST',
      }));
      expect(order.symbol).toBe('AAPL');
      expect(order.status).toBe('PENDING');
    });
  });
});
