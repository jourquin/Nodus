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

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import javax.swing.JButton;
import javax.swing.JOptionPane;

/**
 * Extends the original MetaDbfTableModel to limit the allowed DBF types.
 *
 * @author Bart Jourquin
 */
public class NodusMetaDbfTableModel extends MetaDbfTableModel {

  private static final long serialVersionUID = -5011547449055509195L;

  /** Variable that is used to detect a dbf structure change. */
  private int originalColumCount = -1;

  /** Variable that is used to detect a dbf structure change. */
  private byte[] originalDecimalcount;

  /** Variable that is used to detect a dbf structure change. */
  private int[] originalLength;

  /** Variable that is used to detect a dbf structure change. */
  private String[] originalName;

  /** Variable that is used to detect a dbf structure change. */
  private byte[] originalType;

  private NodusEsriLayer layer;

  private String fileName;

  /**
   * Constructor that allows the edition of the structure of the DbfTable of a NodusEsriLayer.
   *
   * @param layer The NodusEsriLayer the DbfTableModel belongs to.
   */
  public NodusMetaDbfTableModel(NodusEsriLayer layer) {
    super(layer.getModel());
    this.layer = layer;
    addTableModelListener(layer.getModel());
    getOriginalTableStructure();
  }

  /**
   * As the table structure can be modified, one need its original structure in order to test any
   * change.
   */
  private void getOriginalTableStructure() {
    originalColumCount = layer.getModel().getColumnCount();
    originalLength = new int[originalColumCount];
    originalDecimalcount = new byte[originalColumCount];
    originalType = new byte[originalColumCount];
    originalName = new String[originalColumCount];

    for (int i = 0; i < originalColumCount; i++) {
      originalDecimalcount[i] = layer.getModel().getDecimalCount(i);
      originalLength[i] = layer.getModel().getLength(i);
      originalName[i] = layer.getModel().getColumnName(i);
      originalType[i] = layer.getModel().getType(i);
    }
  }

  /**
   * Test if the current table structure is different from the original one.
   *
   * @return boolean
   */
  private boolean isTableStructureChanged() {
    // If the "modify structure" GUI was never called
    if (originalColumCount == -1) {
      return false;
    }

    for (int i = 0; i < layer.getModel().getColumnCount(); i++) {
      if (originalDecimalcount[i] != layer.getModel().getDecimalCount(i)) {
        return true;
      }

      if (originalLength[i] != layer.getModel().getLength(i)) {
        return true;
      }

      if (!originalName[i].equals(layer.getModel().getColumnName(i))) {
        return true;
      }

      if (originalType[i] != layer.getModel().getType(i)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Sets an object at a certain location. The type is translated from integer values to names for
   * easier use.
   */
  @Override
  public void setValueAt(Object object, int row, int column) {

    if (column == META_TYPE_COLUMN_NUMBER) {
      if (DBF_CHARACTER.equals(object) || DBF_TYPE_CHARACTER.equals(object)) {
        object = DBF_TYPE_CHARACTER;
      } else if (DBF_DATE.equals(object) || DBF_TYPE_DATE.equals(object)) {
        object = DBF_TYPE_DATE;
      } else if (DBF_NUMERIC.equals(object) || DBF_TYPE_NUMERIC.equals(object)) {
        object = DBF_TYPE_NUMERIC;
      } else {
        JOptionPane.showMessageDialog(
            null,
            i18n.get(
                NodusMetaDbfTableModel.class,
                "Unsuported_type",
                "Only 'character', 'numeric' and 'date' types are supported"),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    super.setValueAt(object, row, column);

    String s = (String) super.getValueAt(row, META_TYPE_COLUMN_NUMBER);

    if (s.equalsIgnoreCase("date")) {
      super.setValueAt(new Integer(8), row, 2);
      super.setValueAt(new Integer(0), row, 3);
    }
  }

  
  @Override
  public void showGUI(String filename) {
    super.showGUI(filename);

    this.fileName = filename;

    // Find the 'save' button
    Component[] c = controlPanel.getComponents();
    for (int i = 0; i < c.length; i++) {
      if (c[i] instanceof JButton) {
        JButton saveButton = (JButton) c[i];
        if (saveButton
            .getText()
            .equals(i18n.get(MetaDbfTableModel.class, "Save Changes", "Save Changes"))) {
          // Remove current action listener
          ActionListener[] al = saveButton.getActionListeners();
          for (int j = 0; j < al.length; j++) {
            saveButton.removeActionListener(al[j]);
          }

          // Set a new action listener
          saveButton.addActionListener(
              new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                  save();
                }
              });
        }
      }
    }
  }

  @Override
  public void exitWindowClosed() {
    save();
  }

  /**
   * Saves the modified DbfTableModel if needed. Update the SQL DBMS and reload the DBFTableModel.
   */
  private void save() {
    /* if (!isTableStructureChanged()) {
      return;
    }*/

    if (source != null && source.dirty) {
      int check =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  MetaDbfTableModel.class,
                  "Are_you_sure_you_want_to_modify_the_table_format",
                  "Are you sure you want to modify the table format?"),
              i18n.get(MetaDbfTableModel.class, "Confirm_Save", "Confirm Save"),
              JOptionPane.OK_CANCEL_OPTION);

      if (check == JOptionPane.YES_OPTION) {
        // Export the modified DBF and update the DBMS table
        fireTableStructureChanged();
        ExportDBF.exportTable(
            layer.getNodusMapPanel().getNodusProject(),
            layer.getTableName() + NodusC.TYPE_DBF,
            layer.getModel());

        // Reload DbfTableModel
        File file = new File(fileName);
        URI uri = file.toURI();

        try {
          layer.setModel(getDbfTableModel(uri.toURL()));
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }

        ImportDBF.importTable(layer.getNodusMapPanel().getNodusProject(), layer.getTableName());
        layer.doPrepare();
      }
    } else {
      source.cleanupChanges();
    }

    frame.setVisible(false);
  }
}
