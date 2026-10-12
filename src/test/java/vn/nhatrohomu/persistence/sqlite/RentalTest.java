package vn.nhatrohomu.persistence.sqlite;

import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.app.DemoData;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.InvoicePdf;
import vn.nhatrohomu.rental.RentalRepository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Models.*;

/** Exercises lifecycle boundaries rather than mirroring SQL statements. */
public final class RentalTest {
  static int checks;

  static BigDecimal d(String text) {
    return new BigDecimal(text);
  }

  static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    checks++;
  }

  interface Task {
    void run() throws Exception;
  }

  static void rejects(Task task, String label) throws Exception {
    try {
      task.run();
    } catch (IllegalArgumentException e) {
      checks++;
      return;
    }
    throw new AssertionError(label);
  }

  static Contract template(Room room, Tenant tenant, String start, String end) {
    return new Contract(
        0,
        room.id(),
        room.name(),
        tenant.id(),
        tenant.name(),
        start,
        end,
        "",
        d("2000000"),
        d("2000000"),
        5,
        List.of(new Charge("Internet", d("100000")), new Charge("Vệ sinh", d("30000"))));
  }

  public static void main(String[] args) throws Exception {
    Path temp = Files.createTempDirectory("nhatrohomu-rental-test-");
    try {
      SqliteRepository repo = new SqliteRepository(temp.resolve("live.db"));
      RentalRepository rentals = repo.rentals();
      repo.saveRoom(new Room(0, "P01", "", d("0"), d("0"), "METER", null, true));
      repo.saveRoom(new Room(0, "P02", "", d("0"), d("0"), "FIXED", d("3"), true));
      Room room = repo.rooms().getFirst(), other = repo.rooms().getLast();
      rentals.saveTenant(new Tenant(0, "Nguyễn Minh Ánh", "0900000000", "Demo", true));
      Tenant person = rentals.tenants().getFirst();
      rejects(() -> rentals.saveTenant(new Tenant(0, "", "", "", true)), "blank tenant rejected");
      rejects(() -> RentalService.money("1.5", "Tiền thuê"), "fractional rent rejected");
      YearMonth base = YearMonth.now().minusMonths(6);
      String start = base.plusMonths(1).atDay(1).toString(),
          end = base.plusMonths(12).atEndOfMonth().toString();
      rentals.createContract(template(room, person, start, end));
      Contract contract = rentals.contracts().getFirst();
      check(contract.covers(LocalDate.now()), "room occupied within term");
      check(
          rentals.forMonth(room.id(), base.plusMonths(1)).id() == contract.id(),
          "contract selected by invoice period");
      check(rentals.forMonth(room.id(), base.minusMonths(1)) == null, "no contract before term");
      rejects(
          () -> rentals.createContract(template(room, person, start, end)),
          "overlap same room rejected");
      rejects(
          () -> rentals.createContract(template(other, person, start, end)),
          "tenant overlapping room rejected");
      rejects(() -> rentals.archiveTenant(person.id()), "archive active tenant blocked");
      rejects(() -> repo.archiveRoom(room.id()), "archive occupied room blocked");
      Bill utility =
          BillingService.calculate(
              room,
              base.plusMonths(1).toString(),
              repo.previous(room),
              d("10"),
              d("2"),
              d("0"),
              repo.rates(),
              "Ở đủ tháng");
      Bill full = RentalService.withRental(utility, contract, contract.rent());
      check(full.total().compareTo(d("2195000")) == 0, "rent plus utilities and each fee");
      check(full.dueDate().equals(base.plusMonths(1).atDay(5).toString()), "due date snapshot");
      rejects(() -> repo.saveBill(utility, null, null), "cannot omit rent for contracted period");
      repo.saveBill(full, null, null);
      Bill saved = repo.bills(1, 0).getFirst();
      check(
          saved.fees().size() == 2
              && saved.contractId() == contract.id()
              && saved.tenantName().equals(person.name()),
          "persist full invoice snapshot");
      rentals.saveTenant(new Tenant(person.id(), "Tên liên hệ mới", "0900000001", "", true));
      check(
          repo.bills(1, 0).getFirst().tenantName().equals(person.name()),
          "tenant rename keeps old invoice");
      repo.addPayment(saved.id(), d("1000000"), LocalDate.now().toString(), "Trả một phần");
      check(
          repo.outstandingBills().size() == 1
              && saved.total().subtract(repo.received(saved.id())).compareTo(d("1195000")) == 0,
          "partial rental debt");
      rentals.addDeposit(
          contract.id(), "RECEIVE", d("500000"), LocalDate.now().toString(), "Nhận cọc");
      check(
          rentals.heldDeposit(contract.id()).compareTo(d("500000")) == 0
              && repo.received(saved.id()).compareTo(d("1000000")) == 0,
          "deposit separate from invoice receipt");
      rejects(
          () ->
              rentals.addDeposit(
                  contract.id(), "REFUND", d("500001"), LocalDate.now().toString(), ""),
          "cannot refund above held deposit");
      rejects(
          () ->
              rentals.addDeposit(
                  contract.id(), "RECEIVE", d("2000000"), LocalDate.now().toString(), ""),
          "deposit cannot exceed requirement");
      rentals.addDeposit(
          contract.id(), "REFUND", d("100000"), LocalDate.now().toString(), "Hoàn một phần");
      check(rentals.heldDeposit(contract.id()).compareTo(d("400000")) == 0, "partial refund");
      rejects(
          () ->
              rentals.addDeposit(
                  contract.id(), "REFUND", d("1"), LocalDate.now().minusDays(1).toString(), ""),
          "deposit chronological order");
      rejects(
          () -> rentals.cancelUnusedContract(contract.id()),
          "contract with transactions cannot be removed");
      rejects(
          () -> rentals.closeContract(contract.id(), base.atEndOfMonth().toString()),
          "closing before billed period rejected");
      rentals.closeContract(contract.id(), base.plusMonths(1).atEndOfMonth().toString());
      check(
          !rentals.contracts().getFirst().covers(LocalDate.now()),
          "closed room no longer occupied");
      check(
          rentals.heldDeposit(contract.id()).compareTo(d("400000")) == 0,
          "closing retains deposit to settle");
      rentals.saveTenant(new Tenant(0, "Người thuê tiếp theo", "", "", true));
      Tenant next =
          rentals.tenants().stream().filter(t -> t.id() != person.id()).findFirst().orElseThrow();
      rejects(
          () ->
              rentals.createContract(
                  template(room, next, base.plusMonths(1).atEndOfMonth().toString(), end)),
          "one tenant per invoice month");
      rentals.createContract(template(room, next, base.plusMonths(2).atDay(1).toString(), end));
      check(rentals.contracts().size() == 2, "new tenant next month keeps history");
      Contract nextContract = rentals.forMonth(room.id(), base.plusMonths(2));
      Bill adjustment =
          RentalService.withRental(
              BillingService.calculate(
                  room,
                  base.plusMonths(2).toString(),
                  repo.previous(room),
                  d("15"),
                  d("3"),
                  d("0"),
                  repo.rates(),
                  "Giảm tiền phòng theo thỏa thuận"),
              nextContract,
              d("1000000"));
      repo.saveBill(adjustment, null, null);
      check(
          repo.bills(1, 0).getFirst().rent().compareTo(d("1000000")) == 0,
          "explicit rent adjustment saved");
      repo.deleteLatest(repo.bills(1, 0).getFirst());
      check(
          repo.billCount() == 1
              && rentals.forMonth(room.id(), base.plusMonths(2)).fees().size() == 2,
          "cancel invoice keeps contract charge schedule");
      repo.saveBill(adjustment, null, null);
      rejects(
          () -> repo.deleteLatest(repo.bills(10, 0).getLast()),
          "older rental invoice cannot cancel");
      rentals.saveTenant(new Tenant(0, "Người thuê thử", "", "", true));
      Tenant unused =
          rentals.tenants().stream()
              .filter(t -> t.name().equals("Người thuê thử"))
              .findFirst()
              .orElseThrow();
      rentals.createContract(template(other, unused, start, end));
      Contract unusedContract =
          rentals.contracts().stream()
              .filter(k -> k.roomId() == other.id())
              .findFirst()
              .orElseThrow();
      rentals.cancelUnusedContract(unusedContract.id());
      check(rentals.contracts().size() == 2, "unused erroneous contract cancellable");
      Path pdf = temp.resolve("invoice.pdf");
      InvoicePdf.write(pdf, saved, repo.payments(saved.id()));
      try (PdfReader reader = new PdfReader(Files.readAllBytes(pdf))) {
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        check(
            text.contains("Tiền phòng")
                && text.contains("Internet")
                && text.contains("Nguyễn Minh Ánh")
                && text.contains("Hạn thu"),
            "rental PDF snapshot and Vietnamese");
      }
      Path backup = temp.resolve("backup.db");
      repo.backup(backup);
      rentals.addDeposit(
          contract.id(), "REFUND", d("400000"), LocalDate.now().toString(), "Trả hết");
      repo.restore(backup);
      check(
          rentals.heldDeposit(contract.id()).compareTo(d("400000")) == 0
              && rentals.contracts().size() == 2
              && repo.bills(10, 0).getLast().fees().size() == 2,
          "restore rental data, fees and deposits");
      // Simulate the actual v0.4 schema, not just a downgraded version marker.
      Path old = temp.resolve("old.db");
      SqliteRepository oldRepo = new SqliteRepository(old);
      oldRepo.saveRoom(new Room(0, "Legacy", "Tên cũ", d("0"), d("0"), "METER", null, true));
      try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old);
          Statement s = c.createStatement()) {
        FeatureTest.removeRentalSchema(s);
        s.execute("PRAGMA user_version=2");
      }
      SqliteRepository upgraded = new SqliteRepository(old);
      check(
          upgraded.rentals().tenants().size() == 1 && upgraded.rentals().contracts().isEmpty(),
          "v2 migration imports names without fictional contracts");
      repo.restore(old);
      check(
          repo.rooms().size() == 1 && repo.rentals().tenants().size() == 1,
          "restoring v2 upgrades full schema");
      SqliteRepository demoRepo = new SqliteRepository(temp.resolve("demo.db"));
      DemoData.populate(demoRepo);
      check(
          demoRepo.rooms().size() == 4
              && demoRepo.rentals().contracts().size() == 3
              && demoRepo.billCount() == 18,
          "demo profile populated");
      DemoData.populate(demoRepo);
      check(
          demoRepo.billCount() == 18 && demoRepo.rentals().tenants().size() == 3,
          "demo does not duplicate existing data");
      if (args.length > 0)
        InvoicePdf.write(
            Path.of(args[0]),
            saved,
            List.of(
                new Payment(
                    0, saved.id(), d("1000000"), LocalDate.now().toString(), "Thu một phần")));
      System.out.println(
          "PASS: " + checks + " rental lifecycle, snapshot, deposit, migration and PDF checks.");
    } finally {
      try (var files = Files.list(temp)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.delete(temp);
    }
  }
}
