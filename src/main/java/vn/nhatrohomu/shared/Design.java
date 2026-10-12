package vn.nhatrohomu.shared;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.awt.geom.*;
import java.io.InputStream;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;

/** Chỉ lo giao diện: palette, chữ, thẻ bo góc và icon vector nhỏ, không đổi nghiệp vụ. */
public final class Design {
  public enum Mode {
    HOMURA,
    MADOKA
  }

  public static Mode mode = Mode.HOMURA;
  public static Color INK, MUTED, LINE, MINT, NAV, SURFACE, NAV_TEXT, ON_ACCENT;

  public static void setup() {
    apply(Mode.HOMURA);
  }

  public static void setup(java.nio.file.Path directory) {
    Mode saved = Mode.HOMURA;
    try {
      saved = Mode.valueOf(java.nio.file.Files.readString(directory.resolve("theme.txt")).trim());
    } catch (Exception ignored) {
    }
    apply(saved);
  }

  public static void choose(Mode selected, java.nio.file.Path directory)
      throws java.io.IOException {
    java.nio.file.Path file = directory.resolve("theme.txt"),
        temp = directory.resolve("theme.txt.tmp");
    java.nio.file.Files.writeString(temp, selected.name());
    java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    apply(selected);
  }

  public static void apply(Mode selected) {
    mode = selected;
    boolean dark = selected == Mode.HOMURA;
    if (dark) {
      com.formdev.flatlaf.FlatDarkLaf.setup();
    } else {
      FlatLightLaf.setup();
    }
    INK = new Color(dark ? 0xF0ECF7 : 0x3B2838);
    MUTED = new Color(dark ? 0xAAA2BE : 0x8A7082);
    LINE = new Color(dark ? 0x343044 : 0xEADDE5);
    MINT = new Color(dark ? 0x32263F : 0xFCE6EE);
    NAV = new Color(dark ? 0x14111D : 0xF6EDF2);
    NAV_TEXT = new Color(dark ? 0xF0ECF7 : 0x4A3344);
    SURFACE = new Color(dark ? 0x23202E : 0xFFFFFF);
    Ui.BACKGROUND = new Color(dark ? 0x1B1824 : 0xFCF7FA);
    Ui.GREEN = new Color(dark ? 0xC3A1F5 : 0xAF3E72);
    ON_ACCENT = new Color(dark ? 0x241532 : 0xFFFFFF);
    Font font = new Font("Segoe UI", Font.PLAIN, 13);
    try (InputStream in = Design.class.getResourceAsStream("/fonts/BeVietnamPro-Regular.ttf")) {
      if (in != null) {
        font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(13f);
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      }
    } catch (Exception ignored) {
    }
    UIManager.put("defaultFont", new FontUIResource(font));
    UIManager.put("Label.foreground", INK);
    UIManager.put("Panel.background", Ui.BACKGROUND);
    UIManager.put("Button.background", SURFACE);
    UIManager.put("Button.foreground", INK);
    UIManager.put("Button.borderColor", LINE);
    UIManager.put("Button.arc", 14);
    UIManager.put("Component.arc", 12);
    UIManager.put("TextComponent.arc", 12);
    UIManager.put("Button.margin", new Insets(9, 16, 9, 16));
    UIManager.put("TextField.margin", new Insets(9, 10, 9, 10));
    UIManager.put("TextField.background", SURFACE);
    UIManager.put("TextField.foreground", INK);
    UIManager.put("ComboBox.background", SURFACE);
    UIManager.put("ComboBox.foreground", INK);
    UIManager.put("Component.borderColor", LINE);
    UIManager.put("Component.focusColor", Ui.GREEN);
    UIManager.put("Component.accentColor", Ui.GREEN);
    UIManager.put("Table.background", SURFACE);
    UIManager.put("Table.foreground", INK);
    UIManager.put("Table.showVerticalLines", false);
    UIManager.put("Table.showHorizontalLines", true);
    UIManager.put("Table.gridColor", LINE);
    UIManager.put("Table.selectionBackground", MINT);
    UIManager.put("Table.selectionForeground", INK);
    UIManager.put("TableHeader.background", SURFACE);
    UIManager.put("TableHeader.foreground", MUTED);
    UIManager.put("ScrollPane.background", SURFACE);
    UIManager.put("Viewport.background", SURFACE);
    UIManager.put("ScrollBar.width", 8);
  }

  public static JPanel banner() {
    JPanel p =
        new JPanel(new BorderLayout()) {
          protected void paintComponent(Graphics g) {
            Graphics2D d = (Graphics2D) g.create();
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean dark = mode == Mode.HOMURA;
            d.setPaint(
                new GradientPaint(
                    0,
                    0,
                    new Color(dark ? 0x33233F : 0xFBE4ED),
                    getWidth(),
                    getHeight(),
                    new Color(dark ? 0x252233 : 0xF5ECFA)));
            d.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            d.setColor(new Color(dark ? 0xB99AD9 : 0xD79DBA));
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .20f));
            d.setStroke(new BasicStroke(1.5f));
            int x = getWidth() - 105, y = getHeight() / 2;
            if (dark) {
              for (int r : new int[] {42, 62, 80}) d.drawOval(x - r, y - r, r * 2, r * 2);
              for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                d.drawLine(
                    x + (int) (50 * Math.cos(a)),
                    y + (int) (50 * Math.sin(a)),
                    x + (int) (60 * Math.cos(a)),
                    y + (int) (60 * Math.sin(a)));
              }
              d.drawLine(x, y, x + 28, y - 24);
              d.drawLine(x, y, x, y - 34);
            } else {
              for (int i = 0; i < 5; i++) {
                double a = i * 2 * Math.PI / 5;
                d.drawOval(
                    x - 28 + (int) (29 * Math.cos(a)), y - 28 + (int) (29 * Math.sin(a)), 56, 56);
              }
              d.drawOval(x - 13, y - 13, 26, 26);
            }
            d.dispose();
            super.paintComponent(g);
          }
        };
    p.setOpaque(false);
    p.setBorder(BorderFactory.createEmptyBorder(19, 24, 19, 24));
    JPanel text = clear(new BorderLayout(0, 8));
    text.add(
        label(
            mode == Mode.HOMURA ? "HOMURA  /  ĐÊM TĨNH LẶNG" : "MADOKA  /  MỘT NGÀY NHẸ NHÀNG",
            10,
            Ui.GREEN,
            true),
        BorderLayout.NORTH);
    text.add(label("Mỗi căn phòng, một câu chuyện.", 23, INK, true), BorderLayout.CENTER);
    text.add(
        label("Giữ những con số rõ ràng. Dành thời gian cho điều bạn quan tâm.", 11, MUTED, false),
        BorderLayout.SOUTH);
    p.add(text, BorderLayout.CENTER);
    return p;
  }

  public static JLabel label(String text, int size, Color color, boolean bold) {
    JLabel l = new JLabel(text);
    l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
    l.setForeground(color);
    return l;
  }

  public static JPanel clear(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setOpaque(false);
    return p;
  }

  public static JPanel card(java.awt.LayoutManager layout, int padding) {
    JPanel panel = card(SURFACE);
    panel.setLayout(layout);
    panel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    return panel;
  }

  public static JPanel card(Color fill) {
    return new JPanel() {
      {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      }

      protected void paintComponent(Graphics g) {
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.setColor(fill);
        d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        d.setColor(LINE);
        d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        d.dispose();
        super.paintComponent(g);
      }
    };
  }

  public static JButton primary(JButton b) {
    b.setBackground(Ui.GREEN);
    b.setForeground(ON_ACCENT);
    b.putClientProperty("JButton.buttonType", null);
    return b;
  }

  public static JLabel badge(String text) {
    JLabel l = label(text, 11, Ui.GREEN, false);
    l.setOpaque(true);
    l.setBackground(MINT);
    l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    return l;
  }

  public static Icon icon(String kind, Color color) {
    return new Icon() {
      public int getIconWidth() {
        return 20;
      }

      public int getIconHeight() {
        return 20;
      }

      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D d = (Graphics2D) g.create();
        d.translate(x, y);
        d.setColor(color);
        d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (kind) {
          case "rooms" -> {
            for (int row = 0; row < 2; row++)
              for (int col = 0; col < 2; col++)
                d.drawRoundRect(2 + col * 9, 2 + row * 9, 6, 6, 2, 2);
          }
          case "bill" -> {
            d.drawRoundRect(4, 1, 12, 18, 3, 3);
            d.drawLine(7, 6, 13, 6);
            d.drawLine(7, 10, 13, 10);
            d.drawLine(7, 14, 11, 14);
          }
          case "water" -> {
            Path2D p = new Path2D.Double();
            p.moveTo(10, 1);
            p.curveTo(8, 5, 3, 10, 3, 13);
            p.curveTo(3, 21, 17, 21, 17, 13);
            p.curveTo(17, 10, 12, 5, 10, 1);
            d.draw(p);
          }
          case "bolt" -> {
            Path2D p = new Path2D.Double();
            p.moveTo(12, 1);
            p.lineTo(4, 11);
            p.lineTo(10, 11);
            p.lineTo(8, 19);
            p.lineTo(16, 8);
            p.lineTo(10, 8);
            p.closePath();
            d.draw(p);
          }
          case "settings" -> {
            d.drawOval(4, 4, 12, 12);
            d.drawOval(8, 8, 4, 4);
            for (int i = 0; i < 8; i++) {
              double a = i * Math.PI / 4;
              d.drawLine(
                  (int) (10 + 7 * Math.cos(a)),
                  (int) (10 + 7 * Math.sin(a)),
                  (int) (10 + 9 * Math.cos(a)),
                  (int) (10 + 9 * Math.sin(a)));
            }
          }
          default -> {
            d.drawOval(2, 2, 16, 16);
            d.drawLine(6, 10, 9, 13);
            d.drawLine(9, 13, 14, 7);
          }
        }
        d.dispose();
      }
    };
  }
}
