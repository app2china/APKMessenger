package apkinfo;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManifestPermission {
    private final String element;
    private final Map<String, String> attributes;

    private ManifestPermission(String element, Map<String, String> attributes) {
        this.element = element;
        this.attributes = attributes;
    }

    public static ManifestPermission requestFromSummary(String name) {
        return new ManifestPermission("uses-permission", Collections.singletonMap("name", name));
    }

    public boolean isDefinition() {
        return element.equals("permission");
    }

    public boolean isApi23Request() {
        return element.equals("uses-permission-sdk-23") || element.equals("uses-permission-sdk-m");
    }

    public String attribute(String name) {
        return attributes.getOrDefault(name, "");
    }

    static final class Reader {
        private static final Pattern ELEMENT = Pattern.compile("^(\\s*)E: ([^\\s]+).*");
        private static final Pattern ANDROID_ATTRIBUTE = Pattern.compile(
                "^\\s*A: http://schemas\\.android\\.com/apk/res/android:(\\w+)(?:\\(0x[0-9a-fA-F]+\\))?=(.*)");
        private static final Pattern QUOTED = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"");

        private final List<ManifestPermission> declarations = new ArrayList<>();
        private final Deque<Element> ancestry = new ArrayDeque<>();
        private ManifestPermission active;
        private boolean manifestFound;

        void accept(String line) {
            Matcher element = ELEMENT.matcher(line);
            if (element.matches()) {
                collectActive();
                int indent = element.group(1).length();
                while (!ancestry.isEmpty() && ancestry.peek().indent >= indent) {
                    ancestry.pop();
                }
                String name = element.group(2);
                if (ancestry.isEmpty() && name.equals("manifest")) {
                    manifestFound = true;
                } else if (ancestry.size() == 1 && ancestry.peek().name.equals("manifest")
                        && (name.equals("permission") || name.equals("uses-permission")
                        || name.equals("uses-permission-sdk-23") || name.equals("uses-permission-sdk-m"))) {
                    active = new ManifestPermission(name, new HashMap<>());
                }
                ancestry.push(new Element(name, indent));
            } else if (active != null) {
                Matcher attribute = ANDROID_ATTRIBUTE.matcher(line);
                if (attribute.matches()) {
                    String raw = attribute.group(2);
                    Matcher quoted = QUOTED.matcher(raw);
                    active.attributes.put(attribute.group(1), quoted.find()
                            ? quoted.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : raw);
                }
            }
        }

        List<ManifestPermission> finish() throws IOException {
            collectActive();
            if (!manifestFound) {
                throw new IOException("AAPT2 did not return an Android manifest tree");
            }
            return Collections.unmodifiableList(declarations);
        }

        private void collectActive() {
            if (active != null && !active.attribute("name").isEmpty()) {
                declarations.add(active);
            }
            active = null;
        }
    }

    private static final class Element {
        final String name;
        final int indent;

        Element(String name, int indent) {
            this.name = name;
            this.indent = indent;
        }
    }
}
