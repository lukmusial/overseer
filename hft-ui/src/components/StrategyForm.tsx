import { useState, useEffect, useRef } from 'react';
import type { CreateStrategyRequest, TradingSymbol, StrategyTypeInfo, TradingPeriodInfo } from '../types/api';
import { useApi } from '../hooks/useApi';

interface Props {
  onSubmit: (strategy: CreateStrategyRequest) => Promise<void>;
  symbolRefreshKey?: number;
}

const EXCHANGES = ['ALPACA', 'BINANCE'];

function defaultParamsFromType(typeInfo: StrategyTypeInfo): Record<string, unknown> {
  const params: Record<string, unknown> = {};
  for (const p of typeInfo.parameters) {
    params[p.name] = p.default;
  }
  return params;
}

export function StrategyForm({ onSubmit, symbolRefreshKey }: Props) {
  const { getSymbols, getStrategyTypes, getTradingPeriods } = useApi();
  const [name, setName] = useState('');
  const [type, setType] = useState('');
  const [selectedSymbol, setSelectedSymbol] = useState<TradingSymbol | null>(null);
  const [exchange, setExchange] = useState('ALPACA');
  const [parameters, setParameters] = useState<Record<string, unknown>>({});
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loadingSymbols, setLoadingSymbols] = useState(false);
  const [symbolInput, setSymbolInput] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [symbolError, setSymbolError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [strategyTypes, setStrategyTypes] = useState<StrategyTypeInfo[]>([]);
  const [loadingTypes, setLoadingTypes] = useState(true);
  const [tradingPeriods, setTradingPeriods] = useState<TradingPeriodInfo[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState('');
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  // Fetch strategy types and trading periods on mount
  useEffect(() => {
    let cancelled = false;
    setLoadingTypes(true);

    Promise.all([getStrategyTypes(), getTradingPeriods()])
      .then(([types, periods]) => {
        if (!cancelled) {
          setStrategyTypes(types);
          setTradingPeriods(periods);
          if (types.length > 0) {
            setType(types[0].type);
            setParameters(defaultParamsFromType(types[0]));
          }
        }
      })
      .catch((err) => {
        console.error('Failed to fetch strategy types:', err);
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingTypes(false);
        }
      });

    return () => { cancelled = true; };
  }, [getStrategyTypes, getTradingPeriods]);

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

  const handleTypeChange = (newType: string) => {
    setType(newType);
    const typeInfo = strategyTypes.find(t => t.type === newType);
    if (typeInfo) {
      setParameters(defaultParamsFromType(typeInfo));
    }
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

    // Include trading period if selected
    if (selectedPeriod) {
      parsedParameters.tradingPeriod = selectedPeriod;
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

  const selectedType = strategyTypes.find(t => t.type === type);
  const selectedPeriodInfo = tradingPeriods.find(p => p.name === selectedPeriod);

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
          {loadingTypes ? (
            <div className="loading-indicator">Loading strategy types...</div>
          ) : (
            <>
              <select value={type} onChange={(e) => handleTypeChange(e.target.value)}>
                {strategyTypes.map((t) => (
                  <option key={t.type} value={t.type}>{t.name}</option>
                ))}
              </select>
              {selectedType && <small className="type-description">{selectedType.description}</small>}
            </>
          )}
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
        <div className="form-group">
          <label>Trading Period:</label>
          <select
            value={selectedPeriod}
            onChange={(e) => setSelectedPeriod(e.target.value)}
            className="period-select"
          >
            <option value="">All Periods (24h)</option>
            {tradingPeriods.map((p) => (
              <option key={p.name} value={p.name}>
                {p.name} ({p.startTime}-{p.endTime}, {p.positionMultiplier}x)
              </option>
            ))}
          </select>
          {selectedPeriodInfo && selectedPeriodInfo.recommendedStrategies.length > 0 && (
            <small className="type-description">
              Recommended: {selectedPeriodInfo.recommendedStrategies.join(', ')}
            </small>
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
          disabled={loadingSymbols || loadingTypes || symbols.length === 0 || isSubmitting}
        >
          {isSubmitting ? 'Creating...' : 'Create Strategy'}
        </button>
      </form>
    </div>
  );
}
