package com.splam.csv;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calculates order amounts from a metadata-driven JSON order.
 */
public final class OrderCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final List<String> REQUIRED_ROLES = List.of(
            "quantity", "unitPrice", "vatRate");

    private OrderCalculator() {
    }

    /**
     * Reads an order, calculates line amounts and writes the calculated order.
     *
     * <p>When the input is invalid, this method prints corrective guidance and
     * returns without creating or replacing the output file.</p>
     *
     * @param inputJson path to the UTF-8 input JSON file
     * @param outputJson path to the UTF-8 output JSON file
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public static void calculate(Path inputJson, Path outputJson) throws IOException {
        try {
            String source;
            try {
                source = Files.readString(inputJson, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                System.out.println("Invalid input: Cannot read input JSON file.");
                System.out.println("Please check the input path and file permissions.");
                return;
            }
            Object parsed = new MiniJsonParser(source).parse();
            Map<String, Object> document = requireObject(parsed, "root");
            validateDocument(document);
            Map<String, Object> result = calculateDocument(document);
            Files.writeString(outputJson, JsonWriter.write(result), StandardCharsets.UTF_8);
        } catch (InvalidInputException exception) {
            System.out.println("Invalid input: " + exception.getMessage());
            System.out.println("Please correct input.json and try again.");
        }
    }

    private static void validateDocument(Map<String, Object> document) {
        Map<String, Object> columns = requireObject(document.get("columns"), "columns");
        for (String role : REQUIRED_ROLES) {
            List<Object> aliases = requireArray(columns.get(role), "columns." + role);
            if (aliases.isEmpty()) {
                invalid("columns." + role + " must contain at least one alias.");
            }
            for (Object alias : aliases) {
                if (!(alias instanceof String text) || text.trim().isEmpty()) {
                    invalid("Every alias in columns." + role + " must be a non-empty string.");
                }
            }
        }

        String vatFormat = requireString(document.get("vatFormat"), "vatFormat");
        if (!"decimal".equals(vatFormat)) {
            invalid("vatFormat must be 'decimal'.");
        }
        if (!"VND".equals(requireString(document.get("currency"), "currency"))) {
            invalid("currency must be 'VND'.");
        }
        if (asBigDecimal(document.get("roundingScale"), "roundingScale")
                .compareTo(BigDecimal.valueOf(MONEY_SCALE)) != 0) {
            invalid("roundingScale must be 2.");
        }
        if (!"HALF_UP".equals(requireString(document.get("roundingMode"), "roundingMode"))) {
            invalid("roundingMode must be 'HALF_UP'.");
        }

        List<Object> rows = requireArray(document.get("rows"), "rows");
        if (rows.isEmpty()) {
            invalid("rows must contain at least one order line.");
        }
        for (int index = 0; index < rows.size(); index++) {
            validateRow(requireObject(rows.get(index), "rows[" + index + "]"), index, columns);
        }
    }

    private static void validateRow(
            Map<String, Object> row,
            int rowIndex,
            Map<String, Object> columns) {
        requireValue(row, "stt", rowIndex);
        requireNonEmptyString(row, "mã mặt hàng", rowIndex);
        requireNonEmptyString(row, "tên mặt hàng", rowIndex);

        for (String role : REQUIRED_ROLES) {
            String fieldName = resolveFieldName(row, columns, role, rowIndex);
            BigDecimal value = asBigDecimal(row.get(fieldName), "rows[" + rowIndex + "]." + fieldName);
            if (value.signum() < 0) {
                invalid("rows[" + rowIndex + "]." + fieldName + " must not be negative.");
            }
            if ("quantity".equals(role) && value.stripTrailingZeros().scale() > 0) {
                invalid("rows[" + rowIndex + "]." + fieldName + " must be an integer.");
            }
            if ("vatRate".equals(role) && value.compareTo(ONE) > 0) {
                invalid("rows[" + rowIndex + "]." + fieldName + " must be between 0 and 1.");
            }
        }
    }

    private static Map<String, Object> calculateDocument(Map<String, Object> document) {
        Map<String, Object> columns = requireObject(document.get("columns"), "columns");
        List<Object> inputRows = requireArray(document.get("rows"), "rows");
        List<Object> outputRows = new ArrayList<>();
        BigDecimal totalVat = ZERO;
        BigDecimal totalPayable = ZERO;

        for (int index = 0; index < inputRows.size(); index++) {
            Map<String, Object> inputRow = requireObject(inputRows.get(index), "rows[" + index + "]");
            Map<String, Object> outputRow = new LinkedHashMap<>(inputRow);
            BigDecimal quantity = valueForRole(inputRow, columns, "quantity", index);
            BigDecimal unitPrice = valueForRole(inputRow, columns, "unitPrice", index);
            BigDecimal vatRate = valueForRole(inputRow, columns, "vatRate", index);

            BigDecimal beforeTax = money(quantity.multiply(unitPrice));
            BigDecimal vatAmount = money(beforeTax.multiply(vatRate));
            BigDecimal afterTax = money(beforeTax.add(vatAmount));

            outputRow.put("beforeTax", beforeTax);
            outputRow.put("vatAmount", vatAmount);
            outputRow.put("afterTax", afterTax);
            outputRows.add(outputRow);
            totalVat = totalVat.add(vatAmount);
            totalPayable = totalPayable.add(afterTax);
        }

        Map<String, Object> result = new LinkedHashMap<>(document);
        result.put("rows", outputRows);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalVat", money(totalVat));
        summary.put("totalPayable", money(totalPayable));
        result.put("summary", summary);
        return result;
    }

    private static BigDecimal valueForRole(
            Map<String, Object> row,
            Map<String, Object> columns,
            String role,
            int rowIndex) {
        String fieldName = resolveFieldName(row, columns, role, rowIndex);
        return asBigDecimal(row.get(fieldName), "rows[" + rowIndex + "]." + fieldName);
    }

    private static String resolveFieldName(
            Map<String, Object> row,
            Map<String, Object> columns,
            String role,
            int rowIndex) {
        List<Object> aliases = requireArray(columns.get(role), "columns." + role);
        String resolved = null;
        for (String fieldName : row.keySet()) {
            for (Object alias : aliases) {
                if (normalize(fieldName).equals(normalize((String) alias))) {
                    if (resolved != null && !resolved.equals(fieldName)) {
                        invalid("rows[" + rowIndex + "] has multiple fields for role '" + role + "'.");
                    }
                    resolved = fieldName;
                }
            }
        }
        if (resolved == null) {
            invalid("rows[" + rowIndex + "] is missing a field for role '" + role + "'.");
        }
        return resolved;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static void requireValue(Map<String, Object> row, String fieldName, int rowIndex) {
        if (!row.containsKey(fieldName) || row.get(fieldName) == null) {
            invalid("rows[" + rowIndex + "]." + fieldName + " is required.");
        }
    }

    private static void requireNonEmptyString(
            Map<String, Object> row,
            String fieldName,
            int rowIndex) {
        requireValue(row, fieldName, rowIndex);
        if (!(row.get(fieldName) instanceof String value) || value.trim().isEmpty()) {
            invalid("rows[" + rowIndex + "]." + fieldName + " must be a non-empty string.");
        }
    }

    private static BigDecimal asBigDecimal(Object value, String fieldName) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        invalid(fieldName + " must be a JSON number.");
        return ZERO;
    }

    private static String requireString(Object value, String fieldName) {
        if (!(value instanceof String text) || text.trim().isEmpty()) {
            invalid(fieldName + " must be a non-empty string.");
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?>)) {
            invalid(fieldName + " must be a JSON object.");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireArray(Object value, String fieldName) {
        if (!(value instanceof List<?>)) {
            invalid(fieldName + " must be a JSON array.");
        }
        return (List<Object>) value;
    }

    private static void invalid(String message) {
        throw new InvalidInputException(message);
    }

    private static final class InvalidInputException extends RuntimeException {
        private InvalidInputException(String message) {
            super(message);
        }
    }

    private static final class MiniJsonParser {
        private final String source;
        private int position;

        private MiniJsonParser(String source) {
            this.source = source;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != source.length()) {
                invalid("Unexpected content after the root JSON value.");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= source.length()) {
                invalid("Unexpected end of JSON input.");
            }
            return switch (source.charAt(position)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (position >= source.length() || source.charAt(position) != '"') {
                    invalid("JSON object keys must be strings.");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char current = source.charAt(position++);
                if (current == '"') {
                    return value.toString();
                }
                if (current == '\\') {
                    value.append(parseEscape());
                } else if (current < 0x20) {
                    invalid("Control characters are not allowed in JSON strings.");
                } else {
                    value.append(current);
                }
            }
            invalid("Unterminated JSON string.");
            return "";
        }

        private char parseEscape() {
            if (position >= source.length()) {
                invalid("Unterminated JSON escape sequence.");
            }
            return switch (source.charAt(position++)) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicodeEscape();
                default -> {
                    invalid("Invalid JSON escape sequence.");
                    yield 0;
                }
            };
        }

        private char parseUnicodeEscape() {
            if (position + 4 > source.length()) {
                invalid("Incomplete Unicode escape sequence.");
            }
            String hex = source.substring(position, position + 4);
            position += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                invalid("Invalid Unicode escape sequence.");
                return 0;
            }
        }

        private Object parseNumber() {
            int start = position;
            if (consume('-')) {
                if (position >= source.length()) {
                    invalid("Invalid JSON number.");
                }
            }
            if (consume('0')) {
                if (position < source.length() && Character.isDigit(source.charAt(position))) {
                    invalid("JSON numbers must not contain leading zeroes.");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (position < source.length()
                    && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                position++;
                if (position < source.length()
                        && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
                    position++;
                }
                requireDigits();
            }
            try {
                return new BigDecimal(source.substring(start, position));
            } catch (NumberFormatException exception) {
                invalid("Invalid JSON number.");
                return ZERO;
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, position)) {
                invalid("Invalid JSON literal.");
            }
            position += literal.length();
            return value;
        }

        private void requireDigits() {
            int start = position;
            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                position++;
            }
            if (start == position) {
                invalid("Expected a JSON digit.");
            }
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                invalid("Expected '" + expected + "' at JSON position " + position + ".");
            }
        }

        private boolean consume(char expected) {
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }
    }

    private static final class JsonWriter {
        private static String write(Object value) {
            StringBuilder output = new StringBuilder();
            append(value, output);
            output.append(System.lineSeparator());
            return output.toString();
        }

        private static void append(Object value, StringBuilder output) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String text) {
                appendString(text, output);
            } else if (value instanceof BigDecimal number) {
                output.append(number.toPlainString());
            } else if (value instanceof Boolean booleanValue) {
                output.append(booleanValue);
            } else if (value instanceof Map<?, ?> object) {
                appendObject(object, output);
            } else if (value instanceof List<?> array) {
                appendArray(array, output);
            } else {
                invalid("Unsupported value in output JSON.");
            }
        }

        private static void appendObject(Map<?, ?> object, StringBuilder output) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(String.valueOf(entry.getKey()), output);
                output.append(':');
                append(entry.getValue(), output);
            }
            output.append('}');
        }

        private static void appendArray(List<?> array, StringBuilder output) {
            output.append('[');
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                append(array.get(index), output);
            }
            output.append(']');
        }

        private static void appendString(String value, StringBuilder output) {
            output.append('"');
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\f' -> output.append("\\f");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    case '\t' -> output.append("\\t");
                    default -> {
                        if (current < 0x20) {
                            output.append(String.format("\\u%04x", (int) current));
                        } else {
                            output.append(current);
                        }
                    }
                }
            }
            output.append('"');
        }
    }
}