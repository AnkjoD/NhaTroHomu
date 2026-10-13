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
  private List<vn.nhatrohomu.billing.CollectionRepository.Advance> advances = List.of();
  private List<vn.nhatrohomu.billing.CollectionRepository.Advance> issued = List.of();
  private List<vn.nhatrohomu.billing.CollectionRepository.Refund> refunds = List.of();
  private final JCheckBox useAdvance = new JCheckBox("Dùng tiền trả trước", true);
  private final JLabel advanceInfo = new JLabel(), preview = new JLabel();
  private final JComboBox<Tenant> advanceOwner = new JComboBox<>();

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
    form.add(new JLabel("Số tiền khách vừa đưa (đ)"));
    amount.putClientProperty("JTextField.placeholderText", "Nhập số tiền khách vừa trả");
    JPanel receive = new JPanel(new BorderLayout(6, 0));
    receive.add(amount);
    receive.add(fillRemaining, BorderLayout.EAST);
    fillRemaining.addActionListener(
        e ->
            amount.setText(
                remaining.subtract(advanceAmount()).max(BigDecimal.ZERO).toPlainString()));
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
    form.add(new JLabel("Ghi chú"));
    form.add(note);
    if (bill.contractId() == null) {
      advanceOwner.addItem(null);
      for (Tenant person : repository.rentals().tenants()) advanceOwner.addItem(person);
      advanceOwner.setRenderer(
          new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
              return super.getListCellRendererComponent(
                  list,
                  value == null ? "Chưa có hồ sơ: giữ riêng theo phòng cũ" : value,
                  index,
                  selected,
                  focus);
            }
          });
      JPanel ownerChoice = new JPanel(new BorderLayout(6, 0));
      ownerChoice.setOpaque(false);
      ownerChoice.add(advanceOwner);
      ownerChoice.add(
          Ui.button(
              "+ Hồ sơ",
              () -> {
                java.util.Set<Long> before = new java.util.HashSet<>();
                for (int i = 0; i < advanceOwner.getItemCount(); i++)
                  if (advanceOwner.getItemAt(i) != null) before.add(advanceOwner.getItemAt(i).id());
                vn.nhatrohomu.rental.ui.RentalPanel.editProfile(
                    owner,
                    repository,
                    () -> {
                      try {
                        Tenant selected = (Tenant) advanceOwner.getSelectedItem();
                        advanceOwner.removeAllItems();
                        advanceOwner.addItem(null);
                        for (Tenant person : repository.rentals().tenants()) {
                          advanceOwner.addItem(person);
                          if (!before.contains(person.id())) selected = person;
                        }
                        if (selected != null)
                          for (int i = 0; i < advanceOwner.getItemCount(); i++)
                            if (advanceOwner.getItemAt(i) != null
                                && advanceOwner.getItemAt(i).id() == selected.id())
                              advanceOwner.setSelectedIndex(i);
                      } catch (Exception ex) {
                        Ui.error(this, ex);
                      }
                    });
              }),
          BorderLayout.EAST);
      form.add(new JLabel("Người có tiền trả trước"));
      form.add(ownerChoice);
      advanceOwner.addActionListener(
          e -> {
            try {
              reloadAdvances();
              validateAmount();
            } catch (Exception ex) {
              Ui.error(this, ex);
            }
          });
    }
    JPanel credit = new JPanel(new BorderLayout(0, 4));
    credit.setOpaque(false);
    credit.add(useAdvance, BorderLayout.NORTH);
    credit.add(advanceInfo);
    form.add(new JLabel("Tiền trả trước từ kỳ trước"));
    form.add(credit);
    form.add(new JLabel("Sau khi ghi nhận"));
    form.add(preview);
    useAdvance.addActionListener(e -> validateAmount());
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton reverse =
        Ui.button(
            "Sửa khoản thu nhập nhầm",
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
                  int modelRow = table.convertRowIndexToModel(row);
                  if (modelRow < payments.size())
                    repository.reversePayment(payments.get(modelRow).id(), reason);
                  else if (modelRow < payments.size() + issued.size())
                    repository
                        .collections()
                        .reverseAdvance(issued.get(modelRow - payments.size()).id(), reason);
                  else
                    repository
                        .collections()
                        .reverseRefund(
                            refunds.get(modelRow - payments.size() - issued.size()).id(), reason);
                  refresh();
                }
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    actions.add(reverse);
    JButton refund =
        Ui.button(
            "Ghi nhận hoàn trả trước",
            () -> {
              try {
                int row = table.getSelectedRow();
                if (row < 0) return;
                int index = table.convertRowIndexToModel(row) - payments.size();
                if (index < 0 || index >= issued.size())
                  throw new IllegalArgumentException(
                      "Chọn khoản trả trước còn số dư để ghi nhận tiền đã hoàn cho khách.");
                var source = issued.get(index);
                JTextField value = new JTextField(source.available().toPlainString()),
                    day = new JTextField(LocalDate.now().toString()),
                    memo = new JTextField();
                JPanel fields = new JPanel(new GridLayout(0, 2, 10, 10));
                fields.add(new JLabel("Tiền đã hoàn cho khách (đ)"));
                fields.add(value);
                fields.add(new JLabel("Ngày hoàn"));
                fields.add(vn.nhatrohomu.shared.DatePicker.field(day, false));
                fields.add(new JLabel("Ghi chú"));
                fields.add(memo);
                if (JOptionPane.showConfirmDialog(
                        this, fields, "Ghi nhận tiền đã hoàn thực tế", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) return;
                repository
                    .collections()
                    .refund(
                        source.id(),
                        BillingService.number(value.getText(), "Tiền hoàn"),
                        day.getText().trim(),
                        memo.getText(),
                        java.util.UUID.randomUUID().toString());
                refresh();
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    actions.add(refund);
    vn.nhatrohomu.shared.RowActions.install(table, List.of(reverse), false);
    refund.setVisible(false);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              int row = table.getSelectedRow(),
                  index = row < 0 ? -1 : table.convertRowIndexToModel(row) - payments.size();
              refund.setVisible(
                  index >= 0
                      && index < issued.size()
                      && issued.get(index).available().signum() > 0);
            });
    addReceipt.addActionListener(
        e -> {
          try {
            BigDecimal value =
                amount.getText().isBlank()
                    ? BigDecimal.ZERO
                    : BillingService.number(amount.getText(), "Tiền thực nhận");
            BigDecimal used = advanceAmount(),
                applied = value.min(remaining.subtract(used)),
                surplus = value.subtract(applied);
            java.util.List<String> usedSources = new java.util.ArrayList<>();
            BigDecimal left = used;
            for (var source : advances) {
              BigDecimal take = source.available().min(left);
              if (take.signum() <= 0) break;
              usedSources.add(source.month() + ": " + Ui.money(take));
              left = left.subtract(take);
            }
            String sources = String.join("; ", usedSources);
            String confirmation =
                "Tiền khách vừa đưa: "
                    + Ui.money(value)
                    + "\nDùng trả trước: "
                    + Ui.money(used)
                    + (used.signum() > 0 ? " (nguồn " + sources + ")" : "")
                    + "\nGiữ cho kỳ sau: "
                    + Ui.money(surplus)
                    + "\nCòn nợ: "
                    + Ui.money(remaining.subtract(used).subtract(applied));
            if (bill.contractId() == null)
              confirmation +=
                  "\nSố dư thuộc: "
                      + (selectedOwner() == null
                          ? "hồ sơ cũ của " + bill.roomName()
                          : ((Tenant) advanceOwner.getSelectedItem()).name());
            if (remaining.signum() == 0)
              confirmation +=
                  "\nHóa đơn đã trả đủ. Đây là tiền mới trả trước, không phải sửa khoản thu cũ.";
            if (!Ui.confirm(this, confirmation + "\nGhi nhận?")) return;
            repository
                .collections()
                .collect(
                    bill.id(),
                    value,
                    date.getText().trim(),
                    note.getText(),
                    useAdvance.isSelected(),
                    requestKey,
                    selectedOwner());
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
            "Nhập tiền khách vừa đưa. Trả thiếu thì giữ số nợ; trả dư thì lưu cho kỳ sau. Muốn sửa"
                + " tiền đã ghi, chọn dòng gốc rồi Sửa khoản thu nhập nhầm.");
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
    setSize(980, 650);
    setLocationRelativeTo(owner);
  }

  private void validateAmount() {
    try {
      BigDecimal value =
          amount.getText().isBlank()
              ? BigDecimal.ZERO
              : BillingService.number(amount.getText(), "Tiền thực nhận");
      addReceipt.setEnabled(
          (value.signum() > 0 || advanceAmount().signum() > 0)
              && value.signum() >= 0
              && value.stripTrailingZeros().scale() <= 0
              && value.compareTo(new BigDecimal("1000000000")) <= 0);
      BigDecimal used = advanceAmount(), applied = value.min(remaining.subtract(used));
      BigDecimal surplus = value.subtract(applied);
      preview.setText(
          surplus.signum() > 0
              ? "Giữ cho kỳ sau: " + Ui.money(surplus)
              : "Còn nợ: " + Ui.money(remaining.subtract(used).subtract(applied)));
    } catch (IllegalArgumentException ex) {
      addReceipt.setEnabled(false);
    }
  }

  private BigDecimal advanceAmount() {
    return useAdvance.isSelected()
        ? advances.stream()
            .map(vn.nhatrohomu.billing.CollectionRepository.Advance::available)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .min(remaining)
        : BigDecimal.ZERO;
  }

  private Long selectedOwner() {
    Tenant selected = (Tenant) advanceOwner.getSelectedItem();
    return selected == null ? null : selected.id();
  }

  private void reloadAdvances() throws Exception {
    advances = repository.collections().available(bill.id(), selectedOwner());
    useAdvance.setVisible(!advances.isEmpty());
    BigDecimal available =
        advances.stream()
            .map(vn.nhatrohomu.billing.CollectionRepository.Advance::available)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    advanceInfo.setText(
        advances.isEmpty()
            ? "Chưa có số dư từ kỳ trước"
            : Ui.money(available) + " từ " + advances.size() + " khoản");
    advanceInfo.setToolTipText(
        advances.stream()
            .map(a -> "Tháng " + a.month() + ": " + Ui.money(a.available()))
            .collect(java.util.stream.Collectors.joining("; ")));
  }

  private void refresh() throws Exception {
    payments = repository.payments(bill.id());
    reloadAdvances();
    issued = repository.collections().issued(bill.id());
    refunds = repository.collections().refunds(bill.id());
    BigDecimal received =
        payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal rest = bill.total().subtract(received);
    balance.setText(
        "Tổng: "
            + Ui.money(bill.total())
            + "  ·  Đã thanh toán: "
            + Ui.money(received)
            + "  ·  Còn: "
            + Ui.money(rest));
    remaining = rest;
    amount.setText("");
    boolean owes = rest.signum() > 0;
    amount.setEnabled(true);
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
    for (var advance : issued)
      model.addRow(
          new Object[] {
            advance.date(),
            Ui.money(advance.original()),
            "Trả trước tháng "
                + advance.month()
                + " · "
                + (advance.ownerName().isBlank() ? "giữ theo phòng cũ" : advance.ownerName())
                + " · số dư "
                + Ui.money(advance.available())
          });
    for (var refund : refunds)
      model.addRow(
          new Object[] {
            refund.date(),
            Ui.money(refund.amount().negate()),
            "Đã hoàn trả trước · " + refund.note()
          });
    validateAmount();
  }
}
