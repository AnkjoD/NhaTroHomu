package vn.nhatrohomu.billing;

import java.math.BigDecimal;
import java.util.List;

public interface CollectionRepository {
  record Advance(
      long id,
      String month,
      String date,
      BigDecimal original,
      BigDecimal available,
      String note,
      Long ownerId,
      String ownerName) {}

  record Outcome(BigDecimal usedAdvance, BigDecimal appliedCash, BigDecimal savedAdvance) {}

  record Refund(long id, long advanceId, BigDecimal amount, String date, String note) {}

  List<Advance> available(long billId) throws Exception;

  default List<Advance> available(long billId, Long owner) throws Exception {
    return available(billId);
  }

  List<Advance> issued(long billId) throws Exception;

  Outcome collect(
      long billId, BigDecimal cash, String date, String note, boolean useAdvance, String requestKey)
      throws Exception;

  default Outcome collect(
      long billId,
      BigDecimal cash,
      String date,
      String note,
      boolean useAdvance,
      String requestKey,
      Long owner)
      throws Exception {
    if (owner != null) throw new UnsupportedOperationException();
    return collect(billId, cash, date, note, useAdvance, requestKey);
  }

  void reverseAdvance(long id, String reason) throws Exception;

  List<Refund> refunds(long billId) throws Exception;

  void refund(long advanceId, BigDecimal amount, String date, String note, String requestKey)
      throws Exception;

  void reverseRefund(long id, String reason) throws Exception;
}
