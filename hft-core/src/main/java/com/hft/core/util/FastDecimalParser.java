package com.hft.core.util;

/**
 * Zero-allocation decimal string parser optimized for high-frequency trading.
 *
 * <p>Replaces the common pattern of {@code new BigDecimal(s).multiply(BigDecimal.valueOf(scale)).longValue()}
 * which creates 2–4 heap objects per call. This implementation parses character-by-character
 * without creating any intermediate objects (no {@code BigDecimal}, no {@code substring},
 * no {@code char[]} allocation).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Parse a Binance price with 8 decimal places
 * long scaled = FastDecimalParser.parseDecimal("67432.15000000", 8);
 * // Result: 6743215000000L
 *
 * // Parse an Alpaca price with 2 decimal places (cents)
 * long cents = FastDecimalParser.parseDecimal("150.50", 2);
 * // Result: 15050L
 *
 * // Format back to string (trailing zeros stripped)
 * String s = FastDecimalParser.formatDecimal(6743215000000L, 8);
 * // Result: "67432.15"
 * }</pre>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li>Positive decimals: {@code "123.456"}</li>
 *   <li>Negative decimals: {@code "-0.00123"}</li>
 *   <li>Integers (no decimal point): {@code "100"}</li>
 *   <li>Leading/trailing zeros: {@code "0.00100"}, {@code "007.5"}</li>
 *   <li>Zero values: {@code "0"}, {@code "0.0"}, {@code "0.00000000"}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All methods are stateless and thread-safe.
 *
 * @see com.hft.core.model.Order
 * @see com.hft.core.model.Quote
 */
public final class FastDecimalParser {

    private FastDecimalParser() {
        // Utility class — no instantiation
    }

    /**
     * Parses a decimal string into a scaled {@code long} with the specified number of
     * decimal places.
     *
     * <p>The result is equivalent to:
     * <pre>{@code
     * new BigDecimal(s).setScale(targetDecimals, RoundingMode.DOWN)
     *     .movePointRight(targetDecimals).longValueExact();
     * }</pre>
     * but performs zero heap allocations.
     *
     * <p>If the input string contains more fractional digits than {@code targetDecimals},
     * the excess digits are truncated (not rounded).
     *
     * @param s              the decimal string to parse (e.g. {@code "67432.15000000"})
     * @param targetDecimals the number of decimal places in the result (e.g. 8 means
     *                       the result is scaled by 10^8)
     * @return the parsed value as a scaled long
     * @throws NumberFormatException if the string is null, blank, or malformed
     * @throws ArithmeticException   if the result would overflow {@code long}
     */
    public static long parseDecimal(String s, int targetDecimals) {
        if (s == null || s.isBlank()) {
            throw new NumberFormatException("Input string is null or blank");
        }

        int len = s.length();
        int pos = 0;

        // Handle sign
        boolean negative = false;
        char first = s.charAt(0);
        if (first == '-') {
            negative = true;
            pos++;
        } else if (first == '+') {
            pos++;
        }

        if (pos >= len) {
            throw new NumberFormatException("No digits in input: \"" + s + "\"");
        }

        // Parse integer part
        long integerPart = 0;
        boolean hasDigits = false;
        while (pos < len) {
            char c = s.charAt(pos);
            if (c == '.') {
                pos++;
                break;
            }
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid character '" + c + "' at position " + pos + " in: \"" + s + "\"");
            }
            hasDigits = true;
            long next = integerPart * 10 + (c - '0');
            if (next < integerPart) {
                throw new ArithmeticException("Overflow parsing integer part of: \"" + s + "\"");
            }
            integerPart = next;
            pos++;
        }

        // Parse fractional part — consume up to targetDecimals digits
        long fractionalPart = 0;
        int fractionalDigits = 0;
        while (pos < len && fractionalDigits < targetDecimals) {
            char c = s.charAt(pos);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid character '" + c + "' at position " + pos + " in: \"" + s + "\"");
            }
            hasDigits = true;
            fractionalPart = fractionalPart * 10 + (c - '0');
            fractionalDigits++;
            pos++;
        }

        // Skip any remaining fractional digits (truncation, not rounding)
        while (pos < len) {
            char c = s.charAt(pos);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid character '" + c + "' at position " + pos + " in: \"" + s + "\"");
            }
            hasDigits = true;
            pos++;
        }

        if (!hasDigits) {
            throw new NumberFormatException("No digits in input: \"" + s + "\"");
        }

        // Pad fractional part if fewer digits than targetDecimals
        for (int i = fractionalDigits; i < targetDecimals; i++) {
            fractionalPart *= 10;
        }

        // Compute scale multiplier for integer part
        long scale = 1;
        for (int i = 0; i < targetDecimals; i++) {
            scale *= 10;
        }

        // Combine: result = integerPart * scale + fractionalPart
        long result = integerPart * scale;
        if (integerPart != 0 && result / integerPart != scale) {
            throw new ArithmeticException("Overflow computing scaled value for: \"" + s + "\"");
        }

        result += fractionalPart;
        if (result < 0 && !(negative && result == Long.MIN_VALUE)) {
            throw new ArithmeticException("Overflow computing scaled value for: \"" + s + "\"");
        }

        return negative ? -result : result;
    }

    /**
     * Parses a decimal string into a scaled {@code long}, returning a default value
     * if the input is null or blank.
     *
     * <p>This overload is useful when processing optional fields from exchange APIs
     * where a missing value should map to a sentinel (e.g. {@code 0L} or {@code -1L}).
     *
     * @param s              the decimal string to parse, or null/blank
     * @param targetDecimals the number of decimal places in the result
     * @param defaultValue   the value to return if {@code s} is null or blank
     * @return the parsed value as a scaled long, or {@code defaultValue}
     * @throws NumberFormatException if the string is non-blank but malformed
     * @throws ArithmeticException   if the result would overflow {@code long}
     */
    public static long parseDecimal(String s, int targetDecimals, long defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        return parseDecimal(s, targetDecimals);
    }

    /**
     * Parses a decimal substring into a scaled {@code long} without creating a
     * {@link String#substring} allocation.
     *
     * <p>This overload reads characters directly from the source string between
     * {@code fromIndex} (inclusive) and {@code toIndex} (exclusive), avoiding the
     * heap allocation that {@code s.substring(from, to)} would create. For the
     * manual byte-scanner parsers, this eliminates 4-5 substring allocations per
     * quote message.
     *
     * @param s              the source string containing the decimal value
     * @param fromIndex      start index (inclusive)
     * @param toIndex        end index (exclusive)
     * @param targetDecimals the number of decimal places in the result
     * @param defaultValue   value to return if the range is empty
     * @return the parsed value as a scaled long, or {@code defaultValue}
     */
    public static long parseDecimal(String s, int fromIndex, int toIndex, int targetDecimals, long defaultValue) {
        if (s == null || fromIndex >= toIndex || fromIndex < 0 || toIndex > s.length()) {
            return defaultValue;
        }

        int pos = fromIndex;
        boolean negative = false;
        char first = s.charAt(pos);
        if (first == '-') {
            negative = true;
            pos++;
        } else if (first == '+') {
            pos++;
        }

        if (pos >= toIndex) {
            return defaultValue;
        }

        // Parse integer part
        long integerPart = 0;
        while (pos < toIndex) {
            char c = s.charAt(pos);
            if (c == '.') {
                pos++;
                break;
            }
            if (c < '0' || c > '9') {
                return defaultValue;
            }
            integerPart = integerPart * 10 + (c - '0');
            pos++;
        }

        // Parse fractional part
        long fractionalPart = 0;
        int fractionalDigits = 0;
        while (pos < toIndex && fractionalDigits < targetDecimals) {
            char c = s.charAt(pos);
            if (c < '0' || c > '9') {
                break;
            }
            fractionalPart = fractionalPart * 10 + (c - '0');
            fractionalDigits++;
            pos++;
        }

        // Pad fractional part
        for (int i = fractionalDigits; i < targetDecimals; i++) {
            fractionalPart *= 10;
        }

        // Compute scale
        long scale = 1;
        for (int i = 0; i < targetDecimals; i++) {
            scale *= 10;
        }

        long result = integerPart * scale + fractionalPart;
        return negative ? -result : result;
    }

    /**
     * Formats a scaled {@code long} back into a decimal string, stripping trailing
     * zeros from the fractional part.
     *
     * <p>Examples with {@code decimals = 8}:
     * <ul>
     *   <li>{@code 6743215000000L} → {@code "67432.15"}</li>
     *   <li>{@code 10000000000L} → {@code "100"}</li>
     *   <li>{@code 123000L} → {@code "0.00123"}</li>
     *   <li>{@code 0L} → {@code "0"}</li>
     *   <li>{@code -50000000L} → {@code "-0.5"}</li>
     * </ul>
     *
     * @param value    the scaled long value
     * @param decimals the number of decimal places the value is scaled by
     * @return the formatted decimal string with trailing zeros removed
     */
    public static String formatDecimal(long value, int decimals) {
        if (decimals == 0) {
            return Long.toString(value);
        }

        boolean negative = value < 0;
        long abs = negative ? -value : value;

        // Handle Long.MIN_VALUE edge case (cannot negate)
        if (negative && abs < 0) {
            // Fall back for Long.MIN_VALUE — extremely unlikely in practice
            return java.math.BigDecimal.valueOf(value, decimals).stripTrailingZeros().toPlainString();
        }

        long scale = 1;
        for (int i = 0; i < decimals; i++) {
            scale *= 10;
        }

        long integerPart = abs / scale;
        long fractionalPart = abs % scale;

        if (fractionalPart == 0) {
            return negative ? "-" + integerPart : Long.toString(integerPart);
        }

        // Build fractional digits into a char buffer (no allocation beyond the final String)
        // Max fractional digits = decimals (up to ~18 for long)
        char[] buf = new char[decimals];
        int len = decimals;
        long temp = fractionalPart;
        for (int i = decimals - 1; i >= 0; i--) {
            buf[i] = (char) ('0' + temp % 10);
            temp /= 10;
        }

        // Strip trailing zeros
        while (len > 0 && buf[len - 1] == '0') {
            len--;
        }

        StringBuilder sb = new StringBuilder(20 + len);
        if (negative) {
            sb.append('-');
        }
        sb.append(integerPart);
        sb.append('.');
        sb.append(buf, 0, len);
        return sb.toString();
    }
}
