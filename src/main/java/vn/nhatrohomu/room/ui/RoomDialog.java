package vn.nhatrohomu.room.ui;

import java.awt.*;
import javax.swing.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

public final class RoomDialog extends JDialog {
  public RoomDialog(JFrame owner, Repository repository, Room room, Runnable saved)
      throws Exception {
    super(owner, room == null ? "Thêm phòng" : "Sửa phòng", true);
    JTextField name = new JTextField(room == null ? "" : room.name(), 22),
        tenant = new JTextField(room == null ? "" : room.tenant());
    JTextField electricity = new JTextField(room == null ? "0" : Ui.n(room.electricityStart())),
        water = new JTextField(room == null ? "0" : Ui.n(room.waterStart()));
    JComboBox<String> mode = new JComboBox<>(new String[] {"Theo đồng hồ", "Số m³ cố định mỗi kỳ"});
    mode.setSelectedIndex(room != null && room.waterMode().equals("FIXED") ? 1 : 0);
    JTextField fixed =
        new JTextField(room == null || room.fixedWater() == null ? "" : Ui.n(room.fixedWater()));
    boolean hasBills = room != null && repository.hasRoomHistory(room.id());
    electricity.setEditable(!hasBills);
    water.setEditable(!hasBills);
    fixed.setEnabled(mode.getSelectedIndex() == 1);
    mode.addActionListener(e -> fixed.setEnabled(mode.getSelectedIndex() == 1));
    JPanel form = Ui.form();
    Ui.field(form, 0, "Tên phòng *", name);
    tenant.setEditable(false);
    tenant.setToolTipText(
        "Tên từ dữ liệu cũ. Người thuê mới được quản lý qua màn Người thuê và Hợp đồng.");
    Ui.field(form, 1, "Tên từ dữ liệu cũ", tenant);
    Ui.field(form, 2, "Điện ban đầu (kWh)", electricity);
    Ui.field(form, 3, "Nước ban đầu (m³)", water);
    Ui.field(form, 4, "Cách tính nước", mode);
    Ui.field(form, 5, "m³ riêng (trống = mặc định)", fixed);
    JTextArea rentalHelp =
        new JTextArea(
            "Tiền phòng được đặt trong Hợp đồng thuê. Sau khi lưu phòng, thêm Người thuê rồi tạo"
                + " Hợp đồng để gắn phòng, giá thuê và phí hàng tháng.",
            3,
            30);
    rentalHelp.setEditable(false);
    rentalHelp.setLineWrap(true);
    rentalHelp.setWrapStyleWord(true);
    rentalHelp.setOpaque(false);
    Ui.field(form, 6, "Tiền phòng", rentalHelp);
    JLabel hint =
        new JLabel(
            hasBills
                ? "Đã có hóa đơn: số ban đầu được khóa để giữ lịch sử."
                : "Nhập chỉ số đầu kỳ trước khi bắt đầu chốt tháng đầu tiên.");
    hint.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    JButton save =
        Ui.button(
            "Lưu phòng",
            () -> {
              try {
                if (name.getText().trim().isEmpty() || name.getText().length() > 80)
                  throw new IllegalArgumentException("Tên phòng cần 1–80 ký tự.");
                Room value =
                    new Room(
                        room == null ? 0 : room.id(),
                        name.getText(),
                        tenant.getText(),
                        BillingService.number(electricity.getText(), "Điện đầu kỳ"),
                        BillingService.number(water.getText(), "Nước đầu kỳ"),
                        mode.getSelectedIndex() == 0 ? "METER" : "FIXED",
                        fixed.getText().isBlank() || mode.getSelectedIndex() == 0
                            ? null
                            : BillingService.number(fixed.getText(), "Nước riêng"),
                        true);
                repository.saveRoom(value);
                dispose();
                saved.run();
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Hủy", this::dispose));
    buttons.add(save);
    JPanel root = Design.clear(new BorderLayout(0, 20));
    root.setBorder(BorderFactory.createEmptyBorder(26, 26, 20, 26));
    root.setBackground(Ui.BACKGROUND);
    root.setOpaque(true);
    JPanel heading = Design.clear(new BorderLayout(0, 10));
    heading.add(
        Design.label(room == null ? "Thêm phòng mới" : "Thông tin phòng", 26, Design.INK, true),
        BorderLayout.NORTH);
    heading.add(
        Design.label("Ghi nhận một lần, theo dõi mỗi tháng.", 12, Design.MUTED, false),
        BorderLayout.CENTER);
    JPanel card = Design.card(Design.SURFACE);
    card.setLayout(new BorderLayout());
    card.add(form, BorderLayout.CENTER);
    hint.setForeground(Design.MUTED);
    hint.setFont(hint.getFont().deriveFont(11f));
    card.add(hint, BorderLayout.SOUTH);
    Design.primary(save);
    buttons.setOpaque(false);
    root.add(heading, BorderLayout.NORTH);
    root.add(card, BorderLayout.CENTER);
    root.add(buttons, BorderLayout.SOUTH);
    setContentPane(root);
    getRootPane().setDefaultButton(save);
    pack();
    setLocationRelativeTo(owner);
  }
}
