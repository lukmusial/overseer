import type { Strategy } from '../types/api';

interface Props {
  strategies: Strategy[];
  onStart: (id: string) => void;
  onStop: (id: string) => void;
  onRemove: (id: string) => void;
  onInspect: (strategy: Strategy) => void;
}

function getStateColor(state: string): string {
  switch (state) {
    case 'RUNNING': return 'running';
    case 'STOPPED': case 'CANCELLED': return 'stopped';
    case 'COMPLETED': return 'completed';
    case 'FAILED': return 'failed';
    default: return 'pending';
  }
}

function formatPnl(value: number): string {
  const formatted = (value / 100).toFixed(2);
  return value >= 0 ? `+$${formatted}` : `-$${Math.abs(value / 100).toFixed(2)}`;
}

export function StrategyList({ strategies, onStart, onStop, onRemove, onInspect }: Props) {
  if (strategies.length === 0) {
    return (
      <div className="card">
        <h2>Strategies</h2>
        <p className="empty-message">No strategies configured</p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>Strategies ({strategies.length})</h2>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Symbols</th>
            <th>State</th>
            <th>P&L</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {strategies.map((strategy) => (
            <tr key={strategy.id}>
              <td>
                <button
                  className="link-button"
                  onClick={() => onInspect(strategy)}
                  title="Click to inspect"
                >
                  {strategy.name}
                </button>
              </td>
              <td>{strategy.type}</td>
              <td>{strategy.symbols.join(', ')}</td>
              <td>
                <span className={`status-badge ${getStateColor(strategy.state)}`}>
                  {strategy.state}
                </span>
              </td>
              <td className={strategy.stats && strategy.stats.realizedPnl >= 0 ? 'profit' : 'loss'}>
                {strategy.stats ? formatPnl(strategy.stats.realizedPnl + strategy.stats.unrealizedPnl) : '-'}
              </td>
              <td className="actions">
                {strategy.state === 'RUNNING' ? (
                  <button onClick={() => onStop(strategy.id)} className="btn-small btn-warning">
                    Stop
                  </button>
                ) : (
                  <button onClick={() => onStart(strategy.id)} className="btn-small btn-success">
                    Start
                  </button>
                )}
                <button onClick={() => onInspect(strategy)} className="btn-small btn-info">
                  Inspect
                </button>
                <button onClick={() => onRemove(strategy.id)} className="btn-small btn-danger">
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
