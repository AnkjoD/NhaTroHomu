package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.reporting.ExpenseStore;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** Reads all rows, not just schema projections. No repairs or invented history. */
public final class DatabaseValidator {
  private DatabaseValidator() {}

  private static final Set<String> TABLES =
      Set.of(
          "rooms",
          "rates",
          "bills",
          "payments",
          "tenants",
          "contracts",
          "contract_charges",
          "deposits",
          "bill_charges",
          "audit_events",
          "payment_reversals",
          "water_transitions",
          "expenses",
          "expense_reversals");

  public static void checkSafeSchema(Path file) throws SQLException {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement()) {
      s.execute("PRAGMA trusted_schema=OFF");
      Set<String> foundTriggers = new HashSet<>();
      int version;
      try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
        r.next();
        version = r.getInt(1);
      }
      try (ResultSet r =
          s.executeQuery(
              "SELECT type,name,sql FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%'")) {
        while (r.next()) {
          String type = r.getString("type"), name = r.getString("name"), sql = r.getString("sql");
          if (type.equals("view")
              || (type.equals("table")
                  && (!TABLES.contains(name)
                      || sql.toUpperCase(Locale.ROOT).contains("VIRTUAL TABLE"))))
            throw new IllegalArgumentException(
                "Backup chứa cấu trúc không thuộc NhaTroHomu: " + name);
          if (type.equals("trigger")) {
            foundTriggers.add(name);
            if (!normalized(sql).equals(normalized(Schema.triggers().get(name))))
              throw new IllegalArgumentException("Backup chứa trigger lạ hoặc đã bị sửa: " + name);
          }
        }
      }
      if (version == Schema.VERSION && !foundTriggers.equals(Schema.triggers().keySet()))
        throw new IllegalArgumentException("Backup thiếu cơ chế giữ lịch sử chứng từ.");
    }
  }

  private static String normalized(String sql) {
    return sql == null ? "" : sql.strip().replaceAll("\\s+", " ").replaceAll(";+$", "");
  }

  private static BigDecimal d(ResultSet r, String key) throws SQLException {
    try {
      return new BigDecimal(r.getString(key));
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Giá trị tiền/chỉ số sai ở trường " + key + ", bản ghi " + r.getLong("id"), ex);
    }
  }

  private static void flag(int value) {
    if (value != 0 && value != 1) throw new IllegalArgumentException("Cờ trạng thái không hợp lệ.");
  }

  private static void positive(BigDecimal value, String field) {
    RentalService.requireMoney(value, field);
    if (value.signum() == 0) throw new IllegalArgumentException(field + " phải lớn hơn 0.");
  }

  public static void validate(Connection c) throws SQLException {
    try (Statement s = c.createStatement()) {
      try (ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
        if (!r.next() || !r.getString(1).equals("ok"))
          throw new IllegalArgumentException("Database bị lỗi cấu trúc.");
      }
      try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
        if (r.next()) throw new IllegalArgumentException("Database có khóa ngoại không khớp.");
      }
    }
    for (String table : TABLES) {
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT 1 FROM " + table + " WHERE typeof(id)<>'integer' OR id<=0 LIMIT 1")) {
        if (r.next()) throw new IllegalArgumentException("Mã bản ghi không hợp lệ trong " + table);
      }
    }
    for (String table : List.of("rooms", "tenants", "bills")) {
      String flag = table.equals("bills") ? "paid" : "active";
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT 1 FROM "
                      + table
                      + " WHERE typeof("
                      + flag
                      + ")<>'integer' OR "
                      + flag
                      + " NOT IN (0,1) LIMIT 1")) {
        if (r.next()) throw new IllegalArgumentException("Trạng thái không hợp lệ trong " + table);
      }
    }
    Map<Long, Room> rooms = new HashMap<>();
    Map<Long, Tenant> tenants = new HashMap<>();
    Map<Long, Contract> contracts = new HashMap<>();
    Set<Long> cancelledContracts = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM rooms")) {
      while (r.next()) {
        flag(r.getInt("active"));
        Room room =
            new Room(
                r.getLong("id"),
                r.getString("name"),
                r.getString("tenant"),
                d(r, "e_start"),
                d(r, "w_start"),
                r.getString("water_mode"),
                r.getString("fixed_water") == null ? null : d(r, "fixed_water"),
                r.getBoolean("active"));
        DataRules.room(room);
        rooms.put(room.id(), room);
      }
    }
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM rates")) {
      int count = 0;
      while (r.next()) {
        count++;
        if (r.getLong("id") != 1) throw new IllegalArgumentException("Sai mã cấu hình.");
        DataRules.rates(new Rates(d(r, "electricity"), d(r, "water"), d(r, "default_water")));
      }
      if (count != 1) throw new IllegalArgumentException("Thiếu hoặc trùng cấu hình đơn giá.");
    }
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM tenants")) {
      while (r.next()) {
        flag(r.getInt("active"));
        DataRules.name(r.getString("name"), "Tên người thuê", 120);
        DataRules.text(r.getString("phone"), "Điện thoại", 40, false);
        DataRules.text(r.getString("note"), "Ghi chú", 2000, false);
        Tenant t =
            new Tenant(
                r.getLong("id"),
                r.getString("name"),
                r.getString("phone"),
                r.getString("note"),
                r.getBoolean("active"));
        tenants.put(t.id(), t);
      }
    }
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM contracts")) {
      while (r.next()) {
        Room room = rooms.get(r.getLong("room_id"));
        Tenant tenant = tenants.get(r.getLong("tenant_id"));
        if (room == null || tenant == null)
          throw new IllegalArgumentException("Hợp đồng mất phòng/người thuê.");
        Contract k =
            new Contract(
                r.getLong("id"),
                room.id(),
                room.name(),
                tenant.id(),
                tenant.name(),
                r.getString("start_date"),
                r.getString("end_date"),
                r.getString("closed_date"),
                d(r, "rent"),
                d(r, "deposit"),
                r.getInt("due_day"),
                RentalStore.charges(c, "contract_charges", "contract_id", r.getLong("id")));
        if (RentalService.date(k.endDate()).isBefore(RentalService.date(k.startDate()))
            || RentalService.date(k.effectiveEnd()).isBefore(RentalService.date(k.startDate()))
            || RentalService.date(k.effectiveEnd()).isAfter(RentalService.date(k.endDate()))
            || k.dueDay() < 1
            || k.dueDay() > 28)
          throw new IllegalArgumentException("Ngày/hạn thu hợp đồng không hợp lệ: " + k.id());
        RentalService.requireMoney(k.rent(), "Tiền thuê");
        RentalService.requireMoney(k.deposit(), "Tiền cọc");
        if (k.fees().size() > 20) throw new IllegalArgumentException("Hợp đồng quá nhiều phí.");
        Set<String> names = new HashSet<>();
        for (Charge fee : k.fees()) {
          DataRules.name(fee.label(), "Tên phí", 100);
          RentalService.requireMoney(fee.amount(), "Phí");
          if (!names.add(fee.label().trim().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Phí trùng.");
        }
        boolean cancelled = r.getString("cancelled_at") != null;
        if (cancelled) {
          java.time.Instant.parse(r.getString("cancelled_at"));
          DataRules.text(r.getString("cancelled_reason"), "Lý do hủy hợp đồng", 2000, true);
          cancelledContracts.add(k.id());
        }
        for (Contract old : contracts.values()) {
          if (cancelled || cancelledContracts.contains(old.id())) continue;
          if (old.roomId() == k.roomId()
              && k.startDate().substring(0, 7).compareTo(old.effectiveEnd().substring(0, 7)) <= 0
              && old.startDate().substring(0, 7).compareTo(k.effectiveEnd().substring(0, 7)) <= 0)
            throw new IllegalArgumentException(
                "Hợp đồng trùng phòng/kỳ: " + old.id() + " và " + k.id());
          if (old.tenantId() == k.tenantId()
              && k.startDate().compareTo(old.effectiveEnd()) <= 0
              && old.startDate().compareTo(k.effectiveEnd()) <= 0)
            throw new IllegalArgumentException("Người thuê trùng khoảng hợp đồng.");
        }
        contracts.put(k.id(), k);
      }
    }
    Map<Long, Bill> bills = new HashMap<>();
    Set<Long> voided = new HashSet<>();
    Set<String> periods = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT *,length(e_photo) AS e_size,length(w_photo) AS w_size FROM bills ORDER BY"
                    + " room_id,month,id")) {
      while (r.next()) {
        if (!rooms.containsKey(r.getLong("room_id")))
          throw new IllegalArgumentException("Hóa đơn mất phòng.");
        flag(r.getInt("paid"));
        Bill b =
            new Bill(
                r.getLong("id"),
                r.getLong("room_id"),
                r.getString("room_name"),
                r.getString("month"),
                d(r, "e_old"),
                d(r, "e_new"),
                d(r, "w_old"),
                d(r, "w_new"),
                r.getString("water_mode"),
                d(r, "water_used"),
                d(r, "e_rate"),
                d(r, "w_rate"),
                d(r, "total"),
                r.getBoolean("paid"),
                r.getString("note"),
                r.getObject("contract_id") == null ? null : r.getLong("contract_id"),
                r.getString("tenant_name"),
                d(r, "rent"),
                RentalStore.charges(c, "bill_charges", "bill_id", r.getLong("id")),
                r.getString("due_date"));
        try {
          DataRules.bill(b);
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException("Hóa đơn #" + b.id() + ": " + ex.getMessage(), ex);
        }
        if (b.contractId() != null) {
          Contract k = contracts.get(b.contractId());
          if (k == null
              || k.roomId() != b.roomId()
              || b.month().compareTo(k.startDate().substring(0, 7)) < 0
              || b.month()
                      .compareTo(
                          (r.getString("voided_at") != null ? k.endDate() : k.effectiveEnd())
                              .substring(0, 7))
                  > 0
              || cancelledContracts.contains(k.id()))
            throw new IllegalArgumentException("Hóa đơn không khớp hợp đồng: " + b.id());
          if (b.dueDate().isBlank())
            throw new IllegalArgumentException("Hóa đơn thuê thiếu hạn thu.");
        }
        if (r.getString("voided_at") != null) {
          java.time.Instant.parse(r.getString("voided_at"));
          DataRules.text(r.getString("void_reason"), "Lý do hủy", 2000, true);
          voided.add(b.id());
        } else if (!periods.add(b.roomId() + ":" + b.month()))
          throw new IllegalArgumentException("Hóa đơn trùng phòng/kỳ.");
        if (r.getLong("e_size") > 12_000_000 || r.getLong("w_size") > 12_000_000)
          throw new IllegalArgumentException("Ảnh vượt giới hạn.");
        bills.put(b.id(), b);
      }
    }
    Map<Long, Payment> payments = new HashMap<>();
    Map<Long, BigDecimal> received = new HashMap<>();
    Set<String> requests = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM payments")) {
      while (r.next()) {
        long billId = r.getLong("bill_id");
        if (!bills.containsKey(billId)) throw new IllegalArgumentException("Lần thu mất hóa đơn.");
        BigDecimal amount = d(r, "amount");
        positive(amount, "Số thu");
        String date = r.getString("received_date");
        if (!date.isBlank()) DataRules.actualDate(date);
        DataRules.text(r.getString("note"), "Ghi chú thu", 2000, false);
        if (r.getString("request_key") != null && !requests.add(r.getString("request_key")))
          throw new IllegalArgumentException("Mã thu trùng.");
        Payment payment = new Payment(r.getLong("id"), billId, amount, date, r.getString("note"));
        payments.put(payment.id(), payment);
        received.merge(billId, amount, BigDecimal::add);
      }
    }
    Set<Long> reversed = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM payment_reversals")) {
      while (r.next()) {
        long id = r.getLong("payment_id");
        Payment original = payments.get(id);
        if (original == null || !reversed.add(id))
          throw new IllegalArgumentException("Đảo thu mất gốc hoặc trùng.");
        java.time.Instant at = java.time.Instant.parse(r.getString("reversed_at"));
        if (!original.date().isBlank()
            && at.atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .isBefore(RentalService.date(original.date())))
          throw new IllegalArgumentException("Đảo trước ngày thu.");
        DataRules.text(r.getString("reason"), "Lý do đảo", 2000, true);
        received.merge(original.billId(), original.amount().negate(), BigDecimal::add);
      }
    }
    for (Bill b : bills.values()) {
      BigDecimal collected = received.getOrDefault(b.id(), BigDecimal.ZERO);
      if (collected.signum() < 0
          || collected.compareTo(b.total()) > 0
          || (voided.contains(b.id()) && collected.signum() != 0)
          || b.paid() != (collected.compareTo(b.total()) >= 0))
        throw new IllegalArgumentException("Số thu/trạng thái hóa đơn không khớp: " + b.id());
    }
    Map<Long, BigDecimal> held = new HashMap<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM deposits ORDER BY entry_date,id")) {
      while (r.next()) {
        long contractId = r.getLong("contract_id");
        Contract k = contracts.get(contractId);
        if (k == null || cancelledContracts.contains(contractId))
          throw new IllegalArgumentException("Cọc mất hợp đồng hoặc hợp đồng đã hủy.");
        BigDecimal amount = d(r, "amount");
        positive(amount, "Số cọc");
        DataRules.actualDate(r.getString("entry_date"));
        DataRules.text(r.getString("note"), "Ghi chú cọc", 2000, false);
        String kind = r.getString("kind");
        if (!Set.of("RECEIVE", "REFUND").contains(kind))
          throw new IllegalArgumentException("Loại cọc sai.");
        BigDecimal balance =
            held.getOrDefault(contractId, BigDecimal.ZERO)
                .add(kind.equals("RECEIVE") ? amount : amount.negate());
        if (balance.signum() < 0 || balance.compareTo(k.deposit()) > 0)
          throw new IllegalArgumentException("Sổ cọc âm/vượt hạn mức: " + contractId);
        held.put(contractId, balance);
      }
    }
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery("SELECT * FROM water_transitions ORDER BY room_id,effective_month")) {
      while (r.next()) {
        if (!rooms.containsKey(r.getLong("room_id")))
          throw new IllegalArgumentException("Chuyển nước mất phòng.");
        DataRules.period(r.getString("effective_month"));
        DataRules.mode(r.getString("old_mode"));
        DataRules.mode(r.getString("new_mode"));
        if (r.getString("old_mode").equals(r.getString("new_mode")))
          throw new IllegalArgumentException("Chuyển nước không đổi mode.");
        DataRules.quantity(d(r, "opening_reading"), "Nước bàn giao");
        if (r.getString("fixed_water") != null)
          DataRules.quantity(d(r, "fixed_water"), "Nước khoán");
        DataRules.text(r.getString("reason"), "Lý do chuyển", 2000, true);
        java.time.Instant.parse(r.getString("created_at"));
      }
    }
    Set<Long> expenseIds = new HashSet<>();
    Map<Long, String> expenseDates = new HashMap<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM expenses")) {
      while (r.next()) {
        if (r.getObject("room_id") != null && !rooms.containsKey(r.getLong("room_id")))
          throw new IllegalArgumentException("Khoản chi mất phòng.");
        DataRules.name(r.getString("room_name"), "Phòng chi", 80);
        DataRules.name(r.getString("payee"), "Người nhận", 120);
        positive(d(r, "amount_vnd"), "Tiền chi");
        DataRules.actualDate(r.getString("paid_date"));
        DataRules.text(r.getString("note"), "Ghi chú chi", 2000, false);
        if (!ExpenseStore.CATEGORIES.containsKey(r.getString("category"))
            || !Set.of("CASH", "BANK", "OTHER").contains(r.getString("method")))
          throw new IllegalArgumentException("Loại chi/phương thức sai.");
        expenseIds.add(r.getLong("id"));
        expenseDates.put(r.getLong("id"), r.getString("paid_date"));
      }
    }
    Set<Long> reversedExpenses = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM expense_reversals")) {
      while (r.next()) {
        long id = r.getLong("expense_id");
        if (!expenseIds.contains(id) || !reversedExpenses.add(id))
          throw new IllegalArgumentException("Đảo chi mất gốc hoặc trùng.");
        if (java.time.Instant.parse(r.getString("reversed_at"))
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .isBefore(RentalService.date(expenseDates.get(id))))
          throw new IllegalArgumentException("Đảo chi trước ngày chi.");
        DataRules.text(r.getString("reason"), "Lý do đảo chi", 2000, true);
      }
    }
    // Do not rely on foreign_key_check if a forged schema simply omitted the foreign keys.
    for (String query :
        List.of(
            "SELECT 1 FROM contract_charges x LEFT JOIN contracts p ON p.id=x.contract_id WHERE"
                + " p.id IS NULL LIMIT 1",
            "SELECT 1 FROM bill_charges x LEFT JOIN bills p ON p.id=x.bill_id WHERE p.id IS NULL"
                + " LIMIT 1"))
      try (Statement s = c.createStatement();
          ResultSet r = s.executeQuery(query)) {
        if (r.next()) throw new IllegalArgumentException("Khoản phí mất chứng từ gốc.");
      }
  }
}
