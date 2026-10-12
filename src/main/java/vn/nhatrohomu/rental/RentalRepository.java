package vn.nhatrohomu.rental;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import vn.nhatrohomu.shared.Models.*;

/** Separate rental lifecycle from meter readings and payment collection. */
public interface RentalRepository {
  List<Tenant> tenants() throws SQLException;

  void saveTenant(Tenant tenant) throws SQLException;

  void archiveTenant(long id) throws SQLException;

  List<Contract> contracts() throws SQLException;

  void createContract(Contract contract) throws SQLException;

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
