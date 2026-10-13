package vn.nhatrohomu.shared;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.shared.Models.*;

/** Rules applied at the storage boundary as well as the form. */
public final class DataRules {
  private DataRules() {}

  public static void money(BigDecimal value, String field) {
    if (value == null
        || value.signum() < 0
        || value.stripTrailingZeros().scale() > 0
        || value.compareTo(new BigDecimal("1000000000")) > 0)
      throw new IllegalArgumentException(field + ": nhập số nguyên đồng không âm, tối đa 1 tỷ.");
  }

  public static LocalDate date(String value) {
    try {
      LocalDate day = LocalDate.parse(value);
      if (!day.toString().equals(value) || day.getYear() < 1900 || day.getYear() > 9999)
        throw new IllegalArgumentException();
      return day;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Ngày cần đúng YYYY-MM-DD và hợp lệ.");
    }
  }

  public static BigDecimal quantity(BigDecimal value, String field) {
    if (value == null
        || value.signum() < 0
        || value.stripTrailingZeros().scale() > 3
        || value.compareTo(new BigDecimal("1000000000")) > 0)
      throw new IllegalArgumentException(
          field + ": số không âm, tối đa 1 tỷ và 3 chữ số thập phân.");
    return value;
  }

  public static String text(String value, String field, int max, boolean required) {
    if (value == null
        || value.length() > max
        || (required && value.isBlank())
        || value
            .chars()
            .anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t'))
      throw new IllegalArgumentException(
          field + ": giá trị trống, quá dài hoặc chứa ký tự không hợp lệ.");
    return value;
  }

  public static void name(String value, String field, int max) {
    text(value, field, max, true);
    if (value.contains("<")
        || value.contains(">")
        || value.contains("\n")
        || value.contains("\r")
        || value.contains("\t"))
      throw new IllegalArgumentException(field + ": không dùng thẻ HTML hoặc ký tự xuống dòng.");
  }

  public static YearMonth period(String value) {
    try {
      YearMonth month = YearMonth.parse(value);
      if (!month.toString().equals(value) || month.getYear() < 1900 || month.getYear() > 9999)
        throw new IllegalArgumentException();
      return month;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Kỳ phải đúng YYYY-MM, năm 1900–9999.");
    }
  }

  public static void actualDate(String value) {
    if (date(value).isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Ngày thu/chi thực tế không được ở tương lai.");
  }

  public static void mode(String value) {
    if (!Set.of("METER", "FIXED").contains(value))
      throw new IllegalArgumentException("Cách tính nước không hợp lệ.");
  }

  public static void room(Room room) {
    if (room.id() < 0) throw new IllegalArgumentException("Mã phòng không hợp lệ.");
    name(room.name(), "Tên phòng", 80);
    text(room.tenant(), "Tên cũ", 120, false);
    quantity(room.electricityStart(), "Điện đầu kỳ");
    quantity(room.waterStart(), "Nước đầu kỳ");
    mode(room.waterMode());
    if (room.defaultRent() != null) money(room.defaultRent(), "Giá thuê mặc định");
    if (room.fixedWater() != null) quantity(room.fixedWater(), "Nước khoán");
    if (room.waterMode().equals("METER") && room.fixedWater() != null)
      throw new IllegalArgumentException("Phòng dùng đồng hồ không có mức khoán riêng.");
  }

  public static void rates(Rates rates) {
    quantity(rates.electricity(), "Giá điện");
    quantity(rates.water(), "Giá nước");
    quantity(rates.defaultWater(), "Nước mặc định");
  }

  public static void bill(Bill b) {
    bill(b, false);
  }

  public static void bill(Bill b, boolean historicalImport) {
    if (b.importedWaterAmount() != null) {
      if (!historicalImport)
        throw new IllegalArgumentException(
            "Chỉ nhập lịch sử có đối soát được giữ thành tiền nước gốc.");
      money(b.importedWaterAmount(), "Tiền nước gốc");
    }
    period(b.month());
    if (!b.periodStart().isBlank() || !b.periodEnd().isBlank()) {
      java.time.LocalDate first = date(b.periodStart()), last = date(b.periodEnd());
      if (b.contractId() == null
          || last.isBefore(first)
          || !java.time.YearMonth.from(first).equals(period(b.month()))
          || !java.time.YearMonth.from(last).equals(period(b.month())))
        throw new IllegalArgumentException("Khoảng ngày hóa đơn không khớp kỳ/lần thuê.");
    }
    name(b.roomName(), "Tên phòng", 80);
    text(b.note(), "Ghi chú", 2000, false);
    quantity(b.electricityOld(), "Điện cũ");
    quantity(b.electricityNew(), "Điện mới");
    quantity(b.waterOld(), "Nước cũ");
    quantity(b.waterNew(), "Nước mới");
    quantity(b.waterUsed(), "Lượng nước");
    quantity(b.electricityRate(), "Giá điện");
    quantity(b.waterRate(), "Giá nước");
    mode(b.waterMode());
    if (b.electricityUsed().signum() < 0
        || (b.waterMode().equals("METER")
            ? b.waterNew().subtract(b.waterOld()).compareTo(b.waterUsed()) != 0
            : b.waterNew().compareTo(b.waterOld()) != 0))
      throw new IllegalArgumentException("Chỉ số và lượng dùng không khớp.");
    money(b.rent(), "Tiền phòng");
    if (b.fees().size() > 20) throw new IllegalArgumentException("Tối đa 20 khoản phí.");
    Set<String> labels = new HashSet<>();
    for (Charge fee : b.fees()) {
      name(fee.label(), "Tên phí", 100);
      if (fee.serviceId() != null && fee.serviceId() <= 0)
        throw new IllegalArgumentException("Mã khoản phí không hợp lệ.");
      money(fee.amount(), "Phí");
      if (!labels.add(fee.label().trim().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Tên phí trùng.");
    }
    text(b.tenantName(), "Người thuê", 120, false);
    if (!b.dueDate().isBlank()) date(b.dueDate());
    BigDecimal expected =
        b.electricityUsed()
            .multiply(b.electricityRate())
            .add(b.waterCost())
            .add(b.rent())
            .add(b.feeTotal())
            .setScale(0, java.math.RoundingMode.HALF_UP);
    if (b.total() == null
        || b.total().compareTo(expected) != 0
        || b.total().signum() < 0
        || b.total().compareTo(new BigDecimal("1000000000000")) > 0)
      throw new IllegalArgumentException("Tổng hóa đơn không khớp các khoản hoặc vượt giới hạn.");
    if (b.contractId() == null
        && ((!historicalImport && b.rent().signum() != 0)
            || !b.fees().isEmpty()
            || !b.tenantName().isBlank()
            || !b.dueDate().isBlank()))
      throw new IllegalArgumentException(
          "Hóa đơn không có hợp đồng chứa thông tin thuê không hợp lệ.");
  }
}
