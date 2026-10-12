package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalRepository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/**
 * Mỗi thao tác tự đóng connection bằng try-with-resources. Tiền lưu dạng TEXT để giữ độ chính xác.
 */
public final class SqliteRepository implements Repository {
  public final Path database;

  public SqliteRepository(Path database) throws Exception {
    this.database = database.toAbsolutePath().normalize();
    Files.createDirectories(this.database.getParent());
    if (Files.exists(this.database) && Files.size(this.database) > 0)
      DatabaseValidator.checkSafeSchema(this.database);
    Schema.initialize(this.database);
  }

  public RentalRepository rentals() {
    return new RentalStore(database);
  }

  private Connection connection() throws SQLException {
    return DatabaseAccess.open(database);
  }

  private static BigDecimal decimal(ResultSet r, String key) throws SQLException {
    return new BigDecimal(r.getString(key));
  }

  public List<Room> rooms() throws SQLException {
    List<Room> list = new ArrayList<>();
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM rooms WHERE active=1 ORDER BY name")) {
      while (r.next())
        list.add(
            new Room(
                r.getLong("id"),
                r.getString("name"),
                r.getString("tenant"),
                decimal(r, "e_start"),
                decimal(r, "w_start"),
                r.getString("water_mode"),
                r.getString("fixed_water") == null ? null : decimal(r, "fixed_water"),
                true));
    }
    return list;
  }

  public void saveRoom(Room room) throws SQLException {
    DataRules.room(room);
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        String before = "";
        if (room.id() != 0) {
          try (PreparedStatement lock =
              c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
            lock.setLong(1, room.id());
            if (lock.executeUpdate() != 1)
              throw new IllegalArgumentException("Phòng không tồn tại hoặc đã lưu trữ.");
          }
          try (PreparedStatement old = c.prepareStatement("SELECT * FROM rooms WHERE id=?")) {
            old.setLong(1, room.id());
            try (ResultSet r = old.executeQuery()) {
              r.next();
              before =
                  "Tên cũ: "
                      + r.getString("name")
                      + "; nước: "
                      + r.getString("water_mode")
                      + "; khoán: "
                      + r.getString("fixed_water");
              try (PreparedStatement history =
                  c.prepareStatement("SELECT 1 FROM bills WHERE room_id=? LIMIT 1")) {
                history.setLong(1, room.id());
                try (ResultSet exists = history.executeQuery()) {
                  if (exists.next()) {
                    if (decimal(r, "e_start").compareTo(room.electricityStart()) != 0
                        || decimal(r, "w_start").compareTo(room.waterStart()) != 0)
                      throw new IllegalArgumentException(
                          "Phòng đã có chứng từ: không sửa số đầu kỳ ban đầu.");
                    if (!r.getString("water_mode").equals(room.waterMode())
                        && latestActivePeriod(c, room.id()) != null)
                      throw new IllegalArgumentException(
                          "Dùng Đổi cách tính nước để ghi số chốt chuyển đổi và kỳ hiệu lực.");
                  }
                }
              }
            }
          }
        }
        try (PreparedStatement stmt =
            c.prepareStatement(
                room.id() == 0
                    ? "INSERT INTO rooms(name,tenant,e_start,w_start,water_mode,fixed_water)"
                        + " VALUES(?,?,?,?,?,?)"
                    : "UPDATE rooms SET"
                        + " name=?,tenant=?,e_start=?,w_start=?,water_mode=?,fixed_water=? WHERE"
                        + " id=?",
                Statement.RETURN_GENERATED_KEYS)) {
          stmt.setString(1, room.name().trim());
          stmt.setString(2, room.tenant().trim());
          stmt.setString(3, room.electricityStart().toPlainString());
          stmt.setString(4, room.waterStart().toPlainString());
          stmt.setString(5, room.waterMode());
          stmt.setString(6, room.fixedWater() == null ? null : room.fixedWater().toPlainString());
          if (room.id() != 0) stmt.setLong(7, room.id());
          stmt.executeUpdate();
          long id = room.id();
          if (id == 0)
            try (ResultSet keys = stmt.getGeneratedKeys()) {
              keys.next();
              id = keys.getLong(1);
            }
          AuditLog.record(
              c,
              room.id() == 0 ? "CREATE" : "UPDATE",
              "room",
              id,
              before
                  + " → "
                  + room.name()
                  + "; nước: "
                  + room.waterMode()
                  + "; khoán: "
                  + room.fixedWater());
        }
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void archiveRoom(long id) throws SQLException {
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
          lock.setLong(1, id);
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Phòng không còn hoạt động.");
        }
        String missing = BillSchedule.firstMissing(c, id, java.time.YearMonth.now());
        if (missing != null)
          throw new IllegalArgumentException(
              "Còn kỳ thuê chưa lập hóa đơn: "
                  + missing
                  + ". Đối soát trước khi ngừng quản lý phòng.");
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE rooms SET active=0 WHERE id=? AND NOT EXISTS(SELECT 1 FROM contracts WHERE"
                    + " room_id=rooms.id AND cancelled_at IS NULL AND (CASE WHEN closed_date=''"
                    + " THEN end_date ELSE closed_date END)>=?)")) {
          p.setLong(1, id);
          p.setString(2, java.time.LocalDate.now().toString());
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException(
                "Cần hết thời hạn thuê hiện tại/sắp tới trước khi lưu trữ phòng.");
        }
        AuditLog.record(c, "ARCHIVE", "room", id, "Ngừng quản lý phòng; giữ lịch sử.");
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public Rates rates() throws SQLException {
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM rates WHERE id=1")) {
      r.next();
      return new Rates(decimal(r, "electricity"), decimal(r, "water"), decimal(r, "default_water"));
    }
  }

  public void saveRates(Rates rates) throws SQLException {
    DataRules.rates(rates);
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock = c.prepareStatement("UPDATE rates SET id=id WHERE id=1")) {
          lock.executeUpdate();
        }
        String old;
        try (Statement st = c.createStatement();
            ResultSet r = st.executeQuery("SELECT * FROM rates WHERE id=1")) {
          r.next();
          old =
              r.getString("electricity")
                  + "/"
                  + r.getString("water")
                  + "/"
                  + r.getString("default_water");
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE rates SET electricity=?,water=?,default_water=? WHERE id=1")) {
          p.setString(1, rates.electricity().toPlainString());
          p.setString(2, rates.water().toPlainString());
          p.setString(3, rates.defaultWater().toPlainString());
          p.executeUpdate();
        }
        AuditLog.record(
            c,
            "RATES",
            "settings",
            1,
            old + " → " + rates.electricity() + "/" + rates.water() + "/" + rates.defaultWater());
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  private static String latestActivePeriod(Connection c, long id) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("SELECT MAX(month) FROM bills WHERE room_id=? AND voided_at IS NULL")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        r.next();
        return r.getString(1);
      }
    }
  }

  public boolean hasRoomHistory(long id) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p = c.prepareStatement("SELECT 1 FROM bills WHERE room_id=? LIMIT 1")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next();
      }
    }
  }

  public String latestMonth(long id) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT month FROM bills WHERE room_id=? AND voided_at IS NULL ORDER BY month DESC"
                    + " LIMIT 1")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? r.getString(1) : null;
      }
    }
  }

  public Readings previous(Room room) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT e_new,w_new FROM bills WHERE room_id=? AND voided_at IS NULL ORDER BY month"
                    + " DESC LIMIT 1")) {
      p.setLong(1, room.id());
      try (ResultSet r = p.executeQuery()) {
        Readings previous =
            r.next()
                ? new Readings(decimal(r, "e_new"), decimal(r, "w_new"))
                : new Readings(room.electricityStart(), room.waterStart());
        return transitionReadings(c, room.id(), previous);
      }
    }
  }

  private static Readings transitionReadings(Connection c, long room, Readings previous)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT opening_reading FROM water_transitions t WHERE room_id=? AND NOT EXISTS(SELECT"
                + " 1 FROM bills b WHERE b.room_id=t.room_id AND b.voided_at IS NULL AND"
                + " b.month>=t.effective_month) ORDER BY effective_month DESC LIMIT 1")) {
      p.setLong(1, room);
      try (ResultSet r = p.executeQuery()) {
        return r.next()
            ? new Readings(previous.electricity(), decimal(r, "opening_reading"))
            : previous;
      }
    }
  }

  public void changeWaterMode(
      long roomId, String mode, BigDecimal opening, BigDecimal fixed, String reason)
      throws SQLException {
    DataRules.mode(mode);
    DataRules.quantity(opening, "Số nước bàn giao");
    if (fixed != null) DataRules.quantity(fixed, "Nước khoán");
    DataRules.text(reason, "Lý do", 2000, true);
    if (mode.equals("METER") && fixed != null)
      throw new IllegalArgumentException("Đồng hồ không có mức khoán.");
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        String oldMode;
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
          lock.setLong(1, roomId);
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Phòng không còn hoạt động.");
        }
        try (PreparedStatement p = c.prepareStatement("SELECT water_mode FROM rooms WHERE id=?")) {
          p.setLong(1, roomId);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            oldMode = r.getString(1);
          }
        }
        if (oldMode.equals(mode))
          throw new IllegalArgumentException("Cách tính nước không thay đổi.");
        String latest;
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT MAX(month) FROM bills WHERE room_id=? AND voided_at IS NULL")) {
          p.setLong(1, roomId);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            latest = r.getString(1);
          }
        }
        if (latest == null)
          throw new IllegalArgumentException(
              "Chưa có hóa đơn: sửa cách tính nước trong thông tin phòng.");
        if (oldMode.equals("METER")) {
          try (PreparedStatement p =
              c.prepareStatement(
                  "SELECT w_new FROM bills WHERE room_id=? AND month=? AND voided_at IS NULL")) {
            p.setLong(1, roomId);
            p.setString(2, latest);
            try (ResultSet r = p.executeQuery()) {
              r.next();
              if (decimal(r, "w_new").compareTo(opening) != 0)
                throw new IllegalArgumentException(
                    "Chốt hết nước đồng hồ kỳ trước trước khi chuyển sang khoán.");
            }
          }
        }
        String effective = DataRules.period(latest).plusMonths(1).toString();
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO"
                    + " water_transitions(room_id,effective_month,old_mode,new_mode,opening_reading,fixed_water,reason,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?)")) {
          p.setLong(1, roomId);
          p.setString(2, effective);
          p.setString(3, oldMode);
          p.setString(4, mode);
          p.setString(5, opening.toPlainString());
          p.setString(6, fixed == null ? null : fixed.toPlainString());
          p.setString(7, reason.trim());
          p.setString(8, java.time.Instant.now().toString());
          p.executeUpdate();
        }
        try (PreparedStatement p =
            c.prepareStatement("UPDATE rooms SET water_mode=?,fixed_water=? WHERE id=?")) {
          p.setString(1, mode);
          p.setString(2, fixed == null ? null : fixed.toPlainString());
          p.setLong(3, roomId);
          p.executeUpdate();
        }
        AuditLog.record(
            c,
            "WATER_MODE",
            "room",
            roomId,
            oldMode + " → " + mode + "; từ " + effective + "; số chốt " + opening + "; " + reason);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  private static Bill bill(Connection c, ResultSet r) throws SQLException {
    return new Bill(
        r.getLong("id"),
        r.getLong("room_id"),
        r.getString("room_name"),
        r.getString("month"),
        decimal(r, "e_old"),
        decimal(r, "e_new"),
        decimal(r, "w_old"),
        decimal(r, "w_new"),
        r.getString("water_mode"),
        decimal(r, "water_used"),
        decimal(r, "e_rate"),
        decimal(r, "w_rate"),
        decimal(r, "total"),
        r.getBoolean("paid"),
        r.getString("note"),
        r.getObject("contract_id") == null ? null : r.getLong("contract_id"),
        r.getString("tenant_name"),
        decimal(r, "rent"),
        RentalStore.charges(c, "bill_charges", "bill_id", r.getLong("id")),
        r.getString("due_date"),
        r.getString("water_amount") == null ? null : decimal(r, "water_amount"));
  }

  public List<Bill> bills(int limit, int offset) throws SQLException {
    List<Bill> list = new ArrayList<>();
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT"
                    + " id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,contract_id,tenant_name,rent,due_date,water_amount"
                    + " FROM bills WHERE voided_at IS NULL ORDER BY month DESC,id DESC LIMIT ?"
                    + " OFFSET ?")) {
      p.setInt(1, limit);
      p.setInt(2, offset);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) list.add(bill(c, r));
      }
    }
    return list;
  }

  public List<Bill> billsBetween(String firstMonth, String lastMonth) throws SQLException {
    List<Bill> list = new ArrayList<>();
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT"
                    + " id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,contract_id,tenant_name,rent,due_date,water_amount"
                    + " FROM bills WHERE voided_at IS NULL AND month>=? AND month<=? ORDER BY"
                    + " month,room_name")) {
      p.setString(1, firstMonth);
      p.setString(2, lastMonth);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) list.add(bill(c, r));
      }
    }
    return list;
  }

  public List<Bill> roomBills(long roomId, String from, String to) throws SQLException {
    DataRules.period(from);
    DataRules.period(to);
    List<Bill> result = new ArrayList<>();
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT"
                    + " id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,contract_id,tenant_name,rent,due_date,water_amount"
                    + " FROM bills WHERE room_id=? AND voided_at IS NULL AND month>=? AND month<=?"
                    + " ORDER BY month DESC,id DESC")) {
      p.setLong(1, roomId);
      p.setString(2, from);
      p.setString(3, to);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) result.add(bill(c, r));
      }
    }
    return result;
  }

  public List<Bill> outstandingBills() throws SQLException {
    List<Bill> result = new ArrayList<>();
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT"
                    + " id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,contract_id,tenant_name,rent,due_date,water_amount"
                    + " FROM bills WHERE voided_at IS NULL AND paid=0 ORDER BY month,room_name")) {
      while (r.next()) {
        Bill b = bill(c, r);
        if (b.total().signum() > 0) result.add(b);
      }
    }
    return result;
  }

  public int billCount() throws SQLException {
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(*) FROM bills WHERE voided_at IS NULL")) {
      return r.getInt(1);
    }
  }

  public void saveBill(Bill b, byte[] ep, byte[] wp) throws SQLException {
    DataRules.bill(b);
    if ((ep != null && ep.length > 12_000_000) || (wp != null && wp.length > 12_000_000))
      throw new IllegalArgumentException("Ảnh quá lớn, tối đa 12 MB mỗi ảnh.");
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
          lock.setLong(1, b.roomId());
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Phòng không còn được quản lý.");
        }
        Readings opening;
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM rooms WHERE id=?")) {
          p.setLong(1, b.roomId());
          try (ResultSet r = p.executeQuery()) {
            r.next();
            if (!b.roomName().equals(r.getString("name"))
                || !b.waterMode().equals(r.getString("water_mode")))
              throw new IllegalArgumentException("Thông tin phòng đã đổi. Đóng form và mở lại.");
            opening = new Readings(decimal(r, "e_start"), decimal(r, "w_start"));
          }
        }
        Contract rental =
            new RentalStore(database).forMonth(c, b.roomId(), java.time.YearMonth.parse(b.month()));
        if (rental == null) {
          if (b.contractId() != null || b.rent().signum() != 0 || !b.fees().isEmpty())
            throw new IllegalArgumentException("Kỳ này không có hợp đồng thuê.");
        } else {
          if (b.rent().compareTo(rental.rent()) != 0 && b.note().isBlank())
            throw new IllegalArgumentException("Ghi lý do điều chỉnh tiền phòng.");
          Bill expected = RentalService.withRental(b, rental, b.rent());
          if (!Objects.equals(b.contractId(), expected.contractId())
              || !b.tenantName().equals(expected.tenantName())
              || !b.fees().equals(expected.fees())
              || !b.dueDate().equals(expected.dueDate())
              || b.total().compareTo(expected.total()) != 0)
            throw new IllegalArgumentException(
                "Hợp đồng hoặc thông tin phí đã đổi. Đóng form và mở lại.");
        }
        String last = null;
        Readings previous = opening;
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT month,e_new,w_new FROM bills WHERE room_id=? AND voided_at IS NULL ORDER BY"
                    + " month DESC LIMIT 1")) {
          p.setLong(1, b.roomId());
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              last = r.getString("month");
              previous = new Readings(decimal(r, "e_new"), decimal(r, "w_new"));
            }
          }
        }
        java.time.YearMonth selected = DataRules.period(b.month());
        String missing = BillSchedule.firstMissing(c, b.roomId(), selected);
        if (missing != null && missing.compareTo(b.month()) < 0)
          throw new IllegalArgumentException(
              "Còn kỳ thuê chưa lập hóa đơn "
                  + missing
                  + ". Không bỏ qua nghĩa vụ thuê để lập kỳ mới.");
        if (last != null && !selected.equals(DataRules.period(last).plusMonths(1)))
          throw new IllegalArgumentException(
              "Chốt tuần tự từng tháng. Kỳ tiếp theo phải là "
                  + DataRules.period(last).plusMonths(1)
                  + "; không dồn điện nước nhiều tháng vào một kỳ.");
        if (last == null
            && rental != null
            && !selected.equals(java.time.YearMonth.from(RentalService.date(rental.startDate()))))
          throw new IllegalArgumentException(
              "Hóa đơn đầu phải bắt đầu từ kỳ vào ở "
                  + rental.startDate().substring(0, 7)
                  + " để không bỏ sót tiền phòng.");
        previous = transitionReadings(c, b.roomId(), previous);
        if (previous.electricity().compareTo(b.electricityOld()) != 0
            || previous.water().compareTo(b.waterOld()) != 0)
          throw new IllegalArgumentException("Số đầu kỳ đã đổi. Đóng form và mở lại.");
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO"
                    + " bills(room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,note,e_photo,w_photo,contract_id,tenant_name,rent,due_date,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
          p.setLong(1, b.roomId());
          p.setString(2, b.roomName());
          p.setString(3, b.month());
          p.setString(4, b.electricityOld().toPlainString());
          p.setString(5, b.electricityNew().toPlainString());
          p.setString(6, b.waterOld().toPlainString());
          p.setString(7, b.waterNew().toPlainString());
          p.setString(8, b.waterMode());
          p.setString(9, b.waterUsed().toPlainString());
          p.setString(10, b.electricityRate().toPlainString());
          p.setString(11, b.waterRate().toPlainString());
          p.setString(12, b.total().toPlainString());
          p.setString(13, b.note());
          p.setBytes(14, ep);
          p.setBytes(15, wp);
          if (b.contractId() == null) p.setNull(16, Types.INTEGER);
          else p.setLong(16, b.contractId());
          p.setString(17, b.tenantName());
          p.setString(18, b.rent().toPlainString());
          p.setString(19, b.dueDate());
          p.setString(20, java.time.Instant.now().toString());
          p.executeUpdate();
        }
        try (Statement ids = c.createStatement();
            ResultSet key = ids.executeQuery("SELECT last_insert_rowid()")) {
          key.next();
          long id = key.getLong(1);
          RentalStore.saveCharges(c, "bill_charges", "bill_id", id, b.fees());
          updatePaid(c, id);
          AuditLog.record(
              c,
              "ISSUE",
              "bill",
              id,
              b.roomName() + "; " + b.month() + "; tổng " + b.total() + "; " + b.note());
        }
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public byte[] photo(long id, boolean electricity) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT " + (electricity ? "e_photo" : "w_photo") + " FROM bills WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? r.getBytes(1) : null;
      }
    }
  }

  public List<Payment> payments(long billId) throws SQLException {
    List<Payment> list = new ArrayList<>();
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT p.* FROM payments p WHERE bill_id=? AND NOT EXISTS(SELECT 1 FROM"
                    + " payment_reversals r WHERE r.payment_id=p.id) ORDER BY received_date,id")) {
      p.setLong(1, billId);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          list.add(
              new Payment(
                  r.getLong("id"),
                  billId,
                  decimal(r, "amount"),
                  r.getString("received_date"),
                  r.getString("note")));
      }
    }
    return list;
  }

  private void updatePaid(Connection c, long id) throws SQLException {
    BigDecimal received = BigDecimal.ZERO, total;
    try (PreparedStatement p = c.prepareStatement("SELECT total FROM bills WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Hóa đơn không tồn tại.");
        total = decimal(r, "total");
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT p.amount FROM payments p WHERE bill_id=? AND NOT EXISTS(SELECT 1 FROM"
                + " payment_reversals r WHERE r.payment_id=p.id)")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) received = received.add(decimal(r, "amount"));
      }
    }
    try (PreparedStatement p = c.prepareStatement("UPDATE bills SET paid=? WHERE id=?")) {
      p.setBoolean(1, received.compareTo(total) >= 0);
      p.setLong(2, id);
      p.executeUpdate();
    }
  }

  public void addPayment(long id, BigDecimal amount, String date, String note) throws SQLException {
    addPayment(id, amount, date, note, java.util.UUID.randomUUID().toString());
  }

  public void addPayment(long id, BigDecimal amount, String date, String note, String requestKey)
      throws SQLException {
    RentalService.requireMoney(amount, "Số tiền thu");
    if (amount.signum() <= 0) throw new IllegalArgumentException("Số tiền thu phải lớn hơn 0.");
    DataRules.actualDate(date);
    DataRules.text(note, "Ghi chú", 2000, false);
    DataRules.text(requestKey, "Mã thao tác", 100, true);
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        // Lock the write transaction before reading the remaining balance.
        try (PreparedStatement p =
            c.prepareStatement("UPDATE bills SET paid=paid WHERE id=? AND voided_at IS NULL")) {
          p.setLong(1, id);
          if (p.executeUpdate() != 1) throw new IllegalArgumentException("Hóa đơn không tồn tại.");
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT bill_id,amount,received_date,note FROM payments WHERE request_key=?")) {
          p.setString(1, requestKey);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              if (r.getLong("bill_id") != id
                  || decimal(r, "amount").compareTo(amount) != 0
                  || !r.getString("received_date").equals(date)
                  || !r.getString("note").equals(note.trim()))
                throw new IllegalArgumentException("Mã thao tác đã dùng cho nội dung khác.");
              c.commit();
              return;
            }
          }
        }
        BigDecimal total, collected = BigDecimal.ZERO;
        try (PreparedStatement p = c.prepareStatement("SELECT total FROM bills WHERE id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            total = decimal(r, "total");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT p.amount FROM payments p WHERE bill_id=? AND NOT EXISTS(SELECT 1 FROM"
                    + " payment_reversals r WHERE r.payment_id=p.id)")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            while (r.next()) collected = collected.add(decimal(r, "amount"));
          }
        }
        if (amount.compareTo(total.subtract(collected)) > 0)
          throw new IllegalArgumentException("Số tiền vượt quá phần còn thiếu.");
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO payments(bill_id,amount,received_date,note,created_at,request_key)"
                    + " VALUES(?,?,?,?,?,?)")) {
          p.setLong(1, id);
          p.setString(2, amount.toPlainString());
          p.setString(3, date);
          p.setString(4, note.trim());
          p.setString(5, java.time.Instant.now().toString());
          p.setString(6, requestKey);
          p.executeUpdate();
        }
        updatePaid(c, id);
        AuditLog.record(c, "RECEIVE", "bill", id, amount + "; ngày " + date + "; " + note);
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public void deletePayment(long id) throws SQLException {
    reversePayment(id, "Đảo lần thu nhập nhầm");
  }

  public void reversePayment(long id, String reason) throws SQLException {
    DataRules.text(reason, "Lý do đảo thu", 2000, true);
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        long billId;
        try (PreparedStatement lock =
            c.prepareStatement(
                "UPDATE bills SET paid=paid WHERE id=(SELECT bill_id FROM payments WHERE id=?) AND"
                    + " voided_at IS NULL")) {
          lock.setLong(1, id);
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Lần thu không tồn tại hoặc hóa đơn đã hủy.");
        }
        try (PreparedStatement p = c.prepareStatement("SELECT bill_id FROM payments WHERE id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            billId = r.getLong(1);
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO payment_reversals(payment_id,reversed_at,reason) VALUES(?,?,?)")) {
          p.setLong(1, id);
          p.setString(2, java.time.Instant.now().toString());
          p.setString(3, reason.trim());
          p.executeUpdate();
        }
        updatePaid(c, billId);
        AuditLog.record(c, "REVERSE", "payment", id, reason);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  /** Compatibility helper for old callers; the UI uses explicit receipts. */
  public void setPaid(long id, boolean paid) throws SQLException {
    if (paid) {
      BigDecimal total;
      try (Connection c = connection();
          PreparedStatement p = c.prepareStatement("SELECT total FROM bills WHERE id=?")) {
        p.setLong(1, id);
        try (ResultSet r = p.executeQuery()) {
          if (!r.next()) throw new IllegalArgumentException("Hóa đơn không tồn tại.");
          total = decimal(r, "total");
        }
      }
      BigDecimal rest = total.subtract(received(id));
      if (rest.signum() > 0) addPayment(id, rest, java.time.LocalDate.now().toString(), "Thu đủ");
      return;
    }
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE bills SET paid=paid WHERE id=? AND voided_at IS NULL")) {
          lock.setLong(1, id);
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Hóa đơn không tồn tại hoặc đã hủy.");
        }
        List<Long> originals = new ArrayList<>();
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT p.id FROM payments p WHERE p.bill_id=? AND NOT EXISTS(SELECT 1 FROM"
                    + " payment_reversals r WHERE r.payment_id=p.id)")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            while (r.next()) originals.add(r.getLong(1));
          }
        }
        for (long payment : originals) {
          try (PreparedStatement p =
              c.prepareStatement(
                  "INSERT INTO payment_reversals(payment_id,reversed_at,reason) VALUES(?,?,?)")) {
            p.setLong(1, payment);
            p.setString(2, java.time.Instant.now().toString());
            p.setString(3, "Đảo qua helper tương thích");
            p.executeUpdate();
            AuditLog.record(c, "REVERSE", "payment", payment, "Đảo qua helper tương thích");
          }
        }
        updatePaid(c, id);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void deleteLatest(Bill bill) throws SQLException {
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE bills SET voided_at=?,void_reason=? WHERE id=? AND room_id=? AND voided_at"
                    + " IS NULL AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.bill_id=bills.id"
                    + " AND NOT EXISTS(SELECT 1 FROM payment_reversals r WHERE r.payment_id=p.id))"
                    + " AND month=(SELECT MAX(month) FROM bills WHERE room_id=? AND voided_at IS"
                    + " NULL) AND NOT EXISTS(SELECT 1 FROM water_transitions t WHERE"
                    + " t.room_id=bills.room_id AND t.effective_month>bills.month)")) {
          p.setString(1, java.time.Instant.now().toString());
          p.setString(2, "Hủy kỳ cuối để lập lại");
          p.setLong(3, bill.id());
          p.setLong(4, bill.roomId());
          p.setLong(5, bill.roomId());
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException(
                "Chỉ hủy hóa đơn cuối chưa còn khoản thu; không hủy kỳ trước mốc chuyển nước.");
        }
        AuditLog.record(c, "VOID", "bill", bill.id(), "Hủy để lập lại; bản gốc và ảnh vẫn giữ.");
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void backup(Path destination) throws Exception {
    if (Files.exists(destination))
      throw new IllegalArgumentException("Chọn tên file mới; không ghi đè bản sao lưu.");
    try (Connection c = connection();
        PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
      p.setString(1, destination.toAbsolutePath().toString());
      p.execute();
    }
  }

  public void restore(Path source) throws Exception {
    DatabaseAccess.exclusive(
        database,
        () -> {
          restoreExclusive(source);
          return null;
        });
  }

  private void restoreExclusive(Path source) throws Exception {
    if (Files.isSameFile(source, database))
      throw new IllegalArgumentException("Không thể phục hồi từ chính file đang dùng.");
    Path candidate = Files.createTempFile(database.getParent(), "restore-", ".db");
    try {
      if (Files.size(source) > 1_000_000_000L)
        throw new IllegalArgumentException(
            "Backup vượt 1 GB. Cần kiểm tra dung lượng trước khi phục hồi.");
      Files.copy(source, candidate, StandardCopyOption.REPLACE_EXISTING);
      DatabaseValidator.checkSafeSchema(candidate);
      try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + candidate);
          Statement s = c.createStatement()) {
        try (ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
          if (!r.next() || !"ok".equals(r.getString(1)))
            throw new IllegalArgumentException("File database bị lỗi.");
        }
        try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
          if (r.getInt(1) < 1 || r.getInt(1) > Schema.VERSION)
            throw new IllegalArgumentException("Bản sao lưu không đúng phiên bản NhaTroHomu.");
          if (r.getInt(1) >= 2)
            s.executeQuery("SELECT id,bill_id,amount,received_date,note FROM payments LIMIT 1")
                .close();
        }
        s.executeQuery(
                "SELECT id,name,tenant,e_start,w_start,water_mode,fixed_water,active FROM rooms"
                    + " LIMIT 1")
            .close();
        try (ResultSet r =
            s.executeQuery("SELECT electricity,water,default_water FROM rates WHERE id=1")) {
          if (!r.next()) throw new IllegalArgumentException("Thiếu cấu hình đơn giá.");
          decimal(r, "electricity");
          decimal(r, "water");
          decimal(r, "default_water");
        }
        s.executeQuery(
                "SELECT"
                    + " id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,e_photo,w_photo"
                    + " FROM bills LIMIT 1")
            .close();
        try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
          if (r.next())
            throw new IllegalArgumentException("Bản sao lưu có dữ liệu phòng/hóa đơn không khớp.");
        }
      }
      SqliteRepository restored = new SqliteRepository(candidate);
      try (Connection c = restored.connection();
          Statement check = c.createStatement()) {
        for (String query :
            List.of(
                "SELECT id,name,phone,note,active FROM tenants LIMIT 0",
                "SELECT id,room_id,tenant_id,start_date,end_date,closed_date,rent,deposit,due_day"
                    + " FROM contracts LIMIT 0",
                "SELECT contract_id,label,amount FROM contract_charges LIMIT 0",
                "SELECT id,contract_id,kind,amount,entry_date,note FROM deposits LIMIT 0",
                "SELECT bill_id,label,amount FROM bill_charges LIMIT 0",
                "SELECT contract_id,tenant_name,rent,due_date FROM bills LIMIT 0"))
          check.executeQuery(query).close();
      }
      try (Connection c = restored.connection()) {
        DatabaseValidator.validate(c);
      }
      // Luôn giữ một bản cứu hộ trước khi thay database hiện tại.
      backup(database.resolveSibling("before-restore-" + UUID.randomUUID() + ".db"));
      Files.move(candidate, database, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(candidate);
    }
  }
}
