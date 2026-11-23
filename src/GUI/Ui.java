package GUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

final class Ui {
    static final Tone BACKGROUND = new Tone(0xF4F7FB, 0x101624);
    static final Tone SIDEBAR = new Tone(0xEDF2F8, 0x151D2D);
    static final Tone SURFACE = new Tone(0xFFFFFF, 0x1A2335);
    static final Tone CONTROL = new Tone(0xE7EDF5, 0x233047);
    static final Tone BORDER = new Tone(0xCED9E7, 0x303D54);
    static final Tone TEXT = new Tone(0x172941, 0xF3F6FD);
    static final Tone MUTED = new Tone(0x52657D, 0xA5B3CD);
    static final Tone ACCENT = new Tone(0x087466, 0x65E2CD);
    static final Tone ACCENT_FILL = new Tone(0xDDF4EE, 0x203B44);
    static final Tone INK = new Tone(0xFFFFFF, 0x12272C);
    static final Tone ALERT = new Tone(0xA03423, 0xFFBF9C);
    private static final String FOREGROUND_ROLE = "apkmessenger.foreground";
    private static final String BACKGROUND_ROLE = "apkmessenger.background";
    private static boolean dark;
    private static final String TEXT_FONT = availableFont("Microsoft YaHei UI", Font.DIALOG);
    private static final String CODE_FONT = availableFont("Consolas", Font.MONOSPACED);

    private enum ButtonStyle { PRIMARY, SECONDARY, TAB, NAVIGATION }

    private Ui() {
    }

    static final class Tone {
        private final Color lightColor;
        private final Color darkColor;

        private Tone(int light, int dark) {
            lightColor = new Color(light);
            darkColor = new Color(dark);
        }

        Color color() {
            return dark ? darkColor : lightColor;
        }
    }

    static void setDark(boolean enabled) {
        if (dark == enabled) {
            return;
        }
        dark = enabled;
        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) {
                refresh(window);
                window.repaint();
            }
        }
    }

    static void foreground(JComponent component, Tone tone) {
        component.putClientProperty(FOREGROUND_ROLE, tone);
        component.setForeground(tone.color());
        if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            text.setCaretColor(tone.color());
            text.setSelectionColor(ACCENT_FILL.color());
            text.setSelectedTextColor(TEXT.color());
        }
    }

    static void background(JComponent component, Tone tone) {
        component.putClientProperty(BACKGROUND_ROLE, tone);
        component.setBackground(tone.color());
    }

    private static void refresh(Component component) {
        if (component instanceof JComponent) {
            JComponent view = (JComponent) component;
            Tone foreground = (Tone) view.getClientProperty(FOREGROUND_ROLE);
            Tone background = (Tone) view.getClientProperty(BACKGROUND_ROLE);
            if (foreground != null) {
                foreground(view, foreground);
            }
            if (background != null) {
                background(view, background);
            }
            if (view instanceof JTable) {
                ((JTable) view).setGridColor(BORDER.color());
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                refresh(child);
            }
        }
    }

    static Border border(int top, int left, int bottom, int right) {
        return new MatteBorder(top, left, bottom, right, BORDER.color()) {
            @Override
            public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
                color = BORDER.color();
                super.paintBorder(component, graphics, x, y, width, height);
            }
        };
    }

    static Font font(int size, boolean bold) {
        return new Font(TEXT_FONT, bold ? Font.BOLD : Font.PLAIN, size);
    }

    static Font mono(int size) {
        return new Font(CODE_FONT, Font.PLAIN, size);
    }

    private static String availableFont(String preferred, String fallback) {
        return new Font(preferred, Font.PLAIN, 12).getFamily().equalsIgnoreCase(preferred)
                ? preferred : fallback;
    }

    static JLabel label(String text, int size, boolean bold, Tone color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font(size, bold));
        foreground(label, color);
        return label;
    }

    static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel column() {
        JPanel panel = panel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    static JPanel card(Tone color, int radius) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = antialias(graphics);
                g.setColor(color.color());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g.setColor(BORDER.color());
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        styleButton(button, primary ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY);
        return button;
    }

    static JToggleButton tab(String text, boolean navigation) {
        JToggleButton tab = new JToggleButton(text);
        styleButton(tab, navigation ? ButtonStyle.NAVIGATION : ButtonStyle.TAB);
        return tab;
    }

    private static void styleButton(AbstractButton button, ButtonStyle style) {
        boolean primary = style == ButtonStyle.PRIMARY;
        boolean navigation = style == ButtonStyle.NAVIGATION;
        button.setFont(font(navigation ? 15 : 13, true));
        foreground(button, primary ? INK : navigation ? MUTED : TEXT);
        button.setBorder(new EmptyBorder(navigation ? 9 : 8, 14, navigation ? 11 : 8, 14));
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                ButtonModel model = button.getModel();
                boolean active = model.isSelected();
                Color fill = (primary && model.isEnabled() ? ACCENT : active ? ACCENT_FILL : CONTROL).color();
                if (model.isEnabled()) {
                    if (model.isPressed()) {
                        fill = fill.darker();
                    } else if (model.isRollover()) {
                        fill = primary ? (dark ? ACCENT.color().brighter() : ACCENT.color().darker())
                                : (active ? ACCENT_FILL : BORDER).color();
                    }
                }
                Graphics2D g = antialias(graphics);
                if (navigation) {
                    if (model.isRollover() || button.hasFocus()) {
                        g.setColor(SURFACE.color());
                        g.fillRoundRect(0, 0, button.getWidth(), button.getHeight() - 3, 10, 10);
                    }
                    if (active || button.hasFocus()) {
                        g.setColor(ACCENT.color());
                        g.fillRoundRect(10, button.getHeight() - 3, button.getWidth() - 20, 3, 3, 3);
                    }
                } else {
                    Shape outline = new RoundRectangle2D.Float(1, 1, button.getWidth() - 2f,
                            button.getHeight() - 2f, 12, 12);
                    g.setColor(fill);
                    g.fill(outline);
                    g.setColor((button.hasFocus() || active ? ACCENT : BORDER).color());
                    g.draw(outline);
                }
                g.dispose();
                super.paint(graphics, component);
            }

            @Override
            protected void paintText(Graphics graphics, AbstractButton button, Rectangle bounds, String text) {
                if (button.isEnabled()) {
                    super.paintText(graphics, button, bounds, text);
                } else {
                    graphics.setColor(MUTED.color());
                    BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text, -1, bounds.x,
                            bounds.y + graphics.getFontMetrics().getAscent());
                }
            }
        });
        if (style == ButtonStyle.TAB || navigation) {
            button.addItemListener(event -> foreground(button, button.isSelected() ? ACCENT : navigation ? MUTED : TEXT));
        }
    }

    static <T> JComboBox<T> combo() {
        JComboBox<T> combo = new JComboBox<>();
        combo.setFont(font(13, false));
        foreground(combo, TEXT);
        background(combo, CONTROL);
        combo.setBorder(border(1, 1, 1, 1));
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = new JButton();
                arrow.setIcon(chevron());
                foreground(arrow, MUTED);
                background(arrow, CONTROL);
                arrow.setBorder(new EmptyBorder(0, 8, 0, 8));
                arrow.setContentAreaFilled(false);
                arrow.setFocusable(false);
                return arrow;
            }

            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = (BasicComboPopup) super.createPopup();
                popup.setBorder(border(1, 1, 1, 1));
                background(popup.getList(), CONTROL);
                return popup;
            }

            @Override
            public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean focused) {
                graphics.setColor(CONTROL.color());
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean focused) {
                Component cell = comboBox.getRenderer().getListCellRendererComponent(
                        listBox, comboBox.getSelectedItem(), -1, false, false);
                cell.setBackground(CONTROL.color());
                cell.setForeground((comboBox.isEnabled() ? TEXT : MUTED).color());
                currentValuePane.paintComponent(graphics, cell, comboBox,
                        bounds.x, bounds.y, bounds.width, bounds.height, true);
            }
        });
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                putClientProperty("html.disable", Boolean.TRUE);
                setFont(font(13, false));
                setBackground((selected ? ACCENT_FILL : CONTROL).color());
                setForeground((selected ? ACCENT : TEXT).color());
                setBorder(new EmptyBorder(5, 9, 5, 8));
                return this;
            }
        });
        combo.setPreferredSize(new Dimension(128, 34));
        return combo;
    }

    private static Icon chevron() {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return 10;
            }

            @Override
            public int getIconHeight() {
                return 8;
            }

            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = antialias(graphics);
                g.setColor(MUTED.color());
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{x + 1, x + 5, x + 9}, new int[]{y + 2, y + 6, y + 2}, 3);
                g.dispose();
            }
        };
    }

    static JTextField valueField(int size, boolean bold, boolean code, Tone color) {
        JTextField field = new JTextField(1);
        field.setEditable(false);
        field.setOpaque(false);
        field.setBorder(new EmptyBorder(1, 0, 1, 0));
        field.setFont(code ? mono(size) : font(size, bold));
        foreground(field, color);
        field.setDropTarget(null);
        return field;
    }

    static void styleTable(JTable table, int monoColumn) {
        Ui.background(table, Ui.SURFACE);
        Ui.foreground(table, Ui.TEXT);
        table.setFont(Ui.font(13, false));
        table.setRowHeight(37);
        table.setShowVerticalLines(false);
        table.setGridColor(Ui.BORDER.color());
        table.setFillsViewportHeight(true);
        table.setDropTarget(null);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                putClientProperty("html.disable", Boolean.TRUE);
                setBorder(new EmptyBorder(0, 9, 0, 9));
                Font preferred = column == monoColumn ? Ui.mono(12) : Ui.font(13, false);
                setFont(value != null && preferred.canDisplayUpTo(value.toString()) >= 0
                        ? new Font(Font.DIALOG, Font.PLAIN, preferred.getSize()) : preferred);
                setToolTipText(value == null ? null : " " + value);
                setForeground(Ui.TEXT.color());
                setBackground((selected ? Ui.ACCENT_FILL : row % 2 == 0 ? Ui.SURFACE : Ui.SIDEBAR).color());
                return this;
            }
        });

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 35));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setBackground(Ui.CONTROL.color());
                setForeground(Ui.MUTED.color());
                setFont(Ui.font(13, true));
                setBorder(new EmptyBorder(0, 9, 0, 9));
                return this;
            }
        });
    }

    static JScrollPane scroll(Component child) {
        JScrollPane pane = new JScrollPane(child);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.getVerticalScrollBar().setUI(new ScrollBar());
        pane.getHorizontalScrollBar().setUI(new ScrollBar());
        pane.getVerticalScrollBar().setUnitIncrement(18);
        return pane;
    }

    static Icon mark(int size, Tone color, boolean shield) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = antialias(graphics);
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(color.color());
                Path2D shape = new Path2D.Double();
                if (shield) {
                    shape.moveTo(12, 3);
                    shape.lineTo(20, 6);
                    shape.lineTo(20, 11.5);
                    shape.quadTo(20, 17.7, 12, 21);
                    shape.quadTo(4, 17.7, 4, 11.5);
                    shape.lineTo(4, 6);
                    shape.closePath();
                    shape.moveTo(8.5, 12);
                    shape.lineTo(11, 14.5);
                    shape.lineTo(15.5, 10);
                } else {
                    shape.moveTo(6, 3);
                    shape.lineTo(14, 3);
                    shape.lineTo(19, 8);
                    shape.lineTo(19, 21);
                    shape.lineTo(6, 21);
                    shape.closePath();
                    shape.moveTo(14, 3);
                    shape.lineTo(14, 8);
                    shape.lineTo(19, 8);
                    shape.moveTo(9, 13);
                    shape.lineTo(16, 13);
                    shape.moveTo(9, 16.5);
                    shape.lineTo(14, 16.5);
                }
                g.draw(shape);
                g.dispose();
            }
        };
    }

    private static Graphics2D antialias(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    private static class ScrollBar extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return hiddenButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return hiddenButton();
        }

        private JButton hiddenButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension());
            return button;
        }

        @Override
        public Dimension getPreferredSize(JComponent component) {
            JScrollBar bar = (JScrollBar) component;
            return bar.getOrientation() == Adjustable.VERTICAL ? new Dimension(11, 48) : new Dimension(48, 11);
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            graphics.setColor(BACKGROUND.color());
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !component.isEnabled()) {
                return;
            }
            Graphics2D g = antialias(graphics);
            g.setColor((isThumbRollover() ? MUTED : BORDER).color());
            g.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 8, 8);
            g.dispose();
        }
    }
}
