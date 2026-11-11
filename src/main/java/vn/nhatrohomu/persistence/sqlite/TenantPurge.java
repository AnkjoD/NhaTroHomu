package vn.nhatrohomu.persistence.sqlite;

import java.sql.*;
import java.time.LocalDate;
import java.util.Map;

/** Explicit permanent removal; ordinary writes still cannot delete financial history. */
final class TenantPurge {
  private TenantPurge() {}

  static void execute(Connection c, long tenant) throws SQLException {
    c.setAutoCommit(false);
    try (Statement s = c.createStatement()) {
      // Acquire the writer lock before checking for dependencies.
      try (PreparedStatement p =
          c.prepareStatement("UPDATE tenants SET active=active WHERE id=?")) {
        p.setLong(1, tenant);
        if (p.executeUpdate() != 1)
          throw new IllegalArgumentException("Không tìm thấy người thuê.");
      }
      s.execute("CREATE TEMP TABLE purge_tenant(id INTEGER PRIMARY KEY)");
      try (PreparedStatement p = c.prepareStatement("INSERT INTO purge_tenant VALUES(?)")) {
        p.setLong(1, tenant);
        p.executeUpdate();
      }
      s.execute(
          "CREATE TEMP TABLE purge_contract AS SELECT * FROM contracts WHERE tenant_id IN (SELECT"
              + " id FROM purge_tenant)");
      s.execute(
          "CREATE TEMP TABLE purge_bill AS SELECT id FROM bills WHERE contract_id IN (SELECT id"
              + " FROM purge_contract)");
      try (PreparedStatement p =
          c.prepareStatement(
              "SELECT 1 FROM purge_contract WHERE cancelled_at IS NULL AND"
                  + " coalesce(nullif(closed_date,''),nullif(end_date,''),'9999-12-31')>=? AND"
                  + " (closed_date='' OR closed_date>?)")) {
        String today = LocalDate.now().toString();
        p.setString(1, today);
        p.setString(2, today);
        try (ResultSet r = p.executeQuery()) {
          if (r.next())
            throw new IllegalArgumentException(
                "Cần trả phòng hoặc hủy lượt thuê nhập nhầm trước khi xóa vĩnh viễn.");
        }
      }
      reject(
          s,
          "SELECT 1 FROM purge_contract k JOIN bills b ON b.room_id=k.room_id AND"
              + " b.month>=substr(k.start_date,1,7) WHERE k.cancelled_at IS NULL AND b.id NOT IN"
              + " (SELECT id FROM purge_bill)",
          "Có hóa đơn khác dùng lịch sử phòng này. Xóa sẽ làm đứt chuỗi chỉ số; hãy lưu trữ.");
      reject(
          s,
          "SELECT 1 FROM purge_contract k JOIN contracts next ON next.room_id=k.room_id AND"
              + " next.start_date>=k.start_date WHERE k.cancelled_at IS NULL AND next.cancelled_at"
              + " IS NULL AND next.id NOT IN (SELECT id FROM purge_contract)",
          "Có lượt thuê khác nối tiếp hoặc dùng cùng mốc phòng này. Hãy lưu trữ.");
      reject(
          s,
          "SELECT 1 FROM purge_contract k JOIN water_transitions w ON w.room_id=k.room_id AND"
              + " w.effective_month>=substr(k.start_date,1,7) WHERE k.cancelled_at IS NULL",
          "Có mốc đổi cách tính nước liên quan. Hãy lưu trữ để giữ mốc chỉ số.");
      reject(
          s,
          "SELECT 1 FROM bills removed JOIN bills remaining ON remaining.room_id=removed.room_id"
              + " WHERE removed.id IN (SELECT id FROM purge_bill) AND removed.voided_at IS NULL AND"
              + " remaining.voided_at IS NULL AND remaining.id NOT IN (SELECT id FROM purge_bill)",
          "Phòng còn hóa đơn của người khác. Xóa sẽ làm thay đổi mốc tính điện nước; hãy lưu trữ.");
      reject(
          s,
          "SELECT 1 FROM bills b JOIN water_transitions w ON w.room_id=b.room_id WHERE b.id IN"
              + " (SELECT id FROM purge_bill) AND b.voided_at IS NULL",
          "Phòng từng đổi cách tính nước. Hãy lưu trữ để giữ mốc chỉ số.");
      s.execute(
          "CREATE TEMP TABLE purge_collection AS SELECT * FROM collection_events WHERE bill_id IN"
              + " (SELECT id FROM purge_bill)");
      s.execute(
          "CREATE TEMP TABLE purge_advance AS SELECT * FROM advance_receipts WHERE bill_id IN"
              + " (SELECT id FROM purge_bill)");
      s.execute(
          "CREATE TEMP TABLE purge_payment AS SELECT * FROM payments WHERE bill_id IN (SELECT id"
              + " FROM purge_bill)");
      reject(
          s,
          "SELECT 1 FROM collection_events WHERE (owner_tenant_id IN (SELECT id FROM purge_tenant)"
              + " AND id NOT IN (SELECT id FROM purge_collection)) OR (id IN (SELECT id FROM"
              + " purge_collection) AND owner_tenant_id IS NOT NULL AND owner_tenant_id NOT IN"
              + " (SELECT id FROM purge_tenant))",
          "Có khoản thu liên kết với người hoặc hóa đơn khác. Hãy lưu trữ.");
      reject(
          s,
          "SELECT 1 FROM payments WHERE (advance_id IN (SELECT id FROM purge_advance) AND id NOT IN"
              + " (SELECT id FROM purge_payment)) OR (id IN (SELECT id FROM purge_payment) AND"
              + " advance_id IS NOT NULL AND advance_id NOT IN (SELECT id FROM purge_advance))",
          "Có tiền trả trước được dùng chung với hóa đơn khác. Hãy lưu trữ.");

      // SQLite DDL rolls back together with the deletes if any check or write fails.
      Map<String, String> triggers = Schema.triggers();
      for (String name : triggers.keySet())
        if (name.endsWith("_no_delete")) s.execute("DROP TRIGGER " + name);
      // Keep the last meter readings as room settings, with no link to the deleted person.
      s.execute(
          "UPDATE rooms SET (e_start,w_start)=(SELECT e_new,w_new FROM bills b WHERE"
              + " b.room_id=rooms.id AND b.voided_at IS NULL AND b.id IN (SELECT id FROM"
              + " purge_bill) ORDER BY b.month DESC,b.period_end DESC,b.id DESC LIMIT 1) WHERE id"
              + " IN (SELECT room_id FROM bills WHERE id IN (SELECT id FROM purge_bill) AND"
              + " voided_at IS NULL)");
      s.execute(
          "DELETE FROM audit_events WHERE (entity_type='tenant' AND entity_id IN (SELECT id FROM"
              + " purge_tenant)) OR (entity_type IN ('contract','deposit') AND entity_id IN (SELECT"
              + " id FROM purge_contract)) OR (entity_type='bill' AND entity_id IN (SELECT id FROM"
              + " purge_bill)) OR (entity_type='payment' AND entity_id IN (SELECT id FROM"
              + " purge_payment)) OR (entity_type='advance' AND entity_id IN (SELECT id FROM"
              + " purge_advance)) OR (entity_type='advance_refund' AND entity_id IN (SELECT id FROM"
              + " advance_refunds WHERE advance_id IN (SELECT id FROM purge_advance)))");
      s.execute("DELETE FROM payment_reversals WHERE payment_id IN (SELECT id FROM purge_payment)");
      s.execute("DELETE FROM payments WHERE id IN (SELECT id FROM purge_payment)");
      s.execute(
          "DELETE FROM advance_refund_reversals WHERE refund_id IN (SELECT id FROM advance_refunds"
              + " WHERE advance_id IN (SELECT id FROM purge_advance))");
      s.execute("DELETE FROM advance_refunds WHERE advance_id IN (SELECT id FROM purge_advance)");
      s.execute("DELETE FROM advance_reversals WHERE advance_id IN (SELECT id FROM purge_advance)");
      s.execute("DELETE FROM advance_receipts WHERE id IN (SELECT id FROM purge_advance)");
      s.execute("DELETE FROM collection_events WHERE id IN (SELECT id FROM purge_collection)");
      s.execute("DELETE FROM bill_charges WHERE bill_id IN (SELECT id FROM purge_bill)");
      s.execute("DELETE FROM bills WHERE id IN (SELECT id FROM purge_bill)");
      s.execute("DELETE FROM deposits WHERE contract_id IN (SELECT id FROM purge_contract)");
      s.execute(
          "DELETE FROM contract_charges WHERE contract_id IN (SELECT id FROM purge_contract)");
      s.execute("DELETE FROM handover_events WHERE contract_id IN (SELECT id FROM purge_contract)");
      s.execute("DELETE FROM contracts WHERE id IN (SELECT id FROM purge_contract)");
      s.execute("DELETE FROM residents WHERE tenant_id IN (SELECT id FROM purge_tenant)");
      s.execute("DELETE FROM tenants WHERE id IN (SELECT id FROM purge_tenant)");
      for (var trigger : triggers.entrySet())
        if (trigger.getKey().endsWith("_no_delete")) s.execute(trigger.getValue());
      DatabaseValidator.validate(c);
      c.commit();
    } catch (SQLException | RuntimeException ex) {
      c.rollback();
      throw ex;
    }
  }

  private static void reject(Statement s, String query, String message) throws SQLException {
    try (ResultSet rows = s.executeQuery(query + " LIMIT 1")) {
      if (rows.next()) throw new IllegalArgumentException(message);
    }
  }
}
