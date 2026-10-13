package vn.nhatrohomu.shared;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Models.Charge;

/** Reusable money-line editor; suggestions never become charges until explicitly added. */
public final class ChargeEditor extends JPanel {
  private final JPanel lines = Ui.column();
  private final List<Line> entries = new ArrayList<>();
  private Runnable changed = () -> {};
  private boolean replacing;

  private record Line(JPanel panel, JTextField name, JTextField amount, Long serviceId) {}

  public ChargeEditor(vn.nhatrohomu.billing.ServiceCatalog catalog) throws Exception {
    this(List.of());
    remove(((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH));
    JComboBox<vn.nhatrohomu.shared.Models.ServiceItem> choices = new JComboBox<>();
    JButton add =
        Ui.button(
            "Chọn khoản phí",
            () -> {
              var selected = (vn.nhatrohomu.shared.Models.ServiceItem) choices.getSelectedItem();
              if (selected != null)
                addLine(selected.name(), selected.amount().toPlainString(), selected.id());
            });
    Runnable reload =
        () -> {
          try {
            choices.removeAllItems();
            for (var item : catalog.items()) choices.addItem(item);
            add.setEnabled(choices.getItemCount() > 0);
          } catch (Exception ex) {
            Ui.error(this, ex);
          }
        };
    JPanel controls = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
    controls.add(choices);
    controls.add(add);
    controls.add(
        Ui.button(
            "Quản lý khoản phí",
            () -> {
              try {
                new vn.nhatrohomu.billing.ui.ServiceCatalogDialog(
                        SwingUtilities.getWindowAncestor(this), catalog)
                    .setVisible(true);
                reload.run();
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            }));
    add(controls, BorderLayout.SOUTH);
    reload.run();
  }

  public ChargeEditor(List<Charge> previous) {
    super(new BorderLayout(0, 8));
    setOpaque(false);
    lines.setOpaque(false);
    add(lines);
    JPanel controls = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
    controls.add(Ui.button("+ Thêm khoản phí", () -> addLine("", "")));
    if (!previous.isEmpty()) {
      JComboBox<Charge> suggestions = new JComboBox<>();
      previous.stream().distinct().forEach(suggestions::addItem);
      suggestions.setRenderer(
          new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
              return super.getListCellRendererComponent(
                  list,
                  value instanceof Charge fee
                      ? fee.label() + " · " + Ui.money(fee.amount())
                      : value,
                  index,
                  selected,
                  focus);
            }
          });
      suggestions.setToolTipText("Khoản phí từ các thỏa thuận đã lưu; chọn rồi bấm Dùng lại.");
      controls.add(suggestions);
      controls.add(
          Ui.button(
              "Dùng lại",
              () -> {
                Charge selected = (Charge) suggestions.getSelectedItem();
                if (selected != null)
                  addLine(
                      selected.label(), selected.amount().toPlainString(), selected.serviceId());
              }));
    }
    add(controls, BorderLayout.SOUTH);
  }

  public void onChange(Runnable callback) {
    changed = callback;
  }

  private void notifyChanged() {
    if (!replacing) changed.run();
  }

  private void addLine(String label, String value) {
    addLine(label, value, null);
  }

  private void addLine(String label, String value, Long serviceId) {
    if (entries.size() >= 20) {
      Ui.error(this, new IllegalArgumentException("Tối đa 20 khoản phí."));
      return;
    }
    JPanel row = Design.clear(new BorderLayout(8, 0));
    JPanel fields = Design.clear(new GridLayout(1, 2, 8, 0));
    JTextField name = new JTextField(label, 14), amount = new JTextField(value, 12);
    name.setName("charge-name");
    name.setEditable(serviceId == null);
    amount.setName("charge-amount");
    name.putClientProperty("JTextField.placeholderText", "Ví dụ: Internet");
    amount.putClientProperty("JTextField.placeholderText", "Ví dụ: 100000");
    JPanel nameField = Design.clear(new BorderLayout(0, 4));
    nameField.add(new JLabel("Tên khoản phí"), BorderLayout.NORTH);
    nameField.add(name);
    JPanel amountField = Design.clear(new BorderLayout(0, 4));
    amountField.add(new JLabel("Số tiền (đ)"), BorderLayout.NORTH);
    amountField.add(amount);
    fields.add(nameField);
    fields.add(amountField);
    row.add(fields);
    Line entry = new Line(row, name, amount, serviceId);
    row.add(
        Ui.button(
            "Bỏ",
            () -> {
              entries.remove(entry);
              lines.remove(row);
              lines.revalidate();
              lines.repaint();
              notifyChanged();
            }),
        BorderLayout.EAST);
    entries.add(entry);
    lines.add(row);
    DocumentListener listener =
        new DocumentListener() {
          public void insertUpdate(DocumentEvent e) {
            notifyChanged();
          }

          public void removeUpdate(DocumentEvent e) {
            notifyChanged();
          }

          public void changedUpdate(DocumentEvent e) {
            notifyChanged();
          }
        };
    name.getDocument().addDocumentListener(listener);
    amount.getDocument().addDocumentListener(listener);
    lines.revalidate();
    lines.repaint();
    notifyChanged();
  }

  public void setCharges(List<Charge> fees) {
    replacing = true;
    try {
      entries.clear();
      lines.removeAll();
      fees.forEach(fee -> addLine(fee.label(), fee.amount().toPlainString(), fee.serviceId()));
      lines.revalidate();
      lines.repaint();
    } finally {
      replacing = false;
    }
    notifyChanged();
  }

  public List<Charge> charges() {
    List<Charge> result = new ArrayList<>();
    HashSet<String> names = new HashSet<>();
    for (Line entry : entries) {
      String label = entry.name().getText().trim();
      DataRules.name(label, "Tên khoản phí", 100);
      if (!names.add(label.toLowerCase(java.util.Locale.ROOT)))
        throw new IllegalArgumentException("Khoản phí bị trùng tên: " + label);
      result.add(
          new Charge(
              label,
              RentalService.money(entry.amount().getText(), "Số tiền " + label),
              entry.serviceId()));
    }
    return List.copyOf(result);
  }
}
