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
                  bill.contractId() == null ? "HÓA ĐƠN ĐIỆN & NƯỚC" : "HÓA ĐƠN PHÒNG TRỌ",
                  new Font(base, 15)));
          document.add(
              new Paragraph(
                  "Phòng: "
                      + bill.roomName()
                      + "   •   Kỳ: "
                      + bill.month()
                      + "   •   Mã: "
                      + bill.id(),
                  normal));
          if (bill.contractId() != null)
            document.add(
                new Paragraph(
                    "Người thuê: "
                        + bill.tenantName()
                        + "   •   Hợp đồng #"
                        + bill.contractId()
                        + "\nHạn thu: "
                        + bill.dueDate(),
                    normal));
          document.add(new Paragraph(" ", normal));
          PdfPTable table = new PdfPTable(new float[] {2.1f, 2.4f, 1.5f, 1.7f, 2f});
          table.setWidthPercentage(100);
          String[] headings = {"Khoản", "Chỉ số", "Lượng", "Đơn giá", "Thành tiền"};
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
              Ui.money(bill.waterUsed().multiply(bill.waterRate())));
          if (bill.contractId() != null) {
            row(
                table,
                normal,
                "Tiền phòng",
                "Kỳ " + bill.month(),
                "1 tháng",
                Ui.money(bill.rent()),
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
          document.add(
              new Paragraph(
                  "Đã thu: "
                      + Ui.money(received)
                      + "   •   Còn thiếu: "
                      + Ui.money(bill.total().subtract(received)),
                  normal));
          document.add(
              new Paragraph(
                  bill.contractId() == null
                      ? "Tổng được làm tròn đến đồng. Không bao gồm tiền thuê phòng."
                      : "Tổng được làm tròn đến đồng. Tiền cọc được quản lý riêng.",
                  normal));
          if (!bill.note().isBlank())
            document.add(new Paragraph("Ghi chú: " + bill.note(), normal));
          if (!payments.isEmpty()) {
            document.add(new Paragraph("\nLỊCH SỬ THU TIỀN", new Font(base, 13)));
            for (Payment p : payments)
              document.add(
                  new Paragraph(
                      (p.date().isBlank() ? "Ngày chưa được ghi nhận" : p.date())
                          + "  |  "
                          + Ui.money(p.amount())
                          + "  |  "
                          + p.note(),
                      normal));
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
