package vn.nhatrohomu.room.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.reporting.InvoiceCsv;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

/** Filtered invoice snapshots; opening readings are not estimates for missing months. */
public final class RoomHistoryDialog extends JDialog {
  private final Repository repository;
  private final Room room;
  private final Consumer<Bill> details;
  private final JTextField from = new JTextField(YearMonth.now().minusMonths(11).toString(), 7),
      to = new JTextField(YearMonth.now().toString(), 7);
  private final JTable table = new JTable();
  private final JLabel status = new JLabel("Đang đọc lịch sử…");
  private List<Bill> loaded = List.of();
  private final JButton export = Ui.button("Xuất CSV khoảng đã xem", this::export);

  public RoomHistoryDialog(JFrame owner, Repository repository, Room room, Consumer<Bill> details) {
    super(owner, "Lịch sử điện nước · " + room.name(), true);
    this.repository = repository;
    this.room = room;
    this.details = details;
    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    JPanel head = new JPanel(new BorderLayout(0, 8));
    head.add(
        Design.label(room.name() + " · Lịch sử chỉ số và tiền từng kỳ", 22, Design.INK, true),
        BorderLayout.NORTH);
    JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
    filters.add(new JLabel("Từ kỳ"));
    filters.add(vn.nhatrohomu.shared.DatePicker.field(from, true));
    filters.add(new JLabel("Đến kỳ"));
    filters.add(vn.nhatrohomu.shared.DatePicker.field(to, true));
    filters.add(Ui.button("Xem", this::load));
    filters.add(
        Ui.button(
            "Tất cả kỳ",
            () -> {
              from.setText("1900-01");
              to.setText("9999-12");
              load();
            }));
    head.add(filters);
    root.add(head, BorderLayout.NORTH);
    table.setRowHeight(38);
    table.setAutoCreateRowSorter(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scroll = new JScrollPane(table);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(status, BorderLayout.NORTH);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton detail =
        Ui.button(
            "Hóa đơn / PDF / TXT",
            () -> {
              int row = table.getSelectedRow();
              if (row < 0) {
                JOptionPane.showMessageDialog(this, "Chọn một tháng trước.");
                return;
              }
              details.accept(loaded.get(table.convertRowIndexToModel(row)));
            });
    buttons.add(detail);
    RowActions.install(table, List.of(detail));
    root.add(new vn.nhatrohomu.shared.SelectionView(table, List.of(detail), 0, "room-history"));
    buttons.add(export);
    buttons.add(Ui.button("Đóng", this::dispose));
    bottom.add(buttons);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    setSize(1100, 620);
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    setSize(Math.min(getWidth(), screen.width - 80), Math.min(getHeight(), screen.height - 80));
    setLocationRelativeTo(owner);
    load();
  }

  private void load() {
    try {
      String start = from.getText().trim(), end = to.getText().trim();
      DataRules.period(start);
      DataRules.period(end);
      if (start.compareTo(end) > 0)
        throw new IllegalArgumentException("Kỳ bắt đầu phải trước kỳ kết thúc.");
      export.setEnabled(false);
      Ui.background(
          this,
          () -> repository.roomBills(room.id(), start, end),
          rows -> {
            loaded = List.copyOf(rows);
            String[] headings = {
              "Kỳ",
              "Điện cũ",
              "Điện mới",
              "Điện dùng kWh",
              "Tiền điện",
              "Nước cũ",
              "Nước mới",
              "Nước dùng m³",
              "Tiền nước",
              "Điện + nước",
              "Tiền phòng",
              "Phí",
              "Tổng hóa đơn"
            };
            Object[][] values = new Object[rows.size()][];
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < rows.size(); i++) {
              Bill b = rows.get(i);
              sum = sum.add(b.utilitiesCost());
              boolean meter = b.waterMode().equals("METER");
              values[i] =
                  new Object[] {
                    b.month(),
                    b.electricityOld(),
                    b.electricityNew(),
                    b.electricityUsed(),
                    b.electricityCost(),
                    meter ? b.waterOld() : "Khoán",
                    meter ? b.waterNew() : "Khoán",
                    b.waterUsed(),
                    b.waterCost(),
                    b.utilitiesCost(),
                    b.rent(),
                    b.feeTotal(),
                    b.total()
                  };
            }
            table.setModel(
                new DefaultTableModel(values, headings) {
                  public boolean isCellEditable(int r, int c) {
                    return false;
                  }

                  public Class<?> getColumnClass(int c) {
                    return c == 0 || c == 5 || c == 6 ? Object.class : BigDecimal.class;
                  }
                });
            for (int i = 0; i < headings.length; i++)
              table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 90 : 125);
            table.setDefaultRenderer(
                BigDecimal.class,
                new javax.swing.table.DefaultTableCellRenderer() {
                  public java.awt.Component getTableCellRendererComponent(
                      JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                    super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                    int model = t.convertColumnIndexToModel(col);
                    setText(
                        value == null
                            ? ""
                            : (model == 4 || model >= 8
                                ? Ui.money((BigDecimal) value)
                                : Ui.n((BigDecimal) value)));
                    return this;
                  }
                });
            status.setText(
                start
                    + " → "
                    + end
                    + " · "
                    + rows.size()
                    + " kỳ · Điện nước: "
                    + Ui.money(sum)
                    + " · Kéo ngang để xem đủ cột. Kỳ chưa lập hóa đơn không có dữ liệu.");
            export.setEnabled(!rows.isEmpty());
          });
    } catch (Exception e) {
      Ui.error(this, e);
    }
  }

  private void export() {
    JFileChooser picker = new JFileChooser();
    picker.setSelectedFile(new java.io.File("dien-nuoc-phong-" + room.id() + ".csv"));
    if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    java.nio.file.Path path = picker.getSelectedFile().toPath();
    if (java.nio.file.Files.exists(path) && !Ui.confirm(this, "File đã tồn tại. Ghi đè?")) return;
    List<Bill> snapshot = loaded;
    Ui.background(
        this,
        () -> {
          InvoiceCsv.write(path, snapshot, repository);
          return path;
        },
        result -> JOptionPane.showMessageDialog(this, "Đã xuất: " + result));
  }
}
