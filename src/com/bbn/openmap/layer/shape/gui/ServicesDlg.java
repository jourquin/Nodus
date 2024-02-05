/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.TableSorter;
import edu.uclouvain.swing.DefaultCheckListModel;
import edu.uclouvain.swing.JCheckList;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Dialog box for editing lines and services.
 *
 * @author Bart Jourquin
 */
public class ServicesDlg extends EscapeDialog implements ShapeConstants {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 1L;

  /** . */
  private JCheckList<String> checkList = null;

  /** . */
  private JButton closeButton = null;

  /** . */
  private JPanel contentPane = null;

  /** . */
  private JScrollPane scrollPane = null;

  /** . */
  private JTable servicesTable = null;

  /** . */
  private TreeMap<String, ?> listNameForNode = null;

  /** . */
  private DefaultTableModel modeltable = new DefaultTableModel();

  /** . */
  private NodusMapPanel nodusMapPanel;

  /** . */
  private int objectNum;

  /** . */
  private int objectType;

  /** . */
  private ServiceHandler serviceHandler;

  /** . */
  private LinkedList<?> serviceIdsForLink;

  /** . */
  private LinkedList<?> serviceNamesForLink;

  /** . */
  private TableSorter sorter = null;

  /**
   * Dialog box that allows edition of lines and services.
   *
   * @param parent The parent dialog
   * @param nodusEsriLayer The layer the object to edit belongs to
   * @param objectNum Node or Link number
   */
  public ServicesDlg(JDialog parent, NodusEsriLayer nodusEsriLayer, int objectNum) {
    super(nodusEsriLayer.getNodusMapPanel().getMainFrame(), "", true);
    this.nodusMapPanel = nodusEsriLayer.getNodusMapPanel();
    serviceHandler = nodusMapPanel.getNodusProject().getServiceHandler();
    this.objectType = nodusEsriLayer.getType();
    this.objectNum = objectNum;
    initialize();

    getRootPane().setDefaultButton(getCloseButton());
    setAlwaysOnTop(true);
    setLocationRelativeTo(parent);
  }

  /**
   * This method initializes closeButton.
   *
   * @return javax.swing.JButton
   */
  private JButton getCloseButton() {
    if (closeButton == null) {
      closeButton = new JButton();
      closeButton.setText(i18n.get(ServicesDlg.class, "Close", "Close"));
      closeButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              if (objectType == SHAPE_TYPE_POINT) {
                // Object[] selection = checkList.getSelectedValues();
                // int[] selectionIndice = checkList.getSelectedIndices();
                TreeMap<String, Boolean> listNodes = new TreeMap<>();
                for (int i = 0; i < checkList.getModel().getSize(); ++i) {
                  if (checkList.isChecked(i)) {
                    listNodes.put((String) checkList.getModel().getElementAt(i), true);
                  } else {
                    listNodes.put((String) checkList.getModel().getElementAt(i), false);
                  }
                }
                serviceHandler.setStops(objectNum, listNodes);
              }
              setVisible(false);
            }
          });
    }
    return closeButton;
  }

  /**
   * Returns the check list.
   *
   * @return JCheckList
   */
  private JCheckList<String> getJCheckList() {
    if (checkList == null) {
      DefaultCheckListModel<String> defModel = new DefaultCheckListModel<String>();
      checkList = new JCheckList<String>(defModel);
      checkList.setModel(defModel);

      int indice = 0;
      Iterator<String> it = listNameForNode.keySet().iterator();
      while (it.hasNext()) {
        String name = it.next();
        defModel.addItem(name);
        if ((Boolean) listNameForNode.get(name)) {
          defModel.checkItem(indice);
        }

        ++indice;
      }
    }
    return checkList;
  }

  /**
   * This method initializes contentPane.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getJContentPane() {
    if (contentPane == null) {
      GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
      gridBagConstraints1.gridx = 0;
      gridBagConstraints1.insets = new Insets(6, 6, 5, 5);
      GridBagConstraints gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.fill = GridBagConstraints.BOTH;
      gridBagConstraints.weighty = 1.0;
      gridBagConstraints.gridheight = 1;
      gridBagConstraints.insets = new Insets(5, 5, 0, 5);
      gridBagConstraints.weightx = 1.0;
      contentPane = new JPanel();
      contentPane.setLayout(new GridBagLayout());
      contentPane.add(getJScrollPane(), gridBagConstraints);
      contentPane.add(getCloseButton(), gridBagConstraints1);
    }
    return contentPane;
  }

  /**
   * This method initializes scrollPane.
   *
   * @return javax.swing.JScrollPane
   */
  private JScrollPane getJScrollPane() {
    if (scrollPane == null) {
      scrollPane = new JScrollPane();
      if (objectType == SHAPE_TYPE_POLYLINE) {
        scrollPane.setViewportView(getServicesTable());
      } else {
        scrollPane.setViewportView(getJCheckList());
      }
    }
    return scrollPane;
  }

  /**
   * This method initializes jList.
   *
   * @return javax.swing.JList
   */
  private JTable getServicesTable() {

    if (servicesTable == null) {

      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Index", "Index"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Name", "TransportService"));

      DecimalFormat formatter = new DecimalFormat("0000");

      for (int i = 0; i < serviceIdsForLink.size(); i++) {
        modeltable.addRow(
            new Object[] {formatter.format(serviceIdsForLink.get(i)), serviceNamesForLink.get(i)});
      }

      // Make the component editable
      for (int i = 0; i < modeltable.getRowCount(); ++i) {
        for (int j = 0; j < modeltable.getColumnCount(); ++j) {
          modeltable.isCellEditable(i, j);
        }
      }

      sorter = new TableSorter(modeltable);

      servicesTable =
          new JTable(sorter) {
            private static final long serialVersionUID = 1520266998624805797L;

            @Override
            public boolean isCellEditable(int row, int column) {
              return false;
            }
          };

      sorter.setTableHeader(servicesTable.getTableHeader());

      /* Intercept the value changed even */
      // TODO (services) Display selected line
      servicesTable
          .getSelectionModel()
          .addListSelectionListener(
              new javax.swing.event.ListSelectionListener() {
                @Override
                public void valueChanged(javax.swing.event.ListSelectionEvent e) {

                  // Reentrance of not yet consumed event ?
                  if (!e.getValueIsAdjusting()) {
                    return;
                  }
                  if (getServicesTable().getSelectedRow() == -1) {
                    return;
                  }
                  // Get line ID
                  String serviceName =
                      (String)
                          getServicesTable().getValueAt(getServicesTable().getSelectedRow(), 1);
                  if (serviceName == null) {
                    return;
                  }
                  // Hide current line
                  // serviceHandler.paintService(false);

                  // Load new line
                  serviceHandler.displayService(serviceName);
                }
              });
    }
    return servicesTable;
  }

  /**
   * This method initializes this.
   *
   * @return void
   */
  private void initialize() {
    this.setSize(300, 200);
    switch (objectType) {
      case SHAPE_TYPE_POINT:
        this.setTitle(
            i18n.get(ServicesDlg.class, "Services_at_this_node", "Services at this node"));
        listNameForNode = serviceHandler.getServiceNamesForNode(objectNum);
        break;

      case SHAPE_TYPE_POLYLINE:
        this.setTitle(
            i18n.get(ServicesDlg.class, "Services_at_this_link", "Services at this link"));
        serviceIdsForLink = serviceHandler.getServicesForLink(objectNum);
        serviceNamesForLink = serviceHandler.getServiceNamesForLink(objectNum);
        break;

      default:
        break;
    }
    this.setContentPane(getJContentPane());
  }

  /**
   * Reset the service currently edited (if any) before closing the dialog.
   *
   * @param visible Toggle
   */
  @Override
  public void setVisible(boolean visible) {
    // Reset line if needed
    if (!visible) {
      if (!nodusMapPanel.getNodusProject().getServiceHandler().isGUIVisible()) {
        nodusMapPanel.getNodusProject().getServiceHandler().resetService();
      }
    }
    super.setVisible(visible);
  }
}
