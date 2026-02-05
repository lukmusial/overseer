/**
 * Calculates the number of decimal places from a price scale.
 * e.g., scale 100 = 2 decimals, scale 100000000 = 8 decimals
 */
export function getDecimalsFromScale(scale: number): number {
  if (scale <= 0) return 2;
  return Math.round(Math.log10(scale));
}

/**
 * Formats a price value given its scale.
 * Automatically determines decimal places based on scale.
 */
export function formatPrice(price: number, scale: number = 100): string {
  if (price === 0) return '0';
  const decimals = getDecimalsFromScale(scale);
  const value = price / scale;
  // For very small values, show more precision
  if (Math.abs(value) < 0.01 && value !== 0) {
    return value.toPrecision(4);
  }
  return value.toLocaleString(undefined, {
    minimumFractionDigits: Math.min(decimals, 2),
    maximumFractionDigits: decimals,
  });
}

/**
 * Formats a P&L value with sign and currency symbol.
 * Uses the scale to determine decimal places.
 * Format: +$X.XX for positive, -$X.XX for negative
 */
export function formatPnl(pnl: number, scale: number = 100): string {
  const decimals = Math.min(getDecimalsFromScale(scale), 2);
  const value = pnl / scale;
  const absValue = Math.abs(value);
  const formatted = absValue.toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
  return value >= 0 ? `+$${formatted}` : `-$${formatted}`;
}

/**
 * Formats a currency value (already in decimal form).
 */
export function formatCurrency(value: number, decimals: number = 2): string {
  const sign = value >= 0 ? '+' : '';
  return `${sign}$${value.toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;
}
