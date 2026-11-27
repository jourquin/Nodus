/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusPlugin;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.parquet.ParquetFiles;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveStringifier;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * Displays a read-only preview of a local Parquet file.
 *
 * <p>At startup, Nodus discovers this class in a JAR in its {@code plugins} directory and
 * supplies a {@link NodusMapPanel}. The map panel gives the plugin access to the main window
 * and the open project's folder for the file chooser.
 */
public class NodusParquetReaderPlugin extends NodusPlugin {
  private static final String TITLE = "Parquet Reader";
  private ReaderDialog dialog;

  /** Supplies the label and tells Nodus to put the item under Tools. */
  @Override
  public Properties getProperties() {
    Properties properties = new Properties();
    properties.setProperty(MENU_ITEM__TEXT, TITLE);
    properties.setProperty(MENUBAR_ID, Integer.toString(MENU_TOOLS));
    return properties;
  }

  /** Called when the user selects the menu item. Reuses the preview window while it is open. */
  @Override
  public void execute() {
    if (dialog == null || !dialog.isDisplayable()) {
      dialog = new ReaderDialog(getNodusMapPanel());
      dialog.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
              if (dialog == event.getWindow()) {
                dialog = null;
              }
            }
          });
    }
    dialog.setVisible(true);
    dialog.toFront();
  }

  /** Closes the preview when Nodus unloads the plugin and releases the map panel reference. */
  @Override
  public void dispose() {
    if (dialog != null) {
      dialog.dispose();
      dialog = null;
    }
    super.dispose();
  }

  /** Swing window that owns the file chooser, preview grid, and active file read. */
  private static final class ReaderDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final NodusMapPanel mapPanel;
    private final JSpinner rowLimit =
        new JSpinner(new SpinnerNumberModel(100, 1, Integer.MAX_VALUE, 1));
    private final JTable table = new JTable();
    private final JLabel status = new JLabel("Choose a Parquet file to preview.");
    private final Timer reload = new Timer(250, event -> loadPreview());
    private Path file;
    private SwingWorker<Preview, Void> worker;

    ReaderDialog(NodusMapPanel mapPanel) {
      // The final argument allows the user to keep working in Nodus while the preview is open.
      super(mapPanel.getMainFrame(), TITLE, false);
      this.mapPanel = mapPanel;
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);

      JButton open = new JButton("Open Parquet File...");
      open.addActionListener(event -> chooseFile());
      JLabel limitLabel = new JLabel("Rows to display");
      limitLabel.setLabelFor(rowLimit);
      ((JSpinner.DefaultEditor) rowLimit.getEditor()).getTextField().setColumns(7);
      // Wait for a short pause so several quick changes to the row limit trigger one reload.
      reload.setRepeats(false);
      rowLimit.addChangeListener(
          event -> {
            if (file != null) {
              reload.restart();
            }
          });

      JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
      controls.add(open);
      controls.add(limitLabel);
      controls.add(rowLimit);

      table.setPreferredScrollableViewportSize(new Dimension(800, 400));
      // Keep the fitted column widths and let the scroll pane handle wide tables.
      table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      // Treat field names and values from the file as plain text, including HTML-like strings.
      DefaultTableCellRenderer cells = new DefaultTableCellRenderer();
      cells.putClientProperty("html.disable", Boolean.TRUE);
      table.setDefaultRenderer(Object.class, cells);
      TableCellRenderer headers = table.getTableHeader().getDefaultRenderer();
      if (headers instanceof JComponent) {
        ((JComponent) headers).putClientProperty("html.disable", Boolean.TRUE);
      }

      JPanel content = new JPanel(new BorderLayout(0, 8));
      content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      content.add(controls, BorderLayout.NORTH);
      content.add(new JScrollPane(table), BorderLayout.CENTER);
      content.add(status, BorderLayout.SOUTH);
      setContentPane(content);
      pack();
      setLocationRelativeTo(getOwner());
    }

    private void chooseFile() {
      // Fall back to the last selected folder, or let Swing choose a default on the first use.
      File initialDirectory = file == null ? null : file.toFile().getParentFile();
      // Read the current project each time since it can change while the window is open.
      NodusProject project = mapPanel.getNodusProject();
      if (project != null && project.isOpen()) {
        String projectPath = project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
        if (projectPath != null && !projectPath.isBlank()) {
          initialDirectory = new File(projectPath);
        }
      }
      JFileChooser chooser = new JFileChooser(initialDirectory);
      chooser.setLocale(Locale.ENGLISH);
      chooser.updateUI();
      chooser.setDialogTitle("Open Parquet File");
      chooser.setFileFilter(new FileNameExtensionFilter("Parquet files (*.parquet)", "parquet"));
      if (chooser.showDialog(this, "Open") == JFileChooser.APPROVE_OPTION) {
        file = chooser.getSelectedFile().toPath().toAbsolutePath();
        reload.stop();
        loadPreview();
      }
    }

    /** Starts a background read so the window can keep responding while the preview loads. */
    private void loadPreview() {
      cancelLoad();
      // Give this read its own copy of the selection and row limit from the Swing event thread.
      Path source = file;
      int limit = (Integer) rowLimit.getValue();
      table.setModel(new DefaultTableModel());
      status.setText("Reading " + source.getFileName() + "...");
      worker =
          new SwingWorker<Preview, Void>() {
            @Override
            protected Preview doInBackground() throws IOException {
              return readPreview(source, limit, this::isCancelled);
            }

            @Override
            protected void done() {
              // Swing calls this on its event thread. Only the current read can update the grid.
              if (worker != this) {
                return;
              }
              worker = null;
              try {
                Preview preview = get();
                table.setModel(preview);
                fitColumnsToContent();
                status.setText(
                    String.format(
                        Locale.ENGLISH,
                        "Showing %,d of %,d rows from %s.",
                        preview.getRowCount(),
                        preview.totalRows,
                        source.getFileName()));
              } catch (ExecutionException error) {
                status.setText("Could not read " + source.getFileName() + ".");
                JOptionPane.showMessageDialog(
                    ReaderDialog.this,
                    status.getText() + "\n" + error.getCause(),
                    TITLE,
                    JOptionPane.ERROR_MESSAGE);
              } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                status.setText("The preview was interrupted.");
              }
            }
          };
      worker.execute();
    }

    /** Fits each column to the widest rendered header or value, plus a small margin. */
    private void fitColumnsToContent() {
      TableColumnModel columns = table.getColumnModel();
      TableCellRenderer headers = table.getTableHeader().getDefaultRenderer();
      int padding = columns.getColumnMargin() + 8;
      for (int index = 0; index < table.getColumnCount(); index++) {
        TableColumn column = columns.getColumn(index);
        Component header =
            headers.getTableCellRendererComponent(
                table, column.getHeaderValue(), false, false, -1, index);
        int width = header.getPreferredSize().width;
        for (int row = 0; row < table.getRowCount(); row++) {
          Component cell = table.prepareRenderer(table.getCellRenderer(row, index), row, index);
          width = Math.max(width, cell.getPreferredSize().width);
        }
        column.setPreferredWidth(width + padding);
      }
    }

    private void cancelLoad() {
      if (worker != null) {
        SwingWorker<Preview, Void> previous = worker;
        worker = null;
        // Let the background read finish its current file operation and close its own reader.
        previous.cancel(false);
      }
    }

    @Override
    public void dispose() {
      reload.stop();
      cancelLoad();
      super.dispose();
    }
  }

  /** Loads up to the requested row count using the same compression support as Nodus imports. */
  private static Preview readPreview(Path path, int limit, BooleanSupplier cancelled)
      throws IOException {
    try (ParquetFileReader reader = ParquetFiles.openReader(path)) {
      // The schema describes the column names and types used to reconstruct each row.
      MessageType schema = reader.getFileMetaData().getSchema();
      MessageColumnIO columns = new ColumnIOFactory().getColumnIO(schema);
      List<String[]> rows = new ArrayList<>();
      // A row group is a block of rows in the file. Read and release one block at a time.
      while (rows.size() < limit && !cancelled.getAsBoolean()) {
        try (PageReadStore pages = reader.readNextRowGroup()) {
          if (pages == null) {
            break;
          }
          RecordReader<Group> records =
              columns.getRecordReader(pages, new GroupRecordConverter(schema));
          for (long row = 0;
              row < pages.getRowCount() && rows.size() < limit && !cancelled.getAsBoolean();
              row++) {
            Group record = records.read();
            if (record == null) {
              throw new IOException("Unexpected end of Parquet row group.");
            }
            String[] values = new String[schema.getFieldCount()];
            for (int column = 0; column < values.length; column++) {
              values[column] = formatField(record, column);
            }
            rows.add(values);
          }
        }
      }
      // Use the file metadata for the total shown in the status line.
      return new Preview(schema, rows, reader.getRecordCount());
    }
  }

  private static String formatField(Group record, int column) {
    Type type = record.getType().getType(column);
    int count = record.getFieldRepetitionCount(column);
    // A repeated field can hold several values. Display them together as a list.
    if (type.isRepetition(Type.Repetition.REPEATED)) {
      StringJoiner values = new StringJoiner(", ", "[", "]");
      for (int index = 0; index < count; index++) {
        values.add(formatValue(record, type, column, index));
      }
      return values.toString();
    }
    // Swing displays a missing top-level value as an empty cell.
    return count == 0 ? null : formatValue(record, type, column, 0);
  }

  private static String formatValue(Group record, Type type, int column, int index) {
    if (!type.isPrimitive()) {
      // Keep a nested group in a single cell, with field names showing its structure.
      Group group = record.getGroup(column, index);
      GroupType fields = group.getType();
      StringJoiner values = new StringJoiner(", ", "{", "}");
      for (int field = 0; field < fields.getFieldCount(); field++) {
        values.add(fields.getFieldName(field) + "=" + formatField(group, field));
      }
      return values.toString();
    }

    PrimitiveType primitive = type.asPrimitiveType();
    // Parquet's formatter uses type annotations to display dates, decimals, and text correctly.
    PrimitiveStringifier formatter = primitive.stringifier();
    switch (primitive.getPrimitiveTypeName()) {
      case BOOLEAN:
        return formatter.stringify(record.getBoolean(column, index));
      case INT32:
        return formatter.stringify(record.getInteger(column, index));
      case INT64:
        return formatter.stringify(record.getLong(column, index));
      case FLOAT:
        return formatter.stringify(record.getFloat(column, index));
      case DOUBLE:
        return formatter.stringify(record.getDouble(column, index));
      case BINARY:
      case FIXED_LEN_BYTE_ARRAY:
        return formatter.stringify(record.getBinary(column, index));
      case INT96:
        return formatter.stringify(record.getInt96(column, index));
      default:
        return record.getValueToString(column, index);
    }
  }

  /** Supplies headers and values to {@link JTable}, using the base model's read-only cells. */
  private static final class Preview extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private final MessageType schema;
    private final List<String[]> rows;
    private final long totalRows;

    Preview(MessageType schema, List<String[]> rows, long totalRows) {
      this.schema = schema;
      this.rows = rows;
      this.totalRows = totalRows;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return schema.getFieldCount();
    }

    @Override
    public String getColumnName(int column) {
      return schema.getFieldName(column);
    }

    @Override
    public String getValueAt(int row, int column) {
      return rows.get(row)[column];
    }
  }
}
