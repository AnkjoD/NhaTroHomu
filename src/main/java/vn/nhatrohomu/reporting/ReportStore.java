package vn.nhatrohomu.reporting;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import vn.nhatrohomu.persistence.sqlite.DatabaseAccess;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;

/** All report sections are read within one database transaction, without photo blobs. */
public final class ReportStore {
  public record Debt(
      long id,
      String room,
      String tenant,
      String period,
      String due,
      BigDecimal total,
      BigDecimal received,
      BigDecimal remaining,
      long daysLate) {}

  public record Movement(
      String date, String room, String period, BigDecimal amount, String type, String note) {}

  public record Missing(
      long contractId, String room, String tenant, String period, boolean archived) {}

  public record Event(
      long id, String timestamp, String action, String entity, long entityId, String detail) {}

  public record Voided(
      long id, String room, String period, BigDecimal total, String date, String reason) {}

  public record Snapshot(
      List<Debt> debts,
      List<Movement> movements,
      List<Missing> missing,
      List<Event> events,
      List<Voided> voided,
      BigDecimal collectedInRange,
      int unknownDates,
      List<BigDecimal> aging,
      List<ExpenseStore.Entry> expenses,
      BigDecimal expensesInRange) {}

  private final Path database;

  public ReportStore(Path database) {
    this.database = database;
  }

  public Snapshot load(YearMonth period, Long roomFilter) throws SQLException {
    DataRules.period(period.toString());
    LocalDate today = LocalDate.now();
    try (Connection c = DatabaseAccess.open(database)) {
      c.setAutoCommit(false);
      Map<Long, BigDecimal> collected = new HashMap<>();
      List<Movement> movements = new ArrayList<>();
      int unknown = 0;
      BigDecimal cash = BigDecimal.ZERO;
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT p.*,b.room_id,b.room_name,b.month,r.reversed_at,r.reason FROM payments p"
                      + " JOIN bills b ON b.id=p.bill_id LEFT JOIN payment_reversals r ON"
                      + " r.payment_id=p.id ORDER BY p.received_date,p.id")) {
        while (r.next()) {
          if (roomFilter != null && roomFilter != r.getLong("room_id")) continue;
          BigDecimal amount = new BigDecimal(r.getString("amount"));
          if (r.getString("reversed_at") == null)
            collected.merge(r.getLong("bill_id"), amount, BigDecimal::add);
          String date = r.getString("received_date");
          if (date.isBlank()) unknown++;
          else if (YearMonth.from(RentalService.date(date)).equals(period)) {
            movements.add(
                new Movement(
                    date,
                    r.getString("room_name"),
                    r.getString("month"),
                    amount,
                    "Thu",
                    r.getString("note")));
            cash = cash.add(amount);
          }
          if (r.getString("reversed_at") != null) {
            String reversedDate =
                Instant.parse(r.getString("reversed_at"))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString();
            if (YearMonth.from(RentalService.date(reversedDate)).equals(period)) {
              movements.add(
                  new Movement(
                      reversedDate,
                      r.getString("room_name"),
                      r.getString("month"),
                      amount.negate(),
                      "Đảo thu",
                      r.getString("reason")));
              cash = cash.subtract(amount);
            }
          }
        }
      }
      List<Debt> debts = new ArrayList<>();
      List<Voided> voided = new ArrayList<>();
      Set<String> issued = new HashSet<>();
      List<BigDecimal> aging = new ArrayList<>(Collections.nCopies(5, BigDecimal.ZERO));
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT"
                      + " id,room_id,room_name,tenant_name,month,due_date,total,voided_at,void_reason,contract_id"
                      + " FROM bills ORDER BY month,room_name,id")) {
        while (r.next()) {
          if (roomFilter != null && roomFilter != r.getLong("room_id")) continue;
          long id = r.getLong("id");
          BigDecimal total = new BigDecimal(r.getString("total"));
          if (r.getString("voided_at") != null) {
            voided.add(
                new Voided(
                    id,
                    r.getString("room_name"),
                    r.getString("month"),
                    total,
                    r.getString("voided_at"),
                    r.getString("void_reason")));
            continue;
          }
          if (r.getObject("contract_id") != null)
            issued.add(r.getLong("contract_id") + ":" + r.getString("month"));
          BigDecimal paid = collected.getOrDefault(id, BigDecimal.ZERO),
              balance = total.subtract(paid);
          if (balance.signum() <= 0) continue;
          String due = r.getString("due_date");
          long days =
              due.isBlank()
                  ? -1
                  : Math.max(0, ChronoUnit.DAYS.between(RentalService.date(due), today));
          debts.add(
              new Debt(
                  id,
                  r.getString("room_name"),
                  r.getString("tenant_name"),
                  r.getString("month"),
                  due,
                  total,
                  paid,
                  balance,
                  days));
          if (days >= 0) {
            int bucket = days == 0 ? 0 : days <= 30 ? 1 : days <= 60 ? 2 : days <= 90 ? 3 : 4;
            aging.set(bucket, aging.get(bucket).add(balance));
          }
        }
      }
      debts.sort(Comparator.comparingLong(Debt::daysLate).reversed().thenComparing(Debt::period));
      List<Missing> missing = new ArrayList<>();
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT k.id,k.room_id,r.name room_name,t.name"
                      + " tenant_name,r.active,k.start_date,CASE WHEN k.closed_date='' THEN"
                      + " coalesce(nullif(k.end_date,''),'9999-12-31') ELSE k.closed_date END"
                      + " effective_end FROM contracts k JOIN rooms r ON r.id=k.room_id JOIN"
                      + " tenants t ON t.id=k.tenant_id WHERE k.cancelled_at IS NULL ORDER BY"
                      + " r.name,k.start_date")) {
        while (r.next()) {
          if (roomFilter != null && roomFilter != r.getLong("room_id")) continue;
          YearMonth start = YearMonth.from(RentalService.date(r.getString("start_date"))),
              end = YearMonth.from(RentalService.date(r.getString("effective_end")));
          if (end.isAfter(period)) end = period;
          for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1))
            if (!issued.contains(r.getLong("id") + ":" + month))
              missing.add(
                  new Missing(
                      r.getLong("id"),
                      r.getString("room_name"),
                      r.getString("tenant_name"),
                      month.toString(),
                      !r.getBoolean("active")));
        }
      }
      List<Event> events = new ArrayList<>();
      // Audit is intentionally global, with its scope labelled separately from room reports.
      try (Statement s = c.createStatement();
          ResultSet r = s.executeQuery("SELECT * FROM audit_events ORDER BY id DESC LIMIT 500")) {
        while (r.next())
          events.add(
              new Event(
                  r.getLong("id"),
                  r.getString("occurred_at"),
                  r.getString("action"),
                  r.getString("entity_type"),
                  r.getLong("entity_id"),
                  r.getString("detail")));
      }
      ExpenseStore.Summary expenses = ExpenseStore.load(c, period, roomFilter);
      movements.sort(Comparator.comparing(Movement::date));
      c.commit();
      return new Snapshot(
          List.copyOf(debts),
          List.copyOf(movements),
          List.copyOf(missing),
          List.copyOf(events),
          List.copyOf(voided),
          cash,
          unknown,
          List.copyOf(aging),
          expenses.entries(),
          expenses.netPaid());
    }
  }
}
