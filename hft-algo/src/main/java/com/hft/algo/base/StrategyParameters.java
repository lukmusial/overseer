package com.hft.algo.base;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration parameters for trading strategies.
 */
public class StrategyParameters {
    private final Map<String, Object> parameters;

    public StrategyParameters() {
        this.parameters = new HashMap<>();
    }

    public StrategyParameters(Map<String, Object> parameters) {
        this.parameters = new HashMap<>(parameters);
    }

    public StrategyParameters set(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    public int getInt(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    public long getLong(String key, long defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    public double getDouble(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public String getString(String key, String defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    public boolean has(String key) {
        return parameters.containsKey(key);
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(parameters);
    }

    @Override
    public String toString() {
        return "StrategyParameters" + parameters;
    }
}
