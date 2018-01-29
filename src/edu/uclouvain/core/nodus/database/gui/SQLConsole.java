/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.database.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.csv.ExportCSV;
import edu.uclouvain.core.nodus.database.csv.ImportCSV;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import edu.uclouvain.core.nodus.database.xls.ImportXLS;
import edu.uclouvain.core.nodus.swing.GridSwing;
import edu.uclouvain.core.nodus.swing.TableSorter;
import edu.uclouvain.core.nodus.utils.NodusFileFilter;
import edu.uclouvain.core.nodus.utils.SoundPlayer;

import foxtrot.ConcurrentWorker;
import foxtrot.Job;
import foxtrot.Worker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

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
 * - DISABLEHEADERS : If used, the headers of SQL outputs will not be displayed.<br>
 * - DISPLAYGRID : Forces the next SQL output to be displayed in grid format.<br>
 * - DISPLAYTEXT : Forces the next SQL output to be displayed in text format.<br>
 * - ENABLEHEADERS : If used, the headers of SQL outputs will be displayed.<br>
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
 * @author Bart Jourquin
 */
public class SQLConsole implements ActionListener, WindowListener, KeyListener {
  private static final String CLEARSCENARIO = "CLEARSCENARIO";

  private static final String CLEARSCREEN = "CLRSCR";

  private static String defDirectory;

  private static final String DISABLEHEADERS = "DISABLEHEADERS";

  private static final String DISPLAYGRID = "DISPLAYGRID";

  private static boolean displayHeaders = true;

  private static final String DISPLAYTEXT = "DISPLAYTEXT";

  private static final String ENABLEHEADERS = "ENABLEHEADERS";

  private static final String EXPORTCSV = "EXPORTCSV";

  private static final String EXPORTCSVH = "EXPORTCSVH";

  private static final String EXPORTDBF = "EXPORTDBF";

  private static final String EXPORTXLS = "EXPORTXLS";

  private static final String EXPORTXLSX = "EXPORTXLSX";

  private static final String EXTRACTSHP = "EXTRACTSHP";

  private static I18n i18n = Environment.getI18n();

  private static int maxHistory = 24;

  private static final String IMPORTCSV = "IMPORTCSV";

  private static final String IMPORTCSVH = "IMPORTCSVH";

  private static final String IMPORTDBF = "IMPORTDBF";

  private static final String IMPORTXLS = "IMPORTXLS";

  private static final String IMPORTXLSX = "IMPORTXLSX";

  private static final String STOP = "STOP";

  private static int maxRows = 1000;

  private static final String NL = System.getProperty("line.separator");

  /**
   * Reads a SQL batch file.
   *
   * @param file String
   * @return String
   */
  private static String readFile(String file) {
    try {
      FileReader reader = new FileReader(file);
      BufferedReader read = new BufferedReader(reader);
      StringBuffer b = new StringBuffer();
      String s = null;

      while ((s = read.readLine()) != null) {
        b.append(s);
        b.append(NL);
      }

      read.close();
      reader.close();

      return b.toString();
    } catch (IOException e) {
      return e.getMessage();
    }
  }

  /**
   * Writes an SQL batch file or an output text file.
   *
   * @param file String
   * @param text String
   */
  private static void writeFile(String file, String text) {
    try {
      FileWriter write = new FileWriter(file);

      write.write(text.toCharArray());
      write.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private DatabaseMetaData metaData;

  private JSplitPane ewSplitPane;

  private JFrame frame;

  private GridSwing gridResultArea;

  private JScrollPane resultScrollPane;

  private int history;

  int typeOfResultFormat; // 0: grid; 1: text

  private Color colorButton;

  private JButton jbuttonExecute;

  private Connection jdbcConnection;

  private JMenu menuRecent;

  private JMenuItem menuResultInGrid;

  private JMenuItem menuResultInText;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private JSplitPane nsSplitPane;

  private Cursor oldCursor;

  private JPanel resultPanel = new JPanel();

  private DefaultMutableTreeNode rootNode;

  private String scriptFileName = null;

  private TableSorter sorter;

  private JTable resultTable;

  private RSyntaxTextArea sqlCommandsArea;

  private String[] recentQueries;

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

  private HashMap<String, String> variables = new HashMap<>();

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

    // Only create a console if none exists
    Frame[] frames = Frame.getFrames();

    for (Frame element : frames) {
      if (element instanceof JFrame) {
        JFrame f = (JFrame) element;
        if (f.getName().equals(this.getClass().getName()) && f.isVisible()) {
          return;
        }
      }
    }

    this.nodusProject = nodusProject;
    nodusMapPanel = nodusProject.getNodusMapPanel();

    defDirectory = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    // Get the max rows for SQL query results
    maxRows =
        Integer.parseInt(
            nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_MAX_SQL_ROWS, "1000"));

    initialize();

    jdbcConnection = nodusProject.getMainJDBCConnection();
    try {
      metaData = jdbcConnection.getMetaData();
      statement = jdbcConnection.createStatement();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (withGUI) {
      refreshTree();
    }
  }

  /**
   * Used to recall a command saved in the history.
   *
   * @param ev ActionEvent
   * @exclude
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
      sqlCommandsArea.setText(recentQueries[i]);
      resetScript();
    }
  }

  /**
   * Adds a new SQL statement in the history.
   *
   * @param weights String
   */
  private void addToRecent(String s) {
    for (int i = 0; i < maxHistory; i++) {
      if (s.equals(recentQueries[i])) {
        return;
      }
    }

    if (recentQueries[history] != null) {
      menuRecent.remove(history);
    }

    recentQueries[history] = s;

    if (s.length() > 43) {
      s = s.substring(0, 40) + "...";
    }

    JMenuItem item = new JMenuItem(s);

    item.setActionCommand("#" + history);
    item.addActionListener(this); // Clean table
    menuRecent.insert(item, history);

    history = (history + 1) % maxHistory;
  }

  /** Clears the command text area. */
  private void clearCommands() {
    sqlCommandsArea.setText("");
    resetScript();
  }

  /**
   * Deletes all the tables related to a scenario.
   *
   * @param sqlCommand SQL command
   */
  private void clearScenario(String sqlCommand) {
    String scenarioNumber = sqlCommand.toUpperCase();
    int index = scenarioNumber.indexOf(CLEARSCENARIO);

    if (index != -1) {
      scenarioNumber =
          sqlCommand.substring(index + CLEARSCENARIO.length(), sqlCommand.length()).trim();
    }

    int scenario;

    try {
      scenario = Integer.valueOf(scenarioNumber);
    } catch (NumberFormatException e) {
      displayMessageInResult(i18n.get(SQLConsole.class, "Usage", "Usage:"), CLEARSCENARIO + " n");
      return;
    }

    nodusProject.removeScenario(scenario);

    MessageFormat.format(
        i18n.get(SQLConsole.class, "_succeeded", "{0} of \"{1}\" succeeded."),
        CLEARSCENARIO,
        scenarioNumber);
    displayMessageInResult(CLEARSCENARIO, scenarioNumber);
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
            statDlg = new StatDlg(_this, nodusProject);
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
  private void displayMessageInResult(String head, String msg) {
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
  }

  /**
   * Main routine that executes a single SQL statement or batch file. It is also the place where the
   * Nodus specific commands are intercepted and handled.
   *
   * @return True on success
   */
  private boolean execute() {
    setBusy(true);

    // Decompose all the statements
    Vector<String> sqlCommands = getValidSQLCommands(sqlCommandsArea.getText());

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
      txtResultArea.setText(null);
      gridResultArea.clear();
    }

    boolean error = false;
    for (String sqlCommand : sqlCommands) {

      if (error) {
        break;
      }

      String[] g = new String[1];

      String sync = sqlCommand.toUpperCase();

      // Display the command if text display
      if (withGUI) {
        addToTxtResultArea(sqlCommand + NL, true);
      } else {
        System.out.println(sqlCommand);
      }

      // Intercept Nodus specific commands commands

      if (sync.indexOf(STOP) != -1) {
        break;
      }

      // Create, Drop, Alter and Rename table commands need to refresh table list
      if (sync.indexOf("TABLE") != -1) {
        if (sync.indexOf("CREATE") != -1
            || sync.indexOf("ALTER") != -1
            || sync.indexOf("DROP") != -1
            || sync.indexOf("RENAME") != -1) {
          treeMustBeRefreshed = true;
        }
      }

      // "shutdown compact" is not allowed from the console
      if (sync.indexOf("SHUTDOWN") != -1 && sync.indexOf("COMPACT") != -1) {
        JOptionPane.showMessageDialog(
            null,
            i18n.get(
                SQLConsole.class,
                "Shutdown_not_allowed",
                "'SHUTDOWN COMPACT' not allowed from within the SQL console"),
            NodusC.APPNAME,
            JOptionPane.WARNING_MESSAGE);
        continue;
      }

      if (sync.indexOf(ENABLEHEADERS) != -1) {
        displayHeaders = true;
        continue;
      }

      if (sync.indexOf(DISABLEHEADERS) != -1) {
        displayHeaders = false;
        continue;
      }

      if (sync.indexOf(CLEARSCREEN) != -1 && withGUI) {
        txtResultArea.setText("");
        continue;
      }

      if (sync.indexOf(DISPLAYGRID) != -1 && withGUI) {
        menuResultInGrid.doClick();

        continue;
      }

      if (sync.indexOf(DISPLAYTEXT) != -1 && withGUI) {
        menuResultInText.doClick();
        continue;
      }

      if (sync.indexOf(CLEARSCENARIO) != -1) {
        clearScenario(sqlCommand);
        treeMustBeRefreshed = true;
        continue;
      }

      if (sync.indexOf(EXPORTDBF) != -1) {
        boolean b = importExport(sqlCommand, EXPORTDBF);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(IMPORTDBF) != -1) {
        boolean b = importExport(sqlCommand, IMPORTDBF);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(IMPORTCSVH) != -1) {
        boolean b = importExport(sqlCommand, IMPORTCSVH);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(IMPORTCSV) != -1) {
        boolean b = importExport(sqlCommand, IMPORTCSV);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(EXPORTCSVH) != -1) {
        boolean b = importExport(sqlCommand, EXPORTCSVH);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(EXPORTCSV) != -1) {
        boolean b = importExport(sqlCommand, EXPORTCSV);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(IMPORTXLSX) != -1) {
        boolean b = importExport(sqlCommand, IMPORTXLSX);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(EXPORTXLSX) != -1) {
        boolean b = importExport(sqlCommand, EXPORTXLSX);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(IMPORTXLS) != -1) {
        boolean b = importExport(sqlCommand, IMPORTXLS);

        if (b) {
          treeMustBeRefreshed = true;
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(EXPORTXLS) != -1) {
        boolean b = importExport(sqlCommand, EXPORTXLS);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf(EXTRACTSHP) != -1) {
        boolean b = extractShp(sqlCommand);

        if (b) {
          continue;
        } else {
          setBusy(false);
          return false;
        }
      }

      // Intercept delete/insert commands for nodus layers
      if (sync.indexOf("DELETE") != -1) {
        boolean b = isDeleteAllowed(sqlCommand);

        if (!b) {
          setBusy(false);
          return false;
        }
      }

      if (sync.indexOf("INSERT") != -1) {
        boolean b = isInsertAllowed(sqlCommand);

        if (!b) {
          setBusy(false);
          return false;
        }
      }

      gridResultArea.clear();

      try {
        statement.execute(sqlCommand);

        int r = statement.getUpdateCount();

        if (r == -1) {
          // Is a ResultSet
          formatResultSet(statement, maxRows);
        } else {
          // Is an update
          g[0] = "update count";
          if (withGUI) {
            gridResultArea.setHead(g);
          } else {
            System.out.print(g[0] + ": ");
          }

          g[0] = "" + r;
          if (withGUI) {
            gridResultArea.addRow(g);
          } else {
            System.out.println(g[0]);
          }
        }

        if (sqlCommands.size() <= 1) {
          addToRecent(sqlCommandsArea.getText());
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
          g[0] = "SQL Error";
          gridResultArea.setHead(g);
          g[0] = s;

          gridResultArea.addRow(g);
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
   * Creates at Foxtrot thread in which the SQL statements are executed. Refresh the tree if needed
   * and play a sound if the query execution time was a longer than 5 seconds.
   */
  private void executeSQLStatements() {

    final long start = System.currentTimeMillis();
    final int pos = sqlCommandsArea.getCaretPosition();

    try {
      Worker.post(
          new Job() {
            @Override
            public Object run() {
              execute();
              return null;
            }
          });
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Put this out of the Foxtrot job to avoid exception
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            gridResultArea.fireTableChanged(null);
          }
        });

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

  /**
   * Routine that extracts a shapefile according to a where statement. The syntax must be as such:
   * "extractshp from shapefile1 to shapefile2 where 'some sql condition'".
   *
   * @param stmt The command string to process
   * @return true on success.
   */
  private boolean extractShp(String stmt) {
    // Get source shapefile name
    int index;
    String token = " from ";

    if ((index = stmt.indexOf(token)) == -1) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String s = stmt.substring(index + token.length()).trim();

    if ((index = s.indexOf(" ")) == -1) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String fromShapefile = s.substring(0, index);

    // Get the destination file name
    token = " to ";

    if ((index = stmt.indexOf(token)) == -1) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    s = stmt.substring(index + token.length()).trim();

    if ((index = s.indexOf(" ")) == -1) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String toShapefile = s.substring(0, index);

    // get where statement
    token = " where ";

    if ((index = stmt.indexOf(token)) == -1) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String whereStmt = stmt.substring(index + token.length()).trim();

    // Test if it is a valid link layer
    NodusEsriLayer[] layers = nodusProject.getLinkLayers();

    for (NodusEsriLayer element : layers) {
      if (element.getTableName().equals(fromShapefile)) {
        boolean ok = element.extract(toShapefile, whereStmt);

        if (ok) {
          displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "successfuly_extracted", "{0} successfuly extracted."),
                  toShapefile));

          addToRecent(stmt);
        } else {
          displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "Error on extracting", "Error on extracting {0}"),
                  toShapefile));
        }

        return false;
      }
    }

    // The two file names must be different
    if (fromShapefile.equals(toShapefile)) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Error", "Error"),
          i18n.get(
              SQLConsole.class,
              "Cannot_extract_a_file_to_itself",
              "Cannot extract a file to itself."));

      return false;
    }

    // Test if it is a valid node layer
    layers = nodusProject.getNodeLayers();

    for (NodusEsriLayer element : layers) {
      if (element.getTableName().equals(fromShapefile)) {
        boolean ok = element.extract(toShapefile, whereStmt);

        if (ok) {
          displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "successfuly_extracted", "{0} successfuly extracted."),
                  toShapefile));
          addToRecent(stmt);

          return true;
        } else {
          displayMessageInResult(
              "ExtractShp",
              i18n.get(
                  SQLConsole.class, "Error on extracting", "Error on extracting {0}", toShapefile));

          return false;
        }
      }
    }

    // No valid layer was found
    displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        MessageFormat.format(
            i18n.get(SQLConsole.class, "is_not a_valid_shapefile", "{0} is not a valid shapefile."),
            fromShapefile));

    return false;
  }

  /**
   * Formats a ResultSet to display.
   *
   * @param stmt Statement
   * @param maxRows int
   */
  private void formatResultSet(Statement stmt, int maxRows) {
    try {

      ResultSet r = stmt.getResultSet();

      if (r == null) {

        String[] g = new String[1];

        g[0] = i18n.get(SQLConsole.class, "Result", "Result");
        gridResultArea.setHead(g);

        g[0] = i18n.get(SQLConsole.class, "empty", "(empty)");
        gridResultArea.addRow(g);

        return;
      }

      ResultSetMetaData m = r.getMetaData();

      int col = m.getColumnCount();
      String[] h = new String[col];

      for (int i = 1; i <= col; i++) {
        h[i - 1] = m.getColumnLabel(i);
      }

      gridResultArea.setHead(h);

      sorter.setTableHeader(resultTable.getTableHeader());

      int counter = 0;
      jbuttonExecute.setForeground(colorButton);
      while (r.next()) {

        // The result set may be larger that the max rows set. In such a case, change
        // the color of the text in the query button in order to warn the user.
        counter++;
        if (counter == maxRows) {
          jbuttonExecute.setForeground(Color.RED);
        }

        for (int i = 1; i <= col; i++) {
          h[i - 1] = r.getString(i);

          if (r.wasNull()) {
            h[i - 1] = "(null)";
          }
        }

        gridResultArea.addRow(h);
      }

      r.close();
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

  /*
   * TODO The SQL script parser should be rewritten in order to accept new values
   * for existing variables.
   */

  /**
   * Decompose the batch file into commands. A regular batch command must end with a ";" but can be
   * written on multiple lines. A comment starts with a "--" or "#". A command block is delimited as
   * in C or Java Variable definitions start with @@
   */
  private Vector<String> getValidSQLCommands(String sqlCommand) {

    boolean isBatchFile = false;

    // Reset user defined variables
    variables.clear();

    // Remove block commands
    boolean hasBlockComment = true;
    while (hasBlockComment) {
      hasBlockComment = false;
      int beginIdx = sqlCommand.indexOf("/*");
      int endIdx = sqlCommand.indexOf("*/");
      if (beginIdx != -1 && endIdx != -1 && beginIdx < endIdx) {
        String p1 = sqlCommand.substring(0, beginIdx);
        String p2 = sqlCommand.substring(endIdx + 2, sqlCommand.length());
        sqlCommand = p1 + " " + p2;
        hasBlockComment = true;
      }
    }

    // Split per line
    String[] line = sqlCommand.split(NL);

    Vector<String> validLines = new Vector<>();
    String currentCommand = "";
    for (int i = 0; i < line.length; i++) {
      line[i] = line[i].trim();
      if (line[i].length() > 0) {
        if (currentCommand.equals("")) {
          currentCommand = line[i];
        } else {
          // Concatenate multi-line commands
          currentCommand += " " + line[i];
        }

        // Handle variable definitions
        if (currentCommand.startsWith("@@")) {
          int idx = currentCommand.indexOf(":=");
          if (idx != -1) {
            String varName = currentCommand.substring(0, idx).trim();
            String varValue = currentCommand.substring(idx + 2, currentCommand.length()).trim();
            if (varValue.endsWith(";")) {
              varValue = varValue.substring(0, varValue.length() - 1).trim();
            }

            currentCommand = "";

            variables.put(varName, varValue);
          }
        } else {

          // Handle single line comments
          if (currentCommand.startsWith("#") || currentCommand.startsWith("--")) {
            currentCommand = "";
          } else {
            if (currentCommand.endsWith(";")) {
              // Remove trailing semi-column and store command
              validLines.add(currentCommand.substring(0, currentCommand.length() - 1));
              currentCommand = "";
              isBatchFile = true;
            }
          }
        }
      }
    }

    // Handle EOF
    if (!currentCommand.equals("")) {
      validLines.add(currentCommand);
    }

    // Replace all the user defined variables
    Iterator<String> it = validLines.iterator();
    int idx = 0;
    while (it.hasNext()) {
      currentCommand = it.next();
      Iterator<String> it2 = variables.keySet().iterator();
      while (it2.hasNext()) {
        String varName = it2.next();
        String varValue = variables.get(varName);
        currentCommand = currentCommand.replaceAll(varName, varValue);
      }
      validLines.set(idx, currentCommand);
      idx++;
    }

    if (isBatchFile) {
      menuResultInText_actionPerformed(null);
    }

    return validLines;
  }

  /**
   * Handles the Nodus specific import/export commands.
   *
   * @param sqlStmt String The command to process
   * @param operation String The operation to process (IMPORTDBF, EXPORTDBF, IMPORTCSV,
   *     EXPORTCSV,...)
   * @return true on success.
   */
  private boolean importExport(String sqlStmt, String operation) {
    String s = sqlStmt.toUpperCase();
    int index = s.indexOf(operation);

    if (index != -1) {
      s = sqlStmt.substring(index + operation.length(), sqlStmt.length());
    }

    String tableName = s.trim();

    if (tableName.length() == 0) {
      displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          MessageFormat.format(
              i18n.get(SQLConsole.class, "{0} TableName", "TableName"), operation));

      return false;
    }

    boolean succeeded = false;

    if (operation.equals(EXPORTDBF)) {
      succeeded = ExportDBF.exportTable(nodusProject, tableName);
    } else if (operation.equals(IMPORTDBF)) {
      succeeded = ImportDBF.importTable(nodusProject, tableName);
    } else if (operation.equals(EXPORTCSV)) {
      succeeded = ExportCSV.exportTable(nodusProject, tableName, false);
    } else if (operation.equals(EXPORTCSVH)) {
      succeeded = ExportCSV.exportTable(nodusProject, tableName, true);
    } else if (operation.equals(IMPORTCSV)) {
      succeeded = ImportCSV.importTable(nodusProject, tableName, false);
    } else if (operation.equals(IMPORTCSVH)) {
      succeeded = ImportCSV.importTable(nodusProject, tableName, true);
    } else if (operation.equals(IMPORTXLS)) {
      succeeded = ImportXLS.importTable(nodusProject, tableName, false);
    } else if (operation.equals(EXPORTXLS)) {
      succeeded = ExportXLS.exportTable(nodusProject, tableName, false);
    } else if (operation.equals(IMPORTXLSX)) {
      succeeded = ImportXLS.importTable(nodusProject, tableName, true);
    } else if (operation.equals(EXPORTXLSX)) {
      succeeded = ExportXLS.exportTable(nodusProject, tableName, true);
    }

    String g = "";
    if (succeeded) {
      g =
          MessageFormat.format(
              i18n.get(SQLConsole.class, "_succeeded", "{0} of \"{1}\" succeeded."),
              operation,
              tableName);

    } else {
      g =
          MessageFormat.format(
              i18n.get(SQLConsole.class, "_failed", "{0} of \"{1}\"{ failed."),
              operation,
              tableName);
    }

    displayMessageInResult(operation, g);

    if (succeeded) {
      addToRecent(sqlStmt);
    }

    return succeeded;
  }

  /** Initializes main GUI. */
  private void initGUI() {
    JPanel commandPanel = new JPanel();

    //resultPanel = new JPanel();
    nsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, commandPanel, resultPanel);

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
    try {
      InputStream in = NodusMapPanel.class.getResource("eclipse.xml").openStream();
      Theme theme = Theme.load(in);
      theme.apply(sqlCommandsArea);
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

    frame.getContentPane().add(ewSplitPane, BorderLayout.CENTER);
    frame.doLayout();
    frame.pack();
    oldCursor = frame.getCursor();
  }

  /** Creates the GUI. */
  void initialize() {

    frame = new JFrame(NodusC.APPNAME);
    frame.setTitle(i18n.get(SQLConsole.class, "SQL_Console", "SQL Console"));
    frame.setName(this.getClass().getName());
    frame.getContentPane().add(createToolBar(), "North");
    frame.setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus7.png")));

    frame.addWindowListener(this);

    JMenuItem menuOpenScript =
        new JMenuItem(i18n.get(SQLConsole.class, "Open_script", "Open script"));
    menuOpenScript.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
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
            KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
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
            KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
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

    recentQueries = new String[maxHistory];

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

    loadHistory();

    if (withGUI) {
      frame.setVisible(true);
    }
    sqlCommandsArea.requestFocus();
  }

  /**
   * Returns true if delete SQL operations are allowed. They are not allowed for the tables that
   * correspond to the DBF files of shapefiles.
   */
  private boolean isDeleteAllowed(String sqlStmt) {

    sqlStmt = sqlStmt.toLowerCase();

    // Get shapefile name
    int index;
    String token = " from ";

    if ((index = sqlStmt.indexOf(token)) == -1) {
      // probably an error in the SQL statement. To be handled by the jdbc driver
      return true;
    }

    String tableName = sqlStmt.substring(index + token.length()).trim();

    if (tableName.indexOf(' ') != -1) {
      tableName = tableName.substring(0, tableName.indexOf(' '));
    }

    // Is it a Nodus layer?
    if (nodusProject.getLayer(tableName) == null) {
      return true;
    }

    displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        i18n.get(
            SQLConsole.class,
            "Delete_not_allowed_on_Nodus_layers",
            "SQL DELETE operations not allowed on Nodus layers"));
    return false;
  }

  /**
   * Returns true if insert SQL operations are allowed. They are not allowed for the tables that
   * correspond to the DBF files of shapefiles.
   */
  private boolean isInsertAllowed(String sqlStmt) {

    // Get shapefile name
    int index;
    String token = " into ";

    if ((index = sqlStmt.indexOf(token)) == -1) {
      // probably an error in the SQL statement. To be handled by the jdbc driver
      return true;
    }

    String tableName = sqlStmt.substring(index + token.length()).trim();

    // Is it a Nodus layer?
    if (nodusProject.getLayer(tableName) == null) {
      return true;
    }

    displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        i18n.get(
            SQLConsole.class,
            "Insert_not_allowed_on_Nodus_layers",
            "SQL INSERT operations not allowed on Nodus layers"));
    return false;
  }

  /**
   * Handle F5 and Ctrl + Enter.
   *
   * @exclude
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
   * @exclude 
   **/
  @Override
  public void keyReleased(KeyEvent k) {}

  /**
   * .
   * @exclude 
   **/
  @Override
  public void keyTyped(KeyEvent evt) {}

  /** Loads history (recent SQL statements) from property file. */
  private void loadHistory() {
    for (int i = 0; i < maxHistory; i++) {
      String key = "sql.history" + i;
      String value = nodusProject.getLocalProperty(key, null);

      if (value != null) {
        addToRecent(value);
      }
    }
  }

  /**
   * Simple tree node factory method - set parent and user object.
   *
   * @param userObject Object
   * @param parent MutableTreeNode
   * @return DefaultMutableTreeNode
   */
  private DefaultMutableTreeNode makeNode(Object userObject, MutableTreeNode parent) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(userObject);

    if (parent != null) {
      treeModel.insertNodeInto(node, parent, parent.getChildCount());
    }

    return node;
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
            NodusC.TYPE_SQL, i18n.get(SQLConsole.class, "SQL command files", "SQL command files")));

    f.setDialogTitle(i18n.get(SQLConsole.class, "Open Script_", "Open Script..."));

    if (defDirectory != null) {
      f.setCurrentDirectory(new File(defDirectory));
    }

    int option = f.showOpenDialog(frame);
    if (option == JFileChooser.APPROVE_OPTION) {
      File file = f.getSelectedFile();
      if (file != null) {
        scriptFileName = file.getAbsolutePath();

        // Update title with or without full path
        String name = file.getName();
        if (nodusMapPanel.getDisplayFullPath()) {
          name = scriptFileName;
        }
        frame.setTitle(
            i18n.get(SQLConsole.class, "SQL_Console", "SQL Console") + " [" + name + "]");

        sqlCommandsArea.setText(readFile(scriptFileName));
        sqlCommandsArea.setCaretPosition(0);
      }
    }
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

      JDBCUtils jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());
      // drop node tables
      NodusEsriLayer[] layer = nodusProject.getNodeLayers();

      for (NodusEsriLayer element : layer) {
        jdbcUtils.dropTable(element.getTableName());
      }

      // drop node link tables
      layer = nodusProject.getLinkLayers();

      for (NodusEsriLayer element : layer) {
        jdbcUtils.dropTable(element.getTableName());
      }

      // Drop O-D table
      String defValue =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_OD;
      jdbcUtils.dropTable(nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue));

      // Drop exclusions
      defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
      jdbcUtils.dropTable(nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue));

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
        boolean isGrid = true;
        if (typeOfResultFormat == 1) {
          isGrid = false;
        }

        if (isGrid) {
          menuResultInText_actionPerformed(null);
        }

        String fileName = file.getAbsolutePath();

        if (fileName.lastIndexOf(".") == -1) {
          fileName += NodusC.TYPE_TXT;
        }

        writeFile(fileName, txtResultArea.getText());

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
      writeFile(scriptFileName, sqlCommandsArea.getText());
    }
  }

  /** Save As the content of the command text area in a SQL text file. */
  private void menuSaveScriptAs_actionPerformed(ActionEvent e) {
    saveScriptAs();
  }

  /** Clear all existing nodes from the tree model and rebuild from scratch. */
  private void refreshTree() {

    rootNode.removeAllChildren();
    treeModel.nodeStructureChanged(rootNode);

    ConcurrentWorker.post(
        new Job() {
          @Override
          public Object run() {

            DefaultMutableTreeNode propertiesNode;
            Cursor oldC = treeScrollPane.getCursor();
            treeScrollPane.setCursor(new Cursor(Cursor.WAIT_CURSOR));

            // Now rebuild the tree below its root
            try {
              // Start by naming the root node from its URL:
              rootNode.setUserObject(metaData.getURL());

              // In Oracle, limit to the schema of the user
              String schema = null;
              if (JDBCUtils.getDbEngine(jdbcConnection) == JDBCUtils.DB_ORACLE) {
                JDBCUtils jdbcUtils = new JDBCUtils(jdbcConnection);
                schema =
                    jdbcUtils.getCompliantIdentifier(
                        nodusProject.getLocalProperty(NodusC.PROP_JDBC_USERNAME, "null"));
              }

              // get metadata about user tables by building a vector of table names
              String[] usertables = {"TABLE", "GLOBAL TEMPORARY", "VIEW"};
              ResultSet result = metaData.getTables(null, schema, null, usertables);

              Vector<String> tables = new Vector<>();

              // sqlbob@users Added remarks.
              Vector<String> remarks = new Vector<>();

              while (result.next()) {
                if (result.getString(3).indexOf("$") == -1) {
                  tables.addElement(result.getString(3));
                  remarks.addElement(result.getString(5));
                }
              }

              result.close();

              // For each table, build a tree node with interesting info
              for (int i = 0; i < tables.size(); i++) {
                String name = tables.elementAt(i);
                DefaultMutableTreeNode tableNode = makeNode(name, rootNode);
                ResultSet col = metaData.getColumns(null, null, name, null);

                // sqlbob@users Added remarks.
                String remark = remarks.elementAt(i);

                if (remark != null && !remark.trim().equals("")) {
                  makeNode(remark, tableNode);
                }

                // With a child for each column containing pertinent attributes
                while (col.next()) {
                  String c = col.getString(4);
                  DefaultMutableTreeNode columnNode = makeNode(c, tableNode);
                  String type = col.getString(6);

                  makeNode(
                      MessageFormat.format(i18n.get(SQLConsole.class, "Type", "Type: {0}"), type),
                      columnNode);

                  boolean nullable = col.getInt(11) != DatabaseMetaData.columnNoNulls;

                  makeNode(
                      MessageFormat.format(
                          i18n.get(SQLConsole.class, "Nullable", "Nullable: {0}"), nullable),
                      columnNode);
                }

                col.close();

                DefaultMutableTreeNode indexesNode =
                    makeNode(i18n.get(SQLConsole.class, "Indices", "Indices"), tableNode);
                ResultSet ind = metaData.getIndexInfo(null, schema, name, false, false);

                String oldiname = null;

                // A child node to contain each index - and its attributes
                while (ind.next()) {
                  DefaultMutableTreeNode indexNode = null;
                  boolean nonunique = ind.getBoolean(4);
                  String iname = ind.getString(6);

                  if (oldiname == null || !oldiname.equals(iname)) {
                    indexNode = makeNode(iname, indexesNode);
                    makeNode(
                        MessageFormat.format(
                            i18n.get(SQLConsole.class, "Unique", "Unique: {0}"), !nonunique),
                        indexNode);
                    oldiname = iname;
                  }

                  // And the ordered column list for index components
                  makeNode(ind.getString(9), indexNode);
                }

                ind.close();
              }

              // Finally - a little additional metadata on this connection
              propertiesNode =
                  makeNode(i18n.get(SQLConsole.class, "Properties", "Properties"), rootNode);

              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "User", "User: {0}"), metaData.getUserName()),
                  propertiesNode);
              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "ReadOnly", "ReadOnly: {0}"),
                      jdbcConnection.isReadOnly()),
                  propertiesNode);
              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "AutoCommit", "AutoCommit: {0}"),
                      jdbcConnection.getAutoCommit()),
                  propertiesNode);
              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "Driver", "Driver: {0}"),
                      metaData.getDriverName()),
                  propertiesNode);
              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "Product", "Product: {0}"),
                      metaData.getDatabaseProductName()),
                  propertiesNode);
              makeNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "Version", "Version: {0}"),
                      metaData.getDatabaseProductVersion()),
                  propertiesNode);
            } catch (SQLException se) {
              propertiesNode =
                  makeNode(
                      i18n.get(SQLConsole.class, "Error_getting_metadata", "Error getting metadata")
                          + ":",
                      rootNode);
              makeNode(se.getMessage(), propertiesNode);
              makeNode(se.getSQLState(), propertiesNode);
            }

            // treeModel.nodeStructureChanged(rootNode);

            treeScrollPane.setCursor(oldC);
            return null;
          }
        });
    treeModel.nodeStructureChanged(rootNode);
  }

  /** Resets the command area, tell it it doesn't contain a script anymore. */
  public void resetScript() {
    scriptFileName = null;
    frame.setTitle(i18n.get(SQLConsole.class, "SQL_Console", "SQL Console"));
  }

  /**
   * Runs an SQL batch.
   *
   * @param sqlCommands A vector containing a batch of SQL commands.
   * @return True on success
   */
  public boolean runBatch(String[] sqlCommands) {

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
    return execute();
  }

  /** Saves history (recent SQL statements) in property file. */
  private void saveHistory() {
    if (nodusProject.isOpen()) {
      for (int i = 0; i < maxHistory; i++) {
        if (recentQueries[i] != null) {
          String key = "sql.history" + i;
          nodusProject.setLocalProperty(key, recentQueries[i]);
        }
      }
    }
  }

  /** Save a SQL batch file. */
  private void saveScriptAs() {

    JFileChooser f = new JFileChooser(".");
    f.setFileFilter(
        new NodusFileFilter(
            NodusC.TYPE_SQL, i18n.get(SQLConsole.class, "SQL command files", "SQL command files")));
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
          writeFile(scriptFileName, sqlCommandsArea.getText());

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
    if (withGUI) {
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
  }

  /** Displays the formatted text result. */
  private String getResultAsString() {

    StringBuffer b = new StringBuffer();

    if (displayHeaders) {
      String[] col = gridResultArea.getHead();
      int width = col.length;
      int[] size = new int[width];
      for (int i = 0; i < width; i++) {
        size[i] = col[i].length();
      }

      for (int i = 0; i < width; i++) {
        b.append(col[i]);

        for (int l = col[i].length(); l <= size[i]; l++) {
          b.append(' ');
        }
      }

      b.append(NL);

      for (int i = 0; i < width; i++) {
        for (int l = 0; l < size[i]; l++) {
          b.append('-');
        }

        b.append(' ');
      }

      b.append(NL);

      // Do not display empty headers
      if (b.charAt(0) == ' ') {
        b = new StringBuffer();
      }
    }

    Vector<?> data = gridResultArea.getData();
    if (data.isEmpty()) {
      return "";
    }
    String[] col = (String[]) data.elementAt(0);
    int width = col.length;
    String[] row;
    int height = data.size();
    int[] size = new int[width];

    for (int i = 0; i < width; i++) {
      size[i] = col[i].length();
    }

    for (int i = 0; i < height; i++) {
      row = (String[]) data.elementAt(i);
      width = row.length;

      for (int j = 0; j < width; j++) {
        int l = row[j].length();

        if (l > size[j]) {
          size[j] = l;
        }
      }
    }

    for (int i = 0; i < height; i++) {
      row = (String[]) data.elementAt(i);

      for (int j = 0; j < width; j++) {
        b.append(row[j]);

        for (int l = row[j].length(); l <= size[j]; l++) {
          b.append(' ');
        }
      }

      b.append(NL);
    }

    return b.toString();
  }

  /** Updates the result text area. */
  private void updateTextResult() {

    String result = "";
    if (typeOfResultFormat == 1) {
      result = getResultAsString();

      if (withGUI) {
        addToTxtResultArea(result + NL, false);
      } else {
        System.out.println(result);
      }
    }
  }

  /**
   * .@exclude 
   **/
  @Override
  public void windowActivated(WindowEvent e) {}

  /**
   * Close all the open children frames (Statistics dialog box could be currently visible).
   *
   * @param e WindowEvent
   * @exclude
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
   * @exclude
   */
  @Override
  public void windowClosing(WindowEvent ev) {
    try {
      statement.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    saveHistory();
    frame.dispose();
  }

  /**
   * Add the given text to the text result area.SQL statements will be displayed in blue and bold,
   * the results in regular black.
   *
   * @param textToAdd The text to add to the result area.
   * @param isCommand If true, the text will be displayed in bold blue.
   */
  private void addToTxtResultArea(String textToAdd, boolean isCommand) {

    if (isCommand) {
      StyleConstants.setForeground(style, Color.blue);
      StyleConstants.setBold(style, true);
    } else {
      StyleConstants.setForeground(style, Color.black);
      StyleConstants.setBold(style, false);
    }

    try {
      doc.insertString(doc.getLength(), textToAdd, style);
      txtResultArea.setCaretPosition(txtResultArea.getText().length());
    } catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void windowDeactivated(WindowEvent e) {}

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void windowDeiconified(WindowEvent e) {}

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void windowIconified(WindowEvent e) {}

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void windowOpened(WindowEvent e) {}
}
