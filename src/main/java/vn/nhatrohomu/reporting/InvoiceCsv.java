package vn.nhatrohomu.reporting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.CsvField;
import vn.nhatrohomu.shared.Models.Bill;

/** Shared invoice export for all rooms and the selected room history. */
public final class InvoiceCsv {
  private InvoiceCsv() {}

  public static final List<String> HEADERS =
      List.of(
          "Kỳ",
          "Phòng",
          "Người thuê",
          "Điện cũ kWh",
          "Điện mới kWh",
          "Điện dùng kWh",
          "Đơn giá điện VND",
          "Tiền điện VND",
          "Cách tính nước",
          "Nước cũ m³",
          "Nước mới m³",
          "Nước dùng m³",
          "Đơn giá nước VND",
          "Tiền nước VND",
          "Cộng điện nước VND",
          "Tiền phòng VND",
          "Phí VND",
          "Tổng VND",
          "Đã thu VND",
          "Còn thiếu VND",
          "Hạn thu",
          "Ghi chú");

  public static List<String> cells(Bill b, BigDecimal received) {
    return List.of(
        b.month(),
        b.roomName(),
        b.tenantName(),
        b.electricityOld().toPlainString(),
        b.electricityNew().toPlainString(),
        b.electricityUsed().toPlainString(),
        b.electricityRate().toPlainString(),
        b.electricityCost().toPlainString(),
        b.waterMode().equals("METER") ? "Đồng hồ" : "Khoán m³",
        b.waterMode().equals("METER") ? b.waterOld().toPlainString() : "",
        b.waterMode().equals("METER") ? b.waterNew().toPlainString() : "",
        b.waterUsed().toPlainString(),
        b.waterRate().toPlainString(),
        b.waterCost().toPlainString(),
        b.utilitiesCost().toPlainString(),
        b.rent().toPlainString(),
        b.feeTotal().toPlainString(),
        b.total().toPlainString(),
        received.toPlainString(),
        b.total().subtract(received).toPlainString(),
        b.dueDate(),
        b.note());
  }

  private static String line(List<String> cells) {
    return String.join(",", cells.stream().map(CsvField::escape).toList()) + "\r\n";
  }

  public static void write(Path path, List<Bill> bills, Repository repository) throws Exception {
    Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "invoice-", ".csv");
    try {
      try (var w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        w.write("\uFEFF" + line(HEADERS));
        for (Bill b : bills) w.write(line(cells(b, repository.received(b.id()))));
      }
      Files.move(temp, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
