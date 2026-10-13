package vn.nhatrohomu.shared;

import java.math.BigDecimal;

/** Các dữ liệu nghiệp vụ độc lập với giao diện và database. */
public final class Models {
  private Models() {}

  public record Room(
      long id,
      String name,
      String tenant,
      BigDecimal electricityStart,
      BigDecimal waterStart,
      String waterMode,
      BigDecimal fixedWater,
      boolean active,
      BigDecimal defaultRent) {
    public Room(
        long id,
        String name,
        String tenant,
        BigDecimal electricityStart,
        BigDecimal waterStart,
        String waterMode,
        BigDecimal fixedWater,
        boolean active) {
      this(id, name, tenant, electricityStart, waterStart, waterMode, fixedWater, active, null);
    }
  }

  public record Payment(
      long id,
      long billId,
      BigDecimal amount,
      String date,
      String note,
      Long advanceId,
      String sourceMonth) {
    public Payment(long id, long billId, BigDecimal amount, String date, String note) {
      this(id, billId, amount, date, note, null, "");
    }
  }

  public record Resident(
      String name, String relationship, String birthday, String identity, String phone) {}

  public record Tenant(
      long id,
      String name,
      String phone,
      String note,
      boolean active,
      String zalo,
      boolean zaloAuto,
      String identity,
      String birthday,
      String address,
      java.util.List<Resident> residents) {
    public Tenant {
      residents = java.util.List.copyOf(residents);
    }

    public Tenant(long id, String name, String phone, String note, boolean active) {
      this(id, name, phone, note, active, phone, true, "", "", "", java.util.List.of());
    }

    public String zaloContact() {
      return zaloAuto ? phone : zalo;
    }

    @Override
    public String toString() {
      return name + (phone.isBlank() ? "" : " · " + phone);
    }
  }

  public record Charge(String label, BigDecimal amount, Long serviceId) {
    public Charge(String label, BigDecimal amount) {
      this(label, amount, null);
    }
  }

  public record ServiceItem(long id, String name, BigDecimal amount) {
    @Override
    public String toString() {
      return name + " · " + vn.nhatrohomu.shared.Ui.money(amount);
    }
  }

  public record Contract(
      long id,
      long roomId,
      String roomName,
      long tenantId,
      String tenantName,
      String startDate,
      String endDate,
      String closedDate,
      BigDecimal rent,
      BigDecimal deposit,
      int dueDay,
      java.util.List<Charge> fees) {
    public Contract {
      fees = java.util.List.copyOf(fees);
    }

    public String effectiveEnd() {
      return closedDate.isBlank() ? plannedEndBound() : closedDate;
    }

    public String plannedEndBound() {
      return endDate.isBlank() ? "9999-12-31" : endDate;
    }

    public String endLabel() {
      return closedDate.isBlank() ? (endDate.isBlank() ? "Chưa xác định" : endDate) : closedDate;
    }

    public boolean covers(java.time.LocalDate date) {
      return !date.isBefore(java.time.LocalDate.parse(startDate))
          && !date.isAfter(java.time.LocalDate.parse(effectiveEnd()));
    }

    public boolean occupiedOn(java.time.LocalDate date) {
      return covers(date)
          && (closedDate.isBlank() || date.isBefore(java.time.LocalDate.parse(closedDate)));
    }

    public String status() {
      if (!closedDate.isBlank()
          && !java.time.LocalDate.now().isBefore(java.time.LocalDate.parse(closedDate)))
        return "Đã kết thúc";
      if (!closedDate.isBlank() && covers(java.time.LocalDate.now()))
        return "Kết thúc " + closedDate;
      if (java.time.LocalDate.now().isBefore(java.time.LocalDate.parse(startDate)))
        return "Sắp vào ở";
      return covers(java.time.LocalDate.now()) ? "Đang thuê" : "Đã hết hạn";
    }
  }

  public record DepositEntry(
      long id, long contractId, String kind, BigDecimal amount, String date, String note) {}

  public record Rates(BigDecimal electricity, BigDecimal water, BigDecimal defaultWater) {}

  public record Readings(BigDecimal electricity, BigDecimal water) {}

  public record Bill(
      long id,
      long roomId,
      String roomName,
      String month,
      BigDecimal electricityOld,
      BigDecimal electricityNew,
      BigDecimal waterOld,
      BigDecimal waterNew,
      String waterMode,
      BigDecimal waterUsed,
      BigDecimal electricityRate,
      BigDecimal waterRate,
      BigDecimal total,
      boolean paid,
      String note,
      Long contractId,
      String tenantName,
      BigDecimal rent,
      java.util.List<Charge> fees,
      String dueDate,
      BigDecimal importedWaterAmount,
      String periodStart,
      String periodEnd) {
    public Bill {
      fees = java.util.List.copyOf(fees);
    }

    public Bill(
        long id,
        long roomId,
        String roomName,
        String month,
        BigDecimal electricityOld,
        BigDecimal electricityNew,
        BigDecimal waterOld,
        BigDecimal waterNew,
        String waterMode,
        BigDecimal waterUsed,
        BigDecimal electricityRate,
        BigDecimal waterRate,
        BigDecimal total,
        boolean paid,
        String note,
        Long contractId,
        String tenantName,
        BigDecimal rent,
        java.util.List<Charge> fees,
        String dueDate,
        BigDecimal importedWaterAmount) {
      this(
          id,
          roomId,
          roomName,
          month,
          electricityOld,
          electricityNew,
          waterOld,
          waterNew,
          waterMode,
          waterUsed,
          electricityRate,
          waterRate,
          total,
          paid,
          note,
          contractId,
          tenantName,
          rent,
          fees,
          dueDate,
          importedWaterAmount,
          "",
          "");
    }

    public String periodLabel() {
      return periodStart.isBlank() ? month : periodStart + " → " + periodEnd;
    }

    public Bill(
        long id,
        long roomId,
        String roomName,
        String month,
        BigDecimal electricityOld,
        BigDecimal electricityNew,
        BigDecimal waterOld,
        BigDecimal waterNew,
        String waterMode,
        BigDecimal waterUsed,
        BigDecimal electricityRate,
        BigDecimal waterRate,
        BigDecimal total,
        boolean paid,
        String note,
        Long contractId,
        String tenantName,
        BigDecimal rent,
        java.util.List<Charge> fees,
        String dueDate) {
      this(
          id,
          roomId,
          roomName,
          month,
          electricityOld,
          electricityNew,
          waterOld,
          waterNew,
          waterMode,
          waterUsed,
          electricityRate,
          waterRate,
          total,
          paid,
          note,
          contractId,
          tenantName,
          rent,
          fees,
          dueDate,
          null);
    }

    /** Old utility invoices remain readable without inventing rental terms. */
    public Bill(
        long id,
        long roomId,
        String roomName,
        String month,
        BigDecimal electricityOld,
        BigDecimal electricityNew,
        BigDecimal waterOld,
        BigDecimal waterNew,
        String waterMode,
        BigDecimal waterUsed,
        BigDecimal electricityRate,
        BigDecimal waterRate,
        BigDecimal total,
        boolean paid,
        String note) {
      this(
          id,
          roomId,
          roomName,
          month,
          electricityOld,
          electricityNew,
          waterOld,
          waterNew,
          waterMode,
          waterUsed,
          electricityRate,
          waterRate,
          total,
          paid,
          note,
          null,
          "",
          BigDecimal.ZERO,
          java.util.List.of(),
          "");
    }

    public BigDecimal feeTotal() {
      return fees.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal electricityCost() {
      return electricityUsed().multiply(electricityRate);
    }

    public BigDecimal waterCost() {
      return importedWaterAmount == null ? waterUsed.multiply(waterRate) : importedWaterAmount;
    }

    public BigDecimal utilitiesCost() {
      return electricityCost().add(waterCost());
    }

    public BigDecimal electricityUsed() {
      return electricityNew.subtract(electricityOld);
    }
  }
}
