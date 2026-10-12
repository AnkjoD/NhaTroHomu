package vn.nhatrohomu.persistence.sqlite;

import java.sql.*;
import java.time.Instant;

public final class AuditLog {
  private AuditLog() {}

  public static void record(Connection c, String action, String type, long id, String detail)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO audit_events(occurred_at,action,entity_type,entity_id,detail)"
                + " VALUES(?,?,?,?,?)")) {
      p.setString(1, Instant.now().toString());
      p.setString(2, action);
      p.setString(3, type);
      p.setLong(4, id);
      p.setString(5, detail);
      p.executeUpdate();
    }
  }
}
