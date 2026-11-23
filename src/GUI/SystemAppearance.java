package GUI;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class SystemAppearance {
    private static final String PERSONALIZE =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    private SystemAppearance() {
    }

    static void follow(JFrame frame) {
        if (File.separatorChar != '\\') {
            return;
        }
        ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Windows app appearance");
            thread.setDaemon(true);
            return thread;
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                watcher.shutdownNow();
            }
        });
        watcher.scheduleWithFixedDelay(() -> {
            Boolean dark = readWindowsDarkMode();
            if (dark != null) {
                SwingUtilities.invokeLater(() -> {
                    if (frame.isDisplayable()) {
                        Ui.setDark(dark);
                    }
                });
            }
        }, 0, 4, TimeUnit.SECONDS);
    }

    static Boolean readWindowsDarkMode() {
        try {
            int appTheme = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE, "AppsUseLightTheme");
            return appTheme == 0;
        } catch (Win32Exception | SecurityException | LinkageError e) {
            return null;
        }
    }
}
