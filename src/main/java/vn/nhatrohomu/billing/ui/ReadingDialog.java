package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.OcrService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.DataRules;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

public final class ReadingDialog extends JDialog {
  private final Repository repository;
  private final Room room;
  private final Rates rates;
  private Readings old;
  private final List<Contract> contracts;
  private final JTextField rent = new JTextField("0");
  private final JLabel rentalInfo = new JLabel(), feesInfo = new JLabel();
  private final JLabel oldElectricity = new JLabel(), oldWater = new JLabel();
  private final JComboBox<Contract> tenancyChoice = new JComboBox<>();
  private final JCheckBox utilitiesOnly = new JCheckBox("Chỉ lập điện nước, không thu tiền phòng");
  private boolean syncingRental;
  private final JTextField month = new JTextField(YearMonth.now().toString()),
      electricity = new JTextField(),
      water = new JTextField(),
      fixed = new JTextField(),
      note = new JTextField();
  private final JLabel total = new JLabel("Nhập chỉ số để tính tiền");
  private final JCheckBox confirm = new JCheckBox("Tôi đã kiểm tra ảnh, chỉ số, kỳ và số tiền.");
  private final PhotoBox electricityPhoto, waterPhoto;

  public ReadingDialog(JFrame owner, Repository repository, Room room, Runnable saved)
      throws Exception {
    super(owner, "Lập hóa đơn · " + room.name(), true);
    this.repository = repository;
    this.room = room;
    rates = repository.rates();
    old = repository.previous(room);
    contracts = new java.util.ArrayList<>(repository.rentals().contracts());
    electricity.setText(Ui.n(old.electricity()));
    water.setText(Ui.n(old.water()));
    fixed.setText(Ui.n(room.fixedWater() == null ? rates.defaultWater() : room.fixedWater()));
    String latest = repository.latestMonth(room.id());
    if (latest != null) month.setText(DataRules.period(latest).plusMonths(1).toString());
    else
      contracts.stream()
          .filter(k -> k.roomId() == room.id())
          .min(java.util.Comparator.comparing(Contract::startDate))
          .ifPresent(k -> month.setText(k.startDate().substring(0, 7)));
    YearMonth through = latest == null ? YearMonth.now() : DataRules.period(latest).plusMonths(1);
    List<Bill> issued = repository.roomBills(room.id(), "1900-01", "9999-12");
    String firstMissing = null;
    for (Contract k : contracts)
      if (k.roomId() == room.id()) {
        YearMonth end = YearMonth.from(RentalService.date(k.effectiveEnd()));
        if (end.isAfter(through)) end = through;
        for (YearMonth m = YearMonth.from(RentalService.date(k.startDate()));
            !m.isAfter(end);
            m = m.plusMonths(1)) {
          String value = m.toString();
          if (issued.stream()
              .noneMatch(
                  b ->
                      java.util.Objects.equals(b.contractId(), k.id())
                          && b.month().equals(value))) {
            if (firstMissing == null || value.compareTo(firstMissing) < 0) firstMissing = value;
            break;
          }
        }
      }
    if (firstMissing != null) month.setText(firstMissing);
    JPanel form = Ui.form();
    Ui.field(form, 0, "Kỳ (YYYY-MM)", vn.nhatrohomu.shared.DatePicker.field(month, true));
    oldElectricity.setText(Ui.n(old.electricity()));
    oldWater.setText(Ui.n(old.water()));
    Ui.field(form, 1, "Số điện cũ (kWh)", oldElectricity);
    oldElectricity.setToolTipText(
        "Lấy từ số chốt trước hoặc bàn giao. Sửa sai ở chứng từ nguồn để không lệch tiền và kỳ"
            + " sau.");
    Ui.field(form, 2, "Chỉ số điện mới", electricity);
    if (room.waterMode().equals("METER")) {
      Ui.field(form, 3, "Số nước cũ (m³)", oldWater);
      oldWater.setToolTipText(oldElectricity.getToolTipText());
      Ui.field(form, 4, "Chỉ số nước mới", water);
    } else Ui.field(form, 3, "Nước cố định kỳ này (m³)", fixed);
    Ui.field(form, 5, "Đơn giá điện / kWh", new JLabel(Ui.money(rates.electricity())));
    Ui.field(form, 6, "Đơn giá nước / m³", new JLabel(Ui.money(rates.water())));
    tenancyChoice.setRenderer(
        new DefaultListCellRenderer() {
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            return super.getListCellRendererComponent(
                list,
                value instanceof Contract k ? k.tenantName() + " · #" + k.id() : value,
                index,
                selected,
                focus);
          }
        });
    JPanel tenancy = Ui.column();
    tenancy.add(tenancyChoice);
    tenancy.add(rentalInfo);
    tenancy.add(utilitiesOnly);
    utilitiesOnly.addActionListener(e -> update());
    tenancy.add(
        Ui.button(
            "Thiết lập thuê phòng",
            () ->
                vn.nhatrohomu.rental.ui.RentalPanel.letRoom(
                    owner,
                    repository,
                    room.id(),
                    () -> {
                      try {
                        contracts.clear();
                        contracts.addAll(repository.rentals().contracts());
                        syncRental();
                        update();
                      } catch (Exception ex) {
                        Ui.error(this, ex);
                      }
                    })));
    Ui.field(form, 7, "Người thuê", tenancy);
    Ui.field(form, 8, "Tiền phòng (đ)", rent);
    rent.setToolTipText(
        "Mặc định tiền tháng trong hợp đồng. Có thể điều chỉnh khi ở chưa đủ tháng; ghi lý do vào"
            + " ghi chú.");
    Ui.field(form, 9, "Phí hàng tháng", feesInfo);
    Ui.field(form, 10, "Ghi chú", note);
    syncRental();
    tenancyChoice.addActionListener(
        e -> {
          if (!syncingRental) {
            syncDetails();
            update();
          }
        });
    electricityPhoto = new PhotoBox("Ảnh điện", electricity);
    waterPhoto = new PhotoBox("Ảnh nước", water);
    JTabbedPane photos = new JTabbedPane();
    photos.addTab("Đồng hồ điện", electricityPhoto);
    if (room.waterMode().equals("METER")) photos.addTab("Đồng hồ nước", waterPhoto);
    JPanel formCard = Design.card(Design.SURFACE);
    formCard.setLayout(new BorderLayout(0, 14));
    formCard.add(Design.label("Chỉ số & kỳ ghi nhận", 18, Design.INK, true), BorderLayout.NORTH);
    JScrollPane formScroll = new JScrollPane(form);
    formScroll.setBorder(null);
    formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    formScroll.getVerticalScrollBar().setUnitIncrement(18);
    formCard.add(formScroll, BorderLayout.CENTER);
    formCard.setPreferredSize(new Dimension(550, 420));
    JPanel photoCard = Design.card(Design.SURFACE);
    photoCard.setLayout(new BorderLayout(0, 16));
    photoCard.add(Design.label("Đối chiếu ảnh đồng hồ", 18, Design.INK, true), BorderLayout.NORTH);
    photoCard.add(photos, BorderLayout.CENTER);
    JPanel center = Design.clear(new BorderLayout(18, 0));
    center.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formCard, photoCard);
    split.setResizeWeight(0.52);
    split.setDividerLocation(550);
    split.setBorder(null);
    center.add(split, BorderLayout.CENTER);
    JPanel bottom = Ui.column();
    total.setFont(total.getFont().deriveFont(Font.BOLD, 20));
    total.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
    bottom.add(total);
    confirm.setText("Đã kiểm tra số liệu");
    confirm.setIcon(Design.icon("empty-check", Design.MUTED));
    confirm.setSelectedIcon(Design.icon("check", Ui.GREEN));
    confirm.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    confirm.setFocusPainted(false);
    bottom.add(confirm);
    JButton save =
        Ui.button(
            "Lưu hóa đơn",
            () -> {
              try {
                if (!confirm.isSelected())
                  throw new IllegalArgumentException(
                      "Hãy kiểm tra và đánh dấu xác nhận trước khi lưu.");
                if (electricityPhoto.working || waterPhoto.working)
                  throw new IllegalArgumentException("Đợi nhận diện ảnh hoàn tất trước khi lưu.");
                Bill bill = calculate();
                String last = repository.latestMonth(room.id());
                if (bill.periodStart().isBlank()
                    && last != null
                    && last.compareTo(bill.month()) >= 0)
                  throw new IllegalArgumentException(
                      "Cần chốt sau kỳ "
                          + last
                          + ". Để sửa kỳ cuối, hủy hóa đơn cũ trong Lịch sử trước.");
                byte[] ep =
                    electricityPhoto.photo == null ? null : OcrService.png(electricityPhoto.photo);
                byte[] wp = waterPhoto.photo == null ? null : OcrService.png(waterPhoto.photo);
                Ui.background(
                    this,
                    () -> {
                      repository.saveBill(bill, ep, wp);
                      return true;
                    },
                    result -> {
                      dispose();
                      saved.run();
                    });
              } catch (Exception ex) {
                Ui.error(this, ex);
              }
            });
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Hủy", this::dispose));
    buttons.add(save);
    Design.primary(save);
    bottom.add(buttons);
    bottom.setBackground(Design.SURFACE);
    bottom.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Design.LINE),
            BorderFactory.createEmptyBorder(4, 24, 14, 24)));
    confirm.setOpaque(false);
    DocumentListener changes =
        new DocumentListener() {
          public void insertUpdate(DocumentEvent e) {
            update();
          }

          public void removeUpdate(DocumentEvent e) {
            update();
          }

          public void changedUpdate(DocumentEvent e) {
            update();
          }
        };
    for (JTextField field : new JTextField[] {electricity, water, fixed, note, rent})
      field.getDocument().addDocumentListener(changes);
    month
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                syncRental();
                update();
              }

              public void removeUpdate(DocumentEvent e) {
                syncRental();
                update();
              }

              public void changedUpdate(DocumentEvent e) {
                syncRental();
                update();
              }
            });
    JPanel heading = Design.clear(new BorderLayout(0, 10));
    heading.setBorder(BorderFactory.createEmptyBorder(24, 28, 22, 28));
    heading.add(
        Design.label("Lập hóa đơn · " + room.name(), 27, Design.INK, true), BorderLayout.NORTH);
    heading.add(
        Design.label(
            "01  Chọn ảnh     →     02  Kiểm tra chỉ số     →     03  Xác nhận hóa đơn",
            12,
            Design.MUTED,
            false),
        BorderLayout.CENTER);
    getContentPane().setBackground(Ui.BACKGROUND);
    add(heading, BorderLayout.NORTH);
    JScrollPane contentScroll = new JScrollPane(center);
    contentScroll.setBorder(BorderFactory.createEmptyBorder());
    contentScroll.getViewport().setBackground(Ui.BACKGROUND);
    add(contentScroll, BorderLayout.CENTER);
    add(bottom, BorderLayout.SOUTH);
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    setSize(Math.min(1240, screen.width - 50), Math.min(800, screen.height - 50));
    setLocationRelativeTo(owner);
    update();
  }

  private Bill calculate() {
    try {
      YearMonth.parse(month.getText().trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("Kỳ cần đúng dạng YYYY-MM, ví dụ 2026-10.");
    }
    Bill utility =
        BillingService.calculate(
            room,
            month.getText().trim(),
            old,
            BillingService.number(electricity.getText(), "Điện mới"),
            BillingService.number(water.getText(), "Nước mới"),
            BillingService.number(fixed.getText(), "Nước cố định"),
            rates,
            note.getText());
    Contract contract = selectedContract();
    if (contract == null && !utilitiesOnly.isSelected())
      throw new IllegalArgumentException(
          "Thiết lập người thuê để lấy tiền phòng mặc định, hoặc chọn Chỉ lập điện nước.");
    BigDecimal amount =
        contract == null ? BigDecimal.ZERO : RentalService.money(rent.getText(), "Tiền phòng");
    if (contract != null && segmented(contract)) {
      java.time.LocalDate first = first(contract), last = last(contract);
      List<Charge> fees =
          contract.fees().stream()
              .map(
                  f ->
                      new Charge(
                          f.label(),
                          RentalService.prorated(f.amount(), first, last),
                          f.serviceId()))
              .toList();
      if (amount.compareTo(RentalService.prorated(contract.rent(), first, last)) != 0
          && note.getText().isBlank())
        throw new IllegalArgumentException("Ghi lý do điều chỉnh tiền phòng theo số ngày.");
      return RentalService.segment(utility, contract, amount, fees, first, last);
    }
    if (contract != null && amount.compareTo(contract.rent()) != 0 && note.getText().isBlank())
      throw new IllegalArgumentException("Ghi lý do khi điều chỉnh tiền phòng so với hợp đồng.");
    return RentalService.withRental(utility, contract, amount);
  }

  private Contract selectedContract() {
    return (Contract) tenancyChoice.getSelectedItem();
  }

  private java.time.LocalDate first(Contract k) {
    var m = YearMonth.parse(month.getText().trim());
    var start = RentalService.date(k.startDate());
    return start.isAfter(m.atDay(1)) ? start : m.atDay(1);
  }

  private java.time.LocalDate last(Contract k) {
    var m = YearMonth.parse(month.getText().trim());
    var end = RentalService.date(k.effectiveEnd());
    return end.isBefore(m.atEndOfMonth()) ? end : m.atEndOfMonth();
  }

  private boolean segmented(Contract k) {
    try {
      return repository.rentals().hasHandover(k.id())
          || !k.closedDate().isBlank()
              && !last(k).equals(YearMonth.parse(month.getText().trim()).atEndOfMonth());
    } catch (Exception ex) {
      throw new IllegalArgumentException("Không đọc được mốc bàn giao.", ex);
    }
  }

  private void syncRental() {
    syncingRental = true;
    try {
      YearMonth selected = YearMonth.parse(month.getText().trim());
      Contract previous = selectedContract();
      tenancyChoice.removeAllItems();
      List<Bill> issued = repository.roomBills(room.id(), selected.toString(), selected.toString());
      contracts.stream()
          .filter(
              k ->
                  k.roomId() == room.id()
                      && !RentalService.date(k.startDate()).isAfter(selected.atEndOfMonth())
                      && !RentalService.date(k.effectiveEnd()).isBefore(selected.atDay(1)))
          .filter(
              k -> issued.stream().noneMatch(b -> java.util.Objects.equals(b.contractId(), k.id())))
          .forEach(tenancyChoice::addItem);
      if (previous != null)
        for (int i = 0; i < tenancyChoice.getItemCount(); i++)
          if (tenancyChoice.getItemAt(i).id() == previous.id()) tenancyChoice.setSelectedIndex(i);
      syncDetails();
    } catch (Exception ex) {
      rentalInfo.setText("Nhập kỳ hợp lệ để chọn lần thuê");
    } finally {
      syncingRental = false;
    }
  }

  private void syncDetails() {
    try {
      Contract contract = selectedContract();
      old =
          contract == null
              ? repository.previous(room)
              : repository
                  .rentals()
                  .opening(contract.id(), YearMonth.parse(month.getText().trim()));
      oldElectricity.setText(Ui.n(old.electricity()));
      oldWater.setText(Ui.n(old.water()));
      electricity.setText(Ui.n(old.electricity()));
      water.setText(Ui.n(old.water()));
      if (contract != null && segmented(contract) && room.waterMode().equals("FIXED")) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(first(contract), last(contract)) + 1;
        BigDecimal quota = room.fixedWater() == null ? rates.defaultWater() : room.fixedWater();
        fixed.setText(
            quota
                .multiply(BigDecimal.valueOf(days))
                .divide(
                    BigDecimal.valueOf(YearMonth.parse(month.getText().trim()).lengthOfMonth()),
                    3,
                    java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString());
      }
      rent.setEnabled(contract != null);
      utilitiesOnly.setVisible(contract == null);
      if (contract != null) utilitiesOnly.setSelected(false);
      rent.setText(
          contract == null
              ? ""
              : (segmented(contract)
                      ? RentalService.prorated(contract.rent(), first(contract), last(contract))
                      : contract.rent())
                  .toPlainString());
      rentalInfo.setText(
          contract == null
              ? "Chưa có thông tin thuê cho tháng này"
              : "#" + contract.id() + " · " + first(contract) + " → " + last(contract));
      feesInfo.setText(
          contract == null
              ? "Không có"
              : ""
                  + Ui.money(
                      contract.fees().stream()
                          .map(
                              f ->
                                  segmented(contract)
                                      ? RentalService.prorated(
                                          f.amount(), first(contract), last(contract))
                                      : f.amount())
                          .reduce(BigDecimal.ZERO, BigDecimal::add)));
      feesInfo.setToolTipText(contract == null ? null : contract.fees().toString());
    } catch (Exception ignored) {
      rentalInfo.setText("Nhập kỳ hợp lệ để chọn hợp đồng");
    }
  }

  private void update() {
    confirm.setSelected(false);
    try {
      Bill b = calculate();
      total.setForeground(Ui.GREEN);
      total.setText("Tổng hóa đơn: " + Ui.money(b.total()));
      total.setToolTipText("Tiền phòng + điện + nước + phí. Tiền cọc được quản lý riêng.");
    } catch (Exception ex) {
      total.setForeground(new Color(155, 66, 42));
      total.setText(
          selectedContract() == null && !utilitiesOnly.isSelected()
              ? "Cần thiết lập người thuê cho tháng này"
              : "Kiểm tra chỉ số mới và tháng");
      total.setToolTipText(ex.getMessage());
    }
  }

  private final class PhotoBox extends MeterPhotoPanel {
    PhotoBox(String name, JTextField target) {
      super(ReadingDialog.this, room, name, target, () -> confirm.setSelected(false));
    }
  }
}
