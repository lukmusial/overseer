import type { ExchangeStatus } from '../types/api';

interface Props {
  exchanges: ExchangeStatus[];
}

function formatLastHeartbeat(timestamp: number | null): string {
  if (!timestamp) return 'Never';
  const now = Date.now();
  const diff = now - timestamp;
  if (diff < 1000) return 'Just now';
  if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`;
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  return new Date(timestamp).toLocaleTimeString();
}

export function ExchangeStatusPanel({ exchanges }: Props) {
  if (exchanges.length === 0) {
    return (
      <div className="card exchange-status">
        <h2>Exchange Connectivity</h2>
        <p className="empty-message">No exchanges configured</p>
      </div>
    );
  }

  return (
    <div className="card exchange-status">
      <h2>Exchange Connectivity</h2>
      <div className="exchange-list">
        {exchanges.map((exchange) => (
          <div key={exchange.exchange} className="exchange-item">
            <div className="exchange-header">
              <span className={`status-indicator ${exchange.connected ? 'connected' : 'disconnected'}`} />
              <span className="exchange-name">{exchange.name}</span>
              {exchange.mode && (
                <span className={`mode-badge mode-${exchange.mode}`}>
                  {exchange.mode.charAt(0).toUpperCase() + exchange.mode.slice(1)}
                </span>
              )}
            </div>
            <div className="exchange-details">
              <div className="detail-item">
                <span className="label">Status:</span>
                <span className={`value ${exchange.connected ? 'connected' : 'disconnected'}`}>
                  {exchange.connected
                    ? (exchange.authenticated ? 'Connected & Authenticated' : 'Connected')
                    : 'Disconnected'}
                </span>
              </div>
              {exchange.lastHeartbeat && (
                <div className="detail-item">
                  <span className="label">Last Heartbeat:</span>
                  <span className="value">{formatLastHeartbeat(exchange.lastHeartbeat)}</span>
                </div>
              )}
              {exchange.errorMessage && (
                <div className="detail-item error">
                  <span className="label">Error:</span>
                  <span className="value">{exchange.errorMessage}</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
