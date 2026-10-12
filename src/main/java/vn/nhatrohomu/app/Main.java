package vn.nhatrohomu.app;

import java.nio.channels.*;
import java.nio.file.*;
import javax.swing.*;
import vn.nhatrohomu.persistence.sqlite.DatabaseAccess;
import vn.nhatrohomu.persistence.sqlite.DatabaseValidator;
import vn.nhatrohomu.persistence.sqlite.SqliteRepository;
import vn.nhatrohomu.shared.Design;

/** Tạo dependency, giữ khóa một phiên ứng dụng, chuyển việc tạo giao diện sang EDT. */
public final class Main {
  public static void main(String[] args) {
    try {
      boolean demo = java.util.Arrays.asList(args).contains("--demo");
      boolean smoke = java.util.Arrays.asList(args).contains("--smoke");
      String override = System.getProperty("nhatrohomu.data", System.getProperty("sotro.data"));
      String local = System.getenv("LOCALAPPDATA");
      Path directory =
          ProfileDirectory.resolve(
              Path.of(local == null ? System.getProperty("user.home") : local), demo, override);
      Files.createDirectories(directory);
      FileChannel channel =
          FileChannel.open(
              directory.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock lock = channel.tryLock();
      if (lock == null) {
        channel.close();
        throw new IllegalStateException("NhaTroHomu đã mở. Hãy dùng cửa sổ đang chạy.");
      }
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      lock.release();
                      channel.close();
                    } catch (Exception ignored) {
                    }
                  }));
      SqliteRepository repository = new SqliteRepository(ProfileDirectory.database(directory));
      DatabaseValidator.checkSafeSchema(repository.database);
      try (java.sql.Connection c = DatabaseAccess.open(repository.database)) {
        DatabaseValidator.validate(c);
      }
      if (demo) DemoData.populate(repository);
      SwingUtilities.invokeLater(
          () -> {
            Design.setup(directory);
            AppWindow window = new AppWindow(repository, directory);
            if (demo) window.setTitle("NhaTroHomu · DEMO · Dữ liệu minh họa");
            if (smoke) {
              window.dispose();
              System.out.println("GUI initialization OK");
              System.exit(0);
            } else window.setVisible(true);
          });
    } catch (Exception ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(
          null, ex.getMessage(), "Không mở được NhaTroHomu", JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }
  }
}
