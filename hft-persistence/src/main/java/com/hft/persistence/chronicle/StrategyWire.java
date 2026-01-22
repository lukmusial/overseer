package com.hft.persistence.chronicle;

import com.hft.persistence.StrategyRepository.StrategyDefinition;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

import java.util.*;

/**
 * Chronicle Wire format for Strategy serialization.
 * Parameters are stored as a serialized string format for flexibility.
 */
public class StrategyWire extends SelfDescribingMarshallable {
    private String id;
    private String name;
    private String type;
    private String symbolsCsv;  // Comma-separated symbols
    private String exchange;
    private String parametersJson;  // Simple key=value format
    private String state;
    private boolean deleted;  // Soft delete flag

    public StrategyWire() {
    }

    public static StrategyWire from(StrategyDefinition strategy) {
        StrategyWire wire = new StrategyWire();
        wire.id = strategy.id();
        wire.name = strategy.name();
        wire.type = strategy.type();
        wire.symbolsCsv = String.join(",", strategy.symbols());
        wire.exchange = strategy.exchange();
        wire.parametersJson = encodeParameters(strategy.parameters());
        wire.state = strategy.state();
        wire.deleted = false;
        return wire;
    }

    public static StrategyWire deleted(String id) {
        StrategyWire wire = new StrategyWire();
        wire.id = id;
        wire.deleted = true;
        return wire;
    }

    public StrategyDefinition toDefinition() {
        if (deleted) {
            return null;
        }
        List<String> symbols = symbolsCsv != null && !symbolsCsv.isEmpty()
                ? Arrays.asList(symbolsCsv.split(","))
                : List.of();
        return new StrategyDefinition(
                id,
                name,
                type,
                symbols,
                exchange,
                decodeParameters(parametersJson),
                state
        );
    }

    public String getId() {
        return id;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Encodes parameters map to a simple string format.
     * Format: key1=value1;key2=value2
     */
    private static String encodeParameters(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            String key = entry.getKey().replace("=", "\\=").replace(";", "\\;");
            String value = String.valueOf(entry.getValue()).replace("=", "\\=").replace(";", "\\;");
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    /**
     * Decodes parameters from string format back to map.
     */
    private static Map<String, Object> decodeParameters(String encoded) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return params;
        }

        // Split on unescaped semicolons
        String[] pairs = encoded.split("(?<!\\\\);");
        for (String pair : pairs) {
            // Split on unescaped equals
            String[] kv = pair.split("(?<!\\\\)=", 2);
            if (kv.length == 2) {
                String key = kv[0].replace("\\=", "=").replace("\\;", ";");
                String value = kv[1].replace("\\=", "=").replace("\\;", ";");
                // Try to parse as number
                params.put(key, parseValue(value));
            }
        }
        return params;
    }

    /**
     * Attempts to parse value as appropriate type.
     */
    private static Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        // Try long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}
        // Try double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        // Try boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        // Return as string
        return value;
    }
}
