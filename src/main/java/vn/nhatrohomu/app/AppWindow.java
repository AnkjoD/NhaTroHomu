package vn.nhatrohomu.app;

import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.billing.ui.PaymentDialog;
import vn.nhatrohomu.billing.ui.ReadingDialog;
import vn.nhatrohomu.integration.ImagePanel;
import vn.nhatrohomu.integration.InvoicePdf;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.ui.RentalPanel;
import vn.nhatrohomu.reporting.DashboardPanel;
import vn.nhatrohomu.reporting.ReportPanel;
import vn.nhatrohomu.room.ui.RoomDialog;
import vn.nhatrohomu.room.ui.WaterPolicyDialog;
import vn.nhatrohomu.shared.CsvField;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Điều phối giao diện; công thức và SQL nằm ngoài lớp này. */
public final class AppWindow extends JFrame {
  private final Repository repository;
  private final Path dataDirectory;
  private final JPanel body = new JPanel(new BorderLayout());
  private int historyPage;
  private String currentPage = "Tổng quan";

  private final java.util.Map<String, JButton> navigation = new java.util.LinkedHashMap<>();

  public AppWindow(Repository repository, Path dataDirectory) {
    super("NhaTroHomu · Quản lý phòng trọ");
    this.repository = repository;
    this.dataDirectory = dataDirectory;
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    setSize(Math.min(1240, screen.width - 80), Math.min(820, screen.height - 80));
    setMinimumSize(new Dimension(980, 640));
    setLocationRelativeTo(null);
    buildShell();
  }

  private void buildShell() {
    navigation.clear();
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(Ui.BACKGROUND);
    JPanel sidebar = new JPanel(new BorderLayout());
    sidebar.setPreferredSize(new Dimension(210, 0));
    sidebar.setBackground(Design.NAV);
    sidebar.setBorder(BorderFactory.createEmptyBorder(32, 20, 24, 20));
    JPanel top = Design.clear(new BorderLayout(0, 30));
    JPanel branding = Ui.column();
    branding.setOpaque(false);
    JLabel brand = Design.label("NhaTroHomu", 19, Design.NAV_TEXT, true);
    brand.setIcon(Design.icon("rooms", Ui.GREEN));
    brand.setIconTextGap(14);
    branding.add(brand);
    branding.add(Box.createVerticalStrut(10));
    branding.add(Design.label("QUẢN LÝ PHÒNG TRỌ", 10, Design.MUTED, false));
    top.add(branding, BorderLayout.NORTH);
    JPanel menu = new JPanel(new GridLayout(0, 1, 0, 10));
    menu.setOpaque(false);
    menu.add(nav("dashboard", "Tổng quan", this::showDashboard));
    menu.add(nav("rooms", "Phòng trọ", this::showRooms));
    menu.add(nav("tenant", "Người thuê", this::showTenants));
    menu.add(nav("contract", "Hợp đồng", this::showContracts));
    menu.add(nav("bill", "Hóa đơn", this::showHistory));
    menu.add(nav("debt", "Công nợ", this::showDebts));
    menu.add(nav("report", "Báo cáo", this::showReports));
    menu.add(nav("settings", "Cài đặt", this::showSettings));
    top.add(menu, BorderLayout.CENTER);
    sidebar.add(top, BorderLayout.NORTH);
    JPanel bottom = Ui.column();
    bottom.setOpaque(false);
    bottom.add(Design.label("●  Dữ liệu trên thiết bị", 11, Ui.GREEN, false));
    bottom.add(Box.createVerticalStrut(8));
    bottom.add(Design.label("Gọn nhẹ. Rõ từng con số.", 11, Design.MUTED, false));
    bottom.add(Box.createVerticalStrut(24));
    bottom.add(Design.label("NHATROHOMU  /  0.7.2", 10, Design.MUTED, false));
    sidebar.add(bottom, BorderLayout.SOUTH);
    body.setOpaque(false);
    body.setBorder(BorderFactory.createEmptyBorder(32, 32, 24, 32));
    root.add(sidebar, BorderLayout.WEST);
    JPanel workspace = new JPanel(new BorderLayout());
    workspace.setBackground(Ui.BACKGROUND);
    JPanel bar = Design.clear(new BorderLayout());
    bar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Design.LINE),
            BorderFactory.createEmptyBorder(16, 28, 16, 28)));
    bar.add(
        Design.label("KHÔNG GIAN QUẢN LÝ  /  PHÒNG TRỌ", 10, Design.MUTED, false),
        BorderLayout.WEST);
    JPanel themes = Design.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    for (Design.Mode mode : Design.Mode.values()) {
      JButton toggle =
          Ui.button(
              mode == Design.Mode.HOMURA ? "Homura · Tối" : "Madoka · Sáng",
              () -> {
                try {
                  Design.choose(mode, dataDirectory);
                  buildShell();
                } catch (Exception ex) {
                  Ui.error(this, ex);
                }
              });
      toggle.setToolTipText(
          mode == Design.Mode.HOMURA
              ? "Homura — tím đêm và sắc bạc"
              : "Madoka — hồng dịu và nền sáng");
      toggle.setBackground(mode == Design.mode ? Design.MINT : Design.SURFACE);
      toggle.setForeground(mode == Design.mode ? Ui.GREEN : Design.MUTED);
      themes.add(toggle);
    }
    bar.add(themes, BorderLayout.EAST);
    workspace.add(bar, BorderLayout.NORTH);
    workspace.add(body, BorderLayout.CENTER);
    root.add(workspace, BorderLayout.CENTER);
    setContentPane(root);
    javax.swing.SwingUtilities.updateComponentTreeUI(this);
    if (currentPage.equals("Hóa đơn")) showHistory();
    else if (currentPage.equals("Cài đặt")) showSettings();
    else if (currentPage.equals("Người thuê")) showTenants();
    else if (currentPage.equals("Hợp đồng")) showContracts();
    else if (currentPage.equals("Công nợ")) showDebts();
    else if (currentPage.equals("Báo cáo")) showReports();
    else if (currentPage.equals("Tổng quan")) showDashboard();
    else showRooms();
    revalidate();
    repaint();
  }

  private JButton nav(String icon, String title, Runnable action) {
    JButton b = Ui.button(title, action);
    b.setIcon(Design.icon(icon, Design.MUTED));
    b.setIconTextGap(14);
    b.setHorizontalAlignment(SwingConstants.LEFT);
    b.setPreferredSize(new Dimension(190, 48));
    b.putClientProperty("JButton.buttonType", "borderless");
    navigation.put(title, b);
    return b;
  }

  private void selectNav(String title) {
    currentPage = title;
    navigation.forEach(
        (name, b) -> {
          boolean active = name.equals(title);
          b.setBackground(active ? Design.MINT : Design.NAV);
          b.setForeground(active ? Ui.GREEN : Design.MUTED);
        });
  }

  private JPanel header(String title, String subtitle, JComponent actions) {
    JPanel panel = new JPanel(new BorderLayout(8, 16));
    panel.setOpaque(false);
    JPanel words = Ui.column();
    words.setOpaque(false);
    JLabel heading = new JLabel(title);
    heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26));
    heading.setForeground(Design.INK);
    words.add(heading);
    words.add(Box.createVerticalStrut(10));
    words.add(Design.label(subtitle, 12, Design.MUTED, false));
    panel.add(words, BorderLayout.CENTER);
    if (actions != null) panel.add(actions, BorderLayout.SOUTH);
    return panel;
  }

  private void show(JComponent top, JComponent center, JComponent bottom) {
    body.removeAll();
    body.add(top, BorderLayout.NORTH);
    body.add(center, BorderLayout.CENTER);
    if (bottom != null) body.add(bottom, BorderLayout.SOUTH);
    body.revalidate();
    body.repaint();
  }

  private JTable table(String[] columns, Object[][] rows) {
    DefaultTableModel model =
        new DefaultTableModel(rows, columns) {
          public boolean isCellEditable(int row, int col) {
            return false;
          }
        };
    JTable table = new JTable(model);
    table.setRowHeight(52);
    table.setIntercellSpacing(new Dimension(0, 1));
    table.setShowVerticalLines(false);
    table.getTableHeader().setPreferredSize(new Dimension(0, 44));
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    table.getTableHeader().setReorderingAllowed(false);
    return table;
  }

  private int selected(JTable table) {
    int i = table.getSelectedRow();
    if (i < 0) throw new IllegalArgumentException("Chọn một dòng trước.");
    return table.convertRowIndexToModel(i);
  }

  private void showReports() {
    selectNav("Báo cáo");
    try {
      show(new JPanel(), new ReportPanel(repository), null);
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  private void showRooms() {
    selectNav("Phòng trọ");
    try {
      List<Room> rooms = repository.rooms();
      List<Contract> contracts = repository.rentals().contracts();
      java.util.Map<Long, Contract> occupied = new java.util.HashMap<>();
      for (Contract contract : contracts)
        if (contract.covers(java.time.LocalDate.now())) occupied.put(contract.roomId(), contract);
      JPanel top = Design.clear(new BorderLayout(0, 12));
      JPanel heading =
          header("Phòng trọ", "Trạng thái thuê, chỉ số và hóa đơn của từng phòng.", null);
      JPanel addArea = Design.clear(new GridBagLayout());
      addArea.add(Design.primary(Ui.button("+  Thêm phòng", () -> roomDialog(null))));
      heading.add(addArea, BorderLayout.EAST);
      top.add(heading, BorderLayout.NORTH);
      top.add(
          Design.label(
              rooms.size()
                  + " phòng · "
                  + occupied.size()
                  + " đang thuê · "
                  + (rooms.size() - occupied.size())
                  + " chưa có hợp đồng hiện tại",
              13,
              Design.MUTED,
              false),
          BorderLayout.CENTER);
      JPanel listHead = Design.clear(new BorderLayout(12, 0));
      listHead.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
      listHead.add(Design.label("Danh sách phòng", 18, Design.INK, true), BorderLayout.WEST);
      JTextField search = new JTextField(18);
      search.putClientProperty("JTextField.placeholderText", "Tìm phòng hoặc người thuê…");
      search.setPreferredSize(new Dimension(240, 38));
      listHead.add(search, BorderLayout.EAST);
      top.add(listHead, BorderLayout.SOUTH);
      JPanel grid = Design.clear(new GridLayout(0, 2, 18, 18));
      Runnable fill =
          () -> {
            grid.removeAll();
            String query = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
            for (Room r : rooms)
              if ((r.name()
                      + " "
                      + (occupied.containsKey(r.id())
                          ? occupied.get(r.id()).tenantName()
                          : r.tenant()))
                  .toLowerCase(java.util.Locale.ROOT)
                  .contains(query)) {
                try {
                  grid.add(roomCard(r, occupied.get(r.id())));
                } catch (Exception ex) {
                  Ui.error(this, ex);
                }
              }
            grid.revalidate();
            grid.repaint();
          };
      fill.run();
      search
          .getDocument()
          .addDocumentListener(
              new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                  fill.run();
                }

                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                  fill.run();
                }

                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                  fill.run();
                }
              });
      JPanel list = Design.clear(new BorderLayout());
      list.setBorder(BorderFactory.createEmptyBorder(18, 0, 10, 0));
      list.add(grid, BorderLayout.NORTH);
      if (rooms.isEmpty()) {
        JPanel empty = Design.card(Design.SURFACE);
        empty.setLayout(new GridBagLayout());
        empty.setPreferredSize(new Dimension(600, 280));
        JPanel message = Ui.column();
        message.setOpaque(false);
        JLabel icon = new JLabel(Design.icon("rooms", Ui.GREEN));
        icon.setAlignmentX(.5f);
        message.add(icon);
        message.add(Box.createVerticalStrut(20));
        JLabel title = Design.label("Bắt đầu với phòng đầu tiên", 23, Design.INK, true);
        title.setAlignmentX(.5f);
        message.add(title);
        message.add(Box.createVerticalStrut(10));
        JLabel desc =
            Design.label(
                "Thêm phòng và chỉ số ban đầu. Phần còn lại, NhaTroHomu giúp bạn.",
                12,
                Design.MUTED,
                false);
        desc.setAlignmentX(.5f);
        message.add(desc);
        message.add(Box.createVerticalStrut(22));
        JButton add = Design.primary(Ui.button("+  Tạo phòng đầu tiên", () -> roomDialog(null)));
        add.setAlignmentX(.5f);
        message.add(add);
        empty.add(message);
        list.add(empty, BorderLayout.NORTH);
      }
      JScrollPane scroll = new JScrollPane(list);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.getViewport().setBackground(Ui.BACKGROUND);
      scroll.getVerticalScrollBar().setUnitIncrement(20);
      scroll
          .getViewport()
          .addComponentListener(
              new java.awt.event.ComponentAdapter() {
                public void componentResized(java.awt.event.ComponentEvent e) {
                  GridLayout layout = (GridLayout) grid.getLayout();
                  int columns = scroll.getViewport().getWidth() < 850 ? 1 : 2;
                  if (layout.getColumns() != columns) {
                    layout.setColumns(columns);
                    grid.revalidate();
                  }
                }
              });
      JPanel hint = Design.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
      hint.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
      hint.add(
          Design.label(
              "Chọn ảnh · Nhận diện · Kiểm tra · Lưu hóa đơn. Mọi chỉ số đều do bạn xác nhận.",
              11,
              Design.MUTED,
              false));
      show(top, scroll, hint);
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  private JPanel stat(String title, String value, String caption, String icon) {
    JPanel card = Design.card(Design.SURFACE);
    card.setLayout(new BorderLayout(14, 0));
    card.setBorder(BorderFactory.createEmptyBorder(15, 18, 15, 18));
    JLabel mark = new JLabel(Design.icon(icon, Ui.GREEN));
    card.add(mark, BorderLayout.WEST);
    JPanel text = Design.clear(new BorderLayout(0, 4));
    text.add(Design.label(title, 9, Design.MUTED, false), BorderLayout.NORTH);
    text.add(Design.label(value, 23, Design.INK, true), BorderLayout.CENTER);
    card.add(text, BorderLayout.CENTER);
    card.setToolTipText(caption);
    return card;
  }

  private JPanel roomCard(Room r, Contract contract) throws Exception {
    Readings previous = repository.previous(r);
    String latest = repository.latestMonth(r.id());
    boolean historicalRental =
        latest != null
            && repository.roomBills(r.id(), latest, latest).stream()
                .anyMatch(b -> b.rent().signum() > 0);
    JPanel card = Design.card(Design.SURFACE);
    card.setLayout(new BorderLayout(0, 18));
    card.setPreferredSize(new Dimension(360, 252));
    JPanel top = Design.clear(new BorderLayout());
    JPanel identity = Ui.column();
    identity.setOpaque(false);
    identity.add(Design.label(r.name(), 22, Design.INK, true));
    identity.add(Box.createVerticalStrut(5));
    identity.add(
        Design.label(
            contract != null
                ? "Đang thuê · " + contract.tenantName()
                : historicalRental
                    ? "Có lịch sử thuê · chưa bổ sung hợp đồng"
                    : r.tenant().isBlank()
                        ? "Trống · chưa có hợp đồng hiện tại"
                        : "Chưa xác nhận · tên cũ: " + r.tenant(),
            11,
            Design.MUTED,
            false));
    top.add(identity, BorderLayout.CENTER);
    JButton menu = Ui.button("•••", () -> {});
    menu.putClientProperty("JButton.buttonType", "borderless");
    menu.setToolTipText("Tùy chọn phòng");
    JPopupMenu popup = new JPopupMenu();
    JMenuItem edit = new JMenuItem("Sửa thông tin phòng");
    edit.addActionListener(e -> roomDialog(r));
    popup.add(edit);
    JMenuItem rental = new JMenuItem("Quản lý hợp đồng / nhận hoàn cọc");
    rental.addActionListener(e -> showContracts());
    popup.add(rental);
    JMenuItem waterPolicy = new JMenuItem("Đổi cách tính nước / số bàn giao");
    waterPolicy.addActionListener(
        e -> {
          try {
            new WaterPolicyDialog(this, repository, r, this::showRooms).setVisible(true);
          } catch (Exception ex) {
            Ui.error(this, ex);
          }
        });
    popup.add(waterPolicy);
    JMenuItem archive = new JMenuItem("Ngừng sử dụng phòng");
    archive.addActionListener(
        e -> {
          try {
            if (Ui.confirm(
                this, "Ngừng sử dụng phòng " + r.name() + "? Hóa đơn cũ vẫn được giữ.")) {
              repository.archiveRoom(r.id());
              showRooms();
            }
          } catch (Exception ex) {
            Ui.error(this, ex);
          }
        });
    popup.add(archive);
    menu.addActionListener(e -> popup.show(menu, 0, menu.getHeight()));
    top.add(menu, BorderLayout.EAST);
    card.add(top, BorderLayout.NORTH);
    JPanel info = Design.clear(new GridLayout(2, 1, 0, 12));
    info.add(metric("bolt", "Chỉ số điện gần nhất", Ui.n(previous.electricity()) + " kWh"));
    info.add(
        metric(
            "water",
            r.waterMode().equals("METER") ? "Chỉ số nước gần nhất" : "Nước cố định",
            r.waterMode().equals("METER")
                ? Ui.n(previous.water()) + " m³"
                : r.fixedWater() == null ? "Theo mặc định" : Ui.n(r.fixedWater()) + " m³ / kỳ"));
    card.add(info, BorderLayout.CENTER);
    JPanel footer = Design.clear(new BorderLayout(8, 0));
    JPanel periods = Ui.column();
    periods.setOpaque(false);
    periods.add(
        Design.label(
            latest == null ? "Chưa chốt kỳ đầu" : "Kỳ gần nhất · " + latest,
            11,
            Design.MUTED,
            false));
    periods.add(Ui.button("Lịch sử điện nước", () -> roomHistory(r)));
    footer.add(periods, BorderLayout.WEST);
    footer.add(
        Design.primary(
            Ui.button(
                "Lập hóa đơn  →",
                () -> {
                  try {
                    new ReadingDialog(this, repository, r, this::showRooms).setVisible(true);
                  } catch (Exception ex) {
                    Ui.error(this, ex);
                  }
                })),
        BorderLayout.EAST);
    card.add(footer, BorderLayout.SOUTH);
    Ui.contextMenu(card, popup);
    return card;
  }

  private void roomHistory(Room room) {
    new vn.nhatrohomu.room.ui.RoomHistoryDialog(this, repository, room, this::billDialog)
        .setVisible(true);
  }

  private JPanel metric(String icon, String text, String value) {
    JPanel p = Design.clear(new BorderLayout(8, 0));
    JLabel label = Design.label(text, 12, Design.MUTED, false);
    label.setIcon(Design.icon(icon, Ui.GREEN));
    label.setIconTextGap(9);
    p.add(label, BorderLayout.WEST);
    p.add(Design.label(value, 13, Design.INK, true), BorderLayout.EAST);
    return p;
  }

  private JPanel actions() {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 14));
    p.setOpaque(false);
    return p;
  }

  private void roomDialog(Room r) {
    try {
      new RoomDialog(this, repository, r, this::showRooms).setVisible(true);
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  private void showTenants() {
    showRental("Người thuê", RentalPanel.Page.TENANTS);
  }

  private void showContracts() {
    showRental("Hợp đồng", RentalPanel.Page.CONTRACTS);
  }

  private void showDebts() {
    showRental("Công nợ", RentalPanel.Page.DEBTS);
  }

  private void showRental(String title, RentalPanel.Page page) {
    selectNav(title);
    body.removeAll();
    body.add(new RentalPanel(this, repository, page));
    body.revalidate();
    body.repaint();
  }

  private void showDashboard() {
    selectNav("Tổng quan");
    body.removeAll();
    body.add(new DashboardPanel(repository), BorderLayout.CENTER);
    body.revalidate();
    body.repaint();
  }

  private void showHistory() {
    selectNav("Hóa đơn");
    try {
      int count = repository.billCount();
      if (historyPage * 50 >= count) historyPage = Math.max(0, (count - 1) / 50);
      List<Bill> bills = repository.bills(50, historyPage * 50);
      Object[][] rows = new Object[bills.size()][7];
      BigDecimal sum = BigDecimal.ZERO;
      for (int i = 0; i < bills.size(); i++) {
        Bill b = bills.get(i);
        sum = sum.add(b.total());
        rows[i] =
            new Object[] {
              b.month(),
              b.roomName(),
              Ui.money(b.rent()),
              Ui.money(b.feeTotal()),
              Ui.money(b.total()),
              b.paid()
                  ? "Đã thu đủ"
                  : repository.received(b.id()).signum() > 0 ? "Thu một phần" : "Chưa thu",
              b.note()
            };
      }
      JTable table =
          table(
              new String[] {"Kỳ", "Phòng", "Tiền phòng", "Phí", "Tổng", "Thanh toán", "Ghi chú"},
              rows);
      JPanel a = actions();
      a.add(
          Ui.button(
              "Xem hóa đơn",
              () -> {
                try {
                  billDialog(bills.get(selected(table)));
                } catch (Exception ex) {
                  Ui.error(this, ex);
                }
              }));
      a.add(
          Ui.button(
              "Ghi nhận thu tiền",
              () -> {
                try {
                  Bill b = bills.get(selected(table));
                  new PaymentDialog(this, repository, b).setVisible(true);
                  showHistory();
                } catch (Exception ex) {
                  Ui.error(this, ex);
                }
              }));
      a.add(
          Ui.button(
              "Hủy kỳ cuối để sửa",
              () -> {
                try {
                  Bill b = bills.get(selected(table));
                  if (Ui.confirm(
                      this,
                      "Hủy "
                          + b.roomName()
                          + " / "
                          + b.month()
                          + " để nhập lại?\n"
                          + "Chỉ hủy kỳ cuối chưa còn khoản thu. Chứng từ và ảnh được giữ trong Báo"
                          + " cáo.")) {
                    repository.deleteLatest(b);
                    showHistory();
                  }
                } catch (Exception ex) {
                  Ui.error(this, ex);
                }
              }));
      vn.nhatrohomu.shared.RowActions.install(
          table,
          java.util.Arrays.stream(a.getComponents())
              .filter(c -> c instanceof JButton)
              .map(c -> (JButton) c)
              .toList());
      a.add(Ui.button("Xuất CSV", this::exportCsv));
      a.add(Ui.refreshButton(this::showHistory));
      JPanel paging = actions();
      JButton
          back =
              Ui.button(
                  "←",
                  () -> {
                    historyPage--;
                    showHistory();
                  }),
          next =
              Ui.button(
                  "→",
                  () -> {
                    historyPage++;
                    showHistory();
                  });
      back.setEnabled(historyPage > 0);
      next.setEnabled((historyPage + 1) * 50 < count);
      paging.add(
          new JLabel(
              "Tổng trang: "
                  + Ui.money(sum)
                  + " · "
                  + count
                  + " hóa đơn · Trang "
                  + (historyPage + 1)));
      paging.add(back);
      paging.add(next);
      show(
          header(
              "Rõ từng hóa đơn.",
              "Tiền phòng, phí, người thuê và đơn giá được giữ theo lúc lập hóa đơn.",
              a),
          new JScrollPane(table),
          paging);
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }

  private String billText(Bill b) {
    BigDecimal received;
    try {
      received = repository.received(b.id());
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được lịch sử thu tiền.", e);
    }
    return "NHATROHOMU — HÓA ĐƠN\nPhòng: "
        + b.roomName()
        + "\nKỳ: "
        + b.month()
        + (b.contractId() == null
            ? (b.rent().signum() > 0
                ? "\nLoại: hóa đơn lịch sử nhập từ sổ cũ\nTiền phòng: " + Ui.money(b.rent())
                : "\nLoại: điện nước (chưa có hợp đồng)")
            : "\nNgười thuê: "
                + b.tenantName()
                + "\nHợp đồng: #"
                + b.contractId()
                + "\nHạn thu: "
                + b.dueDate()
                + "\nTiền phòng: "
                + Ui.money(b.rent())
                + "\nPhí: "
                + String.join(
                    "; ",
                    b.fees().stream().map(f -> f.label() + " " + Ui.money(f.amount())).toList()))
        + "\n\nĐIỆN\nChỉ số: "
        + Ui.n(b.electricityOld())
        + " → "
        + Ui.n(b.electricityNew())
        + " kWh\nLượng dùng: "
        + Ui.n(b.electricityUsed())
        + " × "
        + Ui.money(b.electricityRate())
        + "\n\nNƯỚC\n"
        + (b.waterMode().equals("METER")
            ? "Chỉ số: " + Ui.n(b.waterOld()) + " → " + Ui.n(b.waterNew()) + " m³\n"
            : "Số m³ cố định\n")
        + "Lượng dùng: "
        + Ui.n(b.waterUsed())
        + " × "
        + Ui.money(b.waterRate())
        + "\nTiền nước: "
        + Ui.money(b.waterCost())
        + "\nTiền điện: "
        + Ui.money(b.electricityCost())
        + "\nCộng điện + nước: "
        + Ui.money(b.utilitiesCost())
        + "\n\nTỔNG: "
        + Ui.money(b.total())
        + "\nTrạng thái: "
        + (received.compareTo(b.total()) >= 0
            ? "Đã thu đủ"
            : received.signum() > 0 ? "Thu một phần" : "Chưa thu")
        + "\nĐã thu: "
        + Ui.money(received)
        + "\nCòn thiếu: "
        + Ui.money(b.total().subtract(received))
        + "\nGhi chú: "
        + b.note()
        + (b.contractId() == null && b.rent().signum() == 0
            ? "\n\nKhông bao gồm tiền thuê phòng."
            : "\n\nTiền cọc được quản lý riêng, không nằm trong hóa đơn.");
  }

  private void billDialog(Bill b) {
    JDialog dialog = new JDialog(this, "Hóa đơn · " + b.roomName(), true);
    JTextArea text = new JTextArea(billText(b), 21, 48);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    dialog.add(new JScrollPane(text));
    JPanel buttons = actions();
    buttons.add(Ui.button("Ảnh điện", () -> showPhoto(dialog, b.id(), true)));
    buttons.add(Ui.button("Ảnh nước", () -> showPhoto(dialog, b.id(), false)));
    buttons.add(
        Ui.button(
            "Lưu TXT",
            () -> {
              Path path = saveFile("hoa-don-" + b.id() + ".txt");
              if (path != null)
                try {
                  Files.writeString(path, billText(b), StandardCharsets.UTF_8);
                  JOptionPane.showMessageDialog(dialog, "Đã xuất hóa đơn.");
                } catch (Exception ex) {
                  Ui.error(dialog, ex);
                }
            }));
    buttons.add(
        Ui.button(
            "Xuất PDF",
            () -> {
              Path path = saveFile("hoa-don-" + b.id() + ".pdf");
              if (path != null)
                Ui.background(
                    dialog,
                    () -> {
                      InvoicePdf.write(path, b, repository.payments(b.id()));
                      return path;
                    },
                    result -> JOptionPane.showMessageDialog(dialog, "Đã xuất PDF: " + result));
            }));
    buttons.add(Ui.button("Đóng", dialog::dispose));
    dialog.add(buttons, BorderLayout.SOUTH);
    dialog.pack();
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void showPhoto(JDialog owner, long id, boolean e) {
    Ui.background(
        owner,
        () -> repository.photo(id, e),
        data -> {
          try {
            if (data == null) throw new IllegalArgumentException("Hóa đơn không có ảnh này.");
            ImagePanel image = new ImagePanel();
            image.setPreferredSize(new Dimension(700, 450));
            image.setImage(ImageIO.read(new ByteArrayInputStream(data)));
            JOptionPane.showMessageDialog(owner, image, "Ảnh đã lưu", JOptionPane.PLAIN_MESSAGE);
          } catch (Exception ex) {
            Ui.error(owner, ex);
          }
        });
  }

  private Path saveFile(String name) {
    JFileChooser picker = new JFileChooser();
    picker.setSelectedFile(new File(name));
    if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
    Path path = picker.getSelectedFile().toPath();
    if (Files.exists(path) && !Ui.confirm(this, "File đã tồn tại. Ghi đè?")) return null;
    return path;
  }

  private static String csv(String text) {
    return CsvField.escape(text);
  }

  private void exportCsv() {
    Path path = saveFile("nhatrohomu-hoa-don.csv");
    if (path == null) return;
    Ui.background(
        this,
        () -> {
          java.util.List<Bill> all = new java.util.ArrayList<>();
          for (int offset = 0; ; offset += 50) {
            List<Bill> page = repository.bills(50, offset);
            if (page.isEmpty()) break;
            all.addAll(page);
          }
          vn.nhatrohomu.reporting.InvoiceCsv.write(path, all, repository);
          return true;
        },
        ok -> JOptionPane.showMessageDialog(this, "Đã xuất toàn bộ hóa đơn CSV."));
  }

  private void showSettings() {
    selectNav("Cài đặt");
    try {
      Rates rates = repository.rates();
      JTextField electricity = new JTextField(Ui.n(rates.electricity()), 14),
          water = new JTextField(Ui.n(rates.water())),
          fixed = new JTextField(Ui.n(rates.defaultWater()));
      JPanel priceCard = Design.card(Design.SURFACE);
      priceCard.setLayout(new BorderLayout(0, 18));
      JPanel title = Design.clear(new BorderLayout(0, 8));
      title.add(Design.label("Đơn giá & định mức", 21, Design.INK, true), BorderLayout.NORTH);
      title.add(
          Design.label("Áp dụng cho các hóa đơn chốt sau này.", 11, Design.MUTED, false),
          BorderLayout.CENTER);
      priceCard.add(title, BorderLayout.NORTH);
      JPanel form = Ui.form();
      Ui.field(form, 0, "Điện / kWh", electricity);
      Ui.field(form, 1, "Nước / m³", water);
      Ui.field(form, 2, "Nước mặc định / kỳ", fixed);
      JPanel values = Design.clear(new BorderLayout(0, 16));
      values.add(form, BorderLayout.NORTH);
      JTextArea hint =
          new JTextArea(
              "Đơn giá tính bằng đồng. Với phòng không có đồng hồ nước, chọn nước cố định và để"
                  + " trống số m³ riêng để dùng định mức này.");
      hint.setEditable(false);
      hint.setOpaque(false);
      hint.setLineWrap(true);
      hint.setWrapStyleWord(true);
      hint.setForeground(Design.MUTED);
      hint.setFont(hint.getFont().deriveFont(12f));
      hint.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));
      values.add(hint, BorderLayout.CENTER);
      priceCard.add(values, BorderLayout.CENTER);
      JPanel saveRow = Design.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      saveRow.add(
          Design.primary(
              Ui.button(
                  "Lưu cài đặt",
                  () -> {
                    try {
                      repository.saveRates(
                          new Rates(
                              BillingService.number(electricity.getText(), "Đơn giá điện"),
                              BillingService.number(water.getText(), "Đơn giá nước"),
                              BillingService.number(fixed.getText(), "Nước mặc định")));
                      JOptionPane.showMessageDialog(
                          this, "Đã lưu. Các hóa đơn cũ vẫn giữ nguyên đơn giá.");
                    } catch (Exception ex) {
                      Ui.error(this, ex);
                    }
                  })));
      priceCard.add(saveRow, BorderLayout.SOUTH);
      JPanel dataCard = Design.card(Design.SURFACE);
      dataCard.setLayout(new BorderLayout(0, 18));
      JPanel dataTitle = Ui.column();
      dataTitle.setOpaque(false);
      dataTitle.add(Design.label("Giữ sổ an toàn", 21, Design.INK, true));
      dataTitle.add(Box.createVerticalStrut(8));
      dataTitle.add(
          Design.label("Một bản sao chứa cả phòng, hóa đơn và ảnh.", 11, Design.MUTED, false));
      dataCard.add(dataTitle, BorderLayout.NORTH);
      JPanel backupActions = Design.clear(new GridLayout(2, 1, 0, 12));
      backupActions.add(
          Ui.button(
              "Xuất bản sao lưu",
              () -> {
                Path p = saveFile("nhatrohomu-backup-" + System.currentTimeMillis() + ".db");
                if (p != null)
                  Ui.background(
                      this,
                      () -> {
                        repository.backup(p);
                        return true;
                      },
                      ok ->
                          JOptionPane.showMessageDialog(this, "Đã sao lưu phòng, hóa đơn và ảnh."));
              }));
      backupActions.add(
          Ui.button(
              "Phục hồi từ bản sao",
              () -> {
                JFileChooser picker = new JFileChooser();
                if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                    && Ui.confirm(
                        this,
                        "Thay dữ liệu hiện tại bằng bản sao lưu? App giữ một bản cứu hộ trước khi"
                            + " thay."))
                  Ui.background(
                      this,
                      () -> {
                        repository.restore(picker.getSelectedFile().toPath());
                        return true;
                      },
                      ok -> {
                        historyPage = 0;
                        showSettings();
                        JOptionPane.showMessageDialog(
                            this, "Đã phục hồi. Bản cứu hộ nằm trong thư mục dữ liệu.");
                      });
              }));
      JPanel dataBody = Design.clear(new BorderLayout(0, 18));
      dataBody.add(backupActions, BorderLayout.NORTH);
      JTextArea path =
          new JTextArea(
              "THƯ MỤC DỮ LIỆU\n"
                  + dataDirectory
                  + "\n\nSao lưu định kỳ ra ổ khác. Phục hồi sẽ thay dữ liệu hiện tại.");
      path.setEditable(false);
      path.setOpaque(false);
      path.setLineWrap(true);
      path.setWrapStyleWord(true);
      path.setForeground(Design.MUTED);
      path.setFont(path.getFont().deriveFont(11f));
      dataBody.add(path, BorderLayout.CENTER);
      dataCard.add(dataBody, BorderLayout.CENTER);
      JPanel cards = Design.clear(new GridLayout(1, 2, 20, 0));
      cards.add(priceCard);
      cards.add(dataCard);
      cards.setPreferredSize(new Dimension(820, 390));
      JPanel center = Design.clear(new BorderLayout(0, 20));
      center.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));
      center.add(cards, BorderLayout.NORTH);
      JPanel note = Design.card(Design.MINT);
      note.setLayout(new BorderLayout(0, 10));
      note.add(
          Design.label("Riêng tư từ thiết bị của bạn", 15, Ui.GREEN, true), BorderLayout.NORTH);
      note.add(
          Design.label(
              "OCR xử lý trên Windows. Bạn luôn có thể nhập chỉ số bằng tay.",
              12,
              Design.MUTED,
              false),
          BorderLayout.CENTER);
      center.add(note, BorderLayout.SOUTH);
      JScrollPane scroll = new JScrollPane(center);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.getViewport().setBackground(Ui.BACKGROUND);
      show(
          header("Cài đặt", "Đơn giá rõ ràng. Dữ liệu được giữ trên máy của bạn.", null),
          scroll,
          null);
    } catch (Exception ex) {
      Ui.error(this, ex);
    }
  }
}
