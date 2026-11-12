package vn.nhatrohomu.rental;

import java.time.LocalDate;
import java.util.*;
import vn.nhatrohomu.shared.Models.Contract;

public final class RoomAvailability {
  private RoomAvailability() {}

  public static Map<Long, Contract> occupied(
      List<Contract> contracts, LocalDate date, Set<Long> continuations) {
    Map<Long, Contract> result = new HashMap<>();
    for (Contract c : contracts)
      if (continuations.contains(c.id()) && date.isBefore(LocalDate.parse(c.startDate())))
        result.put(c.roomId(), c);
    for (Contract c : contracts) if (c.occupiedOn(date)) result.put(c.roomId(), c);
    return result;
  }

  public static Map<Long, Contract> reserved(
      List<Contract> contracts, LocalDate date, Map<Long, Contract> occupied) {
    Map<Long, Contract> result = new HashMap<>();
    contracts.stream()
        .filter(
            c -> date.isBefore(LocalDate.parse(c.startDate())) && !occupied.containsKey(c.roomId()))
        .sorted(Comparator.comparing(Contract::startDate))
        .forEach(c -> result.putIfAbsent(c.roomId(), c));
    return result;
  }
}
