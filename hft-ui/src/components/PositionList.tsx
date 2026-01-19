import type { Position } from '../types/api';

interface Props {
  positions: Position[];
}

function formatPrice(price: number, scale: number = 100): string {
  return (price / scale).toFixed(2);
}

function formatPnl(pnl: number, scale: number = 100): string {
  const value = pnl / scale;
  const sign = value >= 0 ? '+' : '';
  return `${sign}$${value.toFixed(2)}`;
}

export function PositionList({ positions }: Props) {
  const openPositions = positions.filter((p) => !p.isFlat);

  if (openPositions.length === 0) {
    return (
      <div className="card">
        <h2>Positions</h2>
        <p className="empty-message">No open positions</p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>Positions</h2>
      <table>
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Exchange</th>
            <th>Qty</th>
            <th>Avg Entry</th>
            <th>Market</th>
            <th>Value</th>
            <th>Unrealized P&L</th>
            <th>Realized P&L</th>
          </tr>
        </thead>
        <tbody>
          {openPositions.map((pos) => (
            <tr key={`${pos.symbol}-${pos.exchange}`}>
              <td>{pos.symbol}</td>
              <td>{pos.exchange}</td>
              <td className={pos.isLong ? 'long' : 'short'}>
                {pos.isLong ? '+' : ''}{pos.quantity}
              </td>
              <td>${formatPrice(pos.averageEntryPrice)}</td>
              <td>${formatPrice(pos.marketPrice)}</td>
              <td>${formatPrice(pos.marketValue)}</td>
              <td className={pos.unrealizedPnl >= 0 ? 'profit' : 'loss'}>
                {formatPnl(pos.unrealizedPnl)}
              </td>
              <td className={pos.realizedPnl >= 0 ? 'profit' : 'loss'}>
                {formatPnl(pos.realizedPnl)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
