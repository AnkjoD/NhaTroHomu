package vn.nhatrohomu.shared;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Compact two-state control; the application persists the chosen palette. */
public final class ThemeSwitch extends JToggleButton {
  private final Consumer<Design.Mode> change;

  public ThemeSwitch(Design.Mode mode, Consumer<Design.Mode> change) {
    this.change = change;
    setSelected(mode == Design.Mode.HOMURA);
    setPreferredSize(new Dimension(216, 40));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setToolTipText("Homura tối / Madoka sáng");
    getAccessibleContext().setAccessibleName("Chuyển giao diện Homura / Madoka");
    addActionListener(e -> choose(isSelected() ? Design.Mode.HOMURA : Design.Mode.MADOKA));
  }

  public void choose(Design.Mode mode) {
    setSelected(mode == Design.Mode.HOMURA);
    change.accept(mode);
  }

  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight(), half = w / 2;
    g.setColor(Design.SURFACE);
    g.fillRoundRect(0, 0, w, h, h, h);
    g.setColor(Design.LINE);
    g.drawRoundRect(0, 0, w - 1, h - 1, h, h);
    int x = isSelected() ? 3 : half;
    g.setColor(Design.MINT);
    g.fillRoundRect(x, 3, half - 3, h - 6, h - 6, h - 6);
    g.setFont(getFont().deriveFont(12f));
    for (int i = 0; i < 2; i++) {
      boolean active = (i == 0) == isSelected();
      Color color = active ? Ui.GREEN : Design.MUTED;
      int base = i * half;
      Design.icon(i == 0 ? "moon" : "sun", color).paintIcon(this, g, base + 12, (h - 20) / 2);
      g.setColor(color);
      g.drawString(i == 0 ? "Homura" : "Madoka", base + 38, h / 2 + 4);
    }
    if (hasFocus()) {
      g.setColor(Ui.GREEN);
      g.drawRoundRect(1, 1, w - 3, h - 3, h, h);
    }
    g.dispose();
  }
}
