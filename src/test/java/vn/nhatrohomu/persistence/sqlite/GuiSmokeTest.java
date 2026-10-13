package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.nio.file.*;
import javax.swing.*;
import vn.nhatrohomu.app.AppWindow;
import vn.nhatrohomu.billing.ui.ReadingDialog;
import vn.nhatrohomu.reporting.ReportPanel;
import vn.nhatrohomu.reporting.ReportStore;
import vn.nhatrohomu.room.ui.RoomDialog;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;

/** Khởi tạo cửa sổ/form trong dữ liệu tạm; không mở cửa sổ cho người dùng. */
public final class GuiSmokeTest {
  private static JScrollPane findScroll(java.awt.Container parent) {
    for (java.awt.Component child : parent.getComponents()) {
      if (child instanceof JScrollPane pane) return pane;
      if (child instanceof java.awt.Container nested) {
        JScrollPane pane = findScroll(nested);
        if (pane != null) return pane;
      }
    }
    return null;
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("nhatrohomu.ui.test", "true");
    Path temp = Files.createTempDirectory("nhatrohomu-gui-test-");
    try {
      SqliteRepository repo = new SqliteRepository(temp.resolve("test.db"));
      repo.saveRoom(
          new Room(
              0,
              "P01",
              "Dữ liệu giả",
              new BigDecimal("100"),
              new BigDecimal("20"),
              "METER",
              null,
              true));
      Room meter = repo.rooms().getFirst();
      repo.saveRoom(new Room(0, "P02", "", BigDecimal.ZERO, BigDecimal.ZERO, "FIXED", null, true));
      Room fixed =
          repo.rooms().stream().filter(r -> r.name().equals("P02")).findFirst().orElseThrow();
      repo.rentals().saveTenant(new Tenant(0, "Người thuê thử", "", "", true));
      Tenant person = repo.rentals().tenants().getFirst();
      repo.rentals()
          .createContract(
              new Contract(
                  0,
                  meter.id(),
                  meter.name(),
                  person.id(),
                  person.name(),
                  java.time.YearMonth.now().atDay(1).toString(),
                  java.time.LocalDate.now().plusYears(1).toString(),
                  "",
                  new BigDecimal("2000000"),
                  new BigDecimal("2000000"),
                  5,
                  java.util.List.of(new Charge("Internet", new BigDecimal("100000")))));
      SwingUtilities.invokeAndWait(
          () -> {
            Design.setup();
            JTable actionTable =
                new JTable(
                    new javax.swing.table.DefaultTableModel(
                        new Object[][] {{"A"}, {"B"}}, new String[] {"Tên"}));
            actionTable.setAutoCreateRowSorter(true);
            int[] invoked = {0};
            JButton rowButton =
                vn.nhatrohomu.shared.Ui.button(
                    "Chi tiết",
                    () -> {
                      if (actionTable.convertRowIndexToModel(actionTable.getSelectedRow()) != 1)
                        throw new AssertionError("Wrong sorted row");
                      invoked[0]++;
                    });
            vn.nhatrohomu.shared.RowActions.install(actionTable, java.util.List.of(rowButton));
            if (rowButton.isVisible())
              throw new AssertionError("Row action visible without selection");
            actionTable.getRowSorter().toggleSortOrder(0);
            actionTable.getRowSorter().toggleSortOrder(0);
            actionTable.setRowSelectionInterval(0, 0);
            if (!rowButton.isVisible()) throw new AssertionError("Selected row actions hidden");
            actionTable
                .getActionMap()
                .get("open-row")
                .actionPerformed(new java.awt.event.ActionEvent(actionTable, 0, ""));
            if (invoked[0] != 1) throw new AssertionError("Enter did not open selected row");
            actionTable.clearSelection();
            if (rowButton.isVisible())
              throw new AssertionError("Action still visible after clearing selection");
            actionTable
                .getActionMap()
                .get("open-row")
                .actionPerformed(new java.awt.event.ActionEvent(actionTable, 0, ""));
            if (invoked[0] != 1) throw new AssertionError("Action ran without selection");
            AppWindow app = new AppWindow(repo, temp);
            try {
              Design.choose(Design.Mode.MADOKA, temp);
              Design.setup(temp);
              if (Design.mode != Design.Mode.MADOKA)
                throw new AssertionError("Theme preference did not survive reload");
              // Toggle qua chính listener trên cửa sổ, xác minh rebuild không đổi dữ liệu.
              java.util.function.Consumer<java.awt.Container> toggle =
                  new java.util.function.Consumer<>() {
                    public void accept(java.awt.Container c) {
                      for (java.awt.Component child : c.getComponents()) {
                        if (child instanceof vn.nhatrohomu.shared.ThemeSwitch theme) {
                          theme.choose(Design.Mode.HOMURA);
                          return;
                        }
                        if (child instanceof java.awt.Container nested) accept(nested);
                      }
                    }
                  };
              toggle.accept(app.getContentPane());
              if (Design.mode != Design.Mode.HOMURA || repo.rooms().size() != 2)
                throw new AssertionError("Theme switch changed data or failed");
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
            try {
              var roomPage = AppWindow.class.getDeclaredMethod("showRooms");
              roomPage.setAccessible(true);
              roomPage.invoke(app);
              app.setSize(980, 640);
              app.addNotify();
              app.validate();
              java.awt.Container roomBody = app.getContentPane();
              JScrollPane roomScroll = findScroll(roomBody);
              if (roomScroll == null || roomScroll.getViewport().getHeight() < 280)
                throw new AssertionError("Rooms need usable height on a small window");
              new vn.nhatrohomu.room.ui.RoomHistoryDialog(app, repo, meter, b -> {}).dispose();
              new RoomDialog(app, repo, null, () -> {}).dispose();
              new RoomDialog(app, repo, meter, () -> {}).dispose();
              ReadingDialog rentalBill = new ReadingDialog(app, repo, meter, () -> {});
              var calculate = ReadingDialog.class.getDeclaredMethod("calculate");
              calculate.setAccessible(true);
              Bill preview = (Bill) calculate.invoke(rentalBill);
              if (preview.contractId() == null
                  || preview.total().compareTo(new BigDecimal("2100000")) != 0)
                throw new AssertionError("Reading form did not include the rental contract");
              rentalBill.dispose();
              repo.saveBill(preview, null, null);
              Bill paymentBill = repo.bills(1, 0).getFirst();
              var payment = new vn.nhatrohomu.billing.ui.PaymentDialog(app, repo, paymentBill);
              var amountField = payment.getClass().getDeclaredField("amount");
              amountField.setAccessible(true);
              JTextField receivedInput = (JTextField) amountField.get(payment);
              var saveField = payment.getClass().getDeclaredField("addReceipt");
              saveField.setAccessible(true);
              JButton saveReceipt = (JButton) saveField.get(payment);
              var fillField = payment.getClass().getDeclaredField("fillRemaining");
              fillField.setAccessible(true);
              JButton fill = (JButton) fillField.get(payment);
              var advanceField = payment.getClass().getDeclaredField("useAdvance");
              advanceField.setAccessible(true);
              if (!((JCheckBox) advanceField.get(payment)).isSelected())
                throw new AssertionError("Advance option must default on");
              if (!receivedInput.getText().isEmpty() || saveReceipt.isEnabled())
                throw new AssertionError("Blank receipt must not be treated as paid in full");
              fill.doClick();
              if (!receivedInput.getText().equals(paymentBill.total().toPlainString())
                  || repo.received(paymentBill.id()).signum() != 0)
                throw new AssertionError("Filling amount must not record money");
              receivedInput.setText("1500000");
              if (!saveReceipt.isEnabled())
                throw new AssertionError("Valid partial amount disabled");
              receivedInput.setText("99999999");
              if (!saveReceipt.isEnabled()) throw new AssertionError("Advance payment disabled");
              payment.dispose();
              repo.addPayment(
                  paymentBill.id(),
                  paymentBill.total(),
                  java.time.LocalDate.now().toString(),
                  "Synthetic full receipt");
              var settled = new vn.nhatrohomu.billing.ui.PaymentDialog(app, repo, paymentBill);
              if (((JButton) saveField.get(settled)).isEnabled()
                  || ((JButton) fillField.get(settled)).isEnabled())
                throw new AssertionError("Fully paid invoice must disable adding receipts");
              settled.dispose();
              new vn.nhatrohomu.billing.ui.ServiceCatalogDialog(app, repo.services()).dispose();
              new vn.nhatrohomu.integration.ReceiptStyleDialog(
                      app, paymentBill, repo.payments(paymentBill.id()))
                  .dispose();

              new ReadingDialog(app, repo, fixed, () -> {}).dispose();
              ReportPanel report = new ReportPanel(repo);
              var snapshotField = ReportPanel.class.getDeclaredField("snapshot");
              snapshotField.setAccessible(true);
              snapshotField.set(
                  report, new ReportStore(repo.database).load(java.time.YearMonth.now(), null));
              var render = ReportPanel.class.getDeclaredMethod("render", java.time.YearMonth.class);
              render.setAccessible(true);
              render.invoke(report, java.time.YearMonth.now());
              app.setContentPane(report);
              app.setSize(1100, 700);
              app.addNotify();
              app.validate();
              JTabbedPane tabs = null;
              for (java.awt.Component component : report.getComponents())
                if (component instanceof JTabbedPane pane) tabs = pane;
              if (tabs == null || tabs.getTabCount() != 6)
                throw new AssertionError("Report sections missing");
              JPanel page = (JPanel) tabs.getSelectedComponent();
              vn.nhatrohomu.shared.SelectionView view =
                  (vn.nhatrohomu.shared.SelectionView)
                      ((java.awt.BorderLayout) page.getLayout())
                          .getLayoutComponent(java.awt.BorderLayout.CENTER);
              view.selectLayout("Bảng");
              app.validate();
              if (view.getHeight() < 150)
                throw new AssertionError("Report explanation hid the data view");
            } catch (Exception e) {
              throw new RuntimeException(e);
            } finally {
              app.dispose();
            }
          });
      System.out.println(
          "PASS: room forms, rental and utility billing forms, theme switch, report table layout,"
              + " main Swing window.");
    } finally {
      try (var files = Files.list(temp)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(temp);
    }
  }
}
