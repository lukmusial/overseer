package com.hft.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class FastDecimalParserTest {

    @Nested
    @DisplayName("parseDecimal — normal prices")
    class ParseNormalPrices {

        @Test
        @DisplayName("Binance price with 8 decimal places")
        void binancePrice() {
            long result = FastDecimalParser.parseDecimal("67432.15000000", 8);
            assertEquals(6_743_215_000_000L, result);
        }

        @Test
        @DisplayName("Alpaca price with 2 decimal places (cents)")
        void alpacaPrice() {
            long result = FastDecimalParser.parseDecimal("150.50", 2);
            assertEquals(15_050L, result);
        }

        @Test
        @DisplayName("Price with fewer fractional digits than target")
        void fewerFractionalDigits() {
            long result = FastDecimalParser.parseDecimal("67432.15", 8);
            assertEquals(6_743_215_000_000L, result);
        }

        @Test
        @DisplayName("Price with more fractional digits than target (truncation)")
        void moreFractionalDigits() {
            // "123.456789" with targetDecimals=2 should truncate to "123.45"
            long result = FastDecimalParser.parseDecimal("123.456789", 2);
            assertEquals(12_345L, result);
        }
    }

    @Nested
    @DisplayName("parseDecimal — no decimal point")
    class ParseNoDecimal {

        @Test
        @DisplayName("Integer string with scale 8")
        void integerScale8() {
            long result = FastDecimalParser.parseDecimal("100", 8);
            assertEquals(10_000_000_000L, result);
        }

        @Test
        @DisplayName("Integer string with scale 2")
        void integerScale2() {
            long result = FastDecimalParser.parseDecimal("100", 2);
            assertEquals(10_000L, result);
        }

        @Test
        @DisplayName("Integer string with scale 0")
        void integerScale0() {
            long result = FastDecimalParser.parseDecimal("42", 0);
            assertEquals(42L, result);
        }
    }

    @Nested
    @DisplayName("parseDecimal — negative numbers")
    class ParseNegative {

        @Test
        @DisplayName("Negative decimal with leading zero")
        void negativeLeadingZero() {
            long result = FastDecimalParser.parseDecimal("-0.00123", 8);
            assertEquals(-123_000L, result);
        }

        @Test
        @DisplayName("Negative integer")
        void negativeInteger() {
            long result = FastDecimalParser.parseDecimal("-100", 2);
            assertEquals(-10_000L, result);
        }

        @Test
        @DisplayName("Negative price")
        void negativePrice() {
            long result = FastDecimalParser.parseDecimal("-67432.15", 8);
            assertEquals(-6_743_215_000_000L, result);
        }
    }

    @Nested
    @DisplayName("parseDecimal — zero values")
    class ParseZero {

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.0", "0.00000000", "00", "+0"})
        @DisplayName("All zero representations should parse to 0")
        void zeroVariants(String input) {
            assertEquals(0L, FastDecimalParser.parseDecimal(input, 8));
        }
    }

    @Nested
    @DisplayName("parseDecimal — null/blank handling")
    class ParseNullBlank {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("Null and blank strings throw NumberFormatException")
        void nullAndBlankThrow(String input) {
            assertThrows(NumberFormatException.class,
                    () -> FastDecimalParser.parseDecimal(input, 8));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("Null and blank strings return default value in overload")
        void nullAndBlankReturnDefault(String input) {
            assertEquals(-1L, FastDecimalParser.parseDecimal(input, 8, -1L));
        }

        @Test
        @DisplayName("Non-blank string is parsed in default-value overload")
        void nonBlankParsedInDefaultOverload() {
            assertEquals(15_050L, FastDecimalParser.parseDecimal("150.50", 2, 0L));
        }
    }

    @Nested
    @DisplayName("parseDecimal — malformed input")
    class ParseMalformed {

        @ParameterizedTest
        @ValueSource(strings = {"abc", "12.34.56", "12a34", "-", "+"})
        @DisplayName("Malformed strings throw NumberFormatException")
        void malformedInput(String input) {
            assertThrows(NumberFormatException.class,
                    () -> FastDecimalParser.parseDecimal(input, 8));
        }
    }

    @Nested
    @DisplayName("parseDecimal — BigDecimal comparison")
    class BigDecimalComparison {

        @ParameterizedTest
        @CsvSource({
                "67432.15000000, 8",
                "150.50, 2",
                "100, 8",
                "-0.00123, 8",
                "0.00000001, 8",
                "99999.99, 2",
                "1.23456789, 8",
                "0.1, 8",
                "-42.0, 2"
        })
        @DisplayName("Results match BigDecimal computation")
        void matchesBigDecimal(String input, int targetDecimals) {
            long expected = new BigDecimal(input)
                    .setScale(targetDecimals, RoundingMode.DOWN)
                    .movePointRight(targetDecimals)
                    .longValueExact();

            long actual = FastDecimalParser.parseDecimal(input, targetDecimals);
            assertEquals(expected, actual,
                    "Mismatch for input=\"" + input + "\", targetDecimals=" + targetDecimals);
        }
    }

    @Nested
    @DisplayName("formatDecimal")
    class FormatDecimal {

        @Test
        @DisplayName("Formats Binance price — strips trailing zeros")
        void binancePrice() {
            assertEquals("67432.15", FastDecimalParser.formatDecimal(6_743_215_000_000L, 8));
        }

        @Test
        @DisplayName("Formats integer value — no decimal point")
        void integerValue() {
            assertEquals("100", FastDecimalParser.formatDecimal(10_000_000_000L, 8));
        }

        @Test
        @DisplayName("Formats small fraction with leading zeros")
        void smallFraction() {
            assertEquals("0.00123", FastDecimalParser.formatDecimal(123_000L, 8));
        }

        @Test
        @DisplayName("Formats zero")
        void zeroValue() {
            assertEquals("0", FastDecimalParser.formatDecimal(0L, 8));
        }

        @Test
        @DisplayName("Formats negative value")
        void negativeValue() {
            assertEquals("-0.5", FastDecimalParser.formatDecimal(-50_000_000L, 8));
        }

        @Test
        @DisplayName("Formats with zero decimals")
        void zeroDecimals() {
            assertEquals("42", FastDecimalParser.formatDecimal(42L, 0));
        }

        @Test
        @DisplayName("Formats smallest unit")
        void smallestUnit() {
            assertEquals("0.00000001", FastDecimalParser.formatDecimal(1L, 8));
        }

        @Test
        @DisplayName("Formats with scale 2 (cents)")
        void centScale() {
            assertEquals("150.5", FastDecimalParser.formatDecimal(15_050L, 2));
        }
    }

    @Nested
    @DisplayName("Round-trip: parseDecimal → formatDecimal")
    class RoundTrip {

        @ParameterizedTest
        @CsvSource({
                "67432.15, 8",
                "100, 8",
                "0.00123, 8",
                "0, 8",
                "-42.5, 2",
                "150.5, 2",
                "0.00000001, 8",
                "1, 0"
        })
        @DisplayName("Round-trip preserves value")
        void roundTrip(String input, int decimals) {
            long parsed = FastDecimalParser.parseDecimal(input, decimals);
            String formatted = FastDecimalParser.formatDecimal(parsed, decimals);
            long reparsed = FastDecimalParser.parseDecimal(formatted, decimals);

            assertEquals(parsed, reparsed,
                    "Round-trip failed for input=\"" + input + "\", decimals=" + decimals
                            + ", formatted=\"" + formatted + "\"");
        }
    }
}
