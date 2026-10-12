package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.awt.image.BufferedImage;
import vn.nhatrohomu.integration.OcrService;

/** Chạy riêng trên Windows có gói OCR; không dùng ảnh đồng hồ thật. */
public final class OcrIntegrationTest {
  public static void main(String[] args) throws Exception {
    BufferedImage image = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 700, 200);
    g.setColor(Color.BLACK);
    g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 90));
    g.drawString("001234", 70, 130);
    g.dispose();
    OcrService.Result result = new OcrService().recognize(image);
    System.out.println("OCR result: " + result.text());
    if (result.candidates().stream().noneMatch(n -> n.contains("1234")))
      throw new AssertionError("Synthetic digits not recognized");
    System.out.println("PASS: Windows OCR integration on synthetic image.");
  }
}
