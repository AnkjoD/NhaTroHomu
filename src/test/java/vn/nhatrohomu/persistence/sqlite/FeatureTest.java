package vn.nhatrohomu.persistence.sqlite;

import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.InvoicePdf;
import vn.nhatrohomu.shared.Models.*;

public final class FeatureTest {
  static int checks;

  static void check(boolean result, String name) {
    if (!result) throw new AssertionError(name);
    checks++;
  }

  interface Task {
    void run() throws Exception;
  }

  static void rejects(Task task, String name) throws Exception {
    try {
      task.run();
    } catch (IllegalArgumentException e) {
      checks++;
      return;
    }
    throw new AssertionError(name);
  }

  public static void main(String[] args) throws Exception {
    Path temp = Files.createTempDirectory("nhatrohomu-features-");
    try {
      SqliteRepository repo = new SqliteRepository(temp.resolve("live.db"));
      repo.saveRoom(
          new Room(
              0, "Phòng Nguyễn Ánh", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
      Room room = repo.rooms().getFirst();
      repo.saveBill(
          BillingService.calculate(
              room,
              "2026-10",
              repo.previous(room),
              new BigDecimal("10"),
              new BigDecimal("2"),
              BigDecimal.ZERO,
              repo.rates(),
              "Kiểm tra tiếng Việt"),
          null,
          null);
      Bill bill = repo.bills(1, 0).getFirst();
      repo.addPayment(
          bill.id(),
          new BigDecimal("10000"),
          java.time.LocalDate.now().minusDays(1).toString(),
          "Chuyển khoản");
      check(repo.received(bill.id()).equals(new BigDecimal("10000")), "partial received exact");
      check(!repo.bills(1, 0).getFirst().paid(), "partial remains unpaid");
      rejects(() -> repo.deleteLatest(bill), "partial blocks cancel");
      rejects(
          () ->
              repo.addPayment(
                  bill.id(), bill.total(), java.time.LocalDate.now().minusDays(1).toString(), ""),
          "overpayment rejected");
      rejects(
          () ->
              repo.addPayment(
                  bill.id(),
                  new BigDecimal("0"),
                  java.time.LocalDate.now().minusDays(1).toString(),
                  ""),
          "zero payment rejected");
      rejects(
          () ->
              repo.addPayment(
                  bill.id(),
                  new BigDecimal("0.5"),
                  java.time.LocalDate.now().minusDays(1).toString(),
                  ""),
          "fractional VND rejected");
      repo.addPayment(
          bill.id(),
          bill.total().subtract(repo.received(bill.id())),
          java.time.LocalDate.now().toString(),
          "Tiền mặt");
      check(repo.bills(1, 0).getFirst().paid(), "settled after multiple receipts");
      check(
          repo.billsBetween("2026-09", "2026-10").size() == 1
              && repo.billsBetween("2026-11", "2026-12").isEmpty(),
          "dashboard period filter");
      Path pdf = temp.resolve("invoice.pdf");
      InvoicePdf.write(pdf, bill, repo.payments(bill.id()));
      try (PdfReader reader = new PdfReader(Files.readAllBytes(pdf))) {
        check(reader.getNumberOfPages() == 1, "invoice one page");
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        check(
            text.contains("Phòng Nguyễn Ánh")
                && text.contains("Còn thiếu")
                && !text.contains("LỊCH SỬ THU TIỀN")
                && !text.contains("Chuyển khoản"),
            "PDF embedded Vietnamese font/text");
      }
      repo.backup(temp.resolve("backup.db"));
      repo.deletePayment(repo.payments(bill.id()).getFirst().id());
      check(!repo.bills(1, 0).getFirst().paid(), "deleting receipt updates paid");
      repo.restore(temp.resolve("backup.db"));
      check(
          repo.payments(bill.id()).size() == 2 && repo.bills(1, 0).getFirst().paid(),
          "restore payment ledger");
      // Reconstruct the prior schema from a real database, including a paid invoice.
      Path legacy = temp.resolve("legacy.db");
      repo.backup(legacy);
      try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + legacy);
          Statement s = c.createStatement()) {
        s.execute("DROP TABLE payments");
        removeRentalSchema(s);
        s.execute("PRAGMA user_version=1");
      }
      SqliteRepository upgraded = new SqliteRepository(legacy);
      check(
          upgraded.received(bill.id()).compareTo(bill.total()) == 0,
          "migration preserves paid total");
      check(
          upgraded.payments(bill.id()).getFirst().date().isEmpty(),
          "migration does not invent payment date");
      Path oldBackup = temp.resolve("old-backup.db");
      upgraded.backup(oldBackup);
      try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + oldBackup);
          Statement s = c.createStatement()) {
        s.execute("DROP TABLE payments");
        removeRentalSchema(s);
        s.execute("PRAGMA user_version=1");
      }
      repo.restore(oldBackup);
      check(repo.received(bill.id()).compareTo(bill.total()) == 0, "restore upgrades version1");
      if (args.length > 0) InvoicePdf.write(Path.of(args[0]), bill, repo.payments(bill.id()));
      System.out.println("PASS: " + checks + " payment, migration, dashboard and PDF checks.");
    } finally {
      try (var files = Files.list(temp)) {
        for (Path file : files.toList()) Files.deleteIfExists(file);
      }
      Files.delete(temp);
    }
  }

  static void removeRentalSchema(Statement s) throws Exception {
    removeHardeningSchema(s);
    for (String table : List.of("bill_charges", "deposits", "contract_charges"))
      s.execute("DROP TABLE " + table);
    for (String column : List.of("contract_id", "tenant_name", "rent", "due_date"))
      s.execute("ALTER TABLE bills DROP COLUMN " + column);
    s.execute("DROP TABLE contracts");
    s.execute("DROP TABLE residents");
    s.execute("DROP TABLE tenants");
  }

  static void removeHardeningSchema(Statement s) throws Exception {
    s.execute("ALTER TABLE rooms DROP COLUMN default_rent");
    for (String name : Schema.triggers().keySet()) s.execute("DROP TRIGGER IF EXISTS " + name);
    for (String name :
        List.of(
            "payment_reversals",
            "water_transitions",
            "audit_events",
            "expense_reversals",
            "expenses",
            "handover_events")) s.execute("DROP TABLE IF EXISTS " + name);
    s.execute("DROP INDEX IF EXISTS payments_request");
    try (ResultSet r =
        s.executeQuery("SELECT 1 FROM sqlite_schema WHERE type='table' AND name='payments'")) {
      if (r.next()) {
        s.execute("ALTER TABLE payments DROP COLUMN created_at");
        s.execute("ALTER TABLE payments DROP COLUMN request_key");
      }
    }
    s.execute(
        "CREATE TABLE bills_old(id INTEGER PRIMARY KEY,room_id INTEGER NOT NULL REFERENCES"
            + " rooms(id),room_name TEXT NOT NULL,month TEXT NOT NULL,e_old TEXT NOT NULL,e_new"
            + " TEXT NOT NULL,w_old TEXT NOT NULL,w_new TEXT NOT NULL,water_mode TEXT NOT"
            + " NULL,water_used TEXT NOT NULL,e_rate TEXT NOT NULL,w_rate TEXT NOT NULL,total TEXT"
            + " NOT NULL,paid INTEGER NOT NULL DEFAULT 0,note TEXT NOT NULL,e_photo BLOB,w_photo"
            + " BLOB,contract_id INTEGER REFERENCES contracts(id),tenant_name TEXT NOT NULL DEFAULT"
            + " '',rent TEXT NOT NULL DEFAULT '0',due_date TEXT NOT NULL DEFAULT"
            + " '',UNIQUE(room_id,month))");
    s.execute(
        "INSERT INTO bills_old("
            + Schema.BILL_COLUMNS
            + ") SELECT "
            + Schema.BILL_COLUMNS
            + " FROM bills WHERE voided_at IS NULL");
    s.execute("DROP TABLE bills");
    s.execute("ALTER TABLE bills_old RENAME TO bills");
  }
}
