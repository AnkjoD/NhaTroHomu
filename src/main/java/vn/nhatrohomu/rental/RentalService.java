package vn.nhatrohomu.rental;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** Pure functions: no database or Swing dependencies. */
public final class RentalService {
  private RentalService() {}

  public static BigDecimal money(String text, String field) {
    BigDecimal result = BillingService.number(text, field);
    requireMoney(result, field);
    return result;
  }

  public static void requireMoney(BigDecimal value, String field) {
    DataRules.money(value, field);
  }

  public static LocalDate date(String value) {
    return DataRules.date(value);
  }

  public static Bill withRental(Bill utility, Contract contract, BigDecimal rent) {
    if (contract == null) return utility;
    requireMoney(rent, "Tiền phòng kỳ này");
    YearMonth period = YearMonth.parse(utility.month());
    if (contract.roomId() != utility.roomId()
        || date(contract.startDate()).isAfter(period.atEndOfMonth())
        || date(contract.effectiveEnd()).isBefore(period.atDay(1)))
      throw new IllegalArgumentException("Hợp đồng không thuộc phòng/kỳ này.");
    BigDecimal fee =
        contract.fees().stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    LocalDate dueDay = period.atDay(Math.min(contract.dueDay(), period.lengthOfMonth()));
    if (dueDay.isBefore(date(contract.startDate()))) dueDay = date(contract.startDate());
    String due = dueDay.toString();
    return new Bill(
        utility.id(),
        utility.roomId(),
        utility.roomName(),
        utility.month(),
        utility.electricityOld(),
        utility.electricityNew(),
        utility.waterOld(),
        utility.waterNew(),
        utility.waterMode(),
        utility.waterUsed(),
        utility.electricityRate(),
        utility.waterRate(),
        utility
            .electricityUsed()
            .multiply(utility.electricityRate())
            .add(utility.waterUsed().multiply(utility.waterRate()))
            .add(rent)
            .add(fee)
            .setScale(0, RoundingMode.HALF_UP),
        utility.paid(),
        utility.note(),
        contract.id(),
        contract.tenantName(),
        rent,
        contract.fees(),
        due);
  }

  public static BigDecimal prorated(BigDecimal monthly, LocalDate first, LocalDate last) {
    if (last.isBefore(first) || !YearMonth.from(first).equals(YearMonth.from(last)))
      throw new IllegalArgumentException("Khoảng ngày cần nằm trong cùng tháng.");
    return monthly
        .multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1))
        .divide(BigDecimal.valueOf(YearMonth.from(first).lengthOfMonth()), 0, RoundingMode.HALF_UP);
  }

  public static Bill segment(
      Bill utility,
      Contract contract,
      BigDecimal rent,
      java.util.List<Charge> fees,
      LocalDate first,
      LocalDate last) {
    Bill base = withRental(utility, contract, rent);
    BigDecimal total =
        utility
            .utilitiesCost()
            .add(rent)
            .add(fees.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    return new Bill(
        base.id(),
        base.roomId(),
        base.roomName(),
        base.month(),
        base.electricityOld(),
        base.electricityNew(),
        base.waterOld(),
        base.waterNew(),
        base.waterMode(),
        base.waterUsed(),
        base.electricityRate(),
        base.waterRate(),
        total.setScale(0, RoundingMode.HALF_UP),
        base.paid(),
        base.note(),
        base.contractId(),
        base.tenantName(),
        rent,
        fees,
        base.dueDate(),
        null,
        first.toString(),
        last.toString());
  }
}
