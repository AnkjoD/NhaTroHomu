package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Models.*;

public final class DebtFlowTest {
  static int checks;

  static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static BigDecimal debt(SqliteRepository repo) throws Exception {
    BigDecimal total = BigDecimal.ZERO;
    for (Bill b : repo.outstandingBills())
      total = total.add(b.total().subtract(repo.received(b.id())));
    return total;
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("debt-flow-test-");
    try {
      SqliteRepository repo = new SqliteRepository(dir.resolve("test.db"));
      repo.saveRoom(
          new Room(0, "Debt test room", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
      Room room = repo.rooms().getFirst();
      repo.rentals().saveTenant(new Tenant(0, "Synthetic tenant", "", "", true));
      Tenant tenant = repo.rentals().tenants().getFirst();
      YearMonth month = YearMonth.now();
      repo.rentals()
          .createContract(
              new Contract(
                  0,
                  room.id(),
                  room.name(),
                  tenant.id(),
                  tenant.name(),
                  month.atDay(1).toString(),
                  month.plusMonths(3).atEndOfMonth().toString(),
                  "",
                  d("2000000"),
                  BigDecimal.ZERO,
                  5,
                  List.of()));
      for (int i = 0; i < 2; i++) {
        String period = month.plusMonths(i).toString();
        Bill calculated =
            BillingService.calculate(
                room,
                period,
                repo.previous(room),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                repo.rates(),
                "");
        repo.saveBill(
            RentalService.withRental(
                calculated,
                repo.rentals().forMonth(room.id(), YearMonth.parse(period)),
                d("2000000")),
            null,
            null);
      }
      List<Bill> bills = repo.bills(10, 0);
      Bill old =
          bills.stream().filter(b -> b.month().equals(month.toString())).findFirst().orElseThrow();
      Bill next =
          bills.stream().filter(b -> !b.month().equals(month.toString())).findFirst().orElseThrow();
      String today = LocalDate.now().toString();
      repo.addPayment(old.id(), d("1500000"), today, "Partial payment", "partial-first");
      repo.addPayment(old.id(), d("1500000"), today, "Partial payment", "partial-first");
      check(repo.received(old.id()).compareTo(d("1500000")) == 0, "Retry does not double receipt");
      check(
          old.total().subtract(repo.received(old.id())).compareTo(d("500000")) == 0,
          "Partial payment leaves exact debt");
      check(
          repo.bills(10, 0).stream().filter(b -> b.id() == old.id()).noneMatch(Bill::paid),
          "Partial invoice remains unpaid");
      check(next.total().compareTo(d("2000000")) == 0, "Old debt not added to new month's invoice");
      check(
          debt(repo).compareTo(d("2500000")) == 0,
          "Debt includes old remainder and new invoice once");
      repo.addPayment(old.id(), d("500000"), today, "Remaining payment", "partial-second");
      check(
          repo.bills(10, 0).stream().filter(b -> b.id() == old.id()).allMatch(Bill::paid),
          "Second receipt settles original invoice");
      check(debt(repo).compareTo(d("2000000")) == 0, "Settling old debt leaves new bill unchanged");
      long receipt =
          repo.payments(old.id()).stream()
              .filter(x -> x.amount().compareTo(d("500000")) == 0)
              .findFirst()
              .orElseThrow()
              .id();
      repo.reversePayment(receipt, "Synthetic correction");
      check(debt(repo).compareTo(d("2500000")) == 0, "Reversal restores exact debt");
      Path backup = dir.resolve("backup.db");
      repo.backup(backup);
      repo.restore(backup);
      check(debt(repo).compareTo(d("2500000")) == 0, "Partial debt survives backup and restore");
      System.out.println(
          "PASS: " + checks + " partial receipts, cross-month debt and correction checks.");
    } finally {
      try (var files = Files.walk(dir)) {
        for (Path f : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
      }
    }
  }
}
