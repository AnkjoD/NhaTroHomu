package vn.nhatrohomu.reporting;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.ui.PaymentDialog;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.persistence.sqlite.SqliteRepository;
import vn.nhatrohomu.shared.CsvField;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Useful tables and actions accompany the dashboard charts. */
public final class ReportPanel extends JPanel {
  private final ReportStore reports;
  private final Repository repository;
  private final JTextField month = new JTextField(YearMonth.now().toString(), 7);
  private final JComboBox<RoomChoice> room = new JComboBox<>();
  private final JTabbedPane tabs = new JTabbedPane();
  private final JLabel summary = new JLabel("Đang đọc dữ liệu…");
  private long generation;
  private ReportStore.Snapshot snapshot;
  private YearMonth loadedMonth;
  private String loadedRoom;

  record RoomChoice(Long id, String name) {
    public String toString() {
      return name;
    }
  }

  public ReportPanel(Repository repository) {
    super(new BorderLayout(12, 18));
    this.repository = repository;
    if (!(repository instanceof SqliteRepository sqlite))
      throw new IllegalArgumentException("Báo cáo cần sổ SQLite.");
    reports = new ReportStore(sqlite.database);
    setOpaque(false);
    JPanel heading = Design.clear(new BorderLayout(8, 8));
    JPanel words = Ui.column();
    words.setOpaque(false);
    words.add(Design.label("Báo cáo & đối soát", 26, Design.INK, true));
    words.add(summary);
    heading.add(words);
    JPanel filters = Design.clear(new FlowLayout(FlowLayout.RIGHT));
    room.addItem(new RoomChoice(null, "Mọi phòng"));
    try {
      for (Room r : repository.rooms()) room.addItem(new RoomChoice(r.id(), r.name()));
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
    filters.add(new JLabel("Tháng thu / kỳ kiểm tra"));
    filters.add(month);
    filters.add(room);
    filters.add(Ui.refreshButton(this::load));
    filters.add(Ui.button("Xuất CSV", this::export));
    heading.add(filters, BorderLayout.SOUTH);
    add(heading, BorderLayout.NORTH);
    add(tabs);
    room.addActionListener(e -> load());
    month.addActionListener(e -> load());
    load();
  }

  private JTable table(String[] columns, Object[][] rows) {
    JTable t =
        new JTable(
            new DefaultTableModel(rows, columns) {
              public boolean isCellEditable(int row, int col) {
                return false;
              }

              public Class<?> getColumnClass(int col) {
                for (Object[] row : rows) if (row[col] != null) return row[col].getClass();
                return Object.class;
              }
            });
    t.setDefaultRenderer(
        BigDecimal.class,
        new javax.swing.table.DefaultTableCellRenderer() {
          protected void setValue(Object value) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(value == null ? "" : Ui.money((BigDecimal) value));
          }
        });
    t.setRowHeight(34);
    t.setAutoCreateRowSorter(true);
    t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    t.getTableHeader().setReorderingAllowed(false);
    return t;
  }

  private JPanel page(String explanation, JTable table, JComponent action) {
    JPanel p = Design.clear(new BorderLayout(8, 12));
    JTextArea text = new JTextArea(explanation);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setOpaque(false);
    text.setPreferredSize(new Dimension(600, 76));
    p.add(text, BorderLayout.NORTH);
    p.add(new JScrollPane(table));
    if (action != null) p.add(action, BorderLayout.SOUTH);
    return p;
  }

  private void load() {
    final YearMonth selected;
    final Long roomId;
    try {
      selected = DataRules.period(month.getText().trim());
      roomId = ((RoomChoice) room.getSelectedItem()).id();
    } catch (Exception ex) {
      Ui.error(this, ex);
      return;
    }
    long request = ++generation;
    summary.setText("Đang đối soát…");
    new SwingWorker<ReportStore.Snapshot, Void>() {
      protected ReportStore.Snapshot doInBackground() throws Exception {
        return reports.load(selected, roomId);
      }

      protected void done() {
        if (request != generation) return;
        Window owner = SwingUtilities.getWindowAncestor(ReportPanel.this);
        if (owner != null && !owner.isDisplayable()) return;
        try {
          snapshot = get();
          loadedMonth = selected;
          loadedRoom = roomId == null ? "Mọi phòng" : "Mã phòng " + roomId;
          render(selected);
        } catch (Exception ex) {
          summary.setText("Không đọc được báo cáo.");
          Ui.error(ReportPanel.this, ex);
        }
      }
    }.execute();
  }

  private void render(YearMonth selected) {
    int selectedTab = tabs.getSelectedIndex();
    tabs.removeAll();
    BigDecimal debt =
        snapshot.debts().stream()
            .map(ReportStore.Debt::remaining)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    summary.setText(
        "Nợ hiện tại: "
            + Ui.money(debt)
            + " · Thu ghi nhận "
            + selected
            + ": "
            + Ui.money(snapshot.collectedInRange())
            + " · Chi ghi nhận: "
            + Ui.money(snapshot.expensesInRange())
            + " · Kỳ thuê chưa lập: "
            + snapshot.missing().size());
    JTable debts =
        table(
            new String[] {
              "Phòng", "Người thuê", "Kỳ", "Hạn thu", "Tổng", "Đã thu", "Còn nợ", "Trễ ngày"
            },
            snapshot.debts().stream()
                .map(
                    d ->
                        new Object[] {
                          d.room(),
                          d.tenant(),
                          d.period(),
                          d.due().isBlank() ? "Chưa biết" : d.due(),
                          d.total(),
                          d.received(),
                          d.remaining(),
                          d.daysLate() < 0 ? null : d.daysLate()
                        })
                .toArray(Object[][]::new));
    JButton collect =
        Ui.button(
            "Mở hóa đơn / thu tiền",
            () -> {
              try {
                int row = debts.getSelectedRow();
                if (row < 0) throw new IllegalArgumentException("Chọn khoản nợ.");
                long id = snapshot.debts().get(debts.convertRowIndexToModel(row)).id();
                Bill b =
                    repository.outstandingBills().stream()
                        .filter(x -> x.id() == id)
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Khoản này đã đổi. Nhấn Xem để tải lại."));
                new PaymentDialog((JFrame) SwingUtilities.getWindowAncestor(this), repository, b)
                    .setVisible(true);
                load();
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    vn.nhatrohomu.shared.RowActions.install(debts, java.util.List.of(collect));
    String[] buckets = {"Chưa quá hạn", "1–30 ngày", "31–60 ngày", "61–90 ngày", ">90 ngày"};
    StringBuilder age =
        new StringBuilder(
            "Công nợ hiện tại của mọi kỳ; không phải số dư tại tháng quá khứ. Tuổi nợ: ");
    for (int i = 0; i < buckets.length; i++)
      age.append(buckets[i]).append(" ").append(Ui.money(snapshot.aging().get(i))).append("; ");
    age.append("Khoản chưa biết hạn vẫn nằm trong bảng, không gán nhóm tuổi nợ.");
    tabs.addTab("Công nợ & tuổi nợ", page(age.toString(), debts, collect));
    tabs.addTab(
        "Thu theo ngày",
        page(
            "Theo ngày thu thực tế đã nhập và ngày ghi đảo. Cọc tách riêng. "
                + snapshot.unknownDates()
                + " lần thu cũ không biết ngày, không gán vào tháng này.",
            table(
                new String[] {"Ngày", "Phòng", "Kỳ hóa đơn", "Loại", "Số tiền", "Ghi chú"},
                snapshot.movements().stream()
                    .map(
                        m ->
                            new Object[] {
                              m.date(), m.room(), m.period(), m.type(), m.amount(), m.note()
                            })
                    .toArray(Object[][]::new)),
            null));
    JTable expenses =
        table(
            new String[] {
              "Mã",
              "Ngày chi",
              "Phòng",
              "Loại",
              "Người nhận",
              "Tiền chi",
              "Phương thức",
              "Đã đảo",
              "Ngày đảo",
              "Lý do"
            },
            snapshot.expenses().stream()
                .map(
                    e ->
                        new Object[] {
                          e.id(),
                          e.date(),
                          e.room(),
                          ExpenseStore.CATEGORIES.get(e.category()),
                          e.payee(),
                          e.amount(),
                          e.method(),
                          e.reversed() ? "Có" : "Không",
                          e.reversalDate() == null ? "" : e.reversalDate(),
                          e.reason() == null ? e.note() : e.reason()
                        })
                .toArray(Object[][]::new));
    JPanel expenseActions = Design.clear(new FlowLayout(FlowLayout.RIGHT));
    expenseActions.add(
        Ui.button(
            "Ghi khoản đã chi",
            () -> {
              try {
                new ExpenseDialog(
                        (JFrame) SwingUtilities.getWindowAncestor(this), repository, this::load)
                    .setVisible(true);
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            }));
    JButton reverseExpense =
        Ui.button(
            "Đảo khoản chi nhập nhầm",
            () -> {
              try {
                int row = expenses.getSelectedRow();
                if (row < 0) throw new IllegalArgumentException("Chọn khoản chi.");
                ExpenseStore.Entry e =
                    snapshot.expenses().get(expenses.convertRowIndexToModel(row));
                if (e.reversed()) throw new IllegalArgumentException("Khoản này đã đảo.");
                String reason =
                    JOptionPane.showInputDialog(
                        this, "Lý do nhập nhầm khoản chi (không phải hoàn tiền thật):");
                if (reason == null) return;
                if (Ui.confirm(this, "Ghi đảo khoản chi #" + e.id() + "? Bản gốc được giữ.")) {
                  new ExpenseStore(((SqliteRepository) repository).database)
                      .reverse(e.id(), reason);
                  load();
                }
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    expenseActions.add(reverseExpense);
    vn.nhatrohomu.shared.RowActions.install(expenses, java.util.List.of(reverseExpense), false);
    tabs.addTab(
        "Khoản đã chi",
        page(
            "Chi theo ngày đã trả, trừ khoản nhập nhầm được đảo theo ngày đảo. Thu trừ chi trong"
                + " tháng: "
                + Ui.money(snapshot.collectedInRange().subtract(snapshot.expensesInRange()))
                + ". Chưa gồm nhận/hoàn cọc; đây không phải lợi nhuận kế toán. Lọc phòng không gồm"
                + " khoản chi toàn nhà.",
            expenses,
            expenseActions));
    tabs.addTab(
        "Kỳ thuê chưa lập",
        page(
            "Kiểm tra các tháng thuê từ ngày vào ở đến kỳ được chọn. Phòng trống không bị nhắc"
                + " thiếu tiền thuê. Bản hiện tại chốt tuần tự; dữ liệu cũ có lỗ hổng kỳ cần đối"
                + " soát trước khi tiếp tục.",
            table(
                new String[] {"Hợp đồng", "Phòng", "Người thuê", "Kỳ thiếu", "Phòng lưu trữ"},
                snapshot.missing().stream()
                    .map(
                        m ->
                            new Object[] {
                              m.contractId(),
                              m.room(),
                              m.tenant(),
                              m.period(),
                              m.archived() ? "Có" : "Không"
                            })
                    .toArray(Object[][]::new)),
            null));
    tabs.addTab(
        "Hóa đơn đã hủy",
        page(
            "Giữ chứng từ và ảnh gốc, không tính vào nợ hoặc kỳ đã lập. Theo phòng đã lọc, mọi kỳ.",
            table(
                new String[] {"Mã", "Phòng", "Kỳ", "Tổng gốc", "Thời điểm hủy UTC", "Lý do"},
                snapshot.voided().stream()
                    .map(
                        v ->
                            new Object[] {
                              v.id(), v.room(), v.period(), v.total(), v.date(), v.reason()
                            })
                    .toArray(Object[][]::new)),
            null));
    tabs.addTab(
        "Nhật ký",
        page(
            "500 thay đổi gần nhất của toàn sổ; độc lập bộ lọc phòng/tháng. Nhật ký local không bảo"
                + " vệ khỏi người có quyền sửa file máy tính.",
            table(
                new String[] {
                  "Mã", "Thời điểm UTC", "Thao tác", "Đối tượng", "Mã đối tượng", "Chi tiết"
                },
                snapshot.events().stream()
                    .map(
                        e ->
                            new Object[] {
                              e.id(),
                              e.timestamp(),
                              e.action(),
                              e.entity(),
                              e.entityId(),
                              e.detail()
                            })
                    .toArray(Object[][]::new)),
            null));
    if (selectedTab >= 0 && selectedTab < tabs.getTabCount()) tabs.setSelectedIndex(selectedTab);
  }

  private static String csv(String text) {
    return CsvField.escape(text);
  }

  private void export() {
    if (snapshot == null || tabs.getSelectedIndex() < 0) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("nhatrohomu-bao-cao.csv"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path target = chooser.getSelectedFile().toPath();
    if (Files.exists(target) && !Ui.confirm(this, "Thay file CSV đã có?")) return;
    try {
      JPanel panel = (JPanel) tabs.getSelectedComponent();
      JTable table =
          (JTable)
              ((JScrollPane)
                      ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER))
                  .getViewport()
                  .getView();
      StringBuilder out = new StringBuilder("\uFEFF");
      out.append(
              csv(
                  "Báo cáo: "
                      + tabs.getTitleAt(tabs.getSelectedIndex())
                      + "; tháng thu/kỳ kiểm tra "
                      + loadedMonth
                      + "; phòng "
                      + loadedRoom
                      + "; nợ hiện tại; nhật ký toàn sổ"))
          .append("\r\n");
      for (int column = 0; column < table.getColumnCount(); column++) {
        if (column > 0) out.append(',');
        out.append(csv(table.getColumnName(column)));
      }
      out.append("\r\n");
      for (int row = 0; row < table.getRowCount(); row++) {
        for (int column = 0; column < table.getColumnCount(); column++) {
          if (column > 0) out.append(',');
          out.append(
              csv(
                  table.getValueAt(row, column) == null
                      ? ""
                      : String.valueOf(table.getValueAt(row, column))));
        }
        out.append("\r\n");
      }
      Files.writeString(target, out, StandardCharsets.UTF_8);
      JOptionPane.showMessageDialog(this, "Đã xuất bảng đang xem.");
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }
}
