package vn.nhatrohomu.shared;

/** Quoting and formula neutralization are separate; leading whitespace/newlines matter. */
public final class CsvField {
  private CsvField() {}

  public static String escape(String value) {
    String stripped = value.stripLeading();
    if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
