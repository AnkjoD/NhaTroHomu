package vn.nhatrohomu.rental;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import vn.nhatrohomu.shared.Models.*;

/** Separate rental lifecycle from meter readings and payment collection. */
public interface RentalRepository {
  List<Tenant> tenants() throws SQLException;

  default Tenant tenant(long id) throws SQLException {
    return tenants().stream().filter(person -> person.id() == id).findFirst().orElse(null);
  }

  void saveTenant(Tenant tenant) throws SQLException;

  void archiveTenant(long id) throws SQLException;

  default List<Tenant> archivedTenants() throws SQLException {
    return List.of();
  }

  default void restoreTenant(long id) throws SQLException {
    throw new UnsupportedOperationException();
  }

  List<Contract> contracts() throws SQLException;

  void createContract(Contract contract) throws SQLException;

  default void createContract(Contract contract, Readings opening) throws SQLException {
    throw new UnsupportedOperationException();
  }

  default Readings opening(long contractId, YearMonth month) throws SQLException {
    throw new UnsupportedOperationException();
  }

  default boolean hasHandover(long contractId) throws SQLException {
    return false;
  }

  default void checkout(Bill finalBill) throws SQLException {
    throw new UnsupportedOperationException();
  }

  void closeContract(long id, String date) throws SQLException;

  void cancelUnusedContract(long id) throws SQLException;

  Contract forMonth(long roomId, YearMonth month) throws SQLException;

  List<DepositEntry> deposits(long contractId) throws SQLException;

  void addDeposit(long contractId, String kind, BigDecimal amount, String date, String note)
      throws SQLException;

  default BigDecimal heldDeposit(long id) throws SQLException {
    return deposits(id).stream()
        .map(e -> e.kind().equals("RECEIVE") ? e.amount() : e.amount().negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
