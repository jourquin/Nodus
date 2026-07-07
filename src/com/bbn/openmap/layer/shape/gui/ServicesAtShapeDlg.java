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

package com.bbn.openmap.layer.shape.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.swing.DefaultCheckListModel;
import edu.uclouvain.swing.JCheckList;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Dialog box for editing service stop states at a node. */
public class ServicesAtShapeDlg extends EscapeDialog {

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
  private TreeMap<String, Boolean> listNameForNode = null;

  /** . */
  private NodusMapPanel nodusMapPanel;

  /** . */
  private int objectNum;

  /** . */
  private ServiceHandler serviceHandler;

  /** Staged stop selections for a node, returned to the parent dialog. */
  private TreeMap<String, Boolean> selectedServiceStops = null;

  /**
   * Dialog box that allows editing service stop states at a node.
   *
   * @param parent The parent dialog
   * @param nodusEsriLayer The layer the node to edit belongs to
   * @param objectNum Node number
   * @param serviceStopsForNode Staged stop states to display for the node, or null to load current
   *     stop states
   */
  public ServicesAtShapeDlg(
      JDialog parent,
      NodusEsriLayer nodusEsriLayer,
      int objectNum,
      TreeMap<String, Boolean> serviceStopsForNode) {
    super(nodusEsriLayer.getNodusMapPanel().getMainFrame(), "", true);
    this.nodusMapPanel = nodusEsriLayer.getNodusMapPanel();
    serviceHandler = nodusMapPanel.getNodusProject().getServiceHandler();
    this.objectNum = objectNum;
    if (serviceStopsForNode != null) {
      listNameForNode = new TreeMap<>(serviceStopsForNode);
    }
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
      closeButton.setText(i18n.get(ServicesAtShapeDlg.class, "Close", "Close"));
      closeButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              TreeMap<String, Boolean> listNodes = new TreeMap<>();
              for (int i = 0; i < checkList.getModel().getSize(); ++i) {
                if (checkList.isChecked(i)) {
                  listNodes.put((String) checkList.getModel().getElementAt(i), true);
                } else {
                  listNodes.put((String) checkList.getModel().getElementAt(i), false);
                }
              }
              selectedServiceStops = listNodes;
              setVisible(false);
            }
          });
    }
    return closeButton;
  }

  /**
   * Returns the staged stop selections made in this dialog.
   *
   * @return Selected service stops, or null if the dialog was closed without applying selections.
   */
  public TreeMap<String, Boolean> getSelectedServiceStops() {
    if (selectedServiceStops == null) {
      return null;
    }
    return new TreeMap<>(selectedServiceStops);
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
      scrollPane.setViewportView(getJCheckList());
    }
    return scrollPane;
  }

  /**
   * This method initializes this.
   *
   * @return void
   */
  private void initialize() {
    this.setSize(300, 200);
    this.setTitle(
        MessageFormat.format(
            i18n.get(ServicesAtShapeDlg.class, "Services_at_node", "Services at node {0}"),
            Integer.toString(objectNum)));
    if (listNameForNode == null) {
      listNameForNode = serviceHandler.getServiceNamesForNode(objectNum);
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
