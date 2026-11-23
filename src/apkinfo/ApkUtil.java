package apkinfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApkUtil {

    public static final String APPLICATION = "application:";
    public static final String APPLICATION_ICON = "application-icon";
    public static final String APPLICATION_LABEL = "application-label";
    public static final String APPLICATION_LABEL_N = "application: label";
    public static final String DENSITIES = "densities";
    public static final String AppName="application-label";
    public static final String LAUNCHABLE_ACTIVITY = "launchable";
    public static final String PACKAGE = "package";
    public static final String SDK_VERSION = "sdkVersion";
    public static final String SUPPORTS_ANY_DENSITY = "support-any-density";
    public static final String SUPPORTS_SCREENS = "support-screens";
    public static final String TARGET_SDK_VERSION = "targetSdkVersion";
    public static final String VERSION_CODE = "versionCode";
    public static final String VERSION_NAME = "versionName";
    public static final String USES_FEATURE = "uses-feature";
    public static final String USES_IMPLIED_FEATURE = "uses-implied-feature";
    public static final String USES_PERMISSION = "uses-permission";

    private static final String BUNDLED_AAPT_DIRECTORY = "src/aapt/";
    private static final Pattern BADGING_ATTRIBUTE = Pattern.compile(" (\\w+)='(.*?)'(?= \\w+='|$)");
    private static Path extractedAapt;
    private String aaptToolPath;

    public String getAaptToolPath() {
        return aaptToolPath == null ? BUNDLED_AAPT_DIRECTORY : aaptToolPath;
    }

    public void setAaptToolPath(String aaptToolPath) {
        this.aaptToolPath = aaptToolPath;
    }

    public ApkInfo parseApk(String apkPath) {
        File apk = new File(apkPath).getAbsoluteFile();
        try {
            ApkInfo apkInfo = new ApkInfo();
            apkInfo.setSize(apk.length());
            dump(line -> setApkInfoProperty(apkInfo, line), "badging", apk.getPath());
            if (apkInfo.getPackageName() == null || apkInfo.getPackageName().isEmpty()) {
                throw new IOException("APK package name is missing");
            }
            return apkInfo;
        } catch (IOException e) {
            System.err.println("读取 APK 基本信息失败：" + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public List<ManifestPermission> readManifestPermissions(File apk) throws IOException, InterruptedException {
        ManifestPermission.Reader manifest = new ManifestPermission.Reader();
        dump(manifest::accept, "xmltree", apk.getAbsolutePath(), "--file", "AndroidManifest.xml");
        return manifest.finish();
    }

    private void dump(Consumer<String> lines, String... arguments) throws IOException, InterruptedException {
        Path tool = resolveAaptTool();
        String[] command = new String[arguments.length + 2];
        command[0] = tool.toString();
        command[1] = "dump";
        System.arraycopy(arguments, 0, command, 2, arguments.length);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String firstLine = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (firstLine == null && !line.trim().isEmpty()) {
                        firstLine = line;
                    }
                    lines.accept(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(tool.getFileName() + " " + arguments[0] + " failed (exit " + exitCode + ")"
                        + (firstLine == null ? "" : ": " + firstLine));
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private Path resolveAaptTool() throws IOException {
        if (aaptToolPath == null) {
            return bundledAapt();
        }
        String extension = File.separatorChar == '\\' ? ".exe" : "";
        Path originalAapt = Paths.get(aaptToolPath, "aapt" + extension).toAbsolutePath();
        return Files.isRegularFile(originalAapt)
                ? originalAapt
                : Paths.get(aaptToolPath, "aapt2" + extension).toAbsolutePath();
    }

    private static synchronized Path bundledAapt() throws IOException {
        if (File.separatorChar != '\\') {
            throw new IOException("The bundled AAPT2 is built for Windows x64");
        }
        URL resource = ApkUtil.class.getResource("/aapt/aapt2.exe");
        if (resource == null) {
            Path source = Paths.get(BUNDLED_AAPT_DIRECTORY, "aapt2.exe").toAbsolutePath();
            if (Files.isRegularFile(source)) {
                return source;
            }
            throw new IOException("The bundled aapt/aapt2.exe resource is missing");
        }
        if ("file".equals(resource.getProtocol())) {
            try {
                return Paths.get(resource.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Invalid bundled AAPT2 path", e);
            }
        }
        if (extractedAapt == null || !Files.isRegularFile(extractedAapt)) {
            Path executable = Files.createTempFile("apkmessenger-aapt2-", ".exe");
            executable.toFile().deleteOnExit();
            try (InputStream input = resource.openStream()) {
                Files.copy(input, executable, StandardCopyOption.REPLACE_EXISTING);
            }
            extractedAapt = executable;
        }
        return extractedAapt;
    }

    private void setApkInfoProperty(ApkInfo apkInfo, String source) {
        int colon = source.indexOf(':');
        if (colon < 0) {
            return;
        }
        String key = source.substring(0, colon);
        if (key.equals(PACKAGE)) {
            apkInfo.setPackageName(getAttribute(source, "name"));
            apkInfo.setVersionCode(getAttribute(source, VERSION_CODE));
            apkInfo.setVersionName(getAttribute(source, VERSION_NAME));
        } else if (source.startsWith(APPLICATION)) {
            apkInfo.setIcon(getAttribute(source, "icon"));
            if (apkInfo.getLabel() == null) {
                addDefaultLabel(apkInfo, getAttribute(source, "label"));
            }
        } else if (key.startsWith(APPLICATION_ICON)) {
            apkInfo.addToIcons(key, getQuotedValue(source));
        } else if (key.equals(APPLICATION_LABEL)) {
            addDefaultLabel(apkInfo, getQuotedValue(source));
        } else if (key.startsWith(APPLICATION_LABEL + "-")) {
            String locale = key.substring(APPLICATION_LABEL.length() + 1);
            apkInfo.addAppNameKey(locale);
            apkInfo.addAppName(locale, getQuotedValue(source));
        } else if (key.startsWith(LAUNCHABLE_ACTIVITY)) {
            apkInfo.setLaunchableActivity(getAttribute(source, "name"));
        } else if (key.equals(SDK_VERSION) || key.equals("minSdkVersion")) {
            String minSdkVersion = getQuotedValue(source);
            apkInfo.setSdkVersion(minSdkVersion);
            apkInfo.setMinSdkVersion(minSdkVersion);
        } else if (key.equals(TARGET_SDK_VERSION)) {
            apkInfo.setTargetSdkVersion(getQuotedValue(source));
        } else if (key.startsWith(USES_PERMISSION)) {
            apkInfo.addToUsesPermissions(getAttribute(source, "name"));
        } else if (key.startsWith(USES_FEATURE)) {
            apkInfo.addToFeatures(getAttribute(source, "name"));
        }
    }

    private static void addDefaultLabel(ApkInfo apkInfo, String label) {
        if (apkInfo.getLabel() == null) {
            apkInfo.addAppNameKey("label");
        }
        apkInfo.setLabel(label);
        apkInfo.addAppName("label", label);
    }

    private static String getQuotedValue(String source) {
        int start = source.indexOf('\'');
        int end = source.lastIndexOf('\'');
        return start >= 0 && end > start ? source.substring(start + 1, end) : "";
    }

    private static String getAttribute(String source, String name) {
        Matcher attributes = BADGING_ATTRIBUTE.matcher(source);
        while (attributes.find()) {
            if (name.equals(attributes.group(1))) {
                return attributes.group(2);
            }
        }
        return "";
    }

}
