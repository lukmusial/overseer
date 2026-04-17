import { describe, it, expect } from 'vitest';
import { formatPrice, formatPnl, formatQuantity, getDecimalsFromScale } from './format';

describe('format utilities', () => {
  describe('getDecimalsFromScale', () => {
    it('returns 2 for Alpaca priceScale (100)', () => {
      expect(getDecimalsFromScale(100)).toBe(2);
    });

    it('returns 8 for Binance priceScale (100_000_000)', () => {
      expect(getDecimalsFromScale(100_000_000)).toBe(8);
    });

    it('returns 2 for zero or negative', () => {
      expect(getDecimalsFromScale(0)).toBe(2);
      expect(getDecimalsFromScale(-1)).toBe(2);
    });
  });

  describe('formatPrice', () => {
    it('formats Alpaca stock price correctly (scale=100)', () => {
      // $235.50 stored as 23550
      const result = formatPrice(23550, 100);
      expect(result).toContain('235');
      expect(result).toContain('50');
    });

    it('formats Binance BTC price correctly (scale=100_000_000)', () => {
      // $65,000 stored as 6_500_000_000_000
      const result = formatPrice(6_500_000_000_000, 100_000_000);
      expect(result).toContain('65');
      // Should contain thousands - not be billions or fractions
      const numericValue = parseFloat(result.replace(/,/g, ''));
      expect(numericValue).toBeGreaterThan(60_000);
      expect(numericValue).toBeLessThan(70_000);
    });

    it('formats Binance ETH price correctly (scale=100_000_000)', () => {
      // $3,250 stored as 325_000_000_000
      const result = formatPrice(325_000_000_000, 100_000_000);
      const numericValue = parseFloat(result.replace(/,/g, ''));
      expect(numericValue).toBeGreaterThan(3_000);
      expect(numericValue).toBeLessThan(3_500);
    });

    it('formats small crypto prices correctly', () => {
      // $2.25 stored as 225_000_000
      const result = formatPrice(225_000_000, 100_000_000);
      expect(result).toContain('2.25');
    });

    it('returns 0 for zero price', () => {
      expect(formatPrice(0, 100)).toBe('0');
      expect(formatPrice(0, 100_000_000)).toBe('0');
    });

    it('handles default scale (100)', () => {
      const result = formatPrice(23550);
      expect(result).toContain('235');
    });
  });

  describe('formatPnl', () => {
    it('formats positive P&L with + sign for Alpaca', () => {
      // +$7.00 (700 cents)
      const result = formatPnl(700, 100);
      expect(result).toBe('+$7.00');
    });

    it('formats negative P&L with - sign for Alpaca', () => {
      // -$3.00 (300 cents)
      const result = formatPnl(-300, 100);
      expect(result).toBe('-$3.00');
    });

    it('formats positive P&L for Binance', () => {
      // +$500 = 50_000_000_000 at scale 100_000_000
      const result = formatPnl(50_000_000_000, 100_000_000);
      expect(result).toMatch(/^\+\$/);
      const numericValue = parseFloat(result.replace(/[+$,]/g, ''));
      expect(numericValue).toBe(500);
    });

    it('formats negative P&L for Binance', () => {
      // -$1,250 = -125_000_000_000 at scale 100_000_000
      const result = formatPnl(-125_000_000_000, 100_000_000);
      expect(result).toMatch(/^-\$/);
      const numericValue = parseFloat(result.replace(/[-$,]/g, ''));
      expect(numericValue).toBeCloseTo(1250, 0);
    });

    it('zero P&L shows as positive', () => {
      expect(formatPnl(0, 100)).toBe('+$0.00');
    });
  });

  describe('formatQuantity', () => {
    it('formats fractional BTC quantity (0.5 BTC)', () => {
      expect(formatQuantity(50_000_000, 100_000_000)).toBe('0.5');
    });

    it('formats whole stock quantity', () => {
      expect(formatQuantity(100, 1)).toBe('100');
    });

    it('formats small crypto quantity', () => {
      expect(formatQuantity(1_000_000, 100_000_000)).toBe('0.01');
    });

    it('formats zero quantity', () => {
      expect(formatQuantity(0, 100_000_000)).toBe('0');
    });

    it('formats whole BTC quantity', () => {
      expect(formatQuantity(100_000_000, 100_000_000)).toBe('1');
    });

    it('strips trailing zeros', () => {
      // 0.10000000 should become 0.1
      expect(formatQuantity(10_000_000, 100_000_000)).toBe('0.1');
    });

    it('caps crypto quantity at 5 decimals', () => {
      // 0.01335061 should display as 0.01335 (5 decimal max for crypto)
      expect(formatQuantity(1_335_061, 100_000_000)).toBe('0.01335');
    });

    it('shows consistent precision for similar crypto quantities', () => {
      // Both rounded and unrounded should show same number of significant digits
      expect(formatQuantity(1_331_000, 100_000_000)).toBe('0.01331');
      expect(formatQuantity(1_335_061, 100_000_000)).toBe('0.01335');
    });

    it('defaults to scale 1 when not provided', () => {
      expect(formatQuantity(42)).toBe('42');
    });
  });

  describe('price scale consistency', () => {
    it('Alpaca and Binance prices for same dollar value display same', () => {
      // $100 in Alpaca format: 10_000 (cents)
      const alpacaFormatted = formatPrice(10_000, 100);
      // $100 in Binance format: 10_000_000_000
      const binanceFormatted = formatPrice(10_000_000_000, 100_000_000);

      const alpacaValue = parseFloat(alpacaFormatted.replace(/,/g, ''));
      const binanceValue = parseFloat(binanceFormatted.replace(/,/g, ''));

      expect(alpacaValue).toBe(100);
      expect(binanceValue).toBe(100);
    });

    it('order quantities use quantityScale not priceScale', () => {
      // Stock quantities (quantityScale=1) display as-is
      expect(formatQuantity(7, 1)).toBe('7');
      // Crypto quantities (quantityScale=100_000_000) are divided by quantityScale
      expect(formatQuantity(700_000_000, 100_000_000)).toBe('7');
    });
  });
});
