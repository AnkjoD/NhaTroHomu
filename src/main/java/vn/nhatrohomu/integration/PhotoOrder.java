package vn.nhatrohomu.integration;

/** Natural filename order is only a suggestion; users confirm the room mapping. */
public final class PhotoOrder {
  private PhotoOrder() {}

  public static int compare(String a, String b) {
    int i = 0, j = 0;
    while (i < a.length() && j < b.length()) {
      if (Character.isDigit(a.charAt(i)) && Character.isDigit(b.charAt(j))) {
        int x = i, y = j;
        while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
        while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
        int result =
            new java.math.BigInteger(a.substring(x, i))
                .compareTo(new java.math.BigInteger(b.substring(y, j)));
        if (result != 0) return result;
      } else {
        int result =
            Character.compare(
                Character.toLowerCase(a.charAt(i++)), Character.toLowerCase(b.charAt(j++)));
        if (result != 0) return result;
      }
    }
    int result = Integer.compare(a.length() - i, b.length() - j);
    return result != 0 ? result : a.compareToIgnoreCase(b);
  }
}
