package GUI;

import apkinfo.ApkInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

final class LocalizedNamesPanel extends JPanel {
    private static final int MAX_STEM_LENGTH = 240;
    private static final String APK_EXTENSION = ".apk";
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[<>:\"/\\\\|?*\\p{Cc}]+");
    private static final Pattern TRAILING_DOTS_OR_SPACES = Pattern.compile("[. ]+$");
    private static final Pattern DEVICE_NAME = Pattern.compile("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]",
            Pattern.CASE_INSENSITIVE);

    private final JTable table = new JTable();
    private final JLabel status = Ui.label("", 15, true, Ui.MUTED);
    private final JButton copyName = Ui.button("复制文件名", false);

    LocalizedNamesPanel(BiConsumer<String, String> copy) {
        setLayout(new BorderLayout());
        setOpaque(false);
        JPanel card = Ui.card(Ui.SURFACE, 18);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel header = Ui.panel(new BorderLayout(12, 0));
        header.add(status, BorderLayout.CENTER);
        header.add(copyName, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);
        card.add(Ui.scroll(table), BorderLayout.CENTER);
        add(card);
        Ui.styleTable(table, -1);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> copyName.setEnabled(table.getSelectedRow() >= 0));
        copyName.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                copy.accept((String) table.getValueAt(row, 2), "建议文件名");
            }
        });
        showStatus("等待打开 APK");
    }

    void showStatus(String message) {
        setRows(new String[0][]);
        status.setText(message);
    }

    void showApk(ApkInfo apk) {
        String[][] rows = rows(apk);
        setRows(rows);
        status.setText(rows.length + " 项应用名称");
        table.setRowSelectionInterval(0, 0);
    }

    private void setRows(String[][] rows) {
        table.setModel(new DefaultTableModel(rows, new String[]{"语言 / 地区", "应用名", "建议 APK 文件名"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(230);
        table.getColumnModel().getColumn(2).setPreferredWidth(310);
        copyName.setEnabled(false);
    }

    static String[][] rows(ApkInfo apk) {
        Map<String, String> names = apk.getAppName();
        List<String[]> rows = new ArrayList<>();
        List<String[]> chinese = new ArrayList<>();
        String defaultLabel = names.getOrDefault("label", apk.getLabel());
        rows.add(new String[]{"默认", defaultLabel == null || defaultLabel.trim().isEmpty() ? "—" : defaultLabel,
                filename(defaultLabel, apk.getPackageName())});
        for (String tag : localeTags(apk)) {
            String label = names.get(tag);
            Locale locale = toLocale(tag);
            String display = locale.getDisplayName(Locale.SIMPLIFIED_CHINESE);
            String language = display.isEmpty() || display.equalsIgnoreCase(tag) ? tag : display + " (" + tag + ")";
            (isChinese(locale) ? chinese : rows).add(new String[]{language, label, filename(label, apk.getPackageName())});
        }
        rows.addAll(0, chinese);
        return rows.toArray(new String[0][]);
    }

    static String preferredChinese(ApkInfo apk) {
        String firstChinese = null;
        String simplified = null;
        for (String tag : localeTags(apk)) {
            Locale locale = toLocale(tag);
            if (!isChinese(locale)) {
                continue;
            }
            if ("CN".equals(locale.getCountry()) && !"Hant".equals(locale.getScript())) {
                return tag;
            }
            if (firstChinese == null) {
                firstChinese = tag;
            }
            if (simplified == null && ("Hans".equals(locale.getScript()) || "SG".equals(locale.getCountry()))) {
                simplified = tag;
            }
        }
        return simplified != null ? simplified : firstChinese;
    }

    static Set<String> localeTags(ApkInfo apk) {
        Set<String> locales = new LinkedHashSet<>(apk.getAppNameKey());
        locales.addAll(apk.getAppName().keySet());
        locales.remove("label");
        locales.removeIf(tag -> {
            String name = apk.getAppName().get(tag);
            return name == null || name.trim().isEmpty();
        });
        return locales;
    }

    private static Locale toLocale(String tag) {
        String normalized = tag.startsWith("b+") ? tag.substring(2).replace('+', '-') : tag;
        return Locale.forLanguageTag(normalized.replace('_', '-'));
    }

    private static boolean isChinese(Locale locale) {
        String language = locale.getLanguage();
        return "zh".equals(language) || "cmn".equals(language) || "yue".equals(language);
    }

    static String filename(String label, String packageName) {
        String stem = cleanStem(label);
        if (stem.isEmpty()) {
            stem = cleanStem(packageName);
        }
        if (stem.isEmpty()) {
            return "app.apk";
        }
        if (stem.length() > MAX_STEM_LENGTH) {
            int cutoff = Character.isHighSurrogate(stem.charAt(MAX_STEM_LENGTH - 1))
                    ? MAX_STEM_LENGTH - 1 : MAX_STEM_LENGTH;
            stem = TRAILING_DOTS_OR_SPACES.matcher(stem.substring(0, cutoff)).replaceFirst("");
        }
        int dot = stem.indexOf('.');
        String first = dot >= 0 ? stem.substring(0, dot).trim() : stem;
        return (DEVICE_NAME.matcher(first).matches() ? "_" + stem : stem) + APK_EXTENSION;
    }

    private static String cleanStem(String label) {
        if (label == null) {
            return "";
        }
        String stem = INVALID_CHARACTERS.matcher(label.trim()).replaceAll("_");
        if (stem.toLowerCase(Locale.ROOT).endsWith(APK_EXTENSION)) {
            stem = stem.substring(0, stem.length() - APK_EXTENSION.length());
        }
        return TRAILING_DOTS_OR_SPACES.matcher(stem).replaceFirst("");
    }
}
