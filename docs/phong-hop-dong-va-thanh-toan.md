# Phòng, hợp đồng và thanh toán

## Vì sao thêm phòng chưa có tiền phòng?

Phòng lưu tên căn phòng, chỉ số đầu kỳ và cách tính nước. Người thuê lưu họ tên, liên hệ, giấy tờ và người ở cùng. Hợp đồng gắn người thuê với phòng trong một khoảng thời gian, đồng thời lưu giá thuê, cọc, ngày thu và phí định kỳ. Vì giá có thể đổi giữa các lần thuê, giá thuê hiện nằm trong hợp đồng, không nằm trong thông tin đồng hồ của phòng.

Luồng thông thường: thêm phòng → **Cho thuê phòng** → chọn người thuê đã có hoặc bấm **Thêm người thuê** ngay trong form → điền giá và thời hạn → lưu thỏa thuận → lập hóa đơn từng kỳ → ghi tiền thực nhận. Nút Cho thuê phòng nằm ở màn Phòng trọ và menu từng phòng; cũng có ở màn Hợp đồng. Không cần chuyển sang mục Người thuê để thêm hồ sơ. Hồ sơ người vừa thêm được lưu riêng; nếu hủy form cho thuê sau đó, hồ sơ vẫn còn để chọn lại. Thẻ phòng hiện giá hợp đồng nếu có hợp đồng hiện tại. Nếu chỉ có hóa đơn lịch sử, thẻ ghi rõ tiền phòng của kỳ gần nhất; số này không tự thành giá hợp đồng mới.

Để lập hóa đơn: vào **Hóa đơn → Lập hóa đơn → chọn phòng**, hoặc bấm **Lập hóa đơn** ngay trên thẻ phòng. Nhập chỉ số mới của điện/nước (không nhập số điện đã dùng), kiểm tra kỳ, tiền phòng, phí, tổng tiền và xác nhận trước khi lưu. Số cũ lấy từ kỳ trước. Có thể chọn ảnh để OCR rồi sửa số; lưu hóa đơn chưa có nghĩa đã thu tiền, cần ghi lần thu thực nhận riêng.

Màn Hóa đơn mặc định xem tháng mới nhất có dữ liệu. Dùng Tháng hóa đơn để chọn kỳ cũ hoặc Tất cả tháng. Tổng tiền phía dưới tính toàn phạm vi đã chọn, kể cả có nhiều trang. Xuất CSV tháng này xuất tất cả hóa đơn của tháng đang chọn; ở chế độ Tất cả tháng thì xuất toàn bộ. Các nút Xem hóa đơn/Thu tiền vẫn thao tác đúng hóa đơn đang chọn trong bảng hoặc thẻ.

Hóa đơn là số tiền đã chốt của một tháng: tiền phòng + tiền điện + tiền nước + phí. Công nợ là số tiền hóa đơn còn thiếu sau các lần thu hợp lệ. Hết hợp đồng không làm biến mất hóa đơn hoặc nợ.

## Các trường hợp đồng

| Trường | Ý nghĩa |
|---|---|
| Phòng | Phòng được cho thuê. |
| Người thuê đại diện | Người đứng tên thuê. Hồ sơ người ở cùng nằm ở Người thuê. |
| Ngày vào ở | Ngày bắt đầu thời hạn thuê. |
| Ngày kết thúc dự kiến | Ngày cuối của thời hạn thuê, tính cả ngày đó; khác hạn đóng tiền. Ví dụ 13/10/2026–12/10/2027 là thời hạn một năm. |
| Tiền phòng/tháng | Giá thuê cơ bản, chưa gồm điện nước và phí. |
| Mức cọc cần nhận | Mức cọc thỏa thuận; chưa phải khoản tiền đã thu. Ghi Nhận cọc riêng khi thực nhận. |
| Ngày hạn thu mỗi tháng | Ngày hóa đơn đến hạn, chọn 1–28 để có ngày này trong mọi tháng. Chọn 5 thì thường đến hạn ngày 5; tháng đầu không đặt hạn trước ngày bắt đầu thuê. |
| Phí/tháng | Phí lặp hằng tháng, mỗi dòng `Tên=số tiền`, ví dụ `Internet=100000`. Có thể xóa các dòng mẫu nếu không thu. Không nhập khoản chỉ thu một lần vào đây. |

App chưa tự chia tiền theo số ngày cho tháng đầu/cuối. Khi lập hóa đơn có thể chỉnh tiền phòng kỳ đó theo thỏa thuận và ghi lý do. Một phòng chỉ có một hợp đồng trong một tháng ở phiên bản này. Muốn tiếp tục sau ngày kết thúc, cần lập thời hạn tiếp theo; hiện chưa có chức năng gia hạn một hợp đồng đã dùng. Việc hết hạn chỉ ảnh hưởng trạng thái và phạm vi lập hóa đơn theo hợp đồng, không tự hoàn cọc hoặc thanh toán nợ.

## Hóa đơn cũ có sửa được không?

Phiên bản hiện tại không sửa đè hóa đơn đã chốt. Kỳ cuối chưa còn khoản thu và không vướng mốc đổi cách tính nước có thể dùng **Hủy kỳ cuối để sửa**, rồi lập lại. Bản cũ và ảnh được giữ để tra cứu. Không hủy các kỳ trước vì chỉ số đầu tháng sau phụ thuộc tháng trước.

Nếu nhập sai lần thu, dùng **Đảo lần thu nhập nhầm**, ghi lý do, rồi ghi lần thu đúng. Chỉ làm vậy khi khoản thu thật sự bị nhập nhầm; không đảo khoản tiền thực đã nhận chỉ để mở khóa sửa hóa đơn. Đây cũng không phải thao tác hoàn tiền thật cho khách.

Hóa đơn đã thu tiền hoặc có kỳ sau cần chứng từ điều chỉnh giữ lịch sử, đối chiếu chỉ số liên quan và xử lý tiền tăng/giảm. Luồng điều chỉnh này **chưa có**. Khi cần sửa trường hợp đó, giữ bản gốc và đối chiếu trước, không sửa database thủ công.

## Trả thiếu, nợ khác và trả trước

- **Trả thiếu hóa đơn:** hóa đơn 2.500.000đ, thu 2.000.000đ → còn nợ 500.000đ. Những lần thu sau ghi vào cùng hóa đơn. Không cộng 500.000đ lần nữa vào hóa đơn mới. App đã hỗ trợ.
- **Nợ phí định kỳ:** nếu phí đã nằm trong hóa đơn, phần chưa trả đã được tính trong công nợ chung. Hiện app chưa phân bổ từng lần thu cho riêng điện, nước hoặc phòng.
- **Nợ phát sinh riêng:** ví dụ khoản bồi thường đã thỏa thuận hoặc một khoản chỉ thu một lần cần chứng từ riêng, ngày phát sinh, người/phòng chịu trách nhiệm, số tiền, hạn thu và các lần thanh toán. App chưa có sổ khoản phải thu độc lập. Ghi chú không làm tăng công nợ.
- **Trả thừa/đóng trước tháng sau:** có thể xảy ra khi khách muốn trả trước. Hóa đơn 2.500.000đ, khách đưa 3.000.000đ: 2.500.000đ thanh toán hóa đơn, 500.000đ là tiền trả trước còn giữ cho khách. Tháng sau dùng 500.000đ để cấn trừ một lần. Nếu khách chuyển đi, phải đối chiếu và xử lý số còn giữ theo thỏa thuận. Đây không phải nợ âm hay tiền cọc. App hiện chặn khoản thu vượt phần còn thiếu và chưa có sổ trả trước, cấn trừ hoặc hoàn trả trước.

Các khả năng chưa hỗ trợ ở trên cần một sổ giao dịch có lịch sử, không thể thay bằng việc đổi tổng hóa đơn hoặc ghi thêm ghi chú. Tiền cọc cũng được quản lý riêng, không tự chuyển thành khoản thanh toán hóa đơn.

## Dữ liệu nhập từ Excel

Hóa đơn lịch sử có thể đã có tiền phòng nhưng chưa có hợp đồng vì nguồn thiếu người đại diện hoặc thời hạn thuê. Không tự đoán hợp đồng và không tự gắn một người mới vào hóa đơn đã chốt. App chặn tạo hợp đồng mới vào khoảng tháng đã có hóa đơn; tạo cho kỳ tiếp theo phù hợp. Muốn đối chiếu liên kết lịch sử cần thông tin xác nhận và một luồng cập nhật riêng.
