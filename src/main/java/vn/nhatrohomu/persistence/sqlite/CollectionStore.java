package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import vn.nhatrohomu.billing.CollectionRepository;
import vn.nhatrohomu.shared.DataRules;

/** Cash, allocation of earlier advances, and surplus are posted in one transaction. */
public final class CollectionStore implements CollectionRepository {
  private final Path database;

  public CollectionStore(Path database) {
    this.database = database;
  }

  record Owner(long room, Long tenant, String month) {}

  static Owner owner(Connection c, long bill) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT b.room_id,b.month,k.tenant_id FROM bills b LEFT JOIN contracts k ON"
                + " k.id=b.contract_id WHERE b.id=?")) {
      p.setLong(1, bill);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Hóa đơn không tồn tại.");
        return new Owner(
            r.getLong(1), r.getObject(3) == null ? null : r.getLong(3), r.getString(2));
      }
    }
  }

  static BigDecimal balance(Connection c, long advance) throws SQLException {
    BigDecimal total;
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT amount FROM advance_receipts WHERE id=? AND NOT EXISTS(SELECT 1 FROM"
                + " advance_reversals r WHERE r.advance_id=advance_receipts.id)")) {
      p.setLong(1, advance);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) return BigDecimal.ZERO;
        total = new BigDecimal(r.getString(1));
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT amount FROM payments WHERE advance_id=? AND NOT EXISTS(SELECT 1 FROM"
                + " payment_reversals r WHERE r.payment_id=payments.id)")) {
      p.setLong(1, advance);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) total = total.subtract(new BigDecimal(r.getString(1)));
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT amount FROM advance_refunds WHERE advance_id=? AND NOT EXISTS(SELECT 1 FROM"
                + " advance_refund_reversals r WHERE r.refund_id=advance_refunds.id)")) {
      p.setLong(1, advance);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) total = total.subtract(new BigDecimal(r.getString(1)));
      }
    }
    if (total.signum() < 0)
      throw new IllegalArgumentException("Sổ trả trước bị âm; cần kiểm tra trước khi thu.");
    return total;
  }

  public List<Advance> available(long bill) throws Exception {
    return available(bill, null);
  }

  public List<Advance> available(long bill, Long tenant) throws Exception {
    try (Connection c = DatabaseAccess.open(database)) {
      return available(c, bill, tenant);
    }
  }

  static Owner resolveOwner(Connection c, long bill, Long tenant) throws SQLException {
    Owner original = owner(c, bill);
    if (original.tenant() != null && tenant != null && !original.tenant().equals(tenant))
      throw new IllegalArgumentException("Không chuyển tiền của người thuê này sang người khác.");
    Long selected = original.tenant() != null ? original.tenant() : tenant;
    if (selected != null)
      try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM tenants WHERE id=?")) {
        p.setLong(1, selected);
        try (ResultSet r = p.executeQuery()) {
          if (!r.next())
            throw new IllegalArgumentException("Hồ sơ người có tiền trả trước không tồn tại.");
        }
      }
    return new Owner(original.room(), selected, original.month());
  }

  static List<Advance> available(Connection c, long bill, Long tenantOwner) throws SQLException {
    Owner target = resolveOwner(c, bill, tenantOwner);
    List<Advance> result = new ArrayList<>();
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT a.*,b.month,t.name owner_name FROM advance_receipts a JOIN bills b ON"
                + " b.id=a.bill_id LEFT JOIN tenants t ON t.id=a.owner_tenant_id WHERE b.month<?"
                + " AND NOT EXISTS(SELECT 1 FROM advance_reversals r WHERE r.advance_id=a.id) ORDER"
                + " BY b.month,a.received_date,a.id")) {
      p.setString(1, target.month());
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          Long tenant =
              r.getObject("owner_tenant_id") == null ? null : r.getLong("owner_tenant_id");
          Owner source = owner(c, r.getLong("bill_id"));
          if (target.tenant() != null
              ? !target.tenant().equals(tenant)
              : tenant != null || source.room() != target.room()) continue;
          BigDecimal balance = balance(c, r.getLong("id"));
          if (balance.signum() > 0)
            result.add(
                new Advance(
                    r.getLong("id"),
                    r.getString("month"),
                    r.getString("received_date"),
                    new BigDecimal(r.getString("amount")),
                    balance,
                    r.getString("note"),
                    tenant,
                    r.getString("owner_name") == null ? "" : r.getString("owner_name")));
        }
      }
    }
    return List.copyOf(result);
  }

  public List<Advance> issued(long bill) throws Exception {
    List<Advance> result = new ArrayList<>();
    try (Connection c = DatabaseAccess.open(database);
        PreparedStatement p =
            c.prepareStatement(
                "SELECT a.*,b.month,t.name owner_name FROM advance_receipts a JOIN bills b ON"
                    + " b.id=a.bill_id LEFT JOIN tenants t ON t.id=a.owner_tenant_id WHERE"
                    + " a.bill_id=? AND NOT EXISTS(SELECT 1 FROM advance_reversals r WHERE"
                    + " r.advance_id=a.id) ORDER BY a.id")) {
      p.setLong(1, bill);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          result.add(
              new Advance(
                  r.getLong("id"),
                  r.getString("month"),
                  r.getString("received_date"),
                  new BigDecimal(r.getString("amount")),
                  balance(c, r.getLong("id")),
                  r.getString("note"),
                  r.getObject("owner_tenant_id") == null ? null : r.getLong("owner_tenant_id"),
                  r.getString("owner_name") == null ? "" : r.getString("owner_name")));
      }
    }
    return result;
  }

  public Outcome collect(
      long bill, BigDecimal cash, String date, String note, boolean useAdvance, String requestKey)
      throws Exception {
    return collect(bill, cash, date, note, useAdvance, requestKey, null);
  }

  public Outcome collect(
      long bill,
      BigDecimal cash,
      String date,
      String note,
      boolean useAdvance,
      String requestKey,
      Long tenantOwner)
      throws Exception {
    DataRules.money(cash, "Tiền thực nhận");
    DataRules.actualDate(date);
    DataRules.text(note, "Ghi chú", 2000, false);
    DataRules.text(requestKey, "Mã lần thu", 100, true);
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
            c.prepareStatement("UPDATE bills SET paid=paid WHERE id=? AND voided_at IS NULL")) {
          lock.setLong(1, bill);
          if (lock.executeUpdate() != 1)
            throw new IllegalArgumentException("Hóa đơn không tồn tại hoặc đã hủy.");
        }
        Owner target = resolveOwner(c, bill, tenantOwner);
        try (PreparedStatement p =
            c.prepareStatement("SELECT * FROM collection_events WHERE request_key=?")) {
          p.setString(1, requestKey);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              if (r.getLong("bill_id") != bill
                  || new BigDecimal(r.getString("cash")).compareTo(cash) != 0
                  || !r.getString("received_date").equals(date)
                  || !r.getString("note").equals(note)
                  || r.getBoolean("use_advance") != useAdvance
                  || !Objects.equals(
                      r.getObject("owner_tenant_id") == null ? null : r.getLong("owner_tenant_id"),
                      target.tenant()))
                throw new IllegalArgumentException("Mã lần thu đã dùng cho nội dung khác.");
              Outcome old =
                  new Outcome(
                      new BigDecimal(r.getString("used_advance")),
                      new BigDecimal(r.getString("applied_cash")),
                      new BigDecimal(r.getString("saved_advance")));
              c.commit();
              return old;
            }
          }
        }
        BigDecimal total;
        try (PreparedStatement p = c.prepareStatement("SELECT total FROM bills WHERE id=?")) {
          p.setLong(1, bill);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            total = new BigDecimal(r.getString(1));
          }
        }
        BigDecimal remaining = total.subtract(received(c, bill)), used = BigDecimal.ZERO;
        if (remaining.signum() < 0)
          throw new IllegalArgumentException(
              "Hóa đơn đã ghi thanh toán vượt tổng; cần kiểm tra sổ.");
        List<Advance> available = useAdvance ? available(c, bill, target.tenant()) : List.of();
        Map<Long, BigDecimal> allocation = new LinkedHashMap<>();
        for (Advance source : available) {
          BigDecimal take = source.available().min(remaining.subtract(used));
          if (take.signum() <= 0) break;
          if (date.compareTo(source.date()) < 0)
            throw new IllegalArgumentException(
                "Ngày sử dụng tiền trả trước không được trước ngày nhận " + source.date());
          allocation.put(source.id(), take);
          used = used.add(take);
        }
        BigDecimal applied = cash.min(remaining.subtract(used)), surplus = cash.subtract(applied);
        if (cash.signum() == 0 && used.signum() == 0)
          throw new IllegalArgumentException("Nhập tiền thực nhận hoặc chọn dùng số dư trả trước.");
        long event;
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO"
                    + " collection_events(bill_id,cash,received_date,note,use_advance,request_key,used_advance,applied_cash,saved_advance,created_at,owner_tenant_id)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
          p.setLong(1, bill);
          p.setString(2, cash.toPlainString());
          p.setString(3, date);
          p.setString(4, note);
          p.setBoolean(5, useAdvance);
          p.setString(6, requestKey);
          p.setString(7, used.toPlainString());
          p.setString(8, applied.toPlainString());
          p.setString(9, surplus.toPlainString());
          p.setString(10, Instant.now().toString());
          if (target.tenant() == null) p.setNull(11, Types.INTEGER);
          else p.setLong(11, target.tenant());
          p.executeUpdate();
          try (ResultSet keys = p.getGeneratedKeys()) {
            keys.next();
            event = keys.getLong(1);
          }
        }
        for (Advance source : available)
          if (allocation.containsKey(source.id()))
            payment(
                c,
                bill,
                allocation.get(source.id()),
                date,
                "Tiền trả trước từ tháng " + source.month() + " (nhận " + source.date() + ")",
                event,
                source.id());
        if (applied.signum() > 0) payment(c, bill, applied, date, note, event, null);
        if (surplus.signum() > 0) {
          try (PreparedStatement p =
              c.prepareStatement(
                  "INSERT INTO"
                      + " advance_receipts(collection_id,bill_id,amount,received_date,note,created_at,owner_tenant_id)"
                      + " VALUES(?,?,?,?,?,?,?)")) {
            p.setLong(1, event);
            p.setLong(2, bill);
            p.setString(3, surplus.toPlainString());
            p.setString(4, date);
            p.setString(5, note);
            p.setString(6, Instant.now().toString());
            if (target.tenant() == null) p.setNull(7, Types.INTEGER);
            else p.setLong(7, target.tenant());
            p.executeUpdate();
          }
        }
        SqliteRepository.updatePaid(c, bill);
        AuditLog.record(
            c,
            "COLLECT",
            "bill",
            bill,
            "Thực nhận " + cash + "; dùng trả trước " + used + "; giữ cho kỳ sau " + surplus);
        c.commit();
        return new Outcome(used, applied, surplus);
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  static BigDecimal received(Connection c, long bill) throws SQLException {
    BigDecimal amount = BigDecimal.ZERO;
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT amount FROM payments WHERE bill_id=? AND NOT EXISTS(SELECT 1 FROM"
                + " payment_reversals r WHERE r.payment_id=payments.id)")) {
      p.setLong(1, bill);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) amount = amount.add(new BigDecimal(r.getString(1)));
      }
    }
    return amount;
  }

  static void payment(
      Connection c,
      long bill,
      BigDecimal amount,
      String date,
      String note,
      long collection,
      Long advance)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO"
                + " payments(bill_id,amount,received_date,note,created_at,collection_id,advance_id)"
                + " VALUES(?,?,?,?,?,?,?)")) {
      p.setLong(1, bill);
      p.setString(2, amount.toPlainString());
      p.setString(3, date);
      p.setString(4, note);
      p.setString(5, Instant.now().toString());
      p.setLong(6, collection);
      if (advance == null) p.setNull(7, Types.INTEGER);
      else p.setLong(7, advance);
      p.executeUpdate();
    }
  }

  static void reverseAssociated(Connection c, long payment, String reason) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT a.id FROM advance_receipts a JOIN payments p ON p.collection_id=a.collection_id"
                + " WHERE p.id=? AND p.advance_id IS NULL AND NOT EXISTS(SELECT 1 FROM"
                + " advance_reversals r WHERE r.advance_id=a.id)")) {
      p.setLong(1, payment);
      try (ResultSet r = p.executeQuery()) {
        if (r.next()) reverse(c, r.getLong(1), reason);
      }
    }
  }

  static void reverse(Connection c, long id, String reason) throws SQLException {
    BigDecimal original;
    try (PreparedStatement p =
        c.prepareStatement("SELECT amount FROM advance_receipts WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Khoản trả trước không tồn tại.");
        original = new BigDecimal(r.getString(1));
      }
    }
    if (balance(c, id).compareTo(original) != 0)
      throw new IllegalArgumentException(
          "Tiền trả trước đã được dùng hoặc hoàn. Sửa các khoản sử dụng/hoàn nhập nhầm trước khi"
              + " sửa tiền nhận gốc.");
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO advance_reversals(advance_id,reversed_at,reason) VALUES(?,?,?)")) {
      p.setLong(1, id);
      p.setString(2, Instant.now().toString());
      p.setString(3, reason);
      p.executeUpdate();
    }
    AuditLog.record(c, "REVERSE", "advance", id, reason);
  }

  public void reverseAdvance(long id, String reason) throws Exception {
    DataRules.text(reason, "Lý do sửa", 2000, true);
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE bills SET paid=paid WHERE id=(SELECT bill_id FROM advance_receipts WHERE"
                    + " id=?)")) {
          p.setLong(1, id);
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException("Khoản trả trước không tồn tại.");
        }
        reverse(c, id, reason);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public List<Refund> refunds(long bill) throws Exception {
    List<Refund> result = new ArrayList<>();
    try (Connection c = DatabaseAccess.open(database);
        PreparedStatement p =
            c.prepareStatement(
                "SELECT f.* FROM advance_refunds f JOIN advance_receipts a ON a.id=f.advance_id"
                    + " WHERE a.bill_id=? AND NOT EXISTS(SELECT 1 FROM advance_refund_reversals r"
                    + " WHERE r.refund_id=f.id) ORDER BY f.id")) {
      p.setLong(1, bill);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          result.add(
              new Refund(
                  r.getLong("id"),
                  r.getLong("advance_id"),
                  new BigDecimal(r.getString("amount")),
                  r.getString("paid_date"),
                  r.getString("note")));
      }
    }
    return result;
  }

  public void refund(long advance, BigDecimal amount, String date, String note, String key)
      throws Exception {
    DataRules.money(amount, "Tiền đã hoàn");
    if (amount.signum() <= 0) throw new IllegalArgumentException("Tiền hoàn cần lớn hơn 0.");
    DataRules.actualDate(date);
    DataRules.text(note, "Ghi chú hoàn", 2000, false);
    DataRules.text(key, "Mã lần hoàn", 100, true);
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE bills SET paid=paid WHERE id=(SELECT bill_id FROM advance_receipts WHERE"
                    + " id=?)")) {
          p.setLong(1, advance);
          if (p.executeUpdate() != 1)
            throw new IllegalArgumentException("Khoản trả trước không tồn tại.");
        }
        try (PreparedStatement p =
            c.prepareStatement("SELECT * FROM advance_refunds WHERE request_key=?")) {
          p.setString(1, key);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              if (r.getLong("advance_id") != advance
                  || new BigDecimal(r.getString("amount")).compareTo(amount) != 0
                  || !r.getString("paid_date").equals(date)
                  || !r.getString("note").equals(note))
                throw new IllegalArgumentException("Mã lần hoàn đã dùng cho nội dung khác.");
              c.commit();
              return;
            }
          }
        }
        if (amount.compareTo(balance(c, advance)) > 0)
          throw new IllegalArgumentException("Không hoàn vượt số dư trả trước còn lại.");
        try (PreparedStatement p =
            c.prepareStatement("SELECT received_date FROM advance_receipts WHERE id=?")) {
          p.setLong(1, advance);
          try (ResultSet r = p.executeQuery()) {
            r.next();
            if (date.compareTo(r.getString(1)) < 0)
              throw new IllegalArgumentException("Ngày hoàn không được trước ngày nhận tiền.");
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO"
                    + " advance_refunds(advance_id,amount,paid_date,note,request_key,created_at)"
                    + " VALUES(?,?,?,?,?,?)")) {
          p.setLong(1, advance);
          p.setString(2, amount.toPlainString());
          p.setString(3, date);
          p.setString(4, note);
          p.setString(5, key);
          p.setString(6, Instant.now().toString());
          p.executeUpdate();
        }
        AuditLog.record(
            c, "REFUND", "advance", advance, "Đã hoàn thực tế " + amount + " ngày " + date);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void reverseRefund(long id, String reason) throws Exception {
    DataRules.text(reason, "Lý do sửa hoàn tiền", 2000, true);
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement p =
            c.prepareStatement(
                "UPDATE bills SET paid=paid WHERE id=(SELECT a.bill_id FROM advance_receipts a JOIN"
                    + " advance_refunds f ON f.advance_id=a.id WHERE f.id=?)")) {
          p.setLong(1, id);
          if (p.executeUpdate() != 1) throw new IllegalArgumentException("Lần hoàn không tồn tại.");
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO advance_refund_reversals(refund_id,reversed_at,reason)"
                    + " VALUES(?,?,?)")) {
          p.setLong(1, id);
          p.setString(2, Instant.now().toString());
          p.setString(3, reason);
          p.executeUpdate();
        }
        AuditLog.record(c, "REVERSE", "advance_refund", id, reason);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }
}
