package vn.nhatrohomu.persistence.sqlite;

import java.sql.*;
import java.time.*;
import java.util.*;
import vn.nhatrohomu.rental.RentalService;

/** Missing contractual obligations are distinct from vacant rooms. */
final class BillSchedule {
  private BillSchedule() {}

  static String firstMissing(Connection c, long roomId, YearMonth through) throws SQLException {
    Set<String> issued = new HashSet<>();
    try (PreparedStatement p =
        c.prepareStatement("SELECT month FROM bills WHERE room_id=? AND voided_at IS NULL")) {
      p.setLong(1, roomId);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) issued.add(r.getString(1));
      }
    }
    String first = null;
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT start_date,CASE WHEN closed_date='' THEN end_date ELSE closed_date END"
                + " effective_end FROM contracts WHERE room_id=? AND cancelled_at IS NULL")) {
      p.setLong(1, roomId);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          YearMonth start = YearMonth.from(RentalService.date(r.getString(1))),
              end = YearMonth.from(RentalService.date(r.getString(2)));
          if (end.isAfter(through)) end = through;
          for (YearMonth period = start; !period.isAfter(end); period = period.plusMonths(1))
            if (!issued.contains(period.toString())) {
              if (first == null || period.toString().compareTo(first) < 0)
                first = period.toString();
              break;
            }
        }
      }
    }
    return first;
  }
}
