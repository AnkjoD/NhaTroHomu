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
    root.add(new JScrollPane(table));
    JPanel actions = Design.clear(new FlowLayout(FlowLayout.LEFT));
    actions.add(Ui.button("+ Khoản phí", () -> edit(null)));
    JButton edit =
        Ui.button(
            "Sửa",
            () -> {
              int row = table.getSelectedRow();
              if (row >= 0) edit(items.get(row));
            });
    JButton archive =
        Ui.button(
            "Lưu trữ",
            () -> {
              int row = table.getSelectedRow();
              if (row < 0) return;
              try {
                if (Ui.confirm(this, "Lưu trữ khoản phí này? Hợp đồng/hóa đơn cũ giữ nguyên.")) {
                  catalog.archive(items.get(row).id());
                  refresh();
                }
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    RowActions.install(table, List.of(edit, archive));
    actions.add(edit);
    actions.add(archive);
    heading.add(actions, BorderLayout.SOUTH);
    root.add(heading, BorderLayout.NORTH);
    add(root);
    refresh();
  }

  private void refresh() throws Exception {
    items = catalog.items();
    model.setRowCount(0);
    for (ServiceItem item : items)
      model.addRow(new Object[] {item.name(), Ui.money(item.amount())});
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
