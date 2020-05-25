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

package com.bbn.openmap.layer.shape.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.layer.shape.jung.JungVirtualLink;
import com.bbn.openmap.layer.shape.jung.JungVirtualNode;
import com.bbn.openmap.layer.shape.jung.gui.VirtualNetworkGraphViewerDlg;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.GridSwing;
import edu.uclouvain.core.nodus.swing.TableSorter;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Dialog box that displays all the virtual links for a given real node or real link.
 *
 * @author Bart Jourquin
 */
public class VirtualNetworkViewerDlg extends EscapeDialog implements ShapeConstants {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 3971382364950521487L;

  private JButton closeButton = new JButton();

  private JButton drawButton = new JButton();

  private boolean hasServices;

  private boolean hasTime;

  private boolean isNode;

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private NodusEsriLayer nodusEsriLayer;

  private NodusProject nodusProject;

  private int objectId;

  private JButton queryButton = new JButton();

  private GridSwing results = new GridSwing();

  private JTable resultsTable = new JTable(new TableSorter(results));

  private JScrollPane resultsTableScrollPane = new JScrollPane();

  private JLabel sqlLabel = new JLabel();

  private RSyntaxTextArea sqlTextField = new RSyntaxTextArea();

  private String tableName;

  /**
   * Initializes the dialog box, that will draw the virtual links generated from the node or link
   * object of a given layer and which id is passed as parameter.
   *
   * @param nodusEsriLayer The Layer the real node or link belongs to
   * @param id The ID of the node or link
   */
  public VirtualNetworkViewerDlg(NodusEsriLayer nodusEsriLayer, int id) {
    super((JDialog) null, "", true);

    objectId = id;
    this.nodusEsriLayer = nodusEsriLayer;
    nodusProject = nodusEsriLayer.getNodusMapPanel().getNodusProject();

    initialize();
    getRootPane().setDefaultButton(closeButton);
    closeButton.requestFocus();

    setLocationRelativeTo(null);
  }

  /**
   * Closes dialog-box.
   *
   * @param e ActionEvent
   * @exclude
   */
  public void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * Opens a dialog box with a graphic representation of the virtual network at that point.
   *
   * @param e ActionEvent
   * @exclude
   */
  public void drawButton_actionPerformed(ActionEvent e) {

    try {
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();

      // connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlTextField.getText());

      // Find index of the QTY field
      ResultSetMetaData m = rs.getMetaData();
      int col = m.getColumnCount();
      String[] h = new String[col];
      int nbFieldsOk = 0;
      int nbNeededFields = 11;

      for (int i = 1; i <= col; i++) {
        String n = m.getColumnName(i);
        h[i - 1] = n;

        switch (i) {
          case 1:
            if (n.equalsIgnoreCase(NodusC.DBF_NODE1)) {
              nbFieldsOk++;
            }
            break;

          case 2:
            if (n.equalsIgnoreCase(NodusC.DBF_LINK1)) {
              nbFieldsOk++;
            }
            break;

          case 3:
            if (n.equalsIgnoreCase(NodusC.DBF_MODE1)) {
              nbFieldsOk++;
            }
            break;

          case 4:
            if (n.equalsIgnoreCase(NodusC.DBF_MEANS1)) {
              nbFieldsOk++;
            }
            break;

          case 5:
            if (n.equalsIgnoreCase(NodusC.DBF_SERVICE1)) {
              nbFieldsOk++;
            }
            break;

          case 6:
            if (n.equalsIgnoreCase(NodusC.DBF_NODE2)) {
              nbFieldsOk++;
            }
            break;

          case 7:
            if (n.equalsIgnoreCase(NodusC.DBF_LINK2)) {
              nbFieldsOk++;
            }
            break;

          case 8:
            if (n.equalsIgnoreCase(NodusC.DBF_MODE2)) {
              nbFieldsOk++;
            }
            break;

          case 9:
            if (n.equalsIgnoreCase(NodusC.DBF_MEANS2)) {
              nbFieldsOk++;
            }
            break;

          case 10:
            if (n.equalsIgnoreCase(NodusC.DBF_SERVICE2)) {
              nbFieldsOk++;
            }
            break;

          case 11:
            if (n.equalsIgnoreCase(NodusC.DBF_TIME)) {
              nbFieldsOk++;
            }
            break;

          default:
            break;
        }
      }

      if (nbFieldsOk != nbNeededFields) {
        System.err.println("Invalid query for graph drawing");
        return;
      }

      HashMap<String, JungVirtualNode> nodesHashMap = new HashMap<>();
      HashMap<String, JungVirtualLink> linksHashMap = new HashMap<>();

      while (rs.next()) {
        final int node1 = rs.getInt(1);
        final int link1 = rs.getInt(2);
        final byte mode1 = rs.getByte(3);
        final byte means1 = rs.getByte(4);

        final int node2 = rs.getInt(6);
        final int link2 = rs.getInt(7);
        final byte mode2 = rs.getByte(8);
        final byte means2 = rs.getByte(9);

        int line1 = rs.getInt(5);
        int line2 = rs.getInt(10);
        if (!hasServices) {
          line1 = line2 = 0;
        }

        int time = rs.getInt(11);
        if (!hasTime) {
          time = 0;
        }

        int idx = 12;

        final double quantity = rs.getDouble(idx++);
        final double unitCost = rs.getDouble(idx++);
        final double vehicles = rs.getDouble(idx++);

        JungVirtualNode n1 = new JungVirtualNode(node1, link1, mode1, means1, line1);
        JungVirtualNode n2 = new JungVirtualNode(node2, link2, mode2, means2, line2);

        JungVirtualNode n = nodesHashMap.get(n1.toString());
        if (n == null) {
          nodesHashMap.put(n1.toString(), n1);

        } else {
          n1 = n;
        }

        n = nodesHashMap.get(n2.toString());
        if (n == null) {
          nodesHashMap.put(n2.toString(), n2);
        } else {
          n2 = n;
        }

        JungVirtualLink jvl = new JungVirtualLink(n1, n2, quantity, unitCost, vehicles, time);

        linksHashMap.put(jvl.toString(), jvl);
      }
      rs.close();
      stmt.close();

      // Now display graph
      List<JungVirtualNode> nodeList = new ArrayList<JungVirtualNode>(nodesHashMap.values());
      List<JungVirtualLink> linkList = new ArrayList<JungVirtualLink>(linksHashMap.values());
      VirtualNetworkGraphViewerDlg dialog =
          new VirtualNetworkGraphViewerDlg(this, nodeList, linkList, isNode);
      dialog.setVisible(true);

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          null,
          ex.getMessage(),
          i18n.get(VirtualNetworkViewerDlg.class, "SQL error", "SQL error"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void initialize() {
    if (nodusProject == null) {
      return;
    }

    GridBagConstraints queryButtonConstraints =
        new GridBagConstraints(
            1,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    queryButtonConstraints.gridy = 5;

    GridBagConstraints sqlTextFieldConstraints =
        new GridBagConstraints(
            0,
            2,
            4,
            1,
            0.5,
            0.1,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            29);
    sqlTextFieldConstraints.gridwidth = 6;

    GridBagConstraints resultsTableScrollPaneConstraints =
        new GridBagConstraints(
            0,
            3,
            4,
            1,
            0.5,
            2.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0);
    resultsTableScrollPaneConstraints.gridwidth = 6;

    GridBagConstraints closeButtonConstraints =
        new GridBagConstraints(
            2,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    closeButtonConstraints.gridx = 5;
    closeButtonConstraints.gridy = 5;

    GridBagConstraints drawButtonConstraints =
        new GridBagConstraints(
            0,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    drawButtonConstraints.gridx = 3;
    drawButtonConstraints.anchor = GridBagConstraints.EAST;
    drawButtonConstraints.gridy = 5;

    mainPanel.setLayout(mainPanelGridBagLayout);

    drawButton.setText(i18n.get(VirtualNetworkViewerDlg.class, "Draw", "Draw"));
    drawButton.addActionListener(new VirtualNetworkViewerDlgDrawButtonActionAdapter(this));

    closeButton.setText(i18n.get(VirtualNetworkViewerDlg.class, "Close", "Close"));
    closeButton.addActionListener(new VirtualNetworkViewerDlgCloseButtonActionAdapter(this));

    sqlLabel.setText(i18n.get(VirtualNetworkViewerDlg.class, "Query", "Query") + ":");

    queryButton.setText(i18n.get(VirtualNetworkViewerDlg.class, "Query", "Query"));
    queryButton.addActionListener(new VirtualNetworkViewerDlgQueryButtonActionAdapter(this));

    sqlTextField.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    sqlTextField.setHighlightCurrentLine(false);
    sqlTextField.setLineWrap(true);

    /*
     * Example query for nodes.
     *
     * <p>select link1, link2, mode1, means1, service1, mode2, means2, service2, time, qty, ucost,
     * veh from vnet0 where abs(node1) = 1000 and abs(node2) = 1000 Example query for links: select
     * node1, node2, mode1, means1, cost, qty from vnet0 where link1 = 1000 and link2 = 1000
     */
    int scenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);
    tableName = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenario;

    hasTime = JDBCUtils.hasSeveralValues(tableName, NodusC.DBF_TIME);
    hasServices = JDBCUtils.hasSeveralValues(tableName, NodusC.DBF_SERVICE1);

    String sqlStmt =
        " SELECT "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SERVICE1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SERVICE2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_TIME)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_UNITCOST)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_VEHICLES)
            + " FROM "
            + tableName
            + " WHERE ";

    if (nodusEsriLayer.getType() == SHAPE_TYPE_POINT) {
      isNode = true;
      sqlStmt +=
          "ABS("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE1)
              + ") = "
              + objectId
              + " AND "
              + "ABS("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE2)
              + ") = "
              + objectId;
      setTitle(
          MessageFormat.format(
              i18n.get(VirtualNetworkViewerDlg.class, "vn_at_node", "Virtual network at node {0}"),
              objectId));

    } else {
      isNode = false;
      sqlStmt +=
          JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK1)
              + " = "
              + objectId
              + " AND "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK2)
              + " = "
              + objectId;
      setTitle(
          MessageFormat.format(
              i18n.get(VirtualNetworkViewerDlg.class, "vn_at_link", "Virtual network at link {0}"),
              objectId));
    }

    sqlTextField.setText(sqlStmt);

    mainPanel.setPreferredSize(new Dimension(550, 350));
    setContentPane(mainPanel);
    resultsTableScrollPane.getViewport().add(resultsTable);
    mainPanel.add(
        sqlLabel,
        new GridBagConstraints(
            0,
            0,
            4,
            1,
            0.05,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(queryButton, queryButtonConstraints);
    mainPanel.add(drawButton, drawButtonConstraints);
    mainPanel.add(closeButton, closeButtonConstraints);
    mainPanel.add(sqlTextField, sqlTextFieldConstraints);
    mainPanel.add(resultsTableScrollPane, resultsTableScrollPaneConstraints);

    setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus7.png")));

    pack();
  }

  /**
   * Fill the table with the result of the query string.
   *
   * @param e ActionEvent
   * @exclude
   */
  public void queryButton_actionPerformed(ActionEvent e) {
    try {
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();
      String sqlStmt = sqlTextField.getText();

      // connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve result of query
      results.clear();

      ResultSetMetaData m = rs.getMetaData();
      int col = m.getColumnCount();
      String[] h = new String[col];

      h[0] = JDBCUtils.getCompliantIdentifier(NodusC.DBF_NODE1);
      h[1] = JDBCUtils.getCompliantIdentifier(NodusC.DBF_NODE2);
      for (int i = 1; i <= col; i++) {
        h[i - 1] = m.getColumnName(i);
      }

      results.setHead(h);

      ((TableSorter) resultsTable.getModel()).setTableHeader(resultsTable.getTableHeader());

      while (rs.next()) {
        for (int i = 1; i <= col; i++) {
          h[i - 1] = rs.getString(i);

          if (rs.wasNull()) {
            h[i - 1] = "(null)";
          }
        }

        results.addRow(h);
      }

      results.fireTableChanged(null);

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          null,
          ex.getMessage(),
          i18n.get(VirtualNetworkViewerDlg.class, "SQL error", "SQL error"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  class VirtualNetworkViewerDlgCloseButtonActionAdapter implements ActionListener {

    private VirtualNetworkViewerDlg adaptee;

    VirtualNetworkViewerDlgCloseButtonActionAdapter(VirtualNetworkViewerDlg adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      adaptee.closeButton_actionPerformed(e);
    }
  }

  class VirtualNetworkViewerDlgDrawButtonActionAdapter implements ActionListener {

    private VirtualNetworkViewerDlg adaptee;

    VirtualNetworkViewerDlgDrawButtonActionAdapter(VirtualNetworkViewerDlg adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      adaptee.drawButton_actionPerformed(e);
    }
  }

  class VirtualNetworkViewerDlgQueryButtonActionAdapter implements ActionListener {

    private VirtualNetworkViewerDlg adaptee;

    VirtualNetworkViewerDlgQueryButtonActionAdapter(VirtualNetworkViewerDlg adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      adaptee.queryButton_actionPerformed(e);
    }
  }
}
