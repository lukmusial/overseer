import type { Strategy } from '../types/api';

interface Props {
  strategies: Strategy[];
  onEnable: (id: string) => void;
  onDisable: (id: string) => void;
  onRemove: (id: string) => void;
}

export function StrategyList({ strategies, onEnable, onDisable, onRemove }: Props) {
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
      <h2>Strategies</h2>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Symbol</th>
            <th>Exchange</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {strategies.map((strategy) => (
            <tr key={strategy.id}>
              <td>{strategy.name}</td>
              <td>{strategy.type}</td>
              <td>{strategy.symbol}</td>
              <td>{strategy.exchange}</td>
              <td>
                <span className={`status-badge ${strategy.enabled ? 'enabled' : 'disabled'}`}>
                  {strategy.enabled ? 'Enabled' : 'Disabled'}
                </span>
              </td>
              <td>
                {strategy.enabled ? (
                  <button onClick={() => onDisable(strategy.id)} className="btn-small btn-warning">
                    Disable
                  </button>
                ) : (
                  <button onClick={() => onEnable(strategy.id)} className="btn-small btn-success">
                    Enable
                  </button>
                )}
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
