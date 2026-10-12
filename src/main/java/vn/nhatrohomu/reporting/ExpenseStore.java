package vn.nhatrohomu.reporting;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.persistence.sqlite.AuditLog;
import vn.nhatrohomu.persistence.sqlite.DatabaseAccess;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;

/** Paid expenses only, not unpaid supplier invoices or accounting profit. */
public final class ExpenseStore {
  public static final Map<String, String> CATEGORIES =
      Map.of(
          "POWER",
          "Điện đầu vào",
          "WATER",
          "Nước đầu vào",
          "MAINTENANCE",
          "Sửa chữa",
          "CLEANING",
          "Vệ sinh",
          "OTHER",
          "Khác");

  public record Entry(
      long id,
      Long roomId,
      String room,
      String category,
      String payee,
      BigDecimal amount,
      String date,
      String method,
      String note,
      boolean reversed,
      String reversalDate,
      String reason) {}

  public record Summary(List<Entry> entries, BigDecimal netPaid) {}

  private final Path database;

  public ExpenseStore(Path database) {
    this.database = database;
  }

  public void save(
      Long roomId,
      String category,
      String payee,
      BigDecimal amount,
      String date,
      String method,
      String note,
      String key)
      throws SQLException {
    RentalService.requireMoney(amount, "Số tiền chi");
    if (amount.signum() == 0) throw new IllegalArgumentException("Số tiền chi phải lớn hơn 0.");
    DataRules.actualDate(date);
    DataRules.name(payee, "Người nhận tiền", 120);
    DataRules.text(note, "Ghi chú", 2000, false);
    DataRules.text(key, "Mã thao tác", 100, true);
    if (!CATEGORIES.containsKey(category) || !Set.of("CASH", "BANK", "OTHER").contains(method))
      throw new IllegalArgumentException("Loại chi/phương thức không hợp lệ.");
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (Statement lock = c.createStatement()) {
          lock.executeUpdate("UPDATE rates SET id=id WHERE id=1");
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT * FROM expenses WHERE request_key=?")) {
          p.setString(1, key);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              Long oldRoom = r.getObject("room_id") == null ? null : r.getLong("room_id");
              if (!Objects.equals(oldRoom, roomId)
                  || !r.getString("category").equals(category)
                  || !r.getString("payee").equals(payee.trim())
                  || new BigDecimal(r.getString("amount_vnd")).compareTo(amount) != 0
                  || !r.getString("paid_date").equals(date)
                  || !r.getString("method").equals(method)
                  || !r.getString("note").equals(note.trim()))
                throw new IllegalArgumentException("Mã thao tác đã dùng cho khoản chi khác.");
              c.commit();
              return;
            }
          }
        }
        String roomName = "Toàn nhà";
        if (roomId != null)
          try (PreparedStatement p = c.prepareStatement("SELECT name FROM rooms WHERE id=?")) {
            p.setLong(1, roomId);
            try (ResultSet r = p.executeQuery()) {
              if (!r.next()) throw new IllegalArgumentException("Phòng chi không tồn tại.");
              roomName = r.getString(1);
            }
          }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO"
                    + " expenses(room_id,room_name,category,payee,amount_vnd,paid_date,method,note,request_key,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
          if (roomId == null) p.setNull(1, Types.INTEGER);
          else p.setLong(1, roomId);
          p.setString(2, roomName);
          p.setString(3, category);
          p.setString(4, payee.trim());
          p.setLong(5, amount.longValueExact());
          p.setString(6, date);
          p.setString(7, method);
          p.setString(8, note.trim());
          p.setString(9, key);
          p.setString(10, Instant.now().toString());
          p.executeUpdate();
          try (ResultSet r = p.getGeneratedKeys()) {
            r.next();
            AuditLog.record(
                c,
                "PAY_EXPENSE",
                "expense",
                r.getLong(1),
                roomName + "; " + category + "; " + amount + "; " + date + "; " + payee);
          }
        }
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void reverse(long id, String reason) throws SQLException {
    DataRules.text(reason, "Lý do đảo chi", 2000, true);
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (Statement lock = c.createStatement()) {
          lock.executeUpdate("UPDATE rates SET id=id WHERE id=1");
        }
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM expenses WHERE id=?")) {
          p.setLong(1, id);
          try (ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("Khoản chi không tồn tại.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO expense_reversals(expense_id,reversed_at,reason) VALUES(?,?,?)")) {
          p.setLong(1, id);
          p.setString(2, Instant.now().toString());
          p.setString(3, reason.trim());
          p.executeUpdate();
        }
        AuditLog.record(c, "REVERSE_EXPENSE", "expense", id, reason);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  static Summary load(Connection c, YearMonth period, Long roomFilter) throws SQLException {
    List<Entry> entries = new ArrayList<>();
    BigDecimal net = BigDecimal.ZERO;
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT e.*,r.reversed_at,r.reason FROM expenses e LEFT JOIN expense_reversals r ON"
                    + " r.expense_id=e.id ORDER BY e.paid_date,e.id")) {
      while (r.next()) {
        Long room = r.getObject("room_id") == null ? null : r.getLong("room_id");
        if (roomFilter != null && !roomFilter.equals(room)) continue;
        BigDecimal amount = BigDecimal.valueOf(r.getLong("amount_vnd"));
        String date = r.getString("paid_date"), reversal = r.getString("reversed_at");
        String reversalDate =
            reversal == null
                ? null
                : Instant.parse(reversal).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        boolean inPeriod = YearMonth.from(RentalService.date(date)).equals(period),
            reversedInPeriod =
                reversalDate != null
                    && YearMonth.from(RentalService.date(reversalDate)).equals(period);
        if (inPeriod) net = net.add(amount);
        if (reversedInPeriod) net = net.subtract(amount);
        if (inPeriod || reversedInPeriod)
          entries.add(
              new Entry(
                  r.getLong("id"),
                  room,
                  r.getString("room_name"),
                  r.getString("category"),
                  r.getString("payee"),
                  amount,
                  date,
                  r.getString("method"),
                  r.getString("note"),
                  reversal != null,
                  reversalDate,
                  r.getString("reason")));
      }
    }
    return new Summary(List.copyOf(entries), net);
  }
}
