import { useState, useCallback } from 'react';
import type { Order, Strategy } from '../types/api';
import { formatPrice } from '../utils/format';

interface Props {
  orders: Order[];
  strategies: Strategy[];
  onCancel: (orderId: number) => Promise<void>;
  maxOrders?: number;
  showViewAll?: boolean;
  onViewAll?: () => void;
}

function getStrategyDisplay(order: Order, strategies: Strategy[]): string {
  if (order.strategyName) return order.strategyName;
  if (!order.strategyId) return '-';
  const strategy = strategies.find(s => s.id === order.strategyId);
  return strategy ? strategy.name : order.strategyId;
}

type CancelState = 'idle' | 'cancelling' | 'success' | 'error';

export function OrderList({ orders, strategies, onCancel, maxOrders, showViewAll, onViewAll }: Props) {
  const [cancelStates, setCancelStates] = useState<Record<number, CancelState>>({});

  const handleCancel = useCallback(async (orderId: number) => {
    setCancelStates(prev => ({ ...prev, [orderId]: 'cancelling' }));
    try {
      await onCancel(orderId);
      setCancelStates(prev => ({ ...prev, [orderId]: 'success' }));
      // Clear success state after 2 seconds
      setTimeout(() => {
        setCancelStates(prev => {
          const next = { ...prev };
          delete next[orderId];
          return next;
        });
      }, 2000);
    } catch {
      setCancelStates(prev => ({ ...prev, [orderId]: 'error' }));
      // Clear error state after 3 seconds
      setTimeout(() => {
        setCancelStates(prev => {
          const next = { ...prev };
          delete next[orderId];
          return next;
        });
      }, 3000);
    }
  }, [onCancel]);

  if (orders.length === 0) {
    return (
      <div className="card">
        <h2>Orders</h2>
        <p className="empty-message">No orders</p>
      </div>
    );
  }

  const canCancel = (status: string) =>
    ['PENDING', 'SUBMITTED', 'ACCEPTED', 'PARTIALLY_FILLED'].includes(status);

  const displayOrders = maxOrders ? orders.slice(0, maxOrders) : orders;
  const hasMore = maxOrders && orders.length > maxOrders;

  const getCancelButtonContent = (orderId: number, status: string) => {
    const cancelState = cancelStates[orderId];

    // If order status changed to cancelled/filled, don't show button
    if (!canCancel(status)) {
      if (cancelState === 'success') {
        return <span className="cancel-feedback success">Cancelled</span>;
      }
      return null;
    }

    switch (cancelState) {
      case 'cancelling':
        return (
          <button className="btn-small btn-secondary" disabled>
            Cancelling...
          </button>
        );
      case 'success':
        return <span className="cancel-feedback success">Cancelled</span>;
      case 'error':
        return (
          <>
            <span className="cancel-feedback error">Failed</span>
            <button
              onClick={() => handleCancel(orderId)}
              className="btn-small btn-danger"
            >
              Retry
            </button>
          </>
        );
      default:
        return (
          <button
            onClick={() => handleCancel(orderId)}
            className="btn-small btn-danger"
          >
            Cancel
          </button>
        );
    }
  };

  return (
    <div className="card">
      <div className="card-header">
        <h2>Orders</h2>
        {(showViewAll || hasMore) && onViewAll && (
          <button className="btn-link" onClick={onViewAll}>
            View all orders →
          </button>
        )}
      </div>
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
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {displayOrders.map((order) => (
            <tr key={order.clientOrderId}>
              <td>{order.clientOrderId}</td>
              <td className="strategy-name">{getStrategyDisplay(order, strategies)}</td>
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
              <td className="actions-cell">
                {getCancelButtonContent(order.clientOrderId, order.status)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
