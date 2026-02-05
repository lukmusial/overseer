import type { Position } from '../types/api';
import { formatPrice, formatPnl } from '../utils/format';

interface Props {
  positions: Position[];
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
          {openPositions.map((pos) => {
            const scale = pos.priceScale || 100;
            return (
              <tr key={`${pos.symbol}-${pos.exchange}`}>
                <td>{pos.symbol}</td>
                <td>{pos.exchange}</td>
                <td className={pos.isLong ? 'long' : 'short'}>
                  {pos.isLong ? '+' : ''}{pos.quantity}
                </td>
                <td>${formatPrice(pos.averageEntryPrice, scale)}</td>
                <td>${formatPrice(pos.marketPrice, scale)}</td>
                <td>${formatPrice(pos.marketValue, scale)}</td>
                <td className={pos.unrealizedPnl >= 0 ? 'profit' : 'loss'}>
                  {formatPnl(pos.unrealizedPnl, scale)}
                </td>
                <td className={pos.realizedPnl >= 0 ? 'profit' : 'loss'}>
                  {formatPnl(pos.realizedPnl, scale)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
