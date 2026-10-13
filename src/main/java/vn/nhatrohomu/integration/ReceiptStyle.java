package vn.nhatrohomu.integration;

import java.util.Map;
import vn.nhatrohomu.shared.LocalUiSettings;

public record ReceiptStyle(String font, int size, String title, boolean person, boolean due) {
  public ReceiptStyle {
    if (size < 18
        || size > 26
        || font == null
        || font.isBlank()
        || title == null
        || title.isBlank()
        || title.length() > 40
        || title.contains("\n")
        || title.contains("\r"))
      throw new IllegalArgumentException("Mẫu ảnh: font cần có tên, cỡ 18–26, tiêu đề 1–40 ký tự.");
  }

  public static ReceiptStyle defaults() {
    return new ReceiptStyle("Be Vietnam Pro", 22, "PHIẾU TIỀN PHÒNG", true, true);
  }

  public static ReceiptStyle current() {
    try {
      return new ReceiptStyle(
          LocalUiSettings.get("slip.font", "Be Vietnam Pro"),
          Integer.parseInt(LocalUiSettings.get("slip.size", "22")),
          LocalUiSettings.get("slip.title", "PHIẾU TIỀN PHÒNG"),
          Boolean.parseBoolean(LocalUiSettings.get("slip.person", "true")),
          Boolean.parseBoolean(LocalUiSettings.get("slip.due", "true")));
    } catch (IllegalArgumentException ex) {
      return defaults();
    }
  }

  public void save() throws Exception {
    LocalUiSettings.putAll(
        Map.of(
            "slip.font",
            font,
            "slip.size",
            Integer.toString(size),
            "slip.title",
            title,
            "slip.person",
            Boolean.toString(person),
            "slip.due",
            Boolean.toString(due)));
  }
}
