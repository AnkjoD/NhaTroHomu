package vn.nhatrohomu.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Tiến trình riêng: model/native memory được trả lại khi OCR kết thúc. */
public final class RapidOcrWorker {
  private RapidOcrWorker() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("Expected image and output paths");
    // Reflection giữ bản thường chạy được khi chưa cài gói OCR tùy chọn.
    Class<?> configType = Class.forName("io.github.hzkitty.entity.OcrConfig");
    Object config = configType.getConstructor().newInstance();
    for (String section : new String[] {"Global", "Det", "Rec", "Cls"}) {
      Object settings = configType.getField(section).get(config);
      settings.getClass().getMethod("setIntraOpNumThreads", int.class).invoke(settings, 2);
      settings.getClass().getMethod("setInterOpNumThreads", int.class).invoke(settings, 1);
      if (!section.equals("Global")) {
        settings.getClass().getMethod("setUseArena", boolean.class).invoke(settings, false);
      }
    }
    Object global = configType.getField("Global").get(config);
    global.getClass().getMethod("setUseCls", boolean.class).invoke(global, false);
    Class<?> engineType = Class.forName("io.github.hzkitty.RapidOCR");
    Object engine = engineType.getMethod("create", configType).invoke(null, config);
    var image = ImageIO.read(Path.of(args[0]).toFile());
    if (image == null) throw new IllegalArgumentException("Unsupported image");
    Object result =
        engineType.getMethod("run", java.awt.image.BufferedImage.class).invoke(engine, image);
    String text = (String) result.getClass().getMethod("getStrRes").invoke(result);
    Files.writeString(Path.of(args[1]), text == null ? "" : text, StandardCharsets.UTF_8);
  }
}
