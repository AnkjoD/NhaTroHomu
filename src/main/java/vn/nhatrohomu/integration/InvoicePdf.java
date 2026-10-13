package vn.nhatrohomu.integration;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Printable invoice using saved rates and an embedded Vietnamese font. */
public final class InvoicePdf {
  private InvoicePdf() {}

  public static void write(Path path, Bill bill, List<Payment> payments) throws Exception {
    byte[] fontBytes;
    try (var stream = InvoicePdf.class.getResourceAsStream("/fonts/BeVietnamPro-Regular.ttf")) {
      fontBytes = stream.readAllBytes();
    }
    BaseFont base =
        BaseFont.createFont(
            "BeVietnamPro.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
    Font normal = new Font(base, 11),
        title = new Font(base, 24, Font.NORMAL, new Color(91, 66, 132));
    // Create completely before replacing the chosen destination if generation fails.
    Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "invoice-", ".pdf");
    try {
      try (var output = Files.newOutputStream(temporary)) {
        Document document = new Document(PageSize.A4, 44, 44, 42, 42);
        PdfWriter.getInstance(document, output);
        document.open();
        try {
          document.add(new Paragraph("NHATROHOMU", title));
          document.add(
              new Paragraph(
                  bill.contractId() == null && bill.rent().signum() == 0
                      ? "PHIẾU TIỀN ĐIỆN & NƯỚC"
                      : "PHIẾU TIỀN PHÒNG",
                  new Font(base, 15)));
          document.add(
              new Paragraph("Phòng: " + bill.roomName() + "   •   Kỳ: " + bill.month(), normal));
          if (bill.contractId() != null)
            document.add(new Paragraph("Khoảng ngày tính tiền: " + bill.periodLabel(), normal));
          if (bill.contractId() != null)
            document.add(
                new Paragraph(
                    "Người thuê: " + bill.tenantName() + "\nHạn thu: " + bill.dueDate(), normal));

          document.add(new Paragraph(" ", normal));
          PdfPTable table = new PdfPTable(new float[] {2.1f, 2.4f, 1.5f, 1.7f, 2f});
          table.setWidthPercentage(100);
          String[] headings = {"Khoản", "Số trước → sau", "Lượng dùng", "Đơn giá", "Thành tiền"};
          for (String heading : headings) cell(table, heading, normal, true);
          row(
              table,
              normal,
              "Điện",
              Ui.n(bill.electricityOld()) + " -> " + Ui.n(bill.electricityNew()),
              Ui.n(bill.electricityUsed()) + " kWh",
              Ui.money(bill.electricityRate()),
              Ui.money(bill.electricityUsed().multiply(bill.electricityRate())));
          row(
              table,
              normal,
              bill.waterMode().equals("METER") ? "Nước" : "Nước cố định",
              bill.waterMode().equals("METER")
                  ? Ui.n(bill.waterOld()) + " -> " + Ui.n(bill.waterNew())
                  : "Theo mức chỉ định",
              Ui.n(bill.waterUsed()) + " m³",
              Ui.money(bill.waterRate()),
              Ui.money(bill.waterCost()));
          if (bill.contractId() != null || bill.rent().signum() > 0) {
            boolean partial =
                !bill.periodStart().isBlank()
                    && (!bill.periodStart().endsWith("-01")
                        || !bill.periodEnd()
                            .equals(
                                java.time.YearMonth.parse(bill.month()).atEndOfMonth().toString()));
            String rentPeriod =
                partial
                    ? (java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.parse(bill.periodStart()),
                                java.time.LocalDate.parse(bill.periodEnd()))
                            + 1)
                        + " ngày"
                    : "1 tháng";
            row(
                table,
                normal,
                "Tiền phòng",
                bill.periodLabel(),
                rentPeriod,
                partial ? "Theo kỳ ở" : Ui.money(bill.rent()),
                Ui.money(bill.rent()));
            for (Charge fee : bill.fees())
              row(
                  table,
                  normal,
                  fee.label(),
                  "Phí định kỳ",
                  "1 kỳ",
                  Ui.money(fee.amount()),
                  Ui.money(fee.amount()));
          }
          document.add(table);
          BigDecimal received =
              payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
          document.add(
              new Paragraph("Tổng phải thu: " + Ui.money(bill.total()), new Font(base, 15)));
          BigDecimal remaining = bill.total().subtract(received);
          if (remaining.signum() > 0) {
            if (received.signum() > 0)
              document.add(new Paragraph("Đã trả kỳ này: " + Ui.money(received), normal));
            document.add(
                new Paragraph("CÒN NỢ KỲ NÀY: " + Ui.money(remaining), new Font(base, 15)));
          }
        } finally {
          document.close();
        }
      }
      Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void cell(PdfPTable table, String value, Font font, boolean header) {
    PdfPCell cell = new PdfPCell(new Phrase(value, font));
    cell.setPadding(9);
    cell.setBorderColor(new Color(220, 216, 229));
    if (header) cell.setBackgroundColor(new Color(242, 237, 250));
    table.addCell(cell);
  }

  private static void row(PdfPTable table, Font font, String... values) {
    for (String value : values) cell(table, value, font, false);
  }
}
