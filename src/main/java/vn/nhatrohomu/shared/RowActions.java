package vn.nhatrohomu.shared;

import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/** One action definition for selection toolbar, mouse menu and keyboard. */
public final class RowActions {
  private RowActions() {}

  public static void install(JTable table, List<JButton> buttons) {
    install(table, buttons, true);
  }

  public static void install(JTable table, List<JButton> buttons, boolean openDefault) {
    Runnable selection =
        () -> {
          boolean selected = table.getSelectedRow() >= 0;
          for (JButton b : buttons) b.setVisible(selected);
        };
    table.getSelectionModel().addListSelectionListener(e -> selection.run());
    selection.run();
    JPopupMenu popup = new JPopupMenu();
    for (JButton b : buttons) {
      JMenuItem item = new JMenuItem(b.getText(), b.getIcon());
      item.addActionListener(
          e -> {
            if (table.getSelectedRow() >= 0 && b.isEnabled()) b.doClick();
          });
      popup.add(item);
    }
    table.addMouseListener(
        new MouseAdapter() {
          private void menu(MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            int row = table.rowAtPoint(e.getPoint());
            if (row < 0) {
              table.clearSelection();
              return;
            }
            table.setRowSelectionInterval(row, row);
            for (int i = 0; i < buttons.size(); i++)
              popup.getComponent(i).setEnabled(buttons.get(i).isEnabled());
            popup.show(table, e.getX(), e.getY());
          }

          public void mousePressed(MouseEvent e) {
            if (table.rowAtPoint(e.getPoint()) < 0) table.clearSelection();
            menu(e);
          }

          public void mouseReleased(MouseEvent e) {
            menu(e);
          }

          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)
                && e.getClickCount() == 2
                && table.rowAtPoint(e.getPoint()) >= 0) open();
          }

          private void open() {
            if (openDefault
                && !buttons.isEmpty()
                && table.getSelectedRow() >= 0
                && buttons.getFirst().isEnabled()) buttons.getFirst().doClick();
          }
        });
    if (openDefault) {
      table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open-row");
      table
          .getActionMap()
          .put(
              "open-row",
              new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                  if (openDefault
                      && !buttons.isEmpty()
                      && table.getSelectedRow() >= 0
                      && buttons.getFirst().isEnabled()) buttons.getFirst().doClick();
                }
              });
    }
  }
}
