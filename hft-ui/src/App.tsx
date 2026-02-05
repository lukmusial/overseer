import { useState, useEffect, useCallback } from 'react';
import { useApi } from './hooks/useApi';
import { useWebSocket } from './hooks/useWebSocket';
import { EngineStatus } from './components/EngineStatus';
import { StrategyList } from './components/StrategyList';
import { StrategyForm } from './components/StrategyForm';
import { StrategyInspector } from './components/StrategyInspector';
import { OrderList } from './components/OrderList';
import { OrderHistory } from './components/OrderHistory';
import { PositionList } from './components/PositionList';
import { ExchangeStatusPanel } from './components/ExchangeStatusPanel';
import { ChartPanel } from './components/ChartPanel';
import { RiskLimitsPanel } from './components/RiskLimitsPanel';
import type {
  EngineStatus as EngineStatusType,
  Order,
  Position,
  Strategy,
  CreateStrategyRequest,
  ExchangeStatus as ExchangeStatusType,
  RiskLimits,
} from './types/api';
import './App.css';

const MAX_ORDERS_DISPLAY = 50;

export default function App() {
  const [engineStatus, setEngineStatus] = useState<EngineStatusType | null>(null);
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [exchanges, setExchanges] = useState<ExchangeStatusType[]>([]);
  const [wsConnected, setWsConnected] = useState(false);
  const [inspectedStrategy, setInspectedStrategy] = useState<Strategy | null>(null);
  const [symbolRefreshKey, setSymbolRefreshKey] = useState(0);
  const [showOrderHistory, setShowOrderHistory] = useState(false);
  const [showRiskLimits, setShowRiskLimits] = useState(false);
  const [riskLimits, setRiskLimits] = useState<RiskLimits | null>(null);

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
          api.getRecentOrders(MAX_ORDERS_DISPLAY),
          api.getPositions(),
        ]);
        setEngineStatus(status);
        setStrategies(strats);
        setOrders(ords);
        setPositions(pos);
      } catch (error) {
        console.error('Failed to load data:', error);
      }

      // Load exchange status separately (may not exist yet)
      try {
        const exchangeStatus = await api.getExchangeStatus();
        setExchanges(exchangeStatus);
      } catch (error) {
        console.log('Exchange status endpoint not available');
      }

      // Load risk limits
      try {
        const limits = await api.getRiskLimits();
        setRiskLimits(limits);
      } catch (error) {
        console.log('Risk limits endpoint not available');
      }
    };
    loadData();
  }, []);

  // Poll exchange status and risk limits every 5 seconds
  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const exchangeStatus = await api.getExchangeStatus();
        setExchanges(exchangeStatus);
      } catch {
        // Silently ignore - endpoint may not exist
      }
      try {
        const limits = await api.getRiskLimits();
        setRiskLimits(limits);
      } catch {
        // Silently ignore - endpoint may not exist
      }
    }, 5000);
    return () => clearInterval(interval);
  }, [api]);

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
        // Add new order at the front and cap at max
        const newOrders = [order, ...prev];
        return newOrders.slice(0, MAX_ORDERS_DISPLAY);
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
    const unsubStrategies = subscribe<Strategy>('/topic/strategies', (strategy) => {
      setStrategies((prev) => {
        const idx = prev.findIndex((s) => s.id === strategy.id);
        if (idx >= 0) {
          const updated = [...prev];
          updated[idx] = strategy;
          return updated;
        }
        return [...prev, strategy];
      });
    });

    return () => {
      unsubStatus();
      unsubOrders();
      unsubPositions();
      unsubStrategies();
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
      await api.createStrategy(request);
      const strats = await api.getStrategies();
      setStrategies(strats);
    } catch (error) {
      console.error('Failed to create strategy:', error);
    }
  }, [api]);

  const handleStartStrategy = useCallback(async (id: string) => {
    try {
      await api.startStrategy(id);
      setStrategies((prev) =>
        prev.map((s) => (s.id === id ? { ...s, state: 'RUNNING' } : s))
      );
    } catch (error) {
      console.error('Failed to start strategy:', error);
    }
  }, [api]);

  const handleStopStrategy = useCallback(async (id: string) => {
    try {
      await api.stopStrategy(id);
      setStrategies((prev) =>
        prev.map((s) => (s.id === id ? { ...s, state: 'STOPPED' } : s))
      );
    } catch (error) {
      console.error('Failed to stop strategy:', error);
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

  const handleInspectStrategy = useCallback((strategy: Strategy) => {
    setInspectedStrategy(strategy);
  }, []);

  // Exchange mode switching
  const handleSwitchMode = useCallback(async (exchange: string, mode: string) => {
    await api.switchMode(exchange, mode);
    const exchangeStatus = await api.getExchangeStatus();
    setExchanges(exchangeStatus);
    setSymbolRefreshKey((prev) => prev + 1);
  }, [api]);

  // Order operations
  const handleCancelOrder = useCallback(async (orderId: number): Promise<void> => {
    await api.cancelOrder(orderId);
  }, [api]);

  return (
    <div className="app">
      <header>
        <div className="header-left">
          <h1>HFT Trading Dashboard</h1>
          <nav className="main-nav">
            <button
              className={`nav-link ${!showOrderHistory && !showRiskLimits ? 'active' : ''}`}
              onClick={() => { setShowOrderHistory(false); setShowRiskLimits(false); }}
            >
              Dashboard
            </button>
            <button
              className={`nav-link ${showOrderHistory ? 'active' : ''}`}
              onClick={() => { setShowOrderHistory(true); setShowRiskLimits(false); }}
            >
              Order History
            </button>
            <button
              className={`nav-link ${showRiskLimits ? 'active' : ''}`}
              onClick={() => { setShowRiskLimits(true); setShowOrderHistory(false); }}
            >
              Risk Limits
            </button>
          </nav>
        </div>
        <div className={`connection-status ${wsConnected ? 'connected' : 'disconnected'}`}>
          {wsConnected ? 'WebSocket Connected' : 'WebSocket Disconnected'}
        </div>
      </header>

      {showRiskLimits ? (
        <RiskLimitsPanel riskLimits={riskLimits} onBack={() => setShowRiskLimits(false)} onUpdate={setRiskLimits} />
      ) : showOrderHistory ? (
        <OrderHistory strategies={strategies} onBack={() => setShowOrderHistory(false)} />
      ) : (
      <main>
        <div className="grid">
          <div className="col-left">
            <EngineStatus
              status={engineStatus}
              onStart={handleStartEngine}
              onStop={handleStopEngine}
            />
            <ExchangeStatusPanel exchanges={exchanges} onSwitchMode={handleSwitchMode} />
            <StrategyForm onSubmit={handleCreateStrategy} symbolRefreshKey={symbolRefreshKey} />
          </div>
          <div className="col-right">
            <ChartPanel exchanges={exchanges} strategies={strategies} symbolRefreshKey={symbolRefreshKey} subscribe={subscribe} />
            <StrategyList
              strategies={strategies}
              onStart={handleStartStrategy}
              onStop={handleStopStrategy}
              onRemove={handleRemoveStrategy}
              onInspect={handleInspectStrategy}
            />
            <PositionList positions={positions} />
            <OrderList
              orders={orders}
              strategies={strategies}
              onCancel={handleCancelOrder}
              maxOrders={MAX_ORDERS_DISPLAY}
              showViewAll={orders.length >= MAX_ORDERS_DISPLAY}
              onViewAll={() => setShowOrderHistory(true)}
            />
          </div>
        </div>
      </main>
      )}

      {inspectedStrategy && (
        <StrategyInspector
          strategy={inspectedStrategy}
          onClose={() => setInspectedStrategy(null)}
        />
      )}
    </div>
  );
}
