package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.List;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.shared.Models.*;

public final class TurnoverTest {
  static int checks;

  static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  static void check(boolean yes, String label) {
    checks++;
    if (!yes) throw new AssertionError(label);
  }

  interface Action {
    void run() throws Exception;
  }

  static void rejects(Action action, String label) throws Exception {
    try {
      action.run();
    } catch (Exception expected) {
      checks++;
      return;
    }
    throw new AssertionError(label);
  }

  static Contract draft(Room r, Tenant t, String start) {
    return new Contract(
        0,
        r.id(),
        r.name(),
        t.id(),
        t.name(),
        start,
        "2026-12-31",
        "",
        d("3000000"),
        d("2000000"),
        5,
        List.of(new Charge("Internet", d("300000"))));
  }

  static Bill bill(
      SqliteRepository repo,
      Room room,
      Contract k,
      String last,
      String electric,
      String water,
      String note)
      throws Exception {
    LocalDate end = LocalDate.parse(last);
    YearMonth month = YearMonth.from(end);
    LocalDate first =
        LocalDate.parse(k.startDate()).isAfter(month.atDay(1))
            ? LocalDate.parse(k.startDate())
            : month.atDay(1);
    Bill utility =
        BillingService.calculate(
            room,
            month.toString(),
            repo.rentals().opening(k.id(), month),
            d(electric),
            d(water),
            d("0"),
            repo.rates(),
            note);
    return RentalService.segment(
        utility,
        k,
        RentalService.prorated(k.rent(), first, end),
        k.fees().stream()
            .map(f -> new Charge(f.label(), RentalService.prorated(f.amount(), first, end)))
            .toList(),
        first,
        end);
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("turnover-test-");
    SqliteRepository repo = new SqliteRepository(dir.resolve("book.db"));
    repo.saveRoom(new Room(0, "P01", "", d("100"), d("10"), "METER", null, true));
    Room room = repo.rooms().getFirst();
    repo.rentals().saveTenant(new Tenant(0, "Người A", "", "", true));
    repo.rentals().saveTenant(new Tenant(0, "Người B", "", "", true));
    Tenant
        a =
            repo.rentals().tenants().stream()
                .filter(t -> t.name().equals("Người A"))
                .findFirst()
                .orElseThrow(),
        b =
            repo.rentals().tenants().stream()
                .filter(t -> t.name().equals("Người B"))
                .findFirst()
                .orElseThrow();
    repo.rentals().createContract(draft(room, a, "2026-09-01"), new Readings(d("100"), d("10")));
    Contract ka = repo.rentals().contracts().getFirst();
    rejects(
        () ->
            repo.saveRoom(
                new Room(
                    room.id(),
                    room.name(),
                    room.tenant(),
                    d("1"),
                    room.waterStart(),
                    room.waterMode(),
                    room.fixedWater(),
                    true)),
        "Initial meter is locked after a handover even before billing");
    repo.saveRoom(
        new Room(
            room.id(),
            room.name(),
            room.tenant(),
            room.electricityStart(),
            room.waterStart(),
            room.waterMode(),
            room.fixedWater(),
            true,
            d("4000000")));
    check(
        repo.rooms().getFirst().defaultRent().compareTo(d("4000000")) == 0
            && repo.rentals().contracts().getFirst().rent().compareTo(d("3000000")) == 0,
        "Room default changes do not alter agreed rent");
    repo.rentals().addDeposit(ka.id(), "RECEIVE", d("2000000"), "2026-09-01", "Synthetic deposit");
    Bill finalA = bill(repo, room, ka, "2026-09-10", "110", "12", "");
    Bill bad =
        RentalService.segment(
            BillingService.calculate(
                room,
                "2026-09",
                new Readings(d("100"), d("10")),
                d("110"),
                d("12"),
                d("0"),
                repo.rates(),
                ""),
            ka,
            d("1"),
            finalA.fees(),
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-09-10"));
    rejects(() -> repo.rentals().checkout(bad), "Invalid checkout rejected");
    check(
        repo.billCount() == 0 && repo.rentals().contracts().getFirst().closedDate().isBlank(),
        "Failure rolls back both invoice and contract closure");
    repo.rentals().checkout(finalA);
    check(
        repo.rentals().contracts().getFirst().closedDate().equals("2026-09-10")
            && repo.billCount() == 1,
        "Checkout records the final bill and departure together");
    check(
        finalA.rent().compareTo(d("1000000")) == 0 && finalA.feeTotal().compareTo(d("100000")) == 0,
        "Prorates 10 of 30 days with fees");
    rejects(() -> repo.rentals().checkout(finalA), "Duplicate checkout does not duplicate money");
    rejects(
        () ->
            repo.rentals()
                .createContract(draft(room, b, "2026-09-10"), new Readings(d("110"), d("12"))),
        "Inclusive departure day cannot overlap arrival");
    rejects(
        () ->
            repo.rentals()
                .createContract(draft(room, b, "2026-09-15"), new Readings(d("109"), d("12"))),
        "Incoming meter cannot go backward");
    check(
        repo.rentals().contracts().size() == 1,
        "Invalid incoming handover leaves no orphan contract");
    repo.rentals().createContract(draft(room, b, "2026-09-15"), new Readings(d("115"), d("13")));
    Contract kb =
        repo.rentals().contracts().stream()
            .filter(k -> k.tenantId() == b.id())
            .findFirst()
            .orElseThrow();
    if (!java.awt.GraphicsEnvironment.isHeadless())
      javax.swing.SwingUtilities.invokeAndWait(
          () -> {
            javax.swing.JFrame owner = new javax.swing.JFrame();
            try {
              var dialog = new vn.nhatrohomu.billing.ui.ReadingDialog(owner, repo, room, () -> {});
              try {
                var e = dialog.getClass().getDeclaredField("electricity");
                e.setAccessible(true);
                ((javax.swing.JTextField) e.get(dialog)).setText("130");
                var w = dialog.getClass().getDeclaredField("water");
                w.setAccessible(true);
                ((javax.swing.JTextField) w.get(dialog)).setText("15");
                var calculate = dialog.getClass().getDeclaredMethod("calculate");
                calculate.setAccessible(true);
                Bill preview = (Bill) calculate.invoke(dialog);
                check(
                    preview.month().equals("2026-09") && preview.contractId().equals(kb.id()),
                    "Billing form selects the unissued same-month new tenancy");
                check(
                    preview.electricityOld().compareTo(d("115")) == 0
                        && preview.rent().compareTo(d("1600000")) == 0,
                    "Billing form starts at new handover and prorates rent");
              } finally {
                dialog.dispose();
              }
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            } finally {
              owner.dispose();
            }
          });
    check(
        new ReportStore(repo.database)
            .load(YearMonth.of(2026, 9), null).missing().stream()
                .anyMatch(m -> m.contractId() == kb.id()),
        "Old tenant's invoice does not hide the new tenant's missing invoice");
    Bill fullB = bill(repo, room, kb, "2026-09-30", "130", "15", "");
    repo.saveBill(fullB, null, null);
    check(
        repo.roomBills(room.id(), "2026-09", "2026-09").size() == 2,
        "One room supports two distinct September invoices");
    check(
        fullB.electricityOld().compareTo(d("115")) == 0
            && fullB.electricityUsed().compareTo(d("15")) == 0,
        "Vacancy usage is excluded from the new tenant");
    check(fullB.rent().compareTo(d("1600000")) == 0, "New tenant pays 16 of 30 days");
    Bill
        storedA =
            repo.bills(10, 0).stream()
                .filter(v -> v.contractId().equals(ka.id()))
                .findFirst()
                .orElseThrow(),
        storedB =
            repo.bills(10, 0).stream()
                .filter(v -> v.contractId().equals(kb.id()))
                .findFirst()
                .orElseThrow();
    repo.addPayment(storedA.id(), d("100000"), "2026-09-10", "Partial A");
    repo.addPayment(storedB.id(), storedB.total(), "2026-09-30", "Full B");
    rejects(
        () -> repo.rentals().checkout(bill(repo, room, kb, "2026-09-20", "125", "14", "")),
        "Early departure cannot rewrite an already charged full-period bill");
    check(
        repo.rentals().contracts().stream()
            .filter(k -> k.id() == kb.id())
            .findFirst()
            .orElseThrow()
            .closedDate()
            .isBlank(),
        "Blocked early checkout preserves occupancy");
    check(
        repo.outstandingBills().size() == 1
            && repo.outstandingBills().getFirst().contractId().equals(ka.id()),
        "New payment cannot settle the old tenant's debt");
    check(
        repo.rentals().heldDeposit(ka.id()).compareTo(d("2000000")) == 0
            && repo.rentals().heldDeposit(kb.id()).signum() == 0,
        "Deposits stay with the original tenancy");
    rejects(() -> repo.deleteLatest(storedA), "Final handover invoice cannot be voided");
    rejects(() -> repo.saveBill(fullB, null, null), "Repeated segment is rejected");
    repo.rentals().checkout(fullB);
    check(
        repo.billCount() == 2 && repo.received(storedB.id()).compareTo(storedB.total()) == 0,
        "Checkout reuses an identical final invoice without changing receipts");
    try (Connection c = DatabaseAccess.open(repo.database)) {
      DatabaseValidator.validate(c);
    }
    check(true, "Validates split intervals, handovers, receipts and foreign keys");
    Path backup = dir.resolve("backup.db");
    repo.backup(backup);
    repo.restore(backup);
    check(
        repo.billCount() == 2 && repo.rentals().contracts().size() == 2,
        "Backup/restore preserves same-month turnover");
    try (Connection c = DatabaseAccess.open(repo.database);
        Statement s = c.createStatement()) {
      rejects(
          () -> s.execute("UPDATE handover_events SET e_reading='0'"),
          "Handover history cannot be rewritten");
    }
    check(
        RentalService.prorated(d("2900"), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
                .compareTo(d("2900"))
            == 0,
        "Leap February uses 29 days");
    repo.saveRoom(new Room(0, "P02", "", d("0"), d("0"), "FIXED", d("3"), true));
    Room fixed =
        repo.rooms().stream().filter(r -> r.name().equals("P02")).findFirst().orElseThrow();
    repo.rentals().createContract(draft(fixed, a, "2026-09-15"), new Readings(d("0"), d("0")));
    Contract fixedLease =
        repo.rentals().contracts().stream()
            .filter(k -> k.roomId() == fixed.id())
            .findFirst()
            .orElseThrow();
    Bill fixedUtility =
        BillingService.calculate(
            fixed,
            "2026-09",
            new Readings(d("0"), d("0")),
            d("0"),
            d("0"),
            d("1.6"),
            repo.rates(),
            "");
    Bill fixedBill =
        RentalService.segment(
            fixedUtility,
            fixedLease,
            d("1600000"),
            List.of(new Charge("Internet", d("160000"))),
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 9, 30));
    repo.saveBill(fixedBill, null, null);
    check(
        fixedBill.waterUsed().compareTo(d("1.6")) == 0 && fixedBill.waterNew().signum() == 0,
        "Partial fixed water does not invent a meter advance");
    repo.changeWaterMode(fixed.id(), "METER", d("100"), null, "Synthetic switch");
    check(
        repo.rentals().opening(fixedLease.id(), YearMonth.of(2026, 10)).water().compareTo(d("100"))
            == 0,
        "New tenancy respects the dated water-policy handover");
    Room metered =
        repo.rooms().stream().filter(r -> r.id() == fixed.id()).findFirst().orElseThrow();
    repo.saveBill(bill(repo, metered, fixedLease, "2026-10-31", "1", "102", ""), null, null);
    try (Connection c = DatabaseAccess.open(repo.database)) {
      DatabaseValidator.validate(c);
    }
    check(true, "Validates invoices before and after a water-policy switch");
    System.out.println(
        "PASS: " + checks + " turnover, vacancy, debt isolation and transactional checks.");
  }
}
