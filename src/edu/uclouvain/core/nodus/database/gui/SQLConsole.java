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

package edu.uclouvain.core.nodus.database.gui;

import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.CLEARSCENARIO;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXPORTCSV;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXPORTCSVH;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXPORTDBF;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXPORTXLS;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXPORTXLSX;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.EXTRACTSHP;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.IMPORTCSV;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.IMPORTCSVH;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.IMPORTDBF;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.IMPORTXLS;
import static edu.uclouvain.core.nodus.database.gui.SQLConsoleOperations.IMPORTXLSX;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.GUIUtils;
import edu.uclouvain.core.nodus.swing.GridSwing;
import edu.uclouvain.core.nodus.swing.TableSorter;
import edu.uclouvain.core.nodus.utils.NodusFileFilter;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * SQL console for Nodus. - With support for Nodus specific commands,<br>
 * - Allows the execution of SQL batch files; <br>
 * - Generates SQL statements to gather statistics from Nodus assignments (quantities per mode,
 * ...). <br>
 *
 * <p>List specific (case insensitive) commands understood by Nodus:<br>
 * - CLEARSCENARIO num: Deletes all the tables related to the given scenario id.<br>
 * - CLRSCR : Clears the output console.<br>
 * - DISABLEECHO : If used, the SQL commands will not be echoed in the result area.<br>
 * - DISABLEHEADERS : If used, the headers of SQL outputs will not be displayed.<br>
 * - DISPLAYGRID : Forces the next SQL output to be displayed in grid format.<br>
 * - DISPLAYTEXT : Forces the next SQL output to be displayed in text format.<br>
 * - ENABLEECHO : If used, the SQL commands will be echoed in the result area (default).<br>
 * - ENABLEHEADERS : If used, the headers of SQL outputs will be displayed (default).<br>
 * - EXPORTCSV tableName : Exports a table in CSV format.<br>
 * - EXPORTCSVH tableName : Exports a table in CSV format, the first line containing the field names
 * (header line).<br>
 * - EXPORTDBF tableName : Exports a table in DBF format.<br>
 * - EXPORTXLS tableName : Exports a table in XLS format.<br>
 * - EXPORTXLSX tableName : Exports a table in XLSX format.<br>
 * - EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE sqlCondition : Creates a new shapefile from the
 * output of the SQL where statement performed on a shapefile.<br>
 * - IMPORTCSV tableName : Imports a CSV file. The related empty table must exist in the database.
 * <br>
 * - IMPORTCSVH tableName : Imports a CSV file, ignoring the first (header) line. The related empty
 * table must exists in the database.<br>
 * - IMPORTDBF tableName : Imports a DBF file.<br>
 * - IMPORTXLS tableName : Imports a XLS file. The related empty table must exist in the database
 * unless the first row of the sheet contains the format of each column, following the DBF standard.
 * <br>
 * - IMPORTXLSX tableName : Imports a XLSX file. The related empty table must exist in the database
 * unless the first row of the sheet contains the format of each column, following the DBF standard.
 * <br>
 * - STOP : Convenient command that stops a script. Useful for debugging. <br>
 *
 * <p>Direct import commands ask before replacing existing tables; direct export commands and
 * EXTRACTSHP ask before overwriting files. Loaded scripts, multi-command batches, and runBatch
 * calls overwrite without prompting.
 *
 * @author Bart Jourquin
 */
public class SQLConsole implements ActionListener, WindowListener, KeyListener {

  private static final String CLEARSCREEN = "CLRSCR";

  private static String defDirectory;

  private static final String DISABLEECHO = "DISABLEECHO";

  private static final String DISABLEHEADERS = "DISABLEHEADERS";

  private static final String DISPLAYGRID = "DISPLAYGRID";

  private static boolean displayHeaders = true;

  private static final String DISPLAYTEXT = "DISPLAYTEXT";

  private static final String ENABLEECHO = "ENABLEECHO";

  private static final String ENABLEHEADERS = "ENABLEHEADERS";

  private static I18n i18n = Environment.getI18n();

  private static final String STOP = "STOP";

  private static int maxRows = 1000;

  private static final String NL = System.getProperty("line.separator");

  private DatabaseMetaData metaData;

  private JSplitPane ewSplitPane;

  private JFrame frame;

  private GridSwing gridResultArea;

  private JScrollPane resultScrollPane;

  private SQLConsoleHistory history;

  int typeOfResultFormat; // 0: grid; 1: text

  private Color colorButton;

  private JButton jbuttonExecute;

  private Connection jdbcConnection;

  private JMenu menuRecent;

  private JMenuItem menuResultInGrid;

  private JMenuItem menuResultInText;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private SQLConsoleOperations operations;

  private JSplitPane nsSplitPane;

  private Cursor oldCursor;

  private JPanel resultPanel = new JPanel();

  private DefaultMutableTreeNode rootNode;

  private String scriptFileName = null;

  private TableSorter sorter;

  private JTable resultTable;

  private RSyntaxTextArea sqlCommandsArea;

  private Statement statement;

  private StatDlg statDlg = null;

  private Style style;

  private StyledDocument doc;

  private DefaultTreeModel treeModel;

  private boolean treeMustBeRefreshed = true;

  private JScrollPane treeScrollPane;

  private JTree tree;

  private RTextScrollPane txtCommandScroll;

  private JTextPane txtResultArea;

  private JScrollPane txtResultScroll;

  private boolean withEcho = true;

  private boolean withGUI = true;

  /**
   * Displays the SQL console and connects it to the project database.
   *
   * @param nodusProject The Nodus project.
   */
  public SQLConsole(NodusProject nodusProject) {
    this(nodusProject, true);
  }

  /**
   * Creates the SQL console and connects it to the project database.
   *
   * @param nodusProject The Nodus project.
   * @param withGUI If false, the GUI will not be displayed, and the user can call runBatch to
   *     execute an SQL batch.
   */
  public SQLConsole(NodusProject nodusProject, boolean withGUI) {

    this.withGUI = withGUI;

    if (withGUI) {
      // Only create a console if none exists
      Frame[] frames = Frame.getFrames();

      for (Frame element : frames) {
        if (element instanceof JFrame) {
          JFrame f = (JFrame) element;
          if (f.getName().equals(this.getClass().getName()) && f.isVisible()) {
            f.requestFocus();
            return;
          }
        }
      }
    }

    this.nodusProject = nodusProject;
    nodusMapPanel = nodusProject.getNodusMapPanel();
    operations = new SQLConsoleOperations(this, nodusProject, withGUI);

    defDirectory = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    // Get the max rows for SQL query results
    maxRows =
        nodusMapPanel == null
            ? 1000
            : Integer.parseInt(
                nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_MAX_SQL_ROWS, "1000"));

    if (withGUI) {
      initialize();
    } else {
      // Batch execution needs an editor and a result model, but no desktop window.
      sqlCommandsArea = new RSyntaxTextArea();
      gridResultArea = new GridSwing();
    }

    jdbcConnection = nodusProject.getMainJDBCConnection();
    try {
      metaData = jdbcConnection.getMetaData();
      openStatement();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (withGUI) {
      refreshTree();
    }
  }

  /** Closes the shared statement used by this console. */
  private void closeStatement() {
    if (statement == null) {
      return;
    }

    try {
      statement.close();
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      statement = null;
    }
  }

  /**
   * Opens the shared statement if it is not already available.
   *
   * @return True if the statement is ready to execute SQL.
   */
  private boolean openStatement() {
    if (statement != null) {
      return true;
    }

    try {
      statement = jdbcConnection.createStatement();
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Used to recall a command saved in the history.
   *
   * @param ev ActionEvent
   * @hidden
   */
  @Override
  public void actionPerformed(ActionEvent ev) {
    String s = ev.getActionCommand();

    if (s == null) {
      if (ev.getSource() instanceof JMenuItem) {
        s = ((JMenuItem) ev.getSource()).getText();
      }
    }

    if (s != null && s.startsWith("#")) {
      int i = Integer.parseInt(s.substring(1));
      sqlCommandsArea.setText(history.get(i));
      resetScript();
    }
  }

  /** Records successful commands in the GUI history. */
  void addToRecent(String command) {
    if (withGUI) {
      history.addToRecent(command);
    }
  }

  /** Clears the command text area. */
  private void clearCommands() {
    sqlCommandsArea.setText("");
    resetScript();
  }

  /**
   * Creates the tool bar.
   *
   * @return JToolBar
   */
  private JToolBar createToolBar() {

    JButton jbuttonClear =
        new JButton(i18n.get(SQLConsole.class, "Clear_SQL_Statement", "Clear SQL Statement"));

    jbuttonClear.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionevent) {
            clearCommands();
          }
        });

    jbuttonExecute =
        new JButton(i18n.get(SQLConsole.class, "Execute_SQL_Statement", "Execute SQL Statement"));
    colorButton = jbuttonExecute.getForeground();
    jbuttonExecute.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionevent) {
            executeSQLStatements();
          }
        });

    JButton jbuttonStatistics = new JButton(i18n.get(SQLConsole.class, "Statistics", "Statistics"));

    final SQLConsole _this = this;
    jbuttonStatistics.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionevent) {
            statDlg = new StatDlg(nodusProject, _this);
            statDlg.setVisible(true);
          }
        });

    JToolBar toolbar = new JToolBar();
    toolbar.addSeparator();
    toolbar.add(jbuttonClear);
    toolbar.addSeparator();
    toolbar.add(jbuttonExecute);
    toolbar.addSeparator();
    toolbar.add(jbuttonStatistics);
    jbuttonClear.setAlignmentY(0.5F);
    jbuttonClear.setAlignmentX(0.5F);
    jbuttonExecute.setAlignmentY(0.5F);
    jbuttonExecute.setAlignmentX(0.5F);
    jbuttonStatistics.setAlignmentY(0.5F);
    jbuttonStatistics.setAlignmentX(0.5F);

    return toolbar;
  }

  /**
   * Displays a message in the result panel.
   *
   * @param head String that will be displayed as header
   * @param msg Message to display
   */
  void displayMessageInResult(String head, String msg) {
    runOnEdtAndWait(
        () -> {
          // For grid result
          gridResultArea.clear();

          String[] g = new String[1];
          g[0] = head;
          gridResultArea.setHead(g);

          g[0] = msg;
          gridResultArea.addRow(g);
          if (typeOfResultFormat == 1) {
            updateTextResult();
          }
        });
  }

  /**
   * Main routine that executes a single SQL statement or batch file. It is also the place where the
   * Nodus specific commands are intercepted and handled.
   *
   * @param batch True when called explicitly through runBatch
   * @return True on success
   */
  boolean execute(boolean batch) {
    setBusy(true);

    if (!openStatement()) {
      setBusy(false);
      return false;
    }

    // Decompose all the statements
    String sqlCommandsText = getSqlCommandsText();
    SQLConsoleScript.ParsedSQLCommands parsed = SQLConsoleScript.parseSQLCommands(sqlCommandsText);
    if (parsed.batchFile) {
      menuResultInText_actionPerformed(null);
    }
    final boolean confirmOverwrite = !batch && scriptFileName == null && !parsed.multipleCommands;
    Vector<String> sqlCommands = parsed.commands;

    // Limit the output length of a single line query
    try {
      if (sqlCommands.size() > 1) {
        statement.setMaxRows(0);
      } else {
        statement.setMaxRows(maxRows);
      }
    } catch (SQLException e1) {
      e1.printStackTrace();
      setBusy(false);
      return false;
    }

    // Clear the output zone
    if (withGUI) {
      treeMustBeRefreshed = false;
      clearGuiResults();
    }

    boolean error = false;
    for (String sqlCommand : sqlCommands) {

      if (error) {
        break;
      }

      String sync = sqlCommand.trim().toUpperCase();

      if (SQLConsoleScript.isCommand(sync, ENABLEECHO)) {
        withEcho = true;
        continue;
      }

      if (SQLConsoleScript.isCommand(sync, DISABLEECHO)) {
        withEcho = false;
        continue;
      }

      // Display the command if text display
      if (withGUI) {
        if (withEcho) {
          addToTxtResultArea(sqlCommand + NL, true);
        }
      } else {
        System.out.println(sqlCommand);
      }

      // Intercept Nodus specific commands commands

      if (SQLConsoleScript.isCommand(sync, STOP)) {
        break;
      }

      // Create, Drop, Alter and Rename table commands need to refresh table list
      if ((SQLConsoleScript.startsWithCommand(sync, "CREATE TABLE")
              || SQLConsoleScript.startsWithCommand(sync, "ALTER TABLE")
              || SQLConsoleScript.startsWithCommand(sync, "DROP TABLE")
              || SQLConsoleScript.startsWithCommand(sync, "RENAME TABLE"))
          && withGUI) {
        treeMustBeRefreshed = true;
      }

      // "shutdown compact" is not allowed from the console
      if (SQLConsoleScript.startsWithCommand(sync, "SHUTDOWN COMPACT")) {
        showMessageDialogOnEdt(
            i18n.get(
                SQLConsole.class,
                "Shutdown_not_allowed",
                "'SHUTDOWN COMPACT' not allowed from within the SQL console"),
            NodusC.APPNAME,
            JOptionPane.WARNING_MESSAGE);
        continue;
      }

      if (SQLConsoleScript.isCommand(sync, ENABLEHEADERS)) {
        displayHeaders = true;
        continue;
      }

      if (SQLConsoleScript.isCommand(sync, DISABLEHEADERS)) {
        displayHeaders = false;
        continue;
      }

      if (SQLConsoleScript.isCommand(sync, CLEARSCREEN) && withGUI) {
        clearTextResultArea();
        continue;
      }

      if (SQLConsoleScript.isCommand(sync, DISPLAYGRID) && withGUI) {
        runOnEdtAndWait(menuResultInGrid::doClick);

        continue;
      }

      if (SQLConsoleScript.isCommand(sync, DISPLAYTEXT) && withGUI) {
        runOnEdtAndWait(menuResultInText::doClick);
        continue;
      }

      if (SQLConsoleScript.startsWithCommand(sync, CLEARSCENARIO)) {
        operations.clearScenario(sqlCommand);
        treeMustBeRefreshed = true;
        continue;
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXPORTDBF)) {
        boolean b = operations.importExport(sqlCommand, EXPORTDBF, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, IMPORTDBF)) {
        boolean b = operations.importExport(sqlCommand, IMPORTDBF, confirmOverwrite);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, IMPORTCSVH)) {
        boolean b = operations.importExport(sqlCommand, IMPORTCSVH, confirmOverwrite);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, IMPORTCSV)) {
        boolean b = operations.importExport(sqlCommand, IMPORTCSV, confirmOverwrite);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXPORTCSVH)) {
        boolean b = operations.importExport(sqlCommand, EXPORTCSVH, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXPORTCSV)) {
        boolean b = operations.importExport(sqlCommand, EXPORTCSV, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, IMPORTXLSX)) {
        boolean b = operations.importExport(sqlCommand, IMPORTXLSX, confirmOverwrite);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXPORTXLSX)) {
        boolean b = operations.importExport(sqlCommand, EXPORTXLSX, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, IMPORTXLS)) {
        boolean b = operations.importExport(sqlCommand, IMPORTXLS, confirmOverwrite);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXPORTXLS)) {
        boolean b = operations.importExport(sqlCommand, EXPORTXLS, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, EXTRACTSHP)) {
        boolean b = operations.extractShp(sqlCommand, confirmOverwrite);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      // Intercept delete/insert commands for nodus layers
      if (SQLConsoleScript.startsWithCommand(sync, "DELETE")) {
        boolean b = operations.isDeleteAllowed(sqlCommand);

        if (!b) {
          setBusy(false);
          return false;
        }
      }

      if (SQLConsoleScript.startsWithCommand(sync, "INSERT")) {
        boolean b = operations.isInsertAllowed(sqlCommand);

        if (!b) {
          setBusy(false);
          return false;
        }
      }

      clearGridResultArea();

      String[] g = new String[1];

      try {
        statement.execute(sqlCommand);

        int r = statement.getUpdateCount();

        if (r == -1) {
          // Is a ResultSet
          formatResultSet(statement, maxRows);
        } else {
          // Is an update
          g[0] = "update count";
          if (!withGUI) {
            System.out.print(g[0] + ": ");
          }

          g[0] = "" + r;
          if (withGUI) {
            showSingleValueResult("update count", g[0]);
          } else {
            System.out.println(g[0]);
          }
        }

        if (sqlCommands.size() <= 1) {
          addToRecent(sqlCommandsText);
        }

        if (!jdbcConnection.getAutoCommit()) {
          jdbcConnection.commit();
        }

      } catch (SQLException e) {

        error = true;
        String s = e.getMessage() + "\n";
        s += sqlCommand + "\n";
        s += " / Error Code: " + e.getErrorCode();
        s += " / State: " + e.getSQLState();

        if (withGUI) {
          showSingleValueResult("SQL Error", s);
        } else {
          System.err.println(s);
        }
      }

      updateTextResult();
    } // end of list iteration

    setBusy(false);
    if (error) {
      return false;
    }
    return true;
  }

  /**
   * Creates at thread in which the SQL statements are executed. Refresh the tree if needed and play
   * a sound if the query execution time was a longer than 5 seconds.
   */
  private void executeSQLStatements() {

    final long start = System.currentTimeMillis();
    final int pos = sqlCommandsArea.getCaretPosition();

    SwingWorker<Boolean, Void> worker =
        new SwingWorker<Boolean, Void>() {
          @Override
          protected Boolean doInBackground() {
            return SQLConsole.this.execute(false);
          }

          @Override
          protected void done() {
            gridResultArea.fireTableChanged(null);

            // Deselect commands
            int end = sqlCommandsArea.getSelectionEnd();
            sqlCommandsArea.setSelectionStart(end);
            sqlCommandsArea.setSelectionEnd(end);
            sqlCommandsArea.setCaretPosition(pos);
            sqlCommandsArea.moveCaretPosition(pos);

            // Play a sound if query was long
            long stop = System.currentTimeMillis();
            long duration = (stop - start) / 1000;
            if (duration >= 5) {
              nodusMapPanel.getSoundPlayer().play(SoundPlayer.SOUND_DING);
            }

            if (treeMustBeRefreshed) {
              refreshTree();
            }
          }
        };

    worker.execute();
  }

  /**
   * Formats a ResultSet to display.
   *
   * @param stmt Statement
   * @param maxRows int
   */
  private void formatResultSet(Statement stmt, int maxRows) {
    try {
      SQLConsoleResults.FormattedResult result = SQLConsoleResults.read(stmt, maxRows);
      if (result == null) {
        showSingleValueResult(
            i18n.get(SQLConsole.class, "Result", "Result"),
            i18n.get(SQLConsole.class, "empty", "(empty)"));
      } else {
        applyFormattedResultSet(result.header, result.rows, result.maxRowsReached);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns the Frame of this SQL console.
   *
   * @return Frame.
   */
  public JFrame getFrame() {
    return frame;
  }

  /**
   * Returns the command area component of this SQL console.
   *
   * @return JTextArea.
   */
  public JTextArea getSqlCommandArea() {
    return sqlCommandsArea;
  }

  /** Runs file and table overwrite confirmations on the EDT before any destructive work. */
  boolean confirmOverwrite(String message) {
    FutureTask<Integer> prompt = new FutureTask<>(() -> showOverwriteDialog(message));
    try {
      // Commands run on a worker; a console without its own window can also run desktop batches.
      if (SwingUtilities.isEventDispatchThread()) {
        prompt.run();
      } else {
        SwingUtilities.invokeAndWait(prompt);
      }
      return prompt.get() == JOptionPane.YES_OPTION;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * Displays the overwrite prompt on the EDT, with Cancel selected by default.
   *
   * @param message The localized message identifying the table or files to replace.
   * @return The selected option, or CLOSED_OPTION when confirmation is unavailable.
   */
  protected int showOverwriteDialog(String message) {
    if (GraphicsEnvironment.isHeadless()) {
      return JOptionPane.CLOSED_OPTION;
    }
    Object[] options = {
      i18n.get(SQLConsole.class, "Replace", "Replace"),
      i18n.get(SQLConsole.class, "Cancel", "Cancel")
    };
    return JOptionPane.showOptionDialog(
        frame,
        message,
        i18n.get(SQLConsole.class, "Confirm_overwrite", "Confirm overwrite"),
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE,
        null,
        options,
        options[1]);
  }

  /** Initializes main GUI. */
  private void initGUI() {
    JPanel commandPanel = new JPanel();

    // resultPanel = new JPanel();
    nsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, commandPanel, resultPanel);
    nsSplitPane.setContinuousLayout(true);

    commandPanel.setLayout(new BorderLayout());
    resultPanel.setLayout(new BorderLayout());

    sqlCommandsArea = new RSyntaxTextArea();
    sqlCommandsArea.setSyntaxEditingStyle("text/NodusSQL");
    sqlCommandsArea.setHighlightCurrentLine(true);
    sqlCommandsArea.setTabSize(4);
    sqlCommandsArea.setMargin(new Insets(5, 5, 5, 5));
    sqlCommandsArea.addKeyListener(this);
    txtCommandScroll = new RTextScrollPane(sqlCommandsArea);
    commandPanel.add(txtCommandScroll, BorderLayout.CENTER);

    txtResultArea = new JTextPane();
    txtResultArea.setEditable(false);
    txtResultArea.setMargin(new Insets(5, 5, 5, 5));
    txtResultArea.setFont(new Font("monospaced", Font.PLAIN, 12));
    JPanel noWrapPanel = new JPanel(new BorderLayout());
    noWrapPanel.add(txtResultArea);
    txtResultScroll = new JScrollPane(noWrapPanel);
    txtResultScroll.getVerticalScrollBar().setUnitIncrement(16);

    doc = txtResultArea.getStyledDocument();
    style = txtResultArea.addStyle("SQL result", null);

    // Apply theme
    try (InputStream in = NodusMapPanel.class.getResourceAsStream("eclipse.xml")) {
      if (in != null) {
        Theme theme = Theme.load(in);
        theme.apply(sqlCommandsArea);
      }
    } catch (IOException ioe) { // Should never happen
      ioe.printStackTrace();
    }

    gridResultArea = new GridSwing();
    sorter = new TableSorter(gridResultArea);
    resultTable = new JTable(sorter);
    resultScrollPane = new JScrollPane(resultTable);

    resultPanel.add(resultScrollPane, BorderLayout.CENTER);

    // Set up the tree
    rootNode = new DefaultMutableTreeNode("Connection");
    treeModel = new DefaultTreeModel(rootNode);
    tree = new JTree(treeModel);
    treeScrollPane = new JScrollPane(tree);

    treeScrollPane.setPreferredSize(new Dimension(120, 400));
    treeScrollPane.setMinimumSize(new Dimension(70, 100));
    txtCommandScroll.setPreferredSize(new Dimension(360, 100));
    txtCommandScroll.setMinimumSize(new Dimension(180, 100));
    resultScrollPane.setPreferredSize(new Dimension(460, 300));

    ewSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, nsSplitPane);
    ewSplitPane.setContinuousLayout(true);

    frame.getContentPane().add(ewSplitPane, BorderLayout.CENTER);
    frame.doLayout();
    frame.pack();
    oldCursor = frame.getCursor();
  }

  /** Creates the GUI. */
  // @SuppressWarnings("deprecation")
  void initialize() {

    frame = new JFrame(NodusC.APPNAME);
    frame.setTitle(i18n.get(SQLConsole.class, "SQL_Console", "SQL Console"));
    frame.setName(this.getClass().getName());
    frame.getContentPane().add(createToolBar(), "North");
    frame.setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus.png")));

    frame.addWindowListener(this);

    JMenuItem menuOpenScript =
        new JMenuItem(i18n.get(SQLConsole.class, "Open_script", "Open script"));
    menuOpenScript.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menuOpenScript.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuOpenScript_actionPerformed(e);
          }
        });

    JMenuItem menuSaveScript =
        new JMenuItem(i18n.get(SQLConsole.class, "Save_script", "Save script"));
    menuSaveScript.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menuSaveScript.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuSaveScript_actionPerformed(e);
          }
        });

    JMenuItem menuSaveScriptAs =
        new JMenuItem(i18n.get(SQLConsole.class, "Save_script_as", "Save script as"));
    menuSaveScriptAs.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuSaveScriptAs_actionPerformed(e);
          }
        });

    JMenuItem menuSaveResult =
        new JMenuItem(i18n.get(SQLConsole.class, "Save_result", "Save result"));
    menuSaveResult.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuSaveResult_actionPerformed(e);
          }
        });

    JMenuItem menuPurge =
        new JMenuItem(i18n.get(SQLConsole.class, "Purge_project", "Purge project"));
    menuPurge.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuPurge_actionPerformed(e);
          }
        });

    JMenuItem menuExit = new JMenuItem(i18n.get(SQLConsole.class, "Exit", "Exit"));
    menuExit.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menuExit.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuExit_actionPerformed(e);
          }
        });

    JMenu menuFile = new JMenu(i18n.get(SQLConsole.class, "File", "File"));
    menuFile.add(menuOpenScript);
    menuFile.add(menuSaveScript);
    menuFile.add(menuSaveScriptAs);
    menuFile.add(menuSaveResult);
    menuFile.add(menuPurge);
    menuFile.add(menuExit);

    JMenuBar bar = new JMenuBar();
    bar.add(menuFile);

    JMenuItem menuRefreshTree =
        new JMenuItem(i18n.get(SQLConsole.class, "Refresh_tree", "Refresh tree"));
    menuRefreshTree.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuRefreshTree_actionPerformed(e);
          }
        });

    menuResultInGrid =
        new JMenuItem(i18n.get(SQLConsole.class, "Results_in_grid", "Results in grid"));

    menuResultInGrid.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuResultInGrid_actionPerformed(e);
          }
        });

    menuResultInText =
        new JMenuItem(i18n.get(SQLConsole.class, "Results_in_text", "Results in text"));
    menuResultInText.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuResultInText_actionPerformed(e);
          }
        });

    JMenu menuView = new JMenu(i18n.get(SQLConsole.class, "View", "View"));
    menuView.add(menuRefreshTree);
    menuView.add(menuResultInGrid);
    menuView.add(menuResultInText);
    bar.add(menuView);

    menuRecent = new JMenu(i18n.get(SQLConsole.class, "Recent", "Recent"));
    bar.add(menuRecent);

    frame.setJMenuBar(bar);
    initGUI();

    history = new SQLConsoleHistory(nodusProject, menuRecent, this);

    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();

    Dimension size = new Dimension((int) (d.width * 0.66), (int) (d.height * 0.66));
    frame.setSize(size);

    // Full size on screen with less than 640 width
    if (d.width >= 640) {
      frame.setLocation((d.width - size.width) / 2, (d.height - size.height) / 2);
    } else {
      frame.setLocation(0, 0);
      frame.setSize(d);
    }

    history.loadHistory();
    GUIUtils.installToolTips(this, frame);

    if (withGUI) {
      frame.setVisible(true);
    }
    sqlCommandsArea.requestFocus();
  }

  /**
   * Handle F5 and Ctrl + Enter.
   *
   * @hidden
   */
  @Override
  public void keyPressed(KeyEvent evt) {
    // F5 key for Refresh tree
    if (evt.getKeyCode() == KeyEvent.VK_F5) {
      evt.consume();
      refreshTree();
    }

    // Ctrl + Enter to execute SQL statement
    if (evt.getKeyCode() == KeyEvent.VK_ENTER && (evt.isControlDown() || evt.isMetaDown())) {
      evt.consume();
      executeSQLStatements();
    }
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void keyReleased(KeyEvent k) {
    // Must be overridden
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void keyTyped(KeyEvent evt) {
    // Must be overridden
  }

  /** Replaces the visible tree contents with a freshly rebuilt detached root. */
  private void applyTreeRoot(DefaultMutableTreeNode rebuiltRoot) {
    rootNode.removeAllChildren();
    rootNode.setUserObject(rebuiltRoot.getUserObject());

    while (rebuiltRoot.getChildCount() > 0) {
      rootNode.add((MutableTreeNode) rebuiltRoot.getFirstChild());
    }

    treeModel.nodeStructureChanged(rootNode);
  }

  /**
   * Exit the console.
   *
   * @param e ActionEvent
   */
  private void menuExit_actionPerformed(ActionEvent e) {
    windowClosing(null);
  }

  /**
   * Open SQL command batch file and load it into command text area.
   *
   * @param e ActionEvent
   */
  private void menuOpenScript_actionPerformed(ActionEvent e) {
    JFileChooser f = new JFileChooser(".");
    f.setFileFilter(
        new NodusFileFilter(
            NodusC.TYPE_SQL, i18n.get(SQLConsole.class, "SQL_command_files", "SQL command files")));

    f.setDialogTitle(i18n.get(SQLConsole.class, "Open_Script_", "Open Script..."));

    if (defDirectory != null) {
      f.setCurrentDirectory(new File(defDirectory));
    }

    int option = f.showOpenDialog(frame);
    if (option == JFileChooser.APPROVE_OPTION) {
      File file = f.getSelectedFile();
      if (file != null) {
        loadScript(file);
      }
    }
  }

  /** Loads a script and remembers its origin even if it contains only one command. */
  void loadScript(File file) {
    scriptFileName = file.getAbsolutePath();
    if (withGUI) {
      String name = nodusMapPanel.getDisplayFullPath() ? scriptFileName : file.getName();
      frame.setTitle(i18n.get(SQLConsole.class, "SQL_Console", "SQL Console") + " [" + name + "]");
    }
    sqlCommandsArea.setText(SQLConsoleScript.readFile(scriptFileName));
    sqlCommandsArea.setCaretPosition(0);
  }

  /**
   * Drops all project related tables from database.
   *
   * @param e ActionEvent
   */
  private void menuPurge_actionPerformed(ActionEvent e) {
    int answer =
        JOptionPane.showConfirmDialog(
            frame,
            i18n.get(
                SQLConsole.class,
                "Do_you_really_want_to_drop_all_the_project_related_tables",
                "Do you really want to drop all the project related tables?"),
            NodusC.APPNAME,
            JOptionPane.YES_NO_OPTION);

    if (answer == JOptionPane.YES_OPTION) {

      // drop node tables
      NodusEsriLayer[] layer = nodusProject.getNodeLayers();

      for (NodusEsriLayer element : layer) {
        JDBCUtils.dropTable(element.getTableName());
      }

      // drop node link tables
      layer = nodusProject.getLinkLayers();

      for (NodusEsriLayer element : layer) {
        JDBCUtils.dropTable(element.getTableName());
      }

      // Drop O-D table
      String defValue =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_OD;
      JDBCUtils.dropTable(nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue));

      // Drop exclusions
      defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
      JDBCUtils.dropTable(nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue));

      // Drop assignment results
      for (int scenario = 0; scenario < NodusC.MAXSCENARIOS; scenario++) {
        nodusProject.removeScenario(scenario);
      }

      // Now close project
      windowClosing(null);
      nodusProject.close();
    }
  }

  /**
   * Refresh the tables/fields tree.
   *
   * @param e ActionEvent
   */
  private void menuRefreshTree_actionPerformed(ActionEvent e) {
    refreshTree();
  }

  /**
   * Tells the console to display the results in a grid.
   *
   * @param e ActionEvent
   */
  private void menuResultInGrid_actionPerformed(ActionEvent e) {
    typeOfResultFormat = 0;
    resultPanel.removeAll();
    resultPanel.add(resultScrollPane, BorderLayout.CENTER);
    resultPanel.doLayout();
    gridResultArea.fireTableChanged(null);
    resultPanel.repaint();
  }

  /**
   * Menu command to select the text formatting of results.
   *
   * @param e ActionEvent
   */
  private void menuResultInText_actionPerformed(ActionEvent e) {

    typeOfResultFormat = 1;
    if (!withGUI) {
      return;
    }

    resultPanel.removeAll();
    resultPanel.add(txtResultScroll, BorderLayout.CENTER);
    resultPanel.doLayout();
    resultPanel.repaint();
  }

  /**
   * Save the result of a query in a text file.
   *
   * @param e ActionEvent
   */
  private void menuSaveResult_actionPerformed(ActionEvent e) {
    JFileChooser f = new JFileChooser(".");

    f.setFileFilter(new NodusFileFilter(NodusC.TYPE_TXT, "SQL results"));

    f.setDialogTitle(i18n.get(SQLConsole.class, "Save_result_", "Save result..."));

    if (defDirectory != null) {
      f.setCurrentDirectory(new File(defDirectory));
    }

    int option = f.showSaveDialog(frame);

    if (option == JFileChooser.APPROVE_OPTION) {
      File file = f.getSelectedFile();

      if (file != null) {
        String fileName = file.getAbsolutePath();
        if (file.getName().lastIndexOf(".") == -1) {
          fileName += NodusC.TYPE_TXT;
        }
        if (!operations.confirmFileOverwrite(new File(fileName))) {
          return;
        }

        boolean isGrid = true;
        if (typeOfResultFormat == 1) {
          isGrid = false;
        }

        if (isGrid) {
          menuResultInText_actionPerformed(null);
        }

        SQLConsoleScript.writeFile(fileName, txtResultArea.getText());

        if (isGrid) {
          menuResultInGrid_actionPerformed(null);
        }
      }
    }
  }

  /**
   * Save the content of the command text area in a SQL text file.
   *
   * @param e ActionEvent
   */
  private void menuSaveScript_actionPerformed(ActionEvent e) {

    if (scriptFileName == null) {
      saveScriptAs();

    } else {
      SQLConsoleScript.writeFile(scriptFileName, sqlCommandsArea.getText());
    }
  }

  /** Save As the content of the command text area in a SQL text file. */
  private void menuSaveScriptAs_actionPerformed(ActionEvent e) {
    saveScriptAs();
  }

  /** Clear all existing nodes from the tree model and rebuild from scratch. */
  private void refreshTree() {

    final Cursor oldC = treeScrollPane.getCursor();
    rootNode.removeAllChildren();
    treeModel.nodeStructureChanged(rootNode);
    treeScrollPane.setCursor(new Cursor(Cursor.WAIT_CURSOR));

    SwingWorker<DefaultMutableTreeNode, Void> worker =
        new SwingWorker<DefaultMutableTreeNode, Void>() {
          @Override
          protected DefaultMutableTreeNode doInBackground() {
            return SQLConsoleMetadata.build(jdbcConnection, metaData);
          }

          @Override
          protected void done() {
            try {
              applyTreeRoot(get());
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              treeScrollPane.setCursor(oldC);
            }
          }
        };

    worker.execute();
  }

  /** Resets the command area, tell it it doesn't contain a script anymore. */
  public void resetScript() {
    scriptFileName = null;
    if (withGUI) {
      frame.setTitle(i18n.get(SQLConsole.class, "SQL_Console", "SQL Console"));
    }
  }

  /**
   * Runs an SQL batch. Existing export files are overwritten without prompting.
   *
   * @param sqlCommands A vector containing a batch of SQL commands.
   * @return True on success
   */
  public boolean runBatch(String[] sqlCommands) {
    try {
      sqlCommandsArea = new RSyntaxTextArea();
      StringBuffer b = new StringBuffer();
      for (int i = 0; i < sqlCommands.length; i++) {

        // Be sure a semicolon is present
        String s = sqlCommands[i].trim();
        if (!s.endsWith(";")) {
          s += ";";
        }

        b.append(s);
        b.append(NL);
      }
      sqlCommandsArea.setText(b.toString());
      return execute(true);
    } finally {
      closeStatement();
    }
  }

  /** Save a SQL batch file. */
  private void saveScriptAs() {

    JFileChooser f = new JFileChooser(".");
    f.setFileFilter(
        new NodusFileFilter(
            NodusC.TYPE_SQL, i18n.get(SQLConsole.class, "SQL_command_files", "SQL command files")));
    f.setDialogTitle(i18n.get(SQLConsole.class, "Save_script", "Save script"));

    if (defDirectory != null) {
      f.setCurrentDirectory(new File(defDirectory));
    }

    int option = f.showSaveDialog(frame);

    String oldSrcriptFileName = scriptFileName;
    if (option == JFileChooser.APPROVE_OPTION) {
      File file = f.getSelectedFile();

      if (file != null) {
        int answer = JOptionPane.YES_OPTION;
        scriptFileName = file.getAbsolutePath();

        // Add extension if needed
        String extension = "." + ((NodusFileFilter) f.getFileFilter()).getExtension();
        if (!scriptFileName.endsWith(extension)) {
          scriptFileName += extension;
          file = new File(scriptFileName);
        }

        if (file.exists()) {
          answer =
              JOptionPane.showConfirmDialog(
                  frame,
                  i18n.get(SQLConsole.class, "Overwrite", "Overwrite?"),
                  i18n.get(SQLConsole.class, "File_exists", "File exists"),
                  JOptionPane.YES_NO_OPTION);
        }

        if (answer == JOptionPane.YES_OPTION) {
          SQLConsoleScript.writeFile(scriptFileName, sqlCommandsArea.getText());

          // Update title with or without full path
          String name = file.getName();
          if (nodusMapPanel.getDisplayFullPath()) {
            name = scriptFileName;
          }
          frame.setTitle(
              i18n.get(SQLConsole.class, "SQL_Console", "SQL Console") + " [" + name + "]");

        } else {
          scriptFileName = oldSrcriptFileName;
        }
      }
    }
  }

  /** Sets or unsets the wait cursor. */
  private void setBusy(boolean busy) {
    if (!withGUI) {
      return;
    }

    if (!SwingUtilities.isEventDispatchThread()) {
      runOnEdtAndWait(() -> setBusy(busy));
      return;
    }

    if (busy) {
      frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      sqlCommandsArea.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      nodusMapPanel.setBusy(true);
    } else {
      frame.setCursor(oldCursor);
      sqlCommandsArea.setCursor(oldCursor);
      nodusMapPanel.setBusy(false);
    }
  }

  /** Updates the result text area. */
  private void updateTextResult() {
    if (withGUI) {
      runOnEdtAndWait(
          () -> {
            if (typeOfResultFormat == 1) {
              String result = SQLConsoleResults.asText(gridResultArea, displayHeaders);
              if (result.length() > 0) {
                addToTxtResultArea(result + NL, false);
              }
            }
          });
      return;
    }

    if (typeOfResultFormat == 1) {
      String result = SQLConsoleResults.asText(gridResultArea, displayHeaders);
      if (result.length() > 0) {
        System.out.println(result);
      }
    }
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void windowActivated(WindowEvent e) {
    // Must be overridden
  }

  /**
   * Close all the open children frames (Statistics dialog box could be currently visible).
   *
   * @param e WindowEvent
   * @hidden
   */
  @Override
  public void windowClosed(WindowEvent e) {
    // Close window and child
    if (statDlg != null) {
      statDlg.setVisible(false);
    }
  }

  /**
   * Closing window event.
   *
   * @param ev WindowEvent
   * @hidden
   */
  @Override
  public void windowClosing(WindowEvent ev) {
    closeStatement();

    if (withGUI) {
      history.saveHistory();
    }
    if (frame != null) {
      frame.dispose();
    }
  }

  /**
   * Add the given text to the text result area.SQL statements will be displayed in blue and bold,
   * the results in regular black.
   *
   * @param textToAdd The text to add to the result area.
   * @param isCommand If true, the text will be displayed in bold blue.
   */
  private void addToTxtResultArea(String textToAdd, boolean isCommand) {
    if (withGUI && !SwingUtilities.isEventDispatchThread()) {
      runOnEdtAndWait(() -> addToTxtResultArea(textToAdd, isCommand));
      return;
    }

    if (isCommand) {
      StyleConstants.setForeground(style, Color.blue);
      StyleConstants.setBold(style, true);
    } else {
      StyleConstants.setForeground(style, Color.black);
      StyleConstants.setBold(style, false);
    }

    try {
      doc.insertString(doc.getLength(), textToAdd, style);
      txtResultArea.setCaretPosition(txtResultArea.getDocument().getLength());
    } catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  /** Returns the SQL command text from the editor, safely from any thread. */
  private String getSqlCommandsText() {
    if (!withGUI || SwingUtilities.isEventDispatchThread()) {
      return sqlCommandsArea.getText();
    }

    FutureTask<String> task = new FutureTask<>(sqlCommandsArea::getText);
    runOnEdtAndWait(task);
    try {
      return task.get();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read SQL command text", e);
    }
  }

  /** Clears both result panes from the EDT. */
  private void clearGuiResults() {
    runOnEdtAndWait(
        () -> {
          txtResultArea.setText(null);
          gridResultArea.clear();
        });
  }

  /** Clears only the grid result area from the EDT. */
  private void clearGridResultArea() {
    if (!withGUI) {
      return;
    }

    runOnEdtAndWait(gridResultArea::clear);
  }

  /** Clears only the text result area from the EDT. */
  private void clearTextResultArea() {
    if (!withGUI) {
      return;
    }

    runOnEdtAndWait(() -> txtResultArea.setText(""));
  }

  /** Shows a one-cell result in the grid, safely from any thread. */
  private void showSingleValueResult(String head, String value) {
    runOnEdtAndWait(
        () -> {
          String[] header = {head};
          String[] row = {value};
          gridResultArea.setHead(header);
          gridResultArea.addRow(row);
        });
  }

  /** Applies a fully materialized result set to the grid on the EDT. */
  private void applyFormattedResultSet(
      String[] header, List<String[]> rows, boolean maxRowsReached) {
    runOnEdtAndWait(
        () -> {
          gridResultArea.setHead(header);
          if (withGUI) {
            sorter.setTableHeader(resultTable.getTableHeader());
            jbuttonExecute.setForeground(maxRowsReached ? Color.RED : colorButton);
          }
          for (String[] row : rows) {
            gridResultArea.addRow(row);
          }
        });
  }

  /** Displays a message dialog safely from any thread. */
  private void showMessageDialogOnEdt(String message, String title, int messageType) {
    runOnEdtAndWait(() -> JOptionPane.showMessageDialog(null, message, title, messageType));
  }

  /** Runs {@code task} on the EDT and waits for completion when called off the EDT. */
  private void runOnEdtAndWait(Runnable task) {
    if (!withGUI || SwingUtilities.isEventDispatchThread()) {
      task.run();
      return;
    }

    try {
      SwingUtilities.invokeAndWait(task);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to execute task on EDT", e);
    }
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void windowDeactivated(WindowEvent e) {
    // Must be overridden
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void windowDeiconified(WindowEvent e) {
    // Must be overridden
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void windowIconified(WindowEvent e) {
    // Must be overridden
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void windowOpened(WindowEvent e) {
    // Must be overridden
  }
}
