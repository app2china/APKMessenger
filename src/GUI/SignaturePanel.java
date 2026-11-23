package GUI;

import apkinfo.SigningCertificate;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

final class SignaturePanel extends JPanel {
    private final String version;
    private final BiConsumer<String, String> copy;
    private final JPanel toolbar = Ui.panel(new BorderLayout(8, 0));
    private final JLabel certificateLabel = Ui.label("", 14, true, Ui.TEXT);
    private final JComboBox<String> selector = Ui.combo();
    private final JButton copyDetails = Ui.button("复制详情", false);
    private final JButton copyPem = Ui.button("复制 PEM", false);
    private final JButton copyDer = Ui.button("复制 DER", false);
    private final JPanel content = Ui.panel(new CardLayout());
    private final JLabel message = Ui.label("", 15, false, Ui.MUTED);
    private final JScrollPane details = Ui.scroll(null);
    private List<SigningCertificate> certificates = Collections.emptyList();
    private SigningCertificate currentCertificate;

    SignaturePanel(String version, BiConsumer<String, String> copy) {
        this.version = version;
        this.copy = copy;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        selector.getAccessibleContext().setAccessibleName("选择证书");
        selector.addActionListener(event -> showSelected());
        JPanel selection = Ui.panel(new GridBagLayout());
        selection.add(certificateLabel);
        selection.add(selector);
        toolbar.add(selection, BorderLayout.WEST);

        copyDetails.addActionListener(event -> copy.accept(version + " / " + certificateName() + "\n\n"
                + currentCertificate.getReport(), "证书详情"));
        copyPem.addActionListener(event -> copy.accept(currentCertificate.getPem(), "PEM 证书"));
        copyDer.setToolTipText("复制原始证书的 DER 十六进制");
        copyDer.addActionListener(event -> copy.accept(currentCertificate.getDerHex(), "DER 十六进制"));
        JPanel actions = Ui.panel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        actions.add(copyDetails);
        actions.add(copyPem);
        actions.add(copyDer);
        toolbar.add(actions, BorderLayout.EAST);

        JPanel empty = Ui.card(Ui.SURFACE, 18);
        empty.setLayout(new GridBagLayout());
        empty.setBorder(new EmptyBorder(24, 24, 24, 24));
        empty.add(message);
        content.add(empty, "message");
        details.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(details, "details");
        add(toolbar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setCertificates(Collections.emptyList(), "");
    }

    void setCertificates(List<SigningCertificate> certificates, String status) {
        this.certificates = certificates;
        DefaultComboBoxModel<String> options = new DefaultComboBoxModel<>();
        for (int index = 0; index < certificates.size(); index++) {
            options.addElement("证书 " + (index + 1) + " / " + certificates.size());
        }
        selector.setModel(options);
        selector.setVisible(certificates.size() > 1);
        certificateLabel.setVisible(certificates.size() == 1);
        certificateLabel.setText("证书 1");
        toolbar.setVisible(!certificates.isEmpty());
        if (certificates.isEmpty()) {
            currentCertificate = null;
            details.setViewportView(null);
            showMessage(status, Ui.MUTED);
        } else {
            showSelected();
        }
    }

    private String certificateName() {
        return "证书 " + (selector.getSelectedIndex() + 1);
    }

    private void showSelected() {
        int index = selector.getSelectedIndex();
        if (index < 0 || index >= certificates.size()) {
            return;
        }
        currentCertificate = certificates.get(index);
        boolean parsed = currentCertificate.isParsed();
        copyDetails.setVisible(parsed);
        copyPem.setVisible(parsed);
        if (!parsed) {
            details.setViewportView(null);
            showMessage("这张证书无法解析为 X.509，可复制原始 DER 数据。", Ui.ALERT);
            return;
        }

        DetailColumn sections = new DetailColumn();
        sections.add(identity(currentCertificate.getIdentity()));
        sections.add(Box.createVerticalStrut(12));
        JPanel fingerprints = section("证书指纹");
        currentCertificate.getFingerprints().forEach((name, value) -> {
            JPanel row = row(name, value, true, name.equals("SHA-256") ? Ui.ACCENT : Ui.TEXT);
            addCopyAction(row, value, name + " 指纹");
            fingerprints.add(row);
        });
        sections.add(fingerprints);
        sections.add(Box.createVerticalStrut(12));
        sections.add(fields("证书签名算法", currentCertificate.getSignature()));
        sections.add(Box.createVerticalStrut(12));
        sections.add(fields("公钥", currentCertificate.getPublicKey()));
        details.setViewportView(sections);
        ((CardLayout) content.getLayout()).show(content, "details");
        details.getViewport().setViewPosition(new Point());
        SwingUtilities.invokeLater(() -> {
            if (details.getViewport().getView() == sections) {
                details.getViewport().setViewPosition(new Point());
            }
        });
    }

    private void showMessage(String text, Ui.Tone color) {
        message.setText(text);
        Ui.foreground(message, color);
        ((CardLayout) content.getLayout()).show(content, "message");
    }

    private JPanel identity(Map<String, String> fields) {
        JPanel panel = section("证书信息");
        JPanel metrics = Ui.panel(new GridLayout(1, 0, 10, 0));
        for (String name : new String[]{"类型", "证书版本", "序列号"}) {
            JPanel tile = Ui.card(Ui.CONTROL, 12);
            tile.setLayout(new BorderLayout(0, 4));
            tile.setBorder(new EmptyBorder(9, 12, 9, 12));
            tile.add(Ui.label(name, 12, false, Ui.MUTED), BorderLayout.NORTH);
            JTextField value = Ui.valueField(14, true, name.equals("序列号"), Ui.TEXT);
            value.setText(fields.get(name));
            value.setToolTipText(fields.get(name));
            value.setCaretPosition(0);
            tile.add(value, BorderLayout.CENTER);
            metrics.add(tile);
        }
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(metrics);
        panel.add(Box.createVerticalStrut(7));
        fields.forEach((name, value) -> {
            if (!name.equals("类型") && !name.equals("证书版本") && !name.equals("序列号")) {
                panel.add(row(name, value, false, Ui.TEXT));
            }
        });
        return panel;
    }

    private JPanel fields(String title, Map<String, String> fields) {
        JPanel panel = section(title);
        fields.forEach((name, value) -> {
            JPanel field = row(name, value, true, Ui.TEXT);
            if (name.equals("模数")) {
                addCopyAction(field, value, "RSA 模数");
            }
            panel.add(field);
        });
        return panel;
    }

    private void addCopyAction(JPanel row, String value, String name) {
        JButton button = Ui.button("复制", false);
        button.getAccessibleContext().setAccessibleName("复制 " + name);
        button.setToolTipText("复制 " + name);
        button.addActionListener(event -> copy.accept(value, name));
        JPanel buttonColumn = Ui.panel(new BorderLayout());
        buttonColumn.setBorder(new EmptyBorder(0, 10, 0, 0));
        buttonColumn.add(button, BorderLayout.NORTH);
        row.add(buttonColumn, BorderLayout.EAST);
    }

    private JPanel section(String title) {
        JPanel section = Ui.card(Ui.SURFACE, 18);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(new EmptyBorder(16, 18, 12, 18));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(Ui.label(title, 16, true, Ui.TEXT));
        section.add(Box.createVerticalStrut(12));
        return section;
    }

    private JPanel row(String name, String value, boolean mono, Ui.Tone color) {
        JPanel row = Ui.panel(new BorderLayout(12, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(5, 0, 5, 0));
        JLabel label = Ui.label(name, 13, false, Ui.MUTED);
        label.setPreferredSize(new Dimension(100, label.getPreferredSize().height));
        JPanel left = Ui.panel(new BorderLayout());
        left.setBorder(new EmptyBorder(4, 0, 0, 0));
        left.add(label, BorderLayout.NORTH);
        row.add(left, BorderLayout.WEST);
        row.add(new WrappedValue(value, mono, color), BorderLayout.CENTER);
        return row;
    }

    private static class WrappedValue extends JTextArea {
        WrappedValue(String value, boolean mono, Ui.Tone color) {
            ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setFont(mono ? Ui.mono(13) : Ui.font(14, false));
            Ui.foreground(this, color);
            setBorder(new EmptyBorder(4, 0, 4, 0));
            setText(value);
            setDropTarget(null);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            boolean reflow = width != getWidth();
            super.setBounds(x, y, width, height);
            if (reflow) {
                revalidate();
            }
        }
    }

    private static class DetailColumn extends JPanel implements Scrollable {
        DetailColumn() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 6));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
