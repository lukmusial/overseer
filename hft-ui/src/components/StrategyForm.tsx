import { useState } from 'react';
import type { StrategyType, CreateStrategyRequest } from '../types/api';

interface Props {
  onSubmit: (strategy: CreateStrategyRequest) => void;
}

const STRATEGY_TYPES: StrategyType[] = ['VWAP', 'TWAP', 'MOMENTUM', 'MEAN_REVERSION'];
const EXCHANGES = ['ALPACA', 'BINANCE'];

const DEFAULT_PARAMS: Record<StrategyType, Record<string, string>> = {
  VWAP: { targetQuantity: '1000', durationMinutes: '30', participationRate: '0.1' },
  TWAP: { targetQuantity: '1000', durationMinutes: '30', sliceCount: '10' },
  MOMENTUM: { lookbackPeriod: '20', momentumThreshold: '0.02', positionSize: '100' },
  MEAN_REVERSION: { lookbackPeriod: '20', stdDevThreshold: '2.0', positionSize: '100' },
};

export function StrategyForm({ onSubmit }: Props) {
  const [name, setName] = useState('');
  const [type, setType] = useState<StrategyType>('VWAP');
  const [symbol, setSymbol] = useState('');
  const [exchange, setExchange] = useState('ALPACA');
  const [parameters, setParameters] = useState<Record<string, string>>(DEFAULT_PARAMS.VWAP);

  const handleTypeChange = (newType: StrategyType) => {
    setType(newType);
    setParameters(DEFAULT_PARAMS[newType]);
  };

  const handleParamChange = (key: string, value: string) => {
    setParameters((prev) => ({ ...prev, [key]: value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({ name, type, symbol, exchange, parameters });
    setName('');
    setSymbol('');
  };

  return (
    <div className="card">
      <h2>Create Strategy</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Name:</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            placeholder="My VWAP Strategy"
          />
        </div>
        <div className="form-group">
          <label>Type:</label>
          <select value={type} onChange={(e) => handleTypeChange(e.target.value as StrategyType)}>
            {STRATEGY_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label>Symbol:</label>
          <input
            type="text"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value.toUpperCase())}
            required
            placeholder="AAPL"
          />
        </div>
        <div className="form-group">
          <label>Exchange:</label>
          <select value={exchange} onChange={(e) => setExchange(e.target.value)}>
            {EXCHANGES.map((ex) => (
              <option key={ex} value={ex}>{ex}</option>
            ))}
          </select>
        </div>
        <h3>Parameters</h3>
        {Object.entries(parameters).map(([key, value]) => (
          <div key={key} className="form-group">
            <label>{key}:</label>
            <input
              type="text"
              value={value}
              onChange={(e) => handleParamChange(key, e.target.value)}
            />
          </div>
        ))}
        <button type="submit" className="btn-primary">Create Strategy</button>
      </form>
    </div>
  );
}
