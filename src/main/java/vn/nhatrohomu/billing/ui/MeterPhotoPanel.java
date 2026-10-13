package vn.nhatrohomu.billing.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import vn.nhatrohomu.integration.*;
import vn.nhatrohomu.shared.*;
import vn.nhatrohomu.shared.Models.Room;

public class MeterPhotoPanel extends JPanel {
  BufferedImage photo;
  boolean working;
  private final ImagePanel preview = new ImagePanel();
  private final Window owner;
  private final Runnable changed;
  private final String cropKey;
  private final JCheckBox remember;
  private final JLabel status;
  private java.util.function.Consumer<BufferedImage> beforeAccept = image -> {};

  public void beforeAccept(java.util.function.Consumer<BufferedImage> check) {
    beforeAccept = check;
  }

  public void clearPhoto() {
    photo = null;
    preview.setImage(null);
    changed.run();
  }

  public MeterPhotoPanel(
      Window owner, Room room, String name, JTextField target, Runnable changed) {
    super(new BorderLayout(8, 8));
    this.owner = owner;
    this.changed = changed;
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
    add(preview, BorderLayout.CENTER);
    cropKey = "crop." + room.id() + (name.equals("Ảnh điện") ? ".electricity" : ".water");
    remember =
        new JCheckBox(
            "Nhớ khung cho phòng này",
            Boolean.parseBoolean(
                vn.nhatrohomu.shared.LocalUiSettings.get(cropKey + ".enabled", "true")));
    java.util.function.Consumer<ImagePanel.Region> store =
        bounds -> {
          changed.run();
          if (!remember.isSelected()) return;
          try {
            vn.nhatrohomu.shared.LocalUiSettings.put(
                cropKey,
                bounds == null
                    ? null
                    : bounds.x() + "," + bounds.y() + "," + bounds.width() + "," + bounds.height());
          } catch (Exception ex) {
            Ui.error(owner, ex);
          }
        };
    preview.onRegionChanged(store);
    remember.addActionListener(
        e -> {
          try {
            vn.nhatrohomu.shared.LocalUiSettings.put(
                cropKey + ".enabled", Boolean.toString(remember.isSelected()));
            if (!remember.isSelected()) vn.nhatrohomu.shared.LocalUiSettings.put(cropKey, null);
            else store.accept(preview.region());
          } catch (Exception ex) {
            Ui.error(owner, ex);
          }
        });
    status =
        Design.label(
            "Kéo chuột khoanh vùng số. OCR có thể sai, hãy kiểm tra lại.", 10, Design.MUTED, false);
    JButton choose =
        Ui.button(
            "Chọn " + name.toLowerCase(),
            () -> {
              JFileChooser picker = new JFileChooser();
              picker.setFileFilter(
                  new FileNameExtensionFilter("Ảnh PNG, JPG", "png", "jpg", "jpeg"));
              if (picker.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                Ui.background(
                    owner, () -> OcrService.load(picker.getSelectedFile().toPath()), this::accept);
              }
            });
    JButton recognize = Design.primary(new JButton("Nhận diện chỉ số"));
    JComboBox<OcrService.Engine> engine = new JComboBox<>(OcrService.Engine.values());
    engine.setSelectedItem(
        OcrService.rapidAvailable() ? OcrService.Engine.RAPID : OcrService.Engine.WINDOWS);
    engine.setToolTipText(
        "RapidOCR cần gói tùy chọn. Khoanh riêng dãy số; luôn kiểm tra trước khi lưu.");
    recognize.addActionListener(
        event -> {
          BufferedImage selected = preview.selected();
          if (selected == null) {
            Ui.error(owner, new IllegalArgumentException("Chọn ảnh trước."));
            return;
          }
          working = true;
          OcrService.Engine selectedEngine = (OcrService.Engine) engine.getSelectedItem();
          engine.setEnabled(false);
          recognize.setEnabled(false);
          choose.setEnabled(false);
          status.setText("Đang nhận diện…");
          new SwingWorker<OcrService.Result, Void>() {
            protected OcrService.Result doInBackground() throws Exception {
              return new OcrService().recognize(selected, selectedEngine);
            }

            protected void done() {
              working = false;
              engine.setEnabled(true);
              recognize.setEnabled(true);
              choose.setEnabled(true);
              if (!owner.isDisplayable()) return;
              try {
                OcrService.Result result = get();
                status.setText("Đã đọc ảnh. Hãy kiểm tra và chọn số đúng.");
                if (result.candidates().isEmpty()) {
                  Ui.error(
                      owner,
                      new IllegalArgumentException(
                          "Không tìm thấy số. Cắt ảnh sát hơn hoặc nhập tay."));
                  return;
                }
                JComboBox<String> candidates =
                    new JComboBox<>(result.candidates().toArray(String[]::new));
                JTextArea raw = new JTextArea(result.text(), 5, 36);
                raw.setEditable(false);
                raw.setLineWrap(true);
                JPanel panel = Ui.column();
                panel.add(new JLabel("Số tìm thấy (có thể là số seri/đơn vị, cần kiểm tra):"));
                panel.add(candidates);
                panel.add(new JScrollPane(raw));
                if (JOptionPane.showConfirmDialog(
                        owner, panel, "Chọn chỉ số để điền", JOptionPane.OK_CANCEL_OPTION)
                    == JOptionPane.OK_OPTION) target.setText((String) candidates.getSelectedItem());
              } catch (Exception ex) {
                status.setText("Không nhận diện được; vẫn có thể nhập tay.");
                Ui.error(owner, ex);
              }
            }
          }.execute();
        });
    JPanel buttons = Design.clear(new GridLayout(0, 2, 8, 8));
    buttons.add(choose);
    Runnable paste =
        () -> {
          if (working) return;
          Ui.background(owner, ClipboardImage::read, this::accept);
        };
    buttons.add(Ui.button("Dán ảnh", paste));
    preview.setFocusable(true);
    preview.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mousePressed(java.awt.event.MouseEvent e) {
            preview.requestFocusInWindow();
          }
        });
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke("ctrl V"), "paste-image");
    getActionMap()
        .put(
            "paste-image",
            new AbstractAction() {
              public void actionPerformed(java.awt.event.ActionEvent e) {
                paste.run();
              }
            });
    buttons.add(
        Ui.button(
            "Ảnh lớn / khoanh số",
            () -> {
              if (photo == null) {
                Ui.error(owner, new IllegalArgumentException("Chọn ảnh trước."));
                return;
              }
              ImagePanel.Region region = ImagePanel.chooseRegionBounds(owner, photo);
              if (region != null) {
                preview.setImage(photo);
                preview.setRegion(region);
                store.accept(region);
                changed.run();
                status.setText("Đã chọn vùng OCR; ảnh gốc vẫn được giữ khi lưu hóa đơn.");
              }
            }));
    buttons.add(recognize);
    JPanel controls = Ui.column();
    controls.setOpaque(false);
    JPanel engineRow = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
    engineRow.add(new JLabel("Công cụ đọc ảnh:"));
    engineRow.add(engine);
    controls.add(engineRow);
    JPanel cropRow = Design.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
    cropRow.add(remember);
    cropRow.add(
        Ui.button(
            "Bỏ khung",
            () -> {
              preview.setRegion(null);
              store.accept(null);
            }));
    controls.add(cropRow);
    controls.add(buttons);
    controls.add(status);
    add(controls, BorderLayout.SOUTH);
  }

  public void accept(BufferedImage image) {
    if (working)
      throw new IllegalArgumentException("OCR đang chạy. Chờ kết quả trước khi đổi ảnh.");
    beforeAccept.accept(image);
    photo = image;
    preview.setImage(image);
    if (remember.isSelected()) {
      String value = LocalUiSettings.get(cropKey, "");
      try {
        String[] coordinates = value.split(",");
        if (coordinates.length == 4)
          preview.setRegion(
              new ImagePanel.Region(
                  Double.parseDouble(coordinates[0]),
                  Double.parseDouble(coordinates[1]),
                  Double.parseDouble(coordinates[2]),
                  Double.parseDouble(coordinates[3])));
      } catch (IllegalArgumentException ignored) {
      }
    }
    changed.run();
    status.setText("Đã nhận ảnh. Khoanh vùng rồi bấm Nhận diện chỉ số.");
  }
}
