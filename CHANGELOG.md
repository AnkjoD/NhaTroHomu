# Changelog

## 0.9.0

- Mục Thuê phòng gom hồ sơ và lượt thuê; thêm người trong form, giá mặc định lấy từ phòng nhưng thỏa thuận và hóa đơn giữ giá riêng.
- Schema 7 giữ khoảng ngày hóa đơn và mốc bàn giao IN/OUT; cho phép đổi người trong cùng tháng nếu ngày không trùng.
- Trả phòng chốt hóa đơn cuối và trạng thái thuê trong một transaction; dùng lại hóa đơn cuối khớp, giữ các lần thu/nợ/cọc của người cũ.
- Gợi ý tiền phòng/phí và nước khoán theo ngày; không tính điện/nước lúc trống vào người mới. PDF/CSV/lịch sử hiện người và khoảng ngày.
- Nâng cấp có backup; không tự suy ra giá phòng hay tính lại chứng từ cũ. Chưa hỗ trợ trả sớm sau hóa đơn đã chốt quá ngày trả bằng chứng từ điều chỉnh/hoàn tiền.

## 0.8.4

- Hóa đơn xem theo tháng, lập hóa đơn trực tiếp, xuất CSV theo phạm vi đã chọn. Cho thuê phòng thêm người thuê trong cùng form.

## 0.8.3

- Sửa mở/thu gọn thẻ, chiều cao độc lập và cuộn về đầu khi đổi bảng/thẻ; giải thích các trường thuê và hiện tiền phòng.

## 0.8.2

- Gói RapidOCR tùy chọn chạy offline trong worker Java riêng, cùng lựa chọn Windows OCR; xác nhận số trước khi chốt.

## 0.8.1

- Màn hình thu tiền nhấn mạnh tiền thực nhận, thu một phần và nợ còn lại; không tự điền coi như thu đủ.
- Điền số còn thiếu là thao tác riêng; khóa ghi thêm khi đã đủ. Kiểm tra thu nhiều lần, nợ qua kỳ và đảo thu.


## 0.8.0

- Thẻ / Bảng dùng chung dữ liệu và thao tác cho danh sách, báo cáo, lịch sử; nhớ lựa chọn trên máy.
- Lịch chọn ngày/tháng, thanh Homura / Madoka có biểu tượng riêng.
- Hồ sơ hiện phòng thuê và mở liên kết hợp đồng, hóa đơn, công nợ; chọn người sẵn khi gắn phòng.
- Ảnh lớn để khoanh vùng số OCR; giữ ảnh gốc, vẫn xác nhận số thủ công. Chưa có cải thiện độ chính xác OCR được khẳng định.


## 0.7.2

- Schema 6 hỗ trợ hóa đơn lịch sử có tiền phòng khi chưa có hồ sơ hợp đồng; yêu cầu nhật ký nhập.
- Giữ thành tiền nước gốc khác lượng × giá, có dấu vết và bảo vệ khỏi sửa; không đổi giá/số đo để ép tổng.
- PDF, CSV, lịch sử và dashboard dùng đúng thành tiền gốc; bảng phòng phân biệt lịch sử thuê với phòng trống.


## 0.7.1

- Thao tác theo dòng chỉ hiện khi chọn; menu chuột phải, nhấp đúp / Enter mở thao tác mặc định.
- Nút cập nhật dùng icon; icon thanh bên riêng từng mục. Lưu vẫn tự cập nhật; không bỏ yêu cầu tải lại đang chờ.
- Báo cáo giữ tab đang xem khi cập nhật, đổi bộ lọc phòng tự tải lại.


## 0.7.0

- Thu gọn trang phòng, chuyển một cột khi cửa sổ hẹp; thêm lịch sử điện nước theo phòng/khoảng kỳ.
- Sửa CSV lệch cột; bổ sung số cũ/mới, thành tiền từng tiện ích và tổng điện nước; PDF/TXT có tổng điện nước.
- Hồ sơ CCCD/giấy tờ, ngày sinh, địa chỉ, Zalo tự điền có thể ghi đè và danh sách người ở cùng. Schema 5 nâng cấp có backup.

## 0.6.0 — 2026-10-12

- Đổi tên sản phẩm thành NhaTroHomu và chia namespace Java theo chức năng.
- Kiểm tra tổng, lượng dùng, số đầu kỳ và nghĩa vụ thuê tại ranh giới lưu dữ liệu; chặn bỏ tháng và lưu trữ phòng vẫn có nghĩa vụ.
- Giữ hóa đơn đã hủy, khoản thu/chi đã đảo và hợp đồng nhập nhầm; thêm audit, request key, guard lịch sử và backup validation.
- Chuyển nước khoán/đồng hồ tại ranh giới kỳ có số bàn giao.
- Báo cáo thu theo ngày, công nợ/tuổi nợ, kỳ thuê thiếu, chứng từ đã hủy, nhật ký; sổ khoản đã chi và CSV.
- Bộ kiểm tra hồi quy chạy qua PowerShell và Maven; hỗ trợ dùng lại profile Sổ Trọ cũ.

## 0.5.0

Quản lý người thuê, hợp đồng, cọc, hóa đơn tiền thuê/điện/nước/phí, công nợ và demo local.
