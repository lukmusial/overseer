import type { Order } from '../types/api';

interface Props {
  orders: Order[];
  onCancel: (orderId: number) => void;
}

function formatPrice(price: number, scale: number = 100): string {
  return (price / scale).toFixed(2);
}

export function OrderList({ orders, onCancel }: Props) {
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

  return (
    <div className="card">
      <h2>Orders</h2>
      <table>
        <thead>
          <tr>
            <th>ID</th>
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
          {orders.map((order) => (
            <tr key={order.clientOrderId}>
              <td>{order.clientOrderId}</td>
              <td>{order.symbol}</td>
              <td className={order.side === 'BUY' ? 'buy' : 'sell'}>{order.side}</td>
              <td>{order.type}</td>
              <td>{order.quantity}</td>
              <td>{order.price > 0 ? formatPrice(order.price) : '-'}</td>
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
