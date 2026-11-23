package GUI;

import ToolUtil.Md5CaculateUtil;
import ToolUtil.SignUtil;
import ToolUtil.SoInfoUtil;
import ToolUtil.ZipUtil;
import apkinfo.ApkInfo;
import apkinfo.ApkUtil;
import apkinfo.AndroidPermissionCatalog;
import apkinfo.ManifestPermission;
import apkinfo.SigningCertificate;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkinfotoUI {
    private final WinMain view;
    private final AtomicLong apkRequest = new AtomicLong();

    ApkinfotoUI(WinMain view) {
        this.view = view;
    }

    public void OpenApkFile(String filePath) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> OpenApkFile(filePath));
            return;
        }
        File apk = new File(filePath);
        long request = apkRequest.incrementAndGet();
        view.showLoading(apk);
        new Thread(() -> loadSignatures(apk, request), "APK certificates").start();
        new Thread(() -> loadApkInfo(apk, request), "APK information").start();
    }

    private void loadSignatures(File apk, long request) {
        for (int index = 0; index < 3 && request == apkRequest.get(); index++) {
            int scheme = index;
            int version = scheme + 1;
            try {
                List<SigningCertificate> certificates = new ArrayList<>();
                for (byte[] bytes : SignUtil.readCertificates(apk, version)) {
                    certificates.add(new SigningCertificate(bytes));
                }
                showForCurrentApk(request, () -> view.showSignatures(scheme, certificates,
                        "未读取到 V" + version + " 签名证书"));
            } catch (IOException | SecurityException e) {
                showForCurrentApk(request, () -> view.showSignatures(scheme, Collections.emptyList(),
                        "无法读取 V" + version + " 签名证书"));
            }
        }
    }

    private void loadApkInfo(File apk, long request) {
        ApkUtil aapt = new ApkUtil();
        ApkInfo info = aapt.parseApk(apk.getPath());
        if (info == null) {
            showForCurrentApk(request, view::showMetadataError);
            return;
        }
        if (request != apkRequest.get()) {
            return;
        }
        showForCurrentApk(request, () -> view.showMetadata(info));
        loadPermissions(aapt, apk, info, request);
        Icon icon = readIcon(apk, info);
        showForCurrentApk(request, () -> view.showApkIcon(icon));
        if (request == apkRequest.get()) {
            String md5 = Md5CaculateUtil.getMD5(apk);
            String protection = readProtection(apk);
            showForCurrentApk(request, () -> view.showFileDetails(
                    md5 == null ? null : md5.toUpperCase(Locale.ROOT), protection));
        }
    }

    private void loadPermissions(ApkUtil aapt, File apk, ApkInfo info, long request) {
        try {
            String[][] rows = AndroidPermissionCatalog.rows(aapt.readManifestPermissions(apk));
            showForCurrentApk(request, () -> view.showPermissions(rows, true));
        } catch (IOException e) {
            System.err.println("读取 Android 清单权限失败：" + e.getMessage());
            List<ManifestPermission> summary = new ArrayList<>();
            for (String name : new LinkedHashSet<>(info.getUsesPermissions())) {
                if (!name.isEmpty()) {
                    summary.add(ManifestPermission.requestFromSummary(name));
                }
            }
            String[][] rows = AndroidPermissionCatalog.rows(summary);
            showForCurrentApk(request, () -> view.showPermissions(rows, false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Icon readIcon(File apk, ApkInfo apkInfo) {
        Set<String> paths = new LinkedHashSet<>();
        paths.add(apkInfo.getIcon());
        paths.addAll(apkInfo.getIcons().values());
        try (ZipFile zip = new ZipFile(apk)) {
            for (String path : paths) {
                if (path == null || path.isEmpty()) {
                    continue;
                }
                ZipEntry entry = zip.getEntry(path);
                if (entry == null) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    BufferedImage image = ImageIO.read(input);
                    if (image != null) {
                        double scale = 52.0 / Math.max(image.getWidth(), image.getHeight());
                        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                        return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("读取 APK 图标失败：" + e.getMessage());
        }
        return null;
    }

    private String readProtection(File apk) {
        SoInfoUtil soInfo = new SoInfoUtil();
        try {
            for (String path : ZipUtil.readZipFile(apk.getPath(), "lib/")) {
                String protection = soInfo.IsThisSo(new File(path).getName());
                if (!protection.isEmpty()) {
                    return protection;
                }
            }
            return "未识别到加固壳";
        } catch (IOException e) {
            System.err.println("读取 APK 加固信息失败：" + e.getMessage());
            return "读取失败";
        }
    }

    private void showForCurrentApk(long request, Runnable update) {
        SwingUtilities.invokeLater(() -> {
            if (request == apkRequest.get()) {
                update.run();
            }
        });
    }
}
