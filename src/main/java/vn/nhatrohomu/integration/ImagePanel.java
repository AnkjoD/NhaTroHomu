package vn.nhatrohomu.integration;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Ui;

/** Kéo chuột để khoanh vùng số. Tọa độ màn hình được đổi về tọa độ ảnh gốc. */
public final class ImagePanel extends JPanel {
  private BufferedImage image;
  private Point start, end;
  private Region region;
  private java.util.function.Consumer<Region> changed = value -> {};

  public record Region(double x, double y, double width, double height) {
    public Region {
      if (!Double.isFinite(x + y + width + height)
          || x < 0
          || y < 0
          || width <= 0
          || height <= 0
          || x + width > 1.000000001
          || y + height > 1.000000001)
        throw new IllegalArgumentException("Khung ảnh không hợp lệ.");
    }
  }

  public void onRegionChanged(java.util.function.Consumer<Region> callback) {
    changed = callback;
  }

  public void setRegion(Region value) {
    region = value;
    start = null;
    end = null;
    repaint();
  }

  public Region region() {
    return region;
  }

  public ImagePanel() {
    setPreferredSize(new Dimension(480, 190));
    setBackground(Design.MINT);
    MouseAdapter mouse =
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            start = e.getPoint();
            end = start;
            repaint();
          }

          public void mouseDragged(MouseEvent e) {
            end = e.getPoint();
            Rectangle bounds = selectedBounds();
            if (image != null && bounds != null)
              region =
                  new Region(
                      bounds.x / (double) image.getWidth(),
                      bounds.y / (double) image.getHeight(),
                      bounds.width / (double) image.getWidth(),
                      bounds.height / (double) image.getHeight());
            repaint();
          }

          public void mouseReleased(MouseEvent e) {
            if (image != null
                && start != null
                && end != null
                && start.distance(end) >= 8
                && region != null) changed.accept(region);
          }
        };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  public void setImage(BufferedImage image) {
    this.image = image;
    start = null;
    end = null;
    region = null;
    repaint();
  }

  public static BufferedImage chooseRegion(java.awt.Window owner, BufferedImage source) {
    Region bounds = chooseRegionBounds(owner, source);
    if (bounds == null) return null;
    ImagePanel panel = new ImagePanel();
    panel.setImage(source);
    panel.setRegion(bounds);
    return panel.selected();
  }

  public static Region chooseRegionBounds(java.awt.Window owner, BufferedImage source) {
    javax.swing.JDialog dialog =
        new javax.swing.JDialog(
            owner, "Khoanh vùng chỉ số", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    ImagePanel canvas = new ImagePanel();
    canvas.setImage(source);
    javax.swing.JSlider zoom = new javax.swing.JSlider(50, 200, 100);
    zoom.setToolTipText("Phóng ảnh 50%–200%");
    Runnable resize =
        () -> {
          double factor = zoom.getValue() / 100.0;
          canvas.setPreferredSize(
              new Dimension(
                  (int) (source.getWidth() * factor) + 12,
                  (int) (source.getHeight() * factor) + 12));
          canvas.revalidate();
        };
    zoom.addChangeListener(e -> resize.run());
    resize.run();
    javax.swing.JPanel root = new javax.swing.JPanel(new BorderLayout(8, 10));
    root.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 14, 14, 14));
    javax.swing.JPanel top = new javax.swing.JPanel(new BorderLayout(8, 8));
    top.add(
        new javax.swing.JLabel("Kéo khoanh riêng dãy số cần đọc; kéo thanh cuộn để xem vùng khác."),
        BorderLayout.NORTH);
    top.add(zoom);
    root.add(top, BorderLayout.NORTH);
    root.add(new javax.swing.JScrollPane(canvas));
    Region[] result = {null};
    javax.swing.JPanel buttons = new javax.swing.JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Hủy", dialog::dispose));
    buttons.add(
        Design.primary(
            Ui.button(
                "Dùng vùng đã chọn",
                () -> {
                  result[0] = canvas.region == null ? new Region(0, 0, 1, 1) : canvas.region;
                  dialog.dispose();
                })));
    root.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(root);
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    dialog.setSize(Math.min(1000, screen.width - 80), Math.min(760, screen.height - 80));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
    return result[0];
  }

  private double scale() {
    return image == null
        ? 1
        : Math.min(
            (getWidth() - 12.0) / image.getWidth(), (getHeight() - 12.0) / image.getHeight());
  }

  public BufferedImage selected() {
    if (image == null) return null;
    if (region == null) return image;
    int x = Math.min(image.getWidth() - 1, (int) (region.x() * image.getWidth())),
        y = Math.min(image.getHeight() - 1, (int) (region.y() * image.getHeight()));
    int
        right =
            Math.min(
                image.getWidth(),
                (int) Math.ceil((region.x() + region.width()) * image.getWidth() - 1e-9)),
        bottom =
            Math.min(
                image.getHeight(),
                (int) Math.ceil((region.y() + region.height()) * image.getHeight() - 1e-9));
    return image.getSubimage(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
  }

  private Rectangle selectedBounds() {
    if (image == null || start == null || end == null || start.distance(end) < 8 || scale() <= 0)
      return null;
    double scale = scale();
    int ox = (getWidth() - (int) (image.getWidth() * scale)) / 2,
        oy = (getHeight() - (int) (image.getHeight() * scale)) / 2;
    int x = Math.max(0, (int) ((Math.min(start.x, end.x) - ox) / scale)),
        y = Math.max(0, (int) ((Math.min(start.y, end.y) - oy) / scale));
    int right = Math.min(image.getWidth(), (int) ((Math.max(start.x, end.x) - ox) / scale)),
        bottom = Math.min(image.getHeight(), (int) ((Math.max(start.y, end.y) - oy) / scale));
    if (right <= x || bottom <= y) return null;
    return new Rectangle(x, y, right - x, bottom - y);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D draw = (Graphics2D) g.create();
    if (image == null) {
      draw.setColor(Design.MUTED);
      draw.drawString("Chọn ảnh JPG / PNG; kéo chuột khoanh vùng số cần đọc.", 16, 90);
    } else {
      double scale = scale();
      int w = (int) (image.getWidth() * scale), h = (int) (image.getHeight() * scale);
      draw.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      draw.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
      if (region != null) {
        draw.setColor(Ui.GREEN);
        draw.setStroke(new BasicStroke(2));
        draw.drawRect(
            (getWidth() - w) / 2 + (int) (region.x() * w),
            (getHeight() - h) / 2 + (int) (region.y() * h),
            (int) (region.width() * w),
            (int) (region.height() * h));
      }
    }
    draw.dispose();
  }
}
