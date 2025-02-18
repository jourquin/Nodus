/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.TableSorter;
import edu.uclouvain.core.nodus.utils.ColorUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableModel;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.style.PieStyler;
import org.knowm.xchart.style.PieStyler.LabelType;
import org.knowm.xchart.style.Styler.ChartTheme;

/**
 * Displays a series of Pie charts, depending on the selected statistics set in the StatDlg.
 *
 * @author Bart Jourquin
 */
public class StatPieDlg extends EscapeDialog {

  /** Serial UID. */
  private static final long serialVersionUID = 2573941413883549404L;

  /** I18N mechanism. */
  private static I18n i18n = Environment.getI18n();

  /** Loaded tons per mode statistic. */
  public static final byte LoadedTons_M = 0;

  /** Loaded tons per mode and means statistic. */
  public static final byte LoadedTons_Mm = 2;

  /** T.km per mode statistic. */
  public static final byte TKM_M = 1;

  /** T.km per mode and means statistic. */
  public static final byte TKM_Mm = 3;

  /** Unloaded tons per mode statistic. */
  public static final byte UnloadedTons_M = 4;

  /** Unloaded tons per mode and means statistic. */
  public static final byte UnloadedTons_Mm = 5;

  /** Vehicles.km per mode statistic. */
  public static final byte VKM_M = 6;

  /** Vehicles per mode and means statistic. */
  public static final byte VKM_Mm = 7;

  /** . */
  private int nbStats = 8;

  /** . */
  private DecimalFormat formatter = new DecimalFormat("#.##");

  /** Vector that will contain the labels (mode or mode-means). */
  @SuppressWarnings("unchecked")
  private Vector<String>[] labels = (Vector<String>[]) new Vector[nbStats];

  /** Nodus project. */
  private NodusProject nodusProject;

  /** . */
  private JPanel pane = null;

  /** . */
  private JScrollPane scrollPane = null;

  /** . */
  private TableSorter sorter = new TableSorter(new DefaultTableModel());

  /** Array of strings that will contain the SQL queries for the types of results to display. */
  private String[] sqlQuery = new String[nbStats];

  /** . */
  private JTabbedPane tabbedPanes = null;

  /** . */
  private JTable table = null;

  /** . */
  private DefaultTableModel tableModel;

  /** Vector that will contain the results to display. */
  @SuppressWarnings("unchecked")
  private Vector<Float>[] values = (Vector<Float>[]) new Vector[nbStats];

  /** Vector that will contain the colors to display. */
  @SuppressWarnings("unchecked")
  private Vector<Color>[] colors = (Vector<Color>[]) new Vector[nbStats];

  /** The color to associate to each mode. */
  private Color[] modeColors;

  /**
   * Creates the dialog that will contain the pie charts.
   *
   * @param statDlg The parent StatDlg.
   */
  public StatPieDlg(StatDlg statDlg) {
    super(
        statDlg,
        i18n.get(StatPieDlg.class, "Assignment_statistics", "Assignment statistics"),
        false);
    this.nodusProject = statDlg.getNodusProject();
    tableModel = (DefaultTableModel) sorter.getTableModel();
    getModeColors();
  }

  /** Closes the dialog. */
  private void close() {
    setVisible(false);
  }

  /**
   * Displays the GUI. This method is not called by the constructor as the queries must be performed
   * from the StatDlg.
   */
  public void displayGUI() {

    // Test if there is at least one stat to display
    boolean okToDisplay = false;
    for (String element : sqlQuery) {
      if (element != null) {
        okToDisplay = true;
        break;
      }
    }
    if (!okToDisplay) {
      return;
    }

    this.setContentPane(getPane());

    tabbedPanes = getTabbedPanes();

    GridBagConstraints tabbedPanesConstraints = new GridBagConstraints();
    tabbedPanesConstraints.fill = GridBagConstraints.BOTH;
    tabbedPanesConstraints.gridy = 0;
    tabbedPanesConstraints.weightx = 2;
    tabbedPanesConstraints.weighty = 2;
    tabbedPanesConstraints.insets = new Insets(5, 5, 5, 5);
    tabbedPanesConstraints.gridx = 0;

    GridBagConstraints scrollPaneConstraints = new GridBagConstraints();
    scrollPaneConstraints.fill = GridBagConstraints.BOTH;
    scrollPaneConstraints.gridy = 1;
    scrollPaneConstraints.weightx = 1.0;
    scrollPaneConstraints.weighty = 1.0;
    scrollPaneConstraints.insets = new Insets(0, 5, 5, 5);
    scrollPaneConstraints.gridx = 0;

    GridBagConstraints closeButtonConstraints = new GridBagConstraints();
    closeButtonConstraints.gridy = 2;
    closeButtonConstraints.insets = new Insets(0, 5, 5, 5);
    closeButtonConstraints.gridx = 0;
    closeButtonConstraints.weightx = 1.0;
    closeButtonConstraints.weighty = 1.0;

    if (sqlQuery[LoadedTons_M] != null) {
      JLabel label = new JLabel();
      label.setName("" + LoadedTons_M);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "Loaded_Tons_M", "Loaded Tons (M)"), label);
    }

    if (sqlQuery[TKM_M] != null) {
      JLabel label = new JLabel();
      label.setName("" + TKM_M);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "T_Km_M", "T.Km (M)"), label);
    }

    if (sqlQuery[LoadedTons_Mm] != null) {
      JLabel label = new JLabel();
      label.setName("" + LoadedTons_Mm);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "Loaded_tons_Mm", "Loaded tons (Mm)"), label);
    }

    if (sqlQuery[TKM_Mm] != null) {
      JLabel label = new JLabel();
      label.setName("" + TKM_Mm);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "T_Km_Mm", "T.Km (Mm)"), label);
    }

    if (sqlQuery[UnloadedTons_M] != null) {
      JLabel label = new JLabel();
      label.setName("" + UnloadedTons_M);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "Unloaded_Tons_M", "Unloaded Tons (M)"), label);
    }

    if (sqlQuery[UnloadedTons_Mm] != null) {
      JLabel label = new JLabel();
      label.setName("" + UnloadedTons_Mm);
      tabbedPanes.addTab(
          i18n.get(StatPieDlg.class, "Unloaded_tons_Mm", "Unloaded tons (Mm)"), label);
    }

    if (sqlQuery[VKM_M] != null) {
      JLabel label = new JLabel();
      label.setName("" + VKM_M);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "Veh_Km_M", "Veh.Km (M)"), label);
    }

    if (sqlQuery[VKM_Mm] != null) {
      JLabel label = new JLabel();
      label.setName("" + VKM_Mm);
      tabbedPanes.addTab(i18n.get(StatPieDlg.class, "Veh_Km_Mm", "Veh.Km (Mm)"), label);
    }

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            close();
          }
        });

    JButton closeButton = new JButton(i18n.get(StatPieDlg.class, "Close", "Close"));
    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            close();
          }
        });

    pane.add(tabbedPanes, tabbedPanesConstraints);
    pane.add(getScrollPane(), scrollPaneConstraints);
    pane.add(closeButton, closeButtonConstraints);

    fillTable();
    pack();
    setLocationRelativeTo(null);
    setVisible(true);
    getRootPane().setDefaultButton(closeButton);
  }

  /**
   * Executes the query referent to by the index and fill the vectors with labels and values.
   *
   * @param byte Index
   */
  private void executeQuery(byte index) {
    nodusProject.getNodusMapPanel().setBusy(true);
    final Cursor oldCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));

    try {
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();

      // Connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlQuery[index]);

      ResultSetMetaData m = rs.getMetaData();

      /* col will be equal to 2 for "mode" related stats and 3 for "mode-means" stats. */
      int col = m.getColumnCount();

      labels[index] = new Vector<String>();
      values[index] = new Vector<Float>();

      // Track all the mode-means combinations that are used
      boolean[][] usedModeMeans = new boolean[NodusC.MAXMM][NodusC.MAXMM];

      while (rs.next()) {

        if (col == 2) {
          // Mode stats
          int mode = rs.getInt(1);
          labels[index].add(
              MessageFormat.format(i18n.get(StatPieDlg.class, "Mode", "Mode {0}"), mode));
          values[index].add(Float.valueOf(rs.getFloat(2)));
          colors[index].add(modeColors[mode]);
        } else {
          // Mode-Means stats
          int mode = rs.getInt(1);
          int means = rs.getInt(2);
          labels[index].add(
              MessageFormat.format(
                  i18n.get(StatPieDlg.class, "ModeMeans", "Mode {0}, Means {1}"), mode, means));
          values[index].add(Float.valueOf(rs.getFloat(3)));

          // Collect all the means for this mode
          usedModeMeans[mode][means] = true;
        }
      }

      // Get the color shades for the mode-means stats
      if (col == 3) {
        colors[index].addAll(getShades(usedModeMeans));
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      // Probably because one tries to gather info for a group which was not assigned
      // ex.printStackTrace();
    }

    nodusProject.getNodusMapPanel().setBusy(false);
    setCursor(oldCursor);
  }

  /** Fill the table with labels and values. Compute market shares. */
  private void fillTable() {
    // Clear current table
    while (tableModel.getRowCount() > 0) {
      tableModel.removeRow(0);
    }

    byte index = Byte.parseByte(tabbedPanes.getSelectedComponent().getName());

    // Fill with relevant data
    float sum = 0;
    int size = 0;
    if (labels[index] != null) {
      size = labels[index].size();
    }

    // Nothing to display
    if (size == 0) {
      return;
    }

    for (int i = 0; i < size; i++) {
      sum += values[index].get(i);
    }

    // Compute market marketShare and fill table
    for (int i = 0; i < size; i++) {

      // Value
      Long l = values[index].get(i).longValue();

      // Market marketShare
      String s = formatter.format(Float.valueOf(100 * values[index].get(i) / sum));

      // Add row
      tableModel.addRow(new Object[] {labels[index].get(i), l, s});
    }
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartLoadedTonsPerMode() {
    return getPieChart(
        LoadedTons_M, i18n.get(StatPieDlg.class, "Loaded_tons_per_mode", "Loaded tons per mode"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartLoadedTonsPerModeMeans() {
    return getPieChart(
        LoadedTons_Mm,
        i18n.get(StatPieDlg.class, "Loaded_tons_per_mode_means", "Loaded tons per mode/means"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartTonsKmPerMode() {
    return getPieChart(TKM_M, i18n.get(StatPieDlg.class, "Tons_Km_per_mode", "Tons.Km per mode"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartTonsKmPerModeMeans() {
    return getPieChart(
        TKM_Mm, i18n.get(StatPieDlg.class, "Tons_Km_per_mode_means", "Tons.Km per mode/means"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartUnloadedTonsPerMode() {
    return getPieChart(
        UnloadedTons_M,
        i18n.get(StatPieDlg.class, "Unloaded_tons_per_mode", "Unloaded tons per mode"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartUnloadedTonsPerModeMeans() {
    return getPieChart(
        UnloadedTons_Mm,
        i18n.get(StatPieDlg.class, "Unloaded_tons_per_mode_means", "Unloaded tons per mode/means"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartVehiclesKmPerMode() {
    return getPieChart(
        VKM_M, i18n.get(StatPieDlg.class, "Vehicles_Km_per_mode", "Vehicles.Km per mode"));
  }

  /** Returns the pie chart for this statistic. */
  private XChartPanel<PieChart> getChartVehiclesKmPerModeMeans() {
    return getPieChart(
        VKM_Mm,
        i18n.get(StatPieDlg.class, "Vehicles_Km_per_mode_means", "Vehicles.Km per mode/means"));
  }

  /** Fill an array with the base color to associate to each mode. */
  private void getModeColors() {

    // Fill the array with default colors
    modeColors = new Color[NodusC.MAXMM];
    Color[] defaultSequence = {
      Color.red,
      Color.blue,
      Color.gray,
      Color.green,
      Color.cyan,
      Color.magenta,
      Color.orange,
      Color.pink,
      Color.yellow
    };
    int j = 0;
    modeColors[0] = Color.white;
    for (int i = 1; i < modeColors.length; i++) {
      modeColors[i] = defaultSequence[j];
      j++;
      if (j == defaultSequence.length) {
        j = 0;
      }
    }

    for (int i = 0; i < nbStats; i++) {
      colors[i] = new Vector<Color>();
    }

    // Update the array with colors chosen by the user for the project
    for (int i = 0; i < NodusC.MAXMM; i++) {
      String key = NodusC.PROP_MODE_COLOR + "." + i;
      String colorString = nodusProject.getProperty(key);
      if (colorString != null) {
        modeColors[i] = ColorUtils.getColorFromString(colorString, modeColors[i]);
      }
    }
  }

  /** Returns a vector with color shades for a given mode. */
  private Vector<Color> getShades(boolean[][] usedModeMeans) {

    Vector<Color> modeMeansColors = new Vector<Color>();
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {

      int nbMeans = 0;
      int maxMeans = 0;
      for (int means = 0; means < NodusC.MAXMM; means++) {
        if (usedModeMeans[mode][means]) {
          nbMeans++;
          maxMeans = means;
        }
      }

      // If at least one means is used for the mode
      if (nbMeans > 0) {
        Color[] shades = ColorUtils.getShadesPallette(modeColors[mode], maxMeans);
        for (int means = 0; means < NodusC.MAXMM; means++) {
          if (usedModeMeans[mode][means]) {
            modeMeansColors.add(shades[means - 1]);
          }
        }
      }
    }

    return modeMeansColors;
  }

  /**
   * This method initializes the Panel.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getPane() {
    if (pane == null) {
      pane = new JPanel();
      pane.setLayout(new GridBagLayout());
    }
    return pane;
  }

  /**
   * Creates a pie chart for given statistic and title.
   *
   * @param statId The ID of the statistic to display.
   * @param title The title to add to the pie chart/
   * @return The pie chart.
   */
  private XChartPanel<PieChart> getPieChart(byte statId, String title) {

    executeQuery(statId);

    // If nothing was returned from the query
    if (values[statId] == null) {
      return null;
    }

    PieChart chart = new PieChartBuilder().width(600).height(450).theme(ChartTheme.Matlab).build();
    chart.setTitle(title);

    // Customize Chart
    PieStyler styler = chart.getStyler();
    styler.setLabelType(LabelType.Percentage);
    styler.setLabelsDistance(1.15);
    styler.setPlotContentSize(0.75);
    styler.setLegendVisible(true);
    styler.setForceAllLabelsVisible(true);
    styler.setLabelsFontColorAutomaticEnabled(false);

    // Set the colors given ti the modes
    Color[] c = (Color[]) colors[statId].toArray(new Color[colors[statId].size()]);
    chart.getStyler().setSeriesColors(c);

    // Reduce font sizes
    Font font = chart.getStyler().getAnnotationTextFont();
    float size = 12;
    chart.getStyler().setAnnotationTextFont(font.deriveFont(size));
    font = chart.getStyler().getLegendFont();
    size = 9;
    chart.getStyler().setLegendFont(font.deriveFont(size));

    // Add the series to the chart
    for (int i = 0; i < values[statId].size(); i++) {
      chart.addSeries(labels[statId].elementAt(i), values[statId].elementAt(i));
    }

    XChartPanel<PieChart> chartPanel = new XChartPanel<>(chart);
    chartPanel.setName("" + statId);
    return new XChartPanel<>(chart);
  }

  /**
   * This method initializes the ScrollPane.
   *
   * @return javax.swing.JScrollPane
   */
  private JScrollPane getScrollPane() {
    if (scrollPane == null) {
      scrollPane = new JScrollPane();
      Dimension d = new Dimension(600, 100);
      scrollPane.setSize(d);
      scrollPane.setPreferredSize(d);
      scrollPane.setViewportView(getTable());
    }
    return scrollPane;
  }

  /**
   * This method initializes panes.
   *
   * @return javax.swing.JTabbedPane
   */
  private JTabbedPane getTabbedPanes() {
    if (tabbedPanes == null) {
      tabbedPanes = new JTabbedPane();

      tabbedPanes.addChangeListener(
          new javax.swing.event.ChangeListener() {
            // This method is called whenever the selected tab changes
            @Override
            public void stateChanged(ChangeEvent evt) {

              // Get the type of graph
              byte index = Byte.parseByte(tabbedPanes.getSelectedComponent().getName());

              // Generate graph if needed (a simple JLabel was added as a fake component)
              Component c = tabbedPanes.getComponentAt(tabbedPanes.getSelectedIndex());

              if (c instanceof JLabel) {
                XChartPanel<PieChart> chartPanel = null;
                switch (index) {
                  case LoadedTons_M:
                    chartPanel = getChartLoadedTonsPerMode();
                    break;
                  case TKM_M:
                    chartPanel = getChartTonsKmPerMode();
                    break;
                  case LoadedTons_Mm:
                    chartPanel = getChartLoadedTonsPerModeMeans();
                    break;
                  case TKM_Mm:
                    chartPanel = getChartTonsKmPerModeMeans();
                    break;
                  case UnloadedTons_M:
                    chartPanel = getChartUnloadedTonsPerMode();
                    break;
                  case UnloadedTons_Mm:
                    chartPanel = getChartUnloadedTonsPerModeMeans();
                    break;
                  case VKM_M:
                    chartPanel = getChartVehiclesKmPerMode();
                    break;
                  case VKM_Mm:
                    chartPanel = getChartVehiclesKmPerModeMeans();
                    break;
                  default:
                    break;
                }
                if (chartPanel != null) {
                  chartPanel.setName("" + index);
                  tabbedPanes.setComponentAt(tabbedPanes.getSelectedIndex(), chartPanel);
                }
              }

              fillTable();
            }
          });
    }
    return tabbedPanes;
  }

  /**
   * This method initializes jTable.
   *
   * @return javax.swing.JTable
   */
  private JTable getTable() {
    if (table == null) {
      table = new JTable(sorter);
      tableModel.addColumn("");
      tableModel.addColumn(i18n.get(StatPieDlg.class, "Values", "Values"));
      tableModel.addColumn(i18n.get(StatPieDlg.class, "Market_shares", "Market shares"));
      sorter.setTableHeader(table.getTableHeader());
    }
    return table;
  }

  /**
   * Properly close if ESC is pressed.
   *
   * @hidden
   */
  @Override
  public void keyPressed(KeyEvent e) {
    int code = e.getKeyCode();

    if (code == KeyEvent.VK_ESCAPE) {
      close();
    }

    super.keyPressed(e);
  }

  /**
   * Set the query string for a given type of statistic.
   *
   * @param statId The ID of the statistic
   * @param sqlQuery The SQL query that retrieves the statistic.
   */
  public void setQueryString(byte statId, String sqlQuery) {

    this.sqlQuery[statId] = sqlQuery;
  }
}
