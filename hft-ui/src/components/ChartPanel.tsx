import { useState, useEffect } from 'react';
import { CandlestickChart } from './CandlestickChart';
import { useApi } from '../hooks/useApi';
import type { Strategy, TradingSymbol, ExchangeStatus } from '../types/api';

interface ChartPanelProps {
  exchanges: ExchangeStatus[];
  strategies: Strategy[];
}

export function ChartPanel({ exchanges, strategies }: ChartPanelProps) {
  const [selectedExchange, setSelectedExchange] = useState<string>('');
  const [selectedSymbol, setSelectedSymbol] = useState<string>('');
  const [symbols, setSymbols] = useState<TradingSymbol[]>([]);
  const [loading, setLoading] = useState(false);

  const { getSymbols } = useApi();

  // Set default exchange when exchanges load
  useEffect(() => {
    if (exchanges.length > 0 && !selectedExchange) {
      setSelectedExchange(exchanges[0].exchange);
    }
  }, [exchanges, selectedExchange]);

  // Fetch symbols when exchange changes
  useEffect(() => {
    if (!selectedExchange) return;

    const fetchSymbols = async () => {
      setLoading(true);
      try {
        const data = await getSymbols(selectedExchange);
        setSymbols(data);
        // Auto-select first symbol if none selected
        if (data.length > 0 && !selectedSymbol) {
          setSelectedSymbol(data[0].symbol);
        }
      } catch (error) {
        console.error('Failed to fetch symbols:', error);
        setSymbols([]);
      } finally {
        setLoading(false);
      }
    };

    fetchSymbols();
  }, [selectedExchange, getSymbols]);

  // Reset symbol when exchange changes
  const handleExchangeChange = (exchange: string) => {
    setSelectedExchange(exchange);
    setSelectedSymbol('');
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

          <div className="form-group">
            <label>Symbol</label>
            <select
              value={selectedSymbol}
              onChange={(e) => setSelectedSymbol(e.target.value)}
              disabled={loading || symbols.length === 0}
            >
              {loading ? (
                <option>Loading...</option>
              ) : (
                symbols.map((s) => (
                  <option key={s.symbol} value={s.symbol}>
                    {s.symbol} - {s.name}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>
      </div>

      {selectedExchange && selectedSymbol ? (
        <CandlestickChart
          exchange={selectedExchange}
          symbol={selectedSymbol}
          strategies={strategies}
        />
      ) : (
        <div className="empty-message">
          Select an exchange and symbol to view the chart
        </div>
      )}
    </div>
  );
}
