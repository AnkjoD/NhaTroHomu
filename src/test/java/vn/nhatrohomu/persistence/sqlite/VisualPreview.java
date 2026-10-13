package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import vn.nhatrohomu.app.AppWindow;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.billing.ui.PaymentDialog;
import vn.nhatrohomu.billing.ui.ReadingDialog;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.rental.ui.RentalPanel;
import vn.nhatrohomu.reporting.DashboardPanel;
import vn.nhatrohomu.reporting.ExpenseDialog;
import vn.nhatrohomu.reporting.ReportPanel;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.room.ui.RoomDialog;
import vn.nhatrohomu.room.ui.WaterPolicyDialog;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;

/** Vẽ chính component Swing vào PNG, không chụp màn hình hoặc dùng dữ liệu thật. */
public final class VisualPreview {
  static void capture(RootPaneContainer window, String name, Path output) throws Exception {
    Window w = (Window) window;
    w.addNotify();
    w.validate();
    Container content = window.getContentPane();
    BufferedImage image =
        new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    content.printAll(g);
    g.dispose();
    ImageIO.write(image, "png", output.resolve(name).toFile());
  }

  static Component find(Container parent, Class<?> type) {
    if (type.isInstance(parent)) return parent;
    if (parent instanceof JTabbedPane tabs
        && tabs.getSelectedComponent() instanceof Container selected) return find(selected, type);
    for (Component child : parent.getComponents()) {
      if (type.isInstance(child)) return child;
      if (child instanceof Container nested) {
        Component found = find(nested, type);
        if (found != null) return found;
      }
    }
    return null;
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("nhatrohomu.ui.test", "true");
    Path temp = Files.createTempDirectory("nhatrohomu-visual-");
    Path output = Path.of(args[0]);
    Files.createDirectories(output);
    try {
      SqliteRepository repo = new SqliteRepository(temp.resolve("fake.db"));
      for (int i = 1; i <= 4; i++)
        repo.saveRoom(
            new Room(
                0,
                "P0" + i,
                i == 1 ? "Nguyễn Minh Anh" : i == 2 ? "Trần Hoàng Nam" : "",
                new BigDecimal(1000 + i * 170),
                new BigDecimal(20 + i * 9),
                i == 3 ? "FIXED" : "METER",
                i == 3 ? new BigDecimal("3") : null,
                true));
      for (Room sample : repo.rooms())
        if (sample.id() != 4) {
          repo.rentals()
              .saveTenant(
                  new Tenant(
                      0,
                      sample.id() == 1
                          ? "Nguyễn Minh Anh"
                          : sample.id() == 2 ? "Trần Hoàng Nam" : "Lê Thanh Mai",
                      "090000000" + sample.id(),
                      "Dữ liệu demo",
                      true));
          Tenant person =
              repo.rentals().tenants().stream()
                  .filter(t -> t.phone().endsWith("" + sample.id()))
                  .findFirst()
                  .orElseThrow();
          repo.rentals()
              .createContract(
                  new Contract(
                      0,
                      sample.id(),
                      sample.name(),
                      person.id(),
                      person.name(),
                      java.time.YearMonth.now().minusMonths(5).atDay(1).toString(),
                      java.time.LocalDate.now().plusYears(1).toString(),
                      "",
                      new BigDecimal("2000000"),
                      new BigDecimal("2000000"),
                      5,
                      java.util.List.of(
                          new Charge("Internet", new BigDecimal("100000")),
                          new Charge("Vệ sinh", new BigDecimal("30000")))));
        }
      Contract depositContract = repo.rentals().contracts().getFirst();
      repo.rentals()
          .addDeposit(
              depositContract.id(),
              "RECEIVE",
              new BigDecimal("1000000"),
              java.time.LocalDate.now().toString(),
              "Nhận một phần cọc");
      for (int m = 5; m >= 0; m--)
        for (Room sample : repo.rooms()) {
          var previous = repo.previous(sample);
          repo.saveBill(
              RentalService.withRental(
                  BillingService.calculate(
                      sample,
                      java.time.YearMonth.now().minusMonths(m).toString(),
                      previous,
                      previous
                          .electricity()
                          .add(new BigDecimal(25 + (5 - m) * 8 + (int) sample.id() * 7)),
                      previous.water().add(new BigDecimal("3")),
                      new BigDecimal("3"),
                      repo.rates(),
                      "Dữ liệu minh họa"),
                  repo.rentals().forMonth(sample.id(), java.time.YearMonth.now().minusMonths(m)),
                  new BigDecimal("2000000")),
              null,
              null);
        }
      Bill demo = repo.bills(1, 0).getFirst();
      repo.addPayment(
          demo.id(),
          new BigDecimal("50000"),
          java.time.LocalDate.now().toString(),
          "Chuyển khoản một phần");
      Room room = repo.rooms().getFirst();
      SwingUtilities.invokeAndWait(
          () -> {
            Design.apply(args.length > 1 ? Design.Mode.valueOf(args[1]) : Design.Mode.HOMURA);
            AppWindow app = new AppWindow(repo, temp);
            try {
              app.setSize(1240, 820);
              DashboardPanel dashboard =
                  (DashboardPanel) find(app.getContentPane(), DashboardPanel.class);
              var render =
                  DashboardPanel.class.getDeclaredMethod(
                      "render", java.time.YearMonth.class, DashboardPanel.Snapshot.class);
              render.setAccessible(true);
              java.util.Map<Long, BigDecimal> received = new java.util.HashMap<>();
              for (Bill saved : repo.bills(100, 0))
                received.put(saved.id(), repo.received(saved.id()));
              render.invoke(
                  dashboard,
                  java.time.YearMonth.now(),
                  new DashboardPanel.Snapshot(
                      repo.rooms(),
                      repo.bills(100, 0),
                      received,
                      repo.rentals().contracts(),
                      repo.rentals().heldDeposit(depositContract.id())));
              capture(app, "nhatrohomu-dashboard.png", output);
              PaymentDialog payment = new PaymentDialog(app, repo, demo);
              capture(payment, "nhatrohomu-payments.png", output);
              payment.dispose();
              var rooms = AppWindow.class.getDeclaredMethod("showRooms");
              rooms.setAccessible(true);
              rooms.invoke(app);
              capture(app, "nhatrohomu-rooms.png", output);
              for (String method : new String[] {"showHistory", "showSettings"}) {
                var m = AppWindow.class.getDeclaredMethod(method);
                m.setAccessible(true);
                m.invoke(app);
                app.validate();
                capture(
                    app,
                    method.equals("showHistory")
                        ? "nhatrohomu-history.png"
                        : "nhatrohomu-settings.png",
                    output);
              }
              for (String method : new String[] {"showTenants", "showContracts", "showDebts"}) {
                var navigation = AppWindow.class.getDeclaredMethod(method);
                navigation.setAccessible(true);
                navigation.invoke(app);
                RentalPanel panel = (RentalPanel) find(app.getContentPane(), RentalPanel.class);
                for (String fieldName :
                    new String[] {"tenants", "contracts", "debts", "balances", "tenantContracts"}) {
                  var field = RentalPanel.class.getDeclaredField(fieldName);
                  field.setAccessible(true);
                  Object value;
                  if (fieldName.equals("tenantContracts")) value = repo.rentals().contracts();
                  else if (fieldName.equals("tenants")) value = repo.rentals().tenants();
                  else if (fieldName.equals("contracts")) value = repo.rentals().contracts();
                  else if (fieldName.equals("debts")) value = repo.outstandingBills();
                  else {
                    java.util.Map<Long, BigDecimal> values = new java.util.HashMap<>();
                    if (method.equals("showContracts")) {
                      for (Contract k : repo.rentals().contracts())
                        values.put(k.id(), repo.rentals().heldDeposit(k.id()));
                    } else {
                      for (Bill bill : repo.outstandingBills())
                        values.put(bill.id(), repo.received(bill.id()));
                    }
                    value = values;
                  }
                  field.set(panel, value);
                }
                var draw = RentalPanel.class.getDeclaredMethod("render");
                draw.setAccessible(true);
                draw.invoke(panel);
                capture(app, "nhatrohomu-" + method.substring(4).toLowerCase() + ".png", output);
              }
              var reportsMethod = AppWindow.class.getDeclaredMethod("showReports");
              reportsMethod.setAccessible(true);
              reportsMethod.invoke(app);
              ReportPanel reportsPanel =
                  (ReportPanel) find(app.getContentPane(), ReportPanel.class);
              var snapshotField = ReportPanel.class.getDeclaredField("snapshot");
              snapshotField.setAccessible(true);
              snapshotField.set(
                  reportsPanel,
                  new ReportStore(repo.database).load(java.time.YearMonth.now(), null));
              var renderReports =
                  ReportPanel.class.getDeclaredMethod("render", java.time.YearMonth.class);
              renderReports.setAccessible(true);
              renderReports.invoke(reportsPanel, java.time.YearMonth.now());
              capture(app, "nhatrohomu-reports.png", output);
              JTabbedPane reportTabs = (JTabbedPane) find(reportsPanel, JTabbedPane.class);
              for (int tab = 1; tab < reportTabs.getTabCount(); tab++) {
                reportTabs.setSelectedIndex(tab);
                capture(app, "nhatrohomu-report-tab-" + tab + ".png", output);
              }
              ExpenseDialog expense = new ExpenseDialog(app, repo, () -> {});
              capture(expense, "nhatrohomu-add-expense.png", output);
              expense.dispose();
              WaterPolicyDialog transition = new WaterPolicyDialog(app, repo, room, () -> {});
              capture(transition, "nhatrohomu-water-policy.png", output);
              transition.dispose();
              RoomDialog add = new RoomDialog(app, repo, null, () -> {});
              capture(add, "nhatrohomu-add-room.png", output);
              add.dispose();
              ReadingDialog read = new ReadingDialog(app, repo, room, () -> {});
              capture(read, "nhatrohomu-readings.png", output);
              read.dispose();
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            } finally {
              app.dispose();
            }
          });
    } finally {
      DatabaseAccess.exclusive(
          temp.resolve("fake.db"),
          () -> {
            try (var files = Files.list(temp)) {
              for (Path p : files.toList()) Files.deleteIfExists(p);
            }
            return null;
          });
      Files.deleteIfExists(temp);
    }
    System.out.println("Swing previews rendered.");
  }
}
