package vn.nhatrohomu.app;

import java.nio.file.*;

/** Reuse the existing book instead of creating a second empty book after rebranding. */
public final class ProfileDirectory {
  private ProfileDirectory() {}

  public static Path resolve(Path base, boolean demo, String override) {
    if (override != null) return Path.of(override).toAbsolutePath().normalize();
    Path preferred = base.resolve(demo ? "NhaTroHomu-demo" : "NhaTroHomu");
    Path legacy = base.resolve(demo ? "SoTro-demo" : "SoTro");
    if (Files.isRegularFile(preferred.resolve("nhatrohomu.db"))
        || Files.isRegularFile(preferred.resolve("sotro.db"))) return preferred;
    if (Files.isRegularFile(legacy.resolve("sotro.db"))) return legacy;
    return preferred;
  }

  public static Path database(Path directory) {
    Path legacy = directory.resolve("sotro.db");
    return Files.isRegularFile(legacy) ? legacy : directory.resolve("nhatrohomu.db");
  }
}
