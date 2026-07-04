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

package edu.uclouvain.core.nodus.services.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.TableSorter;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Iterator;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

/**
 * The service editor allows editing the lines and services.
 *
 * @author Bart Jourquin
 */
public class ServicesDlg extends EscapeDialog {

  private static final long serialVersionUID = 1L;

  private static I18n i18n = Environment.getI18n();

  private static final String LIST_CARD = "ListCard";
  private static final String EDITOR_CARD = "EditorCard";
  private static final Dimension EDITOR_CARD_SIZE = new Dimension(400, 220);

  /** . */
  private JButton cancelButton = null;

  /** . */
  CardLayout cards = new CardLayout();

  /** . */
  private JButton closeButton = null;

  /** Saves all pending service changes to the database. */
  private JButton listSaveButton = null;

  /** Toggles the filtered service line view on the map. */
  private JCheckBox lineViewCheckBox = null;

  /** True when the map is filtered to the selected service line. */
  private boolean lineViewActive = false;

  /** . */
  private int[] constPeriod = {1, 12, 52, 365};

  /** . */
  private JButton copyButton = null;

  /** . */
  private JButton deleteButton = null;

  /** . */
  private JTextField descriptionField = new JTextField(50);

  /** . */
  private JButton editButton = null;

  /** . */
  private JPanel editorCard = null;

  /** . */
  private JTextField frequencyField = new JTextField(10);

  /** . */
  private JLabel idxField = new JLabel();

  /** . */
  private JPanel listCard = null;

  /** . */
  private JPanel mainPanel = null;

  /** . */
  private JComboBox<String> meansField = new JComboBox<>();

  /** . */
  private JLabel modeField = new JLabel();

  /** . */
  private JTextField nameField = new JTextField(20);

  /** . */
  private JButton addButton = null;

  /** . */
  private NodusMapPanel nodusMapPanel;

  /** . */
  private String[] period = new String[4];

  /** . */
  private JButton saveButton = null;

  /** . */
  private JScrollPane scrollPane = null;

  /** . */
  private ServiceHandler serviceHandler;

  /** . */
  private DefaultTableModel servicesTableModel = new DefaultTableModel();

  /** . */
  private JTable serviceTable = null;

  /** . */
  private TableSorter sorter;

  /** . */
  Dimension startDimension = null;

  /** . */
  private JComboBox<String> time = new JComboBox<>();

  /** True when the list contains changes not yet written to the database. */
  private boolean hasUnsavedServiceChanges = false;

  /** True when the service details editor contains changes not yet applied to the list. */
  private boolean hasUnsavedEditorChanges = false;

  /** Suppresses dirty tracking while editor fields are filled programmatically. */
  private boolean isLoadingEditorFields = false;

  /** Snapshot of editor fields when the details card was opened or last saved. */
  private String originalEditorValues = "";

  /** Prevents the same Escape key press from leaving the editor and closing the dialog. */
  private boolean suppressDialogCloseAfterLeavingEditor = false;

  /**
   * Creates the service editor dialog.
   *
   * @param serviceHandler The service handler.
   */
  public ServicesDlg(ServiceHandler serviceHandler) {
    super((Frame) null, i18n.get(ServicesDlg.class, "Service_editor", "Services editor"), false);
    this.serviceHandler = serviceHandler;
    nodusMapPanel = serviceHandler.getNodusMapPanel();
    initialize();
  }

  /**
   * This method computes the frequency by period.
   *
   * @param num frequency by year
   * @return array of int frequency and index of the period
   */
  private int[] computeFrequencyUnit(int num) {
    int[] frequency = new int[2];
    for (int i = constPeriod.length - 1; i >= 0; --i) {
      if (num % constPeriod[i] == 0) {
        frequency[0] = num / constPeriod[i];
        frequency[1] = i;
        break;
      }
    }
    return frequency;
  }

  /** Formats the annualized frequency in the same unit shown by the editor fields. */
  private String formatFrequency(int frequency) {
    int[] frequencyUnit = computeFrequencyUnit(frequency);
    return MessageFormat.format(
        i18n.get(ServicesDlg.class, "Frequency_per_period_with_year", "{0} per {1} ({2}/year)"),
        Integer.valueOf(frequencyUnit[0]),
        getPeriodLabel(frequencyUnit[1]),
        Integer.valueOf(frequency));
  }

  /** Returns the localized period label for a frequency period index. */
  private String getPeriodLabel(int periodIndex) {
    switch (periodIndex) {
      case 1:
        return i18n.get(ServicesDlg.class, "month", "month");
      case 2:
        return i18n.get(ServicesDlg.class, "week", "week");
      case 3:
        return i18n.get(ServicesDlg.class, "day", "day");
      case 0:
      default:
        return i18n.get(ServicesDlg.class, "year", "year");
    }
  }

  /** Enable or disable the buttons. */
  private void enableButtons() {
    boolean hasRows = getServiceTable().getRowCount() != 0;
    boolean hasSelection = getServiceTable().getSelectedRow() != -1;
    if (hasRows) {
      getDeleteButton().setEnabled(true);
      getEditButton().setEnabled(true);
      getCopyButton().setEnabled(true);
    } else {
      getDeleteButton().setEnabled(false);
      getEditButton().setEnabled(false);
      getCopyButton().setEnabled(false);
    }
    updateLineViewCheckBox();
    getLineViewCheckBox().setEnabled(hasSelection);
  }

  /** Fill the ServicesTable. */
  private void fillServicesTable(String nameService) {
    DecimalFormat f1 = new DecimalFormat("0000");
    DecimalFormat f2 = new DecimalFormat("00");
    TransportService s = serviceHandler.getService(nameService);
    if (s != null) {
      servicesTableModel.addRow(
          new Object[] {
            f1.format(s.getId()),
            nameService,
            f2.format(s.getMode()),
            f2.format(s.getMeans()),
            formatFrequency(s.getFrequency())
          });
    }
  }

  /** Marks the service list as changed. */
  public void markServicesChanged() {
    hasUnsavedServiceChanges = true;
    if (isEditorCardVisible()) {
      hasUnsavedEditorChanges = true;
    }
    serviceHandler.mustBeSaved();
    updateSaveButtons();
  }

  /** Updates the enabled state of the save buttons. */
  private void updateSaveButtons() {
    if (saveButton != null) {
      saveButton.setEnabled(
          hasUnsavedEditorChanges && hasCurrentServiceLinks() && hasValidEditorFields());
    }
    if (listSaveButton != null) {
      listSaveButton.setEnabled(hasUnsavedServiceChanges);
    }
  }

  /** Returns true if the edited service already contains a line path. */
  private boolean hasCurrentServiceLinks() {
    TransportService service = serviceHandler.getCurrentService();
    return service != null && service.getNbLinks() > 0;
  }

  /** Returns true if the editor-card fields contain values that can be saved. */
  private boolean hasValidEditorFields() {
    String meansValue =
        meansField.getSelectedItem() == null ? "" : meansField.getSelectedItem().toString();
    return !nameField.getText().isBlank()
        && !frequencyField.getText().isBlank()
        && !modeField.getText().isBlank()
        && !meansValue.isBlank()
        && JDBCUtils.getInt(frequencyField.getText()) != Integer.MIN_VALUE
        && meansField.getSelectedItem() != null
        && JDBCUtils.getInt(meansValue) != Integer.MIN_VALUE
        && JDBCUtils.getInt(modeField.getText()) != Integer.MIN_VALUE;
  }

  /** Captures the current details editor values for later dirty-state comparisons. */
  private String getEditorValuesSnapshot() {
    String meansValue =
        meansField.getSelectedItem() == null ? "" : meansField.getSelectedItem().toString();
    return nameField.getText()
        + '\n'
        + frequencyField.getText()
        + '\n'
        + time.getSelectedIndex()
        + '\n'
        + modeField.getText()
        + '\n'
        + meansValue;
  }

  /** Resets dirty tracking for the details editor. */
  private void resetEditorDirtyState() {
    originalEditorValues = getEditorValuesSnapshot();
    hasUnsavedEditorChanges = false;
    updateSaveButtons();
  }

  /** Updates dirty tracking after a user-visible editor field change. */
  private void updateEditorDirtyState() {
    if (!isLoadingEditorFields) {
      hasUnsavedEditorChanges = !getEditorValuesSnapshot().equals(originalEditorValues);
    }
    updateSaveButtons();
  }

  /**
   * Returns true if the service editor contains unsaved changes.
   *
   * @return true if the service editor contains unsaved changes
   */
  public boolean hasUnsavedChanges() {
    return hasUnsavedServiceChanges;
  }

  /** Discards changes made in this editor and reloads the persisted services. */
  public void discardPendingChanges() {
    serviceHandler.discardPendingChanges();
    hasUnsavedServiceChanges = false;
    refreshServicesTable();
    updateSaveButtons();
    showCard(LIST_CARD);
  }

  /**
   * Initialize the "cancel" button.
   *
   * @return javax.swing.JButton cancelButton
   */
  private JButton getCancelButton() {
    if (cancelButton == null) {
      cancelButton = new JButton();
      cancelButton.setText(i18n.get(ServicesDlg.class, "Cancel", "Cancel"));
      cancelButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              leaveEditorCard();
            }
          });
    }
    return cancelButton;
  }

  /**
   * Initializes the "close" button.
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
              discardPendingChanges();
              setVisible(false);
            }
          });
    }
    return closeButton;
  }

  /**
   * Initializes the service-list "save" button.
   *
   * @return javax.swing.JButton
   */
  private JButton getListSaveButton() {
    if (listSaveButton == null) {
      listSaveButton = new JButton();
      listSaveButton.setText(i18n.get(ServicesDlg.class, "Save", "Save"));
      listSaveButton.setEnabled(false);
      listSaveButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              if (serviceHandler.savePendingChanges()) {
                hasUnsavedServiceChanges = false;
                updateSaveButtons();
                setVisible(false);
              }
            }
          });
    }
    return listSaveButton;
  }

  /**
   * Initialize the "copy" button.
   *
   * @return javax.swing.JButton copyButton
   */
  private JButton getCopyButton() {
    if (copyButton == null) {
      copyButton = new JButton();
      copyButton.setText(i18n.get(ServicesDlg.class, "Copy", "Copy"));
      copyButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              String keyCopy =
                  JOptionPane.showInputDialog(
                      nodusMapPanel,
                      i18n.get(
                          ServicesDlg.class, "Enter_new_service_name", "Enter new service name"));

              if (keyCopy != null) {
                if (keyCopy.length() > 30) {
                  keyCopy = keyCopy.substring(0, 30);
                }

                // ID must be unique
                if (getRowInModelbyService(keyCopy) != -1) {
                  JOptionPane.showMessageDialog(
                      nodusMapPanel,
                      i18n.get(
                          ServicesDlg.class,
                          "Service_already_exists",
                          "TransportService already exists"),
                      i18n.get(ServicesDlg.class, "Service_Editor", "TransportService Editor"),
                      JOptionPane.ERROR_MESSAGE);

                  return;
                }
                String key =
                    (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
                TransportService s = serviceHandler.getService(key);

                serviceHandler.displayService(keyCopy);
                TransportService serviceCopy = serviceHandler.getCurrentService();
                serviceCopy.setName(keyCopy);
                serviceCopy.setFrequency(s.getFrequency());
                serviceCopy.setMode(s.getMode());
                serviceCopy.setMeans(s.getMeans());
                serviceCopy.setChunks(s.getLinks());
                serviceCopy.setStops(s.getStopNodes());

                serviceHandler.saveService(serviceCopy);

                fillServicesTable(keyCopy);

                markServicesChanged();

                serviceHandler.resetService();

                getServiceTable()
                    .setRowSelectionInterval(
                        getRowInModelbyService(keyCopy), getRowInModelbyService(keyCopy));
              }
            }
          });
    }
    return copyButton;
  }

  /**
   * Initializes the "delete" button.
   *
   * @return javax.swing.JButton deleteButton
   */
  private JButton getDeleteButton() {
    if (deleteButton == null) {
      deleteButton = new JButton();
      deleteButton.setText(i18n.get(ServicesDlg.class, "Delete", "Delete"));
      deleteButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              if (getServiceTable().getSelectedRow() == -1) {
                return;
              }
              showLayerView();

              String serviceName =
                  (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
              if (serviceName == null) {
                return;
              }

              serviceHandler.removeService(serviceName);
              hasUnsavedServiceChanges = true;
              updateSaveButtons();
              if (getRowInModelbyService(serviceName) != -1) {
                servicesTableModel.removeRow(getRowInModelbyService(serviceName));
              }

              if (getServiceTable().getRowCount() > 0) {
                getServiceTable().setRowSelectionInterval(0, 0);
              }

              enableButtons();
            }
          });
    }
    return deleteButton;
  }

  /**
   * Initializes the line-view toggle check box.
   *
   * @return javax.swing.JCheckBox lineViewCheckBox
   */
  private JCheckBox getLineViewCheckBox() {
    if (lineViewCheckBox == null) {
      lineViewCheckBox = new JCheckBox();
      updateLineViewCheckBox();
      lineViewCheckBox.setEnabled(false);
      lineViewCheckBox.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              setLineViewActive(lineViewCheckBox.isSelected());
            }
          });
    }
    return lineViewCheckBox;
  }

  /** Updates the state of the line-view toggle check box. */
  private void updateLineViewCheckBox() {
    if (lineViewCheckBox == null) {
      return;
    }
    lineViewCheckBox.setText(i18n.get(ServicesDlg.class, "Line_view", "Line view"));
    lineViewCheckBox.setSelected(lineViewActive);
  }

  /** Enables or disables the filtered service line view. */
  private void setLineViewActive(boolean active) {
    if (active) {
      String serviceName = getSelectedServiceName();
      if (serviceName == null) {
        lineViewActive = false;
        serviceHandler.clearLineView();
      } else {
        lineViewActive = true;
        serviceHandler.displayLineView(serviceHandler.getService(serviceName));
      }
    } else {
      lineViewActive = false;
      serviceHandler.clearLineView();
    }
    updateLineViewCheckBox();
    enableButtons();
  }

  /** Refreshes the filtered line view after the table selection changes. */
  private void refreshLineViewForSelection() {
    updateLineViewCheckBox();
    if (lineViewActive) {
      String serviceName = getSelectedServiceName();
      if (serviceName == null) {
        setLineViewActive(false);
      } else {
        serviceHandler.displayLineView(serviceHandler.getService(serviceName));
      }
    }
  }

  /** Returns the selected service name, or null if no service is selected. */
  private String getSelectedServiceName() {
    int row = getServiceTable().getSelectedRow();
    if (row == -1) {
      return null;
    }
    Object serviceName = getServiceTable().getValueAt(row, 1);
    return serviceName == null ? null : serviceName.toString();
  }

  /** Restores the normal layer view. */
  private void showLayerView() {
    if (lineViewActive) {
      setLineViewActive(false);
    } else {
      serviceHandler.clearLineView();
      updateLineViewCheckBox();
      enableButtons();
    }
  }

  /**
   * Initializes the "edit" button.
   *
   * @return javax.swing.JButton editButton
   */
  private JButton getEditButton() {
    if (editButton == null) {
      editButton = new JButton();
      editButton.setText(i18n.get(ServicesDlg.class, "Edit", "Edit"));
      editButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              editCurrentService();
            }
          });
    }
    return editButton;
  }

  /** Edits the currently selected service. */
  private void editCurrentService() {
    TransportService s = serviceHandler.getCurrentService();
    if (s == null) {
      return;
    }
    showLayerView();

    isLoadingEditorFields = true;
    nameField.setText(s.getName());
    idxField.setText(s.getId() + "");
    int[] temp = computeFrequencyUnit(s.getFrequency());
    frequencyField.setText(temp[0] + "");
    time.setSelectedIndex(temp[1]);
    loadModeMeans(s.getMode(), s.getMeans());
    isLoadingEditorFields = false;
    resetEditorDirtyState();

    serviceHandler.setListening(true);
    showCard(EDITOR_CARD);
  }

  /**
   * Initialize the editor card.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getEditorCard() {
    if (editorCard == null) {

      GridBagLayout editLayout = new GridBagLayout();

      editorCard = new JPanel();
      editorCard.setLayout(editLayout);

      JPanel modeMeansPanel = new JPanel();
      modeMeansPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
      modeMeansPanel.add(new JLabel(i18n.get(ServicesDlg.class, "Service_Mode", "Mode")));
      modeMeansPanel.add(modeField);
      modeMeansPanel.add(new JLabel(i18n.get(ServicesDlg.class, "Service_Means", "Means")));
      modeMeansPanel.add(meansField);

      JPanel frequencyPanel = new JPanel();
      frequencyPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
      frequencyPanel.add(new JLabel(i18n.get(ServicesDlg.class, "Service_Frequency", "Frequency")));
      frequencyPanel.add(frequencyField);
      frequencyPanel.add(new JLabel(i18n.get(ServicesDlg.class, "Frequency_per", " per ")));
      frequencyPanel.add(time);

      JPanel buttonsPanel = new JPanel();
      buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
      buttonsPanel.add(getCancelButton());
      buttonsPanel.add(getSaveButton());

      addToGridBag(
          editorCard,
          new JLabel(i18n.get(ServicesDlg.class, "Service_Index", "ID")),
          createConstraints(
              0,
              0,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          idxField,
          createConstraints(
              1,
              0,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          new JLabel(i18n.get(ServicesDlg.class, "Service_Name", "Name")),
          createConstraints(
              0,
              1,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          nameField,
          createConstraints(
              1,
              1,
              3,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              new Insets(5, 5, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          modeMeansPanel,
          createConstraints(
              0,
              2,
              4,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(5, 0, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          frequencyPanel,
          createConstraints(
              0,
              3,
              4,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(0, 0, 0, 0),
              0,
              0));

      addToGridBag(
          editorCard,
          buttonsPanel,
          createConstraints(
              0,
              4,
              4,
              1,
              0,
              0,
              GridBagConstraints.CENTER,
              GridBagConstraints.NONE,
              new Insets(0, 0, 5, 0),
              0,
              0));

      period[0] = getPeriodLabel(0);
      period[1] = getPeriodLabel(1);
      period[2] = getPeriodLabel(2);
      period[3] = getPeriodLabel(3);

      for (String element : period) {
        time.addItem(element);
      }

      DocumentListener saveStateDocumentListener =
          new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
              updateEditorDirtyState();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
              updateEditorDirtyState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
              updateEditorDirtyState();
            }
          };
      nameField.getDocument().addDocumentListener(saveStateDocumentListener);
      frequencyField.getDocument().addDocumentListener(saveStateDocumentListener);
      meansField.addItemListener(e -> updateEditorDirtyState());
      time.addItemListener(e -> updateEditorDirtyState());
    }

    return editorCard;
  }

  /**
   * Initializes the services list card.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getListCard() {
    if (listCard == null) {

      listCard = new JPanel();

      listCard.setLayout(new GridBagLayout());

      addToGridBag(
          listCard,
          getServicesScrollPane(),
          createConstraints(
              0,
              1,
              8,
              1,
              0.1,
              0.1,
              GridBagConstraints.NORTH,
              GridBagConstraints.BOTH,
              new Insets(5, 5, 5, 5),
              200,
              0));

      addToGridBag(
          listCard,
          getAddButton(),
          createConstraints(
              0,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getEditButton(),
          createConstraints(
              1,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getCopyButton(),
          createConstraints(
              2,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getDeleteButton(),
          createConstraints(
              3,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getLineViewCheckBox(),
          createConstraints(
              4,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          new JPanel(),
          createConstraints(
              5,
              2,
              1,
              1,
              1,
              0,
              GridBagConstraints.CENTER,
              GridBagConstraints.HORIZONTAL,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getListSaveButton(),
          createConstraints(
              6,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHEAST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));

      addToGridBag(
          listCard,
          getCloseButton(),
          createConstraints(
              7,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHEAST,
              GridBagConstraints.NONE,
              new Insets(5, 5, 5, 5),
              0,
              0));
      enableButtons();
    }
    return listCard;
  }

  /**
   * Initialize the main panel of the dialog.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getMainPane() {
    if (mainPanel == null) {
      mainPanel = new JPanel();

      mainPanel.setLayout(cards);
      mainPanel.add(getListCard(), LIST_CARD);
      mainPanel.add(getEditorCard(), EDITOR_CARD);
    }
    return mainPanel;
  }

  /**
   * Initializes the "add" button.
   *
   * @return javax.swing.JButton addButton
   */
  private JButton getAddButton() {
    if (addButton == null) {
      addButton = new JButton();
      addButton.setText(i18n.get(ServicesDlg.class, "Add", "Add"));
      addButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              showLayerView();

              int idx = serviceHandler.getNewServiceId();

              isLoadingEditorFields = true;
              idxField.setText(idx + "");
              frequencyField.setText(12 + "");
              time.setSelectedIndex(0);
              descriptionField.setText("");
              nameField.setText("");

              loadModeMeans((byte) -1, (byte) -1);
              isLoadingEditorFields = false;
              resetEditorDirtyState();

              nameField.setEditable(true);

              showCard(EDITOR_CARD);

              serviceHandler.resetService();

              serviceHandler.displayService("");
              // setListening(true);
              serviceHandler.setListening(true);
            }
          });
    }
    return addButton;
  }

  /**
   * Returns the index of service in ServiceTable.
   *
   * @param String name of TransportService
   * @return Integer index in ModelServiceTable
   */
  private int getRowInJTablebyService(String nameService) {
    if (nameService != null) {
      for (int i = 0; i < getServiceTable().getRowCount(); i++) {
        if (nameService.compareTo((String) getServiceTable().getValueAt(i, 1)) == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Returns the index of service in ServiceTable.
   *
   * @param Integer id of the TransportService
   * @return Integer index in ServiceTable
   */
  private int getRowInModelbyService(int idService) {
    for (int i = 0; i < servicesTableModel.getRowCount(); i++) {
      if (idService == Integer.valueOf((String) servicesTableModel.getValueAt(i, 0))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index of service in ModelServiceTable.
   *
   * @param String name of the TransportService
   * @return Integer index in ModelServiceTable
   */
  private int getRowInModelbyService(String nameService) {
    if (nameService != null) {
      for (int i = 0; i < servicesTableModel.getRowCount(); i++) {
        if (nameService.compareTo((String) servicesTableModel.getValueAt(i, 1)) == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * This method initializes caveButton.
   *
   * @return javax.swing.JButton caveButton
   */
  private JButton getSaveButton() {
    if (saveButton == null) {
      saveButton = new JButton();
      saveButton.setText(i18n.get(ServicesDlg.class, "Save", "Save"));
      saveButton.setEnabled(false);
      saveButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {

              if (JDBCUtils.getInt(frequencyField.getText()) == Integer.MIN_VALUE) {

                JOptionPane.showMessageDialog(
                    null,
                    MessageFormat.format(
                        i18n.get(ServicesDlg.class, "not_correct", "{0} not correct."),
                        i18n.get(ServicesDlg.class, "frequency_value", "frequency value")),
                    NodusC.APPNAME,
                    JOptionPane.ERROR_MESSAGE);
                return;
              }

              String name = nameField.getText();
              if (name.compareTo("") != 0) {

                // Limit to 30 characters
                if (name.length() > 30) {
                  name = name.substring(0, 30);
                }

                int id = Integer.valueOf(idxField.getText());

                // Name must be unique
                if (getRowInModelbyService(name) != -1 && getRowInModelbyService(id) == -1) {
                  JOptionPane.showMessageDialog(
                      nodusMapPanel,
                      i18n.get(
                          ServicesDlg.class,
                          "Service_already_exists",
                          "TransportService already exists"),
                      i18n.get(ServicesDlg.class, "Service_Editor", "TransportService Editor"),
                      JOptionPane.ERROR_MESSAGE);

                  return;
                }

                TransportService s = serviceHandler.getCurrentService();
                String validationMessage = serviceHandler.getServiceLineValidationMessage(s);
                if (validationMessage != null) {
                  JOptionPane.showMessageDialog(
                      nodusMapPanel,
                      MessageFormat.format(
                          i18n.get(
                              ServicesDlg.class,
                              "Invalid_service_line",
                              "The service line is not valid because {0}."),
                          validationMessage),
                      NodusC.APPNAME,
                      JOptionPane.ERROR_MESSAGE);
                  return;
                }

                s.setName(name);
                s.setFrequency(
                    Integer.valueOf(frequencyField.getText())
                        * constPeriod[time.getSelectedIndex()]);
                s.setMode(Byte.valueOf(modeField.getText()));
                s.setMeans(Byte.valueOf(meansField.getSelectedItem().toString()));

                serviceHandler.saveService(s);

                int row = getRowInModelbyService(id);
                if (row != -1) {
                  servicesTableModel.removeRow(row);
                }

                fillServicesTable(name);

                markServicesChanged();
                enableButtons();
                resetEditorDirtyState();
                serviceHandler.setListening(false);
                showCard(LIST_CARD);
                selectService(name);
              } else {
                JOptionPane.showMessageDialog(
                    null,
                    MessageFormat.format(
                        i18n.get(ServicesDlg.class, "not_correct", "{0} not correct."),
                        i18n.get(ServicesDlg.class, "name_value", "name value")),
                    NodusC.APPNAME,
                    JOptionPane.ERROR_MESSAGE);
              }
            }
          });
    }
    return saveButton;
  }

  /**
   * This method initializes scrollPane.
   *
   * @return javax.swing.JScrollPane
   */
  private JScrollPane getServicesScrollPane() {
    if (scrollPane == null) {
      scrollPane = new JScrollPane();
      scrollPane.setViewportView(getServiceTable());
    }
    return scrollPane;
  }

  /**
   * This method initializes ServiceList.
   *
   * @return javax.swing.JTable
   */
  private JTable getServiceTable() {

    if (serviceTable == null) {
      servicesTableModel.addColumn(i18n.get(ServicesDlg.class, "Service_ID", "ID"));
      servicesTableModel.addColumn(i18n.get(ServicesDlg.class, "Service_Name", "Name"));
      servicesTableModel.addColumn(i18n.get(ServicesDlg.class, "Service_Mode", "Mode"));
      servicesTableModel.addColumn(i18n.get(ServicesDlg.class, "Service_Means", "Means"));
      servicesTableModel.addColumn(i18n.get(ServicesDlg.class, "Service_Frequency", "Frequency"));

      Iterator<String> it = serviceHandler.getServiceNamesIterator();

      while (it.hasNext()) {
        // Get ID
        String key = it.next();

        fillServicesTable(key);
      }

      for (int i = 0; i < servicesTableModel.getRowCount(); ++i) {
        for (int j = 0; j < servicesTableModel.getColumnCount(); ++j) {
          servicesTableModel.isCellEditable(i, j);
        }
      }

      sorter = new TableSorter(servicesTableModel);

      serviceTable =
          new JTable(sorter) {
            private static final long serialVersionUID = -7475265149923185555L;

            @Override
            public boolean isCellEditable(int row, int column) {
              return false;
            }
          };

      sorter.setTableHeader(serviceTable.getTableHeader());
      sorter.setSortingStatus(0, TableSorter.ASCENDING);

      serviceTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
      setServiceTableColumnWidths();
      serviceTable.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (e.getClickCount() == 2 && getServiceTable().getSelectedRow() != -1) {
                editCurrentService();
              }
            }
          });

      /* Intercept the value changed even */
      serviceTable
          .getSelectionModel()
          .addListSelectionListener(
              new javax.swing.event.ListSelectionListener() {
                @Override
                public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                  if (getServiceTable().getSelectedRow() == -1) {
                    return;
                  }

                  String serviceName =
                      (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
                  if (serviceName == null) {
                    return;
                  }
                  // Hide current service
                  serviceHandler.paintService(false);

                  // Load new service
                  serviceHandler.displayService(serviceName);
                  enableButtons();
                  refreshLineViewForSelection();
                }
              });
    }
    return serviceTable;
  }

  /** Sets stable widths so the frequency display remains readable. */
  private void setServiceTableColumnWidths() {
    TableColumnModel columnModel = serviceTable.getColumnModel();
    setServiceTableColumnWidth(columnModel, 0, 80, 60);
    setServiceTableColumnWidth(columnModel, 1, 260, 120);
    setServiceTableColumnWidth(columnModel, 2, 70, 55);
    setServiceTableColumnWidth(columnModel, 3, 80, 60);
    setServiceTableColumnWidth(columnModel, 4, 240, 220);

    serviceTable.setPreferredScrollableViewportSize(new Dimension(760, 520));
  }

  /** Sets preferred and minimum widths for a service table column. */
  private void setServiceTableColumnWidth(
      TableColumnModel columnModel, int column, int preferredWidth, int minWidth) {
    columnModel.getColumn(column).setPreferredWidth(preferredWidth);
    columnModel.getColumn(column).setMinWidth(minWidth);
  }

  /** Rebuilds the service list from the handler state. */
  private void refreshServicesTable() {
    if (serviceTable == null) {
      return;
    }

    servicesTableModel.setRowCount(0);
    Iterator<String> it = serviceHandler.getServiceNamesIterator();

    while (it.hasNext()) {
      fillServicesTable(it.next());
    }
    enableButtons();
  }

  /**
   * This method initializes this dialog.
   *
   * @return void
   */
  private void initialize() {
    // this.setSize(500, 300);

    this.setContentPane(getMainPane());
    installEscapeKey();
    cards.show(mainPanel, LIST_CARD);
    pack();
    setLocationRelativeTo(nodusMapPanel.getMainFrame());
  }

  /** Handles Escape consistently from any component in the dialog. */
  private void installEscapeKey() {
    getRootPane()
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("ESCAPE"), "handleEscape");
    getRootPane()
        .getActionMap()
        .put(
            "handleEscape",
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent e) {
                if (isEditorCardVisible()) {
                  suppressDialogCloseAfterLeavingEditor();
                  leaveEditorCard();
                } else {
                  setVisible(false);
                }
              }
            });
  }

  /**
   * Initializes the "mode" end "means" fields.
   *
   * @param mode Mode to add, or -1
   * @param means Means to add, or -1
   */
  public void loadModeMeans(int mode, int means) {
    modeField.setText(mode + "");
    meansField.removeAllItems();
    if (mode == -1) {
      meansField.addItem("-1");
      meansField.setSelectedIndex(0);
    } else {
      for (int i = 0; i < means; ++i) {
        meansField.addItem("" + (i + 1));
      }
      if (meansField.getItemCount() > 0) {
        meansField.setSelectedItem("" + means);
      }
    }
    updateSaveButtons();
  }

  /**
   * Selects a given service name in the list.
   *
   * @param service The service name to select
   */
  public void selectService(String service) {
    int i = getRowInJTablebyService(service);
    if (i != -1) {
      getServiceTable().setRowSelectionInterval(i, i);
    }
  }

  /** Adds a component to a panel using GridBagLayout constraints. */
  private void addToGridBag(JPanel panel, Component component, GridBagConstraints constraints) {
    panel.add(component, constraints);
  }

  /**
   * Creates GridBagConstraints for a component.
   *
   * @return GridBagConstraints
   */
  private GridBagConstraints createConstraints(
      int gridx,
      int gridy,
      int gridwidth,
      int gridheight,
      double weightx,
      double weighty,
      int anchor,
      int fill,
      Insets insets,
      int ipadx,
      int ipady) {
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = gridx;
    gbc.gridy = gridy;
    gbc.gridwidth = gridwidth;
    gbc.gridheight = gridheight;
    gbc.weightx = weightx;
    gbc.weighty = weighty;
    gbc.anchor = anchor;
    gbc.fill = fill;
    gbc.insets = insets;
    gbc.ipadx = ipadx;
    gbc.ipady = ipady;

    return gbc;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void setVisible(boolean visible) {

    if (!visible && suppressDialogCloseAfterLeavingEditor && !isEditorCardVisible()) {
      suppressDialogCloseAfterLeavingEditor = false;
      return;
    }

    if (!visible && isEditorCardVisible()) {
      suppressDialogCloseAfterLeavingEditor();
      leaveEditorCard();
      return;
    }

    if (!visible && hasUnsavedServiceChanges) {
      showLayerView();
      discardPendingChanges();
    } else if (visible) {
      refreshServicesTable();
    } else {
      showLayerView();
    }
    super.setVisible(visible);
    if (visible) {
      String serviceName = null;
      if (getServiceTable().getRowCount() > 0) {
        if (getServiceTable().getSelectedRow() != -1) {
          serviceName =
              (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
        } else {
          serviceName = (String) getServiceTable().getValueAt(0, 1);
          getServiceTable().setRowSelectionInterval(0, 0);
        }
        serviceHandler.displayService(serviceName);
      }
      cards.show(mainPanel, LIST_CARD);
      enableButtons();
    } else {
      serviceHandler.resetService();
    }
  }

  /** Disposes the dialog without converting the hide request into an editor-card cancel action. */
  public void disposeFromServiceHandler() {
    super.setVisible(false);
    dispose();
  }

  private boolean isEditorCardVisible() {
    return editorCard != null && editorCard.isVisible();
  }

  private void suppressDialogCloseAfterLeavingEditor() {
    suppressDialogCloseAfterLeavingEditor = true;
    SwingUtilities.invokeLater(() -> suppressDialogCloseAfterLeavingEditor = false);
  }

  private void leaveEditorCard() {
    TransportService s = serviceHandler.getCurrentService();
    serviceHandler.resetService();

    String key = s.getName();
    if (key != null) {
      if (getServiceTable().getRowCount() != 0) {
        int row = getRowInModelbyService(key);
        if (row != -1) {
          getServiceTable().setRowSelectionInterval(row, row);
        } else {
          getServiceTable().setRowSelectionInterval(0, 0);
          key = (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
        }
      }
      serviceHandler.displayService(key);
    }
    serviceHandler.setListening(false);

    showCard(LIST_CARD);
  }

  private void showCard(String card) {
    if (card.equals(EDITOR_CARD)) {
      if (startDimension == null) {
        startDimension = getSize();
      }
      setPreferredSize(EDITOR_CARD_SIZE);
    } else {
      setPreferredSize(startDimension);
    }
    cards.show(mainPanel, card);
    pack();
  }
}
