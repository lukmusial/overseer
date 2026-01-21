import { useState, useEffect } from 'react';
import type { CreateStrategyRequest, TradingSymbol } from '../types/api';
import { useApi } from '../hooks/useApi';

interface Props {
  onSubmit: (strategy: CreateStrategyRequest) => void;
}

type StrategyType = 'momentum' | 'meanreversion' | 'vwap' | 'twap';

const STRATEGY_TYPES: { value: StrategyType; label: string; description: string }[] = [
  { value: 'momentum', label: 'Momentum', description: 'Follow price trends using EMA crossovers' },
  { value: 'meanreversion', label: 'Mean Reversion', description: 'Trade price deviations from historical mean' },
  { value: 'vwap', label: 'VWAP', description: 'Volume-weighted average price execution' },
  { value: 'twap', label: 'TWAP', description: 'Time-weighted average price execution' },
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
  vwap: {
    targetQuantity: 1000,
    durationMinutes: 60,
    maxParticipationRate: 0.25,
  },
  twap: {
    targetQuantity: 1000,
    durationMinutes: 60,
    sliceIntervalSeconds: 60,
    maxParticipationRate: 0.25,
  },
};

export function StrategyForm({ onSubmit }: Props) {
  const { getSymbols } = useApi();
  const [name, setName] = useState('');
  const [type, setType] = useState<StrategyType>('momentum');
  const [symbol, setSymbol] = useState('');
  const [exchange, setExchange] = useState('ALPACA');
  const [parameters, setParameters] = useState<Record<string, unknown>>(DEFAULT_PARAMS.momentum);
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loadingSymbols, setLoadingSymbols] = useState(false);
  const [symbolFilter, setSymbolFilter] = useState('');

  // Fetch symbols when exchange changes
  useEffect(() => {
    let cancelled = false;
    setLoadingSymbols(true);
    setSymbol('');
    setSymbolFilter('');

    getSymbols(exchange)
      .then((data) => {
        if (!cancelled) {
          setSymbols(data);
        }
      })
      .catch((err) => {
        console.error('Failed to fetch symbols:', err);
        if (!cancelled) {
          setSymbols([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSymbols(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [exchange, getSymbols]);

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
      name: name || undefined,
      type,
      symbols: [symbol],
      exchange,
      parameters,
    });
    setName('');
    setSymbol('');
    setSymbolFilter('');
  };

  const selectedType = STRATEGY_TYPES.find(t => t.value === type);

  // Filter symbols based on user input
  const filteredSymbols = symbols.filter(
    (s) =>
      s.symbol.toLowerCase().includes(symbolFilter.toLowerCase()) ||
      s.name.toLowerCase().includes(symbolFilter.toLowerCase())
  );

  return (
    <div className="card">
      <h2>Create Strategy</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Name (optional):</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="My Strategy"
          />
        </div>
        <div className="form-group">
          <label>Type:</label>
          <select value={type} onChange={(e) => handleTypeChange(e.target.value as StrategyType)}>
            {STRATEGY_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
            ))}
          </select>
          {selectedType && <small className="type-description">{selectedType.description}</small>}
        </div>
        <div className="form-group">
          <label>Exchange:</label>
          <select value={exchange} onChange={(e) => setExchange(e.target.value)}>
            {EXCHANGES.map((ex) => (
              <option key={ex} value={ex}>{ex}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label>Symbol:</label>
          {loadingSymbols ? (
            <div className="loading-indicator">Loading symbols...</div>
          ) : (
            <>
              <input
                type="text"
                value={symbolFilter}
                onChange={(e) => setSymbolFilter(e.target.value)}
                placeholder="Search symbols..."
                className="symbol-filter"
              />
              <select
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
                required
                size={5}
                className="symbol-select"
              >
                <option value="">-- Select a symbol --</option>
                {filteredSymbols.slice(0, 50).map((s) => (
                  <option key={s.symbol} value={s.symbol}>
                    {s.symbol} - {s.name}
                  </option>
                ))}
                {filteredSymbols.length > 50 && (
                  <option disabled>... {filteredSymbols.length - 50} more (filter to narrow)</option>
                )}
              </select>
              {symbol && (
                <small className="selected-symbol">Selected: {symbol}</small>
              )}
            </>
          )}
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
