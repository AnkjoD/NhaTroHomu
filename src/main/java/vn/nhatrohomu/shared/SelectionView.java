package vn.nhatrohomu.shared;

import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Cards and table share the same sorted model and row actions. */
public final class SelectionView extends JPanel {
  private final JTable table;
  private final List<JButton> actions;
  private final JPanel cards = Design.clear(new BorderLayout());
  private final JPanel views = Design.clear(new CardLayout());
  private final JScrollPane tableScroll, cardScroll;
  private int shown = 60;
  private final int titleColumn;
  private final JComboBox<String> layout = new JComboBox<>(new String[] {"Thẻ", "Bảng"});

  public SelectionView(JTable table, List<JButton> actions, int titleColumn, String key) {
    super(new BorderLayout(0, 10));
    setOpaque(false);
    this.table = table;
    this.actions = List.copyOf(actions);
    this.titleColumn = titleColumn;
    cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
    JPanel toolbar = Design.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    toolbar.add(new JLabel("Cách xem"));
    toolbar.add(layout);
    add(toolbar, BorderLayout.NORTH);
    tableScroll = new JScrollPane(table);
    views.add(tableScroll, "Bảng");
    JPanel list = Design.clear(new BorderLayout());
    list.add(cards, BorderLayout.NORTH);
    cardScroll = new JScrollPane(list);
    cardScroll.getVerticalScrollBar().setUnitIncrement(24);
    cardScroll.setBorder(BorderFactory.createEmptyBorder());
    views.add(cardScroll, "Thẻ");
    add(views);
    java.util.prefs.Preferences preferences =
        java.util.prefs.Preferences.userRoot().node("vn/nhatrohomu/layout");
    String choice = preferences.get(key, "Thẻ");
    layout.setSelectedItem(choice.equals("Bảng") ? "Bảng" : "Thẻ");
    Runnable display =
        () -> {
          if (layout.getSelectedItem().equals("Thẻ")) render();
          ((CardLayout) views.getLayout()).show(views, (String) layout.getSelectedItem());
        };
    layout.addActionListener(
        e -> {
          if (!Boolean.getBoolean("nhatrohomu.ui.test"))
            preferences.put(key, (String) layout.getSelectedItem());
          display.run();
          resetScroll();
        });
    table.getModel().addTableModelListener(e -> render());
    if (table.getRowSorter() != null)
      table
          .getRowSorter()
          .addRowSorterListener(
              e -> {
                shown = 60;
                render();
              });
    table.addPropertyChangeListener(
        "model",
        e -> {
          table.getModel().addTableModelListener(event -> render());
          render();
        });
    table.addPropertyChangeListener(
        "rowSorter",
        e -> {
          if (table.getRowSorter() != null)
            table
                .getRowSorter()
                .addRowSorterListener(
                    event -> {
                      shown = 60;
                      render();
                    });
          render();
        });
    display.run();
  }

  public JTable table() {
    return table;
  }

  public void selectLayout(String value) {
    layout.setSelectedItem(value);
  }

  private void resetScroll() {
    Runnable reset =
        () -> {
          JScrollPane current = layout.getSelectedItem().equals("Thẻ") ? cardScroll : tableScroll;
          current.getViewport().setViewPosition(new Point(0, 0));
          current.getVerticalScrollBar().setValue(0);
          current.getHorizontalScrollBar().setValue(0);
        };
    reset.run();
    SwingUtilities.invokeLater(reset);
  }

  private void refreshCards() {
    cards.revalidate();
    cardScroll.revalidate();
    cardScroll.repaint();
  }

  private String value(int row, int col) {
    Object v = table.getModel().getValueAt(row, col);
    if (v == null) return "";
    if (v instanceof java.math.BigDecimal money) {
      String header = table.getModel().getColumnName(col).toLowerCase(java.util.Locale.ROOT);
      return header.contains("tiền")
              || header.contains("tổng")
              || header.contains("thu")
              || header.contains("thiếu")
          ? Ui.money(money)
          : Ui.n(money);
    }
    return v.toString();
  }

  private void run(int model, JButton action) {
    int view = table.convertRowIndexToView(model);
    if (view < 0) return;
    table.setRowSelectionInterval(view, view);
    if (action.isEnabled()) action.doClick();
  }

  private int priority(String header) {
    String h = header.toLowerCase(java.util.Locale.ROOT);
    if (h.contains("trạng") || h.contains("thanh toán")) return 0;
    if (h.equals("phòng") || h.contains("phòng đang")) return 1;
    if (h.equals("kỳ")) return 2;
    if (h.contains("thiếu")) return 3;
    if (h.contains("tổng")) return 4;
    if (h.contains("người thuê") || h.contains("họ tên")) return 2;
    if (h.contains("điện thoại")) return 6;
    if (h.contains("zalo")) return 7;
    if (h.contains("tiền") || h.contains("cọc")) return 8;
    if (h.equals("mã") || h.equals("hợp đồng") || h.equals("hóa đơn")) return 100;
    return 10;
  }

  private void render() {
    if (!layout.getSelectedItem().equals("Thẻ")) return;
    cards.removeAll();
    int count = Math.min(shown, table.getRowCount());
    if (count == 0) cards.add(Design.label("Chưa có dữ liệu phù hợp.", 14, Design.MUTED, false));
    for (int view = 0; view < count; view++) {
      int row = table.convertRowIndexToModel(view);
      JPanel card = Design.card(Design.SURFACE, true);
      card.setAlignmentX(Component.LEFT_ALIGNMENT);
      card.setLayout(new BorderLayout(0, 12));
      card.add(Design.label(value(row, titleColumn), 19, Design.INK, true), BorderLayout.NORTH);
      JPanel fields = Design.clear(new GridLayout(0, 2, 10, 6));
      JPanel extra = Design.clear(new GridLayout(0, 2, 10, 6));
      int included = 0;
      java.util.List<Integer> columns =
          java.util.stream.IntStream.range(0, table.getColumnCount())
              .boxed()
              .sorted(
                  java.util.Comparator.comparingInt(
                      col -> priority(table.getModel().getColumnName(col))))
              .toList();
      for (int col : columns) {
        if (col == titleColumn) continue;
        String text = value(row, col);
        if (text.isBlank()) continue;
        JPanel field = Design.clear(new BorderLayout(0, 3));
        field.add(
            Design.label(table.getModel().getColumnName(col), 10, Design.MUTED, false),
            BorderLayout.NORTH);
        JTextArea content = new JTextArea(text);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setOpaque(false);
        content.setRows(Math.min(3, 1 + text.length() / 65));
        field.add(content);
        (included++ < 4 ? fields : extra).add(field);
      }
      JPanel facts = Design.clear(new BorderLayout(0, 8));
      facts.add(fields, BorderLayout.NORTH);
      card.add(facts);
      JPanel buttons = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
      for (int i = 0; i < Math.min(2, actions.size()); i++) {
        JButton original = actions.get(i);
        JButton action = Ui.button(original.getText(), () -> run(row, original));
        action.setEnabled(original.isEnabled());
        if (i == 0) Design.primary(action);
        buttons.add(action);
      }
      if (extra.getComponentCount() > 0) {
        JButton expand = new JButton("Xem đầy đủ");
        expand.addActionListener(
            e -> {
              if (extra.getParent() == facts) {
                facts.remove(extra);
                expand.setText("Xem đầy đủ");
              } else {
                facts.add(extra, BorderLayout.CENTER);
                expand.setText("Thu gọn");
              }
              facts.revalidate();
              refreshCards();
            });
        buttons.add(expand);
      }
      if (actions.size() > 2) {
        JPopupMenu menu = new JPopupMenu();
        for (JButton original : actions) {
          JMenuItem item = new JMenuItem(original.getText());
          item.addActionListener(e -> run(row, original));
          menu.add(item);
        }
        JButton more = Ui.button("•••", () -> {});
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        buttons.add(more);
      }
      JPopupMenu menu = new JPopupMenu();
      for (JButton original : actions) {
        JMenuItem item = new JMenuItem(original.getText());
        item.addActionListener(e -> run(row, original));
        menu.add(item);
      }
      if (!actions.isEmpty()) Ui.contextMenu(card, menu);
      card.add(buttons, BorderLayout.SOUTH);
      if (cards.getComponentCount() > 0) cards.add(Box.createVerticalStrut(12));
      cards.add(card);
    }
    if (count < table.getRowCount())
      cards.add(
          Ui.button(
              "Xem thêm (" + count + " / " + table.getRowCount() + ")",
              () -> {
                shown += 60;
                render();
              }));
    refreshCards();
  }
}
