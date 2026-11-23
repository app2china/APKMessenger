package apkinfo;

import ToolUtil.PermissionsUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AndroidPermissionCatalog {
    private static final String[] BASE_LEVELS = {"normal", "dangerous", "signature", "signatureOrSystem", "internal"};
    private static final String[] PROTECTION_FLAGS = {
            "privileged", "development", "appop", "pre23", "installer", "verifier", "preinstalled",
            "setup", "instant", "runtime", "oem", "vendorPrivileged", "textClassifier", null, null,
            "configurator", "incidentReportApprover", "appPredictor", "module", "companion",
            "retailDemo", "recents", "role", "knownSigner"
    };
    private static final Map<String, Entry> PLATFORM = loadPlatform();

    private AndroidPermissionCatalog() {
    }

    public static String[][] rows(List<ManifestPermission> declarations) {
        PermissionsUtil legacy = new PermissionsUtil();
        Map<String, ManifestPermission> definitions = new HashMap<>();
        for (ManifestPermission declaration : declarations) {
            if (declaration.isDefinition()) {
                definitions.putIfAbsent(declaration.attribute("name"), declaration);
            }
        }
        String[][] rows = new String[declarations.size()][5];
        for (int index = 0; index < declarations.size(); index++) {
            ManifestPermission declaration = declarations.get(index);
            String name = declaration.attribute("name");
            ManifestPermission definition = definitions.get(name);
            if (declaration.isDefinition()) {
                rows[index] = definition(name, declaration);
            } else if (definition != null) {
                rows[index] = definition(name, definition);
                rows[index][3] = requestConditions(declaration);
            } else {
                rows[index] = request(name, declaration, PLATFORM.get(name), legacy);
            }
        }
        return rows;
    }

    private static String[] definition(String name, ManifestPermission declaration) {
        String label = declaration.attribute("label");
        String protection = declaration.attribute("protectionLevel");
        String group = declaration.attribute("permissionGroup");
        List<String> notes = new ArrayList<>();
        add(notes, declaration.attribute("description"));
        if (!group.isEmpty()) {
            notes.add("权限组 " + group);
        }
        return new String[]{name, label.isEmpty() || label.startsWith("@") ? "自定义权限" : label,
                protectionLevel(protection.isEmpty() ? "0" : protection), "应用定义", String.join(" · ", notes)};
    }

    private static String[] request(String name, ManifestPermission declaration, Entry platform, PermissionsUtil legacy) {
        String label = platform == null ? "" : platform.label;
        String description = platform == null ? "" : platform.description;
        int legacyIndex = legacy.getList_PermissionsKey().indexOf(name);
        if (legacyIndex >= 0) {
            if (label.isEmpty()) {
                label = legacy.getList_PermissionsName().get(legacyIndex);
            }
            if (description.isEmpty()) {
                description = legacy.getList_PermissionsNotes().get(legacyIndex);
            }
        }
        List<String> notes = new ArrayList<>();
        add(notes, description);
        if (platform != null) {
            if (!platform.introduced.isEmpty()) {
                notes.add("API " + platform.introduced + " 引入");
            }
            if (!platform.deprecated.isEmpty()) {
                notes.add("API " + platform.deprecated + " 起弃用");
            }
        } else if (legacyIndex < 0) {
            notes.add("离线词典未收录，可能由应用、组件库或厂商系统提供");
        }
        if (label.isEmpty()) {
            label = platform == null ? "外部或未收录" : "Android 平台权限";
        }
        return new String[]{name, label, platform == null ? "" : protectionLevel(platform.protection),
                requestConditions(declaration), String.join(" · ", notes)};
    }

    private static String requestConditions(ManifestPermission declaration) {
        String max = declaration.attribute("maxSdkVersion");
        List<String> fields = new ArrayList<>();
        fields.add("应用请求");
        if (declaration.isApi23Request()) {
            fields.add(max.isEmpty() ? "API ≥ 23" : "API 23–" + apiLevel(max));
        } else if (!max.isEmpty()) {
            fields.add("API ≤ " + apiLevel(max));
        }
        String flags = declaration.attribute("usesPermissionFlags");
        if (!flags.isEmpty()) {
            try {
                long bits = Long.decode(flags);
                if ((bits & 0x10000) != 0) {
                    fields.add("neverForLocation（声明不用于推导位置）");
                }
                if ((bits & 0x20000) != 0) {
                    fields.add("onlyForLocationButton");
                }
                if ((bits & 0x40000) != 0) {
                    fields.add("onlyForPrivateComputeCore");
                }
                long remaining = bits & ~0x70000L;
                if (remaining != 0) {
                    fields.add("flags " + hex(remaining));
                }
            } catch (NumberFormatException e) {
                fields.add("flags " + flags);
            }
        }
        addFeature(fields, "需要功能 ", declaration.attribute("requiredFeature"));
        addFeature(fields, "排除功能 ", declaration.attribute("requiredNotFeature"));
        return String.join(" · ", fields);
    }

    private static String protectionLevel(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            long level = Long.decode(value);
            int base = (int) (level & 15);
            List<String> flags = new ArrayList<>();
            flags.add(base < BASE_LEVELS.length ? BASE_LEVELS[base] : hex(base));
            long remaining = level & ~15L;
            for (int index = 0; index < PROTECTION_FLAGS.length; index++) {
                long bit = 1L << (index + 4);
                if (PROTECTION_FLAGS[index] != null && (remaining & bit) != 0) {
                    flags.add(PROTECTION_FLAGS[index]);
                    remaining &= ~bit;
                }
            }
            if (remaining != 0) {
                flags.add(hex(remaining));
            }
            return String.join(" | ", flags);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String apiLevel(String value) {
        try {
            return String.valueOf(Long.decode(value));
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static void add(List<String> parts, String text) {
        if (!text.isEmpty()) {
            parts.add(text);
        }
    }

    private static void addFeature(List<String> parts, String prefix, String value) {
        if (!value.isEmpty()) {
            parts.add(prefix + value);
        }
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private static Map<String, Entry> loadPlatform() {
        Map<String, Entry> entries = new HashMap<>();
        try (InputStream data = openCatalog();
             BufferedReader reader = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] columns = line.split("\t", -1);
                    entries.put(columns[0], new Entry(columns));
                }
            }
        } catch (IOException e) {
            System.err.println("读取 Android 权限目录失败：" + e.getMessage());
        }
        return entries;
    }

    private static InputStream openCatalog() throws IOException {
        InputStream resource = AndroidPermissionCatalog.class.getResourceAsStream("/res/AndroidPermissions.tsv");
        return resource != null ? resource : Files.newInputStream(Paths.get("src/res/AndroidPermissions.tsv"));
    }

    private static final class Entry {
        final String introduced;
        final String deprecated;
        final String protection;
        final String label;
        final String description;

        Entry(String[] columns) {
            introduced = get(columns, 1);
            deprecated = get(columns, 2);
            protection = get(columns, 3);
            label = get(columns, 4);
            description = get(columns, 5);
        }

        private static String get(String[] columns, int index) {
            return index < columns.length ? columns[index] : "";
        }
    }
}
