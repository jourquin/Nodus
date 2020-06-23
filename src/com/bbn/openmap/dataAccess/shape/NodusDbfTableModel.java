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

package com.bbn.openmap.dataAccess.shape;

import com.bbn.openmap.dataAccess.shape.input.DbfInputStream;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;

public class NodusDbfTableModel extends DbfTableModel {

  private static final long serialVersionUID = -7993112735644200473L;

  protected NodusEsriLayer layer;

  /**
   * Creates DbfTableModel from a NodusEsriLayer.
   *
   * @param layer The NodusEsriLayer this DbfTableModel belongs to.
   */
  public NodusDbfTableModel(NodusEsriLayer layer) {

    super(layer.getModel().getColumnCount());

    this.layer = layer;

    // Point to the existing structure and data
    _names = layer.getModel()._names;
    _types = layer.getModel()._types;
    _lengths = layer.getModel()._lengths;
    _decimalCounts = layer.getModel()._decimalCounts;
    _records = layer.getModel()._records;
  }

  public NodusDbfTableModel(DbfInputStream is) {
    super(is);
  }

  /**
   * Intercept the 'edit table' button to open a NodusMeteDbfTableModel instead of a
   * MetaDbfTableModel.
   */
  @Override
  public void showGUI(String fileName, int actionMask) {

    if (frame == null) {
      frame = new JFrame(fileName);

      filePath.replace(0, filePath.capacity(), fileName);

      frame.getContentPane().add(getGUI(null, actionMask), BorderLayout.CENTER);

      frame.setSize(800, 600);
      frame.addWindowListener(
          new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
              exitWindowClosed();
            }
          });
    }

    // Intercept the 'edit structure' button to call a NodusMetaDbfTableModel
    Component[] c = controlPanel.getComponents();
    for (Component element : c) {
      if (element instanceof JButton) {
        JButton button = (JButton) element;
        if (button
            .getText()
            .equals(i18n.get(DbfTableModel.class, "Edit_Table_Format", "Edit Table Format"))) {

          // Remove current action listener
          ActionListener[] al = button.getActionListeners();
          for (ActionListener element2 : al) {
            button.removeActionListener(element2);
          }

          // Set a new action listener
          button.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                  NodusMetaDbfTableModel mdtm = new NodusMetaDbfTableModel(layer, parent);
                  mdtm.addTableModelListener(parent);
                  mdtm.showGUI(filePath.toString());
                }
              });
        }
      }
    }
    
    // Show it on the left of the app
    frame.setLocationRelativeTo(layer.getNodusMapPanel().getNodusLayersPanel());
    frame.setVisible(true);
  }
}
