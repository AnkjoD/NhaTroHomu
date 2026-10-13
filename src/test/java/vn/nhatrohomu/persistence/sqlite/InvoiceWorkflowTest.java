package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.*;
import javax.swing.*;
import vn.nhatrohomu.app.AppWindow;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.rental.ui.RentalPanel;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;

/** Tests real month navigation and inline tenant creation, with synthetic data only. */
public final class InvoiceWorkflowTest {
  static int checks;

  static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  static Component named(Container parent, String name) {
    for (Component child : parent.getComponents()) {
      if (name.equals(child.getName())) return child;
      if (child instanceof Container nested) {
        Component result = named(nested, name);
        if (result != null) return result;
      }
    }
    return null;
  }

  static JTable table(Container parent) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JTable t) return t;
      if (child instanceof Container nested) {
        JTable result = table(nested);
        if (result != null) return result;
      }
    }
    return null;
  }

  static JDialog dialog(String prefix) {
    for (Window window : Window.getWindows())
      if (window instanceof JDialog d && d.isShowing() && d.getTitle().startsWith(prefix)) return d;
    throw new AssertionError("Missing dialog: " + prefix);
  }

  static void later(Runnable task) {
    Timer timer = new Timer(200, e -> task.run());
    timer.setRepeats(false);
    timer.start();
  }

  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("invoice-workflow-");
    SqliteRepository repository = new SqliteRepository(directory.resolve("test.db"));
    repository.saveRoom(
        new Room(0, "P01", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
    Room room = repository.rooms().getFirst();
    for (String period : new String[] {"2026-08", "2026-09", "2026-10"}) {
      Readings old = repository.previous(room);
      repository.saveBill(
          BillingService.calculate(
              room,
              period,
              old,
              old.electricity().add(BigDecimal.ONE),
              old.water(),
              BigDecimal.ZERO,
              new Rates(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO),
              "Synthetic invoice"),
          null,
          null);
    }
    repository.saveRoom(
        new Room(
            0,
            "P02",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "METER",
            null,
            true,
            new BigDecimal("2000000")));
    Room vacant =
        repository.rooms().stream().filter(r -> r.name().equals("P02")).findFirst().orElseThrow();
    Throwable[] error = {null};
    SwingUtilities.invokeAndWait(
        () -> {
          Design.setup();
          AppWindow app = new AppWindow(repository, directory);
          try {
            var history = AppWindow.class.getDeclaredMethod("showHistory");
            history.setAccessible(true);
            history.invoke(app);
            JTable latest = table(app.getContentPane());
            check(
                latest.getRowCount() == 1 && latest.getValueAt(0, 0).equals("2026-10"),
                "Invoices open on the newest month only");
            JComboBox<?> period = (JComboBox<?>) named(app.getContentPane(), "invoice-period");
            period.setSelectedItem("2026-08");
            check(
                table(app.getContentPane()).getRowCount() == 1
                    && table(app.getContentPane()).getValueAt(0, 0).equals("2026-08"),
                "Changing month displays older invoices");
            ((JComboBox<?>) named(app.getContentPane(), "invoice-period"))
                .setSelectedItem("Tất cả tháng");
            check(
                table(app.getContentPane()).getRowCount() == 3,
                "All months restores every invoice");
            history.invoke(app);
            check(
                table(app.getContentPane()).getRowCount() == 3,
                "Refreshing preserves the selected scope");
            app.setVisible(true);
            Timer watchdog =
                new Timer(
                    10000,
                    e -> {
                      error[0] = new AssertionError("Inline rental workflow timed out");
                      for (Window window : Window.getWindows())
                        if (window instanceof JDialog) window.dispose();
                    });
            watchdog.setRepeats(false);
            watchdog.start();
            later(
                () -> {
                  try {
                    JDialog rent = dialog("Cho thuê phòng");
                    check(
                        ((JTextField) named(rent, "rental-rent")).getText().equals("2000000"),
                        "Room default price is prefilled in the tenancy form");
                    JTextField price = (JTextField) named(rent, "rental-rent");
                    JCheckBox override = (JCheckBox) named(rent, "rental-custom-rent");
                    check(
                        !price.isEditable() && !override.isSelected(),
                        "Default room price is locked until explicitly overridden");
                    override.doClick();
                    check(price.isEditable(), "Negotiated price can be edited only after opt in");
                    price.setText("1900000");
                    override.doClick();
                    check(
                        !price.isEditable() && price.getText().equals("2000000"),
                        "Disabling negotiated price restores the room default");
                    later(
                        () -> {
                          try {
                            JDialog person = dialog("Thêm người thuê");
                            ((JTextField) named(person, "tenant-name"))
                                .setText("Người thuê thử luồng mới");
                            DisplayTest.find(person, "Lưu").doClick();
                          } catch (Throwable ex) {
                            error[0] = ex;
                            for (Window w : Window.getWindows())
                              if (w instanceof JDialog) w.dispose();
                          }
                        });
                    DisplayTest.find(rent, "+ Thêm người thuê").doClick();
                    JComboBox<?> people = (JComboBox<?>) named(rent, "rental-tenant");
                    check(
                        people.getSelectedItem() instanceof Tenant t
                            && t.name().equals("Người thuê thử luồng mới"),
                        "New tenant is selected without changing screens");
                    later(
                        () -> {
                          ((JCheckBox) named(rent, "handover-confirmed")).setSelected(true);
                          DisplayTest.find(rent, "Lưu").doClick();
                        });
                  } catch (Throwable ex) {
                    error[0] = ex;
                    for (Window w : Window.getWindows()) if (w instanceof JDialog) w.dispose();
                  }
                });
            int[] callbacks = {0};
            RentalPanel.letRoom(app, repository, vacant.id(), () -> callbacks[0]++);
            watchdog.stop();
            if (error[0] != null) throw new AssertionError(error[0]);
            check(
                repository.rentals().contracts().size() == 1
                    && repository.rentals().contracts().getFirst().roomId() == vacant.id(),
                "Inline flow saves the contract for the chosen room");
            check(
                callbacks[0] == 1 && repository.billCount() == 3,
                "Refresh fires once and existing invoices remain intact");
            check(
                repository
                        .rentals()
                        .contracts()
                        .getFirst()
                        .rent()
                        .compareTo(new BigDecimal("2000000"))
                    == 0,
                "Saved agreement uses the default after the override is disabled");
            watchdog.restart();
            later(
                () -> {
                  try {
                    JDialog checkout = dialog("Trả phòng và chốt kỳ cuối");
                    ((JTextField) named(checkout, "checkout-electricity")).setText("10");
                    ((JTextField) named(checkout, "checkout-water")).setText("1");
                    ((JCheckBox) named(checkout, "checkout-confirmed")).setSelected(true);
                    DisplayTest.find(checkout, "Chốt hóa đơn và trả phòng").doClick();
                  } catch (Throwable ex) {
                    error[0] = ex;
                    for (Window window : Window.getWindows())
                      if (window instanceof JDialog) window.dispose();
                  }
                });
            Contract active = repository.rentals().contracts().getFirst();
            new vn.nhatrohomu.billing.ui.CheckoutDialog(
                    app, repository, active, () -> callbacks[0]++)
                .setVisible(true);
            watchdog.stop();
            if (error[0] != null) throw new AssertionError(error[0]);
            Contract ended = repository.rentals().contracts().getFirst();
            check(
                !ended.occupiedOn(java.time.LocalDate.now())
                    && ended.closedDate().equals(java.time.LocalDate.now().toString()),
                "Checkout immediately releases room occupancy");
            check(
                repository.billCount() == 4 && callbacks[0] == 2,
                "Checkout form creates exactly one final invoice and refreshes once");
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          } finally {
            app.dispose();
          }
        });
    System.out.println("PASS: " + checks + " invoice month and inline rental workflow checks.");
  }
}
