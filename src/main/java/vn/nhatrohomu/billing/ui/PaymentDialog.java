package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Each row is one actual receipt, rather than a manually toggled boolean. */
public final class PaymentDialog extends JDialog {
  private final Repository repository;
  private final Bill bill;
  private final JLabel balance = new JLabel();
  private final JTextField amount = new JTextField(12),
      date = new JTextField(LocalDate.now().toString(), 10),
      note = new JTextField(20);
  private final DefaultTableModel model =
      new DefaultTableModel(new String[] {"Ngày thu", "Số tiền", "Ghi chú"}, 0) {
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JTable table = new JTable(model);
  private String requestKey = java.util.UUID.randomUUID().toString();
  private List<Payment> payments = List.of();

  public PaymentDialog(JFrame owner, Repository repository, Bill bill) throws Exception {
    super(owner, "Thu tiền · " + bill.roomName() + " / " + bill.month(), true);
    this.repository = repository;
    this.bill = bill;
    JPanel root = new JPanel(new BorderLayout(16, 16));
    root.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
    balance.setFont(balance.getFont().deriveFont(Font.BOLD, 17));
    root.add(balance, BorderLayout.NORTH);
    table.setRowHeight(34);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    root.add(new JScrollPane(table), BorderLayout.CENTER);
    JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
    form.add(new JLabel("Số tiền (đồng, không dấu phân cách)"));
    form.add(amount);
    form.add(new JLabel("Ngày thu · YYYY-MM-DD"));
    form.add(date);
    form.add(new JLabel("Ghi chú / phương thức thanh toán"));
    form.add(note);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton reverse =
        Ui.button(
            "Đảo lần thu nhập nhầm",
            () -> {
              try {
                int row = table.getSelectedRow();
                if (row < 0) throw new IllegalArgumentException("Chọn một lần thu.");
                if (Ui.confirm(
                    this,
                    "Ghi đảo lần thu nhập nhầm? Bản gốc được giữ. Không dùng thao tác này để hoàn"
                        + " tiền thật cho khách.")) {
                  String reason = JOptionPane.showInputDialog(this, "Lý do nhập nhầm:");
                  if (reason == null) return;
                  repository.reversePayment(
                      payments.get(table.convertRowIndexToModel(row)).id(), reason);
                  refresh();
                }
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    actions.add(reverse);
    vn.nhatrohomu.shared.RowActions.install(table, List.of(reverse), false);
    actions.add(
        Ui.button(
            "Thêm lần thu",
            () -> {
              try {
                repository.addPayment(
                    bill.id(),
                    BillingService.number(amount.getText(), "Số tiền"),
                    date.getText().trim(),
                    note.getText(),
                    requestKey);
                requestKey = java.util.UUID.randomUUID().toString();
                note.setText("");
                refresh();
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            }));
    actions.add(Ui.button("Đóng", this::dispose));
    JPanel bottom = new JPanel(new BorderLayout(0, 12));
    bottom.add(form, BorderLayout.CENTER);
    bottom.add(actions, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    refresh();
    setSize(800, 490);
    setLocationRelativeTo(owner);
  }

  private void refresh() throws Exception {
    payments = repository.payments(bill.id());
    BigDecimal received =
        payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal rest = bill.total().subtract(received);
    balance.setText(
        "Tổng: "
            + Ui.money(bill.total())
            + "  ·  Đã thu: "
            + Ui.money(received)
            + "  ·  Còn: "
            + Ui.money(rest));
    amount.setText(rest.toPlainString());
    model.setRowCount(0);
    for (Payment p : payments)
      model.addRow(
          new Object[] {
            p.date().isBlank() ? "Chưa có ngày (dữ liệu cũ)" : p.date(),
            Ui.money(p.amount()),
            p.note()
          });
  }
}
