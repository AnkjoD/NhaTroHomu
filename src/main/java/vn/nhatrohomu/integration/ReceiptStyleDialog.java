package vn.nhatrohomu.integration;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

public final class ReceiptStyleDialog extends JDialog {
  public ReceiptStyleDialog(Window owner, Bill bill, List<Payment> payments) {
    super(owner, "Chỉnh mẫu ảnh phiếu tiền", ModalityType.APPLICATION_MODAL);
    ReceiptStyle current = ReceiptStyle.current();
    java.util.TreeSet<String> fonts =
        new java.util.TreeSet<>(
            java.util.List.of(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    fonts.add("Be Vietnam Pro");
    JComboBox<String> font = new JComboBox<>(fonts.toArray(String[]::new));
    font.setSelectedItem(current.font());
    JSpinner size = new JSpinner(new SpinnerNumberModel(current.size(), 18, 26, 1));
    JTextField title = new JTextField(current.title());
    JCheckBox person = new JCheckBox("Hiện tên người thuê", current.person());
    JCheckBox due = new JCheckBox("Hiện hạn thanh toán", current.due());
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Font chữ", font);
    Ui.field(fields, 1, "Cỡ chữ", size);
    Ui.field(fields, 2, "Tiêu đề", title);
    Ui.field(fields, 3, "Thông tin thêm", person);
    Ui.field(fields, 4, "", due);
    JLabel preview = new JLabel();
    java.util.function.Supplier<ReceiptStyle> selected =
        () ->
            new ReceiptStyle(
                (String) font.getSelectedItem(),
                (Integer) size.getValue(),
                title.getText().trim(),
                person.isSelected(),
                due.isSelected());
    Runnable update =
        () -> {
          try {
            var image = InvoiceImage.render(bill, payments, selected.get());
            preview.setIcon(
                new ImageIcon(
                    image.getScaledInstance(
                        720, image.getHeight() * 720 / image.getWidth(), Image.SCALE_SMOOTH)));
            preview.setText("");
          } catch (Exception ex) {
            preview.setText(ex.getMessage());
          }
        };
    font.addActionListener(e -> update.run());
    size.addChangeListener(e -> update.run());
    person.addActionListener(e -> update.run());
    due.addActionListener(e -> update.run());
    title
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
              }
            });
    JPanel root = Design.clear(new BorderLayout(12, 12));
    root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    root.add(fields, BorderLayout.NORTH);
    root.add(new JScrollPane(preview));
    JPanel buttons = Design.clear(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(
        Ui.button(
            "Mặc định",
            () -> {
              ReceiptStyle defaults = ReceiptStyle.defaults();
              font.setSelectedItem(defaults.font());
              size.setValue(defaults.size());
              title.setText(defaults.title());
              person.setSelected(defaults.person());
              due.setSelected(defaults.due());
              update.run();
            }));
    buttons.add(Ui.button("Hủy", this::dispose));
    buttons.add(
        Design.primary(
            Ui.button(
                "Lưu mẫu ảnh",
                () -> {
                  try {
                    selected.get().save();
                    dispose();
                  } catch (Exception ex) {
                    Ui.error(this, ex);
                  }
                })));
    root.add(buttons, BorderLayout.SOUTH);
    setContentPane(root);
    update.run();
    Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setSize(Math.min(950, screen.width - 50), Math.min(760, screen.height - 50));
    setLocationRelativeTo(owner);
  }
}
