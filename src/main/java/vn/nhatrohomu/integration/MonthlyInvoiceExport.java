package vn.nhatrohomu.integration;

import java.nio.file.*;
import java.util.*;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Models.Bill;

/** Publish a complete folder only after every saved invoice has been rendered successfully. */
public final class MonthlyInvoiceExport {
  private MonthlyInvoiceExport() {}

  public record Result(Path directory, int count) {}

  public static Result write(Path parent, String month, Repository repository) throws Exception {
    return write(parent, month, repository, false);
  }

  public static Result write(Path parent, String month, Repository repository, boolean png)
      throws Exception {
    DataRules.period(month);
    List<Bill> invoices =
        repository.billsBetween(month, month).stream()
            .sorted(Comparator.comparing(Bill::roomName).thenComparingLong(Bill::id))
            .toList();
    if (invoices.isEmpty())
      throw new IllegalArgumentException("Tháng này chưa có hóa đơn để xuất phiếu.");
    Path directory = parent.toAbsolutePath().normalize();
    if (!Files.isDirectory(directory))
      throw new IllegalArgumentException("Chọn thư mục lưu phiếu.");
    Path temporary = Files.createTempDirectory(directory, ".phieu-" + month + "-");
    Path published =
        directory.resolve(
            "Phieu-phong-" + month + "-" + UUID.randomUUID().toString().substring(0, 8));
    try {
      for (Bill bill : invoices) {
        String room =
            bill.roomName().replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "-").replaceAll("\\s+", "-");
        room =
            room.codePoints()
                .limit(60)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        String name = "phieu-" + room + "-" + month + "-hd-" + bill.id() + (png ? ".png" : ".pdf");
        if (png) InvoiceImage.write(temporary.resolve(name), bill, repository.payments(bill.id()));
        else InvoicePdf.write(temporary.resolve(name), bill, repository.payments(bill.id()));
      }
      Files.move(temporary, published);
      return new Result(published, invoices.size());
    } finally {
      if (Files.exists(temporary)) {
        try (var files = Files.list(temporary)) {
          for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(temporary);
      }
    }
  }
}
