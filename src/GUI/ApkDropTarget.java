package GUI;

import javax.swing.AbstractAction;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class ApkDropTarget extends DropTargetAdapter {
    private static final int SAFE_ACTIONS = DnDConstants.ACTION_COPY | DnDConstants.ACTION_LINK;
    private static final KeyStroke PASTE = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
    private static final String PASTE_FILE = "apkmessenger-paste-file";
    private final Consumer<File> open;
    private final Consumer<String> feedback;
    private final AbstractAction pasteAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent event) {
            paste();
        }
    };
    private final ContainerAdapter children = new ContainerAdapter() {
        @Override
        public void componentAdded(ContainerEvent event) {
            attach(event.getChild());
        }

        @Override
        public void componentRemoved(ContainerEvent event) {
            detach(event.getChild());
        }
    };

    ApkDropTarget(JRootPane root, Consumer<File> open, Consumer<String> feedback) {
        this.open = open;
        this.feedback = feedback;
        bindPaste(root, JComponent.WHEN_IN_FOCUSED_WINDOW);
        attach(root);
    }

    static boolean isApk(File file) {
        return file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".apk") && file.isFile();
    }

    static File firstApk(List<?> files) {
        for (Object file : files) {
            if (file instanceof File && isApk((File) file)) {
                return (File) file;
            }
        }
        return null;
    }

    private void attach(Component component) {
        if (!(component instanceof JComponent) || component instanceof CellRendererPane) {
            return;
        }
        new DropTarget(component, SAFE_ACTIONS, this, true);
        if ((component instanceof JTextComponent && !((JTextComponent) component).isEditable())
                || component instanceof JTable) {
            bindPaste((JComponent) component, JComponent.WHEN_FOCUSED);
        }
        Container parent = (Container) component;
        parent.addContainerListener(children);
        for (Component child : parent.getComponents()) {
            attach(child);
        }
    }

    private void detach(Component component) {
        if (!(component instanceof JComponent) || component instanceof CellRendererPane) {
            return;
        }
        component.setDropTarget(null);
        Container parent = (Container) component;
        parent.removeContainerListener(children);
        for (Component child : parent.getComponents()) {
            detach(child);
        }
    }

    @Override
    public void dragEnter(DropTargetDragEvent event) {
        updateDragCursor(event);
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
        updateDragCursor(event);
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
        updateDragCursor(event);
    }

    private void updateDragCursor(DropTargetDragEvent event) {
        int action = safeAction(event.getSourceActions());
        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                && action != DnDConstants.ACTION_NONE) {
            event.acceptDrag(action);
        } else {
            event.rejectDrag();
        }
    }

    @Override
    public void drop(DropTargetDropEvent event) {
        int action = safeAction(event.getSourceActions());
        if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || action == DnDConstants.ACTION_NONE) {
            event.rejectDrop();
            return;
        }
        event.acceptDrop(action);
        boolean accepted = false;
        try {
            File apk = readFileList(event.getTransferable());
            if (apk != null) {
                accepted = true;
                SwingUtilities.invokeLater(() -> open.accept(apk));
            } else {
                SwingUtilities.invokeLater(() -> feedback.accept("拖入的文件中没有可打开的 APK"));
            }
        } catch (IOException | UnsupportedFlavorException | SecurityException e) {
            SwingUtilities.invokeLater(() -> feedback.accept("无法读取拖入的文件"));
        } finally {
            event.dropComplete(accepted);
        }
    }

    private static File readFileList(Transferable transfer) throws IOException, UnsupportedFlavorException {
        if (transfer == null || !transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return null;
        }
        Object data = transfer.getTransferData(DataFlavor.javaFileListFlavor);
        return data instanceof List<?> ? firstApk((List<?>) data) : null;
    }

    private void bindPaste(JComponent component, int scope) {
        component.getInputMap(scope).put(PASTE, PASTE_FILE);
        component.getActionMap().put(PASTE_FILE, pasteAction);
    }

    private void paste() {
        try {
            File apk = readFileList(Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null));
            if (apk != null) {
                open.accept(apk);
            } else {
                feedback.accept("请先在资源管理器中复制 APK 文件");
            }
        } catch (IOException | UnsupportedFlavorException | IllegalStateException | SecurityException e) {
            feedback.accept("无法读取剪贴板里的 APK");
        }
    }

    private static int safeAction(int sourceActions) {
        if ((sourceActions & DnDConstants.ACTION_COPY) != 0) {
            return DnDConstants.ACTION_COPY;
        }
        return sourceActions & DnDConstants.ACTION_LINK;
    }
}
