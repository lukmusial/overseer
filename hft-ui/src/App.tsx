import { useState, useEffect, useCallback } from 'react';
import { useApi } from './hooks/useApi';
import { useWebSocket } from './hooks/useWebSocket';
import { EngineStatus } from './components/EngineStatus';
import { StrategyList } from './components/StrategyList';
import { StrategyForm } from './components/StrategyForm';
import { OrderList } from './components/OrderList';
import { PositionList } from './components/PositionList';
import type {
  EngineStatus as EngineStatusType,
  Order,
  Position,
  Strategy,
  CreateStrategyRequest,
} from './types/api';
import './App.css';

export default function App() {
  const [engineStatus, setEngineStatus] = useState<EngineStatusType | null>(null);
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [wsConnected, setWsConnected] = useState(false);

  const api = useApi();
  const { connected, subscribe } = useWebSocket({
    onConnect: () => setWsConnected(true),
    onDisconnect: () => setWsConnected(false),
  });

  // Initial data load
  useEffect(() => {
    const loadData = async () => {
      try {
        const [status, strats, ords, pos] = await Promise.all([
          api.getEngineStatus(),
          api.getStrategies(),
          api.getOrders(),
          api.getPositions(),
        ]);
        setEngineStatus(status);
        setStrategies(strats);
        setOrders(ords);
        setPositions(pos);
      } catch (error) {
        console.error('Failed to load data:', error);
      }
    };
    loadData();
  }, []);

  // WebSocket subscriptions
  useEffect(() => {
    if (!connected) return;

    const unsubStatus = subscribe<EngineStatusType>('/topic/engine/status', setEngineStatus);
    const unsubOrders = subscribe<Order>('/topic/orders', (order) => {
      setOrders((prev) => {
        const idx = prev.findIndex((o) => o.clientOrderId === order.clientOrderId);
        if (idx >= 0) {
          const updated = [...prev];
          updated[idx] = order;
          return updated;
        }
        return [order, ...prev];
      });
    });
    const unsubPositions = subscribe<Position>('/topic/positions', (position) => {
      setPositions((prev) => {
        const key = `${position.symbol}-${position.exchange}`;
        const idx = prev.findIndex((p) => `${p.symbol}-${p.exchange}` === key);
        if (idx >= 0) {
          const updated = [...prev];
          updated[idx] = position;
          return updated;
        }
        return [position, ...prev];
      });
    });

    return () => {
      unsubStatus();
      unsubOrders();
      unsubPositions();
    };
  }, [connected, subscribe]);

  // Engine controls
  const handleStartEngine = useCallback(async () => {
    try {
      await api.startEngine();
    } catch (error) {
      console.error('Failed to start engine:', error);
    }
  }, [api]);

  const handleStopEngine = useCallback(async () => {
    try {
      await api.stopEngine();
    } catch (error) {
      console.error('Failed to stop engine:', error);
    }
  }, [api]);

  // Strategy operations
  const handleCreateStrategy = useCallback(async (request: CreateStrategyRequest) => {
    try {
      const strategy = await api.createStrategy(request);
      setStrategies((prev) => [...prev, strategy]);
    } catch (error) {
      console.error('Failed to create strategy:', error);
    }
  }, [api]);

  const handleEnableStrategy = useCallback(async (id: string) => {
    try {
      await api.enableStrategy(id);
      setStrategies((prev) =>
        prev.map((s) => (s.id === id ? { ...s, enabled: true } : s))
      );
    } catch (error) {
      console.error('Failed to enable strategy:', error);
    }
  }, [api]);

  const handleDisableStrategy = useCallback(async (id: string) => {
    try {
      await api.disableStrategy(id);
      setStrategies((prev) =>
        prev.map((s) => (s.id === id ? { ...s, enabled: false } : s))
      );
    } catch (error) {
      console.error('Failed to disable strategy:', error);
    }
  }, [api]);

  const handleRemoveStrategy = useCallback(async (id: string) => {
    try {
      await api.removeStrategy(id);
      setStrategies((prev) => prev.filter((s) => s.id !== id));
    } catch (error) {
      console.error('Failed to remove strategy:', error);
    }
  }, [api]);

  // Order operations
  const handleCancelOrder = useCallback(async (orderId: number) => {
    try {
      await api.cancelOrder(orderId);
    } catch (error) {
      console.error('Failed to cancel order:', error);
    }
  }, [api]);

  return (
    <div className="app">
      <header>
        <h1>HFT Trading Dashboard</h1>
        <div className={`connection-status ${wsConnected ? 'connected' : 'disconnected'}`}>
          {wsConnected ? 'Connected' : 'Disconnected'}
        </div>
      </header>
      <main>
        <div className="grid">
          <div className="col-left">
            <EngineStatus
              status={engineStatus}
              onStart={handleStartEngine}
              onStop={handleStopEngine}
            />
            <StrategyForm onSubmit={handleCreateStrategy} />
          </div>
          <div className="col-right">
            <StrategyList
              strategies={strategies}
              onEnable={handleEnableStrategy}
              onDisable={handleDisableStrategy}
              onRemove={handleRemoveStrategy}
            />
            <PositionList positions={positions} />
            <OrderList orders={orders} onCancel={handleCancelOrder} />
          </div>
        </div>
      </main>
    </div>
  );
}
