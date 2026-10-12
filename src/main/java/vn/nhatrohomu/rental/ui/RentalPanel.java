package vn.nhatrohomu.rental.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.ui.PaymentDialog;
import vn.nhatrohomu.integration.InvoicePdf;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalRepository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Screens for rental lifecycle, separate from the meter photo workflow. */
public final class RentalPanel extends JPanel {
  public enum Page {
    TENANTS,
    CONTRACTS,
    DEBTS
  }

  private final Repository repository;
  private final RentalRepository rentals;
  private final Page page;
  private final JFrame owner;
  private JTable table;
  private List<Tenant> tenants = List.of();
  private List<Contract> contracts = List.of();
  private List<Bill> debts = List.of();
  private boolean busy;
  private boolean refreshPending;
  private Map<Long, BigDecimal> balances = Map.of();

  private record PanelData(Object items, Map<Long, BigDecimal> balances) {}

  public RentalPanel(JFrame owner, Repository repository, Page page) {
    super(new BorderLayout(0, 18));
    this.owner = owner;
    this.repository = repository;
    this.rentals = repository.rentals();
    this.page = page;
    setOpaque(false);
    refresh();
  }

  private void refresh() {
    if (busy) {
      refreshPending = true;
      return;
    }
    busy = true;
    new SwingWorker<PanelData, Void>() {
      protected PanelData doInBackground() throws Exception {
        Object items =
            switch (page) {
              case TENANTS -> rentals.tenants();
              case CONTRACTS -> rentals.contracts();
              case DEBTS -> repository.outstandingBills();
            };
        Map<Long, BigDecimal> amounts = new HashMap<>();
        if (page == Page.CONTRACTS)
          for (Object value : (List<?>) items) {
            Contract contract = (Contract) value;
            amounts.put(contract.id(), rentals.heldDeposit(contract.id()));
          }
        if (page == Page.DEBTS)
          for (Object value : (List<?>) items) {
            Bill bill = (Bill) value;
            amounts.put(bill.id(), repository.received(bill.id()));
          }
        return new PanelData(items, amounts);
      }

      @SuppressWarnings("unchecked")
      protected void done() {
        busy = false;
        if (!owner.isDisplayable()) return;
        try {
          PanelData loaded = get();
          Object data = loaded.items();
          balances = loaded.balances();
          switch (page) {
            case TENANTS -> tenants = (List<Tenant>) data;
            case CONTRACTS -> contracts = (List<Contract>) data;
            case DEBTS -> debts = (List<Bill>) data;
          }
          render();
          if (refreshPending) {
            refreshPending = false;
            refresh();
          }
        } catch (Exception e) {
          Ui.error(RentalPanel.this, e);
        }
      }
    }.execute();
  }

  private void render() throws Exception {
    removeAll();
    String title =
        switch (page) {
          case TENANTS -> "Người thuê";
          case CONTRACTS -> "Hợp đồng & tiền cọc";
          case DEBTS -> "Công nợ";
        };
    String subtitle =
        switch (page) {
          case TENANTS ->
              "Thông tin liên hệ và lịch sử thuê. Phòng được gắn với người thuê qua hợp đồng.";
          case CONTRACTS -> "Quản lý kỳ thuê, tiền phòng, phí định kỳ và sổ nhận/hoàn cọc.";
          case DEBTS -> "Tất cả hóa đơn còn thiếu tiền, gồm cả các phòng đã ngừng quản lý.";
        };
    JPanel heading = Design.clear(new BorderLayout(0, 10));
    JPanel words = Ui.column();
    words.setOpaque(false);
    words.add(Design.label(title, 26, Design.INK, true));
    words.add(Box.createVerticalStrut(8));
    words.add(Design.label(subtitle, 12, Design.MUTED, false));
    heading.add(words, BorderLayout.NORTH);
    JPanel actions = Design.clear(new FlowLayout(FlowLayout.LEFT, 8, 10));
    if (page == Page.TENANTS) {
      actions.add(Design.primary(Ui.button("+ Người thuê", () -> tenantForm(null))));
      actions.add(
          Ui.button("Hồ sơ / người ở cùng", () -> safe(() -> tenantForm(tenants.get(selected())))));
      actions.add(
          Ui.button("Lịch sử thuê", () -> safe(() -> tenantHistory(tenants.get(selected())))));
      actions.add(
          Ui.button(
              "Lưu trữ",
              () ->
                  safe(
                      () -> {
                        Tenant tenant = tenants.get(selected());
                        if (Ui.confirm(
                            owner, "Lưu trữ " + tenant.name() + "? Lịch sử thuê vẫn được giữ.")) {
                          rentals.archiveTenant(tenant.id());
                          refresh();
                        }
                      })));
      Object[][] rows =
          tenants.stream()
              .map(
                  t ->
                      new Object[] {
                        t.id(), t.name(), t.phone(), t.zaloContact(), t.residents().size(), t.note()
                      })
              .toArray(Object[][]::new);
      table =
          table(
              new String[] {"Mã", "Họ tên", "Điện thoại", "Zalo", "Người ở cùng", "Ghi chú"}, rows);
    } else if (page == Page.CONTRACTS) {
      actions.add(Design.primary(Ui.button("+ Hợp đồng", () -> safe(this::contractForm))));
      actions.add(
          Ui.button("Xem chi tiết", () -> safe(() -> contractDetails(contracts.get(selected())))));
      actions.add(
          Ui.button("Nhận / hoàn cọc", () -> safe(() -> depositDialog(contracts.get(selected())))));
      actions.add(
          Ui.button("Kết thúc thuê", () -> safe(() -> closeForm(contracts.get(selected())))));
      actions.add(
          Ui.button(
              "Hủy chưa phát sinh",
              () ->
                  safe(
                      () -> {
                        Contract contract = contracts.get(selected());
                        if (Ui.confirm(
                            owner,
                            "Hủy hợp đồng #"
                                + contract.id()
                                + " nhập nhầm? Chỉ được khi chưa có hóa đơn và chưa ghi sổ cọc.")) {
                          rentals.cancelUnusedContract(contract.id());
                          refresh();
                        }
                      })));
      Object[][] rows = new Object[contracts.size()][];
      for (int i = 0; i < contracts.size(); i++) {
        Contract k = contracts.get(i);
        rows[i] =
            new Object[] {
              k.id(),
              k.roomName(),
              k.tenantName(),
              k.startDate(),
              k.effectiveEnd(),
              Ui.money(k.rent()),
              Ui.money(balances.get(k.id())),
              k.status()
            };
      }
      table =
          table(
              new String[] {
                "Mã",
                "Phòng",
                "Người thuê",
                "Vào ở",
                "Đến",
                "Tiền/tháng",
                "Cọc đang giữ",
                "Trạng thái"
              },
              rows);
    } else {
      actions.add(
          Design.primary(
              Ui.button(
                  "Ghi nhận thu",
                  () ->
                      safe(
                          () -> {
                            new PaymentDialog(owner, repository, debts.get(selected()))
                                .setVisible(true);
                            refresh();
                          }))));
      actions.add(Ui.button("Xuất PDF", () -> safe(this::exportPdf)));
      BigDecimal total = BigDecimal.ZERO, overdue = BigDecimal.ZERO;
      Object[][] rows = new Object[debts.size()][];
      for (int i = 0; i < debts.size(); i++) {
        Bill bill = debts.get(i);
        BigDecimal received = balances.get(bill.id()), remaining = bill.total().subtract(received);
        boolean late =
            !bill.dueDate().isBlank()
                && RentalService.date(bill.dueDate()).isBefore(LocalDate.now());
        total = total.add(remaining);
        if (late) overdue = overdue.add(remaining);
        rows[i] =
            new Object[] {
              bill.month(),
              bill.roomName(),
              bill.tenantName().isBlank() ? "Điện nước cũ" : bill.tenantName(),
              Ui.money(bill.total()),
              Ui.money(received),
              Ui.money(remaining),
              bill.dueDate().isBlank() ? "Chưa có" : bill.dueDate(),
              late ? "Quá hạn" : "Còn thiếu"
            };
      }
      actions.add(
          Design.label(
              "Còn thiếu: " + Ui.money(total) + "  ·  Quá hạn: " + Ui.money(overdue),
              15,
              Design.INK,
              true));
      table =
          table(
              new String[] {
                "Kỳ", "Phòng", "Người thuê", "Tổng", "Đã thu", "Còn thiếu", "Hạn thu", "Trạng thái"
              },
              rows);
    }
    vn.nhatrohomu.shared.RowActions.install(
        table,
        Arrays.stream(actions.getComponents())
            .filter(c -> c instanceof JButton b && !b.getText().startsWith("+"))
            .map(c -> (JButton) c)
            .toList());
    actions.add(Ui.refreshButton(this::refresh));
    heading.add(actions, BorderLayout.SOUTH);
    add(heading, BorderLayout.NORTH);
    JTextField search = new JTextField(22);
    search.putClientProperty("JTextField.placeholderText", "Tìm tên, phòng, kỳ…");
    javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
        new javax.swing.table.TableRowSorter<>((DefaultTableModel) table.getModel());
    table.setRowSorter(sorter);
    search
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filter();
              }

              void filter() {
                String value = search.getText().trim();
                sorter.setRowFilter(
                    value.isEmpty()
                        ? null
                        : RowFilter.regexFilter("(?iu)" + java.util.regex.Pattern.quote(value)));
              }
            });
    JPanel main = Design.clear(new BorderLayout(0, 12));
    main.add(search, BorderLayout.NORTH);
    main.add(new JScrollPane(table));
    add(main);
    add(
        Design.label(
            page == Page.CONTRACTS
                ? "Tiền cọc tách khỏi hóa đơn/doanh thu. Điều khoản đã lưu được giữ nguyên; thay"
                    + " đổi bằng hợp đồng kỳ mới."
                : page == Page.DEBTS
                    ? "Ngày hạn lấy từ hợp đồng lúc lập hóa đơn. Hóa đơn điện nước cũ không có hạn"
                        + " tự suy ra."
                    : "Chỉ lưu thông tin cần dùng; bản sao lưu chứa thông tin người thuê.",
            11,
            Design.MUTED,
            false),
        BorderLayout.SOUTH);
    revalidate();
    repaint();
  }

  private JTable table(String[] columns, Object[][] rows) {
    DefaultTableModel model =
        new DefaultTableModel(rows, columns) {
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    JTable result = new JTable(model);
    result.setRowHeight(40);
    result.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    result.getTableHeader().setReorderingAllowed(false);
    return result;
  }

  private int selected() {
    int row = table.getSelectedRow();
    if (row < 0) throw new IllegalArgumentException("Chọn một dòng trước.");
    return table.convertRowIndexToModel(row);
  }

  @FunctionalInterface
  interface Task {
    void run() throws Exception;
  }

  private void safe(Task task) {
    try {
      task.run();
    } catch (Exception e) {
      Ui.error(owner, e);
    }
  }

  private void form(String title, JPanel fields, Task save) {
    JDialog dialog = new JDialog(owner, title, true);
    JPanel root = new JPanel(new BorderLayout(0, 16));
    root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
    root.add(new JScrollPane(fields));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Hủy", dialog::dispose));
    buttons.add(
        Design.primary(
            Ui.button(
                "Lưu",
                () -> {
                  try {
                    save.run();
                    dialog.dispose();
                    refresh();
                  } catch (Exception e) {
                    Ui.error(dialog, e);
                  }
                })));
    root.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setSize(Math.max(620, dialog.getWidth()), Math.min(730, dialog.getHeight() + 15));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private void tenantForm(Tenant tenant) {
    Tenant value = tenant == null ? new Tenant(0, "", "", "", true) : tenant;
    JTextField name = new JTextField(value.name(), 25),
        phone = new JTextField(value.phone()),
        zalo = new JTextField(value.zaloContact()),
        identity = new JTextField(value.identity()),
        birthday = new JTextField(value.birthday()),
        address = new JTextField(value.address());
    JCheckBox auto = new JCheckBox("Zalo dùng số điện thoại", value.zaloAuto());
    zalo.setEditable(!auto.isSelected());
    auto.addActionListener(
        e -> {
          zalo.setEditable(!auto.isSelected());
          if (auto.isSelected()) zalo.setText(phone.getText());
        });
    phone
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                sync();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                sync();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                sync();
              }

              void sync() {
                if (auto.isSelected()) zalo.setText(phone.getText());
              }
            });
    JTextArea note = new JTextArea(value.note(), 3, 25);
    note.setLineWrap(true);
    note.setWrapStyleWord(true);
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Họ tên", name);
    Ui.field(fields, 1, "Điện thoại", phone);
    Ui.field(fields, 2, "Tự điền Zalo", auto);
    Ui.field(fields, 3, "Số Zalo (chưa xác minh tài khoản)", zalo);
    Ui.field(fields, 4, "CCCD / giấy tờ (tùy chọn)", identity);
    Ui.field(fields, 5, "Ngày sinh YYYY-MM-DD (tùy chọn)", birthday);
    Ui.field(fields, 6, "Địa chỉ thường trú", address);
    Ui.field(fields, 7, "Ghi chú", new JScrollPane(note));
    DefaultTableModel members =
        new DefaultTableModel(
            new String[] {
              "Họ tên", "Quan hệ", "Ngày sinh YYYY-MM-DD", "CCCD / giấy tờ", "Điện thoại"
            },
            0);
    for (Resident r : value.residents())
      members.addRow(
          new Object[] {r.name(), r.relationship(), r.birthday(), r.identity(), r.phone()});
    JTable roster = new JTable(members);
    roster.setRowHeight(34);
    roster.putClientProperty("terminateEditOnFocusLost", true);
    roster.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    for (int i = 0; i < 5; i++)
      roster.getColumnModel().getColumn(i).setPreferredWidth(i == 2 ? 185 : 150);
    JPanel family = new JPanel(new BorderLayout(0, 8));
    JTextArea help =
        new JTextArea(
            "Gia đình / người ở cùng của hồ sơ này. Mỗi người một dòng; tên bắt buộc, các ô khác có"
                + " thể để trống. Đây là danh sách hiện tại, chưa phải lịch sử vào/ra theo hợp"
                + " đồng.");
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setEditable(false);
    help.setOpaque(false);
    help.setRows(3);
    family.add(help, BorderLayout.NORTH);
    family.add(new JScrollPane(roster));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(
        Ui.button("+ Người ở cùng", () -> members.addRow(new Object[] {"", "", "", "", ""})));
    JButton removeMember =
        Ui.button(
            "Xóa dòng",
            () -> {
              if (roster.isEditing() && !roster.getCellEditor().stopCellEditing()) return;
              if (roster.getSelectedRow() >= 0) members.removeRow(roster.getSelectedRow());
            });
    controls.add(removeMember);
    vn.nhatrohomu.shared.RowActions.install(roster, List.of(removeMember), false);
    family.add(controls, BorderLayout.SOUTH);
    family.setPreferredSize(new Dimension(580, 280));
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Thông tin người thuê", new JScrollPane(fields));
    tabs.addTab("Gia đình / người ở cùng", family);
    JPanel content = new JPanel(new BorderLayout());
    content.add(tabs);
    form(
        tenant == null ? "Thêm người thuê" : "Hồ sơ · " + value.name(),
        content,
        () -> {
          if (roster.isEditing() && !roster.getCellEditor().stopCellEditing())
            throw new IllegalArgumentException("Hãy hoàn tất dòng đang sửa.");
          List<Resident> people = new ArrayList<>();
          for (int i = 0; i < members.getRowCount(); i++) {
            String[] row = new String[5];
            for (int j = 0; j < 5; j++)
              row[j] = Objects.toString(members.getValueAt(i, j), "").trim();
            people.add(new Resident(row[0], row[1], row[2], row[3], row[4]));
          }
          rentals.saveTenant(
              new Tenant(
                  value.id(),
                  name.getText().trim(),
                  phone.getText().trim(),
                  note.getText(),
                  true,
                  zalo.getText().trim(),
                  auto.isSelected(),
                  identity.getText().trim(),
                  birthday.getText().trim(),
                  address.getText().trim(),
                  people));
        });
  }

  private void tenantHistory(Tenant tenant) throws Exception {
    StringBuilder text =
        new StringBuilder(tenant.name() + "\nĐiện thoại: " + tenant.phone() + "\n");
    for (Contract k : rentals.contracts())
      if (k.tenantId() == tenant.id())
        text.append("\n#")
            .append(k.id())
            .append(" · ")
            .append(k.roomName())
            .append(" · ")
            .append(k.startDate())
            .append(" → ")
            .append(k.effectiveEnd())
            .append(" · ")
            .append(k.status());
    textDialog("Lịch sử thuê", text.toString());
  }

  private void contractForm() throws Exception {
    List<Room> rooms = repository.rooms();
    List<Tenant> people = rentals.tenants();
    if (rooms.isEmpty() || people.isEmpty())
      throw new IllegalArgumentException("Thêm phòng và người thuê trước khi tạo hợp đồng.");
    JComboBox<Room> room = new JComboBox<>(rooms.toArray(Room[]::new));
    room.setRenderer(
        new DefaultListCellRenderer() {
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            return super.getListCellRendererComponent(
                list, value instanceof Room r ? r.name() : value, index, selected, focus);
          }
        });
    JComboBox<Tenant> tenant = new JComboBox<>(people.toArray(Tenant[]::new));
    JTextField start = new JTextField(LocalDate.now().toString()),
        end = new JTextField(LocalDate.now().plusYears(1).minusDays(1).toString()),
        rent = new JTextField("2000000"),
        deposit = new JTextField("2000000");
    JSpinner due = new JSpinner(new SpinnerNumberModel(5, 1, 28, 1));
    JTextArea fees = new JTextArea("Internet=100000\nVệ sinh=30000", 4, 28);
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Phòng", room);
    Ui.field(fields, 1, "Người thuê đại diện", tenant);
    Ui.field(fields, 2, "Ngày vào ở · YYYY-MM-DD", start);
    Ui.field(fields, 3, "Ngày hết hạn (gồm ngày này)", end);
    Ui.field(fields, 4, "Tiền phòng / tháng (đ)", rent);
    Ui.field(fields, 5, "Mức cọc cần nhận (đ)", deposit);
    Ui.field(fields, 6, "Ngày hạn thu mỗi tháng", due);
    Ui.field(fields, 7, "Phí / tháng · mỗi dòng Tên=số tiền", new JScrollPane(fees));
    Ui.field(
        fields,
        8,
        "Lưu ý",
        new JLabel("Một hợp đồng/phòng/tháng. Không tự chia tiền theo số ngày."));
    form(
        "Tạo hợp đồng thuê",
        fields,
        () -> {
          Room selected = (Room) room.getSelectedItem();
          Tenant person = (Tenant) tenant.getSelectedItem();
          rentals.createContract(
              new Contract(
                  0,
                  selected.id(),
                  selected.name(),
                  person.id(),
                  person.name(),
                  start.getText().trim(),
                  end.getText().trim(),
                  "",
                  RentalService.money(rent.getText(), "Tiền thuê"),
                  RentalService.money(deposit.getText(), "Tiền cọc"),
                  (Integer) due.getValue(),
                  parseFees(fees.getText())));
        });
  }

  static List<Charge> parseFees(String text) {
    List<Charge> result = new ArrayList<>();
    for (String line : text.split("\\R")) {
      if (line.isBlank()) continue;
      int split = line.lastIndexOf('=');
      if (split < 1) throw new IllegalArgumentException("Phí: mỗi dòng cần dạng Internet=100000.");
      result.add(
          new Charge(
              line.substring(0, split).trim(),
              RentalService.money(line.substring(split + 1), "Phí")));
    }
    return result;
  }

  private void contractDetails(Contract contract) throws Exception {
    StringBuilder text =
        new StringBuilder(
            "HỢP ĐỒNG #"
                + contract.id()
                + "\nPhòng: "
                + contract.roomName()
                + "\nNgười thuê: "
                + contract.tenantName()
                + "\nTừ: "
                + contract.startDate()
                + "\nHết hạn dự kiến: "
                + contract.endDate()
                + "\nKết thúc thực tế: "
                + (contract.closedDate().isBlank() ? "Chưa ghi nhận" : contract.closedDate())
                + "\nTiền thuê / tháng: "
                + Ui.money(contract.rent())
                + "\nMức cọc: "
                + Ui.money(contract.deposit())
                + "\nCọc đang giữ: "
                + Ui.money(rentals.heldDeposit(contract.id()))
                + "\nNgày hạn thu: "
                + contract.dueDay()
                + "\nPhí định kỳ:\n");
    for (Charge fee : contract.fees())
      text.append("  ")
          .append(fee.label())
          .append(": ")
          .append(Ui.money(fee.amount()))
          .append("\n");
    textDialog("Chi tiết hợp đồng", text.toString());
  }

  private void closeForm(Contract contract) {
    JTextField date = new JTextField(LocalDate.now().toString(), 20);
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Ngày kết thúc thuê", date);
    Ui.field(
        fields,
        1,
        "Cọc và công nợ",
        new JLabel("Vẫn được giữ để thu/hoàn riêng. Người mới bắt đầu từ tháng sau."));
    form(
        "Kết thúc · " + contract.roomName(),
        fields,
        () -> {
          if (!Ui.confirm(
              owner,
              "Ghi nhận kết thúc hợp đồng #" + contract.id() + " vào " + date.getText() + "?"))
            throw new IllegalArgumentException("Chưa xác nhận kết thúc.");
          rentals.closeContract(contract.id(), date.getText().trim());
        });
  }

  private void depositDialog(Contract contract) throws Exception {
    JDialog dialog =
        new JDialog(owner, "Sổ cọc · " + contract.roomName() + " / " + contract.tenantName(), true);
    JPanel root = new JPanel(new BorderLayout(0, 15));
    root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
    JLabel balance = new JLabel();
    root.add(balance, BorderLayout.NORTH);
    DefaultTableModel model =
        new DefaultTableModel(new String[] {"Ngày", "Loại", "Số tiền", "Ghi chú"}, 0) {
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    JTable ledger = new JTable(model);
    ledger.setRowHeight(34);
    root.add(new JScrollPane(ledger));
    JComboBox<String> kind = new JComboBox<>(new String[] {"Nhận cọc", "Hoàn cọc"});
    JTextField amount = new JTextField(14),
        date = new JTextField(LocalDate.now().toString(), 10),
        note = new JTextField(20);
    Runnable update =
        () ->
            safe(
                () -> {
                  balance.setText(
                      "Mức cọc: "
                          + Ui.money(contract.deposit())
                          + " · Đang giữ: "
                          + Ui.money(rentals.heldDeposit(contract.id()))
                          + " · Cọc tách khỏi hóa đơn");
                  model.setRowCount(0);
                  for (DepositEntry e : rentals.deposits(contract.id()))
                    model.addRow(
                        new Object[] {
                          e.date(),
                          e.kind().equals("RECEIVE") ? "Nhận" : "Hoàn",
                          Ui.money(e.amount()),
                          e.note()
                        });
                });
    update.run();
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Loại", kind);
    Ui.field(fields, 1, "Số tiền nguyên đồng", amount);
    Ui.field(fields, 2, "Ngày · YYYY-MM-DD", date);
    Ui.field(fields, 3, "Ghi chú", note);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(
        Ui.button(
            "Ghi sổ",
            () -> {
              try {
                rentals.addDeposit(
                    contract.id(),
                    kind.getSelectedIndex() == 0 ? "RECEIVE" : "REFUND",
                    RentalService.money(amount.getText(), "Tiền cọc"),
                    date.getText().trim(),
                    note.getText());
                amount.setText("");
                note.setText("");
                update.run();
                refresh();
              } catch (Exception e) {
                Ui.error(dialog, e);
              }
            }));
    buttons.add(Ui.button("Đóng", dialog::dispose));
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(fields);
    bottom.add(buttons, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    dialog.setContentPane(root);
    dialog.setSize(850, 590);
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private void textDialog(String title, String text) {
    JTextArea area = new JTextArea(text, 18, 48);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    JOptionPane.showMessageDialog(owner, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
  }

  private void exportPdf() throws Exception {
    Bill bill = debts.get(selected());
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("hoa-don-" + bill.id() + ".pdf"));
    if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
    Path path = chooser.getSelectedFile().toPath();
    if (java.nio.file.Files.exists(path) && !Ui.confirm(owner, "Ghi đè file PDF đã có?")) return;
    Ui.background(
        owner,
        () -> {
          InvoicePdf.write(path, bill, repository.payments(bill.id()));
          return path;
        },
        result -> JOptionPane.showMessageDialog(owner, "Đã xuất PDF: " + result));
  }
}
