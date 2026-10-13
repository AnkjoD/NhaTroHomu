package vn.nhatrohomu.shared;

import java.awt.*;
import java.time.*;
import javax.swing.*;

/** Keep typed ISO values while offering a local calendar, without changing business dates. */
public final class DatePicker {
  private DatePicker() {}

  public static JPanel field(JTextField text, boolean monthOnly) {
    JPanel panel = Design.clear(new BorderLayout(6, 0));
    panel.add(text);
    JButton button =
        Ui.button(
            "",
            () -> {
              JPopupMenu menu = calendar(text, monthOnly);
              menu.show(text, 0, text.getHeight());
            });
    button.setIcon(Design.icon("calendar", Design.INK));
    button.setToolTipText(monthOnly ? "Chọn tháng / năm" : "Chọn ngày trên lịch");
    button.getAccessibleContext().setAccessibleName(button.getToolTipText());
    panel.add(button, BorderLayout.EAST);
    return panel;
  }

  public static java.util.List<LocalDate> days(YearMonth month) {
    java.util.List<LocalDate> result = new java.util.ArrayList<>();
    for (int day = 1; day <= month.lengthOfMonth(); day++) result.add(month.atDay(day));
    return java.util.List.copyOf(result);
  }

  public static JPopupMenu calendar(JTextField target, boolean monthOnly) {
    YearMonth initial = YearMonth.now();
    LocalDate selected = null;
    try {
      if (monthOnly) initial = DataRules.period(target.getText().trim());
      else {
        selected = DataRules.date(target.getText().trim());
        initial = YearMonth.from(selected);
      }
    } catch (IllegalArgumentException ignored) {
    }
    JPopupMenu menu = new JPopupMenu();
    JPanel content = new JPanel(new BorderLayout(6, 10));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    JComboBox<String> month = new JComboBox<>();
    for (int m = 1; m <= 12; m++) month.addItem("Tháng " + m);
    month.setSelectedIndex(initial.getMonthValue() - 1);
    JSpinner year = new JSpinner(new SpinnerNumberModel(initial.getYear(), 1900, 9999, 1));
    year.setEditor(new JSpinner.NumberEditor(year, "0"));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    controls.add(month);
    controls.add(year);
    content.add(controls, BorderLayout.NORTH);
    JPanel grid = new JPanel(new GridLayout(7, 7, 3, 3));
    content.add(grid);
    LocalDate chosen = selected;
    Runnable render =
        () -> {
          grid.removeAll();
          YearMonth current = YearMonth.of((Integer) year.getValue(), month.getSelectedIndex() + 1);
          if (monthOnly) {
            grid.setVisible(false);
          } else {
            for (String label : new String[] {"T2", "T3", "T4", "T5", "T6", "T7", "CN"})
              grid.add(new JLabel(label, SwingConstants.CENTER));
            int offset = current.atDay(1).getDayOfWeek().getValue() - 1;
            for (int cell = 0; cell < 42; cell++) {
              int day = cell - offset + 1;
              if (day < 1 || day > current.lengthOfMonth()) {
                grid.add(new JLabel());
                continue;
              }
              LocalDate date = current.atDay(day);
              JButton pick =
                  Ui.button(
                      Integer.toString(day),
                      () -> {
                        target.setText(date.toString());
                        menu.setVisible(false);
                      });
              pick.setMargin(new Insets(5, 5, 5, 5));
              if (date.equals(chosen)) Design.primary(pick);
              if (date.equals(LocalDate.now())) pick.setToolTipText("Hôm nay");
              grid.add(pick);
            }
          }
          content.revalidate();
          menu.pack();
        };
    month.addActionListener(e -> render.run());
    year.addChangeListener(e -> render.run());
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    bottom.add(
        Ui.button(
            monthOnly ? "Tháng này" : "Hôm nay",
            () -> {
              target.setText(monthOnly ? YearMonth.now().toString() : LocalDate.now().toString());
              menu.setVisible(false);
            }));
    if (monthOnly)
      bottom.add(
          Ui.button(
              "Chọn kỳ",
              () -> {
                target.setText(
                    YearMonth.of((Integer) year.getValue(), month.getSelectedIndex() + 1)
                        .toString());
                menu.setVisible(false);
              }));
    bottom.add(
        Ui.button(
            "Xóa",
            () -> {
              target.setText("");
              menu.setVisible(false);
            }));
    content.add(bottom, BorderLayout.SOUTH);
    menu.add(content);
    render.run();
    return menu;
  }
}
