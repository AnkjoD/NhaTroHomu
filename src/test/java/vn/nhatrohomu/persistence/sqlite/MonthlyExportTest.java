package vn.nhatrohomu.persistence.sqlite;

import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.MonthlyInvoiceExport;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

public final class MonthlyExportTest {
  static int checks;

  static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  static Bill utility(SqliteRepository repo, Room room, String month) throws Exception {
    Readings opening = repo.previous(room);
    return BillingService.calculate(
        room,
        month,
        opening,
        opening.electricity().add(BigDecimal.ONE),
        opening.water(),
        BigDecimal.ZERO,
        repo.rates(),
        "");
  }

  static String pdf(Path file) throws Exception {
    try (PdfReader reader = new PdfReader(Files.readAllBytes(file))) {
      StringBuilder text = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++)
        text.append(new PdfTextExtractor(reader).getTextFromPage(page));
      return text.toString();
    }
  }

  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("monthly-export-");
    SqliteRepository repo = new SqliteRepository(directory.resolve("book.db"));
    String month = YearMonth.now().toString();
    for (int i = 1; i <= 60; i++) {
      String name = i == 1 ? "P/01" : i == 2 ? "P:01" : "P" + i;
      repo.saveRoom(new Room(0, name, "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
      Room room =
          repo.rooms().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
      repo.saveBill(utility(repo, room, month), null, null);
    }
    List<Bill> bills = repo.billsBetween(month, month);
    Bill first = bills.getFirst(), second = bills.get(1);
    BigDecimal received = first.total().divide(BigDecimal.TWO, 0, java.math.RoundingMode.DOWN);
    repo.addPayment(
        first.id(), BigDecimal.ONE, LocalDate.now().toString(), "Thử đảo khoản nhập nhầm");
    repo.reversePayment(repo.payments(first.id()).getFirst().id(), "Dữ liệu thử");
    repo.addPayment(first.id(), received, LocalDate.now().toString(), "Tiền mặt");
    repo.addPayment(second.id(), second.total(), LocalDate.now().toString(), "Chuyển khoản");
    repo.saveBill(
        utility(repo, repo.rooms().getFirst(), YearMonth.now().plusMonths(1).toString()),
        null,
        null);
    repo.saveRoom(
        new Room(0, "Phòng trống", "", BigDecimal.ZERO, BigDecimal.ZERO, "METER", null, true));
    Room vacant =
        repo.rooms().stream().filter(r -> r.name().equals("Phòng trống")).findFirst().orElseThrow();
    repo.rentals().saveTenant(new Tenant(0, "Người thử kỳ ngắn", "", "", true));
    Tenant tenant = repo.rentals().tenants().getFirst();
    repo.rentals()
        .createContract(
            new Contract(
                0,
                vacant.id(),
                vacant.name(),
                tenant.id(),
                tenant.name(),
                month + "-01",
                "",
                "",
                new BigDecimal("2000000"),
                BigDecimal.ZERO,
                5,
                List.of()),
            new Readings(BigDecimal.ZERO, BigDecimal.ZERO));
    Contract lease = repo.rentals().contracts().getFirst();
    repo.rentals().checkout(TurnoverTest.bill(repo, vacant, lease, month + "-10", "10", "1", ""));
    int before = repo.billCount();
    Path exports = directory.resolve("exports");
    Files.createDirectories(exports);
    var result = MonthlyInvoiceExport.write(exports, month, repo);
    check(
        result.count() == 61,
        "Exports every invoice across pages, excludes other months and does not invent bills");
    List<Path> files;
    try (var paths = Files.list(result.directory())) {
      files = paths.toList();
    }
    check(
        files.size() == 61
            && files.stream().allMatch(p -> p.getFileName().toString().endsWith(".pdf")),
        "One separate PDF per saved invoice");
    check(
        files.stream().map(p -> p.getFileName().toString()).distinct().count() == 61,
        "Sanitized room names cannot collide because invoice ID is included");
    check(
        files.stream().allMatch(p -> p.getParent().equals(result.directory())),
        "Room names cannot escape the export folder");
    Path firstPdf =
        files.stream()
            .filter(p -> p.toString().endsWith("-hd-" + first.id() + ".pdf"))
            .findFirst()
            .orElseThrow();
    String firstText = pdf(firstPdf);
    check(
        firstText.contains("Đã trả một phần") && firstText.contains(Ui.money(received)),
        "Partial receipts show actual received amount and remaining payment");
    check(
        !firstText.contains("Thử đảo khoản nhập nhầm"),
        "Reversed receipt is excluded from tenant statement");
    Path secondPdf =
        files.stream()
            .filter(p -> p.toString().endsWith("-hd-" + second.id() + ".pdf"))
            .findFirst()
            .orElseThrow();
    check(pdf(secondPdf).contains("Đã thanh toán đủ"), "Settled invoice is clearly marked paid");
    Path partialPdf =
        files.stream()
            .filter(p -> p.getFileName().toString().contains("Phòng-trống"))
            .findFirst()
            .orElseThrow();
    check(
        pdf(partialPdf).contains("10 ngày") && !pdf(partialPdf).contains("1 tháng"),
        "Partial rent is not misrepresented as a full month");
    var again = MonthlyInvoiceExport.write(exports, month, repo);
    check(
        !again.directory().equals(result.directory()) && Files.exists(firstPdf),
        "Repeated export does not overwrite prior output");
    int[] calls = {0};
    Repository failing =
        (Repository)
            java.lang.reflect.Proxy.newProxyInstance(
                Repository.class.getClassLoader(),
                new Class<?>[] {Repository.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("payments") && ++calls[0] == 2)
                    throw new SQLException("Synthetic export failure");
                  return method.invoke(repo, arguments);
                });
    try {
      MonthlyInvoiceExport.write(exports, month, failing);
      throw new AssertionError("Injected failure ignored");
    } catch (SQLException expected) {
      checks++;
    }
    try (var paths = Files.list(exports)) {
      check(
          paths.count() == 2,
          "Failed export leaves neither published partial folders nor temporary files");
    }
    check(
        repo.billCount() == before && repo.received(first.id()).compareTo(received) == 0,
        "Export does not record receipts or alter financial data");
    System.out.println("PASS: " + checks + " monthly PDF export and financial statement checks.");
  }
}
