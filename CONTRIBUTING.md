# Đóng góp

JDK 21. Chạy `run.ps1 -Test` trên Windows hoặc `mvn test`; kiểm tra GUI trong phiên desktop, OCR tích hợp chạy riêng khi có gói OCR Windows.

Giữ thay đổi theo chức năng. UI không là nơi duy nhất kiểm tra tiền; ghi dữ liệu phải xác thực lại trạng thái trong transaction. Khi thay schema, cung cấp migration có backup, mapping và test phục hồi. Không sửa công thức cũ làm đổi số dư đã phát hành.

Test dùng dữ liệu giả và thư mục tạm. Thêm tình huống biên có ý nghĩa: tháng đầu, khoảng ngày, receipt retry, cạnh tranh, rollback và dữ liệu cũ. Giữ comment giải thích lý do/quy tắc, tránh thuật lại từng câu lệnh.

Không commit dữ liệu local, bản xuất, token, khóa, thư mục build, tài liệu học/ghi chú riêng. Kiểm tra `git diff --cached` trước khi commit. README phải phân biệt tính năng đang chạy với kế hoạch.
