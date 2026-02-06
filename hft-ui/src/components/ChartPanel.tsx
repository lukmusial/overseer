import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { CandlestickChart } from './CandlestickChart';
import { useApi } from '../hooks/useApi';
import type { Strategy, TradingSymbol, ExchangeStatus } from '../types/api';

interface ChartPanelProps {
  exchanges: ExchangeStatus[];
  strategies: Strategy[];
  symbolRefreshKey?: number;
  subscribe?: <T>(destination: string, callback: (data: T) => void) => () => void;
  selectedExchange: string;
  selectedSymbol: string;
  onExchangeChange: (exchange: string) => void;
  onSymbolChange: (symbol: string) => void;
}

export function ChartPanel({ exchanges, strategies, symbolRefreshKey, subscribe, selectedExchange, selectedSymbol, onExchangeChange, onSymbolChange }: ChartPanelProps) {
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loading, setLoading] = useState(false);
  const [symbolSearch, setSymbolSearch] = useState('');
  const [symbolDropdownOpen, setSymbolDropdownOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const { getSymbols } = useApi();

  // Set default exchange when exchanges load
  useEffect(() => {
    if (exchanges.length > 0 && !selectedExchange) {
      onExchangeChange(exchanges[0].exchange);
    }
  }, [exchanges, selectedExchange, onExchangeChange]);

  // Fetch symbols when exchange changes or symbolRefreshKey changes
  useEffect(() => {
    if (!selectedExchange) return;

    const fetchSymbols = async () => {
      setLoading(true);
      try {
        const data = await getSymbols(selectedExchange);
        setSymbols(data.sort((a, b) => a.symbol.localeCompare(b.symbol)));
        if (data.length > 0 && !selectedSymbol) {
          // Auto-select first symbol if none selected
          onSymbolChange(data[0].symbol);
          setSymbolSearch(data[0].symbol + ' - ' + data[0].name);
        } else if (selectedSymbol) {
          // Restore search text for existing selection (e.g., after tab switch)
          const existing = data.find((s) => s.symbol === selectedSymbol);
          if (existing) {
            setSymbolSearch(existing.symbol + ' - ' + existing.name);
          }
        }
      } catch (error) {
        console.error('Failed to fetch symbols:', error);
        setSymbols([]);
      } finally {
        setLoading(false);
      }
    };

    fetchSymbols();
  }, [selectedExchange, getSymbols, symbolRefreshKey]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setSymbolDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Scroll highlighted item into view
  useEffect(() => {
    if (highlightedIndex >= 0 && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement;
      if (item) {
        item.scrollIntoView?.({ block: 'nearest' });
      }
    }
  }, [highlightedIndex]);

  // Filter symbols based on search text
  const filteredSymbols = useMemo(() => {
    if (!symbolSearch.trim()) return symbols;
    // If the search text matches the selected symbol's display format, show all symbols
    const selectedDisplay = symbols.find((s) => s.symbol === selectedSymbol);
    if (selectedDisplay && symbolSearch === selectedDisplay.symbol + ' - ' + selectedDisplay.name) {
      return symbols;
    }
    const query = symbolSearch.toLowerCase();
    return symbols.filter(
      (s) =>
        s.symbol.toLowerCase().includes(query) ||
        s.name.toLowerCase().includes(query)
    );
  }, [symbols, symbolSearch, selectedSymbol]);

  // Reset symbol when exchange changes
  const handleExchangeChange = (exchange: string) => {
    onExchangeChange(exchange);
    onSymbolChange('');
    setSymbolSearch('');
    setSymbols([]);
  };

  const handleSymbolSelect = useCallback((symbol: TradingSymbol) => {
    onSymbolChange(symbol.symbol);
    setSymbolSearch(symbol.symbol + ' - ' + symbol.name);
    setSymbolDropdownOpen(false);
    setHighlightedIndex(-1);
  }, [onSymbolChange]);

  const handleSearchChange = (value: string) => {
    setSymbolSearch(value);
    setSymbolDropdownOpen(true);
    setHighlightedIndex(-1);
  };

  const handleSearchFocus = () => {
    if (selectedSymbol) {
      setSymbolSearch('');
    }
    setSymbolDropdownOpen(true);
    setHighlightedIndex(-1);
  };

  const handleSearchKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setSymbolDropdownOpen(false);
      return;
    }

    const visibleSymbols = filteredSymbols.slice(0, 50);
    if (visibleSymbols.length === 0) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        if (!symbolDropdownOpen) {
          setSymbolDropdownOpen(true);
          setHighlightedIndex(0);
        } else {
          setHighlightedIndex((prev) =>
            prev < visibleSymbols.length - 1 ? prev + 1 : prev
          );
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (highlightedIndex >= 0 && highlightedIndex < visibleSymbols.length) {
          handleSymbolSelect(visibleSymbols[highlightedIndex]);
        }
        break;
    }
  }, [filteredSymbols, symbolDropdownOpen, highlightedIndex, handleSymbolSelect]);

  return (
    <div className="card chart-panel">
      <h2>Price Chart</h2>

      <div className="chart-symbol-selector">
        <div className="selector-row">
          <div className="form-group">
            <label>Exchange</label>
            <select
              value={selectedExchange}
              onChange={(e) => handleExchangeChange(e.target.value)}
            >
              {exchanges.map((ex) => (
                <option key={ex.exchange} value={ex.exchange}>
                  {ex.name}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group" ref={dropdownRef}>
            <label>Symbol</label>
            <div className="symbol-search-container">
              <input
                type="text"
                className="symbol-search-input"
                value={loading ? 'Loading...' : symbolSearch}
                onChange={(e) => handleSearchChange(e.target.value)}
                onFocus={handleSearchFocus}
                onKeyDown={handleSearchKeyDown}
                placeholder="Search symbols..."
                disabled={loading || symbols.length === 0}
              />
              {symbolDropdownOpen && !loading && filteredSymbols.length > 0 && (
                <ul className="symbol-search-dropdown" ref={listRef}>
                  {filteredSymbols.slice(0, 50).map((s, index) => (
                    <li
                      key={s.symbol}
                      className={`symbol-search-item${s.symbol === selectedSymbol ? ' selected' : ''}${index === highlightedIndex ? ' highlighted' : ''}`}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        handleSymbolSelect(s);
                      }}
                      onMouseEnter={() => setHighlightedIndex(index)}
                    >
                      <span className="symbol-ticker">{s.symbol}</span>
                      <span className="symbol-name">{s.name}</span>
                    </li>
                  ))}
                  {filteredSymbols.length > 50 && (
                    <li className="symbol-search-item more-items">
                      {filteredSymbols.length - 50} more...
                    </li>
                  )}
                </ul>
              )}
            </div>
          </div>
        </div>
      </div>

      {selectedExchange && selectedSymbol ? (
        <CandlestickChart
          exchange={selectedExchange}
          symbol={selectedSymbol}
          strategies={strategies}
          refreshKey={symbolRefreshKey}
          subscribe={subscribe}
        />
      ) : (
        <div className="empty-message">
          Select an exchange and symbol to view the chart
        </div>
      )}
    </div>
  );
}
