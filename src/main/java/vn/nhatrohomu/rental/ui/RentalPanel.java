package vn.nhatrohomu.rental.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.BillingService;
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
  private final java.util.function.Consumer<Bill> showBill;
  private List<Contract> tenantContracts = List.of();
  private JTable table;
  private List<Tenant> tenants = List.of();
  private List<Contract> contracts = List.of();
  private List<Bill> debts = List.of();
  private boolean busy;
  private boolean refreshPending;
  private Map<Long, BigDecimal> balances = Map.of();
  private Map<Long, Tenant> contractPeople = Map.of();

  private record PanelData(
      Object items,
      Map<Long, BigDecimal> balances,
      List<Contract> links,
      Map<Long, Tenant> people) {}

  public RentalPanel(JFrame owner, Repository repository, Page page) {
    this(
        owner,
        repository,
        page,
        bill -> {
          try {
            new PaymentDialog(owner, repository, bill).setVisible(true);
          } catch (Exception ex) {
            Ui.error(owner, ex);
          }
        });
  }

  public RentalPanel(
      JFrame owner, Repository repository, Page page, java.util.function.Consumer<Bill> showBill) {
    super(new BorderLayout(0, 18));
    this.owner = owner;
    this.showBill = showBill;
    this.repository = repository;
    this.rentals = repository.rentals();
    this.page = page;
    setOpaque(false);
    refresh();
  }

  public void refreshData() {
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
        Map<Long, Tenant> people = new HashMap<>();
        if (page == Page.CONTRACTS) {
          for (Tenant person : rentals.tenants()) people.put(person.id(), person);
          for (Object value : (List<?>) items) {
            Contract contract = (Contract) value;
            amounts.put(contract.id(), rentals.heldDeposit(contract.id()));
            if (!people.containsKey(contract.tenantId())) {
              Tenant person = rentals.tenant(contract.tenantId());
              if (person != null) people.put(person.id(), person);
            }
          }
        }
        if (page == Page.DEBTS)
          for (Object value : (List<?>) items) {
            Bill bill = (Bill) value;
            amounts.put(bill.id(), repository.received(bill.id()));
          }
        return new PanelData(
            items, amounts, page == Page.TENANTS ? rentals.contracts() : List.of(), people);
      }

      @SuppressWarnings("unchecked")
      protected void done() {
        busy = false;
        if (!owner.isDisplayable()) return;
        try {
          PanelData loaded = get();
          Object data = loaded.items();
          balances = loaded.balances();
          contractPeople = loaded.people();
          tenantContracts = loaded.links();
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
          case CONTRACTS -> "Thuê phòng";
          case DEBTS -> "Công nợ";
        };
    String subtitle =
        switch (page) {
          case TENANTS ->
              "Thông tin liên hệ và lịch sử thuê. Phòng được gắn với người thuê qua hợp đồng.";
          case CONTRACTS ->
              "Người đang ở, lịch sử trả phòng và người chưa gắn phòng. Sửa thông tin và người ở"
                  + " cùng ngay tại đây.";
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
      actions.add(Design.primary(Ui.button("+ Người đứng tên", () -> tenantForm(null))));
      actions.add(
          Ui.button("Hồ sơ / người ở cùng", () -> safe(() -> tenantForm(tenants.get(selected())))));
      actions.add(
          Ui.button(
              "Phòng / hợp đồng / hóa đơn",
              () -> safe(() -> tenantConnections(tenants.get(selected())))));
      actions.add(
          Ui.button("Gắn phòng", () -> safe(() -> contractForm(tenants.get(selected()).id()))));
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
                        t.id(),
                        t.name(),
                        currentRooms(t.id()),
                        t.phone(),
                        t.zaloContact(),
                        t.residents().size(),
                        t.note()
                      })
              .toArray(Object[][]::new);
      table =
          table(
              new String[] {
                "Mã", "Họ tên", "Phòng đang thuê", "Điện thoại", "Zalo", "Người ở cùng", "Ghi chú"
              },
              rows);
    } else if (page == Page.CONTRACTS) {
      actions.add(Design.primary(Ui.button("+ Cho thuê phòng", () -> safe(this::contractForm))));
      actions.add(Ui.button("+ Thêm người thuê", () -> tenantForm(null)));
      actions.add(
          Ui.button("Xem chi tiết", () -> safe(() -> contractDetails(contracts.get(selected())))));
      actions.add(
          Ui.button(
              "Thông tin & người ở cùng",
              () -> safe(() -> openContractPerson(contracts.get(selected())))));
      actions.add(
          Ui.button("Nhận / hoàn cọc", () -> safe(() -> depositDialog(contracts.get(selected())))));
      actions.add(
          Ui.button(
              "Trả phòng / chốt kỳ cuối", () -> safe(() -> closeForm(contracts.get(selected())))));
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
              k.endLabel(),
              Ui.money(k.rent()),
              Ui.money(balances.get(k.id())),
              k.status(),
              familySummary(contractPeople.get(k.tenantId()))
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
                "Trạng thái",
                "Người ở cùng / gia đình"
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
    List<JButton> rowActions =
        Arrays.stream(actions.getComponents())
            .filter(c -> c instanceof JButton b && !b.getText().startsWith("+"))
            .map(c -> (JButton) c)
            .toList();
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
    main.add(new vn.nhatrohomu.shared.SelectionView(table, rowActions, 1, "rental-" + page.name()));
    if (page == Page.CONTRACTS) {
      List<Tenant> unassigned =
          contractPeople.values().stream()
              .filter(t -> t.active() && contracts.stream().noneMatch(k -> k.tenantId() == t.id()))
              .sorted(Comparator.comparing(Tenant::name))
              .toList();
      if (!unassigned.isEmpty()) main.add(unassignedPeople(unassigned), BorderLayout.SOUTH);
    }
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

  private JPanel unassignedPeople(List<Tenant> people) {
    JPanel section = Design.clear(new BorderLayout(0, 8));
    section.add(
        Design.label("Người thuê chưa gắn phòng · " + people.size(), 15, Design.INK, true),
        BorderLayout.NORTH);
    JPanel rows = Ui.column();
    rows.setOpaque(false);
    for (Tenant person : people) {
      JPanel row = Design.card(new BorderLayout(12, 0), 10);
      JPanel words = Ui.column();
      words.setOpaque(false);
      words.add(Design.label(person.name(), 14, Design.INK, true));
      words.add(
          Design.label(
              (person.phone().isBlank() ? "Chưa có điện thoại" : person.phone())
                  + " · "
                  + person.residents().size()
                  + " người ở cùng",
              11,
              Design.MUTED,
              false));
      row.add(words);
      JPanel buttons = Design.clear(new FlowLayout(FlowLayout.RIGHT));
      buttons.add(Ui.button("Thông tin & người ở cùng", () -> tenantForm(person)));
      buttons.add(Ui.button("Cho thuê phòng", () -> safe(() -> contractForm(person.id()))));
      buttons.add(
          Ui.button(
              "Lưu trữ",
              () ->
                  safe(
                      () -> {
                        if (Ui.confirm(owner, "Lưu trữ người thuê " + person.name() + "?")) {
                          rentals.archiveTenant(person.id());
                          refresh();
                        }
                      })));
      row.add(buttons, BorderLayout.EAST);
      rows.add(row);
      rows.add(Box.createVerticalStrut(6));
    }
    JScrollPane scroll = new JScrollPane(rows);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setPreferredSize(new Dimension(600, Math.min(180, people.size() * 80)));
    scroll.getVerticalScrollBar().setUnitIncrement(20);
    section.add(scroll);
    return section;
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

  public static void editProfile(JFrame owner, Repository repository, Runnable done) {
    RentalPanel controller = new RentalPanel(owner, repository, Page.TENANTS);
    controller.tenantForm(null);
    done.run();
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
    JScrollPane scroll = new JScrollPane(fields);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    root.add(scroll);
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
    Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    dialog.setSize(
        Math.min(screen.width - 40, Math.max(720, dialog.getWidth() + 24)),
        Math.min(screen.height - 40, Math.min(730, dialog.getHeight() + 15)));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private void residentForm(JTable roster, DefaultTableModel members, int row) {
    if (roster.isEditing() && !roster.getCellEditor().stopCellEditing()) return;
    JTextField[] inputs = new JTextField[5];
    for (int i = 0; i < 5; i++)
      inputs[i] =
          new JTextField(row < 0 ? "" : Objects.toString(members.getValueAt(row, i), ""), 22);
    JPanel fields = Ui.form();
    String[] labels = {"Họ tên", "Quan hệ", "Ngày sinh YYYY-MM-DD", "CCCD / giấy tờ", "Điện thoại"};
    for (int i = 0; i < 5; i++)
      Ui.field(
          fields,
          i,
          labels[i],
          i == 2 ? vn.nhatrohomu.shared.DatePicker.field(inputs[i], false) : inputs[i]);
    while (JOptionPane.showConfirmDialog(
            roster,
            fields,
            row < 0 ? "Thêm người ở cùng" : "Sửa người ở cùng",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      try {
        String[] values =
            Arrays.stream(inputs).map(field -> field.getText().trim()).toArray(String[]::new);
        vn.nhatrohomu.shared.TenantRules.resident(
            new Resident(values[0], values[1], values[2], values[3], values[4]));
        if (row < 0) members.addRow(values);
        else for (int i = 0; i < 5; i++) members.setValueAt(values[i], row, i);
        return;
      } catch (IllegalArgumentException ex) {
        Ui.error(roster, ex);
      }
    }
  }

  private void tenantForm(Tenant tenant) {
    tenantForm(tenant, false);
  }

  private void tenantForm(Tenant tenant, boolean showFamily) {
    Tenant value = tenant == null ? new Tenant(0, "", "", "", true) : tenant;
    JTextField name = new JTextField(value.name(), 25),
        phone = new JTextField(value.phone()),
        zalo = new JTextField(value.zaloContact()),
        identity = new JTextField(value.identity()),
        birthday = new JTextField(value.birthday()),
        address = new JTextField(value.address());
    name.setName("tenant-name");
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
    Ui.field(
        fields,
        5,
        "Ngày sinh YYYY-MM-DD (tùy chọn)",
        vn.nhatrohomu.shared.DatePicker.field(birthday, false));
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
    controls.add(Ui.button("+ Người ở cùng", () -> residentForm(roster, members, -1)));
    JButton removeMember =
        Ui.button(
            "Xóa dòng",
            () -> {
              if (roster.isEditing() && !roster.getCellEditor().stopCellEditing()) return;
              if (roster.getSelectedRow() >= 0) members.removeRow(roster.getSelectedRow());
            });
    JButton editMember =
        Ui.button("Sửa người ở cùng", () -> residentForm(roster, members, roster.getSelectedRow()));
    controls.add(editMember);
    controls.add(removeMember);
    vn.nhatrohomu.shared.RowActions.install(roster, List.of(editMember, removeMember), false);
    family.add(controls, BorderLayout.SOUTH);
    family.setPreferredSize(new Dimension(580, 280));
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Thông tin người thuê", new JScrollPane(fields));
    tabs.addTab("Gia đình / người ở cùng", family);
    if (showFamily) tabs.setSelectedIndex(1);
    JPanel content = new JPanel(new BorderLayout());
    content.add(tabs);
    form(
        tenant == null ? "Thêm người thuê" : "Thông tin người thuê · " + value.name(),
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

  private String currentRooms(long tenantId) {
    String rooms =
        String.join(
            ", ",
            tenantContracts.stream()
                .filter(k -> k.tenantId() == tenantId && k.occupiedOn(LocalDate.now()))
                .map(Contract::roomName)
                .toList());
    if (!rooms.isBlank()) return rooms;
    String future =
        String.join(
            ", ",
            tenantContracts.stream()
                .filter(
                    k ->
                        k.tenantId() == tenantId
                            && RentalService.date(k.startDate()).isAfter(LocalDate.now()))
                .map(Contract::roomName)
                .toList());
    return future.isBlank() ? "Chưa có hợp đồng đang hiệu lực" : "Sắp thuê: " + future;
  }

  private void tenantConnections(Tenant tenant) {
    JDialog dialog = new JDialog(owner, "Liên kết · " + tenant.name(), true);
    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    root.add(
        Design.label(
            tenant.name() + " · phòng, hợp đồng, hóa đơn và công nợ", 18, Design.INK, true),
        BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    root.add(tabs);
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottom.add(
        Ui.button(
            "Gắn phòng / tạo hợp đồng",
            () ->
                safe(
                    () -> {
                      contractForm(tenant.id());
                      dialog.dispose();
                    })));
    bottom.add(Ui.button("Đóng", dialog::dispose));
    root.add(bottom, BorderLayout.SOUTH);
    dialog.setContentPane(root);
    dialog.setSize(950, 650);
    dialog.setLocationRelativeTo(owner);
    record Links(List<Contract> contracts, List<Bill> bills, Map<Long, BigDecimal> received) {}
    Ui.background(
        dialog,
        () -> {
          List<Contract> links =
              rentals.contracts().stream().filter(k -> k.tenantId() == tenant.id()).toList();
          Set<Long> ids = new HashSet<>();
          for (Contract k : links) ids.add(k.id());
          List<Bill> invoices =
              repository.billsBetween("1900-01", "9999-12").stream()
                  .filter(b -> b.contractId() != null && ids.contains(b.contractId()))
                  .toList();
          Map<Long, BigDecimal> received = new HashMap<>();
          for (Bill b : invoices) received.put(b.id(), repository.received(b.id()));
          return new Links(links, invoices, received);
        },
        data -> {
          Object[][] rows =
              data.contracts().stream()
                  .map(
                      k ->
                          new Object[] {
                            k.id(),
                            k.roomName(),
                            k.startDate(),
                            k.endLabel(),
                            Ui.money(k.rent()),
                            k.status()
                          })
                  .toArray(Object[][]::new);
          JTable linked =
              table(
                  new String[] {"Hợp đồng", "Phòng", "Vào ở", "Đến", "Tiền phòng", "Trạng thái"},
                  rows);
          JButton contract =
              Ui.button(
                  "Xem hợp đồng",
                  () ->
                      safe(
                          () ->
                              contractDetails(
                                  data.contracts()
                                      .get(
                                          linked.convertRowIndexToModel(
                                              linked.getSelectedRow())))));
          vn.nhatrohomu.shared.RowActions.install(linked, List.of(contract));
          tabs.addTab(
              "Phòng & hợp đồng",
              new vn.nhatrohomu.shared.SelectionView(
                  linked, List.of(contract), 1, "tenant-contracts"));
          Object[][] bills =
              data.bills().stream()
                  .map(
                      b ->
                          new Object[] {
                            b.id(),
                            b.roomName(),
                            b.month(),
                            Ui.money(b.total()),
                            Ui.money(data.received().get(b.id())),
                            Ui.money(b.total().subtract(data.received().get(b.id())))
                          })
                  .toArray(Object[][]::new);
          JTable invoice =
              table(new String[] {"Hóa đơn", "Phòng", "Kỳ", "Tổng", "Đã thu", "Còn thiếu"}, bills);
          JButton detail =
              Ui.button(
                  "Xem hóa đơn",
                  () -> {
                    int row = invoice.convertRowIndexToModel(invoice.getSelectedRow());
                    showBill.accept(data.bills().get(row));
                  });
          JButton collect =
              Ui.button(
                  "Thu tiền",
                  () ->
                      safe(
                          () -> {
                            int row = invoice.convertRowIndexToModel(invoice.getSelectedRow());
                            Bill b = data.bills().get(row);
                            new PaymentDialog(owner, repository, b).setVisible(true);
                            BigDecimal received = repository.received(b.id());
                            invoice.getModel().setValueAt(Ui.money(received), row, 4);
                            invoice
                                .getModel()
                                .setValueAt(Ui.money(b.total().subtract(received)), row, 5);
                            refresh();
                          }));
          vn.nhatrohomu.shared.RowActions.install(invoice, List.of(detail, collect));
          tabs.addTab(
              "Hóa đơn & công nợ",
              new vn.nhatrohomu.shared.SelectionView(
                  invoice, List.of(detail, collect), 1, "tenant-invoices"));
          if (data.contracts().isEmpty())
            root.add(
                new JLabel(
                    "Người thuê chưa được gắn phòng. Tạo hợp đồng để liên kết; không tự gán hóa đơn"
                        + " Excel thiếu tên."),
                BorderLayout.NORTH);
          dialog.revalidate();
          dialog.repaint();
        });
    dialog.setVisible(true);
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
            .append(k.endLabel())
            .append(" · ")
            .append(k.status());
    textDialog("Lịch sử thuê", text.toString());
  }

  private void contractForm() throws Exception {
    contractForm(null);
  }

  public static void letRoom(JFrame owner, Repository repository, Long roomId, Runnable changed) {
    try {
      RentalPanel controller = new RentalPanel(owner, repository, Page.CONTRACTS);
      controller.contractForm(null, roomId);
      changed.run();
    } catch (Exception ex) {
      Ui.error(owner, ex);
    }
  }

  private void contractForm(Long preferredTenant) throws Exception {
    contractForm(preferredTenant, null);
  }

  private void contractForm(Long preferredTenant, Long preferredRoom) throws Exception {
    List<Room> rooms = repository.rooms();
    List<Tenant> people = rentals.tenants();
    if (rooms.isEmpty()) throw new IllegalArgumentException("Thêm phòng trước khi cho thuê.");
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
    tenant.setName("rental-tenant");
    if (preferredRoom != null)
      for (int i = 0; i < room.getItemCount(); i++)
        if (room.getItemAt(i).id() == preferredRoom) room.setSelectedIndex(i);
    JPanel tenantChoice = new JPanel(new BorderLayout(8, 0));
    tenantChoice.setOpaque(false);
    tenantChoice.add(tenant);
    tenantChoice.add(
        Ui.button(
            "+ Người đứng tên",
            () -> {
              Set<Long> before = new HashSet<>();
              for (int i = 0; i < tenant.getItemCount(); i++) before.add(tenant.getItemAt(i).id());
              Tenant previous = (Tenant) tenant.getSelectedItem();
              tenantForm(null);
              try {
                List<Tenant> updated = rentals.tenants();
                tenant.removeAllItems();
                updated.forEach(tenant::addItem);
                Tenant selected =
                    updated.stream()
                        .filter(person -> !before.contains(person.id()))
                        .findFirst()
                        .orElse(previous);
                if (selected != null)
                  updated.stream()
                      .filter(person -> person.id() == selected.id())
                      .findFirst()
                      .ifPresent(tenant::setSelectedItem);
              } catch (Exception ex) {
                Ui.error(owner, ex);
              }
            }),
        BorderLayout.EAST);
    Component addPerson =
        ((BorderLayout) tenantChoice.getLayout()).getLayoutComponent(BorderLayout.EAST);
    JPanel personActions = Design.clear(new GridLayout(0, 1, 0, 6));
    tenantChoice.remove(addPerson);
    personActions.add(addPerson);
    personActions.add(
        Ui.button(
            "Gia đình / người ở cùng",
            () ->
                safe(
                    () -> {
                      Tenant selected = (Tenant) tenant.getSelectedItem();
                      if (selected == null)
                        throw new IllegalArgumentException(
                            "Thêm hoặc chọn người đứng tên trước, rồi khai người ở cùng.");
                      Tenant current = rentals.tenant(selected.id());
                      if (current == null)
                        throw new IllegalArgumentException("Không tìm thấy người đứng tên.");
                      tenantForm(current, true);
                      tenant.removeAllItems();
                      for (Tenant value : rentals.tenants()) tenant.addItem(value);
                      for (int i = 0; i < tenant.getItemCount(); i++)
                        if (tenant.getItemAt(i).id() == selected.id()) tenant.setSelectedIndex(i);
                    })));
    tenantChoice.add(personActions, BorderLayout.EAST);
    if (preferredTenant != null)
      for (int i = 0; i < tenant.getItemCount(); i++)
        if (tenant.getItemAt(i).id() == preferredTenant) tenant.setSelectedIndex(i);
    LocalDate proposedStart = LocalDate.now();
    String lastInvoice = repository.latestMonth(((Room) room.getSelectedItem()).id());
    if (lastInvoice != null
        && !YearMonth.parse(lastInvoice).isBefore(YearMonth.from(proposedStart)))
      proposedStart =
          repository
              .roomBills(((Room) room.getSelectedItem()).id(), lastInvoice, lastInvoice)
              .stream()
              .map(
                  b ->
                      b.periodEnd().isBlank()
                          ? YearMonth.parse(b.month()).atEndOfMonth()
                          : RentalService.date(b.periodEnd()))
              .max(java.util.Comparator.naturalOrder())
              .orElse(YearMonth.parse(lastInvoice).atEndOfMonth())
              .plusDays(1);
    if (proposedStart.isBefore(LocalDate.now())) proposedStart = LocalDate.now();
    JTextField start = new JTextField(proposedStart.toString()),
        end = new JTextField(""),
        rent =
            new JTextField(
                ((Room) room.getSelectedItem()).defaultRent() == null
                    ? ""
                    : ((Room) room.getSelectedItem()).defaultRent().toPlainString()),
        deposit = new JTextField("0");
    rent.setName("rental-rent");
    rent.setEditable(false);
    JCheckBox customRent = new JCheckBox("Thỏa thuận giá riêng");
    customRent.setName("rental-custom-rent");
    customRent.setToolTipText("Chỉ bật khi giá thuê của người này khác giá mặc định của phòng.");
    JLabel rentHint = new JLabel();
    Runnable resetRent =
        () -> {
          Room selected = (Room) room.getSelectedItem();
          customRent.setSelected(false);
          customRent.setEnabled(selected != null && selected.defaultRent() != null);
          rent.setEditable(false);
          rent.setText(
              selected == null || selected.defaultRent() == null
                  ? ""
                  : selected.defaultRent().toPlainString());
          rentHint.setText(
              selected == null || selected.defaultRent() == null
                  ? "Phòng chưa có giá mặc định. Vào Sửa phòng để bổ sung trước khi cho thuê."
                  : "Đang dùng giá mặc định của phòng.");
        };
    resetRent.run();
    customRent.addActionListener(
        e -> {
          rent.setEditable(customRent.isSelected());
          if (!customRent.isSelected()) resetRent.run();
          else {
            rentHint.setText("Giá riêng chỉ áp dụng cho lượt thuê này.");
            rent.requestFocusInWindow();
            rent.selectAll();
          }
        });
    JPanel rentChoice = new JPanel(new BorderLayout(0, 6));
    rentChoice.setOpaque(false);
    rentChoice.add(rent, BorderLayout.NORTH);
    rentChoice.add(customRent, BorderLayout.CENTER);
    rentChoice.add(rentHint, BorderLayout.SOUTH);
    JSpinner due = new JSpinner(new SpinnerNumberModel(5, 1, 28, 1));
    JCheckBox noEnd = new JCheckBox("Chưa xác định ngày chuyển đi", true);
    noEnd.setName("rental-no-end");
    JPanel endCalendar = vn.nhatrohomu.shared.DatePicker.field(end, false);
    endCalendar.setVisible(false);
    noEnd.addActionListener(
        e -> {
          endCalendar.setVisible(!noEnd.isSelected());
          endCalendar.getParent().revalidate();
        });
    JPanel endChoice = Design.clear(new BorderLayout(0, 6));
    endChoice.add(noEnd, BorderLayout.NORTH);
    endChoice.add(endCalendar);
    JCheckBox takeDeposit = new JCheckBox("Có thỏa thuận tiền cọc");
    deposit.setEditable(false);
    deposit.setEnabled(false);
    deposit.setText("");
    takeDeposit.addActionListener(
        e -> {
          deposit.setEditable(takeDeposit.isSelected());
          deposit.setEnabled(takeDeposit.isSelected());
          if (!takeDeposit.isSelected()) deposit.setText("");
        });
    JPanel depositChoice = Design.clear(new BorderLayout(0, 6));
    depositChoice.add(takeDeposit, BorderLayout.NORTH);
    depositChoice.add(deposit);
    start.setToolTipText(
        "Ngày bắt đầu ở. Kiểm tra chỉ số nhận phòng; tiền tháng đầu được gợi ý theo ngày.");
    end.setToolTipText(
        "Ngày cuối dự kiến của thời hạn thuê, tính cả ngày này. Không phải hạn đóng tiền và không"
            + " tự xóa nợ.");
    rent.setToolTipText("Giá thuê một tháng trong hợp đồng, chưa cộng điện, nước và các phí.");
    deposit.setToolTipText(
        "Mức cọc thỏa thuận. Sau khi tạo hợp đồng, ghi Nhận cọc riêng khi thực nhận tiền.");
    due.setToolTipText(
        "Hạn thanh toán hóa đơn hàng tháng (1–28). Tháng đầu không sớm hơn ngày vào ở.");
    vn.nhatrohomu.shared.ChargeEditor fees =
        new vn.nhatrohomu.shared.ChargeEditor(repository.services());
    Readings initial = repository.previous((Room) room.getSelectedItem());
    JTextField handoverE = new JTextField(initial.electricity().toPlainString()),
        handoverW = new JTextField(initial.water().toPlainString());
    JCheckBox handoverConfirmed = new JCheckBox("Đã kiểm tra chỉ số lúc người này nhận phòng.");
    handoverConfirmed.setName("handover-confirmed");
    room.addActionListener(
        e -> {
          handoverConfirmed.setSelected(false);
          try {
            Readings r = repository.previous((Room) room.getSelectedItem());
            handoverE.setText(r.electricity().toPlainString());
            handoverW.setText(r.water().toPlainString());
          } catch (Exception ex) {
            Ui.error(owner, ex);
          }
          resetRent.run();
        });
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Phòng", room);
    Ui.field(
        fields,
        1,
        "Người đứng tên thuê *",
        withHint(
            tenantChoice,
            "Người đại diện liên hệ và thanh toán. Gia đình được thêm trong mục Người ở cùng của hồ"
                + " sơ."));
    Ui.field(
        fields, 2, "Ngày vào ở · YYYY-MM-DD", vn.nhatrohomu.shared.DatePicker.field(start, false));
    Ui.field(
        fields,
        3,
        "Thời gian ở",
        withHint(
            endChoice,
            "Không biết ngày đi thì giữ lựa chọn này. Khi khách chuyển đi, bấm Trả phòng để chốt"
                + " ngày thực tế."));
    Ui.field(fields, 4, "Tiền phòng / tháng (đ)", rentChoice);
    Ui.field(
        fields,
        5,
        "Tiền cọc thỏa thuận (đ)",
        withHint(
            depositChoice,
            "Không thu cọc thì để tắt. Đây là số tiền thỏa thuận; tiền thực nhận ghi riêng ở Nhận /"
                + " hoàn cọc."));
    Ui.field(
        fields,
        6,
        "Ngày hẹn đóng tiền hằng tháng",
        withHint(
            due,
            "Ví dụ ngày 5. Dùng để ghi hạn thanh toán và phân loại nợ quá hạn; không tự thu tiền"
                + " hay tính phạt."));
    Ui.field(
        fields,
        7,
        "Khoản thu thêm hằng tháng",
        withHint(
            fees,
            "Chọn phí từ danh mục. Quản lý khoản phí để thêm hoặc sửa giá mặc định; không thu phí"
                + " thì để trống."));
    Ui.field(
        fields,
        8,
        "Lưu ý",
        new JLabel("Ngày thuê không được trùng. Tiền kỳ đầu/cuối được gợi ý theo ngày."));
    Ui.field(fields, 10, "Điện lúc nhận phòng (kWh)", handoverE);
    Ui.field(fields, 11, "Nước lúc nhận phòng (m³)", handoverW);
    Ui.field(fields, 12, "Xác nhận bàn giao", handoverConfirmed);
    form(
        "Cho thuê phòng · thông tin thỏa thuận",
        fields,
        () -> {
          Room selected = (Room) room.getSelectedItem();
          Tenant person = (Tenant) tenant.getSelectedItem();
          if (!noEnd.isSelected() && end.getText().isBlank())
            throw new IllegalArgumentException(
                "Chọn ngày kết thúc, hoặc bật Chưa xác định ngày chuyển đi.");
          if (selected.defaultRent() == null)
            throw new IllegalArgumentException(
                "Phòng chưa có giá thuê mặc định. Vào Sửa phòng để bổ sung trước khi cho thuê.");
          if (!handoverConfirmed.isSelected())
            throw new IllegalArgumentException(
                "Kiểm tra và xác nhận chỉ số bàn giao trước khi lưu lần thuê.");
          if (person == null)
            throw new IllegalArgumentException(
                "Chọn hoặc thêm người thuê đại diện ngay trong màn hình này.");
          rentals.createContract(
              new Contract(
                  0,
                  selected.id(),
                  selected.name(),
                  person.id(),
                  person.name(),
                  start.getText().trim(),
                  noEnd.isSelected() ? "" : end.getText().trim(),
                  "",
                  customRent.isSelected()
                      ? RentalService.money(rent.getText(), "Giá thuê riêng")
                      : selected.defaultRent(),
                  takeDeposit.isSelected()
                      ? RentalService.money(deposit.getText(), "Tiền cọc")
                      : BigDecimal.ZERO,
                  (Integer) due.getValue(),
                  fees.charges()),
              new Readings(
                  BillingService.number(handoverE.getText(), "Điện bàn giao"),
                  BillingService.number(handoverW.getText(), "Nước bàn giao")));
        });
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
                + (contract.endDate().isBlank()
                    ? "Chưa xác định ngày chuyển đi"
                    : contract.endDate())
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
    String agreement = text.toString();
    JTextArea area = new JTextArea(22, 52);
    area.setName("rental-family-details");
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    Runnable update =
        () ->
            safe(
                () -> {
                  Tenant person = rentals.tenant(contract.tenantId());
                  area.setText(
                      agreement
                          + "\nHỒ SƠ HIỆN TẠI · NGƯỜI Ở CÙNG / GIA ĐÌNH\n"
                          + familyDetails(person));
                  area.setCaretPosition(0);
                });
    update.run();
    JDialog dialog = new JDialog(owner, "Chi tiết lượt thuê · " + contract.roomName(), true);
    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    root.add(new JScrollPane(area));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(
        Ui.button(
            "Thông tin & người ở cùng",
            () ->
                safe(
                    () -> {
                      openContractPerson(contract);
                      update.run();
                    })));
    buttons.add(Ui.button("Đóng", dialog::dispose));
    root.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private static JPanel withHint(JComponent field, String hint) {
    JPanel panel = Design.clear(new BorderLayout(0, 5));
    panel.add(field, BorderLayout.NORTH);
    JTextArea help = new JTextArea(hint, 2, 35);
    help.setEditable(false);
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setOpaque(false);
    help.setForeground(Design.MUTED);
    help.setFont(help.getFont().deriveFont(11f));
    panel.add(help, BorderLayout.SOUTH);
    return panel;
  }

  static String familySummary(Tenant person) {
    if (person == null) return "Không tìm thấy hồ sơ";
    if (person.residents().isEmpty()) return "Chưa khai người ở cùng";
    return person.residents().stream()
        .map(r -> r.name() + (r.relationship().isBlank() ? "" : " (" + r.relationship() + ")"))
        .collect(java.util.stream.Collectors.joining(", "));
  }

  static String familyDetails(Tenant person) {
    if (person == null) return "Không tìm thấy hồ sơ người thuê.\n";
    StringBuilder text =
        new StringBuilder(
            "Người đại diện: "
                + person.name()
                + "\nĐiện thoại: "
                + person.phone()
                + "\nZalo: "
                + person.zaloContact()
                + "\n");
    if (!person.active()) text.append("Hồ sơ đã lưu trữ; chỉ xem.\n");
    if (person.residents().isEmpty()) text.append("Chưa khai người ở cùng.\n");
    for (Resident resident : person.residents()) {
      text.append("• ").append(resident.name());
      if (!resident.relationship().isBlank()) text.append(" · ").append(resident.relationship());
      if (!resident.birthday().isBlank()) text.append(" · Ngày sinh: ").append(resident.birthday());
      if (!resident.phone().isBlank()) text.append(" · Điện thoại: ").append(resident.phone());
      text.append("\n");
    }
    return text.toString();
  }

  private void openContractPerson(Contract contract) throws Exception {
    Tenant person = rentals.tenant(contract.tenantId());
    if (person == null)
      throw new IllegalArgumentException("Không tìm thấy hồ sơ người thuê của lượt thuê này.");
    if (!person.active()) {
      textDialog("Hồ sơ đã lưu trữ", familyDetails(person));
      return;
    }
    tenantForm(person, true);
    refresh();
  }

  private void closeForm(Contract contract) {
    if (!contract.closedDate().isBlank()) {
      Ui.error(
          owner,
          new IllegalArgumentException("Lần thuê đã kết thúc. Hóa đơn, nợ và cọc vẫn ở hồ sơ cũ."));
      return;
    }
    safe(
        () ->
            new vn.nhatrohomu.billing.ui.CheckoutDialog(owner, repository, contract, this::refresh)
                .setVisible(true));
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
    Ui.field(fields, 2, "Ngày · YYYY-MM-DD", vn.nhatrohomu.shared.DatePicker.field(date, false));
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
