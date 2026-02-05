import type { Order } from '../types/api';
import { formatPrice } from '../utils/format';

interface Props {
  orders: Order[];
  onCancel: (orderId: number) => void;
  maxOrders?: number;
  showViewAll?: boolean;
  onViewAll?: () => void;
}

export function OrderList({ orders, onCancel, maxOrders, showViewAll, onViewAll }: Props) {
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
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {displayOrders.map((order) => (
            <tr key={order.clientOrderId}>
              <td>{order.clientOrderId}</td>
              <td className="strategy-id">{order.strategyId || '-'}</td>
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
              <td>
                {canCancel(order.status) && (
                  <button
                    onClick={() => onCancel(order.clientOrderId)}
                    className="btn-small btn-danger"
                  >
                    Cancel
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
