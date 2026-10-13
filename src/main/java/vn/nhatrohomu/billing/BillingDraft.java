package vn.nhatrohomu.billing;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Models.*;

/** One room's unissued billing interval, with the same pricing rules as individual billing. */
public record BillingDraft(
    Room room,
    YearMonth month,
    Rates rates,
    Readings opening,
    Contract contract,
    boolean segmented,
    LocalDate first,
    LocalDate last,
    BigDecimal rent,
    BigDecimal water,
    List<Charge> fees) {
  public static BillingDraft prepare(Repository repo, Room room, YearMonth month) throws Exception {
    List<Bill> issued = repo.roomBills(room.id(), month.toString(), month.toString());
    List<Contract> covering =
        repo.rentals().contracts().stream()
            .filter(
                k ->
                    k.roomId() == room.id()
                        && !RentalService.date(k.startDate()).isAfter(month.atEndOfMonth())
                        && !RentalService.date(k.effectiveEnd()).isBefore(month.atDay(1)))
            .toList();
    List<Contract> pending =
        covering.stream()
            .filter(
                k ->
                    issued.stream()
                        .noneMatch(b -> java.util.Objects.equals(b.contractId(), k.id())))
            .toList();
    if (pending.size() > 1)
      throw new IllegalArgumentException(
          "Có nhiều lượt thuê chưa chốt trong tháng. Dùng lập từng phòng để chọn người thuê.");
    Contract k = pending.isEmpty() ? null : pending.getFirst();
    if (k == null && (!issued.isEmpty() || !covering.isEmpty()))
      throw new IllegalArgumentException("Tháng này đã lập hóa đơn.");
    Rates rates = repo.rates();
    Readings old = k == null ? repo.previous(room) : repo.rentals().opening(k.id(), month);
    LocalDate first =
        k != null && RentalService.date(k.startDate()).isAfter(month.atDay(1))
            ? RentalService.date(k.startDate())
            : month.atDay(1);
    LocalDate last =
        k != null && RentalService.date(k.effectiveEnd()).isBefore(month.atEndOfMonth())
            ? RentalService.date(k.effectiveEnd())
            : month.atEndOfMonth();
    boolean split = k != null && (repo.rentals().hasHandover(k.id()) || !k.closedDate().isBlank());
    BigDecimal quota = room.fixedWater() == null ? rates.defaultWater() : room.fixedWater();
    if (split && room.waterMode().equals("FIXED"))
      quota =
          quota
              .multiply(
                  BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1))
              .divide(BigDecimal.valueOf(month.lengthOfMonth()), 3, java.math.RoundingMode.HALF_UP);
    Contract selected = k;
    List<Charge> fees =
        k == null
            ? List.of()
            : k.fees().stream()
                .map(
                    f ->
                        split
                            ? new Charge(
                                f.label(),
                                RentalService.prorated(f.amount(), first, last),
                                f.serviceId())
                            : f)
                .toList();
    BigDecimal rent =
        k == null
            ? BigDecimal.ZERO
            : split ? RentalService.prorated(selected.rent(), first, last) : k.rent();
    return new BillingDraft(room, month, rates, old, k, split, first, last, rent, quota, fees);
  }

  public Bill calculate(String electricity, String waterText, String rentText, String note) {
    BigDecimal w =
        BillingService.number(
            waterText, room.waterMode().equals("METER") ? "Nước mới" : "Nước khoán");
    Bill utility =
        BillingService.calculate(
            room,
            month.toString(),
            opening,
            BillingService.number(electricity, "Điện mới"),
            room.waterMode().equals("METER") ? w : opening.water(),
            room.waterMode().equals("FIXED") ? w : BigDecimal.ZERO,
            rates,
            note);
    if (contract == null) return utility;
    BigDecimal amount = RentalService.money(rentText, "Tiền phòng");
    if (amount.compareTo(rent) != 0 && note.isBlank())
      throw new IllegalArgumentException("Tiền phòng đã thay đổi: ghi lý do vào Ghi chú.");
    return segmented
        ? RentalService.segment(utility, contract, amount, fees, first, last)
        : RentalService.withRental(utility, contract, amount);
  }
}
