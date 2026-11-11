package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.ServiceCatalog;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.ServiceItem;

public final class ServiceCatalogPanel extends JPanel {
  private final ServiceCatalog catalog;
  private final DefaultTableModel model =
      new DefaultTableModel(new String[] {"Tên khoản phí", "Giá mặc định/tháng"}, 0) {
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JTable table = new JTable(model);
  private List<ServiceItem> items = List.of();
  private final JCheckBox archived = new JCheckBox("Xem phí đã lưu trữ");

  public ServiceCatalogPanel(ServiceCatalog catalog) throws Exception {
    super(new BorderLayout());
    this.catalog = catalog;
    JPanel root = Design.clear(new BorderLayout(12, 12));
    root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    JPanel heading = Design.clear(new BorderLayout(0, 12));
    heading.add(
        new JLabel(
            "Chọn khoản phí trong danh mục khi cho thuê. Giá đã chốt trước đó không tự đổi."),
        BorderLayout.NORTH);
    table.setRowHeight(38);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    var cells = new javax.swing.table.DefaultTableCellRenderer();
    cells.setHorizontalAlignment(SwingConstants.CENTER);
    table.setDefaultRenderer(Object.class, cells);
    var headers = table.getTableHeader().getDefaultRenderer();
    table
        .getTableHeader()
        .setDefaultRenderer(
            (grid, value, selected, focused, row, column) -> {
              Component component =
                  headers.getTableCellRendererComponent(
                      grid, value, selected, focused, row, column);
              if (component instanceof JLabel label)
                label.setHorizontalAlignment(SwingConstants.CENTER);
              return component;
            });
    root.add(new JScrollPane(table));
    JPanel actions = Design.clear(new FlowLayout(FlowLayout.LEFT));
    actions.add(Ui.button("+ Khoản phí", () -> edit(null)));
    JButton edit =
        Ui.button(
            "Sửa",
            () -> {
              int row = table.getSelectedRow();
              if (row >= 0 && !archived.isSelected()) edit(items.get(row));
            });
    JButton archive = Ui.button("Lưu trữ phí", this::archiveSelected);
    JButton delete =
        Ui.button(
            "Xóa phí",
            () ->
                applySelected(
                    "Xóa khoản phí tạo nhầm? Chỉ xóa được nếu chưa gắn vào lượt thuê hoặc hóa đơn.",
                    catalog::deleteUnused));
    RowActions.install(table, List.of(edit, archive, delete));
    actions.add(edit);
    actions.add(archive);
    actions.add(delete);
    actions.add(archived);
    archived.addActionListener(
        e -> {
          try {
            archive.setText(archived.isSelected() ? "Ngừng tính phí" : "Lưu trữ phí");
            refresh();
          } catch (Exception ex) {
            Ui.error(this, ex);
          }
        });
    heading.add(actions, BorderLayout.SOUTH);
    root.add(heading, BorderLayout.NORTH);
    add(root);
    refresh();
  }

  private void refresh() throws Exception {
    table.clearSelection();
    items = archived.isSelected() ? catalog.archivedItems() : catalog.items();
    model.setRowCount(0);
    for (ServiceItem item : items)
      model.addRow(new Object[] {item.name(), Ui.money(item.amount())});
  }

  private void archiveSelected() {
    int row = table.getSelectedRow();
    if (row < 0) return;
    ServiceItem item = items.get(table.convertRowIndexToModel(row));
    boolean alreadyArchived = archived.isSelected();
    String hide = "Chỉ lưu trữ", stop = "Lưu trữ và ngừng tính", cancel = "Hủy";
    Object[] options = {hide, stop, cancel};
    try {
      if (alreadyArchived) {
        if (!Ui.confirm(
            this,
            item.name()
                + " đã lưu trữ. Ngừng tính khoản này trên tất cả lượt thuê đang mở? Hóa đơn đã lập"
                + " giữ nguyên."))
          return;
      } else {
        int choice =
            JOptionPane.showOptionDialog(
                this,
                item.name()
                    + "\nChỉ lưu trữ: ẩn khỏi danh mục, phòng đã gắn vẫn tính.\n"
                    + "Lưu trữ và ngừng tính: gỡ khỏi tất cả lượt thuê đang mở.\n"
                    + "Hóa đơn đã lập và khoản thu giữ nguyên.",
                "Lưu trữ phí",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                cancel);
        if (choice == 0) {
          catalog.archive(item.id());
          refresh();
          return;
        }
        if (choice != 1) return;
      }
      int count = catalog.stopBilling(item.id());
      refresh();
      JOptionPane.showMessageDialog(this, "Đã ngừng tính phí trên " + count + " lượt thuê.");
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  @FunctionalInterface
  private interface FeeAction {
    void run(long id) throws Exception;
  }

  private void applySelected(String message, FeeAction action) {
    int row = table.getSelectedRow();
    if (row < 0 || !Ui.confirm(this, items.get(row).name() + "\n" + message)) return;
    try {
      action.run(items.get(row).id());
      refresh();
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  private void edit(ServiceItem value) {
    JTextField name = new JTextField(value == null ? "" : value.name(), 24),
        amount = new JTextField(value == null ? "" : value.amount().toPlainString());
    JPanel fields = Ui.form();
    Ui.field(fields, 0, "Tên khoản phí", name);
    Ui.field(fields, 1, "Giá mặc định/tháng (đ)", amount);
    if (JOptionPane.showConfirmDialog(
            this,
            fields,
            value == null ? "Thêm khoản phí" : "Sửa khoản phí",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    try {
      catalog.save(
          new ServiceItem(
              value == null ? 0 : value.id(),
              name.getText(),
              RentalService.money(amount.getText(), "Giá phí")));
      refresh();
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }
}
