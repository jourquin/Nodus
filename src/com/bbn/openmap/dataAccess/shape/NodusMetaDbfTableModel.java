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
import javax.swing.WindowConstants;

/**
 * Extends the original MetaDbfTableModel to better control the changes (do not allow two fields
 * with the same name, changing the type of an existing field or give wrong length and decimal
 * places for 'date' fields. Also controls the 'save' button that is enabled only when the table
 * structure is changed.
 *
 * @author Bart Jourquin
 */
public class NodusMetaDbfTableModel extends MetaDbfTableModel {

  private static final long serialVersionUID = -5011547449055509195L;

  private String fileName;

  /** The NodusEsriLayer this DBF file belongs to. */
  private NodusEsriLayer layer;

  /** Variable that is used to detect a dbf structure change. */
  private Object[] originalDecimalcount;

  /** Variable that is used to detect a dbf structure change. */
  private Object[] originalLength;

  /** Variable that is used to detect a dbf structure change. */
  private Object[] originalName;

  /** Variable that is used to detect a dbf structure change. */
  private Object[] originalType;

  /** 'Save' button which state changes if the table structure is modified. */
  private JButton saveButton = null;

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

  @Override
  public void exitWindowClosed() {

    saveIfNeeded(true);
  }

  /**
   * As the table structure can be modified, one need its original structure in order to test any
   * change.
   */
  private void getOriginalTableStructure() {

    originalName = new Object[getRowCount()];
    originalType = new Object[getRowCount()];
    originalLength = new Object[getRowCount()];
    originalDecimalcount = new Object[getRowCount()];

    for (int i = 0; i < getRowCount(); i++) {
      originalName[i] = getValueAt(i, 0);
      originalType[i] = getValueAt(i, 1);
      originalLength[i] = getValueAt(i, 2);
      originalDecimalcount[i] = getValueAt(i, 3);
    }
  }

  /** Field name and type are not editable. */
  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (columnIndex <= 1 && rowIndex < originalColumnNumber) {
      return false;
    } else {
      return writable;
    }
  }

  /**
   * Test if the current table structure is different from the original one.
   *
   * @return boolean
   */
  private boolean isTableStructureChanged() {

    // If field added or removed
    if (getRowCount() != originalName.length) {
      return true;
    }

    for (int i = 0; i < getRowCount(); i++) {
      if (!originalName[i].equals(getValueAt(i, 0))) {
        return true;
      }
      if (!originalType[i].equals(getValueAt(i, 1))) {
        return true;
      }
      if (!originalLength[i].equals(getValueAt(i, 2))) {
        return true;
      }
      if (!originalDecimalcount[i].equals(getValueAt(i, 3))) {
        return true;
      }
    }

    return false;
  }

  /**
   * Saves the modified DbfTableModel if needed. Updates the SQL DBMS and reload the DBFTableModel.
   */
  private void saveIfNeeded(boolean askUser) {

    if (isTableStructureChanged()) {

      // If called from 'cancel' button or on window closing...
      if (askUser) {
        int check =
            JOptionPane.showConfirmDialog(
                null,
                i18n.get(
                    NodusMetaDbfTableModel.class,
                    "Are_you_sure",
                    "Are you sure you want to cancel changes?"),
                i18n.get(MetaDbfTableModel.class, "Cancel_changes", "Cancel changes"),
                JOptionPane.OK_CANCEL_OPTION);

        if (check == JOptionPane.YES_OPTION) {
          // Cancel changes (do nothing)
          frame.setVisible(false);
          return;
        } else {
          // Back to the GUI
          return;
        }
      }

      // Export the modified DBF
      fireTableStructureChanged();
      ExportDBF.exportTable(
          layer.getNodusMapPanel().getNodusProject(),
          layer.getTableName() + NodusC.TYPE_DBF,
          layer.getModel());

      // Reload the DbfTableModel
      File file = new File(fileName);
      URI uri = file.toURI();

      try {
        layer.setModel(getDbfTableModel(uri.toURL()));
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }

      // Update the SQL database
      ImportDBF.importTable(layer.getNodusMapPanel().getNodusProject(), layer.getTableName());
      layer.doPrepare();
    }

    frame.setVisible(false);
  }

  /**
   * Sets an object at a certain location. The type is translated from integer values to names for
   * easier use.
   */
  @Override
  public void setValueAt(Object object, int row, int column) {

    // A field name must be unique
    if (column == 0) {
      for (int i = 0; i < getRowCount(); i++) {
        if (i != row) { // Don't compare the name to itself
          if (getValueAt(i, 0).equals(object)) {

            JOptionPane.showMessageDialog(
                null,
                i18n.get(
                    NodusMetaDbfTableModel.class, "Duplicated_field_name", "Duplicated field name"),
                NodusC.APPNAME,
                JOptionPane.ERROR_MESSAGE);
            return;
          }
        }
      }
    }

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

    if (isTableStructureChanged()) {
      saveButton.setEnabled(true);
    } else {
      saveButton.setEnabled(false);
    }
  }

  @Override
  public void showGUI(String filename) {
    super.showGUI(filename);
    this.fileName = filename;

    // Closing will be managed manually, depending on the what the user wants
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    // Find the 'save' button
    Component[] c = controlPanel.getComponents();
    for (Component element : c) {
      if (element instanceof JButton) {
        saveButton = (JButton) element;
        if (saveButton
            .getText()
            .equals(i18n.get(MetaDbfTableModel.class, "Save Changes", "Save Changes"))) {
          // Remove current action listener
          ActionListener[] al = saveButton.getActionListeners();
          for (ActionListener element2 : al) {
            saveButton.removeActionListener(element2);
          }

          // Set a new action listener
          saveButton.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                  saveIfNeeded(false);
                }
              });
          saveButton.setEnabled(false);
        }
      }
    }

    // Find the 'cancel' button
    for (Component element : c) {
      if (element instanceof JButton) {
        JButton cancelButton = (JButton) element;
        if (cancelButton.getText().equals(i18n.get(DbfTableModel.class, "Done", "Done"))) {
          // Remove current action listener
          ActionListener[] al = cancelButton.getActionListeners();
          for (ActionListener element2 : al) {
            cancelButton.removeActionListener(element2);
          }

          // Set a new action listener
          cancelButton.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                  saveIfNeeded(true);
                }
              });
          saveButton.setEnabled(false);
        }
      }
    }
  }
}
