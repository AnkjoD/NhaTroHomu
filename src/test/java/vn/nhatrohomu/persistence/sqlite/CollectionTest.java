package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.billing.BillingDraft;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.shared.Models.*;

public final class CollectionTest {
  static int checks;

  static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  static void equal(BigDecimal actual, String expected) {
    checks++;
    if (actual.compareTo(d(expected)) != 0) throw new AssertionError(actual + " != " + expected);
  }

  interface Action {
    void run() throws Exception;
  }

  static void rejected(Action action) throws Exception {
    checks++;
    try {
      action.run();
    } catch (IllegalArgumentException | java.sql.SQLException expected) {
      return;
    }
    throw new AssertionError("Unsafe operation accepted");
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("advance-check-");
    try {
      SqliteRepository repo = new SqliteRepository(dir.resolve("test.db"));
      repo.saveRoom(
          new Room(0, "Credit room", "", d("0"), d("0"), "METER", null, true, d("1000000")));
      Room room = repo.rooms().getFirst();
      repo.rentals().saveTenant(new Tenant(0, "Credit owner", "", "", true));
      Tenant tenant = repo.rentals().tenants().getFirst();
      YearMonth month = YearMonth.now().minusMonths(1);
      String today = LocalDate.now().toString();
      repo.services().save(new ServiceItem(0, "Internet", d("100000")));
      ServiceItem service = repo.services().items().getFirst();
      repo.rentals()
          .createContract(
              new Contract(
                  0,
                  room.id(),
                  room.name(),
                  tenant.id(),
                  tenant.name(),
                  month.atDay(1).toString(),
                  "",
                  "",
                  d("1000000"),
                  d("0"),
                  5,
                  List.of()));
      List<Bill> bills = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        var draft = BillingDraft.prepare(repo, room, month.plusMonths(i));
        equal(draft.rent(), "1000000");
        repo.saveBill(draft.calculate("0", "0", "1000000", ""), null, null);
        bills.add(repo.bills(1, 0).getFirst());
      }
      rejected(() -> BillingDraft.prepare(repo, room, month));
      Bill old = bills.get(0), next = bills.get(1), future = bills.get(2);
      var collections = repo.collections();
      var result = collections.collect(old.id(), d("1500000"), today, "Extra", true, "source");
      equal(result.appliedCash(), "1000000");
      equal(result.savedAdvance(), "500000");
      collections.collect(old.id(), d("1500000"), today, "Extra", true, "source");
      equal(repo.received(old.id()), "1000000");
      rejected(() -> collections.collect(old.id(), d("1"), today, "Extra", true, "source"));
      long advance = collections.issued(old.id()).getFirst().id();
      equal(collections.available(next.id()).getFirst().available(), "500000");
      result = collections.collect(next.id(), d("0"), today, "", true, "use");
      equal(result.usedAdvance(), "500000");
      equal(repo.received(next.id()), "500000");
      if (!repo.payments(next.id()).getFirst().sourceMonth().equals(month.toString()))
        throw new AssertionError("Lost source month");
      checks++;
      rejected(() -> collections.reverseAdvance(advance, "Cannot erase used source"));
      rejected(
          () ->
              repo.reversePayment(
                  repo.payments(old.id()).getFirst().id(), "Cannot erase used cash"));
      collections.collect(next.id(), d("500000"), today, "Balance", true, "finish");
      equal(repo.received(next.id()), "1000000");
      equal(
          new ReportStore(repo.database).load(YearMonth.now(), null).collectedInRange(), "2000000");
      long allocation =
          repo.payments(next.id()).stream()
              .filter(p -> p.advanceId() != null)
              .findFirst()
              .orElseThrow()
              .id();
      repo.reversePayment(allocation, "Wrong destination");
      equal(collections.available(future.id()).getFirst().available(), "500000");
      collections.refund(advance, d("200000"), today, "Returned to owner", "refund");
      collections.refund(advance, d("200000"), today, "Returned to owner", "refund");
      equal(collections.issued(old.id()).getFirst().available(), "300000");
      equal(
          new ReportStore(repo.database).load(YearMonth.now(), null).collectedInRange(), "1800000");
      rejected(() -> collections.refund(advance, d("400000"), today, "Too much", "overflow"));
      rejected(() -> collections.collect(future.id(), d("-1"), today, "", true, "negative"));
      rejected(() -> collections.collect(future.id(), d("0.5"), today, "", true, "fraction"));
      rejected(
          () ->
              collections.collect(
                  future.id(),
                  d("1"),
                  LocalDate.now().plusDays(1).toString(),
                  "",
                  true,
                  "future-date"));
      collections.reverseRefund(collections.refunds(old.id()).getFirst().id(), "Refund not made");
      equal(collections.issued(old.id()).getFirst().available(), "500000");
      rejected(() -> collections.collect(future.id(), d("0"), today, "", false, "unchecked"));
      equal(collections.issued(old.id()).getFirst().available(), "500000");
      collections.collect(future.id(), d("0"), today, "", true, "future-use");
      equal(repo.received(future.id()), "500000");
      equal(
          new ReportStore(repo.database).load(YearMonth.now(), null).collectedInRange(), "2000000");
      repo.reversePayment(repo.payments(future.id()).getFirst().id(), "Wrong allocation");
      repo.reversePayment(repo.payments(old.id()).getFirst().id(), "Wrong source");
      equal(repo.received(old.id()), "0");
      equal(
          new ReportStore(repo.database).load(YearMonth.now(), null).collectedInRange(), "500000");
      collections.collect(old.id(), d("1500000"), today, "New source", true, "second-source");
      repo.rentals().saveTenant(new Tenant(0, "Other owner", "", "", true));
      long other =
          repo.rentals().tenants().stream()
              .filter(t -> t.name().equals("Other owner"))
              .findFirst()
              .orElseThrow()
              .id();
      rejected(() -> collections.available(next.id(), other));
      java.util.concurrent.ExecutorService pool =
          java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        var first =
            pool.submit(
                () -> {
                  try {
                    return collections
                        .collect(next.id(), d("0"), today, "", true, "race-next")
                        .usedAdvance();
                  } catch (IllegalArgumentException empty) {
                    return BigDecimal.ZERO;
                  }
                });
        var second =
            pool.submit(
                () -> {
                  try {
                    return collections
                        .collect(future.id(), d("0"), today, "", true, "race-future")
                        .usedAdvance();
                  } catch (IllegalArgumentException empty) {
                    return BigDecimal.ZERO;
                  }
                });
        equal(first.get().add(second.get()), "500000");
      } finally {
        pool.shutdownNow();
      }
      equal(collections.issued(old.id()).getFirst().available(), "0");
      equal(
          new ReportStore(repo.database).load(YearMonth.now(), null).collectedInRange(), "2000000");
      try (var c = DatabaseAccess.open(repo.database)) {
        DatabaseValidator.validate(c);
      }
      Path backup = dir.resolve("backup.db");
      repo.backup(backup);
      repo.restore(backup);
      checks++;
      System.out.println(
          "PASS: " + checks + " advance, refund, provenance and cash conservation checks.");
    } finally {
      try (var files = Files.walk(dir)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
    }
  }
}
