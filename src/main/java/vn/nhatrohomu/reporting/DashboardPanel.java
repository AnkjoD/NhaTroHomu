package vn.nhatrohomu.reporting;

import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import javax.swing.*;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.rental.RentalService;
import vn.nhatrohomu.shared.Design;
import vn.nhatrohomu.shared.Models.*;
import vn.nhatrohomu.shared.Ui;

/** Snapshot loaded off the EDT; decimal money is only converted for drawing bars. */
public final class DashboardPanel extends JPanel {
  private final Repository repository;
  private final JTextField month = new JTextField(YearMonth.now().toString(), 7);
  private final JPanel content = new JPanel();
  private long request;

  public record Snapshot(
      List<Room> rooms,
      List<Bill> bills,
      Map<Long, BigDecimal> received,
      List<Contract> contracts,
      BigDecimal heldDeposit) {
    public Snapshot(List<Room> rooms, List<Bill> bills, Map<Long, BigDecimal> received) {
      this(rooms, bills, received, List.of(), BigDecimal.ZERO);
    }
  }

  public DashboardPanel(Repository repository) {
    super(new BorderLayout(0, 20));
    this.repository = repository;
    setOpaque(false);
    JPanel heading = Design.clear(new BorderLayout());
    JPanel words = Ui.column();
    words.setOpaque(false);
    words.add(Design.label("Một tháng, rõ mọi con số.", 26, Design.INK, true));
    words.add(Box.createVerticalStrut(8));
    words.add(
        Design.label(
            "Theo kỳ hóa đơn · Tiền phòng, điện nước & phí · Tiền cọc tách riêng",
            12,
            Design.MUTED,
            false));
    heading.add(words, BorderLayout.CENTER);
    JPanel filter = Design.clear(new FlowLayout(FlowLayout.RIGHT));
    filter.add(new JLabel("Kỳ"));
    filter.add(month);
    filter.add(Ui.button("Xem", this::load));
    heading.add(filter, BorderLayout.EAST);
    add(heading, BorderLayout.NORTH);
    content.setOpaque(false);
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getVerticalScrollBar().setUnitIncrement(20);
    add(scroll);
    load();
  }

  private void load() {
    final YearMonth selected;
    try {
      selected = YearMonth.parse(month.getText().trim());
    } catch (Exception e) {
      Ui.error(this, new IllegalArgumentException("Nhập kỳ theo YYYY-MM, ví dụ 2026-10."));
      return;
    }
    long generation = ++request;
    content.removeAll();
    content.add(new JLabel("Đang đọc số liệu…"));
    content.revalidate();
    content.repaint();
    new SwingWorker<Snapshot, Void>() {
      protected Snapshot doInBackground() throws Exception {
        // Retrieve metadata only; the bills query deliberately excludes photo blobs.
        List<Bill> bills =
            repository.billsBetween(selected.minusMonths(5).toString(), selected.toString());
        Map<Long, BigDecimal> received = new HashMap<>();
        for (Bill bill : bills)
          if (bill.month().equals(selected.toString()))
            received.put(bill.id(), repository.received(bill.id()));
        List<Contract> contracts = repository.rentals().contracts();
        BigDecimal held = BigDecimal.ZERO;
        for (Contract contract : contracts)
          held = held.add(repository.rentals().heldDeposit(contract.id()));
        return new Snapshot(repository.rooms(), bills, received, contracts, held);
      }

      protected void done() {
        if (generation != request) return;
        Window window = SwingUtilities.getWindowAncestor(DashboardPanel.this);
        if (window != null && !window.isDisplayable()) return;
        try {
          render(selected, get());
        } catch (Exception e) {
          content.removeAll();
          content.add(new JLabel("Không đọc được thống kê. Nhấn Xem để thử lại."));
          Ui.error(DashboardPanel.this, e);
        }
      }
    }.execute();
  }

  private void render(YearMonth selected, Snapshot data) {
    content.removeAll();
    List<Bill> current =
        data.bills().stream().filter(b -> b.month().equals(selected.toString())).toList();
    BigDecimal total = BigDecimal.ZERO, received = BigDecimal.ZERO;
    Set<Long> billed = new HashSet<>();
    for (Bill b : current) {
      total = total.add(b.total());
      received = received.add(data.received().get(b.id()));
      billed.add(b.roomId());
    }
    List<Room> missing =
        data.rooms().stream()
            .filter(
                r ->
                    !billed.contains(r.id())
                        && data.contracts().stream()
                            .anyMatch(
                                k ->
                                    k.roomId() == r.id()
                                        && !RentalService.date(k.startDate())
                                            .isAfter(selected.atEndOfMonth())
                                        && !RentalService.date(k.effectiveEnd())
                                            .isBefore(selected.atDay(1))))
            .toList();
    JPanel cards = Design.clear(new GridLayout(1, 4, 12, 0));
    cards.add(stat("Đã chốt", Ui.money(total)));
    cards.add(stat("Đã thu", Ui.money(received)));
    cards.add(stat("Còn thiếu", Ui.money(total.subtract(received))));
    cards.add(stat("Kỳ thuê chưa chốt", Integer.toString(missing.size())));
    cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
    content.add(cards);
    content.add(Box.createVerticalStrut(16));
    JLabel scope =
        Design.label(
            "Đã thu = mọi lần thu cho hóa đơn kỳ này, kể cả thu vào tháng khác.",
            11,
            Design.MUTED,
            false);
    content.add(scope);
    content.add(Box.createVerticalStrut(16));
    long occupied =
        data.rooms().stream()
            .filter(
                r ->
                    data.contracts().stream()
                        .anyMatch(k -> k.roomId() == r.id() && k.occupiedOn(java.time.LocalDate.now())))
            .count();
    JPanel occupation = Design.clear(new GridLayout(1, 3, 12, 0));
    occupation.add(stat("Đang thuê hôm nay", Long.toString(occupied)));
    occupation.add(stat("Chưa có hợp đồng hôm nay", Long.toString(data.rooms().size() - occupied)));
    occupation.add(stat("Cọc đang giữ · mọi hợp đồng", Ui.money(data.heldDeposit())));
    occupation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
    content.add(occupation);
    content.add(Box.createVerticalStrut(16));
    List<String> periods = new ArrayList<>();
    List<Double> electricity = new ArrayList<>(), water = new ArrayList<>();
    for (int i = 5; i >= 0; i--) {
      String period = selected.minusMonths(i).toString();
      periods.add(period);
      BigDecimal e = BigDecimal.ZERO, w = BigDecimal.ZERO;
      for (Bill b : data.bills())
        if (b.month().equals(period)) {
          e = e.add(b.electricityUsed().multiply(b.electricityRate()));
          w = w.add(b.waterCost());
        }
      electricity.add(e.doubleValue());
      water.add(w.doubleValue());
    }
    content.add(
        chartCard(
            "Khoản điện & nước đã lập · 6 tháng",
            new Bars(periods, electricity, water, "đồng", "Điện", "Nước")));
    content.add(Box.createVerticalStrut(16));
    List<Double> rents = new ArrayList<>(), fees = new ArrayList<>();
    for (String period : periods) {
      BigDecimal rent = BigDecimal.ZERO, fee = BigDecimal.ZERO;
      for (Bill bill : data.bills())
        if (bill.month().equals(period)) {
          rent = rent.add(bill.rent());
          fee = fee.add(bill.feeTotal());
        }
      rents.add(rent.doubleValue());
      fees.add(fee.doubleValue());
    }
    content.add(
        chartCard(
            "Tiền phòng & phí đã lập hóa đơn · 6 tháng",
            new Bars(periods, rents, fees, "đồng", "Phòng", "Phí")));
    content.add(Box.createVerticalStrut(16));
    JPanel usage = Design.clear(new BorderLayout(0, 12));
    JComboBox<String> unit = new JComboBox<>(new String[] {"Điện · kWh", "Nước · m³"});
    JPanel usageTitle = Design.clear(new BorderLayout());
    usageTitle.add(Design.label("Lượng dùng theo phòng", 17, Design.INK, true));
    usageTitle.add(unit, BorderLayout.EAST);
    usage.add(usageTitle, BorderLayout.NORTH);
    JPanel area = Design.clear(new BorderLayout());
    usage.add(area);
    Runnable draw =
        () -> {
          boolean e = unit.getSelectedIndex() == 0;
          area.removeAll();
          area.add(
              new Bars(
                  current.stream()
                      .map(b -> b.roomName() + (!e && !b.waterMode().equals("METER") ? " *" : ""))
                      .toList(),
                  current.stream()
                      .map(b -> (e ? b.electricityUsed() : b.waterUsed()).doubleValue())
                      .toList(),
                  List.of(),
                  e ? "kWh" : "m³",
                  e ? "Điện" : "Nước",
                  ""));
          area.revalidate();
          area.repaint();
        };
    unit.addActionListener(e -> draw.run());
    draw.run();
    usage.add(
        Design.label(
            "* Nước theo mức cố định; không phải số đo thực tế. Rê chuột lên cột để xem giá trị.",
            11,
            Design.MUTED,
            false),
        BorderLayout.SOUTH);
    JPanel usageCard = Design.card(new BorderLayout(), 20);
    usageCard.add(usage);
    usageCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 345));
    content.add(usageCard);
    content.add(Box.createVerticalStrut(16));
    JPanel reminders = Design.card(new BorderLayout(0, 10), 20);
    reminders.add(Design.label("Cần kiểm tra", 17, Design.INK, true), BorderLayout.NORTH);
    JTextArea notes = new JTextArea();
    notes.setEditable(false);
    notes.setOpaque(false);
    notes.setForeground(Design.MUTED);
    notes.setLineWrap(true);
    notes.setWrapStyleWord(true);
    StringBuilder text =
        new StringBuilder(
            missing.isEmpty()
                ? "Các phòng đang quản lý có hợp đồng trong kỳ đều đã có hóa đơn."
                : "Chưa chốt (có hợp đồng trong kỳ): "
                    + String.join(", ", missing.stream().map(Room::name).toList()));
    long unpaid =
        current.stream().filter(b -> data.received().get(b.id()).compareTo(b.total()) < 0).count();
    text.append("\n").append(unpaid).append(" hóa đơn kỳ này còn thiếu tiền.");
    for (Bill b : current)
      for (Bill previous : data.bills())
        if (previous.roomId() == b.roomId()
            && previous.month().equals(selected.minusMonths(1).toString())
            && previous.electricityUsed().signum() > 0
            && b.electricityUsed()
                    .compareTo(previous.electricityUsed().multiply(new BigDecimal("1.5")))
                > 0)
          text.append("\n")
              .append(b.roomName())
              .append(": điện tăng hơn 50% so với kỳ trước; kiểm tra lại ảnh và số chốt.");
    notes.setText(text.toString());
    for (Bill bill : current)
      if (!bill.dueDate().isBlank()
          && RentalService.date(bill.dueDate()).isBefore(java.time.LocalDate.now())
          && data.received().get(bill.id()).compareTo(bill.total()) < 0)
        text.append("\n")
            .append(bill.roomName())
            .append(": hóa đơn quá hạn ")
            .append(bill.dueDate());
    for (Contract contract : data.contracts())
      if (contract.occupiedOn(java.time.LocalDate.now())
          && !RentalService.date(contract.effectiveEnd())
              .isAfter(java.time.LocalDate.now().plusDays(30)))
        text.append("\n")
            .append(contract.roomName())
            .append(": hợp đồng kết thúc ")
            .append(contract.effectiveEnd());
    notes.setText(text.toString());
    reminders.add(notes);
    reminders.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
    content.add(reminders);
    content.revalidate();
    content.repaint();
  }

  private JPanel stat(String label, String value) {
    JPanel card = Design.card(new BorderLayout(0, 10), 18);
    card.add(Design.label(label, 12, Design.MUTED, false), BorderLayout.NORTH);
    card.add(Design.label(value, 20, Design.INK, true));
    return card;
  }

  private JPanel chartCard(String title, Bars chart) {
    JPanel card = Design.card(new BorderLayout(0, 12), 20);
    card.add(Design.label(title, 17, Design.INK, true), BorderLayout.NORTH);
    card.add(chart);
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
    return card;
  }

  static final class Bars extends JComponent {
    final List<String> labels;
    final List<Double> first, second;
    final String unit, a, b;

    Bars(
        List<String> labels,
        List<Double> first,
        List<Double> second,
        String unit,
        String a,
        String b) {
      this.labels = labels;
      this.first = first;
      this.second = second;
      this.unit = unit;
      this.a = a;
      this.b = b;
      setPreferredSize(new Dimension(760, 245));
      setToolTipText("");
    }

    public String getToolTipText(java.awt.event.MouseEvent e) {
      if (labels.isEmpty()) return "Chưa có hóa đơn";
      int i = (int) ((e.getX() - 66) * labels.size() / (double) Math.max(1, getWidth() - 86));
      if (i < 0 || i >= labels.size()) return null;
      return labels.get(i)
          + ": "
          + a
          + " "
          + Ui.n(BigDecimal.valueOf(first.get(i)))
          + " "
          + unit
          + (second.isEmpty()
              ? ""
              : " · " + b + " " + Ui.n(BigDecimal.valueOf(second.get(i))) + " " + unit);
    }

    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(11f));
        int left = 66,
            top = 30,
            bottom = getHeight() - 42,
            height = bottom - top,
            width = getWidth() - left - 20;
        if (labels.isEmpty()) {
          g.setColor(Design.MUTED);
          g.drawString("Chưa có hóa đơn cho kỳ này.", left, top + 70);
          return;
        }
        double max = 1;
        for (double v : first) max = Math.max(max, v);
        for (double v : second) max = Math.max(max, v);
        max *= 1.15;
        for (int i = 0; i <= 4; i++) {
          int y = bottom - height * i / 4;
          g.setColor(Design.LINE);
          g.drawLine(left, y, getWidth() - 20, y);
          g.setColor(Design.MUTED);
          double v = max * i / 4;
          String label =
              v >= 1000000
                  ? String.format(java.util.Locale.ROOT, "%.1fM", v / 1000000)
                  : v >= 1000
                      ? String.format(java.util.Locale.ROOT, "%.0fk", v / 1000)
                      : String.format(java.util.Locale.ROOT, "%.0f", v);
          g.drawString(label, 8, y + 4);
        }
        int slot = Math.max(1, width / labels.size()),
            bar = Math.max(2, Math.min(34, (slot - 12) / (second.isEmpty() ? 1 : 2)));
        for (int i = 0; i < labels.size(); i++) {
          int center = left + slot * i + slot / 2;
          int x = center - (second.isEmpty() ? bar / 2 : bar + 2);
          int h = (int) (height * first.get(i) / max);
          g.setColor(Ui.GREEN);
          g.fillRoundRect(x, bottom - h, bar, h, 7, 7);
          if (!second.isEmpty()) {
            h = (int) (height * second.get(i) / max);
            g.setColor(new Color(82, 163, 188));
            g.fillRoundRect(center + 2, bottom - h, bar, h, 7, 7);
          }
          g.setColor(Design.MUTED);
          String label = labels.get(i);
          if (label.length() > 12) label = label.substring(0, 10) + "…";
          if (labels.size() < 16 || i % Math.max(1, labels.size() / 10) == 0)
            g.drawString(label, center - g.getFontMetrics().stringWidth(label) / 2, bottom + 21);
        }
        g.setColor(Ui.GREEN);
        g.drawString("● " + a, left, 16);
        g.setColor(new Color(82, 163, 188));
        if (!second.isEmpty()) g.drawString("● " + b, left + 95, 16);
        g.setColor(Design.MUTED);
        g.drawString(unit, getWidth() - 55, 16);
      } finally {
        g.dispose();
      }
    }
  }
}
