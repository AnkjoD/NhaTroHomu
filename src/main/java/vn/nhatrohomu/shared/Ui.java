package vn.nhatrohomu.shared;

import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;

/** Thành phần giao diện dùng lại và cầu nối giữa tác vụ nền với EDT. */
public final class Ui {
  public static Color GREEN = new Color(20, 122, 96), BACKGROUND = new Color(245, 248, 247);

  public static String money(BigDecimal value) {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ";
  }

  public static String n(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  public static JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    b.addActionListener(e -> action.run());
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  public static JButton refreshButton(Runnable action) {
    JButton button = button("", action);
    button.setIcon(Design.icon("refresh", Design.INK));
    button.setToolTipText("Cập nhật dữ liệu");
    button.getAccessibleContext().setAccessibleName("Cập nhật dữ liệu");
    return button;
  }

  public static void contextMenu(Component component, JPopupMenu menu) {
    component.addMouseListener(
        new java.awt.event.MouseAdapter() {
          private void show(java.awt.event.MouseEvent e) {
            if (e.isPopupTrigger()) menu.show(e.getComponent(), e.getX(), e.getY());
          }

          public void mousePressed(java.awt.event.MouseEvent e) {
            show(e);
          }

          public void mouseReleased(java.awt.event.MouseEvent e) {
            show(e);
          }
        });
    if (component instanceof Container parent)
      for (Component child : parent.getComponents()) contextMenu(child, menu);
  }

  public static JPanel column() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    return p;
  }

  public static JPanel form() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return p;
  }

  public static void field(JPanel form, int row, String label, JComponent input) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row;
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(7, 4, 7, 14);
    form.add(new JLabel(label), c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(7, 4, 7, 4);
    form.add(input, c);
  }

  public static void error(Component owner, Throwable ex) {
    while (ex.getCause() != null
        && (ex instanceof java.util.concurrent.ExecutionException
            || ex instanceof java.lang.reflect.InvocationTargetException)) ex = ex.getCause();
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    if (message.contains("UNIQUE constraint failed: rooms.name"))
      message = "Tên phòng đã tồn tại, kể cả phòng đã ngừng sử dụng. Hãy chọn tên khác.";
    JOptionPane.showMessageDialog(owner, message, "Chưa hoàn thành", JOptionPane.WARNING_MESSAGE);
  }

  public static boolean confirm(Component owner, String text) {
    return JOptionPane.showConfirmDialog(
            owner, text, "Xác nhận", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  public static <T> void background(Window owner, Callable<T> work, Consumer<T> done) {
    owner.setEnabled(false);
    owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<T, Void>() {
      protected T doInBackground() throws Exception {
        return work.call();
      }

      protected void done() {
        owner.setEnabled(true);
        owner.setCursor(Cursor.getDefaultCursor());
        owner.toFront();
        try {
          done.accept(get());
        } catch (Exception ex) {
          error(owner, ex);
        }
      }
    }.execute();
  }
}
