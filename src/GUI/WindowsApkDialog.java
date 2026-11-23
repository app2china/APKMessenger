package GUI;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

final class WindowsApkDialog {
    private static final Guid.CLSID FILE_OPEN_DIALOG = new Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}");
    private static final Guid.IID FILE_OPEN_INTERFACE = new Guid.IID("{D57C7288-D4AD-4768-BE02-9D969532D960}");
    private static final Guid.IID SHELL_ITEM_INTERFACE = new Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}");
    private static final int CANCELLED = 0x800704C7;
    private static final int FORCE_FILESYSTEM = 0x40;
    private static final int PATH_MUST_EXIST = 0x800;
    private static final int FILE_MUST_EXIST = 0x1000;
    private static final int FILESYSTEM_PATH = 0x80058000;

    private WindowsApkDialog() {
    }

    static File choose(Window owner, File currentApk) throws IOException {
        Ole32 ole = Ole32.INSTANCE;
        check(ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED | Ole32.COINIT_DISABLE_OLE1DDE)
                .intValue(), "CoInitializeEx");
        try {
            PointerByReference instance = new PointerByReference();
            check(ole.CoCreateInstance(FILE_OPEN_DIALOG, null, WTypes.CLSCTX_INPROC_SERVER,
                    FILE_OPEN_INTERFACE, instance).intValue(), "CoCreateInstance");
            FileOpenDialog dialog = new FileOpenDialog(instance.getValue());
            try {
                dialog.configure(currentApk);
                int result = dialog.call(FileOpenDialog.SHOW, Native.getWindowPointer(owner));
                if (result == CANCELLED) {
                    return null;
                }
                check(result, "IFileDialog.Show");
                return dialog.selectedFile();
            } finally {
                dialog.Release();
            }
        } finally {
            ole.CoUninitialize();
        }
    }

    private static void check(int result, String operation) throws IOException {
        if (result < 0) {
            throw new IOException(String.format(Locale.ROOT, "%s failed (0x%08X)", operation, result));
        }
    }

    private static class ComObject extends Unknown {
        ComObject(Pointer pointer) {
            super(pointer);
        }

        int call(int slot, Object... arguments) {
            Object[] parameters = new Object[arguments.length + 1];
            parameters[0] = getPointer();
            System.arraycopy(arguments, 0, parameters, 1, arguments.length);
            return _invokeNativeInt(slot, parameters);
        }
    }

    private static final class FileOpenDialog extends ComObject {
        // Vtable positions include the three IUnknown methods, then IModalWindow::Show.
        static final int SHOW = 3;
        private static final int SET_FILE_TYPES = 4;
        private static final int SET_OPTIONS = 9;
        private static final int GET_OPTIONS = 10;
        private static final int SET_FOLDER = 12;
        private static final int SET_TITLE = 17;
        private static final int GET_RESULT = 20;
        private static final int GET_DISPLAY_NAME = 5; // IShellItem
        private Memory filter;

        FileOpenDialog(Pointer pointer) {
            super(pointer);
        }

        void configure(File currentApk) throws IOException {
            IntByReference options = new IntByReference();
            check(call(GET_OPTIONS, options), "IFileDialog.GetOptions");
            check(call(SET_OPTIONS, options.getValue() | FORCE_FILESYSTEM | PATH_MUST_EXIST | FILE_MUST_EXIST),
                    "IFileDialog.SetOptions");
            check(call(SET_TITLE, new WString("打开 APK")), "IFileDialog.SetTitle");
            setApkFilter();
            if (currentApk != null && currentApk.getParentFile() != null) {
                selectFolder(currentApk.getParentFile());
            }
        }

        private void setApkFilter() throws IOException {
            // COMDLG_FILTERSPEC contains pointers to the display name and the glob.
            String label = "Android APK (*.apk)";
            String glob = "*.apk";
            long nameOffset = 2L * Native.POINTER_SIZE;
            long patternOffset = nameOffset + (label.length() + 1L) * Native.WCHAR_SIZE;
            filter = new Memory(patternOffset + (glob.length() + 1L) * Native.WCHAR_SIZE);
            filter.setWideString(nameOffset, label);
            filter.setWideString(patternOffset, glob);
            filter.setPointer(0, filter.share(nameOffset));
            filter.setPointer(Native.POINTER_SIZE, filter.share(patternOffset));
            check(call(SET_FILE_TYPES, 1, filter), "IFileDialog.SetFileTypes");
        }

        private void selectFolder(File folder) {
            PointerByReference result = new PointerByReference();
            HRESULT created = ShellItems.INSTANCE.SHCreateItemFromParsingName(new WString(folder.getPath()),
                    null, new Guid.REFIID(SHELL_ITEM_INTERFACE), result);
            if (created.intValue() >= 0) {
                ComObject item = new ComObject(result.getValue());
                try {
                    call(SET_FOLDER, item.getPointer());
                } finally {
                    item.Release();
                }
            }
        }

        File selectedFile() throws IOException {
            PointerByReference result = new PointerByReference();
            check(call(GET_RESULT, result), "IFileDialog.GetResult");
            ComObject item = new ComObject(result.getValue());
            try {
                PointerByReference path = new PointerByReference();
                check(item.call(GET_DISPLAY_NAME, FILESYSTEM_PATH, path), "IShellItem.GetDisplayName");
                try {
                    return new File(path.getValue().getWideString(0));
                } finally {
                    Ole32.INSTANCE.CoTaskMemFree(path.getValue());
                }
            } finally {
                item.Release();
            }
        }
    }

    public interface ShellItems extends StdCallLibrary {
        ShellItems INSTANCE = Native.load("shell32", ShellItems.class);

        HRESULT SHCreateItemFromParsingName(WString path, Pointer bindContext, Guid.REFIID interfaceId,
                                            PointerByReference item);
    }
}
