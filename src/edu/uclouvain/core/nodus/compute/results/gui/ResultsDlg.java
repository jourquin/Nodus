/*
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.results.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.layer.LabelLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.results.LinkResults;
import edu.uclouvain.core.nodus.compute.results.NodeResults;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.gui.StatDlg;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Dialog box that is used to ask the user which type of results he wants to display on the
 * networks.
 *
 * @author Bart Jourquin
 */
public class ResultsDlg extends EscapeDialog {

  private static final long serialVersionUID = -6643630055356676869L;

  private static I18n i18n = Environment.getI18n();

  private static final int actionLinkPath = 12;

  private static final int actionLinkQuantities = 10;

  private static final int actionLinkTimeDependentQuantities = 13;

  private static final int actionLinkVehicles = 11;

  private static final int actionNodeOD = 0;

  /** Set of possible results to display on links. */
  private static String[] linkActions = {
    i18n.get(ResultsDlg.class, "Assigned_quantities", "Assigned quantities"),
    i18n.get(ResultsDlg.class, "Assigned_vehicles", "Assigned vehicles"),
    i18n.get(ResultsDlg.class, "Path", "Path"),
    i18n.get(ResultsDlg.class, "Time_dependant_quantities", "Time dependant quantities")
  };

  /** Set of possible results to display on nodes. */
  static String[] nodeActions = {i18n.get(ResultsDlg.class, "OD_matrix", "O-D matrix")};

  private JComboBox<String> actionsComboBox = new JComboBox<>();

  private ButtonGroup buttonGroup = new ButtonGroup();

  private JButton cancelButton = new JButton();

  private int currentAction = -1;

  private int currentScenario;

  private JButton defaultQueryButton = null;

  private JCheckBox exportCheckBox = null;

  private GridBagLayout gridBagLayout1 = new GridBagLayout();

  private JRadioButton linkRadioButton = new JRadioButton();

  private String linksFlowQueryString = "";

  private String dynamicFlowQueryString = "";

  private String linksVehiclesQueryString = "";

  private JPanel mainPanel = new JPanel();

  private JRadioButton nodeRadioButton = new JRadioButton();

  private String nodesFlowQueryString = "";

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private JButton okButton = new JButton();

  private String pathQueryString = "";

  private JButton resetButton = new JButton();

  private JLabel sqlLabel = new JLabel();

  private RSyntaxTextArea sqlTextPane = new RSyntaxTextArea();

  private final JButton statsButton = new JButton();

  private final JCheckBox relativeToViewCheckBox = new JCheckBox();

  /**
   * Initializes the dialog box.
   *
   * @param mapPanel The NodusMapPanel
   */
  public ResultsDlg(NodusMapPanel mapPanel) {
    super(mapPanel.getMainFrame(), "", true);
    setTitle(i18n.get(ResultsDlg.class, "Display_results", "Display results"));

    nodusMapPanel = mapPanel;
    nodusProject = nodusMapPanel.getNodusProject();
    currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);

    initialize();
    getRootPane().setDefaultButton(okButton);

    setLocationRelativeTo(nodusMapPanel);
  }

  /**
   * Updates the text area with a SQL statement corresponding to the result to display.
   *
   * @param e ActionEvent
   */
  private void actionsComboBox_actionPerformed(ActionEvent e) {
    sqlTextPane.setText("");

    int index = actionsComboBox.getSelectedIndex();
    getExportCheckBox().setEnabled(true);
    if (index > 0) {
      index--;

      if (nodeRadioButton.isSelected()) {
        switch (index) {
          case 0: // Display O-D matrix
            currentAction = actionNodeOD;
            sqlTextPane.setText(getODQueryString());

            break;
          default:
            break;
        }
      } else {
        switch (index) {
          case 0: // Display assigned quantities
            currentAction = actionLinkQuantities;
            sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_QUANTITY));

            break;

          case 1: // Display assigned vehicles
            currentAction = actionLinkVehicles;
            sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_VEHICLES));

            break;

          case 2: // Display path
            currentAction = actionLinkPath;
            sqlTextPane.setText(getPathQueryString());

            break;

          case 3: // Time dependent assigned
            currentAction = actionLinkTimeDependentQuantities;
            sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_QUANTITY, true));
            getExportCheckBox().setSelected(false);
            getExportCheckBox().setEnabled(false);
            break;
          default:
            break;
        }
      }
    }
  }

  /**
   * Just closes the dialog box.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * Resets the default query string for the selected action.
   *
   * @param e Action event
   */
  private void defaultButton_actionPerformed(ActionEvent e) {
    switch (currentAction) {
      case actionNodeOD:
        nodesFlowQueryString = "";
        sqlTextPane.setText(getODQueryString());
        break;

      case actionLinkQuantities:
        linksFlowQueryString = "";
        sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_QUANTITY));
        break;

      case actionLinkVehicles:
        linksVehiclesQueryString = "";
        sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_VEHICLES));
        break;

      case actionLinkPath:
        pathQueryString = "";
        sqlTextPane.setText(getPathQueryString());
        break;

      case actionLinkTimeDependentQuantities:
        dynamicFlowQueryString = "";
        sqlTextPane.setText(getFlowsQueryString(NodusC.DBF_QUANTITY, true));
        break;

      default:
        break;
    }

    actionsComboBox_actionPerformed(e);
  }

  /**
   * This method initializes jButton.
   *
   * @return javax.swing.JButton
   */
  private JButton getDefaultQueryButton() {
    if (defaultQueryButton == null) {
      defaultQueryButton = new JButton();
      defaultQueryButton.setText(i18n.get(ResultsDlg.class, "Default_query", "Default query"));
      defaultQueryButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              defaultButton_actionPerformed(e);
            }
          });
    }

    return defaultQueryButton;
  }

  /**
   * This method initializes jCheckBox.
   *
   * @return javax.swing.JCheckBox
   */
  private JCheckBox getExportCheckBox() {
    if (exportCheckBox == null) {
      exportCheckBox = new JCheckBox();
      exportCheckBox.setText(i18n.get(ResultsDlg.class, "Export", "Export"));
    }
    return exportCheckBox;
  }

  /**
   * Builds a template SQL query string for the display of the assigned flows. <br>
   * Example :<br>
   * select link, sum(qty) from project_vnet1 where link1 = link2 group by link1 <br>
   * or <br>
   * select link, sum(veh) from project_vnet1 where link1 = link2 group by link1 <br>
   *
   * @param typeOfFlow String
   * @return String
   */
  private String getFlowsQueryString(String typeOfFlow) {
    return getFlowsQueryString(typeOfFlow, false);
  }

  /**
   * Builds an SQL query string for the display of the assignment.
   *
   * @param typeOfFlow NodusC.DBF_QUANTITY or NodusC.DBF_VEHICLES.
   * @param isTimeDependent True if the result to display is time dependent.
   * @return The queru string.
   */
  private String getFlowsQueryString(String typeOfFlow, boolean isTimeDependent) {
    // Get the latest
    if (!isTimeDependent) {
      if (typeOfFlow.equals(NodusC.DBF_QUANTITY)) {
        if (!linksFlowQueryString.equals("")) {
          return linksFlowQueryString;
        }
      } else {
        if (!linksVehiclesQueryString.equals("")) {
          return linksVehiclesQueryString;
        }
      }
    } else {
      if (!dynamicFlowQueryString.equals("")) {
        return dynamicFlowQueryString;
      }
    }

    // Build a new one
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    String tableName =
        nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, defValue) + currentScenario;
    tableName = JDBCUtils.getCompliantIdentifier(tableName);

    String timeString = "";
    if (isTimeDependent) {
      timeString = ", " + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_TIME);
    }

    return "SELECT "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK1)
        + ", SUM("
        + JDBCUtils.getQuotedCompliantIdentifier(typeOfFlow)
        + ") FROM "
        + tableName
        + " WHERE "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK1)
        + " = "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK2)
        + " GROUP BY "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK1)
        + timeString;
  }

  /**
   * Builds a template SQL query string for the display of the O-D matrix. <br>
   * Example: <br>
   * select org, sum(qty) from miniod group by org <br>
   *
   * @return String.
   */
  private String getODQueryString() {
    // Return the latest
    if (!nodesFlowQueryString.equals("")) {
      return nodesFlowQueryString;
    }

    // Get the OD table associated to the current scenario
    String tableName = nodusProject.getLocalProperty(NodusC.PROP_OD_TABLE + currentScenario, null);

    // Get default one if not found
    if (tableName == null) {
      String defValue =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_OD;
      tableName = nodusProject.getLocalProperty(NodusC.PROP_OD_TABLE, defValue);
    }

    tableName = JDBCUtils.getCompliantIdentifier(tableName);

    return "SELECT "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN)
        + ", SUM("
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
        + ") FROM "
        + tableName
        + " GROUP BY "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN);
  }

  /**
   * Builds a template SQL query string for the display of a path between two nodes. <br>
   * The user just has to replace the "???" by the wanted origin and destination node numbers.
   *
   * @return String
   */
  private String getPathQueryString() {
    // Returns the latest
    if (!pathQueryString.equals("")) {
      return pathQueryString;
    }

    // Build a new one
    String defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    String detailTableName =
        nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, defValue)
            + currentScenario
            + NodusC.SUFFIX_DETAIL;
    String headerTableName =
        nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, defValue)
            + currentScenario
            + NodusC.SUFFIX_HEADER;

    detailTableName = JDBCUtils.getCompliantIdentifier(detailTableName);
    headerTableName = JDBCUtils.getCompliantIdentifier(headerTableName);

    /* Does the header file have "time" values ? */
    String timeWhereClause = "";
    if (JDBCUtils.hasSeveralValues(headerTableName, NodusC.DBF_TIME)) {
      timeWhereClause =
          " AND " + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_TIME) + " = ???";
    }

    /*
     * Example : SELECT ABS(miniproject_path0_detail.link), Sum(qty) FROM miniproject_path0_header
     * INNER JOIN miniproject_path0_detail ON miniproject_path0_header.pathidx =
     * miniproject_path0_detail.pathidx where org=11 and dst=3352 GROUP BY
     * miniproject_path0_detail.link
     */
    return "SELECT ABS("
        + detailTableName
        + "."
        + JDBCUtils.getCompliantIdentifier(NodusC.DBF_LINK)
        + "), SUM("
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
        + ")"
        + " FROM "
        + headerTableName
        + " INNER JOIN "
        + detailTableName
        + " ON "
        + headerTableName
        + "."
        + JDBCUtils.getCompliantIdentifier(NodusC.DBF_PATH_INDEX)
        + " = "
        + detailTableName
        + "."
        + JDBCUtils.getCompliantIdentifier(NodusC.DBF_PATH_INDEX)
        + " WHERE "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
        + " = ??"
        + " AND "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN)
        + " = ??? AND "
        + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION)
        + " = ???"
        + timeWhereClause
        + " GROUP BY "
        + detailTableName
        + "."
        + JDBCUtils.getCompliantIdentifier(NodusC.DBF_LINK);
  }

  /** Initializes the GUI components of the dialog box. */
  private void initialize() {
    setContentPane(mainPanel);

    final GridBagConstraints resetButtonConstraints =
        new GridBagConstraints(
            2,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0);

    final GridBagConstraints defaultQueryButtonConstraints = new GridBagConstraints();

    sqlTextPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    sqlTextPane.setHighlightCurrentLine(false);
    sqlTextPane.setLineWrap(true);
    mainPanel.setLayout(gridBagLayout1);
    nodeRadioButton.setEnabled(true);
    nodeRadioButton.setText(i18n.get(ResultsDlg.class, "Nodes", "Nodes"));
    nodeRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            nodeRadioButton_actionPerformed(e);
          }
        });

    linkRadioButton.setSelected(true);
    linkRadioButton.setText(i18n.get(ResultsDlg.class, "Links", "Links"));
    linkRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            linkRadioButton_actionPerformed(e);
          }
        });
    resetButton.setSelectedIcon(null);

    resetButton.setText(i18n.get(ResultsDlg.class, "Reset_display", "Reset display"));
    resetButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            resetButton_actionPerformed(e);
          }
        });

    buttonGroup.add(nodeRadioButton);
    buttonGroup.add(linkRadioButton);

    sqlLabel.setText(i18n.get(ResultsDlg.class, "SQL_statement", "SQL statement (if relevant):"));

    sqlTextPane.setMinimumSize(new Dimension(500, 100));
    sqlTextPane.setPreferredSize(new Dimension(500, 100));
    sqlTextPane.setText("");
    okButton.setText(i18n.get(ResultsDlg.class, "Ok", "Ok"));
    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });

    cancelButton.setText(i18n.get(ResultsDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    statsButton.setText(i18n.get(ResultsDlg.class, "Statistics", "Statistics"));
    GridBagConstraints statsButtonConstraints = new GridBagConstraints();
    statsButtonConstraints.anchor = GridBagConstraints.EAST;
    statsButtonConstraints.insets = new Insets(5, 5, 0, 0);
    statsButtonConstraints.gridx = 4;
    statsButtonConstraints.gridy = 3;
    statsButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            StatDlg statDlg = new StatDlg(nodusProject, null);
            statDlg.setVisible(true);
          }
        });

    GridBagConstraints relativeToViewGridBagConstraint = new GridBagConstraints();
    relativeToViewGridBagConstraint.anchor = GridBagConstraints.EAST;
    relativeToViewGridBagConstraint.insets = new Insets(0, 0, 5, 5);
    relativeToViewGridBagConstraint.gridx = 3;
    relativeToViewGridBagConstraint.gridy = 1;
    relativeToViewCheckBox.setText(
        i18n.get(ResultsDlg.class, "Relative_to_view", "Relative to view"));
    mainPanel.add(relativeToViewCheckBox, relativeToViewGridBagConstraint);

    GridBagConstraints exportGridBagConstraint = new GridBagConstraints();
    exportGridBagConstraint.gridx = 4;
    exportGridBagConstraint.anchor = GridBagConstraints.EAST;
    exportGridBagConstraint.insets = new Insets(0, 0, 5, 10);
    exportGridBagConstraint.gridy = 1;
    mainPanel.add(getExportCheckBox(), exportGridBagConstraint);
    mainPanel.add(statsButton, statsButtonConstraints);

    actionsComboBox.setMinimumSize(new Dimension(350, 24));
    actionsComboBox.setPreferredSize(new Dimension(350, 24));
    actionsComboBox.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            actionsComboBox_actionPerformed(e);
          }
        });

    mainPanel.add(
        sqlLabel,
        new GridBagConstraints(
            0,
            1,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 10, 5, 5),
            0,
            0));
    mainPanel.add(
        actionsComboBox,
        new GridBagConstraints(
            2,
            0,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 0, 5, 10),
            0,
            0));
    mainPanel.add(
        sqlTextPane,
        new GridBagConstraints(
            0,
            2,
            5,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 10, 5, 10),
            0,
            0));
    mainPanel.add(
        cancelButton,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        okButton,
        new GridBagConstraints(
            1,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        linkRadioButton,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        nodeRadioButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(10, 10, 10, 10),
            0,
            0));

    // Get the last saved query strings
    nodesFlowQueryString =
        nodusProject.getLocalProperty(NodusC.PROP_NODES_FLOW_QUERY + currentScenario, "");
    linksFlowQueryString =
        nodusProject.getLocalProperty(NodusC.PROP_LINKS_FLOW_QUERY + currentScenario, "");
    dynamicFlowQueryString =
        nodusProject.getLocalProperty(NodusC.PROP_DYNAMIC_FLOW_QUERY + currentScenario, "");
    linksVehiclesQueryString =
        nodusProject.getLocalProperty(NodusC.PROP_LINKS_VEHICLES_QUERY + currentScenario, "");
    pathQueryString = nodusProject.getLocalProperty(NodusC.PROP_PATH_QUERY + currentScenario, "");

    setActions();

    // sqlTextArea.setLineWrap(true);
    defaultQueryButtonConstraints.gridx = 3;
    defaultQueryButtonConstraints.gridy = 3;
    defaultQueryButtonConstraints.insets = new Insets(5, 5, 0, 5);
    defaultQueryButtonConstraints.anchor = GridBagConstraints.WEST;
    resetButtonConstraints.anchor = java.awt.GridBagConstraints.WEST;
    mainPanel.add(resetButton, resetButtonConstraints);
    mainPanel.add(getDefaultQueryButton(), defaultQueryButtonConstraints);

    pack();
  }

  /**
   * Updates the possible types of results for links.
   *
   * @param e ActionEvent
   */
  private void linkRadioButton_actionPerformed(ActionEvent e) {
    setActions();
  }

  /**
   * Updates the possible types of results for nodes.
   *
   * @param e ActionEvent
   */
  private void nodeRadioButton_actionPerformed(ActionEvent e) {
    setActions();
  }

  /**
   * Retrieve the selected options and launches the operation to perform.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {
    boolean success = true;
    int index = actionsComboBox.getSelectedIndex();
    boolean export = exportCheckBox.isSelected();
    boolean relativeToView = relativeToViewCheckBox.isSelected();

    setVisible(false);
    if (index > 0) {
      index--;

      if (nodeRadioButton.isSelected()) {
        NodeResults nr = new NodeResults(nodusMapPanel, relativeToView, export);

        switch (index) {
          case 0: // Display flows
            resetLayers();
            nodesFlowQueryString = sqlTextPane.getText();
            nodusProject.setLocalProperty(
                NodusC.PROP_NODES_FLOW_QUERY + currentScenario, nodesFlowQueryString);
            success = nr.readOD(sqlTextPane.getText());
            break;

          default:
            break;
        }
      } else {
        LinkResults lr = new LinkResults(nodusMapPanel, relativeToView, export);

        switch (index) {
          case 0: // Display assigned quantities
            resetLayers();
            linksFlowQueryString = sqlTextPane.getText();
            nodusProject.setLocalProperty(
                NodusC.PROP_LINKS_FLOW_QUERY + currentScenario, linksFlowQueryString);
            success = lr.displayFlows(sqlTextPane.getText());

            break;

          case 1: // Display assigned vehicles
            resetLayers();
            linksVehiclesQueryString = sqlTextPane.getText();
            nodusProject.setLocalProperty(
                NodusC.PROP_LINKS_VEHICLES_QUERY + currentScenario, linksVehiclesQueryString);
            success = lr.displayFlows(sqlTextPane.getText());

            break;

          case 2: // Display path
            resetLayers();
            pathQueryString = sqlTextPane.getText();
            nodusProject.setLocalProperty(
                NodusC.PROP_PATH_QUERY + currentScenario, pathQueryString);
            success = lr.displayPath(sqlTextPane.getText());

            break;

          case 3: // Display time dependent assigned quantities
            resetLayers();
            dynamicFlowQueryString = sqlTextPane.getText();
            nodusProject.setLocalProperty(
                NodusC.PROP_DYNAMIC_FLOW_QUERY + currentScenario, dynamicFlowQueryString);
            success = lr.displayTimeDependentFlows(sqlTextPane.getText());
            resetLayers();
            break;

          default:
            break;
        }
      }
    }

    if (!success) {
      setVisible(true);
    }
  }

  /**
   * Reset the "display results" flag of all the layers and repaint the map.
   *
   * @param e ActionEvent
   */
  private void resetButton_actionPerformed(ActionEvent e) {
    setVisible(false);
    resetLayers();
  }

  /** Resets the "display results" state of the layers. */
  public void resetLayers() {

    NodusEsriLayer[] layers;

    if (nodeRadioButton.isSelected()) {
      layers = nodusProject.getNodeLayers();
    } else {
      layers = nodusProject.getLinkLayers();
    }

    for (NodusEsriLayer element : layers) {

      // Reset the user defined attribute of each graphic
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();
      while (it.hasNext()) {
        OMGraphic omg = it.next();
        RealNetworkObject rn = (RealNetworkObject) omg.getAttribute(0);
        if (rn != null) {
          rn.setResult(0.0);
        }
      }

      element.setDisplayResults(false);
      element.getLocationHandler().setDisplayResults(false);
      element.getLocationHandler().reloadData();
      element.applyWhereFilter(element.getWhereStmt());
      element.attachStyles();
      element.doPrepare();
    }

    // Is a label layer present?
    LabelLayer labelLayer = null;
    Layer[] l = nodusMapPanel.getLayerHandler().getLayers();
    for (Layer element : l) {
      if (element.getClass().getName() == "com.bbn.openmap.layer.LabelLayer") {
        labelLayer = (LabelLayer) element;
        break;
      }
    }
    if (labelLayer != null) {
      labelLayer.setLabelText("");
      labelLayer.doPrepare();
    }
  }

  /**
   * Fills the combo with the possible results to display for a given type of graphics (nodes or
   * links).
   */
  private void setActions() {
    actionsComboBox.removeAllItems();
    sqlTextPane.setText("");

    String[] actions = nodeActions;

    if (linkRadioButton.isSelected()) {
      actions = linkActions;
    }

    actionsComboBox.addItem(
        i18n.get(ResultsDlg.class, "Choose_type_of_display", "Choose type of display:"));

    for (String element : actions) {
      actionsComboBox.addItem(element);
    }
  }
}
