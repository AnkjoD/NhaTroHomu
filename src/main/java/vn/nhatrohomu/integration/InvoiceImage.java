package vn.nhatrohomu.integration;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.text.AttributedString;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Compact tenant-facing slip; excludes internal notes and payment ledger details. */
public final class InvoiceImage {
  private InvoiceImage() {}

  private static final int WIDTH = 1200, MARGIN = 32;
  private static final int[] COLUMNS = {190, 140, 140, 160, 230, 276};

  private static int columnWidth(String[] row, int column) {
    if (row.length == 2) {
      int amountWidth = COLUMNS[COLUMNS.length - 1];
      return column == 0 ? WIDTH - 2 * MARGIN - amountWidth : amountWidth;
    }
    return COLUMNS[column];
  }

  private static Font font(ReceiptStyle style) throws Exception {
    if (!style.font().equals("Be Vietnam Pro"))
      return new Font(style.font(), Font.PLAIN, style.size());
    try (var stream = InvoiceImage.class.getResourceAsStream("/fonts/BeVietnamPro-Regular.ttf")) {
      return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont((float) style.size());
    }
  }

  private static List<TextLayout> wrap(
      String text, Font font, FontRenderContext context, int width) {
    if (text.isEmpty()) text = " ";
    AttributedString value = new AttributedString(text);
    value.addAttribute(java.awt.font.TextAttribute.FONT, font);
    LineBreakMeasurer measure = new LineBreakMeasurer(value.getIterator(), context);
    List<TextLayout> lines = new ArrayList<>();
    while (measure.getPosition() < text.length()) lines.add(measure.nextLayout(width));
    return lines;
  }

  public static List<String[]> rows(Bill bill) {
    List<String[]> result = new ArrayList<>();
    result.add(
        new String[] {
          "Điện",
          Ui.n(bill.electricityOld()),
          Ui.n(bill.electricityNew()),
          Ui.n(bill.electricityUsed()) + " kWh",
          Ui.money(bill.electricityRate()),
          Ui.money(bill.electricityUsed().multiply(bill.electricityRate()))
        });
    boolean meter = bill.waterMode().equals("METER");
    result.add(
        new String[] {
          meter ? "Nước" : "Nước khoán",
          meter ? Ui.n(bill.waterOld()) : "",
          meter ? Ui.n(bill.waterNew()) : "",
          Ui.n(bill.waterUsed()) + " m³",
          Ui.money(bill.waterRate()),
          Ui.money(bill.waterCost())
        });
    if (bill.contractId() != null || bill.rent().signum() > 0) {
      String label = "Tiền phòng";
      if (!bill.periodStart().isBlank()) {
        LocalDate first = LocalDate.parse(bill.periodStart()),
            last = LocalDate.parse(bill.periodEnd());
        if (first.getDayOfMonth() != 1
            || last.getDayOfMonth() != YearMonth.from(last).lengthOfMonth())
          label += " (" + (java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1) + " ngày)";
      }
      result.add(new String[] {label, Ui.money(bill.rent())});
    }
    for (Charge fee : bill.fees())
      result.add(new String[] {fee.label(), "", "", "1 kỳ", "", Ui.money(fee.amount())});
    return result;
  }

  public static List<String> advanceLines(List<Payment> payments) {
    java.util.Map<String, BigDecimal> sources = new java.util.TreeMap<>();
    for (Payment payment : payments)
      if (payment.advanceId() != null)
        sources.merge(payment.sourceMonth(), payment.amount(), BigDecimal::add);
    return sources.entrySet().stream()
        .map(e -> "Dùng trả trước từ tháng " + e.getKey() + ": " + Ui.money(e.getValue()))
        .toList();
  }

  public static BufferedImage render(Bill bill, List<Payment> payments) throws Exception {
    return render(bill, payments, ReceiptStyle.current());
  }

  public static BufferedImage render(Bill bill, List<Payment> payments, ReceiptStyle style)
      throws Exception {
    Font normal = font(style),
        bold = normal.deriveFont(Font.BOLD),
        title = normal.deriveFont(Font.BOLD, style.size() * 1.45f);
    int lineHeight = Math.max(30, style.size() + 8);
    List<String[]> rows = rows(bill);
    rows.add(new String[] {"Tổng tiền", Ui.money(bill.total())});
    int totalRow = rows.size() - 1;
    BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    Graphics2D sizing = measure.createGraphics();
    FontRenderContext context = sizing.getFontRenderContext();
    List<TextLayout> titleLines = wrap(style.title(), title, context, WIDTH - 64);
    int titleLineHeight = title.getSize() + 10;
    List<String> headings = new ArrayList<>();
    headings.add(bill.roomName() + "  ·  Tháng " + bill.month());
    if (style.person() && !bill.tenantName().isBlank())
      headings.add("Người thuê: " + bill.tenantName());
    if (!bill.periodStart().isBlank()) headings.add("Kỳ ở: " + bill.periodLabel());
    if (style.due() && !bill.dueDate().isBlank()) headings.add("Hẹn thanh toán: " + bill.dueDate());
    int headerHeight =
        20
            + title.getSize()
            + (titleLines.size() - 1) * titleLineHeight
            + normal.getSize()
            + 12
            + 8;
    for (String heading : headings)
      headerHeight += wrap(heading, normal, context, WIDTH - 64).size() * lineHeight;
    int[] heights = new int[rows.size()];
    for (int r = 0; r < rows.size(); r++) {
      String[] row = rows.get(r);
      Font rowFont = r == totalRow ? bold : normal;
      int lines = 1;
      for (int c = 0; c < row.length; c++)
        lines = Math.max(lines, wrap(row[c], rowFont, context, columnWidth(row, c) - 20).size());
      heights[r] = lines * lineHeight + 24;
    }
    sizing.dispose();
    BigDecimal received =
        payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = bill.total().subtract(received);
    List<String> advanceLines = advanceLines(payments);
    boolean hasBalance = remaining.signum() > 0;
    boolean showReceived = hasBalance && received.signum() > 0;
    int summaryHeight = hasBalance ? 24 + title.getSize() : 0;
    if (showReceived) summaryHeight += lineHeight;
    int height =
        headerHeight
            + 58
            + java.util.Arrays.stream(heights).sum()
            + summaryHeight
            + MARGIN
            + advanceLines.size() * lineHeight;
    BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, WIDTH, height);
      g.setColor(new Color(68, 45, 107));
      g.setFont(title);
      int titleY = 20 + title.getSize();
      for (TextLayout line : titleLines) {
        line.draw(g, MARGIN, titleY);
        titleY += titleLineHeight;
      }
      int y = titleY - titleLineHeight + normal.getSize() + 12;
      for (String heading : headings) {
        for (TextLayout line : wrap(heading, normal, context, WIDTH - 64)) {
          line.draw(g, MARGIN, y);
          y += lineHeight;
        }
      }
      int top = headerHeight;
      paintRow(
          g,
          new String[] {"Khoản", "Số cũ", "Số mới", "Lượng dùng", "Đơn giá", "Thành tiền"},
          bold,
          context,
          top,
          58,
          true);
      top += 58;
      for (int i = 0; i < rows.size(); i++) {
        paintRow(g, rows.get(i), i == totalRow ? bold : normal, context, top, heights[i], false);
        top += heights[i];
      }
      int summaryY = top;
      g.setColor(new Color(68, 45, 107));
      if (showReceived) {
        summaryY += 24 + normal.getSize();
        g.setFont(normal);
        g.drawString("Đã trả kỳ này: " + Ui.money(received), MARGIN, summaryY);
      }
      if (hasBalance) {
        summaryY = top + summaryHeight;
        g.setFont(title);
        g.drawString("Tiền phải trả: " + Ui.money(remaining), MARGIN, summaryY);
      }
      g.setFont(normal);
      g.setColor(new Color(65, 85, 100));
      int sourceY = summaryY + lineHeight;
      for (String source : advanceLines) {
        g.drawString(source, MARGIN, sourceY);
        sourceY += lineHeight;
      }
    } finally {
      g.dispose();
    }
    return image;
  }

  private static void paintRow(
      Graphics2D g,
      String[] values,
      Font font,
      FontRenderContext context,
      int y,
      int height,
      boolean header) {
    int x = MARGIN;
    for (int col = 0; col < values.length; col++) {
      int width = columnWidth(values, col);
      g.setColor(header ? new Color(242, 237, 250) : Color.WHITE);
      g.fillRect(x, y, width, height);
      g.setColor(new Color(210, 205, 220));
      g.drawRect(x, y, width, height);
      g.setColor(new Color(40, 40, 45));
      int lineY = y + font.getSize() + 12;
      for (TextLayout line : wrap(values[col], font, context, width - 20)) {
        float textX = values.length == 2 && col == 0 ? x + (width - line.getAdvance()) / 2 : x + 10;
        line.draw(g, textX, lineY);
        lineY += Math.max(30, font.getSize() + 8);
      }
      x += width;
    }
  }

  public static void write(Path path, Bill bill, List<Payment> payments) throws Exception {
    Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "slip-", ".png");
    try {
      if (!ImageIO.write(render(bill, payments), "png", temporary.toFile()))
        throw new IllegalStateException("Không tạo được ảnh PNG.");
      Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static Transferable transferable(BufferedImage image) {
    return new Transferable() {
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {DataFlavor.imageFlavor};
      }

      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
      }

      public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
        return image;
      }
    };
  }
}
