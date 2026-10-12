package vn.nhatrohomu.app;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Models.*;

/** Fake data for a dedicated demo profile. Never clears or reseeds an existing book. */
public final class DemoData {
  private DemoData() {}

  public static void populate(Repository repo) throws Exception {
    if (!repo.rooms().isEmpty()
        || repo.billCount() != 0
        || !repo.rentals().tenants().isEmpty()
        || !repo.rentals().contracts().isEmpty()) return;
    for (int i = 1; i <= 4; i++)
      repo.saveRoom(
          new Room(
              0,
              "P0" + i,
              "",
              new BigDecimal(1000 + i * 170),
              new BigDecimal(20 + i * 9),
              i == 3 ? "FIXED" : "METER",
              i == 3 ? new BigDecimal("3") : null,
              true));
    String[] names = {"Nguyễn Minh Anh", "Trần Hoàng Nam", "Lê Thanh Mai"};
    List<Room> rooms = repo.rooms();
    for (int i = 0; i < 3; i++) {
      repo.rentals()
          .saveTenant(
              new Tenant(0, names[i], "", "Liên hệ minh họa, không phải người thuê thật", true));
      String name = names[i];
      Tenant person =
          repo.rentals().tenants().stream()
              .filter(t -> t.name().equals(name))
              .findFirst()
              .orElseThrow();
      Room room = rooms.get(i);
      repo.rentals()
          .createContract(
              new Contract(
                  0,
                  room.id(),
                  room.name(),
                  person.id(),
                  person.name(),
                  YearMonth.now().minusMonths(5).atDay(1).toString(),
                  LocalDate.now().plusYears(1).toString(),
                  "",
                  new BigDecimal("2000000"),
                  new BigDecimal("2000000"),
                  5,
                  List.of(
                      new Charge("Internet", new BigDecimal("100000")),
                      new Charge("Vệ sinh", new BigDecimal("30000")))));
    }
    for (int m = 5; m >= 0; m--)
      for (int i = 0; i < 3; i++) {
        Room room = rooms.get(i);
        Readings previous = repo.previous(room);
        YearMonth period = YearMonth.now().minusMonths(m);
        Contract contract = repo.rentals().forMonth(room.id(), period);
        Bill utility =
            BillingService.calculate(
                room,
                period.toString(),
                previous,
                previous.electricity().add(new BigDecimal(30 + i * 10 + (5 - m) * 8)),
                previous.water().add(new BigDecimal("3")),
                new BigDecimal("3"),
                repo.rates(),
                "Dữ liệu minh họa");
        repo.saveBill(RentalService.withRental(utility, contract, contract.rent()), null, null);
      }
    Contract contract = repo.rentals().contracts().getFirst();
    repo.rentals()
        .addDeposit(
            contract.id(),
            "RECEIVE",
            new BigDecimal("1000000"),
            LocalDate.now().toString(),
            "Nhận một phần cọc");
    List<Bill> latest = repo.bills(3, 0);
    repo.addPayment(
        latest.get(0).id(), new BigDecimal("500000"), LocalDate.now().toString(), "Thu một phần");
    repo.addPayment(
        latest.get(1).id(), latest.get(1).total(), LocalDate.now().toString(), "Thu đủ");
  }
}
