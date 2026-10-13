package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.swing.*;
import vn.nhatrohomu.billing.ui.*;
import vn.nhatrohomu.integration.*;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

public final class PhotoWorkflowTest {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static Object field(Object value, String name) throws Exception {
    var f = value.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(value);
  }

  static BufferedImage picture(int rgb) {
    var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(new Color(rgb));
    g.fillRect(0, 0, 200, 100);
    g.dispose();
    return image;
  }

  static void approve(Container parent) {
    for (Component component : parent.getComponents()) {
      if (component instanceof JOptionPane option) option.setValue(JOptionPane.OK_OPTION);
      else if (component instanceof Container nested) approve(nested);
    }
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("photo-workflow-");
    BatchBillingDialog[] dialog = {null};
    try {
      BufferedImage image = picture(0xff0000);
      BufferedImage pasted = ClipboardImage.read(InvoiceImage.transferable(image));
      check(
          pasted != image && pasted.getRGB(10, 10) == image.getRGB(10, 10),
          "Clipboard image must be copied");
      var large = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
      pasted = ClipboardImage.read(InvoiceImage.transferable(large));
      check(pasted.getWidth() == 1600 && pasted.getHeight() == 800, "Clipboard image not bounded");
      try {
        ClipboardImage.read(new StringSelection("not an image"));
        throw new AssertionError("Text accepted");
      } catch (IllegalArgumentException expected) {
        checks++;
      }
      Path file = dir.resolve("copied.png");
      javax.imageio.ImageIO.write(image, "png", file.toFile());
      Transferable fileList =
          new Transferable() {
            public DataFlavor[] getTransferDataFlavors() {
              return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
              return flavor.equals(DataFlavor.javaFileListFlavor);
            }

            public Object getTransferData(DataFlavor flavor) {
              return java.util.List.of(file.toFile());
            }
          };
      check(
          ClipboardImage.read(fileList).getRGB(10, 10) == image.getRGB(10, 10),
          "Copied file failed");
      check(PhotoOrder.compare("room2.jpg", "room10.jpg") < 0, "Filename order must be numeric");
      SqliteRepository repo = new SqliteRepository(dir.resolve("test.db"));
      for (String name : java.util.List.of("P10", "P2")) {
        repo.saveRoom(
            new Room(
                0,
                name,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "METER",
                null,
                true,
                new BigDecimal("1000000")));
        Room room =
            repo.rooms().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
        repo.rentals().saveTenant(new Tenant(0, "Tenant " + name, "", "", true));
        Tenant tenant =
            repo.rentals().tenants().stream()
                .filter(t -> t.name().equals("Tenant " + name))
                .findFirst()
                .orElseThrow();
        repo.rentals()
            .createContract(
                new Contract(
                    0,
                    room.id(),
                    name,
                    tenant.id(),
                    tenant.name(),
                    YearMonth.now().atDay(1).toString(),
                    "",
                    "",
                    new BigDecimal("1000000"),
                    BigDecimal.ZERO,
                    5,
                    java.util.List.of()));
      }
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              Design.setup(dir);
              LocalUiSettings.load(dir);
              dialog[0] = new BatchBillingDialog(null, repo, () -> {});
              dialog[0].addNotify();
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
          });
      long deadline = System.currentTimeMillis() + 10000;
      while (System.currentTimeMillis() < deadline) {
        boolean[] ready = {false};
        SwingUtilities.invokeAndWait(
            () -> {
              try {
                ready[0] = ((java.util.List<?>) field(dialog[0], "entries")).size() == 2;
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            });
        if (ready[0]) break;
        Thread.sleep(50);
      }
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              var entries = (java.util.List<?>) field(dialog[0], "entries");
              check(entries.size() == 2, "Batch did not load");
              check(
                  ((JPanel) field(entries.getFirst(), "card")).getComponentCount() > 0,
                  "Room card missing");
              int index = 0;
              for (Object entry : entries) {
                MeterPhotoPanel photo = (MeterPhotoPanel) field(entry, "electricityPhoto");
                photo.accept(picture(index++ == 0 ? 0xff0000 : 0x0000ff));
                check(photo.getActionMap().get("paste-image") != null, "Paste shortcut missing");
                check(
                    ((JTextField) field(entry, "electricity")).getText().isEmpty(),
                    "Photo auto-invented reading");
                ((JTextField) field(entry, "electricity")).setText("10");
                ((JTextField) field(entry, "water")).setText("2");
              }
              javax.swing.Timer timer =
                  new javax.swing.Timer(
                      50,
                      e -> {
                        for (Window window : Window.getWindows())
                          if (window instanceof JDialog && window.isVisible())
                            approve((Container) window);
                      });
              timer.start();
              try {
                var commit = dialog[0].getClass().getDeclaredMethod("commit");
                commit.setAccessible(true);
                commit.invoke(dialog[0]);
              } finally {
                timer.stop();
              }
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
          });
      deadline = System.currentTimeMillis() + 10000;
      while (repo.billCount() != 2 && System.currentTimeMillis() < deadline) Thread.sleep(50);
      check(repo.billCount() == 2, "Batch not saved");
      for (Bill bill : repo.bills(10, 0)) {
        byte[] data = repo.photo(bill.id(), true);
        check(data != null, "Batch discarded photo");
        BufferedImage saved = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
        check(
            saved.getRGB(10, 10) == (bill.roomName().equals("P2") ? 0xffff0000 : 0xff0000ff),
            "Photo assigned to wrong room");
      }
      try (var connection = DatabaseAccess.open(repo.database)) {
        DatabaseValidator.validate(connection);
      }
      System.out.println(
          "PASS: "
              + checks
              + " clipboard, ordered room photos and batch photo persistence checks.");
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (dialog[0] != null) dialog[0].dispose();
          });
      try (var files = Files.walk(dir)) {
        for (Path path : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }
}
