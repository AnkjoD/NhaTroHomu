package vn.nhatrohomu.integration;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** OCR chạy trên máy, chỉ khởi tạo khi người dùng yêu cầu; không gửi ảnh ra mạng. */
public final class OcrService {
  public static BufferedImage load(Path path) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
      if (input == null) throw new IOException("Không đọc được ảnh.");
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("Hãy dùng ảnh PNG hoặc JPG.");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || (long) w * h > 200_000_000)
          throw new IOException("Ảnh quá lớn. Hãy giảm kích thước trước.");
        ImageReadParam param = reader.getDefaultReadParam();
        int sample = Math.max(1, (int) Math.ceil(Math.max(w, h) / 1600.0));
        param.setSourceSubsampling(sample, sample, 0, 0);
        return reader.read(0, param);
      } finally {
        reader.dispose();
      }
    }
  }

  public static byte[] png(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  public record Result(String text, List<String> candidates) {}

  public static List<String> candidates(String text) {
    // Không tự đoán số nào là chỉ số đồng hồ; cho người dùng chọn giữa các ứng viên.
    LinkedHashSet<String> values = new LinkedHashSet<>();
    Matcher matcher =
        Pattern.compile("(?<![\\p{L}\\d])\\d{1,9}(?:[.,]\\d{1,3})?(?![\\p{L}\\d])").matcher(text);
    while (matcher.find() && values.size() < 30) values.add(matcher.group().replace(',', '.'));
    return new ArrayList<>(values);
  }

  public Result recognize(BufferedImage image) throws Exception {
    Path directory = Files.createTempDirectory("nhatrohomu-ocr-");
    Process process = null;
    try {
      Path script = directory.resolve("ocr.ps1"),
          photo = directory.resolve("image.png"),
          output = directory.resolve("result.txt");
      try (InputStream in = getClass().getResourceAsStream("/ocr.ps1")) {
        if (in == null) throw new IOException("Thiếu thành phần OCR trong bản build.");
        Files.copy(in, script);
      }
      ImageIO.write(image, "png", photo.toFile());
      String windows = System.getenv("SystemRoot");
      if (windows == null) throw new IOException("OCR bản này cần Windows 10/11.");
      Path shell = Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
      process =
          new ProcessBuilder(
                  shell.toString(),
                  "-NoProfile",
                  "-NonInteractive",
                  "-ExecutionPolicy",
                  "Bypass",
                  "-File",
                  script.toString(),
                  "-ImagePath",
                  photo.toString())
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      if (!process.waitFor(45, TimeUnit.SECONDS))
        throw new IOException("OCR quá thời gian. Thử cắt ảnh sát vùng số hoặc nhập tay.");
      String text = Files.readString(output, StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0)
        throw new IOException(
            text.isEmpty()
                ? "Không chạy được OCR của Windows. Bạn vẫn có thể nhập số thủ công."
                : text);
      return new Result(text, candidates(text));
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
      try (var files = Files.list(directory)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(directory);
    }
  }
}
