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

export interface Strategy {
  id: string;
  name: string;
  type: StrategyType;
  symbol: string;
  exchange: string;
  enabled: boolean;
  parameters: Record<string, string>;
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
  name: string;
  type: StrategyType;
  symbol: string;
  exchange: string;
  parameters: Record<string, string>;
}

export interface Notification {
  type: string;
  message: string;
  timestamp: number;
}
