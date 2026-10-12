package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.nhatrohomu.app.ProfileDirectory;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.*;

/** Regression and abuse cases use throwaway books. Never uses the real profile. */
public final class SafetyTest {
  static int checks;

  static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  static void check(boolean ok, String reason) {
    if (!ok) throw new AssertionError(reason);
    checks++;
  }

  interface Task {
    void run() throws Exception;
  }

  static void rejects(Task task, String label) throws Exception {
    try {
      task.run();
    } catch (IllegalArgumentException | SQLException expected) {
      checks++;
      return;
    }
    throw new AssertionError(label);
  }

  static SqliteRepository book(Path root, String name, String mode) throws Exception {
    SqliteRepository r = new SqliteRepository(root.resolve(name + ".db"));
    r.saveRoom(
        new Room(
            0, "P01", "", d("100"), d("100"), mode, mode.equals("FIXED") ? d("3") : null, true));
    return r;
  }

  static Bill bill(SqliteRepository r, String month, String e, String w) throws Exception {
    Room room = r.rooms().getFirst();
    return BillingService.calculate(
        room, month, r.previous(room), d(e), d(w), d("3"), r.rates(), "");
  }

  static Contract contract(SqliteRepository r, YearMonth from) throws Exception {
    r.rentals().saveTenant(new Tenant(0, "Demo", "", "", true));
    Room room = r.rooms().getFirst();
    Tenant t = r.rentals().tenants().getFirst();
    r.rentals()
        .createContract(
            new Contract(
                0,
                room.id(),
                room.name(),
                t.id(),
                t.name(),
                from.atDay(1).toString(),
                from.plusYears(2).atEndOfMonth().toString(),
                "",
                d("2000000"),
                d("1000000"),
                5,
                List.of()));
    return r.rentals().contracts().getFirst();
  }

  static void execute(Path file, String sql) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  static long count(Path file, String table) throws Exception {
    try (Connection c = DatabaseAccess.open(file);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
      r.next();
      return r.getLong(1);
    }
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("nhatrohomu-safety-");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      YearMonth first = YearMonth.now().minusMonths(2);
      Path profileRoot = root.resolve("profiles");
      Files.createDirectories(profileRoot);
      check(
          ProfileDirectory.resolve(profileRoot, false, null)
              .equals(profileRoot.resolve("NhaTroHomu")),
          "fresh profile uses new product name");
      Path legacy = profileRoot.resolve("SoTro");
      Files.createDirectories(legacy);
      Files.write(legacy.resolve("sotro.db"), new byte[0]);
      check(
          ProfileDirectory.resolve(profileRoot, false, null).equals(legacy),
          "existing legacy book reused without copying data");
      check(
          ProfileDirectory.database(legacy).equals(legacy.resolve("sotro.db")),
          "legacy file name preserved");
      check(
          !ProfileDirectory.resolve(profileRoot, true, null).equals(legacy),
          "demo never selects real legacy book");
      Files.delete(legacy.resolve("sotro.db"));
      Files.delete(legacy);
      Files.delete(profileRoot);
      SqliteRepository scheduled = book(root, "scheduled", "METER");
      Contract k = contract(scheduled, first);
      scheduled.rentals().closeContract(k.id(), LocalDate.now().plusMonths(2).toString());
      rejects(() -> scheduled.archiveRoom(k.roomId()), "B01 future closure cannot archive room");
      rejects(
          () -> scheduled.rentals().archiveTenant(k.tenantId()),
          "B01 future closure cannot archive tenant");

      SqliteRepository cancellations = book(root, "cancellations", "METER");
      Contract old = contract(cancellations, first);
      cancellations.rentals().cancelUnusedContract(old.id());
      check(
          cancellations.rentals().contracts().isEmpty()
              && count(cancellations.database, "contracts") == 1,
          "cancelled contract retained but not active");
      check(
          new ReportStore(cancellations.database).load(YearMonth.now(), null).missing().isEmpty(),
          "cancelled contract creates no missing rent obligations");
      rejects(
          () -> cancellations.rentals().cancelUnusedContract(old.id()),
          "cannot cancel contract twice");
      Contract replacement = contract(cancellations, first);
      check(replacement.id() > old.id(), "contract ID not reused after cancellation");

      SqliteRepository gaps = book(root, "gaps", "METER");
      Contract g = contract(gaps, first);
      rejects(
          () ->
              gaps.saveBill(
                  RentalService.withRental(
                      bill(gaps, first.plusMonths(1).toString(), "110", "101"), g, g.rent()),
                  null,
                  null),
          "B02 cannot skip first contract month");
      gaps.saveBill(
          RentalService.withRental(bill(gaps, first.toString(), "110", "101"), g, g.rent()),
          null,
          null);
      rejects(
          () ->
              gaps.saveBill(
                  RentalService.withRental(
                      bill(gaps, first.plusMonths(2).toString(), "130", "103"), g, g.rent()),
                  null,
                  null),
          "B02 cannot skip middle month");
      check(gaps.billCount() == 1, "failed issue rolled back");

      gaps.saveBill(
          RentalService.withRental(
              bill(gaps, first.plusMonths(1).toString(), "120", "102"), g, g.rent()),
          null,
          null);
      gaps.deleteLatest(gaps.bills(1, 0).getFirst());
      gaps.rentals().closeContract(g.id(), first.atEndOfMonth().toString());
      try (Connection c = DatabaseAccess.open(gaps.database)) {
        DatabaseValidator.validate(c);
      }
      check(
          gaps.billCount() == 1,
          "voided later invoice remains valid after earlier contract closure");

      SqliteRepository stale = book(root, "stale", "METER");
      Room sr = stale.rooms().getFirst();
      Bill before = bill(stale, first.toString(), "110", "101");
      stale.saveRoom(new Room(sr.id(), sr.name(), "", d("105"), d("100"), "METER", null, true));
      rejects(() -> stale.saveBill(before, null, null), "B03 first opening rechecked");
      rejects(
          () ->
              stale.saveBill(
                  new Bill(
                      0,
                      sr.id(),
                      sr.name(),
                      first.toString(),
                      d("105"),
                      d("110"),
                      d("100"),
                      d("101"),
                      "METER",
                      d("1"),
                      d("3500"),
                      d("15000"),
                      d("1"),
                      false,
                      ""),
                  null,
                  null),
          "B04 forged total rejected");
      rejects(
          () ->
              stale.saveBill(
                  new Bill(
                      0,
                      sr.id(),
                      sr.name(),
                      first.toString(),
                      d("105"),
                      d("110"),
                      d("100"),
                      d("101"),
                      "METER",
                      d("99"),
                      d("3500"),
                      d("15000"),
                      d("1502500"),
                      false,
                      ""),
                  null,
                  null),
          "forged water usage rejected");
      rejects(
          () ->
              stale.saveRoom(
                  new Room(sr.id(), sr.name(), "", d("-1"), d("100"), "METER", null, true)),
          "negative opening rejected at boundary");
      rejects(
          () -> stale.saveRates(new Rates(d("-1"), d("1"), d("3"))),
          "negative tariff rejected at boundary");
      rejects(
          () ->
              stale.saveRoom(
                  new Room(
                      sr.id(), "<html><img src=x>", "", d("105"), d("100"), "METER", null, true)),
          "Swing HTML injection in name rejected");
      rejects(() -> DataRules.period("+10000-01"), "extended period cannot break lexical ordering");
      rejects(
          () ->
              stale.saveRoom(new Room(999, "NoSuchRoom", "", d("0"), d("0"), "METER", null, true)),
          "missing room update cannot report success");

      SqliteRepository water = book(root, "water", "FIXED");
      Room wr = water.rooms().getFirst();
      water.saveBill(bill(water, first.toString(), "110", "120"), null, null);
      rejects(
          () ->
              water.saveRoom(
                  new Room(wr.id(), wr.name(), "", d("100"), d("100"), "METER", null, true)),
          "B05 direct mode change blocked");
      water.changeWaterMode(wr.id(), "METER", d("120"), null, "Bàn giao đầu kỳ");
      rejects(
          () -> water.changeWaterMode(wr.id(), "FIXED", d("120"), d("3"), "Đổi lại trước khi chốt"),
          "pending boundary cannot be overwritten");
      Bill next = bill(water, first.plusMonths(1).toString(), "120", "125");
      check(next.waterUsed().compareTo(d("5")) == 0, "B05 correct new meter baseline");
      water.saveBill(next, null, null);
      Bill issued = water.bills(1, 0).getFirst();
      water.deleteLatest(issued);
      check(
          water.previous(water.rooms().getFirst()).water().compareTo(d("120")) == 0,
          "void respects transition opening");
      water.saveBill(next, null, null);
      check(water.bills(1, 0).getFirst().id() > issued.id(), "voided bill ID not reused");
      try (Connection c = DatabaseAccess.open(water.database)) {
        DatabaseValidator.validate(c);
      }

      SqliteRepository zero = book(root, "zero", "METER");
      zero.saveBill(bill(zero, first.toString(), "100", "100"), new byte[] {1, 2}, null);
      Bill zb = zero.bills(1, 0).getFirst();
      zero.deleteLatest(zb);
      check(
          zero.hasRoomHistory(zero.rooms().getFirst().id()),
          "voided history keeps opening readings locked");
      check(
          zero.billCount() == 0 && count(zero.database, "bills") == 1,
          "B06 zero invoice voidable and retained");
      check(Arrays.equals(zero.photo(zb.id(), true), new byte[] {1, 2}), "void keeps photo");
      rejects(
          () -> zero.addPayment(zb.id(), d("1"), LocalDate.now().toString(), ""),
          "cannot collect voided invoice");

      SqliteRepository pay = book(root, "payments", "METER");
      pay.saveBill(bill(pay, first.toString(), "110", "101"), null, null);
      Bill pb = pay.bills(1, 0).getFirst();
      String today = LocalDate.now().toString();
      pay.addPayment(pb.id(), d("10000"), today, "A", "same-key");
      pay.addPayment(pb.id(), d("10000"), today, "A", "same-key");
      check(pay.payments(pb.id()).size() == 1, "idempotent retry does not double receive");
      rejects(
          () -> pay.addPayment(pb.id(), d("10001"), today, "A", "same-key"),
          "key cannot be reused with changed amount");
      rejects(
          () -> pay.addPayment(pb.id(), d("1"), LocalDate.now().plusDays(1).toString(), ""),
          "future receipt rejected");
      long receipt = pay.payments(pb.id()).getFirst().id();
      pay.reversePayment(receipt, "Nhập nhầm");
      check(
          pay.received(pb.id()).signum() == 0
              && count(pay.database, "payments") == 1
              && count(pay.database, "payment_reversals") == 1,
          "reversal retains original and correct balance");
      rejects(() -> pay.reversePayment(receipt, "Lần hai"), "cannot reverse twice");
      rejects(() -> execute(pay.database, "DELETE FROM payments"), "SQL delete receipt blocked");
      rejects(
          () -> execute(pay.database, "UPDATE payments SET amount='1'"),
          "SQL overwrite receipt blocked");
      rejects(
          () -> execute(pay.database, "DELETE FROM audit_events"),
          "audit history deletion blocked");
      rejects(() -> execute(pay.database, "DELETE FROM bills"), "SQL delete invoice blocked");
      rejects(
          () -> execute(pay.database, "UPDATE bills SET total='1'"), "SQL rewrite total blocked");
      rejects(
          () -> execute(zero.database, "UPDATE bills SET voided_at=NULL,void_reason=NULL"),
          "cannot silently reactivate voided invoice");

      CountDownLatch start = new CountDownLatch(1);
      Callable<Boolean> collect =
          () -> {
            start.await();
            try {
              pay.addPayment(pb.id(), d("40000"), today, "Concurrent");
              return true;
            } catch (IllegalArgumentException expected) {
              return false;
            }
          };
      Future<Boolean> a = executor.submit(collect), b = executor.submit(collect);
      start.countDown();
      check(
          a.get(10, TimeUnit.SECONDS) ^ b.get(10, TimeUnit.SECONDS),
          "concurrent overpayment exactly one winner");
      check(pay.received(pb.id()).compareTo(d("40000")) == 0, "concurrent balance exact");
      try (Connection c = DatabaseAccess.open(pay.database)) {
        DatabaseValidator.validate(c);
      }

      Path valid = root.resolve("valid.db");
      pay.backup(valid);
      long live = pay.billCount();
      for (String corruption :
          List.of(
              "UPDATE bills SET total='not-money'",
              "UPDATE bills SET total='1'",
              "UPDATE payments SET amount='999999'")) {
        Path bad = root.resolve("bad-" + UUID.randomUUID() + ".db");
        Files.copy(valid, bad);
        if (corruption.contains("payments")) execute(bad, "DROP TRIGGER payments_no_update");
        if (corruption.contains("bills")) execute(bad, "DROP TRIGGER bills_no_rewrite");
        execute(bad, corruption);
        if (corruption.contains("payments"))
          execute(bad, Schema.triggers().get("payments_no_update"));
        if (corruption.contains("bills")) execute(bad, Schema.triggers().get("bills_no_rewrite"));
        rejects(() -> pay.restore(bad), "B07 malformed ledger rejected: " + corruption);
        check(
            pay.billCount() == live && pay.received(pb.id()).compareTo(d("40000")) == 0,
            "failed restore preserves live balance");
      }
      Path malicious = root.resolve("malicious.db");
      Files.copy(valid, malicious);
      execute(
          malicious,
          "CREATE TRIGGER malicious AFTER INSERT ON payments BEGIN UPDATE bills SET total='1';"
              + " END");
      rejects(() -> pay.restore(malicious), "unknown backup trigger rejected before execution");
      Path view = root.resolve("view.db");
      Files.copy(valid, view);
      execute(view, "CREATE VIEW surprising AS SELECT * FROM bills");
      rejects(() -> pay.restore(view), "unexpected backup view rejected");
      try (Connection held = DatabaseAccess.open(pay.database)) {
        CountDownLatch attempted = new CountDownLatch(1);
        Future<?> restoring =
            executor.submit(
                () -> {
                  attempted.countDown();
                  try {
                    pay.restore(valid);
                  } catch (Exception ex) {
                    throw new RuntimeException(ex);
                  }
                });
        attempted.await();
        Thread.sleep(100);
        check(!restoring.isDone(), "restore waits while another connection is open");
        held.close();
        restoring.get(10, TimeUnit.SECONDS);
      }
      check(pay.received(pb.id()).compareTo(d("40000")) == 0, "restore resumes with correct data");
      System.out.println("PASS: " + checks + " safety, regression and concurrency checks.");
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
      try (var files = Files.list(root)) {
        for (Path file : files.toList()) Files.deleteIfExists(file);
      }
      Files.delete(root);
    }
  }
}
