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
  private final JLabel state = new JLabel();
  private final JButton addReceipt = new JButton("Ghi tiền thực nhận"),
      fillRemaining = new JButton("Điền số còn thiếu");
  private BigDecimal remaining = BigDecimal.ZERO;
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
    JPanel summary = new JPanel(new BorderLayout(0, 8));
    summary.add(balance, BorderLayout.NORTH);
    summary.add(state);
    root.add(summary, BorderLayout.NORTH);
    table.setRowHeight(34);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    root.add(new JScrollPane(table), BorderLayout.CENTER);
    JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
    form.add(new JLabel("Số tiền thực nhận (đồng, không phân cách)"));
    amount.putClientProperty("JTextField.placeholderText", "Nhập số tiền khách vừa trả");
    JPanel receive = new JPanel(new BorderLayout(6, 0));
    receive.add(amount);
    receive.add(fillRemaining, BorderLayout.EAST);
    fillRemaining.addActionListener(e -> amount.setText(remaining.toPlainString()));
    form.add(receive);

    amount
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                validateAmount();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                validateAmount();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                validateAmount();
              }
            });
    form.add(new JLabel("Ngày thu · YYYY-MM-DD"));
    form.add(vn.nhatrohomu.shared.DatePicker.field(date, false));
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
    addReceipt.addActionListener(
        e -> {
          try {
            repository.addPayment(
                bill.id(),
                BillingService.number(amount.getText(), "Tiền thực nhận"),
                date.getText().trim(),
                note.getText(),
                requestKey);
            requestKey = java.util.UUID.randomUUID().toString();
            note.setText("");
            refresh();
          } catch (Exception ex) {
            Ui.error(this, ex);
          }
        });
    actions.add(addReceipt);
    actions.add(Ui.button("Đóng", this::dispose));
    JPanel bottom = new JPanel(new BorderLayout(0, 12));
    JPanel entry = new JPanel(new BorderLayout(0, 10));
    entry.add(form);
    JTextArea help =
        new JTextArea(
            "Khách trả thiếu: ghi đúng tiền vừa nhận, phần còn lại tự nằm trong Công nợ. Thu bổ"
                + " sung vào cùng hóa đơn; không cộng nợ cũ thành phí tháng mới.");
    help.setEditable(false);
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setOpaque(false);
    help.setRows(2);
    entry.add(help, BorderLayout.SOUTH);
    bottom.add(entry, BorderLayout.CENTER);
    bottom.add(actions, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    refresh();
    setSize(900, 560);
    setLocationRelativeTo(owner);
  }

  private void validateAmount() {
    try {
      BigDecimal value = BillingService.number(amount.getText(), "Tiền thực nhận");
      addReceipt.setEnabled(
          remaining.signum() > 0
              && value.signum() > 0
              && value.stripTrailingZeros().scale() <= 0
              && value.compareTo(remaining) <= 0
              && value.compareTo(new BigDecimal("1000000000")) <= 0);
    } catch (IllegalArgumentException ex) {
      addReceipt.setEnabled(false);
    }
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
    remaining = rest;
    amount.setText("");
    boolean owes = rest.signum() > 0;
    amount.setEnabled(owes);
    addReceipt.setEnabled(false);
    fillRemaining.setEnabled(owes);
    state.setText(
        !owes
            ? "Đã thu đủ · không còn công nợ"
            : received.signum() > 0
                ? "Đã thu một phần · còn nợ " + Ui.money(rest)
                : "Chưa thu · còn nợ " + Ui.money(rest));
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
