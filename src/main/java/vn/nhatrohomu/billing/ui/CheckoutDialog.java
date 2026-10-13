package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import vn.nhatrohomu.billing.BillingService;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

/** Checkout and final invoice commit together; debt/deposits remain with the old tenancy. */
public final class CheckoutDialog extends JDialog {
  private final Repository repository;
  private final Contract contract;
  private final Room room;
  private final Rates rates;
  private final JTextField date = new JTextField(),
      electricity = new JTextField(),
      water = new JTextField(),
      rent = new JTextField(),
      note = new JTextField();
  private final ChargeEditor fees;
  private final JLabel opening = new JLabel(), summary = new JLabel();
  private final JCheckBox confirmed =
      new JCheckBox("Đã kiểm tra ngày trả, chỉ số và các khoản tiền. Chưa ghi nhận thu tiền.");
  private boolean updating;

  public CheckoutDialog(JFrame owner, Repository repository, Contract contract, Runnable saved)
      throws Exception {
    super(
        owner,
        "Trả phòng và chốt kỳ cuối · " + contract.roomName() + " · " + contract.tenantName(),
        true);
    this.repository = repository;
    this.contract = contract;
    fees = new ChargeEditor(contract.fees());
    rates = repository.rates();
    room =
        repository.rooms().stream()
            .filter(r -> r.id() == contract.roomId())
            .findFirst()
            .orElseThrow();
    LocalDate proposed = LocalDate.now();
    if (proposed.isAfter(RentalService.date(contract.plannedEndBound())))
      proposed = RentalService.date(contract.plannedEndBound());
    if (proposed.isBefore(RentalService.date(contract.startDate())))
      proposed = RentalService.date(contract.startDate());
    date.setText(proposed.toString());
    date.setName("checkout-date");
    electricity.setName("checkout-electricity");
    water.setName("checkout-water");
    confirmed.setName("checkout-confirmed");
    JPanel form = Ui.form();
    Ui.field(form, 0, "Ngày trả phòng (gồm ngày này)", DatePicker.field(date, false));
    Ui.field(form, 1, "Chỉ số đầu kỳ của người này", opening);
    Ui.field(form, 2, "Điện lúc trả phòng (kWh)", electricity);
    Ui.field(
        form,
        3,
        room.waterMode().equals("METER") ? "Nước lúc trả phòng (m³)" : "Nước khoán kỳ cuối (m³)",
        water);
    Ui.field(form, 4, "Tiền phòng kỳ cuối (đ)", rent);
    Ui.field(form, 5, "Khoản thu thêm kỳ cuối", fees);
    Ui.field(form, 6, "Ghi chú / lý do điều chỉnh", note);
    JTextArea help =
        new JTextArea(
            "Tiền phòng và phí được gợi ý theo số ngày / số ngày trong tháng. Bạn có thể sửa theo"
                + " thỏa thuận. Lưu sẽ chốt hóa đơn cuối, chỉ số trả phòng và kết thúc thuê cùng"
                + " lúc. Công nợ và cọc vẫn của người cũ; ghi thu/hoàn riêng.",
            4,
            48);
    help.setEditable(false);
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setOpaque(false);
    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
    root.add(new JScrollPane(form));
    JPanel bottom = Ui.column();
    bottom.add(help);
    bottom.add(summary);
    bottom.add(confirmed);
    JButton save =
        Design.primary(
            Ui.button(
                "Chốt hóa đơn và trả phòng",
                () -> {
                  try {
                    if (!confirmed.isSelected())
                      throw new IllegalArgumentException("Kiểm tra và đánh dấu xác nhận trước.");
                    Bill bill = calculate();
                    Ui.background(
                        this,
                        () -> {
                          repository.rentals().checkout(bill);
                          return true;
                        },
                        ok -> {
                          dispose();
                          saved.run();
                        });
                  } catch (Exception ex) {
                    Ui.error(this, ex);
                  }
                }));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Hủy", this::dispose));
    buttons.add(save);
    bottom.add(buttons);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    date.getDocument().addDocumentListener(listener(this::defaults));
    for (var field : List.of(electricity, water, rent, note))
      field.getDocument().addDocumentListener(listener(this::preview));
    fees.onChange(this::preview);
    defaults();
    setSize(900, 650);
    setLocationRelativeTo(owner);
  }

  private static DocumentListener listener(Runnable run) {
    return new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        run.run();
      }

      public void removeUpdate(DocumentEvent e) {
        run.run();
      }

      public void changedUpdate(DocumentEvent e) {
        run.run();
      }
    };
  }

  private void defaults() {
    if (updating) return;
    updating = true;
    try {
      LocalDate last = RentalService.date(date.getText().trim());
      YearMonth month = YearMonth.from(last);
      LocalDate first =
          RentalService.date(contract.startDate()).isAfter(month.atDay(1))
              ? RentalService.date(contract.startDate())
              : month.atDay(1);
      Readings old = repository.rentals().opening(contract.id(), month);
      opening.setText(
          "Điện "
              + Ui.n(old.electricity())
              + " · Nước "
              + Ui.n(old.water())
              + " · "
              + first
              + " → "
              + last);
      rent.setText(RentalService.prorated(contract.rent(), first, last).toPlainString());
      fees.setCharges(
          contract.fees().stream()
              .map(f -> new Charge(f.label(), RentalService.prorated(f.amount(), first, last)))
              .toList());
      Bill existing =
          repository.roomBills(room.id(), month.toString(), month.toString()).stream()
              .filter(b -> java.util.Objects.equals(b.contractId(), contract.id()))
              .findFirst()
              .orElse(null);
      if (existing != null
          && (existing.periodEnd().isBlank()
                  ? month.atEndOfMonth().toString()
                  : existing.periodEnd())
              .equals(last.toString())) {
        rent.setText(existing.rent().toPlainString());
        fees.setCharges(existing.fees());
      }
      if (room.waterMode().equals("FIXED")) {
        BigDecimal monthly = room.fixedWater() == null ? rates.defaultWater() : room.fixedWater();
        long days = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1;
        water.setText(
            monthly
                .multiply(BigDecimal.valueOf(days))
                .divide(
                    BigDecimal.valueOf(month.lengthOfMonth()), 3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString());
      }
    } catch (Exception ex) {
      opening.setText("Chọn ngày hợp lệ trong thời hạn thuê.");
    } finally {
      updating = false;
    }
    preview();
  }

  private Bill calculate() throws Exception {
    LocalDate last = RentalService.date(date.getText().trim());
    YearMonth month = YearMonth.from(last);
    LocalDate first =
        RentalService.date(contract.startDate()).isAfter(month.atDay(1))
            ? RentalService.date(contract.startDate())
            : month.atDay(1);
    Readings old = repository.rentals().opening(contract.id(), month);
    BigDecimal amount = BillingService.number(water.getText(), "Nước bàn giao/khoán");
    Bill utility =
        BillingService.calculate(
            room,
            month.toString(),
            old,
            BillingService.number(electricity.getText(), "Điện bàn giao"),
            room.waterMode().equals("METER") ? amount : old.water(),
            amount,
            rates,
            note.getText());
    return RentalService.segment(
        utility,
        contract,
        RentalService.money(rent.getText(), "Tiền phòng"),
        fees.charges(),
        first,
        last);
  }

  private void preview() {
    if (updating) return;
    confirmed.setSelected(false);
    try {
      summary.setText("Tổng kỳ cuối: " + Ui.money(calculate().total()));
    } catch (Exception ex) {
      summary.setText("Nhập đầy đủ chỉ số và số tiền để xem tổng.");
    }
  }
}
