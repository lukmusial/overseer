import type { Strategy } from '../types/api';
import { formatPnl } from '../utils/format';

interface Props {
  strategy: Strategy;
  onClose: () => void;
}

function formatValue(value: unknown): string {
  if (typeof value === 'number') {
    return value.toLocaleString();
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No';
  }
  return String(value);
}

export function StrategyInspector({ strategy, onClose }: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Strategy: {strategy.name}</h2>
          <button className="close-btn" onClick={onClose}>&times;</button>
        </div>
        <div className="modal-body">
          <section>
            <h3>General Information</h3>
            <div className="info-grid">
              <div className="info-item">
                <span className="label">ID:</span>
                <span className="value mono">{strategy.id}</span>
              </div>
              <div className="info-item">
                <span className="label">Type:</span>
                <span className="value">{strategy.type}</span>
              </div>
              <div className="info-item">
                <span className="label">State:</span>
                <span className={`value status-badge ${strategy.state.toLowerCase()}`}>
                  {strategy.state}
                </span>
              </div>
              <div className="info-item">
                <span className="label">Symbols:</span>
                <span className="value">{strategy.symbols.join(', ')}</span>
              </div>
              <div className="info-item">
                <span className="label">Progress:</span>
                <span className="value">{(strategy.progress * 100).toFixed(1)}%</span>
              </div>
            </div>
          </section>

          <section>
            <h3>Parameters</h3>
            <div className="info-grid">
              {Object.entries(strategy.parameters).map(([key, value]) => (
                <div className="info-item" key={key}>
                  <span className="label">{key}:</span>
                  <span className="value">{formatValue(value)}</span>
                </div>
              ))}
            </div>
          </section>

          {strategy.stats && (
            <section>
              <h3>Performance Statistics</h3>
              <div className="info-grid">
                <div className="info-item">
                  <span className="label">Total Orders:</span>
                  <span className="value">{strategy.stats.totalOrders}</span>
                </div>
                <div className="info-item">
                  <span className="label">Filled Orders:</span>
                  <span className="value">{strategy.stats.filledOrders}</span>
                </div>
                <div className="info-item">
                  <span className="label">Cancelled:</span>
                  <span className="value">{strategy.stats.cancelledOrders}</span>
                </div>
                <div className="info-item">
                  <span className="label">Rejected:</span>
                  <span className="value">{strategy.stats.rejectedOrders}</span>
                </div>
                <div className="info-item">
                  <span className="label">Realized P&L:</span>
                  <span className={`value ${strategy.stats.realizedPnl >= 0 ? 'profit' : 'loss'}`}>
                    {formatPnl(strategy.stats.realizedPnl, strategy.priceScale)}
                  </span>
                </div>
                <div className="info-item">
                  <span className="label">Unrealized P&L:</span>
                  <span className={`value ${strategy.stats.unrealizedPnl >= 0 ? 'profit' : 'loss'}`}>
                    {formatPnl(strategy.stats.unrealizedPnl, strategy.priceScale)}
                  </span>
                </div>
                <div className="info-item">
                  <span className="label">Max Drawdown:</span>
                  <span className="value loss">{formatPnl(-strategy.stats.maxDrawdown, strategy.priceScale)}</span>
                </div>
              </div>
            </section>
          )}
        </div>
        <div className="modal-footer">
          <button onClick={onClose} className="btn-secondary">Close</button>
        </div>
      </div>
    </div>
  );
}
