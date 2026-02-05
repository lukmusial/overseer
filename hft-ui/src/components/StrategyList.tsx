import type { Strategy } from '../types/api';
import { formatPnl } from '../utils/format';

interface Props {
  strategies: Strategy[];
  onStart: (id: string) => void;
  onStop: (id: string) => void;
  onRemove: (id: string) => void;
  onInspect: (strategy: Strategy) => void;
}

interface ParameterInfo {
  name: string;
  description: string;
}

interface StrategyExplanation {
  description: string;
  howItWorks: string[];
  diagram: string;
  whenItTrades: string[];
  parameters: ParameterInfo[];
}

const STRATEGY_EXPLANATIONS: Record<string, StrategyExplanation> = {
  Momentum: {
    description: 'Follows trends using EMA crossover signals.',
    howItWorks: [
      'Computes a short-period and long-period Exponential Moving Average (EMA) on each quote.',
      'When the short EMA crosses above the long EMA by more than the signal threshold, a BUY signal is generated.',
      'When the short EMA crosses below the long EMA by more than the threshold, a SELL signal is generated.',
    ],
    diagram: [
      '  Price',
      '   ^          ___--- Short EMA',
      '   |      _--/',
      '   |   _-/   \\___--- Long EMA',
      '   | -/ X             ',
      '   |/  BUY            ',
      '   +--------------------> Time',
      '        crossover point',
    ].join('\n'),
    whenItTrades: [
      'BUY when shortEMA > longEMA * (1 + threshold)',
      'SELL when shortEMA < longEMA * (1 - threshold)',
    ],
    parameters: [
      { name: 'shortPeriod', description: 'Number of quotes for the fast EMA (default: 10)' },
      { name: 'longPeriod', description: 'Number of quotes for the slow EMA (default: 30)' },
      { name: 'signalThreshold', description: 'Minimum EMA gap to trigger a signal as a ratio (default: 0.02 = 2%)' },
      { name: 'maxPositionSize', description: 'Maximum position size in units (default: 1000)' },
    ],
  },
  MeanReversion: {
    description: 'Trades reversals to the mean using Bollinger Bands / Z-score.',
    howItWorks: [
      'Maintains a rolling window of prices over the lookback period.',
      'Computes the rolling mean and standard deviation.',
      'Calculates a Z-score: (price - mean) / stddev.',
      'When the Z-score exceeds the entry threshold (price far from mean), it trades toward the mean.',
    ],
    diagram: [
      '  Price',
      '   ^   --- Upper Band (mean + Z*std)',
      '   |  /   \\        SELL here',
      '   | /     \\      /',
      '   |/  ------\\---/---- Mean',
      '   |          \\ /',
      '   |           X  BUY here',
      '   +------- Lower Band --------> Time',
    ].join('\n'),
    whenItTrades: [
      'BUY when Z-score < -entryZScore (price far below mean)',
      'SELL when Z-score > +entryZScore (price far above mean)',
      'EXIT when |Z-score| < exitZScore (price returns to mean)',
    ],
    parameters: [
      { name: 'lookbackPeriod', description: 'Number of prices in the rolling window (default: 20)' },
      { name: 'entryZScore', description: 'Z-score threshold to enter a trade (default: 2.0)' },
      { name: 'exitZScore', description: 'Z-score threshold to exit a trade (default: 0.5)' },
      { name: 'maxPositionSize', description: 'Maximum position size in units (default: 1000)' },
    ],
  },
  VWAP: {
    description: 'Executes a large order in volume-weighted time buckets.',
    howItWorks: [
      'Splits the target quantity across 10 time buckets over the duration.',
      'Each bucket releases a slice proportional to expected volume.',
      'Limits participation to maxParticipationRate of market volume per slice.',
      'Goal: match the Volume-Weighted Average Price of the session.',
    ],
    diagram: [
      '  Volume',
      '   ^',
      '   |  ##     ##',
      '   | ####   ####  ##',
      '   | #### # #### ####',
      '   | #### # #### ####',
      '   +---|---|---|---|---> Time',
      '     B1  B2  B3  ...  B10',
    ].join('\n'),
    whenItTrades: [
      'Submits child orders at each time bucket boundary.',
      'Adjusts slice sizes to match historical volume profile.',
    ],
    parameters: [
      { name: 'targetQuantity', description: 'Total quantity to execute (default: 1000)' },
      { name: 'durationMinutes', description: 'Time window for execution in minutes (default: 60)' },
      { name: 'maxParticipationRate', description: 'Max fraction of market volume per slice (default: 0.25)' },
      { name: 'side', description: 'Order side: BUY or SELL (default: BUY)' },
    ],
  },
  TWAP: {
    description: 'Executes a large order in equal time slices.',
    howItWorks: [
      'Divides the target quantity into equal slices at regular intervals.',
      'Submits one child order per slice interval.',
      'Simpler than VWAP -- ignores volume profile.',
      'Goal: achieve the Time-Weighted Average Price over the window.',
    ],
    diagram: [
      '  Qty/slice',
      '   ^',
      '   | ## ## ## ## ## ##',
      '   | ## ## ## ## ## ##',
      '   | ## ## ## ## ## ##',
      '   +--|--|--|--|--|----> Time',
      '    s1 s2 s3 s4 s5 s6',
    ].join('\n'),
    whenItTrades: [
      'Submits equal-sized orders at each slice interval.',
      'Continues until total targetQuantity is filled or duration expires.',
    ],
    parameters: [
      { name: 'targetQuantity', description: 'Total quantity to execute (default: 1000)' },
      { name: 'durationMinutes', description: 'Time window for execution in minutes (default: 60)' },
      { name: 'sliceIntervalSeconds', description: 'Seconds between each child order (default: 60)' },
      { name: 'maxParticipationRate', description: 'Max fraction of market volume per slice (default: 0.25)' },
      { name: 'side', description: 'Order side: BUY or SELL (default: BUY)' },
    ],
  },
};

function StrategyInfoTooltip({ strategy }: { strategy: Strategy }) {
  const explanation = STRATEGY_EXPLANATIONS[strategy.type];
  if (!explanation) return null;

  return (
    <span className="strategy-info-wrapper">
      <button className="info-btn" aria-label={`Info about ${strategy.type}`} type="button">i</button>
      <div className="strategy-tooltip" role="tooltip">
        <div className="strategy-tooltip-content">
          <div className="tooltip-header">{strategy.type} Strategy</div>
          <p className="tooltip-description">{explanation.description}</p>

          <div className="tooltip-section">
            <strong>How it works</strong>
            <ul>
              {explanation.howItWorks.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </div>

          <div className="tooltip-section">
            <strong>Signal diagram</strong>
            <pre className="tooltip-diagram">{explanation.diagram}</pre>
          </div>

          <div className="tooltip-section">
            <strong>When it trades</strong>
            <ul>
              {explanation.whenItTrades.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </div>

          <div className="tooltip-section">
            <strong>Parameters</strong>
            <div className="tooltip-params">
              {explanation.parameters.map((param) => (
                <div className="tooltip-param" key={param.name}>
                  <span className="param-name">{param.name}</span>
                  {strategy.parameters[param.name] !== undefined && (
                    <span className="param-value">= {String(strategy.parameters[param.name])}</span>
                  )}
                  <span className="param-desc">{param.description}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </span>
  );
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
              <td>
                <span className="type-with-info">
                  {strategy.type}
                  <StrategyInfoTooltip strategy={strategy} />
                </span>
              </td>
              <td>{strategy.symbols.join(', ')}</td>
              <td>
                <span className={`status-badge ${getStateColor(strategy.state)}`}>
                  {strategy.state}
                </span>
              </td>
              <td className={strategy.stats && (strategy.stats.realizedPnl + strategy.stats.unrealizedPnl) >= 0 ? 'profit' : 'loss'}>
                {strategy.stats ? formatPnl(strategy.stats.realizedPnl + strategy.stats.unrealizedPnl, strategy.priceScale) : '-'}
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
