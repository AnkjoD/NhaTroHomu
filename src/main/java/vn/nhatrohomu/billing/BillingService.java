package vn.nhatrohomu.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** Hàm tính tiền không đụng tới Swing hay SQL, nên có thể kiểm thử riêng. */
public final class BillingService {
  public static BigDecimal number(String text, String field) {
    try {
      if (text == null || text.length() > 40) throw new NumberFormatException();
      // Chấp nhận dấu phẩy thập phân; không chấp nhận dấu phân cách hàng nghìn.
      BigDecimal value = new BigDecimal(text.trim().replace(',', '.'));
      if (value.signum() < 0
          || value.scale() > 3
          || value.compareTo(new BigDecimal("1000000000")) > 0) throw new NumberFormatException();
      return value.stripTrailingZeros();
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          field
              + ": nhập số không âm, tối đa 3 chữ số thập phân; không dùng dấu phân cách hàng"
              + " nghìn.");
    }
  }

  public static Bill calculate(
      Room room,
      String month,
      Readings old,
      BigDecimal electricityNew,
      BigDecimal waterNew,
      BigDecimal fixedWater,
      Rates rates,
      String note) {
    DataRules.room(room);
    DataRules.rates(rates);
    DataRules.quantity(old.electricity(), "Điện cũ");
    DataRules.quantity(old.water(), "Nước cũ");
    DataRules.quantity(electricityNew, "Điện mới");
    DataRules.quantity(waterNew, "Nước mới");
    DataRules.quantity(fixedWater, "Nước khoán");
    DataRules.period(month); // Dùng YYYY-MM để so sánh và lưu kỳ thống nhất.
    BigDecimal electricityUsed = electricityNew.subtract(old.electricity());
    if (electricityUsed.signum() < 0)
      throw new IllegalArgumentException(
          "Chỉ số điện mới phải lớn hơn hoặc bằng số cũ. Kiểm tra ảnh hoặc số đầu kỳ.");
    BigDecimal waterUsed;
    if (room.waterMode().equals("METER")) {
      waterUsed = waterNew.subtract(old.water());
      if (waterUsed.signum() < 0)
        throw new IllegalArgumentException("Chỉ số nước mới phải lớn hơn hoặc bằng số cũ.");
    } else {
      waterUsed = fixedWater;
      waterNew = old.water(); // Nước khoán là lượng dùng, không làm tăng chỉ số đồng hồ.
    }
    if (waterUsed.signum() < 0 || rates.electricity().signum() < 0 || rates.water().signum() < 0)
      throw new IllegalArgumentException("Lượng dùng và đơn giá không được âm.");
    BigDecimal total =
        electricityUsed
            .multiply(rates.electricity())
            .add(waterUsed.multiply(rates.water()))
            .setScale(0, RoundingMode.HALF_UP);
    return new Bill(
        0,
        room.id(),
        room.name(),
        month,
        old.electricity(),
        electricityNew,
        old.water(),
        waterNew,
        room.waterMode(),
        waterUsed,
        rates.electricity(),
        rates.water(),
        total,
        false,
        note == null ? "" : note.trim());
  }
}
