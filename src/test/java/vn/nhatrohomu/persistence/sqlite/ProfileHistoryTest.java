package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.reporting.InvoiceCsv;
import vn.nhatrohomu.shared.Models.*;

public final class ProfileHistoryTest {
  private static int checks;

  private static void check(boolean yes, String reason) {
    checks++;
    if (!yes) throw new AssertionError(reason);
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("profile-history-test-");
    try {
      Path file = dir.resolve("test.db");
      SqliteRepository repo = new SqliteRepository(file);
      repo.rentals().saveTenant(new Tenant(0, "Hồ sơ giả", "0900000000", "", true));
      Tenant t = repo.rentals().tenants().getFirst();
      check(t.zaloContact().equals(t.phone()), "default Zalo");
      Resident child = new Resident("Người ở cùng giả", "Con", "2015-01-01", "TEST-ID", "");
      repo.rentals()
          .saveTenant(
              new Tenant(
                  t.id(),
                  t.name(),
                  "0900000001",
                  "",
                  true,
                  "0900000002",
                  false,
                  "FAKE-ID",
                  "1990-01-01",
                  "Địa chỉ giả",
                  List.of(child)));
      t = repo.rentals().tenants().getFirst();
      check(t.zaloContact().equals("0900000002") && !t.zaloAuto(), "Zalo independent override");
      check(
          t.residents().equals(List.of(child))
              && t.identity().equals("FAKE-ID")
              && t.address().equals("Địa chỉ giả"),
          "profile and household persisted");
      Tenant saved = t;
      check(
          repo.rentals().tenant(t.id()).equals(saved),
          "Lookup by tenant ID includes the correct household");
      check(
          repo.rentals().tenant(Long.MAX_VALUE) == null,
          "Missing tenant must not resolve to another household");
      try {
        repo.rentals()
            .saveTenant(
                new Tenant(
                    t.id(),
                    "Đã đổi",
                    t.phone(),
                    "",
                    true,
                    "",
                    true,
                    "",
                    "2999-01-01",
                    "",
                    List.of()));
        throw new AssertionError("future birthday accepted");
      } catch (IllegalArgumentException expected) {
        checks++;
      }
      check(
          repo.rentals().tenants().getFirst().equals(saved), "invalid profile preserves household");
      try (Connection c = DatabaseAccess.open(file)) {
        DatabaseValidator.validate(c);
      }
      Path backup = dir.resolve("backup.db");
      repo.backup(backup);
      repo.rentals()
          .saveTenant(
              new Tenant(
                  t.id(), t.name(), "0900000003", "", true, "", true, "", "", "", List.of()));
      check(repo.rentals().tenants().getFirst().residents().isEmpty(), "remove member");
      check(
          repo.rentals().tenants().getFirst().zaloContact().equals("0900000003"),
          "auto follows edited phone");
      repo.restore(backup);
      check(repo.rentals().tenants().getFirst().equals(saved), "profile restore round trip");
      repo.saveRoom(new Room(0, "P01", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
      Room room = repo.rooms().getFirst();
      Rates rates = repo.rates();
      for (String month : List.of("2026-08", "2026-09")) {
        Readings previous = repo.previous(room);
        repo.saveBill(
            BillingService.calculate(
                room,
                month,
                previous,
                previous.electricity().add(new BigDecimal("10")),
                previous.water().add(new BigDecimal("2")),
                BigDecimal.ZERO,
                rates,
                ""),
            null,
            null);
      }
      List<Bill> bills = repo.roomBills(room.id(), "2026-09", "2026-09");
      check(
          bills.size() == 1 && bills.getFirst().electricityOld().intValue() == 10,
          "room history respects period and opening");
      check(repo.roomBills(999, "1900-01", "9999-12").isEmpty(), "room isolation");
      Bill bill = bills.getFirst();
      List<String> cells = InvoiceCsv.cells(bill, BigDecimal.ZERO);
      check(
          cells.size() == InvoiceCsv.HEADERS.size()
              && cells.get(3).equals("10")
              && cells.get(4).equals("20"),
          "CSV column alignment and readings");
      check(
          bill.electricityCost().intValue() == 35000
              && bill.waterCost().intValue() == 30000
              && bill.utilitiesCost().intValue() == 65000,
          "separate utility subtotals");
      Path csv = dir.resolve("export.csv");
      InvoiceCsv.write(csv, bills, repo);
      check(
          Files.readString(csv).startsWith("\uFEFF")
              && Files.readString(csv).contains("Cộng điện nước VND"),
          "CSV Vietnamese header and BOM");
      Path old = dir.resolve("version4.db");
      new SqliteRepository(old)
          .rentals()
          .saveTenant(new Tenant(0, "Người thuê cũ", "0900000004", "Giữ ghi chú", true));
      try (Connection c = DatabaseAccess.open(old);
          Statement s = c.createStatement()) {
        s.execute("DROP TABLE residents");
        FeatureTest.removeSchema9(s);
        for (String col : List.of("zalo", "zalo_auto", "identity_no", "birthday", "address"))
          s.execute("ALTER TABLE tenants DROP COLUMN " + col);
        s.execute("DROP TRIGGER bills_no_rewrite");
        s.execute("DROP TABLE handover_events");
        s.execute("ALTER TABLE rooms DROP COLUMN default_rent");
        s.execute("DROP INDEX bills_active_period");
        s.execute(
            "CREATE UNIQUE INDEX bills_active_period ON bills(room_id,month) WHERE voided_at IS"
                + " NULL");
        s.execute("ALTER TABLE bills DROP COLUMN period_start");
        s.execute("ALTER TABLE bills DROP COLUMN period_end");
        s.execute("ALTER TABLE bills DROP COLUMN water_amount");
        s.execute(Schema.triggers(4).get("bills_no_rewrite"));
        s.execute("PRAGMA user_version=4");
      }
      Tenant upgraded = new SqliteRepository(old).rentals().tenants().getFirst();
      check(
          upgraded.name().equals("Người thuê cũ")
              && upgraded.zaloContact().equals("0900000004")
              && upgraded.note().equals("Giữ ghi chú"),
          "schema4 migration preserves contact");
      try (var files = Files.list(dir)) {
        check(
            files.anyMatch(x -> x.getFileName().toString().startsWith("before-upgrade-")),
            "migration rescue backup");
      }
      repo.rentals().archiveTenant(saved.id());
      Tenant archived = repo.rentals().tenant(saved.id());
      check(
          archived != null && !archived.active() && archived.residents().equals(saved.residents()),
          "Archived tenant retains household for historical tenancy lookup");
      System.out.println(
          "PASS: "
              + checks
              + " profile, household, migration, room history and invoice export checks.");
    } finally {
      try (var paths = Files.walk(dir)) {
        for (Path x : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
      }
    }
  }
}
