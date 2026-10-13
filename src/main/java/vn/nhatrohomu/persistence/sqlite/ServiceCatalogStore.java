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
    List<ServiceItem> result = new ArrayList<>();
    try (Connection c = DatabaseAccess.open(database);
        Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery("SELECT * FROM service_catalog WHERE active=1 ORDER BY name,id")) {
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

  public void archive(long id) throws Exception {
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      try (PreparedStatement p =
          c.prepareStatement("UPDATE service_catalog SET active=0 WHERE id=? AND active=1")) {
        p.setLong(1, id);
        if (p.executeUpdate() != 1) throw new IllegalArgumentException("Khoản phí không tồn tại.");
        AuditLog.record(c, "ARCHIVE", "service", id, "Giữ giá đã lưu trong hợp đồng/hóa đơn.");
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    }
  }
}
