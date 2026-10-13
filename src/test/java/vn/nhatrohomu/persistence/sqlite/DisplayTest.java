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

  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          System.setProperty("nhatrohomu.ui.test", "true");
          Design.setup();
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
    System.out.println(
        "PASS: " + checks + " calendar, shared layouts, filtering and theme checks.");
  }
}
