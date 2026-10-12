package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

public final class HistoricalInvoiceTest {
  private static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("historical-invoice-test-");
    try {
      SqliteRepository repo = new SqliteRepository(dir.resolve("test.db"));
      repo.saveRoom(
          new Room(0, "Imported room", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
      Bill bill =
          new Bill(
              0,
              1,
              "Imported room",
              "2026-01",
              BigDecimal.ZERO,
              BigDecimal.ONE,
              BigDecimal.ZERO,
              new BigDecimal("2"),
              "METER",
              new BigDecimal("2"),
              new BigDecimal("10"),
              new BigDecimal("3000"),
              new BigDecimal("110"),
              true,
              "Synthetic legacy record",
              null,
              "",
              new BigDecimal("100"),
              List.of(),
              "",
              BigDecimal.ZERO);
      DataRules.bill(bill, true);
      check(
          bill.waterCost().signum() == 0 && bill.waterRate().intValue() == 3000,
          "Original rate and water amount preserved");
      try {
        DataRules.bill(bill);
        throw new AssertionError("Ordinary invoice accepted historical override");
      } catch (IllegalArgumentException expected) {
        checks++;
      }
      try (Connection c = DatabaseAccess.open(repo.database);
          Statement s = c.createStatement()) {
        s.execute(
            "INSERT INTO"
                + " bills(room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,rent,water_amount)"
                + " VALUES(1,'Imported"
                + " room','2026-01','0','1','0','2','METER','2','10','3000','110',1,'Synthetic"
                + " legacy record','100','0')");
        s.execute(
            "INSERT INTO payments(bill_id,amount,received_date,note) VALUES(1,'110','','Paid status"
                + " confirmed; date unknown')");
        try {
          DatabaseValidator.validate(c);
          throw new AssertionError("Missing import provenance accepted");
        } catch (IllegalArgumentException expected) {
          checks++;
        }
        AuditLog.record(c, "IMPORT", "bill", 1, "Synthetic source reference");
        DatabaseValidator.validate(c);
        checks++;
        try {
          s.execute("UPDATE bills SET water_amount='10' WHERE id=1");
          throw new AssertionError("Imported amount rewritten");
        } catch (SQLException expected) {
          checks++;
        }
      }
      Path backup = dir.resolve("backup.db");
      repo.backup(backup);
      repo.restore(backup);
      Bill read = repo.bills(1, 0).getFirst();
      check(
          read.waterCost().signum() == 0
              && read.rent().intValue() == 100
              && repo.outstandingBills().isEmpty(),
          "Imported amount and paid balance round trip");
      System.out.println(
          "PASS: " + checks + " historical invoice, source guard and backup checks.");
    } finally {
      try (var files = Files.walk(dir)) {
        for (Path f : files.sorted(java.util.Comparator.reverseOrder()).toList())
          Files.deleteIfExists(f);
      }
    }
  }
}
