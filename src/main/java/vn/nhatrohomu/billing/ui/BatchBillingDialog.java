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

  private record Assigned(Entry entry, java.nio.file.Path path) {}

  private record Loaded(Entry entry, java.awt.image.BufferedImage image) {}

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
              if (entries.stream().anyMatch(e -> e.dirty && !e.stored)
                  && !Ui.confirm(this, "Đổi tháng sẽ bỏ phần đang nhập chưa lưu. Tiếp tục?"))
                return;
              load();
            }));
    JPanel toolbars = Design.clear(new GridLayout(0, 1, 0, 8));
    toolbars.add(top);
    JPanel photoActions = Design.clear(new FlowLayout(FlowLayout.LEFT));
    photoActions.add(Ui.button("Chọn nhiều ảnh điện", () -> assignPhotos(true)));
    photoActions.add(Ui.button("Chọn nhiều ảnh nước", () -> assignPhotos(false)));
    toolbars.add(photoActions);
    root.add(toolbars, BorderLayout.NORTH);
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
    if (entries.stream()
        .anyMatch(
            e ->
                e.electricityPhoto != null
                    && (e.electricityPhoto.working || e.waterPhoto.working))) {
      Ui.error(this, new IllegalArgumentException("Chờ nhận diện xong trước khi đổi tháng."));
      return;
    }
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
          for (Room room :
              repository.rooms().stream()
                  .sorted(
                      (a, b) -> vn.nhatrohomu.integration.PhotoOrder.compare(a.name(), b.name()))
                  .toList()) {
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
      for (Entry entry : selected) {
        if (entry.electricityPhoto.working || entry.waterPhoto.working)
          throw new IllegalArgumentException("Chờ nhận diện ảnh xong trước khi lưu.");
        bills.add(entry.calculate());
      }
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
              Entry entry = selected.get(i);
              repository.saveBill(
                  bills.get(i),
                  entry.electricityPhoto.photo == null
                      ? null
                      : vn.nhatrohomu.integration.OcrService.png(entry.electricityPhoto.photo),
                  entry.waterPhoto.photo == null
                      ? null
                      : vn.nhatrohomu.integration.OcrService.png(entry.waterPhoto.photo));
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
              entry.photoViews.setVisible(false);
              entry.showPhotos.setEnabled(false);
              entry.electricityPhoto.clearPhoto();
              entry.waterPhoto.clearPhoto();
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

  private void assignPhotos(boolean electric) {
    List<Entry> targets =
        entries.stream()
            .filter(
                e ->
                    e.plan.draft() != null
                        && !e.stored
                        && (electric || e.plan.room().waterMode().equals("METER")))
            .toList();
    if (targets.isEmpty()) {
      Ui.error(
          this, new IllegalArgumentException("Không có phòng chưa lập phù hợp trong tháng này."));
      return;
    }
    JFileChooser picker = new JFileChooser();
    picker.setMultiSelectionEnabled(true);
    picker.setFileFilter(
        new javax.swing.filechooser.FileNameExtensionFilter("Ảnh PNG, JPG", "png", "jpg", "jpeg"));
    if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    java.io.File[] files = picker.getSelectedFiles();
    java.util.Arrays.sort(
        files, (a, b) -> vn.nhatrohomu.integration.PhotoOrder.compare(a.getName(), b.getName()));
    JPanel mapping = Ui.form();
    List<JComboBox<java.io.File>> choices = new ArrayList<>();
    for (int i = 0; i < targets.size(); i++) {
      JComboBox<java.io.File> choice = new JComboBox<>();
      choice.addItem(null);
      for (java.io.File file : files) choice.addItem(file);
      choice.setRenderer(
          new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
              putClientProperty("html.disable", true);
              return super.getListCellRendererComponent(
                  list,
                  value == null ? "Không gán ảnh" : ((java.io.File) value).getName(),
                  index,
                  selected,
                  focus);
            }
          });
      if (i < files.length) choice.setSelectedIndex(i + 1);
      choices.add(choice);
      Ui.field(mapping, i, targets.get(i).plan.room().name(), choice);
    }
    JPanel content = Design.clear(new BorderLayout(8, 8));
    content.add(
        new JLabel(
            "Gợi ý theo tên file. Chọn lại ảnh cho đúng phòng; ảnh sẽ hiện để bạn kiểm tra."),
        BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(mapping);
    scroll.setPreferredSize(new Dimension(650, Math.min(420, targets.size() * 60 + 30)));
    content.add(scroll);
    if (JOptionPane.showConfirmDialog(
            this,
            content,
            electric ? "Gán ảnh điện cho phòng" : "Gán ảnh nước cho phòng",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    List<Assigned> assigned = new ArrayList<>();
    java.util.Set<java.nio.file.Path> unique = new java.util.HashSet<>();
    for (int i = 0; i < choices.size(); i++) {
      java.io.File file = (java.io.File) choices.get(i).getSelectedItem();
      if (file == null) continue;
      if (!unique.add(file.toPath())) {
        Ui.error(
            this,
            new IllegalArgumentException(
                "Một ảnh đang được gán cho nhiều phòng. Chọn lại để tránh nhầm."));
        return;
      }
      Entry target = targets.get(i);
      if (target.electricityPhoto.working || target.waterPhoto.working) {
        Ui.error(this, new IllegalArgumentException("Chờ nhận diện xong trước khi đổi ảnh."));
        return;
      }
      assigned.add(new Assigned(target, file.toPath()));
    }
    if (assigned.isEmpty()) return;
    if (assigned.stream()
            .anyMatch(
                a ->
                    (electric ? a.entry().electricityPhoto.photo : a.entry().waterPhoto.photo)
                        != null)
        && !Ui.confirm(
            this,
            "Thay ảnh đang có ở các phòng đã chọn? Chỉ số đang nhập được giữ để bạn kiểm tra lại."))
      return;
    final long existingPixels = photoPixels();
    Ui.background(
        this,
        () -> {
          List<Loaded> images = new ArrayList<>();
          long pixels = existingPixels;
          for (Assigned a : assigned) {
            var image = vn.nhatrohomu.integration.OcrService.load(a.path());
            pixels += pixels(image);
            requireCapacity(pixels);
            images.add(new Loaded(a.entry(), image));
          }
          return images;
        },
        images -> {
          for (Loaded image : images) {
            Entry entry = image.entry();
            (electric ? entry.electricityPhoto : entry.waterPhoto).accept(image.image());
            entry.showPhotos.setSelected(true);
            entry.photoViews.setVisible(true);
            entry.photoViews.setSelectedIndex(electric ? 0 : 1);
            entry.card.revalidate();
          }
          cards.revalidate();
          cards.repaint();
          progress.setText(
              "Đã gán "
                  + images.size()
                  + " ảnh. Đối chiếu đúng phòng, bấm Nhận diện rồi kiểm tra chỉ số trước khi lưu.");
        });
  }

  private static long pixels(java.awt.image.BufferedImage image) {
    return image == null ? 0 : (long) image.getWidth() * image.getHeight();
  }

  private long photoPixels() {
    long result = 0;
    for (Entry entry : entries)
      if (entry.electricityPhoto != null)
        result += pixels(entry.electricityPhoto.photo) + pixels(entry.waterPhoto.photo);
    return result;
  }

  private static void requireCapacity(long pixels) {
    if (pixels > 32_000_000)
      throw new IllegalArgumentException(
          "Nhiều ảnh đang mở cùng lúc. Lập theo nhóm phòng nhỏ hơn hoặc giảm kích thước ảnh để giữ"
              + " ứng dụng nhẹ.");
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
    boolean stored, dirty;
    MeterPhotoPanel electricityPhoto, waterPhoto;
    final JTabbedPane photoViews = new JTabbedPane();
    final JCheckBox showPhotos = new JCheckBox("Ảnh đồng hồ · chọn, dán hoặc nhận diện");

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
      electricityPhoto =
          new MeterPhotoPanel(
              BatchBillingDialog.this, draft.room(), "Ảnh điện", electricity, () -> dirty = true);
      waterPhoto =
          new MeterPhotoPanel(
              BatchBillingDialog.this, draft.room(), "Ảnh nước", water, () -> dirty = true);
      electricityPhoto.beforeAccept(
          image -> requireCapacity(photoPixels() - pixels(electricityPhoto.photo) + pixels(image)));
      waterPhoto.beforeAccept(
          image -> requireCapacity(photoPixels() - pixels(waterPhoto.photo) + pixels(image)));
      photoViews.addTab("Điện", electricityPhoto);
      if (draft.room().waterMode().equals("METER")) photoViews.addTab("Nước", waterPhoto);
      photoViews.setVisible(false);
      showPhotos.addActionListener(
          e -> {
            photoViews.setVisible(showPhotos.isSelected());
            card.revalidate();
            cards.revalidate();
          });
      JPanel center = Design.clear(new BorderLayout(0, 10));
      center.add(fields, BorderLayout.NORTH);
      JPanel photos = Design.clear(new BorderLayout(0, 8));
      photos.add(showPhotos, BorderLayout.NORTH);
      photos.add(photoViews);
      center.add(photos);
      card.add(center);
      card.add(status, BorderLayout.SOUTH);
      DocumentListener changes =
          new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
              dirty = true;
              preview();
            }

            public void removeUpdate(DocumentEvent e) {
              dirty = true;
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
