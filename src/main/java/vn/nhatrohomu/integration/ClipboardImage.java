package vn.nhatrohomu.integration;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Reads a copied image or a single copied image file without uploading it. */
public final class ClipboardImage {
  private ClipboardImage() {}

  public static BufferedImage read() throws Exception {
    return read(Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null));
  }

  public static BufferedImage read(Transferable content) throws Exception {
    if (content == null)
      throw new IllegalArgumentException("Clipboard chưa có ảnh. Sao chép ảnh rồi dán lại.");
    if (content.isDataFlavorSupported(DataFlavor.imageFlavor)) {
      Image source = (Image) content.getTransferData(DataFlavor.imageFlavor);
      javax.swing.ImageIcon loaded = new javax.swing.ImageIcon(source);
      int w = loaded.getIconWidth(), h = loaded.getIconHeight();
      if (w <= 0 || h <= 0 || (long) w * h > 200_000_000)
        throw new IllegalArgumentException("Ảnh clipboard không hợp lệ hoặc quá lớn.");
      double scale = Math.min(1, 1600.0 / Math.max(w, h));
      BufferedImage copy =
          new BufferedImage(
              Math.max(1, (int) (w * scale)),
              Math.max(1, (int) (h * scale)),
              BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = copy.createGraphics();
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, copy.getWidth(), copy.getHeight());
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, copy.getWidth(), copy.getHeight(), null);
      graphics.dispose();
      return copy;
    }
    if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      List<?> files = (List<?>) content.getTransferData(DataFlavor.javaFileListFlavor);
      if (files.size() == 1 && files.getFirst() instanceof File file)
        return OcrService.load(file.toPath());
      throw new IllegalArgumentException(
          "Để gán nhiều ảnh, dùng Chọn nhiều ảnh trong màn Lập nhiều phòng.");
    }
    throw new IllegalArgumentException(
        "Clipboard chưa có ảnh. Dùng Sao chép ảnh, không chỉ sao chép đường dẫn.");
  }
}
