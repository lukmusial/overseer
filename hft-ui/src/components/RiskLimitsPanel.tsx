import { useState, useCallback } from 'react';
import { useApi } from '../hooks/useApi';
import type { RiskLimits } from '../types/api';

interface RiskLimitsPanelProps {
  riskLimits: RiskLimits | null;
  onBack: () => void;
  onUpdate: (limits: RiskLimits) => void;
}

function formatCurrency(cents: number): string {
  const dollars = cents / 100;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(dollars);
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US').format(value);
}

function getProgressColor(percentage: number): string {
  if (percentage >= 90) return 'danger';
  if (percentage >= 75) return 'warning';
  return 'safe';
}

interface ProgressBarProps {
  label: string;
  current: number;
  max: number;
  formatValue?: (value: number) => string;
}

function ProgressBar({ label, current, max, formatValue = formatNumber }: ProgressBarProps) {
  const percentage = max > 0 ? Math.min((Math.abs(current) / max) * 100, 100) : 0;
  const colorClass = getProgressColor(percentage);

  return (
    <div className="risk-limit-item">
      <div className="risk-limit-header">
        <span className="risk-limit-label">{label}</span>
        <span className="risk-limit-values">
          {formatValue(current)} / {formatValue(max)}
        </span>
      </div>
      <div className="risk-limit-bar">
        <div
          className={`risk-limit-fill ${colorClass}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      <div className="risk-limit-percentage">{percentage.toFixed(1)}%</div>
    </div>
  );
}

interface EditableLimitProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  isCurrency?: boolean;
}

function EditableLimit({ label, value, onChange, isCurrency = false }: EditableLimitProps) {
  const displayValue = isCurrency ? value / 100 : value;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputValue = parseFloat(e.target.value) || 0;
    const newValue = isCurrency ? Math.round(inputValue * 100) : Math.round(inputValue);
    onChange(newValue);
  };

  return (
    <div className="risk-edit-item">
      <label className="risk-edit-label">{label}</label>
      <div className="risk-edit-input-wrapper">
        {isCurrency && <span className="currency-prefix">$</span>}
        <input
          type="number"
          className="risk-edit-input"
          value={displayValue}
          onChange={handleChange}
          min={0}
        />
      </div>
    </div>
  );
}

interface StaticLimitProps {
  label: string;
  value: number;
  formatValue?: (value: number) => string;
}

function StaticLimit({ label, value, formatValue = formatNumber }: StaticLimitProps) {
  return (
    <div className="risk-static-item">
      <span className="risk-static-label">{label}</span>
      <span className="risk-static-value">{formatValue(value)}</span>
    </div>
  );
}

export function RiskLimitsPanel({ riskLimits, onBack, onUpdate }: RiskLimitsPanelProps) {
  const api = useApi();
  const [isEditing, setIsEditing] = useState(false);
  const [editedLimits, setEditedLimits] = useState<RiskLimits['limits'] | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleEdit = useCallback(() => {
    if (riskLimits) {
      setEditedLimits({ ...riskLimits.limits });
      setIsEditing(true);
      setError(null);
    }
  }, [riskLimits]);

  const handleCancel = useCallback(() => {
    setIsEditing(false);
    setEditedLimits(null);
    setError(null);
  }, []);

  const handleSave = useCallback(async () => {
    if (!editedLimits) return;

    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateRiskLimits(editedLimits);
      onUpdate(updated);
      setIsEditing(false);
      setEditedLimits(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save risk limits');
    } finally {
      setSaving(false);
    }
  }, [editedLimits, api, onUpdate]);

  const updateLimit = useCallback((key: keyof RiskLimits['limits'], value: number) => {
    setEditedLimits(prev => prev ? { ...prev, [key]: value } : null);
  }, []);

  if (!riskLimits) {
    return (
      <div className="risk-limits-page">
        <div className="risk-limits-header">
          <button className="btn-secondary" onClick={onBack}>Back to Dashboard</button>
          <h1>Risk Limits</h1>
        </div>
        <div className="card">
          <div className="empty-message">Loading risk limits...</div>
        </div>
      </div>
    );
  }

  const { limits, usage, tradingEnabled, disabledReason } = riskLimits;
  const displayLimits = isEditing && editedLimits ? editedLimits : limits;

  return (
    <div className="risk-limits-page">
      <div className="risk-limits-header">
        <button className="btn-secondary" onClick={onBack}>Back to Dashboard</button>
        <h1>Risk Limits</h1>
        {!isEditing ? (
          <button className="btn-primary" onClick={handleEdit}>Edit Limits</button>
        ) : (
          <div className="edit-actions">
            <button className="btn-secondary" onClick={handleCancel} disabled={saving}>Cancel</button>
            <button className="btn-success" onClick={handleSave} disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
        )}
      </div>

      {error && (
        <div className="card error-card">
          <div className="error-message">{error}</div>
        </div>
      )}

      {/* Trading Status */}
      <div className="card">
        <h2>Trading Status</h2>
        <div className="trading-status-row">
          <div className={`trading-status-indicator ${tradingEnabled ? 'enabled' : 'disabled'}`}>
            <span className="status-dot" />
            <span className="status-text">
              {tradingEnabled ? 'Trading Enabled' : 'Trading Disabled'}
            </span>
          </div>
          {!tradingEnabled && disabledReason && (
            <div className="disabled-reason">
              Reason: {disabledReason}
            </div>
          )}
        </div>
      </div>

      {/* Daily Usage */}
      <div className="card">
        <h2>Daily Limits & Usage</h2>
        {isEditing && editedLimits ? (
          <div className="risk-edit-grid">
            <EditableLimit
              label="Max Orders/Day"
              value={editedLimits.maxOrdersPerDay}
              onChange={(v) => updateLimit('maxOrdersPerDay', v)}
            />
            <EditableLimit
              label="Max Daily Notional"
              value={editedLimits.maxDailyNotional}
              onChange={(v) => updateLimit('maxDailyNotional', v)}
              isCurrency
            />
            <EditableLimit
              label="Max Daily Loss"
              value={editedLimits.maxDailyLoss}
              onChange={(v) => updateLimit('maxDailyLoss', v)}
              isCurrency
            />
            <EditableLimit
              label="Max Net Exposure"
              value={editedLimits.maxNetExposure}
              onChange={(v) => updateLimit('maxNetExposure', v)}
              isCurrency
            />
          </div>
        ) : (
          <div className="risk-limits-grid">
            <ProgressBar
              label="Orders Today"
              current={usage.ordersSubmittedToday}
              max={displayLimits.maxOrdersPerDay}
            />
            <ProgressBar
              label="Daily Notional"
              current={usage.notionalTradedToday}
              max={displayLimits.maxDailyNotional}
              formatValue={formatCurrency}
            />
            <ProgressBar
              label="Daily P&L vs Loss Limit"
              current={-usage.currentDailyPnl}
              max={displayLimits.maxDailyLoss}
              formatValue={(v) => formatCurrency(v < 0 ? -v : v)}
            />
            <ProgressBar
              label="Net Exposure"
              current={usage.currentNetExposure}
              max={displayLimits.maxNetExposure}
              formatValue={formatCurrency}
            />
          </div>
        )}
      </div>

      {/* Per-Order Limits */}
      <div className="card">
        <h2>Per-Order Limits</h2>
        {isEditing && editedLimits ? (
          <div className="risk-edit-grid">
            <EditableLimit
              label="Max Order Size"
              value={editedLimits.maxOrderSize}
              onChange={(v) => updateLimit('maxOrderSize', v)}
            />
            <EditableLimit
              label="Max Order Notional"
              value={editedLimits.maxOrderNotional}
              onChange={(v) => updateLimit('maxOrderNotional', v)}
              isCurrency
            />
            <EditableLimit
              label="Max Position Size"
              value={editedLimits.maxPositionSize}
              onChange={(v) => updateLimit('maxPositionSize', v)}
            />
          </div>
        ) : (
          <div className="risk-static-grid">
            <StaticLimit label="Max Order Size" value={displayLimits.maxOrderSize} />
            <StaticLimit label="Max Order Notional" value={displayLimits.maxOrderNotional} formatValue={formatCurrency} />
            <StaticLimit label="Max Position Size" value={displayLimits.maxPositionSize} />
          </div>
        )}
      </div>

      {/* Per-Position Limits */}
      <div className="card">
        <h2>Per-Position Limits</h2>
        {isEditing && editedLimits ? (
          <div className="risk-edit-grid">
            <EditableLimit
              label="Max Drawdown/Position"
              value={editedLimits.maxDrawdownPerPosition}
              onChange={(v) => updateLimit('maxDrawdownPerPosition', v)}
              isCurrency
            />
            <EditableLimit
              label="Max Unrealized Loss/Position"
              value={editedLimits.maxUnrealizedLossPerPosition}
              onChange={(v) => updateLimit('maxUnrealizedLossPerPosition', v)}
              isCurrency
            />
          </div>
        ) : (
          <div className="risk-static-grid">
            <StaticLimit label="Max Drawdown/Position" value={displayLimits.maxDrawdownPerPosition} formatValue={formatCurrency} />
            <StaticLimit label="Max Unrealized Loss/Position" value={displayLimits.maxUnrealizedLossPerPosition} formatValue={formatCurrency} />
          </div>
        )}
      </div>
    </div>
  );
}
