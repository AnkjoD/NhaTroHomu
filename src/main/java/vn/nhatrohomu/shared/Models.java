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
      boolean active) {}

  public record Payment(long id, long billId, BigDecimal amount, String date, String note) {}

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

  public record Charge(String label, BigDecimal amount) {}

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
      return closedDate.isBlank() ? endDate : closedDate;
    }

    public boolean covers(java.time.LocalDate date) {
      return !date.isBefore(java.time.LocalDate.parse(startDate))
          && !date.isAfter(java.time.LocalDate.parse(effectiveEnd()));
    }

    public String status() {
      if (!closedDate.isBlank()
          && java.time.LocalDate.now().isAfter(java.time.LocalDate.parse(closedDate)))
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
      String dueDate) {
    public Bill {
      fees = java.util.List.copyOf(fees);
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
      return waterUsed.multiply(waterRate);
    }

    public BigDecimal utilitiesCost() {
      return electricityCost().add(waterCost());
    }

    public BigDecimal electricityUsed() {
      return electricityNew.subtract(electricityOld);
    }
  }
}
