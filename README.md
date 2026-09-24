# common_csv_2

VAT calculator for metadata-driven JSON orders in Java.

## Overview

`OrderCalculator` reads an order from a JSON file, maps each row to the configured metadata aliases, validates the document, calculates VAT values using `BigDecimal`, and writes the calculated result to an output JSON file.

The project follows the required business rules:

- `RoundingMode.HALF_UP`
- money precision fixed at scale 2
- currency is `VND`
- input metadata resolves aliases with case-insensitive and trimmed comparison
- invalid input is rejected before writing output

## Public API

```java
public static void calculate(Path inputJson, Path outputJson) throws IOException
```

### JavaDoc-style contract

```java
/**
 * Reads an order, calculates line amounts, and writes the calculated order.
 *
 * <p>When the input document is invalid, this method prints a corrective
 * message to the console and returns without creating or overwriting the
 * output file.</p>
 *
 * @param inputJson path to the UTF-8 input JSON file
 * @param outputJson path to the UTF-8 output JSON file
 * @throws IOException if the input file cannot be read or the output file cannot be written
 */
public static void calculate(Path inputJson, Path outputJson) throws IOException
```

## Supported input format

The calculator expects a metadata-driven order JSON like the sample in [examples/input.json](examples/input.json):

```json
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
```

## Output format

The program preserves the original document and adds computed values per row, plus a summary section:

```json
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
      "% vat": 0.10,
      "beforeTax": 200001.00,
      "vatAmount": 20000.10,
      "afterTax": 220001.10
    }
  ],
  "summary": {
    "totalVat": 20000.10,
    "totalPayable": 220001.10
  }
}
```

## Calculation rules

For each row:

```text
beforeTax = round(quantity * unitPrice, 2)
vatAmount = round(beforeTax * vatRate, 2)
afterTax  = round(beforeTax + vatAmount, 2)
```

For the entire order:

```text
totalVat = round(sum(vatAmount), 2)
totalPayable = round(sum(afterTax), 2)
```

All calculations use `BigDecimal` and `RoundingMode.HALF_UP`.

## Validation behavior

The calculator rejects invalid data before writing output. Typical validation checks include:

- missing metadata or wrong structure
- missing or duplicate alias mapping
- required row fields absent
- negative or non-integer quantity
- negative price or VAT
- VAT greater than `1.00`
- empty product code or product name
- empty rows list

When invalid, it prints a clear message and returns without overwriting the output file.

## Project structure

```text
src/
  com/
    splam/
      csv/
        OrderCalculator.java
        OrderCalculatorTest.java
examples/
  input.json
  output.json
README.md
```

## Compile

### Windows PowerShell

```powershell
cd "D:\posco-dx\github.com\syphuonglam\common_csv_2"
javac -d out\classes -sourcepath src src\com\splam\csv\OrderCalculator.java
```

### Linux/macOS

```bash
cd /path/to/common_csv_2
javac -d out/classes -sourcepath src src/com/splam/csv/OrderCalculator.java
```

## Run the calculator

```java
Path input = Path.of("examples", "input.json");
Path output = Path.of("out", "result.json");
OrderCalculator.calculate(input, output);
```

## Run tests

The project ships with a simple Java test harness without external dependencies.

```powershell
cd "D:\posco-dx\github.com\syphuonglam\common_csv_2"
javac -d out\classes -sourcepath src src\com\splam\csv\OrderCalculator.java src\com\splam\csv\OrderCalculatorTest.java
java -cp out\classes com.splam.csv.OrderCalculatorTest
```

### Test coverage included

- valid calculation for sample order
- alias resolution with whitespace and case normalization
- invalid input does not overwrite an existing output file

## Notes

- Public API is intentionally minimal.
- Business logic is implemented with Java Standard Library only.
- The JSON parser is intentionally compact and tailored to this project’s expected structure.
- The documentation follows JavaDoc conventions for API clarity and maintenance.
