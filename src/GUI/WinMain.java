package GUI;

import apkinfo.ApkInfo;
import apkinfo.SigningCertificate;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class WinMain {
    private final JFrame window = new JFrame("APK Messenger");
    private final Icon placeholder = Ui.mark(32, Ui.ACCENT, false);
    private final JLabel appIcon = new JLabel(placeholder, SwingConstants.CENTER);
    private final JTextField appName = Ui.valueField(21, true, false, Ui.TEXT);
    private final JTextField packageName = Ui.valueField(13, false, true, Ui.MUTED);
    private final JTextField apkFileName = Ui.valueField(13, false, false, Ui.TEXT);
    private final JTextField versionName = Ui.valueField(16, true, false, Ui.TEXT);
    private final JTextField versionCode = Ui.valueField(16, true, true, Ui.TEXT);
    private final JTextField md5 = Ui.valueField(12, false, true, Ui.TEXT);
    private final JTextField protection = Ui.valueField(14, false, false, Ui.TEXT);
    private final JComboBox<String> languages = Ui.combo();
    private final Map<JTextField, JButton> copyButtons = new HashMap<>();
    private final JPanel workspace = Ui.panel(new CardLayout());
    private final JPanel analysisPages = Ui.panel(new CardLayout());
    private final JPanel schemePages = Ui.panel(new CardLayout());
    private final JLabel permissionStatus = Ui.label("", 13, false, Ui.MUTED);
    private final JLabel status = Ui.label("等待打开 APK", 13, false, Ui.MUTED);
    private final JLabel feedback = Ui.label("", 13, true, Ui.ACCENT);
    private final Timer feedbackTimer = new Timer(2600, event -> feedback.setText(""));
    private final JTable permissions = new JTable();
    private final LocalizedNamesPanel localizedNames = new LocalizedNamesPanel(this::copy);
    private final SignaturePanel[] signatureViews = new SignaturePanel[3];
    private final JToggleButton[] schemeTabs = new JToggleButton[3];
    private final ApkinfotoUI controller = new ApkinfotoUI(this);
    private Map<String, String> appNames = Collections.emptyMap();
    private File currentApk;
    private boolean fileDialogOpen;

    public void CreatWin() {
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        window.setIconImage(makeWindowIcon());
        window.setMinimumSize(new Dimension(880, 600));
        feedbackTimer.setRepeats(false);

        JPanel root = new JPanel(new BorderLayout());
        Ui.background(root, Ui.BACKGROUND);
        root.add(header(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        workspace.setBorder(new EmptyBorder(22, 24, 22, 24));
        workspace.add(emptyWorkspace(), "empty");
        workspace.add(analysis(), "analysis");
        root.add(workspace, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        window.setContentPane(root);
        new ApkDropTarget(window.getRootPane(), this::open, this::notifyUser);
        window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "open-apk");
        window.getRootPane().getActionMap().put("open-apk", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooseApk();
            }
        });

        resetSummary();
        setValue(appName, "尚未打开 APK");
        setPermissions(new String[0][]);
        window.setSize(1160, 800);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        SystemAppearance.follow(window);
    }

    private Image makeWindowIcon() {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Ui.mark(32, Ui.ACCENT, true).paintIcon(window, graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        Ui.background(header, Ui.SIDEBAR);
        header.setBorder(BorderFactory.createCompoundBorder(
                Ui.border(0, 0, 1, 0), new EmptyBorder(13, 24, 13, 24)));
        JPanel brand = Ui.panel(new BorderLayout(11, 0));
        JPanel badge = Ui.card(Ui.ACCENT_FILL, 13);
        badge.setLayout(new BorderLayout());
        badge.setPreferredSize(new Dimension(38, 38));
        badge.add(new JLabel(Ui.mark(23, Ui.ACCENT, true), SwingConstants.CENTER));
        brand.add(badge, BorderLayout.WEST);
        brand.add(Ui.label("APK Messenger", 21, true, Ui.TEXT), BorderLayout.CENTER);
        header.add(brand, BorderLayout.WEST);

        JButton open = Ui.button("打开 APK", true);
        open.setIcon(Ui.mark(17, Ui.INK, false));
        open.setIconTextGap(7);
        open.setToolTipText("打开 APK 文件 (Ctrl+O)");
        open.addActionListener(event -> chooseApk());
        header.add(open, BorderLayout.EAST);
        return header;
    }

    private JPanel sidebar() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setPreferredSize(new Dimension(286, 0));
        Ui.background(rail, Ui.SIDEBAR);
        rail.setBorder(Ui.border(0, 0, 0, 1));

        JPanel body = Ui.column();
        body.setBorder(new EmptyBorder(20, 18, 18, 18));
        body.add(Ui.label("应用概览", 15, true, Ui.MUTED));
        body.add(Box.createVerticalStrut(15));

        JPanel iconTile = Ui.card(Ui.ACCENT_FILL, 17);
        iconTile.setLayout(new BorderLayout());
        iconTile.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconTile.setPreferredSize(new Dimension(68, 68));
        iconTile.setMaximumSize(new Dimension(68, 68));
        iconTile.add(appIcon);
        body.add(iconTile);
        body.add(Box.createVerticalStrut(10));
        appName.setAlignmentX(Component.LEFT_ALIGNMENT);
        packageName.setAlignmentX(Component.LEFT_ALIGNMENT);
        appName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
        packageName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        appName.getAccessibleContext().setAccessibleName("应用名");
        packageName.getAccessibleContext().setAccessibleName("包名");
        body.add(appName);
        body.add(packageName);
        body.add(Box.createVerticalStrut(17));

        JPanel versions = Ui.card(Ui.SURFACE, 15);
        versions.setLayout(new GridLayout(1, 2, 10, 0));
        versions.setBorder(new EmptyBorder(12, 13, 12, 13));
        versions.setAlignmentX(Component.LEFT_ALIGNMENT);
        versions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        versions.add(field("版本名", versionName, false));
        versions.add(field("版本号", versionCode, false));
        body.add(versions);
        body.add(Box.createVerticalStrut(16));
        body.add(field("APK 文件", apkFileName, true));
        body.add(Box.createVerticalStrut(10));
        body.add(field("文件 MD5", md5, true));
        body.add(Box.createVerticalStrut(10));
        body.add(field("加固类型", protection, false));
        body.add(Box.createVerticalStrut(16));

        JLabel localeLabel = Ui.label("应用名语言", 12, false, Ui.MUTED);
        localeLabel.setLabelFor(languages);
        body.add(localeLabel);
        body.add(Box.createVerticalStrut(6));
        languages.setAlignmentX(Component.LEFT_ALIGNMENT);
        languages.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
        languages.setEnabled(false);
        languages.addActionListener(event -> {
            Object selection = languages.getSelectedItem();
            if (selection != null) {
                setValue(appName, appNames.get("默认".equals(selection) ? "label" : selection));
            }
        });
        body.add(languages);

        JScrollPane scroll = Ui.scroll(body);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rail.add(scroll, BorderLayout.CENTER);

        JToggleButton alwaysOnTop = Ui.tab("窗口置顶", false);
        alwaysOnTop.addActionListener(event -> window.setAlwaysOnTop(alwaysOnTop.isSelected()));
        JPanel options = Ui.panel(new BorderLayout());
        options.setBorder(new EmptyBorder(7, 17, 11, 17));
        options.add(alwaysOnTop, BorderLayout.WEST);
        rail.add(options, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel field(String name, JTextField value, boolean canCopy) {
        JPanel group = Ui.panel(new BorderLayout(0, 5));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = Ui.label(name, 12, false, Ui.MUTED);
        label.setLabelFor(value);
        if (canCopy) {
            JPanel title = Ui.panel(new BorderLayout());
            title.add(label, BorderLayout.CENTER);
            boolean path = value == apkFileName;
            JButton button = Ui.button(path ? "复制路径" : "复制", false);
            button.getAccessibleContext().setAccessibleName(path ? "复制 APK 文件路径" : "复制" + name);
            button.setEnabled(false);
            button.addActionListener(event -> copy(path ? currentApk.getAbsolutePath() : value.getText(),
                    path ? "APK 路径" : name));
            title.add(button, BorderLayout.EAST);
            copyButtons.put(value, button);
            group.add(title, BorderLayout.NORTH);
        } else {
            group.add(label, BorderLayout.NORTH);
        }
        group.add(value, BorderLayout.CENTER);
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, group.getPreferredSize().height));
        return group;
    }

    private JPanel emptyWorkspace() {
        JPanel area = Ui.card(Ui.SURFACE, 22);
        area.setLayout(new GridBagLayout());
        JPanel instructions = Ui.column();
        JPanel tile = Ui.card(Ui.ACCENT_FILL, 20);
        tile.setLayout(new BorderLayout());
        tile.setAlignmentX(Component.CENTER_ALIGNMENT);
        tile.setPreferredSize(new Dimension(72, 72));
        tile.setMaximumSize(new Dimension(72, 72));
        tile.add(new JLabel(Ui.mark(38, Ui.ACCENT, false), SwingConstants.CENTER));
        instructions.add(tile);
        instructions.add(Box.createVerticalStrut(17));
        JLabel title = Ui.label("打开一个 APK", 24, true, Ui.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructions.add(title);
        instructions.add(Box.createVerticalStrut(8));
        JLabel help = Ui.label("将 APK 拖到这里，或在资源管理器复制文件后按 Ctrl+V。", 14, false, Ui.MUTED);
        help.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructions.add(help);
        area.add(instructions);
        return area;
    }

    private JPanel analysis() {
        JPanel area = Ui.panel(new BorderLayout(0, 17));
        JPanel tabs = Ui.panel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        tabs.setBorder(Ui.border(0, 0, 1, 0));
        ButtonGroup group = new ButtonGroup();
        addTab(tabs, group, analysisPages, "签名信息", "signatures", true, true);
        addTab(tabs, group, analysisPages, "权限信息", "permissions", false, true);
        addTab(tabs, group, analysisPages, "多语言文件名", "localized-names", false, true);
        area.add(tabs, BorderLayout.NORTH);
        analysisPages.add(signatureWorkspace(), "signatures");
        analysisPages.add(permissionWorkspace(), "permissions");
        analysisPages.add(localizedNames, "localized-names");
        area.add(analysisPages, BorderLayout.CENTER);
        return area;
    }

    private JPanel signatureWorkspace() {
        JPanel area = Ui.panel(new BorderLayout(0, 12));
        JPanel tabs = Ui.panel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        ButtonGroup group = new ButtonGroup();
        for (int index = 0; index < signatureViews.length; index++) {
            String version = "V" + (index + 1);
            signatureViews[index] = new SignaturePanel(version, this::copy);
            schemePages.add(signatureViews[index], version);
            schemeTabs[index] = addTab(tabs, group, schemePages, version, version, index == 0, false);
            if (index < signatureViews.length - 1) {
                tabs.add(Box.createHorizontalStrut(8));
            }
        }
        area.add(tabs, BorderLayout.NORTH);
        area.add(schemePages, BorderLayout.CENTER);
        return area;
    }

    private JToggleButton addTab(JPanel tabs, ButtonGroup group, JPanel pages, String label, String card,
                                 boolean selected, boolean navigation) {
        JToggleButton tab = Ui.tab(label, navigation);
        group.add(tab);
        tab.setSelected(selected);
        tab.addActionListener(event -> ((CardLayout) pages.getLayout()).show(pages, card));
        tabs.add(tab);
        return tab;
    }

    private JPanel permissionWorkspace() {
        JPanel panel = Ui.card(Ui.SURFACE, 18);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));
        permissionStatus.setFont(Ui.font(15, true));
        panel.add(permissionStatus, BorderLayout.NORTH);
        Ui.styleTable(permissions, 0);
        panel.add(Ui.scroll(permissions), BorderLayout.CENTER);
        JTextArea details = new JTextArea();
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(Ui.font(13, false));
        Ui.background(details, Ui.BACKGROUND);
        Ui.foreground(details, Ui.TEXT);
        details.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane detailPane = Ui.scroll(details);
        detailPane.setPreferredSize(new Dimension(0, 110));
        detailPane.setVisible(false);
        panel.add(detailPane, BorderLayout.SOUTH);
        permissions.getSelectionModel().addListSelectionListener(event -> {
            int row = permissions.getSelectedRow();
            detailPane.setVisible(row >= 0);
            if (row >= 0) {
                details.setText(permissions.getValueAt(row, 0) + "\n"
                        + permissions.getValueAt(row, 3) + "\n" + permissions.getValueAt(row, 4));
                details.setCaretPosition(0);
            }
            panel.revalidate();
        });
        return panel;
    }

    private JPanel footer() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        Ui.background(bar, Ui.SIDEBAR);
        bar.setBorder(BorderFactory.createCompoundBorder(
                Ui.border(1, 0, 0, 0), new EmptyBorder(8, 24, 8, 24)));
        bar.add(status);
        bar.add(feedback, BorderLayout.EAST);
        return bar;
    }

    void showLoading(File apk) {
        currentApk = apk;
        resetSummary();
        setValue(appName, "正在读取 APK…");
        setValue(apkFileName, apk.getName());
        apkFileName.setToolTipText(" " + apk.getAbsolutePath());
        setPermissions(new String[0][]);
        localizedNames.showStatus("正在读取多语言应用名…");
        permissionStatus.setText("正在读取权限…");
        for (int index = 0; index < signatureViews.length; index++) {
            showSignatures(index, Collections.emptyList(), "正在读取 V" + (index + 1) + " 签名…");
        }
        Ui.foreground(status, Ui.MUTED);
        status.setText("正在读取  ·  " + apk.getName());
        feedback.setText("");
        ((CardLayout) workspace.getLayout()).show(workspace, "analysis");
    }

    void showMetadata(ApkInfo apk) {
        appNames = new HashMap<>(apk.getAppName());
        languages.setModel(new DefaultComboBoxModel<>(new String[]{"默认"}));
        for (String locale : LocalizedNamesPanel.localeTags(apk)) {
            languages.addItem(locale);
        }
        languages.setEnabled(languages.getItemCount() > 1);
        String chinese = LocalizedNamesPanel.preferredChinese(apk);
        if (chinese != null) {
            languages.setSelectedItem(chinese);
        }
        setValue(appName, chinese == null ? apk.getLabel() : appNames.get(chinese));
        setValue(packageName, apk.getPackageName());
        setValue(versionName, apk.getVersionName());
        setValue(versionCode, apk.getVersionCode());
        localizedNames.showApk(apk);
        status.setText("已读取基本信息  ·  " + currentApk.getName());
    }

    void showPermissions(String[][] rows, boolean fromManifest) {
        setPermissions(rows);
        String title = rows.length + " 项权限声明";
        permissionStatus.setText(fromManifest ? title + "  ·  AndroidManifest.xml"
                : "清单读取失败  ·  权限摘要（" + rows.length + " 项）");
    }

    void showApkIcon(Icon icon) {
        appIcon.setIcon(icon == null ? placeholder : icon);
    }

    void showMetadataError() {
        setValue(appName, "基本信息读取失败");
        permissionStatus.setText("无法读取权限");
        localizedNames.showStatus("无法读取多语言应用名");
        Ui.foreground(status, Ui.ALERT);
        status.setText("无法读取 APK 基本信息，请检查文件是否有效");
    }

    void showFileDetails(String hash, String protectionType) {
        setValue(md5, hash);
        if (hash == null) {
            md5.setText("读取失败");
        }
        setValue(protection, protectionType);
    }

    void showSignatures(int index, List<SigningCertificate> certificates, String message) {
        String version = "V" + (index + 1);
        signatureViews[index].setCertificates(certificates, message);
        schemeTabs[index].setText(certificates.isEmpty() ? version : version + "  ·  " + certificates.size());
        schemeTabs[index].setToolTipText(certificates.isEmpty() ? message : certificates.size() + " 张证书");
    }

    private void resetSummary() {
        appIcon.setIcon(placeholder);
        appNames = Collections.emptyMap();
        languages.setModel(new DefaultComboBoxModel<>(new String[]{"默认"}));
        languages.setEnabled(false);
        setValue(apkFileName, null);
        setValue(packageName, null);
        setValue(versionName, null);
        setValue(versionCode, null);
        setValue(md5, null);
        setValue(protection, null);
    }

    private void setValue(JTextField field, String value) {
        boolean present = value != null && !value.isEmpty();
        field.setText(present ? value : "—");
        field.setCaretPosition(0);
        field.setToolTipText(present ? " " + value : null);
        JButton action = copyButtons.get(field);
        if (action != null) {
            action.setEnabled(present);
        }
    }

    private void setPermissions(String[][] rows) {
        permissions.setModel(new DefaultTableModel(rows, new String[]{"权限标识", "名称", "保护级别", "声明与条件", "说明"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        permissions.getColumnModel().getColumn(0).setPreferredWidth(285);
        permissions.getColumnModel().getColumn(1).setPreferredWidth(125);
        permissions.getColumnModel().getColumn(2).setPreferredWidth(110);
        permissions.getColumnModel().getColumn(3).setPreferredWidth(155);
        permissions.getColumnModel().getColumn(4).setPreferredWidth(260);
    }

    private void chooseApk() {
        if (fileDialogOpen) {
            return;
        }
        fileDialogOpen = true;
        File previous = currentApk;
        SwingWorker<File, Void> picker = new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws IOException {
                return WindowsApkDialog.choose(window, previous);
            }

            @Override
            protected void done() {
                fileDialogOpen = false;
                if (!window.isDisplayable()) {
                    return;
                }
                try {
                    File chosen = get();
                    if (chosen != null) {
                        open(chosen);
                    }
                } catch (ExecutionException e) {
                    System.err.println("打开 Windows 文件选择框失败：" + e.getCause());
                    notifyUser("无法打开 Windows 文件选择框，可将 APK 拖入窗口");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        new Thread(picker, "Windows APK picker").start();
    }

    private boolean open(File file) {
        if (!ApkDropTarget.isApk(file)) {
            notifyUser("请选择一个 .apk 文件");
            return false;
        }
        controller.OpenApkFile(file.getAbsolutePath());
        return true;
    }

    private void copy(String text, String label) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            notifyUser("已复制" + label);
        } catch (IllegalStateException | SecurityException e) {
            notifyUser("无法访问剪贴板，请重试");
        }
    }

    private void notifyUser(String message) {
        feedback.setText(message);
        feedbackTimer.restart();
    }
}
