package vn.nhatrohomu.shared;

import java.time.LocalDate;
import vn.nhatrohomu.shared.Models.*;

/** Validation of optional contact and identity data; never an identity verification. */
public final class TenantRules {
  private TenantRules() {}

  public static void birthday(String value) {
    if (!value.isBlank() && DataRules.date(value).isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Ngày sinh không được ở tương lai.");
  }

  public static void identity(String value) {
    DataRules.text(value, "CCCD / giấy tờ", 40, false);
  }

  public static void resident(Resident r) {
    DataRules.name(r.name(), "Tên người ở cùng", 120);
    DataRules.text(r.relationship(), "Quan hệ", 80, false);
    birthday(r.birthday());
    identity(r.identity());
    DataRules.text(r.phone(), "Điện thoại", 40, false);
  }

  public static void tenant(Tenant t) {
    if (t.id() < 0) throw new IllegalArgumentException("Mã người thuê không hợp lệ.");
    DataRules.name(t.name(), "Tên người thuê", 120);
    DataRules.text(t.phone(), "Điện thoại", 40, false);
    DataRules.text(t.zalo(), "Zalo", 40, false);
    DataRules.text(t.note(), "Ghi chú", 2000, false);
    DataRules.text(t.address(), "Địa chỉ", 500, false);
    identity(t.identity());
    birthday(t.birthday());
    if (t.residents().size() > 30)
      throw new IllegalArgumentException("Tối đa 30 người ở cùng mỗi hồ sơ.");
    for (Resident r : t.residents()) resident(r);
  }
}
