import { useState, useEffect, useRef, useMemo } from 'react';
import { CandlestickChart } from './CandlestickChart';
import { useApi } from '../hooks/useApi';
import type { Strategy, TradingSymbol, ExchangeStatus } from '../types/api';

interface ChartPanelProps {
  exchanges: ExchangeStatus[];
  strategies: Strategy[];
  symbolRefreshKey?: number;
}

export function ChartPanel({ exchanges, strategies, symbolRefreshKey }: ChartPanelProps) {
  const [selectedExchange, setSelectedExchange] = useState<string>('');
  const [selectedSymbol, setSelectedSymbol] = useState<string>('');
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loading, setLoading] = useState(false);
  const [symbolSearch, setSymbolSearch] = useState('');
  const [symbolDropdownOpen, setSymbolDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const { getSymbols } = useApi();

  // Set default exchange when exchanges load
  useEffect(() => {
    if (exchanges.length > 0 && !selectedExchange) {
      setSelectedExchange(exchanges[0].exchange);
    }
  }, [exchanges, selectedExchange]);

  // Fetch symbols when exchange changes or symbolRefreshKey changes
  useEffect(() => {
    if (!selectedExchange) return;

    const fetchSymbols = async () => {
      setLoading(true);
      try {
        const data = await getSymbols(selectedExchange);
        setSymbols(data.sort((a, b) => a.symbol.localeCompare(b.symbol)));
        // Auto-select first symbol if none selected
        if (data.length > 0 && !selectedSymbol) {
          setSelectedSymbol(data[0].symbol);
          setSymbolSearch(data[0].symbol + ' - ' + data[0].name);
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
    setSelectedExchange(exchange);
    setSelectedSymbol('');
    setSymbolSearch('');
    setSymbols([]);
  };

  const handleSymbolSelect = (symbol: TradingSymbol) => {
    setSelectedSymbol(symbol.symbol);
    setSymbolSearch(symbol.symbol + ' - ' + symbol.name);
    setSymbolDropdownOpen(false);
  };

  const handleSearchChange = (value: string) => {
    setSymbolSearch(value);
    setSymbolDropdownOpen(true);
  };

  const handleSearchFocus = () => {
    if (selectedSymbol) {
      setSymbolSearch('');
    }
    setSymbolDropdownOpen(true);
  };

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
                placeholder="Search symbols..."
                disabled={loading || symbols.length === 0}
              />
              {symbolDropdownOpen && !loading && filteredSymbols.length > 0 && (
                <ul className="symbol-search-dropdown">
                  {filteredSymbols.slice(0, 50).map((s) => (
                    <li
                      key={s.symbol}
                      className={`symbol-search-item${s.symbol === selectedSymbol ? ' selected' : ''}`}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        handleSymbolSelect(s);
                      }}
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
        />
      ) : (
        <div className="empty-message">
          Select an exchange and symbol to view the chart
        </div>
      )}
    </div>
  );
}
