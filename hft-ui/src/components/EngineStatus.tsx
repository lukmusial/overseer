import type { EngineStatus as EngineStatusType } from '../types/api';

interface Props {
  status: EngineStatusType | null;
  onStart: () => void;
  onStop: () => void;
}

function formatUptime(millis: number): string {
  const seconds = Math.floor(millis / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) return `${days}d ${hours % 24}h ${minutes % 60}m`;
  if (hours > 0) return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
  return `${seconds}s`;
}

export function EngineStatus({ status, onStart, onStop }: Props) {
  if (!status) {
    return <div className="card">Loading engine status...</div>;
  }

  return (
    <div className="card">
      <h2>Engine Status</h2>
      <div className="status-grid">
        <div className="status-item">
          <span className="label">Status:</span>
          <span className={`value ${status.running ? 'running' : 'stopped'}`}>
            {status.running ? 'Running' : 'Stopped'}
          </span>
        </div>
        <div className="status-item">
          <span className="label">Uptime:</span>
          <span className="value">{formatUptime(status.uptimeMillis)}</span>
        </div>
        <div className="status-item">
          <span className="label">Orders Processed:</span>
          <span className="value">{status.totalOrdersProcessed}</span>
        </div>
        <div className="status-item">
          <span className="label">Trades Executed:</span>
          <span className="value">{status.totalTradesExecuted}</span>
        </div>
        <div className="status-item">
          <span className="label">Active Strategies:</span>
          <span className="value">{status.activeStrategies}</span>
        </div>
        <div className="status-item">
          <span className="label">Open Positions:</span>
          <span className="value">{status.openPositions}</span>
        </div>
        <div className="status-item">
          <span className="label">Pending Orders:</span>
          <span className="value">{status.pendingOrders}</span>
        </div>
      </div>
      <div className="actions">
        {status.running ? (
          <button onClick={onStop} className="btn-danger">Stop Engine</button>
        ) : (
          <button onClick={onStart} className="btn-success">Start Engine</button>
        )}
      </div>
    </div>
  );
}
