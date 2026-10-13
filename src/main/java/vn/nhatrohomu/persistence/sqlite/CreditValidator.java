package vn.nhatrohomu.persistence.sqlite;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import vn.nhatrohomu.shared.DataRules;

/** Verify funding provenance and cash conservation independently of database constraints. */
final class CreditValidator {
  record Event(
      long bill,
      Long owner,
      BigDecimal cash,
      BigDecimal used,
      BigDecimal applied,
      BigDecimal saved,
      String date,
      String note,
      Instant created) {}

  record Source(
      long bill, long collection, Long owner, BigDecimal amount, String date, Instant created) {}

  static BigDecimal money(ResultSet r, String column) throws SQLException {
    BigDecimal amount = new BigDecimal(r.getString(column));
    DataRules.money(amount, column);
    return amount;
  }

  static void validate(Connection c) throws SQLException {
    Map<Long, Event> events = new HashMap<>();
    Set<String> requests = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM collection_events")) {
      while (r.next()) {
        long bill = r.getLong("bill_id");
        Long owner = r.getObject("owner_tenant_id") == null ? null : r.getLong("owner_tenant_id");
        CollectionStore.resolveOwner(c, bill, owner);
        BigDecimal cash = money(r, "cash"),
            used = money(r, "used_advance"),
            applied = money(r, "applied_cash"),
            saved = money(r, "saved_advance");
        DataRules.actualDate(r.getString("received_date"));
        DataRules.text(r.getString("note"), "Ghi chú thu", 2000, false);
        DataRules.text(r.getString("request_key"), "Mã lần thu", 100, true);
        int flag = r.getInt("use_advance");
        if ((flag != 0 && flag != 1)
            || (flag == 0 && used.signum() != 0)
            || cash.add(used).signum() == 0
            || cash.compareTo(applied.add(saved)) != 0
            || !requests.add(r.getString("request_key")))
          throw new IllegalArgumentException("Lần nhận tiền/trả trước không cân đối.");
        events.put(
            r.getLong("id"),
            new Event(
                bill,
                owner,
                cash,
                used,
                applied,
                saved,
                r.getString("received_date"),
                r.getString("note"),
                Instant.parse(r.getString("created_at"))));
      }
    }
    Map<Long, Source> sources = new HashMap<>();
    Set<Long> eventSources = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM advance_receipts")) {
      while (r.next()) {
        long eventId = r.getLong("collection_id");
        Event event = events.get(eventId);
        BigDecimal amount = money(r, "amount");
        Long owner = r.getObject("owner_tenant_id") == null ? null : r.getLong("owner_tenant_id");
        if (event == null
            || !eventSources.add(eventId)
            || amount.signum() <= 0
            || amount.compareTo(event.saved()) != 0
            || r.getLong("bill_id") != event.bill()
            || !Objects.equals(owner, event.owner())
            || !r.getString("received_date").equals(event.date())
            || !r.getString("note").equals(event.note()))
          throw new IllegalArgumentException("Khoản trả trước thiếu nguồn thu hoặc sai số tiền.");
        Instant created = Instant.parse(r.getString("created_at"));
        if (created.isBefore(event.created()))
          throw new IllegalArgumentException("Số dư xuất hiện trước lần nhận tiền.");
        sources.put(
            r.getLong("id"),
            new Source(event.bill(), eventId, owner, amount, event.date(), created));
      }
    }
    for (var entry : events.entrySet())
      if ((entry.getValue().saved().signum() > 0) != eventSources.contains(entry.getKey()))
        throw new IllegalArgumentException("Tiền dư chưa có chứng từ giữ trả trước.");
    Map<Long, Instant> reversedSources = new HashMap<>(), reversedPayments = new HashMap<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM advance_reversals")) {
      while (r.next()) {
        long id = r.getLong("advance_id");
        Source source = sources.get(id);
        Instant reversed = Instant.parse(r.getString("reversed_at"));
        DataRules.text(r.getString("reason"), "Lý do sửa trả trước", 2000, true);
        if (source == null
            || reversed.isBefore(source.created())
            || reversedSources.put(id, reversed) != null)
          throw new IllegalArgumentException("Đảo khoản trả trước thiếu nguồn hoặc sai thời điểm.");
      }
    }
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT payment_id,reversed_at FROM payment_reversals")) {
      while (r.next()) reversedPayments.put(r.getLong(1), Instant.parse(r.getString(2)));
    }
    Map<Long, BigDecimal> cashByEvent = new HashMap<>(),
        usedByEvent = new HashMap<>(),
        activeUsed = new HashMap<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM payments")) {
      while (r.next()) {
        if (r.getObject("collection_id") == null) {
          if (r.getObject("advance_id") != null)
            throw new IllegalArgumentException("Dùng trả trước thiếu lần ghi nhận.");
          continue;
        }
        long eventId = r.getLong("collection_id");
        Event event = events.get(eventId);
        if (event == null
            || event.bill() != r.getLong("bill_id")
            || !event.date().equals(r.getString("received_date")))
          throw new IllegalArgumentException("Thanh toán không khớp lần ghi nhận.");
        BigDecimal amount = money(r, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Khoản thanh toán cần dương.");
        if (r.getObject("advance_id") == null) {
          cashByEvent.merge(eventId, amount, BigDecimal::add);
          continue;
        }
        long id = r.getLong("advance_id");
        Source source = sources.get(id);
        if (source == null
            || source.collection() == eventId
            || event.date().compareTo(source.date()) < 0
            || event.created().isBefore(source.created()))
          throw new IllegalArgumentException(
              "Dùng trả trước thiếu nguồn hoặc xảy ra trước khi nhận tiền.");
        CollectionStore.Owner from = CollectionStore.resolveOwner(c, source.bill(), source.owner()),
            to = CollectionStore.resolveOwner(c, event.bill(), event.owner());
        if (from.month().compareTo(to.month()) >= 0
            || (to.tenant() != null
                ? !to.tenant().equals(from.tenant())
                : from.tenant() != null || to.room() != from.room()))
          throw new IllegalArgumentException("Tiền trả trước bị chuyển sai người/kỳ.");
        Instant reversedSource = reversedSources.get(id),
            reversedPayment = reversedPayments.get(r.getLong("id"));
        if (reversedSource != null
            && (!event.created().isBefore(reversedSource)
                || reversedPayment == null
                || reversedPayment.isAfter(reversedSource)))
          throw new IllegalArgumentException("Tiền trả trước đã đảo nhưng vẫn được dùng.");
        usedByEvent.merge(eventId, amount, BigDecimal::add);
        if (reversedPayment == null) activeUsed.merge(id, amount, BigDecimal::add);
      }
    }
    for (var entry : events.entrySet())
      if (entry
                  .getValue()
                  .applied()
                  .compareTo(cashByEvent.getOrDefault(entry.getKey(), BigDecimal.ZERO))
              != 0
          || entry
                  .getValue()
                  .used()
                  .compareTo(usedByEvent.getOrDefault(entry.getKey(), BigDecimal.ZERO))
              != 0)
        throw new IllegalArgumentException("Thanh toán không cân đối với lần nhận tiền.");
    Map<Long, Instant> reversedRefunds = new HashMap<>();
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT x.*,f.created_at FROM advance_refund_reversals x JOIN advance_refunds f ON"
                    + " f.id=x.refund_id")) {
      while (r.next()) {
        Instant at = Instant.parse(r.getString("reversed_at"));
        DataRules.text(r.getString("reason"), "Lý do sửa hoàn", 2000, true);
        if (at.isBefore(Instant.parse(r.getString("created_at")))
            || reversedRefunds.put(r.getLong("refund_id"), at) != null)
          throw new IllegalArgumentException("Đảo hoàn sai thời điểm.");
      }
    }
    Set<String> refundKeys = new HashSet<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM advance_refunds")) {
      while (r.next()) {
        long id = r.getLong("advance_id");
        Source source = sources.get(id);
        BigDecimal amount = money(r, "amount");
        String date = r.getString("paid_date");
        DataRules.actualDate(date);
        DataRules.text(r.getString("note"), "Ghi chú hoàn", 2000, false);
        DataRules.text(r.getString("request_key"), "Mã hoàn", 100, true);
        Instant created = Instant.parse(r.getString("created_at")),
            reversed = reversedRefunds.get(r.getLong("id")),
            sourceReversed = reversedSources.get(id);
        if (source == null
            || amount.signum() <= 0
            || !refundKeys.add(r.getString("request_key"))
            || date.compareTo(source.date()) < 0
            || created.isBefore(source.created())
            || (sourceReversed != null
                && (!created.isBefore(sourceReversed)
                    || reversed == null
                    || reversed.isAfter(sourceReversed))))
          throw new IllegalArgumentException("Hoàn trả trước thiếu nguồn hoặc sai thời điểm.");
        if (reversed == null) activeUsed.merge(id, amount, BigDecimal::add);
      }
    }
    for (var entry : sources.entrySet())
      if (activeUsed
              .getOrDefault(entry.getKey(), BigDecimal.ZERO)
              .compareTo(entry.getValue().amount())
          > 0) throw new IllegalArgumentException("Đã dùng vượt số dư trả trước.");
  }
}
