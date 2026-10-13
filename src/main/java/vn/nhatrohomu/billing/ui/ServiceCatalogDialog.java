package vn.nhatrohomu.billing.ui;

import java.awt.*;
import javax.swing.*;
import vn.nhatrohomu.billing.ServiceCatalog;
import vn.nhatrohomu.shared.Ui;

public final class ServiceCatalogDialog extends JDialog {
  public ServiceCatalogDialog(Window owner, ServiceCatalog catalog) throws Exception {
    super(owner, "Danh mục khoản phí", ModalityType.APPLICATION_MODAL);
    add(new ServiceCatalogPanel(catalog));
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottom.add(Ui.button("Đóng", this::dispose));
    add(bottom, BorderLayout.SOUTH);
    setSize(780, 480);
    setLocationRelativeTo(owner);
  }
}
