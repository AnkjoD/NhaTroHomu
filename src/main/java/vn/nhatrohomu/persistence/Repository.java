package vn.nhatrohomu.persistence;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import vn.nhatrohomu.rental.RentalRepository;
import vn.nhatrohomu.shared.Models.*;

/** Giao diện chỉ biết hợp đồng này, không biết SQLite lưu ra sao. */
public interface Repository {
  RentalRepository rentals();

  List<Room> rooms() throws SQLException;

  void saveRoom(Room room) throws SQLException;

  void archiveRoom(long id) throws SQLException;

  Rates rates() throws SQLException;

  void saveRates(Rates rates) throws SQLException;

  Readings previous(Room room) throws SQLException;

  default boolean hasRoomHistory(long roomId) throws SQLException {
    return latestMonth(roomId) != null;
  }

  String latestMonth(long roomId) throws SQLException;

  List<Bill> bills(int limit, int offset) throws SQLException;

  List<Bill> billsBetween(String firstMonth, String lastMonth) throws SQLException;

  default List<Bill> roomBills(long roomId, String from, String to) throws SQLException {
    return billsBetween(from, to).stream().filter(b -> b.roomId() == roomId).toList();
  }

  List<Bill> outstandingBills() throws SQLException;

  int billCount() throws SQLException;

  void saveBill(Bill bill, byte[] electricityPhoto, byte[] waterPhoto) throws SQLException;

  byte[] photo(long billId, boolean electricity) throws SQLException;

  List<Payment> payments(long billId) throws SQLException;

  void addPayment(long billId, java.math.BigDecimal amount, String date, String note)
      throws SQLException;

  default void addPayment(
      long id, java.math.BigDecimal amount, String date, String note, String requestKey)
      throws SQLException {
    addPayment(id, amount, date, note);
  }

  default void reversePayment(long id, String reason) throws SQLException {
    deletePayment(id);
  }

  default void changeWaterMode(
      long id, String mode, java.math.BigDecimal opening, java.math.BigDecimal fixed, String reason)
      throws SQLException {
    throw new UnsupportedOperationException();
  }

  void deletePayment(long id) throws SQLException;

  default java.math.BigDecimal received(long billId) throws SQLException {
    return payments(billId).stream()
        .map(Payment::amount)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  void setPaid(long id, boolean paid) throws SQLException;

  void deleteLatest(Bill bill) throws SQLException;

  void backup(Path destination) throws Exception;

  void restore(Path source) throws Exception;
}
