package vn.nhatrohomu.persistence.sqlite;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import vn.nhatrohomu.billing.ServiceCatalog;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.ServiceItem;

public final class ServiceCatalogStore implements ServiceCatalog {
  private final Path database;

  public ServiceCatalogStore(Path database) {
    this.database = database;
  }

  public List<ServiceItem> items() throws Exception {
    return items(true);
  }

  public List<ServiceItem> archivedItems() throws Exception {
    return items(false);
  }

  private List<ServiceItem> items(boolean active) throws Exception {
    List<ServiceItem> result = new ArrayList<>();
    try (Connection c = DatabaseAccess.open(database);
        Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT * FROM service_catalog WHERE active="
                    + (active ? 1 : 0)
                    + " ORDER BY name,id")) {
      while (r.next())
        result.add(
            new ServiceItem(
                r.getLong("id"),
                r.getString("name"),
                new java.math.BigDecimal(r.getString("amount"))));
    }
    return result;
  }

  public void save(ServiceItem item) throws Exception {
    DataRules.name(item.name(), "Tên khoản phí", 100);
    DataRules.money(item.amount(), "Giá phí");
    if (item.id() < 0) throw new IllegalArgumentException("Mã khoản phí không hợp lệ.");
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try (PreparedStatement p =
          c.prepareStatement(
              item.id() == 0
                  ? "INSERT INTO service_catalog(name,amount) VALUES(?,?)"
                  : "UPDATE service_catalog SET name=?,amount=? WHERE id=? AND active=1")) {
        p.setString(1, item.name().trim());
        p.setString(2, item.amount().toPlainString());
        if (item.id() != 0) p.setLong(3, item.id());
        if (p.executeUpdate() != 1)
          throw new IllegalArgumentException("Khoản phí không tồn tại hoặc đã lưu trữ.");
        AuditLog.record(c, "SAVE", "service", item.id(), item.name() + " · " + item.amount());
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }

  public void deleteUnused(long id) throws Exception {
    try (Connection c = DatabaseAccess.open(database);
        PreparedStatement p =
            c.prepareStatement(
                "DELETE FROM service_catalog WHERE id=? AND NOT EXISTS(SELECT 1 FROM"
                    + " contract_charges WHERE service_id=?) AND NOT EXISTS(SELECT 1 FROM"
                    + " bill_charges WHERE service_id=?)")) {
      for (int i = 1; i <= 3; i++) p.setLong(i, id);
      if (p.executeUpdate() != 1)
        throw new IllegalArgumentException(
            "Phí đã được dùng hoặc không còn tồn tại. Nếu muốn bỏ phí khỏi các hóa đơn chưa lập,"
                + " chọn Lưu trữ phí.");
    }
  }

  public int archive(long id) throws Exception {
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try (PreparedStatement archive =
              c.prepareStatement("UPDATE service_catalog SET active=0 WHERE id=?");
          PreparedStatement detach =
              c.prepareStatement(
                  "DELETE FROM contract_charges WHERE service_id=? AND contract_id IN (SELECT id"
                      + " FROM contracts WHERE cancelled_at IS NULL AND closed_date='' AND"
                      + " (end_date='' OR end_date>=?))")) {
        archive.setLong(1, id);
        if (archive.executeUpdate() != 1)
          throw new IllegalArgumentException("Khoản phí không tồn tại.");
        detach.setLong(1, id);
        detach.setString(2, java.time.LocalDate.now().toString());
        int count = detach.executeUpdate();
        AuditLog.record(
            c,
            "STOP_BILLING",
            "service",
            id,
            "Ngừng tính trên " + count + " lượt thuê đang mở; hóa đơn đã lập giữ nguyên.");
        c.commit();
        return count;
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }
}
