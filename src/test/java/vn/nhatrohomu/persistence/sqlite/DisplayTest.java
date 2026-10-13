package vn.nhatrohomu.persistence.sqlite;

import java.awt.*;
import java.time.*;
import javax.swing.*;
import javax.swing.table.*;
import vn.nhatrohomu.shared.*;

public final class DisplayTest {
  static int checks;

  static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  static JButton find(Container parent, String label) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton b && b.getText().equals(label)) return b;
      if (c instanceof Container nested) {
        JButton b = find(nested, label);
        if (b != null) return b;
      }
    }
    return null;
  }

  static JScrollPane visibleScroll(Container parent) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JScrollPane scroll && scroll.isShowing()) return scroll;
      if (c instanceof Container nested) {
        JScrollPane scroll = visibleScroll(nested);
        if (scroll != null) return scroll;
      }
    }
    return null;
  }

  static JTextArea text(Container parent, String value) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JTextArea area && area.getText().equals(value)) return area;
      if (c instanceof Container nested) {
        JTextArea area = text(nested, value);
        if (area != null) return area;
      }
    }
    return null;
  }

  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          System.setProperty("nhatrohomu.ui.test", "true");
          Design.setup();
          var internet = new Models.Charge("Internet", new java.math.BigDecimal("100000"));
          ChargeEditor feeEditor = new ChargeEditor(java.util.List.of(internet));
          check(feeEditor.charges().isEmpty(), "Suggested fee is not automatically charged");
          find(feeEditor, "Dùng lại").doClick();
          check(
              feeEditor.charges().size() == 1
                  && feeEditor.charges().getFirst().label().equals(internet.label())
                  && feeEditor.charges().getFirst().amount().compareTo(internet.amount()) == 0,
              "Previously saved fee can be reused explicitly");
          find(feeEditor, "+ Thêm khoản phí").doClick();
          try {
            feeEditor.charges();
            throw new AssertionError("Blank fee row accepted");
          } catch (IllegalArgumentException expected) {
            checks++;
          }
          feeEditor.setCharges(java.util.List.of(internet, internet));
          try {
            feeEditor.charges();
            throw new AssertionError("Duplicate fee accepted");
          } catch (IllegalArgumentException expected) {
            checks++;
          }
          feeEditor.setCharges(
              java.util.List.of(new Models.Charge("Phí thử", new java.math.BigDecimal("-1"))));
          try {
            feeEditor.charges();
            throw new AssertionError("Negative fee accepted");
          } catch (IllegalArgumentException expected) {
            checks++;
          }
          feeEditor.setCharges(java.util.List.of());
          check(feeEditor.charges().isEmpty(), "Removing charges leaves no hidden amounts");
          JTextField date = new JTextField("2024-02-10");
          JPopupMenu calendar = DatePicker.calendar(date, false);
          check(
              date.getText().equals("2024-02-10"), "Opening calendar must not change a typed date");
          find(calendar, "29").doClick();
          check(date.getText().equals("2024-02-29"), "Leap day selected");
          JTextField nonLeap = new JTextField("2025-02-10");
          check(
              find(DatePicker.calendar(nonLeap, false), "29") == null,
              "Non-leap February rejects day29");
          JTextField month = new JTextField("2025-11");
          find(DatePicker.calendar(month, true), "Chọn kỳ").doClick();
          check(month.getText().equals("2025-11"), "Month picker preserves year and month");
          check(DatePicker.days(YearMonth.of(2100, 2)).size() == 28, "Century leap-year boundary");
          DefaultTableModel model =
              new DefaultTableModel(
                  new Object[][] {{"1", "A"}, {"2", "B"}}, new String[] {"Mã", "Tên"});
          JTable table = new JTable(model);
          table.setAutoCreateRowSorter(true);
          int[] chosen = {-1};
          JButton action =
              Ui.button(
                  "Mở", () -> chosen[0] = table.convertRowIndexToModel(table.getSelectedRow()));
          RowActions.install(table, java.util.List.of(action));
          SelectionView view =
              new SelectionView(table, java.util.List.of(action), 1, "test-display");
          view.selectLayout("Thẻ");
          table.getRowSorter().toggleSortOrder(1);
          table.getRowSorter().toggleSortOrder(1);
          find(view, "Mở").doClick();
          check(chosen[0] == 1, "Card action uses the sorted row identity");
          ((TableRowSorter<?>) table.getRowSorter()).setRowFilter(RowFilter.regexFilter("A", 1));
          find(view, "Mở").doClick();
          check(chosen[0] == 0, "Filtered card action targets the correct record");
          view.selectLayout("Bảng");
          check(
              view.table() == table && table.getRowCount() == 1,
              "Layouts share one filtered model");
          model.addRow(new Object[] {"3", "A2"});
          view.selectLayout("Thẻ");
          check(table.getRowCount() == 2, "New records visible after update in both layouts");
          int[] change = {0};
          ThemeSwitch theme =
              new ThemeSwitch(
                  Design.Mode.HOMURA,
                  mode -> {
                    check(mode == Design.Mode.MADOKA, "Theme toggles to light");
                    change[0]++;
                  });
          theme.doClick();
          check(change[0] == 1, "One switch sends one callback");
        });
    JFrame[] frame = {null};
    SelectionView[] cards = {null};
    JPanel[] firstCard = {null};
    int[] collapsed = {0}, listHeight = {0}, otherHeight = {0};
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            Object[][] rows = new Object[12][8];
            for (int i = 0; i < rows.length; i++)
              rows[i] =
                  new Object[] {
                    "Kỳ " + i, "P01", "100", "200", "300", "400", "500", "extra-detail-" + i
                  };
            JTable data =
                new JTable(
                    new DefaultTableModel(
                        rows,
                        new String[] {
                          "Kỳ",
                          "Phòng",
                          "Tổng",
                          "Điện cũ",
                          "Điện mới",
                          "Nước cũ",
                          "Nước mới",
                          "Ghi chú"
                        }));
            cards[0] = new SelectionView(data, java.util.List.of(), 0, "test-expand");
            cards[0].selectLayout("Thẻ");
            frame[0] = new JFrame("Layout regression — synthetic data");
            frame[0].add(cards[0]);
            frame[0].setSize(720, 500);
            frame[0].setVisible(true);
          });
      SwingUtilities.invokeAndWait(
          () -> {
            frame[0].validate();
            JButton expand = find(cards[0], "Xem đầy đủ");
            firstCard[0] = (JPanel) expand.getParent().getParent();
            collapsed[0] = firstCard[0].getHeight();
            JPanel list = (JPanel) firstCard[0].getParent();
            listHeight[0] = list.getPreferredSize().height;
            otherHeight[0] = list.getComponent(2).getHeight();
            check(
                text(firstCard[0], "extra-detail-0") == null,
                "Collapsed fields are absent from layout");
            expand.doClick();
          });
      SwingUtilities.invokeAndWait(
          () -> {
            RepaintManager.currentManager(frame[0]).validateInvalidComponents();
            JTextArea detail = text(firstCard[0], "extra-detail-0");
            check(
                detail != null && detail.isShowing() && detail.getHeight() > 0,
                "First expand click displays detail immediately");
            check(firstCard[0].getHeight() > collapsed[0], "Expanded card grows");
            check(
                firstCard[0].getParent().getComponent(2).getHeight() == otherHeight[0],
                "Expansion does not stretch other cards");
            find(firstCard[0], "Thu gọn").doClick();
          });
      SwingUtilities.invokeAndWait(
          () -> {
            RepaintManager.currentManager(frame[0]).validateInvalidComponents();
            check(
                firstCard[0].getHeight() == collapsed[0],
                "Collapse restores the original card height");
            check(
                firstCard[0].getParent().getPreferredSize().height == listHeight[0],
                "Collapse removes blank space from the list");
            visibleScroll(cards[0]).getVerticalScrollBar().setValue(500);
            cards[0].selectLayout("Bảng");
          });
      SwingUtilities.invokeAndWait(() -> cards[0].selectLayout("Thẻ"));
      SwingUtilities.invokeAndWait(
          () ->
              check(
                  visibleScroll(cards[0]).getViewport().getViewPosition().equals(new Point(0, 0)),
                  "Switching layouts resets scroll to the beginning"));
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (frame[0] != null) frame[0].dispose();
          });
    }
    System.out.println(
        "PASS: " + checks + " calendar, shared layouts, filtering and theme checks.");
  }
}
