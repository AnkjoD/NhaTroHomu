# Changelog

## 0.6.0 — 2026-10-12

- Đổi tên sản phẩm thành NhaTroHomu và chia namespace Java theo chức năng.
- Kiểm tra tổng, lượng dùng, số đầu kỳ và nghĩa vụ thuê tại ranh giới lưu dữ liệu; chặn bỏ tháng và lưu trữ phòng vẫn có nghĩa vụ.
- Giữ hóa đơn đã hủy, khoản thu/chi đã đảo và hợp đồng nhập nhầm; thêm audit, request key, guard lịch sử và backup validation.
- Chuyển nước khoán/đồng hồ tại ranh giới kỳ có số bàn giao.
- Báo cáo thu theo ngày, công nợ/tuổi nợ, kỳ thuê thiếu, chứng từ đã hủy, nhật ký; sổ khoản đã chi và CSV.
- Bộ kiểm tra hồi quy chạy qua PowerShell và Maven; hỗ trợ dùng lại profile Sổ Trọ cũ.

## 0.5.0

Quản lý người thuê, hợp đồng, cọc, hóa đơn tiền thuê/điện/nước/phí, công nợ và demo local.
