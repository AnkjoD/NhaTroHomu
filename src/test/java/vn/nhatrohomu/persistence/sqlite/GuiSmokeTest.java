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
  public static void main(String[] args) throws Exception {
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
                        if (child instanceof JButton b && b.getText().equals("Homura · Tối")) {
                          b.doClick();
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
              JScrollPane scroll =
                  (JScrollPane)
                      ((java.awt.BorderLayout) page.getLayout())
                          .getLayoutComponent(java.awt.BorderLayout.CENTER);
              if (scroll.getViewport().getHeight() < 150)
                throw new AssertionError("Report explanation hid the table");
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
