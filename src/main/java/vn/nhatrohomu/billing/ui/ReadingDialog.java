package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.integration.ImagePanel;
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
  private final Readings old;
  private final List<Contract> contracts;
  private final JTextField rent = new JTextField("0");
  private final JLabel rentalInfo = new JLabel(), feesInfo = new JLabel();
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
    contracts = repository.rentals().contracts();
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
    JPanel form = Ui.form();
    Ui.field(form, 0, "Kỳ (YYYY-MM)", vn.nhatrohomu.shared.DatePicker.field(month, true));
    Ui.field(form, 1, "Điện kỳ trước (kWh)", new JLabel(Ui.n(old.electricity())));
    Ui.field(form, 2, "Chỉ số điện mới", electricity);
    if (room.waterMode().equals("METER")) {
      Ui.field(form, 3, "Nước cũ (m³)", new JLabel(Ui.n(old.water())));
      Ui.field(form, 4, "Chỉ số nước mới", water);
    } else Ui.field(form, 3, "Nước cố định kỳ này (m³)", fixed);
    Ui.field(form, 5, "Đơn giá điện / kWh", new JLabel(Ui.money(rates.electricity())));
    Ui.field(form, 6, "Đơn giá nước / m³", new JLabel(Ui.money(rates.water())));
    Ui.field(form, 7, "Hợp đồng kỳ này", rentalInfo);
    Ui.field(form, 8, "Tiền phòng kỳ này (đ)", rent);
    rent.setToolTipText(
        "Mặc định tiền tháng trong hợp đồng. Có thể điều chỉnh khi ở chưa đủ tháng; ghi lý do vào"
            + " ghi chú.");
    Ui.field(form, 9, "Phí hàng tháng", feesInfo);
    Ui.field(form, 10, "Ghi chú / lý do giảm tiền", note);
    syncRental();
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
    formScroll.getVerticalScrollBar().setUnitIncrement(18);
    formCard.add(formScroll, BorderLayout.CENTER);
    formCard.setPreferredSize(new Dimension(410, 420));
    JPanel photoCard = Design.card(Design.SURFACE);
    photoCard.setLayout(new BorderLayout(0, 16));
    photoCard.add(Design.label("Đối chiếu ảnh đồng hồ", 18, Design.INK, true), BorderLayout.NORTH);
    photoCard.add(photos, BorderLayout.CENTER);
    JPanel center = Design.clear(new BorderLayout(18, 0));
    center.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
    center.add(formCard, BorderLayout.WEST);
    center.add(photoCard, BorderLayout.CENTER);
    JPanel bottom = Ui.column();
    total.setFont(total.getFont().deriveFont(Font.BOLD, 20));
    total.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
    bottom.add(total);
    bottom.add(confirm);
    JButton save =
        Ui.button(
            "Xác nhận & lưu hóa đơn",
            () -> {
              try {
                if (!confirm.isSelected())
                  throw new IllegalArgumentException(
                      "Hãy kiểm tra và đánh dấu xác nhận trước khi lưu.");
                if (electricityPhoto.working || waterPhoto.working)
                  throw new IllegalArgumentException("Đợi nhận diện ảnh hoàn tất trước khi lưu.");
                Bill bill = calculate();
                String last = repository.latestMonth(room.id());
                if (last != null && last.compareTo(bill.month()) >= 0)
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
    setSize(Math.min(1040, screen.width - 70), Math.min(740, screen.height - 70));
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
    BigDecimal amount =
        contract == null ? BigDecimal.ZERO : RentalService.money(rent.getText(), "Tiền phòng");
    if (contract != null && amount.compareTo(contract.rent()) != 0 && note.getText().isBlank())
      throw new IllegalArgumentException("Ghi lý do khi điều chỉnh tiền phòng so với hợp đồng.");
    return RentalService.withRental(utility, contract, amount);
  }

  private Contract selectedContract() {
    YearMonth selected = YearMonth.parse(month.getText().trim());
    return contracts.stream()
        .filter(
            k ->
                k.roomId() == room.id()
                    && !RentalService.date(k.startDate()).isAfter(selected.atEndOfMonth())
                    && !RentalService.date(k.effectiveEnd()).isBefore(selected.atDay(1)))
        .findFirst()
        .orElse(null);
  }

  private void syncRental() {
    try {
      Contract contract = selectedContract();
      rent.setEnabled(contract != null);
      rent.setText(contract == null ? "0" : contract.rent().toPlainString());
      rentalInfo.setText(
          contract == null
              ? "Chỉ điện nước · chưa có hợp đồng"
              : "#" + contract.id() + " · " + contract.tenantName());
      feesInfo.setText(
          contract == null
              ? "Không có"
              : ""
                  + Ui.money(
                      contract.fees().stream()
                          .map(Charge::amount)
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
      total.setText("Kiểm tra chỉ số hoặc kỳ nhập");
      total.setToolTipText(ex.getMessage());
    }
  }

  private final class PhotoBox extends JPanel {
    private BufferedImage photo;
    private boolean working;
    private final ImagePanel preview = new ImagePanel();

    PhotoBox(String name, JTextField target) {
      super(new BorderLayout(8, 8));
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
      add(preview, BorderLayout.CENTER);
      JLabel status =
          Design.label(
              "Kéo chuột khoanh vùng số. OCR có thể sai, hãy kiểm tra lại.",
              10,
              Design.MUTED,
              false);
      JButton choose =
          Ui.button(
              "Chọn " + name.toLowerCase(),
              () -> {
                JFileChooser picker = new JFileChooser();
                picker.setFileFilter(
                    new FileNameExtensionFilter("Ảnh PNG, JPG", "png", "jpg", "jpeg"));
                if (picker.showOpenDialog(ReadingDialog.this) == JFileChooser.APPROVE_OPTION) {
                  Ui.background(
                      ReadingDialog.this,
                      () -> OcrService.load(picker.getSelectedFile().toPath()),
                      image -> {
                        photo = image;
                        preview.setImage(image);
                        confirm.setSelected(false);
                        status.setText("Đã chọn ảnh. Khoanh vùng rồi bấm Đọc số.");
                      });
                }
              });
      JButton recognize = Design.primary(new JButton("Nhận diện chỉ số"));
      JComboBox<OcrService.Engine> engine = new JComboBox<>(OcrService.Engine.values());
      engine.setSelectedItem(
          OcrService.rapidAvailable() ? OcrService.Engine.RAPID : OcrService.Engine.WINDOWS);
      engine.setToolTipText(
          "RapidOCR cần gói tùy chọn. Khoanh riêng dãy số; luôn kiểm tra trước khi lưu.");
      recognize.addActionListener(
          event -> {
            BufferedImage selected = preview.selected();
            if (selected == null) {
              Ui.error(ReadingDialog.this, new IllegalArgumentException("Chọn ảnh trước."));
              return;
            }
            working = true;
            OcrService.Engine selectedEngine = (OcrService.Engine) engine.getSelectedItem();
            engine.setEnabled(false);
            recognize.setEnabled(false);
            choose.setEnabled(false);
            status.setText("Đang nhận diện…");
            new SwingWorker<OcrService.Result, Void>() {
              protected OcrService.Result doInBackground() throws Exception {
                return new OcrService().recognize(selected, selectedEngine);
              }

              protected void done() {
                working = false;
                engine.setEnabled(true);
                recognize.setEnabled(true);
                choose.setEnabled(true);
                if (!ReadingDialog.this.isDisplayable()) return;
                try {
                  OcrService.Result result = get();
                  status.setText("Đã đọc ảnh. Hãy kiểm tra và chọn số đúng.");
                  if (result.candidates().isEmpty()) {
                    Ui.error(
                        ReadingDialog.this,
                        new IllegalArgumentException(
                            "Không tìm thấy số. Cắt ảnh sát hơn hoặc nhập tay."));
                    return;
                  }
                  JComboBox<String> candidates =
                      new JComboBox<>(result.candidates().toArray(String[]::new));
                  JTextArea raw = new JTextArea(result.text(), 5, 36);
                  raw.setEditable(false);
                  raw.setLineWrap(true);
                  JPanel panel = Ui.column();
                  panel.add(new JLabel("Số tìm thấy (có thể là số seri/đơn vị, cần kiểm tra):"));
                  panel.add(candidates);
                  panel.add(new JScrollPane(raw));
                  if (JOptionPane.showConfirmDialog(
                          ReadingDialog.this,
                          panel,
                          "Chọn chỉ số để điền",
                          JOptionPane.OK_CANCEL_OPTION)
                      == JOptionPane.OK_OPTION)
                    target.setText((String) candidates.getSelectedItem());
                } catch (Exception ex) {
                  status.setText("Không nhận diện được; vẫn có thể nhập tay.");
                  Ui.error(ReadingDialog.this, ex);
                }
              }
            }.execute();
          });
      JPanel buttons = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 12));
      buttons.add(choose);
      buttons.add(
          Ui.button(
              "Ảnh lớn / khoanh số",
              () -> {
                if (photo == null) {
                  Ui.error(ReadingDialog.this, new IllegalArgumentException("Chọn ảnh trước."));
                  return;
                }
                BufferedImage region = ImagePanel.chooseRegion(ReadingDialog.this, photo);
                if (region != null) {
                  preview.setImage(region);
                  confirm.setSelected(false);
                  status.setText("Đã chọn vùng OCR; ảnh gốc vẫn được giữ khi lưu hóa đơn.");
                }
              }));
      buttons.add(recognize);
      JPanel controls = Ui.column();
      controls.setOpaque(false);
      JPanel engineRow = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
      engineRow.add(new JLabel("Công cụ đọc ảnh:"));
      engineRow.add(engine);
      controls.add(engineRow);
      controls.add(buttons);
      controls.add(status);
      add(controls, BorderLayout.SOUTH);
    }
  }
}
