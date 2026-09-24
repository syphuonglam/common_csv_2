package com.splam.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple Java test harness for the order calculator.
 */
public final class OrderCalculatorTest {

    private static int failed = 0;

    private OrderCalculatorTest() {
    }

    public static void main(String[] args) throws IOException {
        testValidCalculation();
        testAliasNormalization();
        testInvalidInputDoesNotOverwriteOutput();

        if (failed > 0) {
            System.out.println("FAILED: " + failed + " test(s) failed.");
            System.exit(1);
        }
        System.out.println("All tests passed.");
    }

    private static void testValidCalculation() throws IOException {
        Path input = Files.createTempFile("order-valid-input", ".json");
        Path output = Files.createTempFile("order-valid-output", ".json");
        Files.writeString(input, """
            {
              "columns": {
                "quantity": ["số lượng", "quantity", "qty"],
                "unitPrice": ["đơn giá", "unit price", "price"],
                "vatRate": ["vat", "% vat", "tax rate"]
              },
              "vatFormat": "decimal",
              "vatExample": "0.10",
              "currency": "VND",
              "roundingScale": 2,
              "roundingMode": "HALF_UP",
              "rows": [
                {
                  "stt": 1,
                  "mã mặt hàng": "MA001",
                  "tên mặt hàng": "Tên hàng 1",
                  "số lượng": 2,
                  "đơn giá": 100000.50,
                  "% vat": 0.10
                }
              ]
            }
            """, StandardCharsets.UTF_8);

        OrderCalculator.calculate(input, output);

        String actual = Files.readString(output, StandardCharsets.UTF_8);
        assertContains(actual, "\"beforeTax\":200001.00", "beforeTax should be 200001.00");
        assertContains(actual, "\"vatAmount\":20000.10", "vatAmount should be 20000.10");
        assertContains(actual, "\"afterTax\":220001.10", "afterTax should be 220001.10");
        assertContains(actual, "\"totalVat\":20000.10", "summary totalVat should be 20000.10");
        assertContains(actual, "\"totalPayable\":220001.10", "summary totalPayable should be 220001.10");
    }

    private static void testAliasNormalization() throws IOException {
        Path input = Files.createTempFile("order-alias-input", ".json");
        Path output = Files.createTempFile("order-alias-output", ".json");
        Files.writeString(input, """
            {
              "columns": {
                "quantity": [" Quantity ", "QTY"],
                "unitPrice": ["UNIT PRICE", "price"],
                "vatRate": ["VAT", "% VAT"]
              },
              "vatFormat": "decimal",
              "currency": "VND",
              "roundingScale": 2,
              "roundingMode": "HALF_UP",
              "rows": [
                {
                  "stt": 1,
                  "mã mặt hàng": "MA002",
                  "tên mặt hàng": "Tên hàng 2",
                  " QUANTITY ": 3,
                  "unit price": 200.00,
                  "% VAT": 0.05
                }
              ]
            }
            """, StandardCharsets.UTF_8);

        OrderCalculator.calculate(input, output);

        String actual = Files.readString(output, StandardCharsets.UTF_8);
        assertContains(actual, "\"beforeTax\":600.00", "normalized alias should still calculate beforeTax");
        assertContains(actual, "\"vatAmount\":30.00", "normalized alias should still calculate VAT");
        assertContains(actual, "\"afterTax\":630.00", "normalized alias should still calculate afterTax");
    }

    private static void testInvalidInputDoesNotOverwriteOutput() throws IOException {
        Path input = Files.createTempFile("order-invalid-input", ".json");
        Path output = Files.createTempFile("order-invalid-output", ".json");
        Files.writeString(input, """
            {
              "columns": {
                "quantity": ["số lượng"],
                "unitPrice": ["đơn giá"],
                "vatRate": ["vat"]
              },
              "vatFormat": "decimal",
              "currency": "VND",
              "roundingScale": 2,
              "roundingMode": "HALF_UP",
              "rows": [
                {
                  "stt": 1,
                  "mã mặt hàng": "MA003",
                  "tên mặt hàng": "Tên hàng 3",
                  "số lượng": 2.5,
                  "đơn giá": 100,
                  "vat": 0.10
                }
              ]
            }
            """, StandardCharsets.UTF_8);

        Files.writeString(output, "KEEP_ME", StandardCharsets.UTF_8);

        OrderCalculator.calculate(input, output);

        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertEquals("KEEP_ME", content, "Invalid input must not overwrite the existing output file");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            failed++;
            System.out.println("FAIL: " + message + "\nExpected to find: " + expected + "\nActual: " + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            failed++;
            System.out.println("FAIL: " + message + "\nExpected: " + expected + "\nActual: " + actual);
        }
    }
}
