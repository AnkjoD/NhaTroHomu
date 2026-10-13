# Phòng, hợp đồng và thanh toán

## Giá phòng, giá thỏa thuận và hóa đơn

Phòng lưu tên, chỉ số đầu kỳ, cách tính nước và **giá thuê mặc định**. Khi thêm/sửa phòng, giá mặc định là bắt buộc; nhập 0 nếu miễn tiền phòng. Khi cho thuê, app dùng giá phòng và khóa ô tiền. Chỉ bật **Thỏa thuận giá riêng** nếu có thỏa thuận khác; tắt lựa chọn này sẽ lấy lại giá mặc định. Phòng từ dữ liệu cũ chưa có giá vẫn được giữ nguyên, nhưng cần vào Sửa phòng bổ sung trước khi tạo lượt thuê mới. Người thuê lưu hồ sơ con người. Lượt thuê/hợp đồng gắn người đó với phòng trong một khoảng thời gian và lưu giá đã thỏa thuận, cọc, hạn thu, phí. Sửa giá phòng không tự đổi giá thỏa thuận hoặc hóa đơn đã chốt. Giá trên hóa đơn lịch sử không bị tự đoán thành giá mặc định mới.

Luồng thông thường: thêm phòng và giá mặc định → **Cho thuê phòng** → chọn/thêm người thuê trong form → kiểm tra giá, thời hạn và chỉ số nhận phòng → lưu → lập hóa đơn từng kỳ → ghi tiền thực nhận. Mục **Thuê phòng** gom hai tab **Người đang thuê / lịch sử thuê** và **Hồ sơ / người ở cùng**; dữ liệu vẫn tách để giữ lịch sử. Nếu lưu hồ sơ rồi hủy form cho thuê, hồ sơ vẫn còn để chọn lại. Thẻ phòng hiện giá thỏa thuận khi có người đang thuê; nếu trống thì hiện giá mặc định hoặc ghi rõ tiền của kỳ lịch sử khi chưa thiết lập giá.

Để lập hóa đơn: vào **Hóa đơn → Lập hóa đơn → chọn phòng**, hoặc bấm **Lập hóa đơn** ngay trên thẻ phòng. Nhập chỉ số mới của điện/nước (không nhập số điện đã dùng), kiểm tra kỳ, tiền phòng, phí, tổng tiền và xác nhận trước khi lưu. Số cũ lấy từ kỳ trước. Có thể chọn ảnh để OCR rồi sửa số; lưu hóa đơn chưa có nghĩa đã thu tiền, cần ghi lần thu thực nhận riêng.

Màn Hóa đơn mặc định xem tháng mới nhất có dữ liệu. Dùng Tháng hóa đơn để chọn kỳ cũ hoặc Tất cả tháng. Tổng tiền phía dưới tính toàn phạm vi đã chọn, kể cả có nhiều trang. Xuất CSV tháng này xuất tất cả hóa đơn của tháng đang chọn; ở chế độ Tất cả tháng thì xuất toàn bộ. Các nút Xem hóa đơn/Thu tiền vẫn thao tác đúng hóa đơn đang chọn trong bảng hoặc thẻ.

Hóa đơn là số tiền đã chốt của một tháng: tiền phòng + tiền điện + tiền nước + phí. Công nợ là số tiền hóa đơn còn thiếu sau các lần thu hợp lệ. Hết hợp đồng không làm biến mất hóa đơn hoặc nợ.

## Các trường hợp đồng

| Trường | Ý nghĩa |
|---|---|
| Phòng | Phòng được cho thuê. |
| Người thuê đại diện | Người đứng tên thuê. Hồ sơ người ở cùng nằm ở Người thuê. |
| Ngày vào ở | Ngày bắt đầu thời hạn thuê. |
| Thời gian ở | Mặc định chưa xác định ngày chuyển đi. Nếu đã thỏa thuận thời hạn, bỏ chọn rồi chọn ngày kết thúc. Khi chuyển đi, chốt ngày thực tế bằng Trả phòng. |
| Tiền phòng/tháng | Giá thuê cơ bản, chưa gồm điện nước và phí. |
| Tiền cọc thỏa thuận | Mặc định không thu cọc. Bật Có thỏa thuận tiền cọc nếu có; số tiền thỏa thuận chưa phải tiền thực nhận. |
| Ngày hẹn đóng tiền hằng tháng | Ngày hóa đơn đến hạn, chọn 1–28 để có ngày này trong mọi tháng. Chọn 5 thì thường đến hạn ngày 5; tháng đầu không đặt hạn trước ngày bắt đầu thuê. |
| Khoản thu thêm hằng tháng | Bấm Thêm khoản phí rồi nhập Tên khoản phí và Số tiền, ví dụ Internet và 100000. Dùng lại chọn khoản phí từ thỏa thuận đã lưu. Để trống khi không thu phí. |

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

## Gia đình ngay trong lượt thuê

Danh sách lượt thuê có trường Người ở cùng / gia đình. Chọn Hồ sơ / gia đình để mở đúng người đại diện của lượt thuê và vào thẳng tab gia đình; nút này cũng có trong menu chuột phải. Xem chi tiết lượt thuê có danh sách người ở cùng phía dưới và nút chỉnh sửa. Đây là hồ sơ hiện tại của người đại diện, không phải bản chụp danh sách gia đình tại từng thời điểm thuê. Hồ sơ đã lưu trữ được xem lại, không tự kích hoạt lại khi mở liên kết.

## Form cho thuê dễ nhập (0.10.0)

Người đứng tên thuê là người đại diện liên hệ và thanh toán. Nút Gia đình / người ở cùng mở hồ sơ của đúng người đang chọn, vào thẳng tab gia đình; không tạo thành hợp đồng độc lập cho từng thành viên.

Khi chưa biết ngày chuyển đi, không điền ngày giả: app lưu ngày kết thúc dự kiến trống, vẫn tính hóa đơn và giữ trạng thái đang thuê đến khi chốt trả phòng. Ngày hẹn đóng tiền dùng để ghi hạn hóa đơn và phân loại nợ quá hạn; không tự thu tiền, tính phạt hoặc gửi tin nhắn.

Khoản phí đã chọn được lưu riêng trong thỏa thuận và hóa đơn, không tự thay đổi giá cũ. Danh sách dùng lại lấy từ phí của các thỏa thuận đã lưu; đây chưa phải danh mục dịch vụ độc lập. Form trả phòng cũng dùng ô tên và tiền riêng. Hướng dẫn ngắn nằm ngay dưới từng ô; bỏ nút Giải thích các trường.

Tham khảo cách tổ chức thông tin: [Hợp đồng Resident](https://docs.resident.vn/khach-hang/hop-dong), [Dịch vụ Resident](https://docs.resident.vn/quan-ly-van-hanh/dich-vu). Hỗ trợ chưa xác định ngày đi là lựa chọn riêng của NhaTroHomu cho luồng thuê dài hạn; không khẳng định các sản phẩm tham khảo có cùng lựa chọn.

Sổ nâng lên schema 8 với sao lưu trước khi nâng cấp; giá, hợp đồng có thời hạn, hóa đơn và thu tiền cũ được giữ nguyên. Bản 0.9.x không đọc sổ schema 8, cần dùng ứng dụng 0.10.0 trở lên.
