import { useCallback } from 'react';
import type {
  EngineStatus,
  Order,
  Position,
  Strategy,
  CreateOrderRequest,
  CreateStrategyRequest,
  ExchangeStatus,
  TradingSymbol,
  ChartData,
} from '../types/api';

const API_BASE = '/api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.json();
}

export function useApi() {
  // Engine operations
  const getEngineStatus = useCallback(async (): Promise<EngineStatus> => {
    return fetchJson<EngineStatus>(`${API_BASE}/engine/status`);
  }, []);

  const startEngine = useCallback(async (): Promise<void> => {
    await fetch(`${API_BASE}/engine/start`, { method: 'POST' });
  }, []);

  const stopEngine = useCallback(async (): Promise<void> => {
    await fetch(`${API_BASE}/engine/stop`, { method: 'POST' });
  }, []);

  // Order operations
  const getOrders = useCallback(async (): Promise<Order[]> => {
    return fetchJson<Order[]>(`${API_BASE}/orders`);
  }, []);

  const getActiveOrders = useCallback(async (): Promise<Order[]> => {
    return fetchJson<Order[]>(`${API_BASE}/orders/active`);
  }, []);

  const submitOrder = useCallback(async (order: CreateOrderRequest): Promise<Order> => {
    return fetchJson<Order>(`${API_BASE}/orders`, {
      method: 'POST',
      body: JSON.stringify(order),
    });
  }, []);

  const cancelOrder = useCallback(async (orderId: number): Promise<void> => {
    await fetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' });
  }, []);

  // Position operations
  const getPositions = useCallback(async (): Promise<Position[]> => {
    return fetchJson<Position[]>(`${API_BASE}/positions`);
  }, []);

  // Strategy operations
  const getStrategies = useCallback(async (): Promise<Strategy[]> => {
    return fetchJson<Strategy[]>(`${API_BASE}/strategies`);
  }, []);

  const createStrategy = useCallback(async (strategy: CreateStrategyRequest): Promise<Strategy> => {
    return fetchJson<Strategy>(`${API_BASE}/strategies`, {
      method: 'POST',
      body: JSON.stringify(strategy),
    });
  }, []);

  const startStrategy = useCallback(async (id: string): Promise<void> => {
    await fetch(`${API_BASE}/strategies/${id}/start`, { method: 'POST' });
  }, []);

  const stopStrategy = useCallback(async (id: string): Promise<void> => {
    await fetch(`${API_BASE}/strategies/${id}/stop`, { method: 'POST' });
  }, []);

  const getStrategy = useCallback(async (id: string): Promise<Strategy> => {
    return fetchJson<Strategy>(`${API_BASE}/strategies/${id}`);
  }, []);

  const removeStrategy = useCallback(async (id: string): Promise<void> => {
    await fetch(`${API_BASE}/strategies/${id}`, { method: 'DELETE' });
  }, []);

  const getExchangeStatus = useCallback(async (): Promise<ExchangeStatus[]> => {
    return fetchJson<ExchangeStatus[]>(`${API_BASE}/exchanges/status`);
  }, []);

  const getSymbols = useCallback(async (exchange: string): Promise<TradingSymbol[]> => {
    return fetchJson<TradingSymbol[]>(`${API_BASE}/exchanges/${exchange}/symbols`);
  }, []);

  // Chart operations
  const getChartData = useCallback(async (
    exchange: string,
    symbol: string,
    interval: string = '5m',
    periods: number = 100
  ): Promise<ChartData> => {
    return fetchJson<ChartData>(
      `${API_BASE}/chart/${exchange}/${symbol}?interval=${interval}&periods=${periods}`
    );
  }, []);

  return {
    getEngineStatus,
    startEngine,
    stopEngine,
    getOrders,
    getActiveOrders,
    submitOrder,
    cancelOrder,
    getPositions,
    getStrategies,
    getStrategy,
    createStrategy,
    startStrategy,
    stopStrategy,
    removeStrategy,
    getExchangeStatus,
    getSymbols,
    getChartData,
  };
}
