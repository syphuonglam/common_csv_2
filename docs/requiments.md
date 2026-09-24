# Yêu cầu: Tính tiền đơn hàng từ JSON bằng Java

## 1. Mục tiêu

Xây dựng một hàm Java 21 xử lý một đơn hàng từ `input.json` và ghi kết quả vào
`output.json`.

Hàm phải tính tổng tiền trước thuế, tiền VAT và tổng tiền sau thuế cho từng mặt
hàng, đồng thời tính tổng VAT và tổng tiền khách hàng phải thanh toán.

Source code và JavaDoc phải được viết bằng tiếng Anh.

## 2. Ràng buộc kỹ thuật

- Sử dụng Java 21.
- Không sử dụng Maven, Spring hoặc Spring Boot.
- Chỉ sử dụng Java Standard Library.
- Không sử dụng thư viện JSON bên ngoài.
- Sử dụng `BigDecimal` cho toàn bộ phép tính tài chính.
- Không sử dụng `double` hoặc `float` để tính tiền.
- Sử dụng `RoundingMode.HALF_UP` với scale bằng 2.
- Đơn vị tiền tệ là Việt Nam đồng (`VND`).
- Giá trị tiền trong output phải có đúng 2 chữ số thập phân.
- API công khai chỉ cung cấp một hàm cho khách hàng sử dụng.

## 3. Định dạng input.json

File đầu vào gồm metadata mô tả vai trò các cột và danh sách các dòng hàng hóa.
Thứ tự của các thuộc tính JSON không có ý nghĩa.

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

### 3.1. Metadata định nghĩa vai trò cột

Chương trình không được phụ thuộc vào vị trí của cột. Vai trò nghiệp vụ phải
được xác định bằng metadata và alias:

| Vai trò | Alias mặc định |
| --- | --- |
| `quantity` | `số lượng`, `quantity`, `qty` |
| `unitPrice` | `đơn giá`, `unit price`, `price` |
| `vatRate` | `vat`, `% vat`, `tax rate` |

Khi tìm alias, chương trình phải bỏ khoảng trắng đầu/cuối và không phân biệt
chữ hoa, chữ thường. Không được suy đoán vai trò cột dựa trên giá trị dữ liệu.

Nếu thiếu alias, có nhiều alias cùng khớp một vai trò hoặc metadata không đúng
cấu trúc, input được xem là không hợp lệ.

Mỗi dòng hàng phải có các trường `stt`, mã mặt hàng, tên mặt hàng và một cột
cho từng vai trò `quantity`, `unitPrice`, `vatRate`.

## 4. Quy tắc dữ liệu

### 4.1. Số lượng

- Bắt buộc là số nguyên không âm.
- Không cho phép số thập phân.
- Không cho phép giá trị rỗng hoặc không phải số.

### 4.2. Đơn giá

- Bắt buộc là số không âm.
- Cho phép số nguyên hoặc số thập phân.
- Được xử lý bằng `BigDecimal`.

### 4.3. VAT

- VAT được nhập dưới dạng số thập phân; `0.10` có nghĩa là 10%.
- Giá trị hợp lệ nằm trong khoảng từ `0.00` đến `1.00`.
- `0.00` tương đương 0% và `0.05` tương đương 5%.

### 4.4. Mã và tên mặt hàng

- Không được rỗng.
- Phải được giữ nguyên trong kết quả.
- Phải hỗ trợ dữ liệu Unicode, bao gồm tiếng Việt.

## 5. Công thức tính toán

Với mỗi dòng hàng hóa:

```text
beforeTax = round(quantity * unitPrice, 2)
vatAmount = round(beforeTax * vatRate, 2)
afterTax  = round(beforeTax + vatAmount, 2)
```

Tổng đơn hàng:

```text
totalVat = round(sum(vatAmount), 2)
totalPayable = round(sum(afterTax), 2)
```

Mọi phép làm tròn sử dụng `scale = 2` và `RoundingMode.HALF_UP`.

## 6. Định dạng output.json

Output phải giữ lại metadata và các dòng hàng hóa, đồng thời bổ sung:

- `beforeTax`: tổng tiền trước thuế của dòng hàng.
- `vatAmount`: tiền VAT của dòng hàng.
- `afterTax`: tổng tiền sau thuế của dòng hàng.

Output cũng phải có phần `summary` gồm `totalVat` và `totalPayable`.

Ví dụ:

```json
{
  "currency": "VND",
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

## 7. Xử lý input không hợp lệ

Khi input không hợp lệ, hàm phải:

1. In thông báo hướng dẫn bằng `System.out.println()`.
2. Nêu rõ nguyên nhân và cách sửa dữ liệu.
3. Không tạo file output.
4. Không ghi đè file output hiện có.
5. Không ghi output một phần.

Các lỗi tối thiểu phải xử lý:

- File không tồn tại hoặc JSON không hợp lệ.
- Thiếu metadata hoặc metadata sai cấu trúc.
- Thiếu hoặc trùng vai trò cột.
- Thiếu trường bắt buộc trong dòng hàng.
- Số lượng là số thập phân.
- Đơn giá hoặc VAT là số âm.
- VAT lớn hơn `1.00`.
- Giá trị số không hợp lệ.
- Mã hàng hoặc tên hàng rỗng.
- Danh sách hàng hóa rỗng.

## 8. API công khai

API đề xuất:

```java
public static void calculate(Path inputJson, Path outputJson) throws IOException
```

Yêu cầu đối với API:

- Có đúng một hàm public phục vụ khách hàng.
- Có JavaDoc bằng tiếng Anh cho class và method public.
- Sử dụng `Path` cho file input và output.
- Các hàm hỗ trợ khác, nếu cần, phải có phạm vi `private`.
- Không yêu cầu `main` method.

## 9. Tiêu chí nghiệm thu

Source code được chấp nhận khi:

- Biên dịch được bằng Java 21 và `javac`.
- Không sử dụng Maven, Spring, Spring Boot hoặc thư viện ngoài.
- Xử lý đúng khi thứ tự thuộc tính trong JSON thay đổi.
- Xác định đúng vai trò cột thông qua metadata.
- Dùng `BigDecimal` và `RoundingMode.HALF_UP`.
- Làm tròn đúng 2 chữ số tại mọi bước tính toán.
- Tính đúng tiền trước thuế, VAT và sau thuế.
- Tính đúng tổng VAT và tổng tiền phải trả.
- Không tạo hoặc ghi đè output khi input không hợp lệ.
- Có JavaDoc tiếng Anh cho API công khai.
- Hỗ trợ dữ liệu tiếng Việt và Unicode.