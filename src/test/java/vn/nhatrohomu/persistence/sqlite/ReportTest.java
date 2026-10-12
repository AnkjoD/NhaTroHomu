package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.reporting.ExpenseStore;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.shared.CsvField;
import vn.nhatrohomu.shared.Models.*;

/** Compare invoice periods, receipt dates, real obligations and paid expenses. */
public final class ReportTest {
  static int checks;

  static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  static void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
    checks++;
  }

  static void rejects(SafetyTest.Task task, String label) throws Exception {
    try {
      task.run();
    } catch (IllegalArgumentException | SQLException ex) {
      checks++;
      return;
    }
    throw new AssertionError(label);
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("nhatrohomu-reports-");
    try {
      SqliteRepository repo = SafetyTest.book(root, "reports", "METER");
      Room room = repo.rooms().getFirst();
      repo.saveRoom(new Room(0, "Vacant", "", d("0"), d("0"), "METER", null, true));
      Room vacant = repo.rooms().getLast();
      YearMonth previous = YearMonth.now().minusMonths(1), current = YearMonth.now();
      Contract contract = SafetyTest.contract(repo, previous);
      repo.saveBill(
          RentalService.withRental(
              SafetyTest.bill(repo, previous.toString(), "110", "101"), contract, contract.rent()),
          null,
          null);
      Bill invoice = repo.bills(1, 0).getFirst();
      repo.addPayment(invoice.id(), d("10000"), LocalDate.now().toString(), "Thu tháng trước");
      ReportStore reports = new ReportStore(repo.database);
      ReportStore.Snapshot snapshot = reports.load(current, null);
      check(
          snapshot.collectedInRange().compareTo(d("10000")) == 0,
          "receipt counted by date, not invoice month");
      check(
          reports.load(previous, null).collectedInRange().signum() == 0,
          "prior invoice period does not invent cash received then");
      check(
          snapshot.movements().getFirst().period().equals(previous.toString()),
          "movement retains original invoice period");
      check(
          snapshot.debts().getFirst().remaining().compareTo(d("2040000")) == 0,
          "all-period current debt includes partial old invoice");
      check(
          snapshot.aging().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(d("2040000"))
              == 0,
          "aging sum reconciles debt with known due dates");
      check(
          snapshot.missing().size() == 1
              && snapshot.missing().getFirst().period().equals(current.toString()),
          "missing obligation is current contracted period only");
      check(
          reports.load(current, vacant.id()).missing().isEmpty(),
          "vacant room does not create missing rent invoice");
      repo.rentals()
          .addDeposit(contract.id(), "RECEIVE", d("200000"), LocalDate.now().toString(), "Cọc");
      check(
          reports.load(current, null).collectedInRange().compareTo(d("10000")) == 0,
          "deposit excluded from rent receipts");
      ExpenseStore expenses = new ExpenseStore(repo.database);
      expenses.save(
          room.id(),
          "MAINTENANCE",
          "Thợ sửa",
          d("200000"),
          LocalDate.now().toString(),
          "BANK",
          "Sửa khóa",
          "expense-key");
      expenses.save(
          room.id(),
          "MAINTENANCE",
          "Thợ sửa",
          d("200000"),
          LocalDate.now().toString(),
          "BANK",
          "Sửa khóa",
          "expense-key");
      snapshot = reports.load(current, null);
      check(
          snapshot.expenses().size() == 1 && snapshot.expensesInRange().compareTo(d("200000")) == 0,
          "expense retry idempotent");
      check(
          snapshot.collectedInRange().subtract(snapshot.expensesInRange()).compareTo(d("-190000"))
              == 0,
          "cash receipts less paid expenses, deposit excluded");
      check(
          reports.load(current, vacant.id()).expenses().isEmpty(),
          "room filter does not include other room expenses");
      rejects(
          () ->
              expenses.save(
                  room.id(),
                  "OTHER",
                  "Thợ sửa",
                  d("200001"),
                  LocalDate.now().toString(),
                  "BANK",
                  "Sửa khóa",
                  "expense-key"),
          "changed expense payload cannot reuse key");
      rejects(
          () ->
              expenses.save(
                  999L,
                  "OTHER",
                  "Thợ sửa",
                  d("1"),
                  LocalDate.now().toString(),
                  "BANK",
                  "",
                  "bad-room"),
          "expense room must exist");
      rejects(
          () ->
              expenses.save(
                  null,
                  "OTHER",
                  "Thợ sửa",
                  d("1"),
                  LocalDate.now().plusDays(1).toString(),
                  "BANK",
                  "",
                  "future-expense"),
          "future actual expense rejected");
      rejects(
          () ->
              expenses.save(
                  null,
                  "OTHER",
                  "Thợ sửa",
                  d("0.5"),
                  LocalDate.now().toString(),
                  "BANK",
                  "",
                  "fractional-expense"),
          "fractional expense rejected");
      long id = snapshot.expenses().getFirst().id();
      expenses.reverse(id, "Nhập nhầm");
      snapshot = reports.load(current, null);
      check(
          snapshot.expenses().getFirst().reversed() && snapshot.expensesInRange().signum() == 0,
          "reversal retains expense and correct net");
      rejects(() -> expenses.reverse(id, "Lần hai"), "expense cannot reverse twice");
      rejects(
          () -> SafetyTest.execute(repo.database, "DELETE FROM expenses"),
          "paid expense deletion blocked");
      check(
          CsvField.escape("  =1+1\nsecond line").startsWith("\"'"),
          "CSV formula after whitespace and newline neutralized");
      check(
          CsvField.escape("Nguyễn \"Ánh\"").equals("\"Nguyễn \"\"Ánh\"\"\""),
          "CSV quoting preserves Vietnamese and quotes");
      Path backup = root.resolve("backup.db");
      repo.backup(backup);
      expenses.save(
          null,
          "POWER",
          "Nhà cung cấp",
          d("100000"),
          LocalDate.now().toString(),
          "BANK",
          "",
          "whole-house");
      repo.restore(backup);
      check(
          reports.load(current, null).expenses().size() == 1,
          "backup restores expenses and reversals together");
      try (Connection c = DatabaseAccess.open(repo.database)) {
        DatabaseValidator.validate(c);
      }
      check(
          SafetyTest.count(repo.database, "audit_events") > 0, "business changes visible in audit");
      System.out.println("PASS: " + checks + " report, expense and export checks.");
    } finally {
      try (var files = Files.list(root)) {
        for (Path f : files.toList()) Files.deleteIfExists(f);
      }
      Files.delete(root);
    }
  }
}
