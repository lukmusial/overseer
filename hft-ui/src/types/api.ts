export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
export type OrderStatus = 'PENDING' | 'SUBMITTED' | 'ACCEPTED' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED' | 'EXPIRED';
export type TimeInForce = 'DAY' | 'GTC' | 'IOC' | 'FOK';
export type StrategyType = 'VWAP' | 'TWAP' | 'MOMENTUM' | 'MEAN_REVERSION';

export interface EngineStatus {
  running: boolean;
  startTime: number | null;
  uptimeMillis: number;
  totalOrdersProcessed: number;
  totalTradesExecuted: number;
  activeStrategies: number;
  openPositions: number;
  pendingOrders: number;
}

export interface Order {
  clientOrderId: number;
  exchangeOrderId: string | null;
  symbol: string;
  exchange: string;
  side: OrderSide;
  type: OrderType;
  timeInForce: TimeInForce;
  quantity: number;
  price: number;
  stopPrice: number;
  filledQuantity: number;
  averageFilledPrice: number;
  status: OrderStatus;
  rejectReason: string | null;
  strategyId: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface Position {
  symbol: string;
  exchange: string;
  quantity: number;
  averageEntryPrice: number;
  marketPrice: number;
  marketValue: number;
  realizedPnl: number;
  unrealizedPnl: number;
  maxDrawdown: number;
  isLong: boolean;
  isShort: boolean;
  isFlat: boolean;
}

export type AlgorithmState = 'CREATED' | 'INITIALIZED' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface StrategyStats {
  startTimeNanos: number;
  endTimeNanos: number;
  totalOrders: number;
  filledOrders: number;
  cancelledOrders: number;
  rejectedOrders: number;
  realizedPnl: number;
  unrealizedPnl: number;
  maxDrawdown: number;
}

export interface Strategy {
  id: string;
  name: string;
  type: string;
  state: AlgorithmState;
  symbols: string[];
  parameters: Record<string, unknown>;
  progress: number;
  stats: StrategyStats | null;
}

export interface ExchangeStatus {
  exchange: string;
  name: string;
  mode: string;
  connected: boolean;
  authenticated: boolean;
  lastHeartbeat: number | null;
  errorMessage: string | null;
}

export interface TradingSymbol {
  symbol: string;
  name: string;
  exchange: string;
  assetClass: string;
  baseAsset: string;
  quoteAsset: string;
  tradable: boolean;
  marginable: boolean;
  shortable: boolean;
}

export interface CreateOrderRequest {
  symbol: string;
  exchange: string;
  side: OrderSide;
  type: OrderType;
  timeInForce: TimeInForce;
  quantity: number;
  price?: number;
  stopPrice?: number;
  strategyId?: string;
}

export interface CreateStrategyRequest {
  name?: string;
  type: string;
  symbols: string[];
  exchange: string;
  parameters: Record<string, unknown>;
}

export interface Notification {
  type: string;
  message: string;
  timestamp: number;
}

// Chart data types
export interface Candle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface OrderMarker {
  time: number;
  price: number;
  side: OrderSide;
  quantity: number;
  status: string;
  strategyId: string | null;
  orderId: string;
}

export interface TriggerRange {
  strategyId: string;
  strategyName: string;
  type: string;
  symbol: string;
  currentPrice: number;
  buyTriggerLow: number | null;
  buyTriggerHigh: number | null;
  sellTriggerLow: number | null;
  sellTriggerHigh: number | null;
  description: string;
}

export const EXCHANGE_MODES: Record<string, string[]> = {
  ALPACA: ['stub', 'sandbox', 'live'],
  BINANCE: ['stub', 'testnet', 'live'],
};

export interface ChartData {
  symbol: string;
  exchange: string;
  interval: string;
  dataSource: string;  // "stub", "live", "testnet", "sandbox"
  candles: Candle[];
  orders: OrderMarker[];
  triggerRanges: TriggerRange[];
}
