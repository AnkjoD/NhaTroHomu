package vn.nhatrohomu.shared;

import java.nio.file.*;
import java.util.Properties;

/** Per-book presentation preferences; never modify financial records. */
public final class LocalUiSettings {
  private static Path file;
  private static Properties values = new Properties();

  private LocalUiSettings() {}

  public static synchronized void load(Path directory) {
    file = directory.resolve("presentation.properties");
    values = new Properties();
    if (Files.isRegularFile(file)) {
      try (var input = Files.newInputStream(file)) {
        values.load(input);
      } catch (Exception ignored) {
        values = new Properties();
      }
    }
  }

  public static synchronized String get(String key, String fallback) {
    return values.getProperty(key, fallback);
  }

  public static synchronized void put(String key, String value) throws Exception {
    java.util.Map<String, String> change = new java.util.HashMap<>();
    change.put(key, value);
    putAll(change);
  }

  public static synchronized void putAll(java.util.Map<String, String> changes) throws Exception {
    Properties next = new Properties();
    next.putAll(values);
    changes.forEach(
        (key, value) -> {
          if (value == null) next.remove(key);
          else next.setProperty(key, value);
        });
    if (file != null) {
      Files.createDirectories(file.getParent());
      Path temporary = Files.createTempFile(file.getParent(), "presentation-", ".tmp");
      try {
        try (var output = Files.newOutputStream(temporary)) {
          next.store(output, "NhaTroHomu presentation");
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    }
    values = next;
  }
}
