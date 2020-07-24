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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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

  /** 'Delete' button that is only enabled for non mandatory fields. */
  private JButton deleteButton = null;

  /** 'Add' button. */
  private JButton addButton = null;

  private DbfTableModel source;

  private boolean addingNewRecord = false;
  private int dirtyRecordIndex = -1;

  // String fileName;

  /**
   * Constructor that allows the edition of the structure of the DbfTable of a NodusEsriLayer.
   *
   * @param layer The NodusEsriLayer the DbfTableModel belongs to.
   * @param source The DbfTableModel to modify.
   */
  public NodusMetaDbfTableModel(NodusEsriLayer layer, DbfTableModel source) {
    super(source);
    this.layer = layer;
    this.source = source;

    getOriginalTableStructure();
  }

  /** Ensure that the proposed field name doesn't already exist. */
  @Override
  public void addBlankRecord() {

    super.addBlankRecord();
    saveButton.setEnabled(true);
    addButton.setEnabled(false);
    deleteButton.setEnabled(false);

    int newRowIndex = table.getRowCount() - 1;

    // The field name must be unique. Add a numeric suffix if needed
    String fieldName = (String) getValueAt(newRowIndex, 0);
    String newName = fieldName;
    boolean found = true;
    int suffix = 1;
    while (found) {
      found = false;
      for (int i = 0; i < newRowIndex; i++) {
        String s = (String) getValueAt(i, 0);
        if (s.equalsIgnoreCase(newName)) {
          newName = fieldName + (suffix++);
          found = true;
        }
      }
    }

    // Set the field name and invite the user to edit it
    setValueAt(newName, newRowIndex, 0);
    table.scrollRectToVisible(table.getCellRect(newRowIndex, 0, true));
    table.editCellAt(newRowIndex, 0);
    table.getEditorComponent().requestFocus();

    addingNewRecord = true;
  }

  private void deleteRecord() {
    int index = getTable().getSelectedRow();

    if (index != -1) {

      // Ask to make sure...
      int check =
          JOptionPane.showConfirmDialog(
              null,
              (i18n.get(
                      DbfTableModel.class,
                      "Are_you_sure_you_want_to_delete",
                      "Are you sure you want to delete")
                  + " "
                  + i18n.get(DbfTableModel.class, "this_row", "this row?")),
              i18n.get(DbfTableModel.class, "Confirm_Delete", "Confirm Delete"),
              JOptionPane.OK_CANCEL_OPTION);

      if (check == JOptionPane.YES_OPTION) {
        remove(index);
        saveIfNeeded(false, false);
      }
    }
  }

  /** Overridden to ask the user if he really wants to cancel his changes. */
  @Override
  public void exitWindowClosed() {
    saveIfNeeded(true, true);
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
    if (isMandatoryField(rowIndex)) {
      return false;
    }

    if (addingNewRecord && table.getSelectedRow() != table.getRowCount() - 1) {
      return false;
    }

    if (dirtyRecordIndex != -1 && table.getSelectedRow() != dirtyRecordIndex) {
      return false;
    }

    if (columnIndex <= 1 && rowIndex < originalColumnNumber) {
      return false;
    } else {
      return writable;
    }
  }

  /**
   * Test if the row contains a mandatory field.
   *
   * @param rowIndex Row index in the table.
   * @return True if the row contains a mandatory field.
   */
  private boolean isMandatoryField(int rowIndex) {
    int nbMandatoryFields;
    if (layer.getType() == ShapeConstants.SHAPE_TYPE_POLYLINE) {
      nbMandatoryFields = NodusC.LINKS_MANDATORY_NAMES.length;
    } else {
      nbMandatoryFields = NodusC.NODES_MANDATORY_NAMES.length;
    }

    if (rowIndex < nbMandatoryFields) {
      return true;
    }
    return false;
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
  private void saveIfNeeded(boolean askForSave, boolean askForCancel) {

    if (isTableStructureChanged()) {

      // If called from 'cancel' button or on window closing...
      if (askForCancel) {
        int check =
            JOptionPane.showConfirmDialog(
                null,
                i18n.get(
                    NodusMetaDbfTableModel.class,
                    "Are_you_sure",
                    "Are you sure you want to cancel changes?"),
                i18n.get(MetaDbfTableModel.class, "Cancel_changes", "Cancel changes"),
                JOptionPane.YES_NO_OPTION);

        if (check == JOptionPane.YES_OPTION) {
          // Cancel changes
          source.cleanupChanges();
          frame.setVisible(false);
          return;
        } else {
          // Back to the GUI
          return;
        }
      }

      if (askForSave) {
        int check =
            JOptionPane.showConfirmDialog(
                null,
                i18n.get(
                    MetaDbfTableModel.class,
                    "Do_you_want_to_save_your_changes",
                    "Do you want to save your changes?"),
                NodusC.APPNAME,
                JOptionPane.YES_NO_OPTION);

        if (check == JOptionPane.YES_OPTION) {
          fireTableStructureChanged();
        } else {
          source.cleanupChanges();
          frame.setVisible(false);
          return;
        }
      } else {
        fireTableStructureChanged();
      }

      // Export the modified DBF
      ExportDBF.exportTable(
          layer.getNodusMapPanel().getNodusProject(),
          layer.getTableName() + NodusC.TYPE_DBF,
          source);

      // Update the SQL database
      ImportDBF.importTable(layer.getNodusMapPanel().getNodusProject(), layer.getTableName());

      // Update the DbfTableModel
      layer.setModel(source);

      // Repaint the layer
      layer.doPrepare();
      layer.getLocationHandler().reloadData();
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
      super.setValueAt(Integer.valueOf(8), row, 2);
      super.setValueAt(Integer.valueOf(0), row, 3);
    }

    if (isTableStructureChanged()) {
      saveButton.setEnabled(true);
      addButton.setEnabled(false);
      dirtyRecordIndex = table.getSelectedRow();
    } else {
      saveButton.setEnabled(false);
      addButton.setEnabled(true);
      dirtyRecordIndex = -1;
    }
  }

  @Override
  public void showGUI(String filename) {

    if (frame == null) {
      frame =
          new JFrame(
              i18n.get(
                  MetaDbfTableModel.class,
                  "Editing_Attribute_File_Structure",
                  "Editing Attribute File Structure"));

      frame
          .getContentPane()
          .add(getGUI(filename, MODIFY_ROW_MASK | DONE_MASK), BorderLayout.CENTER);

      saveButton = new JButton(i18n.get(MetaDbfTableModel.class, "Save Changes", "Save Changes"));
      saveButton.setEnabled(false);
      saveButton.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
              saveIfNeeded(true, false);
            }
          });

      controlPanel.add(saveButton);
      frame.validate();

      frame.setSize(500, 300);
      frame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
              exitWindowClosed();
            }
          });
    }

    // Only one row must be selected at once
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // Closing will be managed manually, depending on the what the user wants
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    // Find the "add" button
    Component[] c = controlPanel.getComponents();
    for (Component element : c) {
      if (element instanceof JButton) {
        JButton button = (JButton) element;
        if (button.getText().equals(i18n.get(DbfTableModel.class, "Add", "Add"))) {
          addButton = button;
          break;
        }
      }
    }

    // Find the 'cancel' button and replace its listener
    for (Component element : c) {
      if (element instanceof JButton) {
        JButton button = (JButton) element;
        if (button.getText().equals(i18n.get(DbfTableModel.class, "Done", "Done"))) {
          // Change text
          button.setText(i18n.get(DbfTableModel.class, "Cancel", "Cancel"));

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
                  saveIfNeeded(true, true);
                }
              });
          break;
        }
      }
    }

    // Find the 'delete' button and replace its listener
    for (Component element : c) {
      if (element instanceof JButton) {
        JButton button = (JButton) element;
        if (button.getText().equals(i18n.get(DbfTableModel.class, "Delete", "Delete"))) {
          deleteButton = button;
          deleteButton.setEnabled(false);

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
                  deleteRecord();
                }
              });

          break;
        }
      }
    }

    ListSelectionModel selectionModel = table.getSelectionModel();

    selectionModel.addListSelectionListener(
        new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {

            int rowIndex = table.getSelectedRow();
            if (isMandatoryField(rowIndex)) {
              deleteButton.setEnabled(false);
            } else {
              if (!addingNewRecord) {
                deleteButton.setEnabled(true);
              }
            }

            if (isTableStructureChanged()) {
              saveButton.setEnabled(true);
            } else {
              saveButton.setEnabled(false);
            }

            // Stay on dirty record
            if (dirtyRecordIndex != -1) {
              table.setRowSelectionInterval(dirtyRecordIndex, dirtyRecordIndex);
            }
          }
        });

    frame.setLocationRelativeTo(layer.getNodusMapPanel());
    frame.setVisible(true);
  }
}
