package vn.nhatrohomu.room.ui;

import java.awt.*;
import java.math.BigDecimal;
import javax.swing.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Explicit boundary conversion; cannot silently count water from old quota months. */
public final class WaterPolicyDialog extends JDialog {
  public WaterPolicyDialog(JFrame owner, Repository repo, Room room, Runnable saved)
      throws Exception {
    super(owner, "Đổi cách tính nước · " + room.name(), true);
    String last = repo.latestMonth(room.id());
    if (last == null)
      throw new IllegalArgumentException("Phòng chưa có hóa đơn: sửa trong thông tin phòng.");
    String target = room.waterMode().equals("METER") ? "FIXED" : "METER";
    JTextField opening = new JTextField(Ui.n(repo.previous(room).water()));
    JTextField fixed = new JTextField(Ui.n(repo.rates().defaultWater()));
    JTextField reason = new JTextField();
    opening.setEditable(target.equals("METER"));
    fixed.setEnabled(target.equals("FIXED"));
    JPanel form = Ui.form();
    Ui.field(
        form, 0, "Kỳ đầu áp dụng", new JLabel(DataRules.period(last).plusMonths(1).toString()));
    Ui.field(
        form,
        1,
        "Đổi sang",
        new JLabel(target.equals("METER") ? "Theo đồng hồ" : "Số m³ khoán mỗi tháng"));
    Ui.field(form, 2, "Số nước tại mốc chuyển", opening);
    Ui.field(form, 3, "Mức khoán riêng · m³", fixed);
    Ui.field(form, 4, "Lý do / ghi nhận bàn giao *", reason);
    JTextArea hint =
        new JTextArea(
            "Chuyển ở ranh giới kỳ, sau khi chốt kỳ trước. Khi quay lại đồng hồ, nhập số bắt đầu đo"
                + " tại mốc chuyển; không dùng số từ những tháng khoán. Bản này chưa hỗ trợ chuyển"
                + " giữa tháng hoặc thay đồng hồ.");
    hint.setEditable(false);
    hint.setLineWrap(true);
    hint.setWrapStyleWord(true);
    hint.setOpaque(false);
    JCheckBox confirmed = new JCheckBox("Tôi đã kiểm tra số chốt và kỳ hiệu lực.");
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Đóng", this::dispose));
    buttons.add(
        Design.primary(
            Ui.button(
                "Xác nhận chuyển",
                () -> {
                  try {
                    if (!confirmed.isSelected())
                      throw new IllegalArgumentException("Xác nhận số bàn giao trước khi chuyển.");
                    BigDecimal value = BillingService.number(opening.getText(), "Số nước bàn giao");
                    BigDecimal quota =
                        target.equals("FIXED")
                            ? BillingService.number(fixed.getText(), "Nước khoán")
                            : null;
                    repo.changeWaterMode(room.id(), target, value, quota, reason.getText());
                    dispose();
                    saved.run();
                  } catch (Exception ex) {
                    Ui.error(this, ex);
                  }
                })));
    JPanel root = new JPanel(new BorderLayout(12, 12));
    root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    root.add(form, BorderLayout.NORTH);
    root.add(hint, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(confirmed, BorderLayout.NORTH);
    bottom.add(buttons, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    setSize(680, 430);
    setLocationRelativeTo(owner);
  }
}
