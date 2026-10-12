# Phạm vi bảo mật

NhaTroHomu là ứng dụng local dành cho người đang có quyền dùng máy và sổ. Không có server, tài khoản, vai trò hay mô hình các nhân viên không tin tưởng nhau dùng chung sổ.

## Đã có

- SQL nhận dữ liệu người dùng qua PreparedStatement; các tên bảng/column động dùng danh sách cho phép.
- Kiểm tra định dạng và ý nghĩa dữ liệu ở lớp ghi, không chỉ ở UI: tiền/chỉ số, mode, kỳ/ngày, tổng, thu vượt và dữ liệu stale.
- Khóa ghi trước khi tính số dư; transaction rollback khi một bước ghi thất bại; request key cho retry thu/chi.
- Giữ chứng từ đã hủy/đảo, audit và trigger chống thay/xóa qua SQL thông thường.
- Giới hạn độ dài input, kích thước ảnh và backup; lấy kích thước BLOB trước khi đọc nội dung khi validate.
- `trusted_schema=OFF`; phục hồi từ chối view/table/trigger không thuộc app hoặc guard bị thiếu/sửa, kiểm tra dữ liệu nghiệp vụ trước khi thay live.
- Chặn công thức trong CSV, kể cả sau khoảng trắng/xuống dòng. Không trình bày tên người/phòng như thẻ HTML Swing.
- OCR local gọi script cố định bằng danh sách đối số ProcessBuilder; ảnh không tải lên dịch vụ ngoài.
- Một phiên/sổ; khóa vòng đời connection khi phục hồi và giữ bản cứu hộ trước thay file.

Các nguyên tắc tham khảo: [SQL parameterization — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html), [semantic input validation — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html), [untrusted databases — SQLite](https://www.sqlite.org/security.html).

## Giới hạn

DB/backup/ảnh/PDF không mã hóa. Người có quyền sửa file, thay binary hoặc đọc bộ nhớ của máy có thể đọc/thay dữ liệu; trigger và nhật ký không phải bằng chứng chống giả mạo người đó. Backup có cấu trúc/số liệu hợp lệ cũng không chứng minh người tạo nó là người đáng tin.

Không có phê duyệt của người thứ hai khi chỉnh tiền thuê, đảo khoản thu/chi hoặc phục hồi. Mô hình nhiều nhân viên cần xác thực, phân quyền và quy trình phê duyệt riêng, không dùng chung file local rồi dựa vào ghi chú lý do.

Chưa tự sao lưu, ký bộ cài hoặc kiểm tra OCR trên tập ảnh đồng hồ thật. Các khoản do người dùng nhập vẫn cần đối chiếu chứng từ/ngân hàng; báo cáo không chứng minh tiền đã chuyển thực tế.

## Báo lỗi

Không đưa database, ảnh giấy tờ, liên hệ người thuê, khóa hoặc token vào issue. Mô tả phiên bản, thao tác tái hiện và dữ liệu giả tối thiểu. Lỗi có thể ảnh hưởng tiền/dữ liệu cần test hồi quy trước khi phát hành.
