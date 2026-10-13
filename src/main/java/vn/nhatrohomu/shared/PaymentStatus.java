package vn.nhatrohomu.shared;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentStatus(String text, boolean paid) {
  public static PaymentStatus of(BigDecimal total, BigDecimal received, String due) {
    BigDecimal rest = total.subtract(received);
    if (rest.signum() <= 0)
      return new PaymentStatus(total.signum() == 0 ? "Không phải thu" : "Đã thanh toán", true);
    boolean late = !due.isBlank() && LocalDate.parse(due).isBefore(LocalDate.now());
    return new PaymentStatus(
        (late ? "Quá hạn" : received.signum() > 0 ? "Trả một phần" : "Chưa trả")
            + " · "
            + Ui.money(rest),
        false);
  }

  public Color color() {
    return Design.mode == Design.Mode.HOMURA
        ? paid ? new Color(108, 220, 167) : new Color(255, 147, 151)
        : paid ? new Color(25, 130, 91) : new Color(174, 53, 55);
  }

  public javax.swing.Icon icon() {
    return Design.icon(paid ? "check" : "unpaid", color());
  }

  @Override
  public String toString() {
    return text;
  }
}
