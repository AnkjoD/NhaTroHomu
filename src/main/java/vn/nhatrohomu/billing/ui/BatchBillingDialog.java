package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import vn.nhatrohomu.billing.BillingDraft;
import vn.nhatrohomu.persistence.Repository;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.*;

/** Scroll through rooms and commit selected drafts, preserving successful rows on retry. */
public final class BatchBillingDialog extends JDialog {
  private final Repository repository;
  private final Runnable saved;
  private final JPanel cards = Ui.column();
  private final JTextField month = new JTextField(YearMonth.now().toString(), 10);
  private final List<Entry> entries = new ArrayList<>();
  private final JLabel progress = new JLabel("Nhập chỉ số mới cho từng phòng.");

  private record Prepared(Room room, BillingDraft draft, String error) {}

  private record Result(Entry entry, String error) {}

  public BatchBillingDialog(JFrame owner, Repository repository, Runnable saved) throws Exception {
    super(owner, "Lập hóa đơn nhiều phòng", true);
    this.repository = repository;
    this.saved = saved;
    var invoices = repository.billsBetween("1900-01", "9999-12");
    invoices.stream()
        .map(Bill::month)
        .max(String::compareTo)
        .ifPresent(last -> month.setText(YearMonth.parse(last).plusMonths(1).toString()));
    JPanel root = Design.clear(new BorderLayout(0, 16));
    root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
    JPanel top = Design.clear(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Tháng lập hóa đơn"));
    top.add(DatePicker.field(month, true));
    top.add(
        Ui.button(
            "Áp dụng tháng",
            () -> {
              if (entries.stream()
                      .anyMatch(
                          e -> !e.electricity.getText().isBlank() || !e.note.getText().isBlank())
                  && !Ui.confirm(this, "Đổi tháng sẽ bỏ phần đang nhập chưa lưu. Tiếp tục?"))
                return;
              load();
            }));
    root.add(top, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(cards);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(22);
    root.add(scroll);
    JPanel bottom = Design.clear(new BorderLayout(8, 8));
    bottom.add(progress);
    JPanel buttons = Design.clear(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(Ui.button("Đóng", this::dispose));
    buttons.add(Design.primary(Ui.button("Kiểm tra và lập các phòng đã chọn", this::commit)));
    bottom.add(buttons, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setSize(Math.min(980, screen.width - 50), Math.min(800, screen.height - 50));
    setLocationRelativeTo(owner);
    load();
  }

  private void load() {
    final YearMonth selected;
    try {
      selected = DataRules.period(month.getText().trim());
    } catch (Exception ex) {
      Ui.error(this, ex);
      return;
    }
    Ui.background(
        this,
        () -> {
          List<Prepared> plans = new ArrayList<>();
          for (Room room : repository.rooms()) {
            try {
              plans.add(new Prepared(room, BillingDraft.prepare(repository, room, selected), ""));
            } catch (Exception ex) {
              plans.add(new Prepared(room, null, ex.getMessage()));
            }
          }
          return plans;
        },
        plans -> {
          entries.clear();
          cards.removeAll();
          for (Prepared plan : plans) {
            Entry entry = new Entry(plan);
            entries.add(entry);
            cards.add(entry.card);
            cards.add(Box.createVerticalStrut(12));
          }
          cards.revalidate();
          cards.repaint();
          progress.setText(
              "Tháng " + selected + " · Chọn phòng, nhập số mới rồi kiểm tra trước khi lưu.");
        });
  }

  private void commit() {
    List<Entry> selected =
        entries.stream().filter(e -> e.include.isSelected() && !e.stored).toList();
    if (selected.isEmpty()) {
      Ui.error(this, new IllegalArgumentException("Chọn ít nhất một phòng chưa lưu."));
      return;
    }
    List<Bill> bills = new ArrayList<>();
    try {
      for (Entry entry : selected) bills.add(entry.calculate());
    } catch (Exception ex) {
      Ui.error(this, ex);
      return;
    }
    var sum =
        bills.stream()
            .map(Bill::total)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    if (!Ui.confirm(
        this,
        "Lập "
            + bills.size()
            + " hóa đơn · Tổng "
            + Ui.money(sum)
            + "?\nKiểm tra chỉ số mới của từng phòng. Chưa ghi nhận thu tiền.")) return;
    Ui.background(
        this,
        () -> {
          List<Result> results = new ArrayList<>();
          for (int i = 0; i < bills.size(); i++) {
            try {
              repository.saveBill(bills.get(i), null, null);
              results.add(new Result(selected.get(i), ""));
            } catch (Exception ex) {
              results.add(new Result(selected.get(i), ex.getMessage()));
            }
          }
          return results;
        },
        results -> {
          int done = 0;
          for (Result result : results) {
            Entry entry = result.entry();
            if (result.error().isBlank()) {
              done++;
              entry.stored = true;
              entry.include.setSelected(false);
              entry.include.setEnabled(false);
              for (JTextField field :
                  List.of(entry.electricity, entry.water, entry.rent, entry.note))
                field.setEditable(false);
              entry.status.setText("Đã lập hóa đơn · chưa thu tiền");
              entry.status.setForeground(Ui.GREEN);
            } else {
              entry.status.setText(result.error());
              entry.status.setForeground(new Color(170, 50, 40));
            }
          }
          progress.setText(
              "Đã lưu "
                  + done
                  + "/"
                  + results.size()
                  + " phòng. Phòng lỗi giữ dữ liệu để sửa và thử lại.");
          if (done > 0) saved.run();
        });
  }

  private final class Entry {
    final Prepared plan;
    final JPanel card = Design.card(Design.SURFACE);
    final JCheckBox include = new JCheckBox("Lập phòng này");
    final JTextField electricity = new JTextField(),
        water = new JTextField(),
        rent = new JTextField(),
        note = new JTextField();
    final JLabel status = new JLabel();
    boolean stored;

    Entry(Prepared plan) {
      this.plan = plan;
      card.setLayout(new BorderLayout(0, 12));
      card.setAlignmentX(Component.LEFT_ALIGNMENT);
      JPanel heading = Design.clear(new BorderLayout());
      heading.add(Design.label(plan.room().name(), 20, Design.INK, true));
      heading.add(include, BorderLayout.EAST);
      card.add(heading, BorderLayout.NORTH);
      if (plan.draft() == null) {
        include.setEnabled(false);
        status.setText(plan.error());
        card.add(status);
        return;
      }
      BillingDraft draft = plan.draft();
      include.setSelected(draft.contract() != null);
      JPanel fields = Ui.form();
      Ui.field(
          fields,
          0,
          "Người thuê",
          new JLabel(
              draft.contract() == null
                  ? "Chưa có thông tin thuê. Chọn Lập phòng này nếu chỉ ghi điện nước."
                  : draft.contract().tenantName()));
      Ui.field(fields, 1, "Số điện cũ", new JLabel(Ui.n(draft.opening().electricity())));
      Ui.field(fields, 2, "Số điện mới", electricity);
      Ui.field(fields, 3, "Số nước cũ", new JLabel(Ui.n(draft.opening().water())));
      Ui.field(
          fields,
          4,
          draft.room().waterMode().equals("METER") ? "Số nước mới" : "Nước khoán (m³)",
          water);
      if (draft.room().waterMode().equals("FIXED")) water.setText(Ui.n(draft.water()));
      rent.setText(draft.contract() == null ? "" : draft.rent().toPlainString());
      rent.setEditable(draft.contract() != null);
      Ui.field(fields, 5, "Tiền phòng (đ)", rent);
      Ui.field(fields, 6, "Ghi chú", note);
      card.add(fields);
      card.add(status, BorderLayout.SOUTH);
      DocumentListener changes =
          new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
              preview();
            }

            public void removeUpdate(DocumentEvent e) {
              preview();
            }

            public void changedUpdate(DocumentEvent e) {
              preview();
            }
          };
      for (JTextField field : List.of(electricity, water, rent, note))
        field.getDocument().addDocumentListener(changes);
      preview();
    }

    Bill calculate() {
      try {
        return plan.draft()
            .calculate(electricity.getText(), water.getText(), rent.getText(), note.getText());
      } catch (Exception ex) {
        throw new IllegalArgumentException(plan.room().name() + ": " + ex.getMessage(), ex);
      }
    }

    void preview() {
      if (stored) return;
      try {
        status.setText("Tổng: " + Ui.money(calculate().total()));
        status.setForeground(Ui.GREEN);
      } catch (Exception ex) {
        status.setText("Nhập chỉ số mới để tính tiền.");
        status.setForeground(Design.MUTED);
      }
    }
  }
}
