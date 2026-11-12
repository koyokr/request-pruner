package kr.koyo.requestpruner;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/** The Request Pruner suite tab: add, remove, enable and disable rules in the removal list. */
final class RulePanel {
    private static final String[] COLUMNS = {"Enabled", "Cookie", "Type", "Comment"};

    private final HeaderRules rules;
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final java.util.List<Row> rows = new java.util.ArrayList<>();
    private record Row(String group, int index) {}

    private void rebuildRows() {
        rows.clear();
        var entries = rules.entries();
        String query = filter.getText().strip().toLowerCase(Locale.ROOT);
        // Cookies show the Custom vendor first; the sort is stable, so everything else keeps its order.
        var order = IntStream.range(0, entries.size()).boxed()
                .sorted(Comparator.comparing(i -> rules.cookies && entries.get(i).builtIn())).toList();
        String previous = null;
        for (int i : order) {
            var entry = entries.get(i);
            boolean groupMatches = rules.cookies && contains(entry.group(), query);
            if (!groupMatches && !contains(entry.rule().name(), query)) continue;
            if (rules.cookies && !entry.group().equals(previous)) {
                rows.add(new Row(entry.group(), -1));
                previous = entry.group();
            }
            // A filter shows its matches without expanding the vendor first.
            if (!rules.cookies || expanded.contains(entry.group()) || !query.isEmpty()) {
                rows.add(new Row(entry.group(), i));
            }
        }
    }

    private static boolean contains(String text, String query) {
        return text.toLowerCase(Locale.ROOT).contains(query);
    }

    private void refresh() {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        rebuildRows();
        model.fireTableDataChanged();
    }

    private final JPanel component = new JPanel(new BorderLayout(0, 8));
    private final JTextField input = new JTextField(28);
    private final JTextField filter = new JTextField(16);
    private final JLabel status = new JLabel(" ");
    private final JButton removeButton = new JButton("Remove");

    private final AbstractTableModel model = new AbstractTableModel() {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || ((column == 1 || column == 3) && rows.get(row).index() >= 0
                    && !rules.entries().get(rows.get(row).index()).builtIn());
        }

        @Override
        public Object getValueAt(int row, int column) {
            Row item = rows.get(row);
            if (item.index() < 0) {
                var members = rules.entries().stream().filter(e -> e.group().equals(item.group())).toList();
                long enabled = members.stream().filter(HeaderRules.Entry::enabled).count();
                return switch (column) {
                    case 0 -> enabled == members.size();
                    case 1 -> (expanded.contains(item.group()) ? "▼ " : "▶ ") + item.group();
                    case 2 -> enabled + "/" + members.size() + " enabled";
                    default -> CookieDefaults.groupComment(item.group(), members.getFirst().comment());
                };
            }
            HeaderRules.Entry entry = rules.entries().get(item.index());
            return switch (column) {
                case 0 -> entry.enabled();
                case 1 -> entry.rule().toString();
                case 2 -> entry.builtIn() ? "Default" : "Custom";
                default -> entry.comment();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean enabled) {
                Row item = rows.get(row);
                if (item.index() < 0) rules.setGroupEnabled(item.group(), enabled);
                else rules.setEnabled(item.index(), enabled);
                fireTableDataChanged();
            } else if (column == 1 && value instanceof String name && rows.get(row).index() >= 0) {
                try {
                    HeaderRule rule = HeaderRule.parse(name);
                    rules.rename(rows.get(row).index(), rule);
                    rebuildRows();
                    fireTableDataChanged();
                    fitColumns();
                    status.setText("Renamed to " + rule + ".");
                } catch (IllegalArgumentException ex) {
                    status.setText(ex.getMessage());
                }
            } else if (column == 3 && value instanceof String comment && rows.get(row).index() >= 0) {
                rules.setComment(rows.get(row).index(), comment);
                fireTableRowsUpdated(row, row);
            }
        }
    };
    private final JTable table = new JTable(model) {
        @Override public String getToolTipText(java.awt.event.MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            return row < 0 ? null : model.getValueAt(row, 3).toString();
        }
    };

    RulePanel(HeaderRules rules) {
        this.rules = rules;
        rebuildRows();
        if (!rules.cookies) table.getColumnModel().getColumn(1).setHeaderValue("Header");
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                // Only the first click of a double-click toggles, so the vendor does not flip back.
                if (event.getClickCount() == 1 && row >= 0 && table.columnAtPoint(event.getPoint()) == 1
                        && rows.get(row).index() < 0) {
                    String group = rows.get(row).group();
                    if (!expanded.remove(group)) expanded.add(group);
                    refresh();
                }
            }
        });

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // Pin Enabled, Name and Type to their content so Comment takes the remaining width.
        fitColumns();
        // Burp applies its theme font after construction, so refit when the font changes.
        table.addPropertyChangeListener("font", e -> fitColumns());
        table.getSelectionModel().addListSelectionListener(e -> updateRemoveButton());

        var addButton = new JButton("Add");
        addButton.addActionListener(e -> add());
        input.addActionListener(e -> add());
        removeButton.addActionListener(e -> remove());
        updateRemoveButton();

        var list = new JScrollPane(table);
        list.setPreferredSize(new Dimension(500, 250));

        var addRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        addRow.add(new JLabel(rules.cookies ? "Cookie:" : "Header:"));
        addRow.add(input);
        addRow.add(addButton);
        addRow.add(removeButton);

        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        var title = new JLabel(rules.cookies ? "Cookies to remove" : "Headers to remove");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(new JLabel(rules.cookies
                ? "Names are case-sensitive. Click a vendor to expand; its checkbox toggles all."
                : "Names ignore case. Add a name or Prefix-*."));
        header.add(new JLabel("Default entries can be toggled, not removed. Double-click a custom name or comment to edit."));
        header.add(Box.createVerticalStrut(6));
        header.add(toolbar());
        Arrays.stream(header.getComponents()).forEach(c -> ((JComponent) c).setAlignmentX(0));

        var footer = new JPanel(new BorderLayout(0, 4));
        footer.add(addRow, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);

        var content = new JPanel(new BorderLayout(0, 8));
        content.add(header, BorderLayout.NORTH);
        content.add(list, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        component.add(content, BorderLayout.CENTER);
    }

    private JPanel toolbar() {
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        var search = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        search.add(new JLabel("Filter:"));
        search.add(filter);

        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        if (rules.cookies) {
            var expandAll = new JButton("Expand all");
            expandAll.addActionListener(e -> {
                rules.entries().forEach(entry -> expanded.add(entry.group()));
                refresh();
            });
            var collapseAll = new JButton("Collapse all");
            collapseAll.addActionListener(e -> {
                expanded.clear();
                refresh();
            });
            buttons.add(expandAll);
            buttons.add(collapseAll);
        }
        var reset = new JButton("Reset to defaults");
        reset.setToolTipText("Restore the default on/off state of Default entries. Custom entries are kept.");
        reset.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(component,
                    "Restore the default on/off state of all Default entries?\nCustom entries are kept.",
                    "Reset to defaults", JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION) return;
            rules.resetDefaults();
            refresh();
            status.setText("Default entries reset.");
        });
        buttons.add(reset);

        var toolbar = new JPanel(new BorderLayout());
        toolbar.add(search, BorderLayout.WEST);
        toolbar.add(buttons, BorderLayout.EAST);
        return toolbar;
    }

    JPanel component() {
        return component;
    }

    private void fitColumns() {
        fitColumn(0);
        fitColumn(1, rules.entries().stream()
                .flatMap(e -> Stream.of(e.rule().toString(), "▼ " + e.group())).toArray(String[]::new));
        fitColumn(2, rules.cookies ? "88/88 enabled" : "Default");
    }

    private void fitColumn(int column, String... samples) {
        var col = table.getColumnModel().getColumn(column);
        var metrics = table.getFontMetrics(table.getFont());
        int width = table.getFontMetrics(table.getTableHeader().getFont()).stringWidth(col.getHeaderValue().toString());
        for (String sample : samples) width = Math.max(width, metrics.stringWidth(sample));
        col.setMinWidth(width + 16);
        col.setMaxWidth(width + 16);
        col.setPreferredWidth(width + 16);
    }

    private void add() {
        try {
            HeaderRule rule = HeaderRule.parse(input.getText());
            rules.add(rule);
            expanded.add("Custom");
            filter.setText("");
            refresh();
            int added = rules.entries().size() - 1;
            int row = IntStream.range(0, rows.size()).filter(i -> rows.get(i).index() == added).findFirst().orElseThrow();
            fitColumns();
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
            input.setText("");
            status.setText("Added " + rule + ".");
        } catch (IllegalArgumentException ex) {
            status.setText(ex.getMessage());
        }
        input.requestFocusInWindow();
    }

    private void remove() {
        var selected = Arrays.stream(table.getSelectedRows()).map(row -> rows.get(row).index()).boxed().collect(Collectors.toSet());
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        rules.removeCustom(selected);
        refresh();
        status.setText("Removed " + selected.size() + (selected.size() == 1 ? " entry." : " entries."));
    }

    /** Remove is offered only when every selected entry is a custom one. */
    private void updateRemoveButton() {
        int[] rows = Arrays.stream(table.getSelectedRows()).filter(row -> row < this.rows.size()).toArray();
        boolean anyDefault = Arrays.stream(rows).anyMatch(row -> (this.rows.get(row).index() < 0 || rules.entries().get(this.rows.get(row).index()).builtIn()));
        removeButton.setEnabled(rows.length > 0 && !anyDefault);
        removeButton.setToolTipText(anyDefault ? "Default entries can be turned on or off but not removed." : null);
    }
}
