package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** Day intervals and handovers are recorded without rewriting settled invoices. */
final class TurnoverStore {
  private final Path database;

  TurnoverStore(Path database) {
    this.database = database;
  }

  static Contract contract(Connection c, long id) throws SQLException {
    return RentalStore.contracts(c).stream()
        .filter(k -> k.id() == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Lần thuê không còn hiệu lực."));
  }

  static void event(Connection c, Contract k, String kind, String date, Readings readings)
      throws SQLException {
    DataRules.quantity(readings.electricity(), "Điện bàn giao");
    DataRules.quantity(readings.water(), "Nước bàn giao");
    validateBoundary(c, k, date, readings);
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO"
                + " handover_events(contract_id,kind,event_date,e_reading,w_reading,water_mode,created_at)"
                + " SELECT ?,?,?,?,?,water_mode,? FROM rooms WHERE id=?")) {
      p.setLong(1, k.id());
      p.setString(2, kind);
      p.setString(3, date);
      p.setString(4, readings.electricity().toPlainString());
      p.setString(5, readings.water().toPlainString());
      p.setString(6, Instant.now().toString());
      p.setLong(7, k.roomId());
      p.executeUpdate();
    }
    AuditLog.record(
        c,
        "HANDOVER_" + kind,
        "contract",
        k.id(),
        date + "; điện " + readings.electricity() + "; nước " + readings.water());
  }

  private static void validateBoundary(Connection c, Contract k, String date, Readings reading)
      throws SQLException {
    String mode;
    try (PreparedStatement p = c.prepareStatement("SELECT water_mode FROM rooms WHERE id=?")) {
      p.setLong(1, k.roomId());
      try (ResultSet r = p.executeQuery()) {
        r.next();
        mode = r.getString(1);
      }
    }
    for (boolean previous : new boolean[] {true, false}) {
      String comparison = previous ? "<=" : ">", direction = previous ? "DESC" : "ASC";
      try (PreparedStatement p =
          c.prepareStatement(
              "SELECT h.e_reading,h.w_reading,h.water_mode,h.event_date FROM handover_events h JOIN"
                  + " contracts other ON other.id=h.contract_id WHERE other.room_id=? AND"
                  + " other.cancelled_at IS NULL AND h.event_date"
                  + comparison
                  + "? ORDER BY h.event_date "
                  + direction
                  + ",h.id "
                  + direction
                  + " LIMIT 1")) {
        p.setLong(1, k.roomId());
        p.setString(2, date);
        try (ResultSet r = p.executeQuery()) {
          if (!r.next()) continue;
          int electric = reading.electricity().compareTo(new BigDecimal(r.getString(1))),
              water = reading.water().compareTo(new BigDecimal(r.getString(2)));
          boolean reset = false;
          String first = previous ? r.getString(4) : date, last = previous ? date : r.getString(4);
          try (PreparedStatement transition =
              c.prepareStatement(
                  "SELECT 1 FROM water_transitions WHERE room_id=? AND effective_month>? AND"
                      + " effective_month<=? LIMIT 1")) {
            transition.setLong(1, k.roomId());
            transition.setString(2, first.substring(0, 7));
            transition.setString(3, last.substring(0, 7));
            try (ResultSet changed = transition.executeQuery()) {
              reset = changed.next();
            }
          }
          if ((previous ? electric < 0 : electric > 0)
              || (!reset
                  && mode.equals("METER")
                  && r.getString(3).equals("METER")
                  && (previous ? water < 0 : water > 0)))
            throw new IllegalArgumentException(
                "Chỉ số bàn giao không khớp thứ tự thời gian. Kiểm tra cả mốc vào/ra trước và sau"
                    + " (kể cả lượt thuê dự kiến).");
        }
      }
    }
  }

  static Readings opening(Connection c, Contract k, YearMonth month) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT e_new,w_new,month FROM bills WHERE contract_id=? AND month<? AND voided_at IS"
                + " NULL ORDER BY month DESC,id DESC LIMIT 1")) {
      p.setLong(1, k.id());
      p.setString(2, month.toString());
      try (ResultSet r = p.executeQuery()) {
        if (r.next())
          return transitioned(
              c,
              k.roomId(),
              new Readings(new BigDecimal(r.getString(1)), new BigDecimal(r.getString(2))),
              r.getString(3),
              month);
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT e_reading,w_reading FROM handover_events WHERE contract_id=? AND kind='IN'")) {
      p.setLong(1, k.id());
      try (ResultSet r = p.executeQuery()) {
        if (r.next())
          return transitioned(
              c,
              k.roomId(),
              new Readings(new BigDecimal(r.getString(1)), new BigDecimal(r.getString(2))),
              k.startDate().substring(0, 7),
              month);
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT e_new,w_new,month FROM bills WHERE room_id=? AND month<? AND voided_at IS NULL"
                + " ORDER BY month DESC,id DESC LIMIT 1")) {
      p.setLong(1, k.roomId());
      p.setString(2, month.toString());
      try (ResultSet r = p.executeQuery()) {
        if (r.next())
          return transitioned(
              c,
              k.roomId(),
              new Readings(new BigDecimal(r.getString(1)), new BigDecimal(r.getString(2))),
              r.getString(3),
              month);
      }
    }
    try (PreparedStatement p = c.prepareStatement("SELECT e_start,w_start FROM rooms WHERE id=?")) {
      p.setLong(1, k.roomId());
      try (ResultSet r = p.executeQuery()) {
        r.next();
        return transitioned(
            c,
            k.roomId(),
            new Readings(new BigDecimal(r.getString(1)), new BigDecimal(r.getString(2))),
            "1900-01",
            month);
      }
    }
  }

  private static Readings transitioned(
      Connection c, long room, Readings reading, String after, YearMonth through)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT opening_reading FROM water_transitions WHERE room_id=? AND effective_month>?"
                + " AND effective_month<=? ORDER BY effective_month DESC LIMIT 1")) {
      p.setLong(1, room);
      p.setString(2, after);
      p.setString(3, through.toString());
      try (ResultSet r = p.executeQuery()) {
        return r.next()
            ? new Readings(reading.electricity(), new BigDecimal(r.getString(1)))
            : reading;
      }
    }
  }

  void issue(Bill bill, byte[] ep, byte[] wp) throws SQLException {
    transact(bill, ep, wp, false);
  }

  void checkout(Bill bill) throws SQLException {
    transact(bill, null, null, true);
  }

  private void transact(Bill b, byte[] ep, byte[] wp, boolean checkout) throws SQLException {
    DataRules.bill(b);
    if (b.contractId() == null || b.periodStart().isBlank())
      throw new IllegalArgumentException("Chọn lần thuê và khoảng ngày trước khi chốt.");
    if ((ep != null && ep.length > 12_000_000) || (wp != null && wp.length > 12_000_000))
      throw new IllegalArgumentException("Ảnh vượt 12 MB.");
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
          lock.setLong(1, b.roomId());
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Phòng không còn được quản lý.");
        }
        Contract k = contract(c, b.contractId());
        boolean reuse = false;
        if (k.roomId() != b.roomId() || !k.tenantName().equals(b.tenantName()))
          throw new IllegalArgumentException("Hóa đơn không thuộc người/phòng trả.");
        if (checkout) {
          LocalDate end = DataRules.date(b.periodEnd());
          if (!k.closedDate().isBlank()
              || end.isBefore(DataRules.date(k.startDate()))
              || end.isAfter(DataRules.date(k.endDate())))
            throw new IllegalArgumentException(
                "Ngày trả phòng ngoài thời hạn hoặc lần thuê đã kết thúc.");
          try (PreparedStatement p =
              c.prepareStatement(
                  "SELECT * FROM bills WHERE contract_id=? AND voided_at IS NULL AND month>=? ORDER"
                      + " BY month DESC,id DESC LIMIT 1")) {
            p.setLong(1, k.id());
            p.setString(2, b.month());
            try (ResultSet r = p.executeQuery()) {
              if (r.next()) {
                String oldEnd =
                    r.getString("period_end").isBlank()
                        ? DataRules.period(r.getString("month")).atEndOfMonth().toString()
                        : r.getString("period_end");
                String oldFirst =
                    r.getString("period_start").isBlank()
                        ? (DataRules.date(k.startDate())
                                .isAfter(DataRules.period(r.getString("month")).atDay(1))
                            ? k.startDate()
                            : r.getString("month") + "-01")
                        : r.getString("period_start");
                if (!r.getString("month").equals(b.month())
                    || !oldEnd.equals(b.periodEnd())
                    || !oldFirst.equals(b.periodStart())
                    || new BigDecimal(r.getString("e_new")).compareTo(b.electricityNew()) != 0
                    || new BigDecimal(r.getString("w_new")).compareTo(b.waterNew()) != 0
                    || new BigDecimal(r.getString("total")).compareTo(b.total()) != 0
                    || new BigDecimal(r.getString("rent")).compareTo(b.rent()) != 0
                    || !RentalStore.charges(c, "bill_charges", "bill_id", r.getLong("id"))
                        .equals(b.fees()))
                  throw new IllegalArgumentException(
                      "Đã có hóa đơn trong/sau khoảng trả phòng. Ngày, chỉ số và các khoản phải"
                          + " khớp; cần đối soát khi trả sớm, không ghi đè chứng từ.");
                reuse = true;
              }
            }
          }
          try (PreparedStatement p =
              c.prepareStatement("UPDATE contracts SET closed_date=? WHERE id=?")) {
            p.setString(1, b.periodEnd());
            p.setLong(2, k.id());
            p.executeUpdate();
          }
          k = contract(c, k.id());
        }
        if (!reuse) persist(c, k, b, ep, wp);
        if (checkout) {
          event(c, k, "OUT", b.periodEnd(), new Readings(b.electricityNew(), b.waterNew()));
          AuditLog.record(
              c,
              "CHECKOUT",
              "contract",
              k.id(),
              "Trả phòng và chốt hóa đơn cuối cùng: "
                  + b.periodEnd()
                  + "; không chuyển nợ/cọc sang người mới.");
        }
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  private static void persist(Connection c, Contract k, Bill b, byte[] ep, byte[] wp)
      throws SQLException {
    YearMonth month = DataRules.period(b.month());
    LocalDate first =
        DataRules.date(k.startDate()).isAfter(month.atDay(1))
            ? DataRules.date(k.startDate())
            : month.atDay(1);
    LocalDate last =
        DataRules.date(k.effectiveEnd()).isBefore(month.atEndOfMonth())
            ? DataRules.date(k.effectiveEnd())
            : month.atEndOfMonth();
    if (k.roomId() != b.roomId()
        || !b.tenantName().equals(k.tenantName())
        || !b.periodStart().equals(first.toString())
        || !b.periodEnd().equals(last.toString()))
      throw new IllegalArgumentException("Ngày tính tiền/người thuê không khớp lần thuê.");
    Bill expected = RentalService.segment(b, k, b.rent(), b.fees(), first, last);
    if (!b.dueDate().equals(expected.dueDate()))
      throw new IllegalArgumentException("Hạn thu không khớp.");
    BigDecimal normalRent = RentalService.prorated(k.rent(), first, last);
    List<Charge> normalFees =
        k.fees().stream()
            .map(f -> new Charge(f.label(), RentalService.prorated(f.amount(), first, last)))
            .toList();
    if ((normalRent.compareTo(b.rent()) != 0 || !normalFees.equals(b.fees())) && b.note().isBlank())
      throw new IllegalArgumentException("Ghi lý do khi điều chỉnh tiền phòng/phí so với số ngày.");
    try (PreparedStatement p = c.prepareStatement("SELECT name,water_mode FROM rooms WHERE id=?")) {
      p.setLong(1, k.roomId());
      try (ResultSet r = p.executeQuery()) {
        r.next();
        if (!b.roomName().equals(r.getString(1)) || !b.waterMode().equals(r.getString(2)))
          throw new IllegalArgumentException("Thông tin phòng đã đổi.");
      }
    }
    for (YearMonth required = YearMonth.from(DataRules.date(k.startDate()));
        required.isBefore(month);
        required = required.plusMonths(1)) {
      try (PreparedStatement p =
          c.prepareStatement(
              "SELECT 1 FROM bills WHERE contract_id=? AND month=? AND voided_at IS NULL")) {
        p.setLong(1, k.id());
        p.setString(2, required.toString());
        try (ResultSet r = p.executeQuery()) {
          if (!r.next())
            throw new IllegalArgumentException(
                "Còn kỳ thuê chưa chốt " + required + ". Chốt theo thứ tự của người này.");
        }
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT 1 FROM bills WHERE room_id=? AND voided_at IS NULL AND (month>? OR (month=? AND"
                + " (period_start='' OR NOT(period_end<? OR period_start>?)))) LIMIT 1")) {
      p.setLong(1, k.roomId());
      p.setString(2, b.month());
      p.setString(3, b.month());
      p.setString(4, b.periodStart());
      p.setString(5, b.periodEnd());
      try (ResultSet r = p.executeQuery()) {
        if (r.next())
          throw new IllegalArgumentException(
              "Khoảng ngày đã có hóa đơn hoặc đã chốt kỳ sau; không tính trùng.");
      }
    }
    Readings opening = opening(c, k, month);
    if (opening.electricity().compareTo(b.electricityOld()) != 0
        || opening.water().compareTo(b.waterOld()) != 0)
      throw new IllegalArgumentException("Chỉ số đầu kỳ/bàn giao đã đổi. Mở lại form.");
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO"
                + " bills(room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,note,e_photo,w_photo,contract_id,tenant_name,rent,due_date,created_at,period_start,period_end)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
      p.setLong(1, b.roomId());
      p.setString(2, b.roomName());
      p.setString(3, b.month());
      BigDecimal[] amounts = {b.electricityOld(), b.electricityNew(), b.waterOld(), b.waterNew()};
      for (int i = 0; i < 4; i++) p.setString(i + 4, amounts[i].toPlainString());
      p.setString(8, b.waterMode());
      p.setString(9, b.waterUsed().toPlainString());
      p.setString(10, b.electricityRate().toPlainString());
      p.setString(11, b.waterRate().toPlainString());
      p.setString(12, b.total().toPlainString());
      p.setString(13, b.note());
      p.setBytes(14, ep);
      p.setBytes(15, wp);
      p.setLong(16, k.id());
      p.setString(17, b.tenantName());
      p.setString(18, b.rent().toPlainString());
      p.setString(19, b.dueDate());
      p.setString(20, Instant.now().toString());
      p.setString(21, b.periodStart());
      p.setString(22, b.periodEnd());
      p.executeUpdate();
    }
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
      r.next();
      long id = r.getLong(1);
      RentalStore.saveCharges(c, "bill_charges", "bill_id", id, b.fees());
      try (PreparedStatement p = c.prepareStatement("UPDATE bills SET paid=? WHERE id=?")) {
        p.setInt(1, b.total().signum() == 0 ? 1 : 0);
        p.setLong(2, id);
        p.executeUpdate();
      }
      AuditLog.record(
          c,
          "ISSUE_SEGMENT",
          "bill",
          id,
          k.tenantName() + "; " + b.periodLabel() + "; tổng " + b.total());
    }
  }
}
