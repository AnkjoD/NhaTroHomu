# Changelog

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
