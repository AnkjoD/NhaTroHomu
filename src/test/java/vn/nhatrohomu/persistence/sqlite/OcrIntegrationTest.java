package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.awt.image.BufferedImage;
import vn.nhatrohomu.integration.OcrService;

/** Chạy riêng trên Windows; truyền RAPID để kiểm tra gói OCR tùy chọn bằng ảnh tổng hợp. */
public final class OcrIntegrationTest {
  public static void main(String[] args) throws Exception {
    BufferedImage image = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 700, 200);
    g.setColor(Color.BLACK);
    g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
    g.drawString("998877", 70, 27);
    g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 90));
    g.drawString("001234", 70, 130);
    g.dispose();
    OcrService.Engine engine =
        args.length == 0 ? OcrService.Engine.WINDOWS : OcrService.Engine.valueOf(args[0]);
    OcrService.Result result = new OcrService().recognize(image, engine);
    System.out.println("OCR result: " + result.text());
    if (result.candidates().isEmpty() || !result.candidates().getFirst().equals("001234"))
      throw new AssertionError("Largest numeric box was not ranked first: " + result.candidates());
    System.out.println("PASS: " + engine + " integration on synthetic image.");
  }
}
