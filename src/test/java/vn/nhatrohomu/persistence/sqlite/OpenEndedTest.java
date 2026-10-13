package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.List;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.shared.Models.*;

public final class OpenEndedTest {
  static int checks;

  static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  static void rejects(TurnoverTest.Action action, String message) throws Exception {
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      checks++;
      return;
    }
    throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("open-ended-lease-");
    Path file = directory.resolve("book.db");
    SqliteRepository repo = new SqliteRepository(file);
    repo.saveRoom(
        new Room(
            0,
            "P01",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "METER",
            null,
            true,
            new BigDecimal("2000000")));
    repo.rentals().saveTenant(new Tenant(0, "Đại diện thử", "", "", true));
    try (Connection c = DatabaseAccess.open(file);
        Statement s = c.createStatement()) {
      FeatureTest.removeSchema9(s);
      s.execute("PRAGMA user_version=7");
    }
    repo = new SqliteRepository(file);
    try (Connection c = DatabaseAccess.open(file);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("PRAGMA user_version")) {
      check(
          r.next() && r.getInt(1) == Schema.VERSION,
          "Schema 7 upgrades without rebuilding existing tables");
    }
    try (var files = Files.list(directory)) {
      check(
          files.anyMatch(p -> p.getFileName().toString().startsWith("before-upgrade-")),
          "Schema 7 is backed up before allowing open-ended tenancies");
    }
    SqliteRepository book = repo;
    Room room = book.rooms().getFirst();
    Tenant person = book.rentals().tenants().getFirst();
    YearMonth previous = YearMonth.now().minusMonths(1);
    String first = previous.atDay(1).toString();
    Contract draft =
        new Contract(
            0,
            room.id(),
            room.name(),
            person.id(),
            person.name(),
            first,
            "",
            "",
            room.defaultRent(),
            BigDecimal.ZERO,
            5,
            List.of());
    book.rentals().createContract(draft, new Readings(BigDecimal.ZERO, BigDecimal.ZERO));
    Contract lease = book.rentals().contracts().getFirst();
    check(
        lease.endDate().isEmpty() && lease.occupiedOn(LocalDate.now().plusYears(10)),
        "Open-ended tenancy stays occupied without fabricating a departure date");
    check(
        lease.endLabel().equals("Chưa xác định"),
        "Internal bound is never shown as a departure date");
    rejects(
        () -> book.rentals().archiveTenant(person.id()),
        "Active open-ended representative cannot be archived");
    rejects(() -> book.archiveRoom(room.id()), "Active open-ended room cannot be archived");
    book.rentals().saveTenant(new Tenant(0, "Người khác thử", "", "", true));
    Tenant other =
        book.rentals().tenants().stream()
            .filter(t -> t.id() != person.id())
            .findFirst()
            .orElseThrow();
    rejects(
        () ->
            book.rentals()
                .createContract(
                    new Contract(
                        0,
                        room.id(),
                        room.name(),
                        other.id(),
                        other.name(),
                        LocalDate.now().plusYears(2).toString(),
                        "",
                        "",
                        room.defaultRent(),
                        BigDecimal.ZERO,
                        5,
                        List.of())),
        "Future lease cannot overlap an open-ended tenant");
    check(
        new ReportStore(file).load(YearMonth.now(), null).missing().size() == 2,
        "Missing invoices stop at the selected month for an open-ended tenancy");
    Bill prior =
        TurnoverTest.bill(book, room, lease, previous.atEndOfMonth().toString(), "10", "1", "");
    book.saveBill(prior, null, null);
    check(
        book.rentals().forMonth(room.id(), YearMonth.now()).id() == lease.id(),
        "Next billing month resolves the same open-ended tenancy");
    Bill finalBill =
        TurnoverTest.bill(book, room, lease, LocalDate.now().toString(), "20", "2", "");
    book.rentals().checkout(finalBill);
    Contract closed = book.rentals().contracts().getFirst();
    check(
        closed.closedDate().equals(LocalDate.now().toString())
            && !closed.occupiedOn(LocalDate.now()),
        "Checkout closes an open-ended tenancy on its actual date");
    check(
        book.billCount() == 2 && book.outstandingBills().size() == 2,
        "Checkout preserves unpaid monthly obligations");
    try (Connection c = DatabaseAccess.open(file)) {
      DatabaseValidator.validate(c);
    }
    check(true, "Validator accepts open-ended, closed, segmented invoices and handovers");
    System.out.println("PASS: " + checks + " open-ended tenancy and migration checks.");
  }
}
