Yêu cầu: Viết 01 hàm java
Đầu vào: input.csv (1 đơn hàng)
Mô tả đầu vào: 
Header: stt,mã mặt hàng,tên mặt hàng,số lượng,đơn giá,% vat  
Row 0,<1>,<MA001>,<Tên hàng 1>,<đơn giá 1>,<số lượng 1>,< % vat>
Row 1,<2>,<MA002>,<Tên hàng 1>,<đơn giá 2>,<số lượng 2>,< % vat> 
=> Các giá trị trong đây khách hàng sẽ nhập đủ nếu sai thứ tự cột sẽ xuất ra thông báo hướng dẫn (dùng system.println())

Đầu ra: output.csv 01 đơn hàng có thêm tổng trước thuế và sau thuế cho từng mặt hàng và thêm 2 dòng mới tổng VAT của đơn hàng
Header: stt,mã mặt hàng,tên mặt hàng,số lượng,đơn giá,% vat,[Tổng tiền trước thuế], [Tổng tiền sau thuế]
Row 0,<1>,<MA001>,<Tên hàng 1>,<đơn giá 1>,<số lượng 1>,< % vat>,[output1],[output2]
Row 1,<2>,<MA002>,<Tên hàng 1>,<đơn giá 2>,<số lượng 2>, < % vat>, [output1],[output2]
Tổng VAT đơn hàng [output3]
Tổng tiền khách hàng phải trả [output4]

Hàm chúng ta cần cung cấp cho khách hàng sẽ thêm các giá trị các field [output1],[output2],[output3],[output4] theo công thức chuẩn kế toán tài chính.
=> Chúng ta sẽ làm 01 hàm đáp ứng yêu cầu này nhận vào input.csv và output.csv như mô tả ở trên và tài liệu hướng dẫn

Lưu ý hàm này viết chuẩn java doc và dùng tiếng anh.

Yêu cầu ràng buộc thêm
Có header, không cần phân biệt tên cột chỉ cần thứ tự và giá trị của cột. Nếu định dạng không đúng phải thông báo hướng dẫn
Cột số lượng không cho số thập phân
CSV chuẩn bằng dấu phẩy
Đơn giá và số lượng có cho phép số thập phân
VAT nhập dưới dạng 0.10 (tức là 10%)
Làm tròn 2 số tại tất cả các vị trí tính toán, chốt là RoundingMode.HALF_UP
Tiền tệ là việt nam đồng
Số chữ số thập phân của tiền là 2 chữ số
Nếu input sai, không tạo output và in ra màn hình thông báo có hướng dẫn
Hai dòng tổng có cấu trúc cột giống như các dòng khác và nằm dưới cuối của file
Hàm nhận String hay Path => Tùy bạn quyết định sao cho nhanh gọn dễ hiểu ít mã nguồn nhất
Dùng thư viện Java Standard Library

Gợi ý
input.json
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
  "roundingMode": "HALF_UP"
}