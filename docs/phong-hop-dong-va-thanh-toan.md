# Phòng, hợp đồng và thanh toán

## Giá phòng, giá thỏa thuận và hóa đơn

Phòng lưu tên, chỉ số đầu kỳ, cách tính nước và **giá thuê mặc định**. Khi thêm/sửa phòng, giá mặc định là bắt buộc; nhập 0 nếu miễn tiền phòng. Khi cho thuê, app dùng giá phòng và khóa ô tiền. Chỉ bật **Thỏa thuận giá riêng** nếu có thỏa thuận khác; tắt lựa chọn này sẽ lấy lại giá mặc định. Phòng từ dữ liệu cũ chưa có giá vẫn được giữ nguyên, nhưng cần vào Sửa phòng bổ sung trước khi tạo lượt thuê mới. Người thuê lưu hồ sơ con người. Lượt thuê/hợp đồng gắn người đó với phòng trong một khoảng thời gian và lưu giá đã thỏa thuận, cọc, hạn thu, phí. Sửa giá phòng không tự đổi giá thỏa thuận hoặc hóa đơn đã chốt. Giá trên hóa đơn lịch sử không bị tự đoán thành giá mặc định mới.

Luồng thông thường: thêm phòng và giá mặc định → **Cho thuê phòng** → chọn/thêm người thuê trong form → kiểm tra giá, thời hạn và chỉ số nhận phòng → lưu → lập hóa đơn từng kỳ → ghi tiền thực nhận. Mục **Thuê phòng** gom hai tab **Lượt thuê / trả phòng** và **Hồ sơ / người ở cùng**; dữ liệu vẫn tách để giữ lịch sử. Nếu lưu hồ sơ rồi hủy form cho thuê, hồ sơ vẫn còn để chọn lại. Thẻ phòng hiện giá thỏa thuận khi có người đang thuê; nếu trống thì hiện giá mặc định hoặc ghi rõ tiền của kỳ lịch sử khi chưa thiết lập giá.

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
| Phí/tháng | Phí lặp hằng tháng, mỗi dòng `Tên=số tiền`, ví dụ `Internet=100000`. Mặc định để trống; không nhập khoản chỉ thu một lần vào đây. |

Lượt thuê mới có bàn giao được gợi ý tiền phòng và phí tháng đầu/cuối bằng **giá tháng × số ngày ở / số ngày của tháng**, tính cả ngày đầu/cuối và làm tròn từng khoản về đồng. Nước khoán được gợi ý tương ứng số ngày, tối đa ba chữ số thập phân. Bạn kiểm tra và có thể chỉnh tiền phòng, các khoản kỳ cuối theo thỏa thuận; khác mức gợi ý cần ghi lý do. Hợp đồng cũ chưa có bàn giao vẫn đọc được theo cách tính cũ; không tự tính lại hóa đơn lịch sử. Hiện chưa có thao tác gia hạn một lượt thuê đã dùng.

## Trả phòng và đổi người trong cùng tháng

1. Trong **Thuê phòng**, chọn lượt thuê cũ → **Trả phòng / chốt kỳ cuối**.
2. Chọn ngày trả, nhập số điện/nước thực tế, kiểm tra tiền phòng/phí và đánh dấu xác nhận.
3. Bấm chốt: app lưu hóa đơn cuối, mốc OUT và ngày kết thúc trong **cùng một giao dịch**. Lỗi ở bất kỳ bước nào đều không đổi dở dang trạng thái phòng. Nếu đã có hóa đơn kết thúc đúng ngày/chỉ số/các khoản này, app dùng lại chứng từ đó, giữ nguyên các lần thu.
4. Lượt thuê cũ kết thúc, không còn được tính là người đang ở. Nợ và cọc vẫn liên kết người/lượt cũ để thu/hoàn riêng, không tự cấn trừ.
5. Khi có người mới, tạo lượt thuê mới và nhập chỉ số lúc nhận phòng. Ngày nhận cần sau ngày trả của người cũ; bản này tính theo ngày, chưa chia giờ để đổi người ngay cùng ngày.

Ví dụ A ở ngày 1–10, B vào ngày 15: cùng tháng có hai hóa đơn, mỗi hóa đơn có người và khoảng ngày riêng. A kết thúc ở số điện 110; B nhận phòng ở 115 → B chỉ bị tính từ 115, không gánh 5 kWh trong lúc phòng trống. Các mốc bàn giao được giữ để đối chiếu. Một lượt thuê có tối đa một hóa đơn đang hiệu lực mỗi tháng; app chặn khoảng ngày chồng nhau hoặc chốt lùi sau khi đã có kỳ sau.

Nếu đã chốt hóa đơn đến cuối tháng nhưng khách muốn trả sớm hơn, chưa có chứng từ điều chỉnh/hoàn phần đã thu trong bản này. App báo cần đối soát và giữ nguyên hóa đơn/thu tiền, không tự xóa hoặc giảm doanh thu. Khách dự kiến vào ở sau cũng cần chỉ số bàn giao chính xác; các mốc trước/sau phải tăng đúng thứ tự, không ghi chỉ số ước lượng để thay cho số thực tế.

## Hóa đơn cũ có sửa được không?

Phiên bản hiện tại không sửa đè hóa đơn đã chốt. Kỳ cuối chưa còn khoản thu, chưa có chứng từ mới hơn và không vướng mốc đổi nước/bàn giao OUT có thể dùng **Hủy kỳ cuối để sửa**, rồi lập lại. Bản cũ và ảnh được giữ. Hóa đơn đã gắn trả phòng không được hủy vì mốc bàn giao và lượt thuê mới phụ thuộc nó.

Nếu nhập sai lần thu, dùng **Đảo lần thu nhập nhầm**, ghi lý do, rồi ghi lần thu đúng. Chỉ làm vậy khi khoản thu thật sự bị nhập nhầm; không đảo khoản tiền thực đã nhận chỉ để mở khóa sửa hóa đơn. Đây cũng không phải thao tác hoàn tiền thật cho khách.

Hóa đơn đã thu tiền hoặc có kỳ sau cần chứng từ điều chỉnh giữ lịch sử, đối chiếu chỉ số liên quan và xử lý tiền tăng/giảm. Luồng điều chỉnh này **chưa có**. Khi cần sửa trường hợp đó, giữ bản gốc và đối chiếu trước, không sửa database thủ công.

## Trả thiếu, nợ khác và trả trước

- **Trả thiếu hóa đơn:** hóa đơn 2.500.000đ, thu 2.000.000đ → còn nợ 500.000đ. Những lần thu sau ghi vào cùng hóa đơn. Không cộng 500.000đ lần nữa vào hóa đơn mới. App đã hỗ trợ.
- **Nợ phí định kỳ:** nếu phí đã nằm trong hóa đơn, phần chưa trả đã được tính trong công nợ chung. Hiện app chưa phân bổ từng lần thu cho riêng điện, nước hoặc phòng.
- **Nợ phát sinh riêng:** ví dụ khoản bồi thường đã thỏa thuận hoặc một khoản chỉ thu một lần cần chứng từ riêng, ngày phát sinh, người/phòng chịu trách nhiệm, số tiền, hạn thu và các lần thanh toán. App chưa có sổ khoản phải thu độc lập. Ghi chú không làm tăng công nợ.
- **Trả thừa/đóng trước tháng sau:** có thể xảy ra khi khách muốn trả trước. Hóa đơn 2.500.000đ, khách đưa 3.000.000đ: 2.500.000đ thanh toán hóa đơn, 500.000đ là tiền trả trước còn giữ cho khách. Tháng sau dùng 500.000đ để cấn trừ một lần. Nếu khách chuyển đi, phải đối chiếu và xử lý số còn giữ theo thỏa thuận. Đây không phải nợ âm hay tiền cọc. App hiện chặn khoản thu vượt phần còn thiếu và chưa có sổ trả trước, cấn trừ hoặc hoàn trả trước.

Các khả năng chưa hỗ trợ ở trên cần một sổ giao dịch có lịch sử, không thể thay bằng việc đổi tổng hóa đơn hoặc ghi thêm ghi chú. Tiền cọc cũng được quản lý riêng, không tự chuyển thành khoản thanh toán hóa đơn.

## Dữ liệu nhập từ Excel

Hóa đơn lịch sử có thể đã có tiền phòng nhưng chưa có hợp đồng vì nguồn thiếu người đại diện hoặc thời hạn thuê. Không tự đoán hợp đồng hoặc giá mặc định, không gắn một người mới vào chứng từ đã chốt. App chặn lượt thuê mới trùng khoảng ngày đã tính tiền; hóa đơn cũ chưa có khoảng ngày được giữ theo cả tháng. Muốn đối chiếu liên kết lịch sử cần thông tin xác nhận và một luồng cập nhật riêng.
