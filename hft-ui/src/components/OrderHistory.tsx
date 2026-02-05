import { useState, useEffect, useCallback } from 'react';
import { useApi } from '../hooks/useApi';
import { formatPrice } from '../utils/format';
import type { Order, Strategy } from '../types/api';

interface Props {
  strategies: Strategy[];
  onBack?: () => void;  // Optional - navigation handled by app header
}

function getStrategyName(strategyId: string | null | undefined, strategies: Strategy[]): string {
  if (!strategyId) return '-';
  const strategy = strategies.find(s => s.id === strategyId);
  return strategy ? strategy.name : strategyId;
}

const ORDER_STATUSES = [
  'PENDING',
  'SUBMITTED',
  'ACCEPTED',
  'PARTIALLY_FILLED',
  'FILLED',
  'CANCELLED',
  'REJECTED',
  'EXPIRED',
];

export function OrderHistory({ strategies }: Props) {
  const api = useApi();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [strategyId, setStrategyId] = useState('');
  const [status, setStatus] = useState('');
  const [symbol, setSymbol] = useState('');

  // Pagination
  const [offset, setOffset] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const limit = 50;

  const searchOrders = useCallback(async (newOffset = 0) => {
    setLoading(true);
    setError(null);
    try {
      const results = await api.searchOrders(
        strategyId || undefined,
        status || undefined,
        symbol || undefined,
        limit,
        newOffset
      );
      setOrders(results);
      setOffset(newOffset);
      setHasMore(results.length === limit);
    } catch (err) {
      setError('Failed to search orders');
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [api, strategyId, status, symbol]);

  // Initial load
  useEffect(() => {
    searchOrders(0);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    searchOrders(0);
  };

  const handleReset = () => {
    setStrategyId('');
    setStatus('');
    setSymbol('');
    setOffset(0);
  };

  const handlePrevPage = () => {
    if (offset >= limit) {
      searchOrders(offset - limit);
    }
  };

  const handleNextPage = () => {
    if (hasMore) {
      searchOrders(offset + limit);
    }
  };

  return (
    <main className="order-history">
      <div className="order-history-header">
        <h2>Order History</h2>
      </div>

      <div className="card">
        <form onSubmit={handleSearch} className="order-filters">
          <div className="filter-row">
            <div className="filter-group">
              <label>Strategy:</label>
              <select value={strategyId} onChange={(e) => setStrategyId(e.target.value)}>
                <option value="">All Strategies</option>
                {strategies.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.id})
                  </option>
                ))}
              </select>
            </div>

            <div className="filter-group">
              <label>Status:</label>
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">All Statuses</option>
                {ORDER_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>

            <div className="filter-group">
              <label>Symbol:</label>
              <input
                type="text"
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
                placeholder="Search symbol..."
              />
            </div>

            <div className="filter-actions">
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? 'Searching...' : 'Search'}
              </button>
              <button type="button" className="btn-secondary" onClick={handleReset}>
                Reset
              </button>
            </div>
          </div>
        </form>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="card">
        {orders.length === 0 ? (
          <p className="empty-message">No orders found</p>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Strategy</th>
                  <th>Symbol</th>
                  <th>Side</th>
                  <th>Type</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Filled</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => (
                  <tr key={order.clientOrderId}>
                    <td>{order.clientOrderId}</td>
                    <td className="strategy-name">{getStrategyName(order.strategyId, strategies)}</td>
                    <td>{order.symbol}</td>
                    <td className={order.side === 'BUY' ? 'buy' : 'sell'}>{order.side}</td>
                    <td>{order.type}</td>
                    <td>{order.quantity}</td>
                    <td>{order.price > 0 ? formatPrice(order.price, order.priceScale || 100) : '-'}</td>
                    <td>{order.filledQuantity}/{order.quantity}</td>
                    <td>
                      <span className={`status-badge status-${order.status.toLowerCase()}`}>
                        {order.status}
                      </span>
                    </td>
                    <td className="reject-reason" title={order.rejectReason || undefined}>
                      {order.rejectReason || '-'}
                    </td>
                    <td>{new Date(order.createdAt / 1000000).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="pagination">
              <button
                className="btn-secondary"
                onClick={handlePrevPage}
                disabled={offset === 0 || loading}
              >
                ← Previous
              </button>
              <span className="pagination-info">
                Showing {offset + 1} - {offset + orders.length}
              </span>
              <button
                className="btn-secondary"
                onClick={handleNextPage}
                disabled={!hasMore || loading}
              >
                Next →
              </button>
            </div>
          </>
        )}
      </div>
    </main>
  );
}
