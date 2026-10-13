package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.rental.RentalRepository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** JDBC persistence of tenants, contracts, charge schedules and deposit ledger. */
public final class RentalStore implements RentalRepository {
  private final Path database;

  public RentalStore(Path database) {
    this.database = database;
  }

  private Connection connection() throws SQLException {
    return DatabaseAccess.open(database);
  }

  public static void migrate(Connection c) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE tenants(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT NOT NULL,note"
              + " TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1)");
      s.execute(
          "CREATE TABLE contracts(id INTEGER PRIMARY KEY,room_id INTEGER NOT NULL REFERENCES"
              + " rooms(id),tenant_id INTEGER NOT NULL REFERENCES tenants(id),start_date TEXT NOT"
              + " NULL,end_date TEXT NOT NULL,closed_date TEXT NOT NULL DEFAULT '',rent TEXT NOT"
              + " NULL,deposit TEXT NOT NULL,due_day INTEGER NOT NULL CHECK(due_day BETWEEN 1 AND"
              + " 28))");
      s.execute("CREATE INDEX contracts_room ON contracts(room_id,start_date,end_date)");
      s.execute(
          "CREATE TABLE contract_charges(id INTEGER PRIMARY KEY,contract_id INTEGER NOT NULL"
              + " REFERENCES contracts(id),label TEXT NOT NULL,amount TEXT NOT NULL)");
      s.execute(
          "CREATE TABLE deposits(id INTEGER PRIMARY KEY,contract_id INTEGER NOT NULL REFERENCES"
              + " contracts(id),kind TEXT NOT NULL CHECK(kind IN ('RECEIVE','REFUND')),amount TEXT"
              + " NOT NULL,entry_date TEXT NOT NULL,note TEXT NOT NULL)");
      s.execute("CREATE INDEX deposits_contract ON deposits(contract_id)");
      s.execute("ALTER TABLE bills ADD COLUMN contract_id INTEGER REFERENCES contracts(id)");
      s.execute("ALTER TABLE bills ADD COLUMN tenant_name TEXT NOT NULL DEFAULT ''");
      s.execute("ALTER TABLE bills ADD COLUMN rent TEXT NOT NULL DEFAULT '0'");
      s.execute("ALTER TABLE bills ADD COLUMN due_date TEXT NOT NULL DEFAULT ''");
      s.execute(
          "CREATE TABLE bill_charges(id INTEGER PRIMARY KEY,bill_id INTEGER NOT NULL REFERENCES"
              + " bills(id) ON DELETE CASCADE,label TEXT NOT NULL,amount TEXT NOT NULL)");
      s.execute("CREATE INDEX bill_charges_bill ON bill_charges(bill_id)");
      s.execute(
          "INSERT INTO tenants(name,phone,note) SELECT DISTINCT trim(tenant),'','Tên chuyển từ sổ"
              + " điện nước cũ; chưa có hợp đồng' FROM rooms WHERE trim(tenant)<>''");
    }
  }

  public List<Tenant> tenants() throws SQLException {
    List<Tenant> result = new ArrayList<>();
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM tenants WHERE active=1 ORDER BY name,id")) {
      while (r.next())
        result.add(
            new Tenant(
                r.getLong("id"),
                r.getString("name"),
                r.getString("phone"),
                r.getString("note"),
                true,
                r.getString("zalo"),
                r.getBoolean("zalo_auto"),
                r.getString("identity_no"),
                r.getString("birthday"),
                r.getString("address"),
                residents(c, r.getLong("id"))));
    }
    return result;
  }

  static List<Resident> residents(Connection c, long id) throws SQLException {
    List<Resident> result = new ArrayList<>();
    try (PreparedStatement p =
        c.prepareStatement("SELECT * FROM residents WHERE tenant_id=? ORDER BY id")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          result.add(
              new Resident(
                  r.getString("name"),
                  r.getString("relationship"),
                  r.getString("birthday"),
                  r.getString("identity_no"),
                  r.getString("phone")));
      }
    }
    return result;
  }

  public Tenant tenant(long id) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p = c.prepareStatement("SELECT * FROM tenants WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) return null;
        return new Tenant(
            r.getLong("id"),
            r.getString("name"),
            r.getString("phone"),
            r.getString("note"),
            r.getBoolean("active"),
            r.getString("zalo"),
            r.getBoolean("zalo_auto"),
            r.getString("identity_no"),
            r.getString("birthday"),
            r.getString("address"),
            residents(c, id));
      }
    }
  }

  public void saveTenant(Tenant tenant) throws SQLException {
    vn.nhatrohomu.shared.TenantRules.tenant(tenant);
    String sql =
        tenant.id() == 0
            ? "INSERT INTO tenants(name,phone,note,zalo,zalo_auto,identity_no,birthday,address)"
                + " VALUES(?,?,?,?,?,?,?,?)"
            : "UPDATE tenants SET"
                + " name=?,phone=?,note=?,zalo=?,zalo_auto=?,identity_no=?,birthday=?,address=?"
                + " WHERE id=? AND active=1";
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        long id = tenant.id();
        try (PreparedStatement p = c.prepareStatement(sql)) {
          p.setString(1, tenant.name().trim());
          p.setString(2, tenant.phone().trim());
          p.setString(3, tenant.note().trim());
          p.setString(4, tenant.zaloAuto() ? "" : tenant.zalo().trim());
          p.setBoolean(5, tenant.zaloAuto());
          p.setString(6, tenant.identity().trim());
          p.setString(7, tenant.birthday().trim());
          p.setString(8, tenant.address().trim());
          if (id != 0) p.setLong(9, id);
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException("Người thuê không tồn tại hoặc đã lưu trữ.");
        }
        if (id == 0)
          try (Statement s = c.createStatement();
              ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
            r.next();
            id = r.getLong(1);
          }
        try (PreparedStatement p = c.prepareStatement("DELETE FROM residents WHERE tenant_id=?")) {
          p.setLong(1, id);
          p.executeUpdate();
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO residents(tenant_id,name,relationship,birthday,identity_no,phone)"
                    + " VALUES(?,?,?,?,?,?)")) {
          for (Resident r : tenant.residents()) {
            p.setLong(1, id);
            p.setString(2, r.name().trim());
            p.setString(3, r.relationship().trim());
            p.setString(4, r.birthday().trim());
            p.setString(5, r.identity().trim());
            p.setString(6, r.phone().trim());
            p.addBatch();
          }
          p.executeBatch();
        }
        c.commit();
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  public void archiveTenant(long id) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "UPDATE tenants SET active=0 WHERE id=? AND NOT EXISTS(SELECT 1 FROM contracts"
                    + " WHERE tenant_id=? AND cancelled_at IS NULL AND (CASE WHEN closed_date=''"
                    + " THEN end_date ELSE closed_date END)>=? AND (closed_date='' OR"
                    + " closed_date>?))")) {
      p.setLong(1, id);
      p.setLong(2, id);
      p.setString(3, LocalDate.now().toString());
      p.setString(4, LocalDate.now().toString());
      if (p.executeUpdate() != 1)
        throw new IllegalArgumentException(
            "Cần kết thúc hợp đồng hiện tại/sắp tới trước khi lưu trữ người thuê.");
    }
  }

  static List<Charge> charges(Connection c, String table, String key, long id) throws SQLException {
    if (!((table.equals("bill_charges") && key.equals("bill_id"))
        || (table.equals("contract_charges") && key.equals("contract_id"))))
      throw new IllegalArgumentException("Bảng phí không hợp lệ.");
    List<Charge> fees = new ArrayList<>();
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT label,amount FROM " + table + " WHERE " + key + "=? ORDER BY id")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) fees.add(new Charge(r.getString(1), new BigDecimal(r.getString(2))));
      }
    }
    return fees;
  }

  static void saveCharges(Connection c, String table, String key, long id, List<Charge> fees)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("INSERT INTO " + table + "(" + key + ",label,amount) VALUES(?,?,?)")) {
      for (Charge fee : fees) {
        p.setLong(1, id);
        p.setString(2, fee.label().trim());
        p.setString(3, fee.amount().toPlainString());
        p.addBatch();
      }
      p.executeBatch();
    }
  }

  public List<Contract> contracts() throws SQLException {
    try (Connection c = connection()) {
      return contracts(c);
    }
  }

  static List<Contract> contracts(Connection c) throws SQLException {
    List<Contract> result = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT k.*,r.name room_name,t.name tenant_name FROM contracts k JOIN rooms r ON"
                    + " r.id=k.room_id JOIN tenants t ON t.id=k.tenant_id WHERE k.cancelled_at IS"
                    + " NULL ORDER BY k.start_date DESC,k.id DESC")) {
      while (r.next())
        result.add(
            new Contract(
                r.getLong("id"),
                r.getLong("room_id"),
                r.getString("room_name"),
                r.getLong("tenant_id"),
                r.getString("tenant_name"),
                r.getString("start_date"),
                r.getString("end_date"),
                r.getString("closed_date"),
                new BigDecimal(r.getString("rent")),
                new BigDecimal(r.getString("deposit")),
                r.getInt("due_day"),
                charges(c, "contract_charges", "contract_id", r.getLong("id"))));
    }
    return result;
  }

  public Contract forMonth(long roomId, YearMonth month) throws SQLException {
    try (Connection c = connection()) {
      return forMonth(c, roomId, month);
    }
  }

  Contract forMonth(Connection c, long roomId, YearMonth month) throws SQLException {
    List<Contract> matches =
        contracts(c).stream()
            .filter(
                k ->
                    k.roomId() == roomId
                        && !RentalService.date(k.startDate()).isAfter(month.atEndOfMonth())
                        && !RentalService.date(k.effectiveEnd()).isBefore(month.atDay(1)))
            .toList();
    if (matches.size() > 1)
      throw new IllegalArgumentException(
          "Tháng này có nhiều người thuê. Chọn đúng lần thuê để lập hóa đơn.");
    return matches.isEmpty() ? null : matches.getFirst();
  }

  public void createContract(Contract contract) throws SQLException {
    createContract(contract, null);
  }

  public void createContract(Contract contract, Readings opening) throws SQLException {
    if (!contract.closedDate().isBlank())
      throw new IllegalArgumentException("Hợp đồng mới không có ngày kết thúc thực tế.");
    LocalDate start = RentalService.date(contract.startDate()),
        end = RentalService.date(contract.endDate());
    if (end.isBefore(start))
      throw new IllegalArgumentException("Ngày hết hạn phải từ ngày vào ở trở đi.");
    RentalService.requireMoney(contract.rent(), "Tiền thuê");
    RentalService.requireMoney(contract.deposit(), "Tiền cọc");
    if (contract.dueDay() < 1 || contract.dueDay() > 28)
      throw new IllegalArgumentException("Ngày hạn thu chọn từ 1 đến 28.");
    if (contract.fees().size() > 20) throw new IllegalArgumentException("Tối đa 20 khoản phí.");
    Set<String> names = new HashSet<>();
    for (Charge fee : contract.fees()) {
      if (fee.label().isBlank()
          || fee.label().length() > 100
          || !names.add(fee.label().trim().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Tên phí không được trống, quá dài hoặc trùng.");
      DataRules.name(fee.label(), "Tên phí", 100);
      RentalService.requireMoney(fee.amount(), "Phí " + fee.label());
    }
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        // Acquire the write lock before checking overlapping occupation.
        try (PreparedStatement p =
            c.prepareStatement("UPDATE rooms SET active=active WHERE id=? AND active=1")) {
          p.setLong(1, contract.roomId());
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException("Chọn phòng đang được quản lý.");
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT 1 FROM tenants WHERE id=? AND active=1")) {
          p.setLong(1, contract.tenantId());
          try (ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("Chọn người thuê chưa lưu trữ.");
          }
        }
        for (Contract existing : contracts(c)) {
          if (existing.roomId() == contract.roomId()
              && !end.isBefore(RentalService.date(existing.startDate()))
              && !start.isAfter(RentalService.date(existing.effectiveEnd())))
            throw new IllegalArgumentException(
                "Phòng đã có người thuê trong khoảng ngày này. Ngày trả phòng tính cả ngày đó.");
          if (existing.roomId() == contract.roomId()
              && start.isAfter(RentalService.date(existing.effectiveEnd()))
              && YearMonth.from(start)
                  .equals(YearMonth.from(RentalService.date(existing.effectiveEnd())))) {
            try (PreparedStatement p =
                c.prepareStatement(
                    "SELECT 1 FROM handover_events WHERE contract_id=? AND kind='OUT'")) {
              p.setLong(1, existing.id());
              try (ResultSet r = p.executeQuery()) {
                if (opening == null || !r.next())
                  throw new IllegalArgumentException(
                      "Đổi người cùng tháng cần chốt Trả phòng và chỉ số bàn giao trước.");
              }
            }
          }
          if (existing.tenantId() == contract.tenantId()
              && !end.isBefore(RentalService.date(existing.startDate()))
              && !start.isAfter(RentalService.date(existing.effectiveEnd())))
            throw new IllegalArgumentException(
                "Người thuê đang có hợp đồng khác trong khoảng ngày này.");
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT 1 FROM bills WHERE room_id=? AND voided_at IS NULL AND month>=? AND"
                    + " month<=? AND (period_start='' OR NOT(period_end<? OR period_start>?)) LIMIT"
                    + " 1")) {
          p.setLong(1, contract.roomId());
          p.setString(2, YearMonth.from(start).toString());
          p.setString(3, YearMonth.from(end).toString());
          p.setString(4, start.toString());
          p.setString(5, end.toString());
          try (ResultSet r = p.executeQuery()) {
            if (r.next())
              throw new IllegalArgumentException(
                  "Khoảng thuê đã có hóa đơn. Không gắn hợp đồng mới vào kỳ đã chốt.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO contracts(room_id,tenant_id,start_date,end_date,rent,deposit,due_day)"
                    + " VALUES(?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
          p.setLong(1, contract.roomId());
          p.setLong(2, contract.tenantId());
          p.setString(3, contract.startDate());
          p.setString(4, contract.endDate());
          p.setString(5, contract.rent().toPlainString());
          p.setString(6, contract.deposit().toPlainString());
          p.setInt(7, contract.dueDay());
          p.executeUpdate();
          try (ResultSet keys = p.getGeneratedKeys()) {
            keys.next();
            long newId = keys.getLong(1);
            saveCharges(c, "contract_charges", "contract_id", newId, contract.fees());
            if (opening != null) {
              DataRules.quantity(opening.electricity(), "Điện bàn giao");
              DataRules.quantity(opening.water(), "Nước bàn giao");
              Readings baseline =
                  TurnoverStore.opening(c, TurnoverStore.contract(c, newId), YearMonth.from(start));
              if (opening.electricity().compareTo(baseline.electricity()) < 0
                  || opening.water().compareTo(baseline.water()) < 0)
                throw new IllegalArgumentException(
                    "Chỉ số nhận phòng thấp hơn số đầu/đã chốt trước đó.");
              TurnoverStore.event(
                  c, TurnoverStore.contract(c, newId), "IN", contract.startDate(), opening);
            }
            AuditLog.record(
                c,
                "CREATE",
                "contract",
                keys.getLong(1),
                "Phòng "
                    + contract.roomId()
                    + "; người thuê "
                    + contract.tenantId()
                    + "; "
                    + contract.startDate()
                    + " → "
                    + contract.endDate()
                    + "; tiền phòng "
                    + contract.rent()
                    + "; cọc "
                    + contract.deposit());
          }
        }
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public boolean hasHandover(long id) throws SQLException {
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement("SELECT 1 FROM handover_events WHERE contract_id=? AND kind='IN'")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next();
      }
    }
  }

  public Readings opening(long id, YearMonth month) throws SQLException {
    try (Connection c = connection()) {
      return TurnoverStore.opening(c, TurnoverStore.contract(c, id), month);
    }
  }

  public void checkout(Bill bill) throws SQLException {
    new TurnoverStore(database).checkout(bill);
  }

  public void closeContract(long id, String date) throws SQLException {
    if (hasHandover(id))
      throw new IllegalArgumentException(
          "Lần thuê có bàn giao: dùng Trả phòng và chốt kỳ cuối để lưu cùng hóa đơn/chỉ số.");
    LocalDate closed = RentalService.date(date);
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement("UPDATE contracts SET closed_date=closed_date WHERE id=?")) {
          p.setLong(1, id);
          p.executeUpdate();
        }
        Contract contract =
            contracts(c).stream()
                .filter(k -> k.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không có hợp đồng."));
        if (!contract.closedDate().isBlank()
            || closed.isBefore(RentalService.date(contract.startDate()))
            || closed.isAfter(RentalService.date(contract.endDate())))
          throw new IllegalArgumentException(
              "Ngày kết thúc cần nằm trong thời hạn hợp đồng chưa kết thúc.");
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT 1 FROM bills WHERE contract_id=? AND voided_at IS NULL AND month>?"
                    + " LIMIT 1")) {
          p.setLong(1, id);
          p.setString(2, YearMonth.from(closed).toString());
          try (ResultSet r = p.executeQuery()) {
            if (r.next())
              throw new IllegalArgumentException(
                  "Đã lập hóa đơn sau ngày kết thúc. Kiểm tra các hóa đơn trước.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement("UPDATE contracts SET closed_date=? WHERE id=?")) {
          p.setString(1, date);
          p.setLong(2, id);
          p.executeUpdate();
        }
        AuditLog.record(c, "CLOSE", "contract", id, "Ngày kết thúc: " + date);
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public void cancelUnusedContract(long id) throws SQLException {
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement("UPDATE contracts SET rent=rent WHERE id=?")) {
          p.setLong(1, id);
          if (p.executeUpdate() != 1) throw new IllegalArgumentException("Không có hợp đồng.");
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "SELECT 1 FROM bills WHERE contract_id=? UNION ALL SELECT 1 FROM deposits WHERE"
                    + " contract_id=? LIMIT 1")) {
          p.setLong(1, id);
          p.setLong(2, id);
          try (ResultSet r = p.executeQuery()) {
            if (r.next())
              throw new IllegalArgumentException(
                  "Chỉ hủy hợp đồng chưa có hóa đơn và chưa ghi sổ cọc. Dùng kết thúc thuê để giữ"
                      + " lịch sử.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE contracts SET cancelled_at=?,cancelled_reason=? WHERE id=? AND cancelled_at"
                    + " IS NULL")) {
          p.setString(1, java.time.Instant.now().toString());
          p.setString(2, "Hủy hợp đồng nhập nhầm chưa có chứng từ");
          p.setLong(3, id);
          if (p.executeUpdate() != 1) throw new IllegalArgumentException("Hợp đồng đã hủy.");
        }
        AuditLog.record(
            c, "CANCEL_UNUSED", "contract", id, "Giữ hồ sơ để mã hợp đồng không được cấp lại.");
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public List<DepositEntry> deposits(long id) throws SQLException {
    List<DepositEntry> result = new ArrayList<>();
    try (Connection c = connection();
        PreparedStatement p =
            c.prepareStatement(
                "SELECT * FROM deposits WHERE contract_id=? ORDER BY entry_date,id")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          result.add(
              new DepositEntry(
                  r.getLong("id"),
                  id,
                  r.getString("kind"),
                  new BigDecimal(r.getString("amount")),
                  r.getString("entry_date"),
                  r.getString("note")));
      }
    }
    return result;
  }

  public void addDeposit(long id, String kind, BigDecimal amount, String date, String note)
      throws SQLException {
    RentalService.requireMoney(amount, "Tiền cọc");
    DataRules.actualDate(date);
    DataRules.text(note, "Ghi chú cọc", 2000, false);
    if (amount.signum() == 0 || (!kind.equals("RECEIVE") && !kind.equals("REFUND")))
      throw new IllegalArgumentException("Chọn nhận/hoàn cọc và số tiền lớn hơn 0.");
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      try {
        BigDecimal required, received = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        try (PreparedStatement p =
            c.prepareStatement("UPDATE contracts SET rent=rent WHERE id=?")) {
          p.setLong(1, id);
          if (p.executeUpdate() != 1) throw new IllegalArgumentException("Không có hợp đồng.");
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT deposit,start_date FROM contracts WHERE id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            required = new BigDecimal(r.getString(1));
          }
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT MAX(entry_date) FROM deposits WHERE contract_id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            if (r.next() && r.getString(1) != null && date.compareTo(r.getString(1)) < 0)
              throw new IllegalArgumentException(
                  "Ghi sổ cọc theo thứ tự ngày; ngày mới không được trước lần ghi gần nhất.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT kind,amount FROM deposits WHERE contract_id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            while (r.next()) {
              if (r.getString(1).equals("RECEIVE"))
                received = received.add(new BigDecimal(r.getString(2)));
              else refunded = refunded.add(new BigDecimal(r.getString(2)));
            }
          }
        }
        if (kind.equals("RECEIVE")
            && amount.compareTo(required.subtract(received.subtract(refunded))) > 0)
          throw new IllegalArgumentException("Tổng nhận cọc vượt mức cọc hợp đồng.");
        if (kind.equals("REFUND") && amount.compareTo(received.subtract(refunded)) > 0)
          throw new IllegalArgumentException("Tiền hoàn vượt số cọc đang giữ.");
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO deposits(contract_id,kind,amount,entry_date,note)"
                    + " VALUES(?,?,?,?,?)")) {
          p.setLong(1, id);
          p.setString(2, kind);
          p.setString(3, amount.toPlainString());
          p.setString(4, date);
          p.setString(5, note.trim());
          p.executeUpdate();
        }
        AuditLog.record(c, kind, "deposit", id, amount + "; " + date + "; " + note);
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }
}
