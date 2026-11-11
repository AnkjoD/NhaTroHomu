package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.billing.BillingDraft;
import vn.nhatrohomu.integration.*;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

public final class PresentationCatalogTest {
  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("catalog-presentation-");
    try {
      SqliteRepository repo = new SqliteRepository(dir.resolve("test.db"));
      repo.services().save(new ServiceItem(0, "Internet", new BigDecimal("100000")));
      var fee = repo.services().items().getFirst();
      repo.saveRoom(
          new Room(
              0,
              "Catalog room",
              "",
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              "METER",
              null,
              true,
              new BigDecimal("1000000")));
      Room room = repo.rooms().getFirst();
      repo.rentals().saveTenant(new Tenant(0, "Catalog tenant", "", "", true));
      Tenant tenant = repo.rentals().tenants().getFirst();
      repo.rentals()
          .createContract(
              new Contract(
                  0,
                  room.id(),
                  room.name(),
                  tenant.id(),
                  tenant.name(),
                  YearMonth.now().atDay(1).toString(),
                  "",
                  "",
                  new BigDecimal("1000000"),
                  BigDecimal.ZERO,
                  5,
                  List.of(new Charge(fee.name(), fee.amount(), fee.id()))));
      var draft = BillingDraft.prepare(repo, room, YearMonth.now());
      repo.saveBill(draft.calculate("0", "0", "1000000", ""), null, null);
      Bill bill = repo.bills(1, 0).getFirst();
      check(bill.fees().getFirst().serviceId().equals(fee.id()), "Bill lost catalog relationship");
      repo.saveRoom(
          new Room(
              0,
              "Room only",
              "",
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              "METER",
              null,
              true,
              new BigDecimal("1300000")));
      Room roomOnly =
          repo.rooms().stream().filter(x -> x.name().equals("Room only")).findFirst().orElseThrow();
      var noTenant = BillingDraft.prepare(repo, roomOnly, YearMonth.now());
      check(noTenant.rent().equals(new BigDecimal("1300000")), "Batch did not pick room price");
      var standalone = noTenant.calculate("0", "0", "", "");
      check(
          standalone.total().equals(new BigDecimal("1300000"))
              && standalone.contractId() == null
              && standalone.fees().isEmpty(),
          "Room price missing or invented tenancy");
      check(
          noTenant.calculate("0", "0", "0", "").rent().signum() == 0,
          "Utility-only override failed");
      repo.saveBill(standalone, null, null);
      try (var connection = DatabaseAccess.open(repo.database)) {
        DatabaseValidator.validate(connection);
      }
      repo.services().save(new ServiceItem(fee.id(), "Network", new BigDecimal("200000")));
      check(
          repo.roomBills(room.id(), bill.month(), bill.month())
                  .getFirst()
                  .fees()
                  .getFirst()
                  .amount()
                  .compareTo(new BigDecimal("100000"))
              == 0,
          "Catalog edit changed bill");
      check(
          repo.rentals().contracts().getFirst().fees().getFirst().label().equals("Internet"),
          "Catalog edit changed agreement");
      try {
        repo.services().deleteUnused(fee.id());
        throw new AssertionError("Linked fee was deleted");
      } catch (IllegalArgumentException expected) {
      }
      check(repo.services().archive(fee.id()) == 1, "Archive did not detach active fee");
      check(repo.services().items().isEmpty(), "Archived fee still selectable");
      check(
          repo.services().archivedItems().getFirst().id() == fee.id(),
          "Archived fee cannot be found");
      check(repo.services().archive(fee.id()) == 0, "Reapplying archive must be safe");
      try {
        repo.services().deleteUnused(fee.id());
        throw new AssertionError("A fee used only by issued invoices was deleted");
      } catch (IllegalArgumentException expected) {
      }
      check(
          BillingDraft.prepare(repo, room, YearMonth.now().plusMonths(1)).fees().isEmpty(),
          "Fee still applied to next draft");
      check(
          repo.roomBills(room.id(), bill.month(), bill.month()).getFirst().equals(bill),
          "Stop rewrote issued invoice");
      repo.services().save(new ServiceItem(0, "Unused", BigDecimal.ONE));
      repo.services().deleteUnused(repo.services().items().getFirst().id());
      check(repo.services().items().isEmpty(), "Unused fee was not deleted");
      LocalUiSettings.load(dir);
      var style = new ReceiptStyle("SansSerif", 26, "CUSTOM SLIP", false, false);
      style.save();
      LocalUiSettings.load(dir);
      check(ReceiptStyle.current().equals(style), "Style not retained after restart");
      var image = InvoiceImage.render(bill, List.of(), style);
      check(image.getWidth() == 1200 && image.getHeight() > 200, "Custom image not rendered");
      LocalUiSettings.put("slip.size", "invalid");
      check(
          ReceiptStyle.current().equals(ReceiptStyle.defaults()),
          "Invalid local preference breaks rendering");
      ImagePanel panel = new ImagePanel();
      panel.setImage(new java.awt.image.BufferedImage(1000, 800, 1));
      var bounds = new ImagePanel.Region(.2, .1, .3, .2);
      panel.setRegion(bounds);
      panel.setSize(400, 300);
      check(
          panel.selected().getWidth() == 300 && panel.selected().getHeight() == 160,
          "Wrong normalized crop");
      panel.setSize(900, 600);
      check(panel.selected().getWidth() == 300, "Resizing changes crop");
      panel.setImage(new java.awt.image.BufferedImage(2000, 1600, 1));
      panel.setRegion(bounds);
      check(
          panel.selected().getWidth() == 600 && panel.selected().getHeight() == 320,
          "Crop not reusable across resolutions");
      try (var c = DatabaseAccess.open(repo.database)) {
        DatabaseValidator.validate(c);
      }
      System.out.println(
          "PASS: catalog links, immutable prices, font preferences and reusable crop checks.");
    } finally {
      LocalUiSettings.load(dir.resolve("unused"));
      try (var files = Files.walk(dir)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
    }
  }
}
