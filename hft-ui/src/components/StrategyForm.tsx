import { useState } from 'react';
import type { CreateStrategyRequest } from '../types/api';

interface Props {
  onSubmit: (strategy: CreateStrategyRequest) => void;
}

type StrategyType = 'momentum' | 'meanreversion';

const STRATEGY_TYPES: { value: StrategyType; label: string }[] = [
  { value: 'momentum', label: 'Momentum' },
  { value: 'meanreversion', label: 'Mean Reversion' },
];

const EXCHANGES = ['ALPACA', 'BINANCE'];

const DEFAULT_PARAMS: Record<StrategyType, Record<string, unknown>> = {
  momentum: {
    shortPeriod: 10,
    longPeriod: 30,
    signalThreshold: 0.02,
    maxPositionSize: 1000,
  },
  meanreversion: {
    lookbackPeriod: 20,
    entryZScore: 2.0,
    exitZScore: 0.5,
    maxPositionSize: 1000,
  },
};

export function StrategyForm({ onSubmit }: Props) {
  const [type, setType] = useState<StrategyType>('momentum');
  const [symbol, setSymbol] = useState('');
  const [exchange, setExchange] = useState('ALPACA');
  const [parameters, setParameters] = useState<Record<string, unknown>>(DEFAULT_PARAMS.momentum);

  const handleTypeChange = (newType: StrategyType) => {
    setType(newType);
    setParameters(DEFAULT_PARAMS[newType]);
  };

  const handleParamChange = (key: string, value: string) => {
    const numValue = parseFloat(value);
    setParameters((prev) => ({
      ...prev,
      [key]: isNaN(numValue) ? value : numValue,
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      type,
      symbols: [symbol],
      exchange,
      parameters,
    });
    setSymbol('');
  };

  return (
    <div className="card">
      <h2>Create Strategy</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Type:</label>
          <select value={type} onChange={(e) => handleTypeChange(e.target.value as StrategyType)}>
            {STRATEGY_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
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
              value={String(value)}
              onChange={(e) => handleParamChange(key, e.target.value)}
            />
          </div>
        ))}
        <button type="submit" className="btn-primary">Create Strategy</button>
      </form>
    </div>
  );
}
