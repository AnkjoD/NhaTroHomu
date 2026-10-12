package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.Arrays;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.OcrService;
import vn.nhatrohomu.shared.Models.*;

/** Kiểm thử độc lập, không mở UI và không chạm database thật. */
public final class SelfTest {
  private static int checks;

  static BigDecimal d(String n) {
    return new BigDecimal(n);
  }

  static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  interface Operation {
    void run() throws Exception;
  }

  static void rejects(Operation operation, String message) throws Exception {
    try {
      operation.run();
    } catch (Exception expected) {
      checks++;
      return;
    }
    throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path temp = Files.createTempDirectory("nhatrohomu-test-");
    try {
      Room room = new Room(0, "P01", "An", d("100"), d("20"), "METER", null, true);
      Rates rates = new Rates(d("3500"), d("15000"), d("3"));
      Bill meter =
          BillingService.calculate(
              room,
              "2026-10",
              new Readings(d("100"), d("20")),
              d("110"),
              d("22"),
              d("3"),
              rates,
              "");
      check(meter.total().compareTo(d("65000")) == 0, "10 kWh + 2 m³");
      Room fixed = new Room(0, "P02", "", d("100"), d("20"), "FIXED", d("4"), true);
      Bill quota =
          BillingService.calculate(
              fixed,
              "2026-10",
              new Readings(d("100"), d("20")),
              d("101"),
              d("999"),
              d("4"),
              rates,
              "");
      check(quota.total().compareTo(d("63500")) == 0, "fixed water amount");
      check(quota.waterNew().compareTo(d("20")) == 0, "fixed water must not advance meter");
      rejects(
          () ->
              BillingService.calculate(
                  room,
                  "2026-10",
                  new Readings(d("100"), d("20")),
                  d("99"),
                  d("22"),
                  d("3"),
                  rates,
                  ""),
          "decreasing electricity");
      rejects(
          () ->
              BillingService.calculate(
                  room,
                  "2026-10",
                  new Readings(d("100"), d("20")),
                  d("101"),
                  d("19"),
                  d("3"),
                  rates,
                  ""),
          "decreasing water");
      rejects(() -> BillingService.number("-3", "test"), "negative input");
      rejects(() -> BillingService.number("1.2345", "test"), "excess precision");
      rejects(() -> BillingService.number("1,000,000", "test"), "ambiguous thousands");
      check(BillingService.number("2,5", "test").compareTo(d("2.5")) == 0, "decimal comma");
      Bill rounding =
          BillingService.calculate(
              room,
              "2026-10",
              new Readings(d("100"), d("20")),
              d("100.001"),
              d("20"),
              d("0"),
              new Rates(d("500"), d("0"), d("0")),
              "");
      check(rounding.total().compareTo(d("1")) == 0, "money round half up once");
      check(
          OcrService.candidates("Serial ABC123 001234,5 kWh 230 V")
              .equals(java.util.List.of("001234.5", "230")),
          "OCR candidates leave choice to human");
      SqliteRepository repo = new SqliteRepository(temp.resolve("live.db"));
      repo.saveRoom(room);
      Room stored = repo.rooms().getFirst();
      check(stored.id() > 0, "room persistent ID");
      rejects(() -> repo.saveRoom(room), "duplicate room");
      Bill bill =
          BillingService.calculate(
              stored, "2026-10", repo.previous(stored), d("110"), d("22"), d("0"), rates, "test");
      byte[] photo = {1, 2, 3};
      repo.saveBill(bill, photo, null);
      Bill persisted = repo.bills(10, 0).getFirst();
      check(Arrays.equals(repo.photo(persisted.id(), true), photo), "photo persisted");
      rejects(() -> repo.saveBill(bill, null, null), "duplicate month");
      repo.saveRates(new Rates(d("9999"), d("88888"), d("5")));
      check(
          repo.bills(10, 0).getFirst().total().compareTo(d("65000")) == 0,
          "rates snapshot preserved");
      rejects(
          () ->
              repo.saveRoom(new Room(stored.id(), "P01", "", d("0"), d("20"), "METER", null, true)),
          "opening readings locked");
      Bill next =
          BillingService.calculate(
              stored, "2026-11", repo.previous(stored), d("115"), d("23"), d("0"), rates, "");
      repo.saveBill(next, null, null);
      rejects(() -> repo.deleteLatest(persisted), "cannot delete earlier period");
      Bill latest = repo.bills(10, 0).getFirst();
      repo.setPaid(latest.id(), true);
      rejects(() -> repo.deleteLatest(latest), "cannot delete paid period");
      repo.setPaid(latest.id(), false);
      repo.deleteLatest(latest);
      check(repo.billCount() == 1, "delete latest returns previous readings");
      Path backup = temp.resolve("backup.db");
      repo.backup(backup);
      rejects(() -> repo.backup(backup), "no backup overwrite");
      repo.archiveRoom(stored.id());
      check(repo.rooms().isEmpty() && repo.billCount() == 1, "archive preserves history");
      repo.restore(backup);
      check(
          repo.rooms().size() == 1 && Arrays.equals(repo.photo(persisted.id(), true), photo),
          "restore includes photos");
      Path bad = temp.resolve("bad.db");
      Files.writeString(bad, "not a database");
      rejects(() -> repo.restore(bad), "invalid restore rejected");
      check(repo.billCount() == 1, "failed restore preserves live data");
      SqliteRepository reopened = new SqliteRepository(temp.resolve("live.db"));
      check(reopened.billCount() == 1, "reopen persists records");
      System.out.println("PASS: " + checks + " business and database checks.");
    } finally {
      try (var files = Files.list(temp)) {
        for (Path file : files.toList()) Files.deleteIfExists(file);
      }
      Files.deleteIfExists(temp);
    }
  }
}
