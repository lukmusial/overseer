import { useState, useEffect, useRef } from 'react';
import type { CreateStrategyRequest, TradingSymbol } from '../types/api';
import { useApi } from '../hooks/useApi';

interface Props {
  onSubmit: (strategy: CreateStrategyRequest) => Promise<void>;
  symbolRefreshKey?: number;
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
    side: 'BUY',
  },
  twap: {
    targetQuantity: 1000,
    durationMinutes: 60,
    sliceIntervalSeconds: 60,
    maxParticipationRate: 0.25,
    side: 'BUY',
  },
};

export function StrategyForm({ onSubmit, symbolRefreshKey }: Props) {
  const { getSymbols } = useApi();
  const [name, setName] = useState('');
  const [type, setType] = useState<StrategyType>('momentum');
  const [selectedSymbol, setSelectedSymbol] = useState<TradingSymbol | null>(null);
  const [exchange, setExchange] = useState('ALPACA');
  const [parameters, setParameters] = useState<Record<string, unknown>>(DEFAULT_PARAMS.momentum);
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loadingSymbols, setLoadingSymbols] = useState(false);
  const [symbolInput, setSymbolInput] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [symbolError, setSymbolError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  // Scroll highlighted item into view
  useEffect(() => {
    if (highlightedIndex >= 0 && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement;
      if (item) {
        item.scrollIntoView?.({ block: 'nearest' });
      }
    }
  }, [highlightedIndex]);

  // Fetch symbols when exchange changes
  useEffect(() => {
    let cancelled = false;
    setLoadingSymbols(true);
    setSelectedSymbol(null);
    setSymbolInput('');
    setSymbolError('');

    getSymbols(exchange)
      .then((data) => {
        if (!cancelled) {
          setSymbols(data.sort((a, b) => a.symbol.localeCompare(b.symbol)));
        }
      })
      .catch((err) => {
        console.error('Failed to fetch symbols:', err);
        if (!cancelled) {
          setSymbols([]);
          setSymbolError('Failed to load symbols');
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
  }, [exchange, getSymbols, symbolRefreshKey]);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleTypeChange = (newType: StrategyType) => {
    setType(newType);
    setParameters(DEFAULT_PARAMS[newType]);
  };

  const handleParamChange = (key: string, value: string) => {
    // Store raw string during editing to preserve intermediate
    // decimal input like "0.", "0.0" (parseFloat would swallow these)
    setParameters((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  // Filter symbols based on input
  const filteredSymbols = symbols.filter(
    (s) =>
      s.symbol.toLowerCase().includes(symbolInput.toLowerCase()) ||
      s.name.toLowerCase().includes(symbolInput.toLowerCase())
  );

  const handleSymbolInputChange = (value: string) => {
    // Clear existing selection when user starts typing
    if (selectedSymbol && value !== selectedSymbol.symbol) {
      setSelectedSymbol(null);
    }
    setSymbolInput(value);
    setShowDropdown(true);
    setHighlightedIndex(-1);
    setSymbolError('');

    // Check if input exactly matches a symbol
    const exactMatch = symbols.find(
      (s) => s.symbol.toLowerCase() === value.toLowerCase()
    );
    if (exactMatch) {
      setSelectedSymbol(exactMatch);
    }
  };

  const handleSymbolSelect = (sym: TradingSymbol) => {
    setSelectedSymbol(sym);
    setSymbolInput(sym.symbol);
    setShowDropdown(false);
    setSymbolError('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setShowDropdown(false);
      return;
    }

    if (filteredSymbols.length === 0) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        if (!showDropdown) {
          setShowDropdown(true);
          setHighlightedIndex(0);
        } else {
          setHighlightedIndex((prev) =>
            prev < Math.min(filteredSymbols.length, 10) - 1 ? prev + 1 : prev
          );
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (highlightedIndex >= 0 && highlightedIndex < filteredSymbols.length) {
          handleSymbolSelect(filteredSymbols[highlightedIndex]);
        }
        break;
    }
  };

  const validateSymbol = (): boolean => {
    if (!symbolInput.trim()) {
      setSymbolError('Please select a symbol');
      return false;
    }

    const validSymbol = symbols.find(
      (s) => s.symbol.toLowerCase() === symbolInput.toLowerCase()
    );

    if (!validSymbol) {
      setSymbolError(`"${symbolInput}" is not available. Please select from the list.`);
      return false;
    }

    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError('');

    if (!validateSymbol()) {
      inputRef.current?.focus();
      return;
    }

    // Parse string parameter values back to numbers for submission
    const parsedParameters: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(parameters)) {
      const num = parseFloat(String(val));
      parsedParameters[key] = isNaN(num) ? val : num;
    }

    setIsSubmitting(true);
    try {
      await onSubmit({
        name: name || undefined,
        type,
        symbols: [selectedSymbol!.symbol],
        exchange,
        parameters: parsedParameters,
      });
      setName('');
      setSelectedSymbol(null);
      setSymbolInput('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to create strategy';
      setSubmitError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const selectedType = STRATEGY_TYPES.find(t => t.value === type);

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
          ) : symbols.length === 0 ? (
            <div className="error-message">No symbols available for {exchange}</div>
          ) : (
            <div className="symbol-autocomplete" ref={dropdownRef}>
              <input
                ref={inputRef}
                type="text"
                value={symbolInput}
                onChange={(e) => handleSymbolInputChange(e.target.value)}
                onFocus={() => {
                  if (selectedSymbol) {
                    setSelectedSymbol(null);
                    setSymbolInput('');
                  }
                  setShowDropdown(true);
                }}
                onKeyDown={handleKeyDown}
                placeholder="Type to search symbols..."
                className={`symbol-input ${symbolError ? 'input-error' : ''} ${selectedSymbol ? 'input-valid' : ''}`}
                autoComplete="off"
              />
              {symbolError && (
                <div className="error-message">{symbolError}</div>
              )}
              {selectedSymbol && !symbolError && (
                <div className="selected-symbol-info">
                  {selectedSymbol.symbol} - {selectedSymbol.name}
                </div>
              )}
              {!selectedSymbol && symbolInput && !symbolError && (
                <div className="symbol-hint">
                  {filteredSymbols.length > 0
                    ? `${filteredSymbols.length} matching symbol${filteredSymbols.length !== 1 ? 's' : ''} - select from dropdown`
                    : 'No matching symbols found'}
                </div>
              )}
              {showDropdown && filteredSymbols.length > 0 && (
                <ul className="symbol-dropdown" ref={listRef}>
                  {filteredSymbols.slice(0, 10).map((sym, index) => (
                    <li
                      key={sym.symbol}
                      className={`symbol-option ${index === highlightedIndex ? 'highlighted' : ''} ${selectedSymbol?.symbol === sym.symbol ? 'selected' : ''}`}
                      onClick={() => handleSymbolSelect(sym)}
                      onMouseEnter={() => setHighlightedIndex(index)}
                    >
                      <span className="symbol-ticker">{sym.symbol}</span>
                      <span className="symbol-name">{sym.name}</span>
                    </li>
                  ))}
                  {filteredSymbols.length > 10 && (
                    <li className="symbol-option more-items">
                      ... and {filteredSymbols.length - 10} more
                    </li>
                  )}
                </ul>
              )}
            </div>
          )}
        </div>
        <h3>Parameters</h3>
        {Object.entries(parameters).map(([key, value]) => (
          <div key={key} className="form-group">
            <label>{key}:</label>
            {key === 'side' ? (
              <select
                value={String(value)}
                onChange={(e) => handleParamChange(key, e.target.value)}
              >
                <option value="BUY">BUY</option>
                <option value="SELL">SELL</option>
              </select>
            ) : (
              <input
                type="text"
                value={String(value)}
                onChange={(e) => handleParamChange(key, e.target.value)}
              />
            )}
          </div>
        ))}
        {submitError && (
          <div className="error-message submit-error">{submitError}</div>
        )}
        <button
          type="submit"
          className="btn-primary"
          disabled={loadingSymbols || symbols.length === 0 || isSubmitting}
        >
          {isSubmitting ? 'Creating...' : 'Create Strategy'}
        </button>
      </form>
    </div>
  );
}
