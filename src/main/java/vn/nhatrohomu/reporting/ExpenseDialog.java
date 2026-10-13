package vn.nhatrohomu.reporting;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.swing.*;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.persistence.sqlite.SqliteRepository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

public final class ExpenseDialog extends JDialog {
  private String key = java.util.UUID.randomUUID().toString();

  record Choice(Long id, String name) {
    public String toString() {
      return name;
    }
  }

  public ExpenseDialog(JFrame owner, Repository repository, Runnable saved) throws Exception {
    super(owner, "Ghi khoản đã chi", true);
    ExpenseStore store = new ExpenseStore(((SqliteRepository) repository).database);
    JComboBox<Choice> room = new JComboBox<>();
    room.addItem(new Choice(null, "Toàn nhà"));
    for (Room r : repository.rooms()) room.addItem(new Choice(r.id(), r.name()));
    String[] categories = {"POWER", "WATER", "MAINTENANCE", "CLEANING", "OTHER"};
    JComboBox<String> category =
        new JComboBox<>(
            java.util.Arrays.stream(categories)
                .map(ExpenseStore.CATEGORIES::get)
                .toArray(String[]::new));
    JComboBox<String> method = new JComboBox<>(new String[] {"Tiền mặt", "Chuyển khoản", "Khác"});
    JTextField amount = new JTextField(),
        date = new JTextField(LocalDate.now().toString()),
        payee = new JTextField(),
        note = new JTextField();
    JPanel form = Ui.form();
    Ui.field(form, 0, "Phòng / toàn nhà", room);
    Ui.field(form, 1, "Loại chi", category);
    Ui.field(form, 2, "Số tiền nguyên đồng *", amount);
    Ui.field(form, 3, "Ngày đã trả *", vn.nhatrohomu.shared.DatePicker.field(date, false));
    Ui.field(form, 4, "Người/đơn vị nhận tiền *", payee);
    Ui.field(form, 5, "Phương thức", method);
    Ui.field(form, 6, "Ghi chú / mã chuyển khoản", note);
    JCheckBox confirm = new JCheckBox("Đây là tiền đã trả, không phải khoản dự kiến hoặc còn nợ.");
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    actions.add(Ui.button("Đóng", this::dispose));
    actions.add(
        Design.primary(
            Ui.button(
                "Ghi khoản chi",
                () -> {
                  try {
                    if (!confirm.isSelected())
                      throw new IllegalArgumentException("Xác nhận khoản đã trả.");
                    BigDecimal value = RentalService.money(amount.getText(), "Tiền chi");
                    store.save(
                        ((Choice) room.getSelectedItem()).id(),
                        categories[category.getSelectedIndex()],
                        payee.getText(),
                        value,
                        date.getText().trim(),
                        new String[] {"CASH", "BANK", "OTHER"}[method.getSelectedIndex()],
                        note.getText(),
                        key);
                    key = java.util.UUID.randomUUID().toString();
                    dispose();
                    saved.run();
                  } catch (Exception ex) {
                    Ui.error(this, ex);
                  }
                })));
    JPanel root = new JPanel(new BorderLayout(12, 12));
    root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    root.add(form);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(confirm, BorderLayout.NORTH);
    bottom.add(actions, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    pack();
    setMinimumSize(new Dimension(620, 480));
    setSize(680, 510);
    setLocationRelativeTo(owner);
  }
}
