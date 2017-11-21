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
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SwingConstants;

/**
 * Dialog box that generates SQL statements for various statistics that can be performed on the
 * Nodus database. The generated SQL string is then passed to the SQL console that calls this dialog
 * box.
 *
 * @author Bart Jourquin
 */
public class StatDlg extends EscapeDialog {

  private static final long serialVersionUID = -214549961309202274L;
  private static I18n i18n = Environment.getI18n();

  private static final String PROP_STAT_LD_T_M = "stat_LoadedTonsPerMode";
  private static final String PROP_STAT_LD_T_MM = "stat_LoadedTonsPerModeMeans";
  private static final String PROP_STAT_NBOD = "stat_NbOD";
  private static final String PROP_STAT_TKM_M = "stat_TKmPerMode";
  private static final String PROP_STAT_TKM_MM = "stat_TKmPerModeMeans";
  private static final String PROP_STAT_TOTAL_COST = "stat_TotalCost";
  private static final String PROP_STAT_UL_T_M = "stat_UnloadedTonsPerMode";
  private static final String PROP_STAT_UL_T_MM = "stat_UnloadedTonsPerModeMeans";
  private static final String PROP_STAT_VKM_M = "stat_VehKmPerMode";
  private static final String PROP_STAT_VKM_MM = "stat_VehKmPerModeMeans";

  private JPanel checkBoxPanel = new JPanel();

  private GridBagLayout checkBoxPanelGridBagLayout = new GridBagLayout();

  private JButton closeButton = new JButton();

  private JButton deselectAllButton = new JButton();

  private String distanceUnit = "km";

  private JLabel dummyLabel = new JLabel();

  private JButton generateQueriesButton = new JButton();

  private JLabel groupLabel = new JLabel();

  private JSpinner groupSpinner = new JSpinner();

  private JDBCUtils jdbcUtils;

  private JCheckBox loadedTonsPerModeCheckBox = new JCheckBox();

  private JCheckBox loadedTonsPerModeMeansCheckBox = new JCheckBox();

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private JCheckBox nbODCheckBox = new JCheckBox();

  private NodusProject nodusProject;

  private JCheckBox pieCheckBox = null;

  private JPanel rightPanel = new JPanel();

  private GridBagLayout rightPanelGridBagLayout = new GridBagLayout();

  private JComboBox<String> scenarioComboBox = new JComboBox<String>();

  private JLabel scenarioLabel = new JLabel();

  private JButton selectAllButton = new JButton();

  private String[] spinnerList;

  private SQLConsole sqlConsole;

  private JCheckBox tonsKmPerModeCheckBox = new JCheckBox();

  private JCheckBox tonsKmPerModeMeansCheckBox = new JCheckBox();

  private JCheckBox totalCostCheckBox = new JCheckBox();

  private JCheckBox unloadedTonsPerModeCheckBox = new JCheckBox();

  private JCheckBox unloadedTonsPerModeMeansCheckBox = new JCheckBox();

  private JCheckBox vehKmPerModeCheckBox = new JCheckBox();

  private JCheckBox vehKmPerModeMeansCheckBox = new JCheckBox();

  private String virtualNetTableName;

  /**
   * Creates the dialog.
   *
   * @param sqlConsole The SQLConsole this dialog is created from.
   * @param nodusProject The Nodus project.
   */
  public StatDlg(SQLConsole sqlConsole, NodusProject nodusProject) {
    super(sqlConsole.getFrame(), i18n.get(StatDlg.class, "Statistics", "Statistics"), false);

    this.sqlConsole = sqlConsole;
    this.nodusProject = nodusProject;
    jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());

    initialize();
    getRootPane().setDefaultButton(closeButton);

    int x = nodusProject.getNodusMapPanel().getMainFrame().getX();
    int y = nodusProject.getNodusMapPanel().getMainFrame().getY();
    setLocation(x + 100, y + 100);
  }

  /**
   * Simply closes the dialog box.
   *
   * @param e ActionEvent
   */
  private void closeButton_actionPerformed(ActionEvent e) {
    saveState();
    setVisible(false);
  }

  /**
   * Unselect all the statistics.
   *
   * @param e ActionEvent
   */
  private void deselectAllButton_actionPerformed(ActionEvent e) {
    selectCheckBoxes(false);
  }

  /** Generates the queries for the selected statistics. */
  private void generateQueries() {
    String batchSqlStmt = "";

    // Fetch scenario
    virtualNetTableName = (String) scenarioComboBox.getSelectedItem();

    // Fetch group
    int group = -1;
    group = -1;

    String value = (String) groupSpinner.getValue();

    if (value.compareTo(spinnerList[0]) != 0) {
      group = Integer.parseInt(value);
    }

    String sqlStmt;
    String header;

    nodusProject.getNodusMapPanel().setBusy(true);
    final Cursor oldCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));

    StatPieDlg statPieDlg = new StatPieDlg(this);

    /**
     * Number of entries in the OD matrix. Samples : <br>
     * - select count() from od<br>
     * select count() from od where grp = 1<br>
     */
    if (nbODCheckBox.isSelected()) {
      String defTable = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + " _od";
      String odTableName = nodusProject.getLocalProperty(NodusC.PROP_OD_TABLE, defTable);
      // JDBCUtils jdbcUtils = new JDBCUtils(jdbcConnection);
      odTableName = jdbcUtils.getCompliantIdentifier(odTableName);

      header =
          "#" + i18n.get(StatDlg.class, "O_D_entries_in", "O-D entries in") + " " + odTableName;

      sqlStmt = "SELECT COUNT(*) FROM " + odTableName;
      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        sqlStmt +=
            " WHERE " + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP) + " = " + group;
      }

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Total cost. Samples : <br>
     * - select sum(cost*qty) from vnet0 <br>
     * - select sum(cost1*qty1) from vnet0 <br>
     */
    if (totalCostCheckBox.isSelected()) {
      header = "#" + i18n.get(StatDlg.class, "Total_cost", "Total cost");

      String qty = NodusC.DBF_QUANTITY;
      String cost = NodusC.DBF_UNITCOST;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
        cost += group;
      }

      sqlStmt =
          "SELECT ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(cost)
              + "*"
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "),1) FROM "
              + virtualNetTableName;

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    // Unit to be used for flows : t_km r t_mile
    String unit = "T_KM";

    /**
     * Tons km/miles per mode. Samples: <br>
     * select mode1, sum(qty*length) as tkm from vnet0 where length > 0 group by mode1 order by
     * mode1<br>
     * select mode1, sum(qty1*length) as tkm from vnet0 where length > 0 group by mode1 order by
     * mode1<br>
     */
    if (tonsKmPerModeCheckBox.isSelected()) {
      header =
          "#" + MessageFormat.format(i18n.get(StatDlg.class, "per_mode", "{0} per mode"), unit);
      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "*"
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + "),0) AS "
              + unit
              + " FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1);

      statPieDlg.setQueryString(StatPieDlg.TKM_M, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Tons km/miles per mode/means. Samples: <br>
     * - select mode1, means1, sum(qty*length) as tkm from vnet0 where length > 0 group by mode1,
     * means1 ordr by mode1, means1<br>
     * - select mode1, means1, sum(qty1*length1) as tkm from vnet0 where length > 0 group by mode1,
     * means1 order by mode1, means1·<br>
     * ·
     */
    if (tonsKmPerModeMeansCheckBox.isSelected()) {
      header =
          "#"
              + MessageFormat.format(
                  i18n.get(StatDlg.class, "per_mode_means", "{0} per mode/means"), unit);
      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "*"
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + "),0) AS "
              + unit
              + " FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1);

      statPieDlg.setQueryString(StatPieDlg.TKM_Mm, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    // Unit to be used for flows : t_km r t_mile
    unit = "VEH_KM";

    /**
     * Vehicles km/miles per mode. Samples:<br>
     * - select mode1, sum(veh*length) as tkm from vnet0 where length > 0 group by mode1 order by
     * mode1<br>
     * - select mode1, sum(veh1*length1) as tkm from vnet0 where length > 0 group by mode1 order by
     * mode1<br>
     */
    if (vehKmPerModeCheckBox.isSelected()) {
      header =
          "#" + MessageFormat.format(i18n.get(StatDlg.class, "per_mode", "{0} per mode"), unit);

      String veh = NodusC.DBF_VEHICLES;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        veh += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(veh)
              + "*"
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + "),0) AS "
              + unit
              + " FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1);

      statPieDlg.setQueryString(StatPieDlg.VKM_M, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Vehicles km/miles per mode/means. Samples: <br>
     * - select mode1, means1, sum(veh*length) as tkm from vnet0 where length > 0 group by mode1,
     * means1 order by mode1, means1<br>
     * - select mode1, means1, sum(veh1*length1) as tkm from vnet0 where length > 0 group by mode1,
     * means1 order by mode1, means1<br>
     */
    if (vehKmPerModeMeansCheckBox.isSelected()) {
      header =
          "#"
              + MessageFormat.format(
                  i18n.get(StatDlg.class, "per_mode_means", "{0} per mode/means"), unit);

      String veh = NodusC.DBF_VEHICLES;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        veh += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(veh)
              + "*"
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + "),0) AS "
              + unit
              + " FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1);

      statPieDlg.setQueryString(StatPieDlg.VKM_Mm, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Loaded tons per mode. Samples: <br>
     * - select mode2, sum(qty) from vnet0 where mode1=0 and mode2 > 0 group by mode2 order by mode2
     * <br>
     * - select mode2, sum(qty1) from vnet0 where mode1=0 and mode2 > 0 group by mode2 order by
     * mode2<br>
     */
    if (loadedTonsPerModeCheckBox.isSelected()) {
      header = "#" + i18n.get(StatDlg.class, "Loaded_tons_per_mode", "Loaded tons per mode");

      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "),0) FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " = 0 AND "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2);

      statPieDlg.setQueryString(StatPieDlg.LoadedTons_M, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Loaded tons per mode/means. Samples : <br>
     * - select mode2, means2, sum(qty) from vnet0 where mode1=0 and mode2 > 0 group by mode2,means2
     * order by mode2, means2 <br>
     * - select mode2, means2, sum(qty1) from vnet0 where mode1=0 and mode2 > 0 group by
     * mode2,means2 order by mode2, means2<br>
     */
    if (loadedTonsPerModeMeansCheckBox.isSelected()) {
      header =
          "#" + i18n.get(StatDlg.class, "Loaded_tons_per_mode_means", "Loaded tons per mode/means");

      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "),0) FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " = 0 AND "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2);

      statPieDlg.setQueryString(StatPieDlg.LoadedTons_Mm, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Unloaded tons per mode. Samples : <br>
     * - select mode1, sum(qty) from vnet0 where mode2=0 and mode1 > 0 group by mode1 order by mode1
     * <br>
     * - select mode1, sum(qty1) from vnet0 where mode2=0 and mode1 > 0 group by mode1 order by
     * mode1<br>
     */
    if (unloadedTonsPerModeCheckBox.isSelected()) {
      header = "#" + i18n.get(StatDlg.class, "Unloaded_tons_per_mode", "Unloaded tons per mode");

      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "),0) FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + " = 0 AND "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1);

      statPieDlg.setQueryString(StatPieDlg.UnloadedTons_M, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    /**
     * Unloaded tons per mode/means. Samples : <br>
     * - select mode1, means1, sum(qty) from vnet0 where mode2=0 and mode1 > 0 group by mode1,means1
     * order by mode1, means1<br>
     * - select mode1, means1, sum(qty1) from vnet0 where mode2=0 and mode1 > 0 group by
     * mode1,means1 order by mode1, means1<br>
     */
    if (unloadedTonsPerModeMeansCheckBox.isSelected()) {
      header =
          "#"
              + i18n.get(
                  StatDlg.class, "Unloaded_tons_per_mode_means", "Unloaded tons per mode/means");

      String qty = NodusC.DBF_QUANTITY;

      if (group != -1) {
        header += " " + i18n.get(StatDlg.class, "for_group", "for group") + " " + group;
        qty += group;
      }

      sqlStmt =
          "SELECT "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + ", ROUND(SUM("
              + jdbcUtils.getQuotedCompliantIdentifier(qty)
              + "),0) FROM "
              + virtualNetTableName
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
              + " = 0 AND "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + " > 0 GROUP BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
              + " ORDER BY "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
              + ", "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1);

      statPieDlg.setQueryString(StatPieDlg.UnloadedTons_Mm, sqlStmt);

      batchSqlStmt += header + ";\n" + sqlStmt + ";\n";
    }

    sqlConsole.getSqlCommandArea().setText(batchSqlStmt);
    sqlConsole.resetScript();
    nodusProject.getNodusMapPanel().setBusy(false);
    setCursor(oldCursor);

    if (pieCheckBox.isSelected()) {
      statPieDlg.displayGUI();
    }
  }

  /**
   * Builds all the queries related to the selected statistics.
   *
   * @param e ActionEvent
   */
  private void generateQueriesButton_actionPerformed(ActionEvent e) {
    generateQueries();
  }

  /**
   * Enables or not the "Generate queries" button. Used by the StatPieDlg to avoid reentrance.
   *
   * @param enable If true, enable the button.
   */
  public void enableGenerateQueriesButton(boolean enable) {
    generateQueriesButton.setEnabled(enable);
  }

  /**
   * Returns the Nodus project.
   *
   * @return The Nodus project.
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  /**
   * This method initializes pieCheckBox
   *
   * @return javax.swing.JCheckBox
   */
  private JCheckBox getPieCheckBox() {
    if (pieCheckBox == null) {
      pieCheckBox = new JCheckBox();
      pieCheckBox.setText(i18n.get(StatDlg.class, "draw_graphs", "Draw graph(weights)"));
    }
    return pieCheckBox;
  }

  /**
   * Initializes the dialog box GUI.
   *
   * @throws Exception On error
   */
  private void initialize() {
    // Which distance unit is used?
    GridBagConstraints gridBagConstraints1 =
        new GridBagConstraints(
            0,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 0),
            0,
            0);
    gridBagConstraints1.gridy = 7;
    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    GridBagConstraints okButtonConstraints =
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0);
    okButtonConstraints.insets = new Insets(0, 5, 0, 5);

    okButtonConstraints.gridy = 6;

    mainPanel.setLayout(mainPanelGridBagLayout);
    nbODCheckBox.setHorizontalAlignment(SwingConstants.LEADING);
    nbODCheckBox.setOpaque(false);
    nbODCheckBox.setSelected(false);
    nbODCheckBox.setText(i18n.get(StatDlg.class, "Number_of_O_D_entries", "Number of O-D entries"));

    totalCostCheckBox.setSelected(false);
    totalCostCheckBox.setOpaque(false);
    totalCostCheckBox.setText(i18n.get(StatDlg.class, "Total_cost", "Total cost"));

    checkBoxPanel.setLayout(checkBoxPanelGridBagLayout);

    loadedTonsPerModeCheckBox.setSelected(true);
    loadedTonsPerModeCheckBox.setOpaque(false);
    loadedTonsPerModeCheckBox.setText(
        i18n.get(StatDlg.class, "Loaded_tons_per_mode", "Loaded tons per mode"));

    loadedTonsPerModeMeansCheckBox.setText(
        i18n.get(StatDlg.class, "Loaded_tons_per_mode_means", "Loaded tons per mode/means"));
    loadedTonsPerModeMeansCheckBox.setOpaque(false);

    unloadedTonsPerModeCheckBox.setText(
        i18n.get(StatDlg.class, "Unloaded_tons_per_mode", "Unloaded tons per mode"));
    unloadedTonsPerModeCheckBox.setOpaque(false);

    unloadedTonsPerModeMeansCheckBox.setText(
        i18n.get(StatDlg.class, "Unloaded_tons_per_mode_means", "Unloaded tons per mode/means"));
    unloadedTonsPerModeMeansCheckBox.setOpaque(false);

    vehKmPerModeCheckBox.setText(
        MessageFormat.format(
            i18n.get(StatDlg.class, "Vehicleskmpm", "Vehicles.{0} per mode"), distanceUnit));
    vehKmPerModeCheckBox.setOpaque(false);

    vehKmPerModeMeansCheckBox.setText(
        MessageFormat.format(
            i18n.get(StatDlg.class, "Vehicleskmpmm", "Vehicles.{0} per mode/means"), distanceUnit));
    vehKmPerModeMeansCheckBox.setOpaque(false);

    tonsKmPerModeCheckBox.setSelected(true);
    tonsKmPerModeCheckBox.setOpaque(false);
    tonsKmPerModeCheckBox.setText(
        MessageFormat.format(
            i18n.get(StatDlg.class, "Tonskmpm", "Tons.{0} per mode"), distanceUnit));

    tonsKmPerModeMeansCheckBox.setText(
        MessageFormat.format(
            i18n.get(StatDlg.class, "Tonskmpmm", "Tons.{0} per mode/means"), distanceUnit));
    tonsKmPerModeMeansCheckBox.setOpaque(false);

    rightPanel.setLayout(rightPanelGridBagLayout);
    groupLabel.setText(i18n.get(StatDlg.class, "Group", "Group"));

    generateQueriesButton.setText(i18n.get(StatDlg.class, "Generate_queries", "Generate queries"));
    generateQueriesButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            generateQueriesButton_actionPerformed(e);
          }
        });

    closeButton.setText(i18n.get(StatDlg.class, "Close", "Close"));
    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });

    selectAllButton.setText(i18n.get(StatDlg.class, "Select_all", "Select all"));
    selectAllButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            selectAllButton_actionPerformed(e);
          }
        });

    deselectAllButton.setText(i18n.get(StatDlg.class, "Deselect_all", "Deselect all"));
    deselectAllButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            deselectAllButton_actionPerformed(e);
          }
        });
    // JDBCUtils jdbcUtils = new JDBCUtils(jdbcConnection);
    scenarioLabel.setText(i18n.get(StatDlg.class, "Scenario", "Scenario"));

    setContentPane(mainPanel);

    mainPanel.add(
        checkBoxPanel,
        new GridBagConstraints(
            0,
            0,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(10, 10, 10, 0),
            0,
            0));
    mainPanel.add(
        rightPanel,
        new GridBagConstraints(
            2,
            0,
            1,
            2,
            1.1,
            1.1,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.BOTH,
            new Insets(10, 5, 10, 5),
            0,
            0));
    rightPanel.add(
        scenarioLabel,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            1.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 0, 5),
            0,
            0));
    rightPanel.add(
        scenarioComboBox,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            1.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 5, 5),
            0,
            0));
    rightPanel.add(
        groupLabel,
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    rightPanel.add(
        groupSpinner,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            1.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 0, 5),
            0,
            0));
    rightPanel.add(
        dummyLabel,
        new GridBagConstraints(
            0,
            4,
            1,
            1,
            1.0,
            1.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0),
            0,
            0));
    rightPanel.add(generateQueriesButton, okButtonConstraints);
    rightPanel.add(closeButton, gridBagConstraints1);
    rightPanel.add(getPieCheckBox(), gridBagConstraints);
    mainPanel.add(
        deselectAllButton,
        new GridBagConstraints(
            1,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 10, 0),
            0,
            0));
    mainPanel.add(
        selectAllButton,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 10, 10, 5),
            0,
            0));
    checkBoxPanel.add(
        nbODCheckBox,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        totalCostCheckBox,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        loadedTonsPerModeCheckBox,
        new GridBagConstraints(
            0,
            9,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        loadedTonsPerModeMeansCheckBox,
        new GridBagConstraints(
            0,
            10,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        unloadedTonsPerModeCheckBox,
        new GridBagConstraints(
            0,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        unloadedTonsPerModeMeansCheckBox,
        new GridBagConstraints(
            0,
            12,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        tonsKmPerModeCheckBox,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        tonsKmPerModeMeansCheckBox,
        new GridBagConstraints(
            0,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        vehKmPerModeCheckBox,
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    checkBoxPanel.add(
        vehKmPerModeMeansCheckBox,
        new GridBagConstraints(
            0,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));

    // Get the current scenario
    int currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);

    for (int i = 0; i < NodusC.MAXSCENARIOS; i++) {
      virtualNetTableName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      virtualNetTableName =
          nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, virtualNetTableName) + i;
      virtualNetTableName = jdbcUtils.getCompliantIdentifier(virtualNetTableName);

      if (jdbcUtils.tableExists(virtualNetTableName)) {
        scenarioComboBox.addItem(virtualNetTableName);

        if (i == currentScenario) {
          scenarioComboBox.setSelectedItem(virtualNetTableName);
        }
      }
    }

    // At least one scenario must exists
    if (scenarioComboBox.getItemCount() == 0) {
      generateQueriesButton.setEnabled(false);
    }

    // Fill the spinner with possible groups
    spinnerList = new String[NodusC.MAXMM + 1];
    spinnerList[0] = "All";

    for (int i = 0; i < NodusC.MAXMM; i++) {
      spinnerList[i + 1] = String.valueOf(i);
    }

    reloadState();

    SpinnerListModel listModel = new SpinnerListModel(spinnerList);
    groupSpinner.setModel(listModel);

    pack();
  }

  /** Set the checkboxes in the state they were saved. */
  private void reloadState() {
    nbODCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_NBOD, false));
    totalCostCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_TOTAL_COST, false));
    loadedTonsPerModeCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_LD_T_M, true));
    loadedTonsPerModeMeansCheckBox.setSelected(
        nodusProject.getLocalProperty(PROP_STAT_LD_T_MM, false));
    unloadedTonsPerModeCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_UL_T_M, false));
    unloadedTonsPerModeMeansCheckBox.setSelected(
        nodusProject.getLocalProperty(PROP_STAT_UL_T_MM, false));
    tonsKmPerModeCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_TKM_M, true));
    tonsKmPerModeMeansCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_TKM_MM, false));
    vehKmPerModeCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_VKM_M, false));
    vehKmPerModeMeansCheckBox.setSelected(nodusProject.getLocalProperty(PROP_STAT_VKM_MM, false));
  }

  /** Save the state of the checkboxes in the local properties. */
  private void saveState() {
    nodusProject.setLocalProperty(PROP_STAT_NBOD, nbODCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_TOTAL_COST, totalCostCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_LD_T_M, loadedTonsPerModeCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_LD_T_MM, loadedTonsPerModeMeansCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_UL_T_M, unloadedTonsPerModeCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_UL_T_MM, unloadedTonsPerModeMeansCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_TKM_M, tonsKmPerModeCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_TKM_MM, tonsKmPerModeMeansCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_VKM_M, vehKmPerModeCheckBox.isSelected());
    nodusProject.setLocalProperty(PROP_STAT_VKM_MM, vehKmPerModeMeansCheckBox.isSelected());
  }

  /**
   * Select all the statistics.
   *
   * @param e ActionEvent
   */
  private void selectAllButton_actionPerformed(ActionEvent e) {
    selectCheckBoxes(true);
  }

  /**
   * Browses the list of checkboxes and select/deselect them all.
   *
   * @param state boolean
   */
  private void selectCheckBoxes(boolean state) {
    Component[] c = checkBoxPanel.getComponents();

    for (Component element : c) {
      if (element instanceof JCheckBox) {
        JCheckBox cb = (JCheckBox) element;
        cb.setSelected(state);
      }
    }
  }
}
