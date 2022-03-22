/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Iterator;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * The service editor allows editing the lines and services.
 *
 * @author Bart Jourquin
 */
//TODO (services) Referesh UI
public class ServicesDlg extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 1L;

  private static final Insets ZERO_INSETS = new Insets(0, 0, 0, 0);
  private static final Insets TYPE_INSETS = new Insets(5, 5, 5, 5);
  private static final Insets LABEL_INSETS = new Insets(5, 15, 5, 5);

  /** . */
  private CardLayout cl = new CardLayout();

  /** . */
  private int[] constPeriod = {1, 12, 52, 365};

  /** . */
  private JPanel displayPanel = null;

  /** . */
  private JPanel editPanel = null;

  /** . */
  private NumberFormat formatter;

  /** . */
  private GridBagConstraints gridBagConstraints;

  /** . */
  private JScrollPane scrollPane = null;

  /** . */
  private JLabel modeField = new JLabel();

  /** . */
  private JLabel idxField = new JLabel();

  /** . */
  private DefaultTableModel modeltable = new DefaultTableModel();

  /** . */
  private JTextField nameField = new JTextField(20);

  /** . */
  private JTextField frequencyField = new JTextField(10);

  /** . */
  private JTextField descriptionField = new JTextField(50);

  /** . */
  private JButton newButton = null;

  /** . */
  private JButton editButton = null;

  /** . */
  private JButton deleteButton = null;

  /** . */
  private JButton closeButton = null;

  /** . */
  private JButton cancelButton = null;

  /** . */
  private JButton saveButton = null;

  /** . */
  private JButton copyButton = null;

  /** . */
  private NodusMapPanel nodusMapPanel;

  /** . */
  private JPanel panelRoot = null;

  /** . */
  private String[] period = new String[4];

  /** . */
  private ServiceHandler serviceHandler;

  /** . */
  private JTable serviceTable = null;

  /** . */
  private TableSorter sorter;

  /** . */
  private JComboBox<String> time = new JComboBox<String>();

  /** . */
  private JComboBox<String> meansField = new JComboBox<String>();

  /**
   * Creates the service editor dialog.
   *
   * @param serviceHandler The service handler.
   */
  public ServicesDlg(ServiceHandler serviceHandler) {
    super(
        serviceHandler.getNodusMapPanel().getMainFrame(),
        i18n.get(ServicesDlg.class, "Service_editor", "Transport Services editor"),
        false);
    this.serviceHandler = serviceHandler;
    nodusMapPanel = serviceHandler.getNodusMapPanel();
    initialize();
  }

  /** This method enables or disable the buttons. */
  private void buttonEnable() {
    if (getServiceTable().getRowCount() != 0) {
      getDeleteButton().setEnabled(true);
      getEditButton().setEnabled(true);
      getCopyButton().setEnabled(true);
    } else {
      getDeleteButton().setEnabled(false);
      getEditButton().setEnabled(false);
      getCopyButton().setEnabled(false);
    }
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

  /** This method fills the ServiceTable. */
  private void fillInTableService(String nameService) {
    formatter = new DecimalFormat("00000");
    TransportService s = serviceHandler.getService(nameService);
    if (s != null) {
      modeltable.addRow(
          new Object[] {
            formatter.format(s.getId()),
            nameService,
            formatter.format(s.getMode()),
            formatter.format(s.getMeans()),
            formatter.format(s.getFrequency()),
            s.getDescription()
          });
    }
  }

  /**
   * This method initializes cancelButton.
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
                    key =
                        (String)
                            getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
                  }
                }
                serviceHandler.displayService(key);
              }
              serviceHandler.setListening(false);
              cl.show(panelRoot, "display");
            }
          });
    }
    return cancelButton;
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
              setVisible(false);
            }
          });
    }
    return closeButton;
  }

  /**
   * This method initializes copyButton.
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
                          ServicesDlg.class,
                          "Enter_new_service_name",
                          "Enter new service name"));

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
                serviceCopy.setDescription(s.getDescription());
                serviceCopy.setChunks(s.getLinks());
                serviceCopy.setStops(s.getStopNodes());

                serviceHandler.saveService(serviceCopy);

                fillInTableService(keyCopy);

                serviceHandler.mustBeSaved();

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
   * This method initializes deleteButton.
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

              String serviceName =
                  (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
              if (serviceName == null) {
                return;
              }

              serviceHandler.removeService(serviceName);
              if (getRowInModelbyService(serviceName) != -1) {
                modeltable.removeRow(getRowInModelbyService(serviceName));
              }

              if (getServiceTable().getRowCount() > 0) {
                getServiceTable().setRowSelectionInterval(0, 0);
              }

              buttonEnable();
            }
          });
    }
    return deleteButton;
  }

  /**
   * This method initializes the panel which visualizes the services.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getDisplayPanel() {
    if (displayPanel == null) {

      displayPanel = new JPanel();

      displayPanel.setLayout(new GridBagLayout());

      displayPanel.add(
          getJScrollPane(),
          setContraints(
              0,
              1,
              5,
              1,
              0.1,
              0.1,
              GridBagConstraints.NORTH,
              GridBagConstraints.BOTH,
              TYPE_INSETS,
              200,
              0));

      displayPanel.add(
          getNewButton(),
          setContraints(
              0,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));

      displayPanel.add(
          getEditButton(),
          setContraints(
              1,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));

      displayPanel.add(
          getCopyButton(),
          setContraints(
              2,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));

      displayPanel.add(
          getDeleteButton(),
          setContraints(
              3,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHWEST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));

      displayPanel.add(
          getCloseButton(),
          setContraints(
              4,
              2,
              1,
              1,
              0,
              0,
              GridBagConstraints.SOUTHEAST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));
      buttonEnable();
    }
    return displayPanel;
  }

  /**
   * This method initializes editButton.
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
              formatter = new DecimalFormat("00000");

              TransportService s = serviceHandler.getCurrentService();
              nameField.setText(s.getName());
              idxField.setText(s.getId() + "");
              int[] temp = computeFrequencyUnit(s.getFrequency());
              frequencyField.setText(temp[0] + "");
              time.setSelectedIndex(temp[1]);
              loadModeMeans(s.getMode(), s.getMeans());
              descriptionField.setText(s.getDescription());

              nameField.setEditable(false);
              serviceHandler.setListening(true);
              cl.show(panelRoot, "edit");
            }
          });
    }
    return editButton;
  }

  /**
   * This method initializes the panel which edit the services.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getEditPanel() {
    if (editPanel == null) {

      GridBagLayout editLayout = new GridBagLayout();

      editPanel = new JPanel();
      editPanel.setLayout(editLayout);

      JPanel panelFreq = new JPanel();
      panelFreq.setLayout(new FlowLayout(FlowLayout.LEFT));
      panelFreq.add(frequencyField);
      panelFreq.add(new JLabel(" per "));
      panelFreq.add(time);

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Name", "Name")),
          setContraints(
              0,
              0,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          nameField,
          setContraints(
              1,
              0,
              1,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Index", "Index")),
          setContraints(
              2,
              0,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          idxField,
          setContraints(
              3,
              0,
              1,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Mode", "Mode")),
          setContraints(
              0,
              1,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          modeField,
          setContraints(
              1,
              1,
              1,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));
      editPanel.add(
          modeField,
          setContraints(
              1,
              1,
              1,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Means", "Means")),
          setContraints(
              2,
              1,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          meansField,
          setContraints(
              3,
              1,
              1,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Frequency", "Frequency")),
          setContraints(
              0,
              2,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          panelFreq,
          setContraints(
              1,
              2,
              GridBagConstraints.RELATIVE,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              ZERO_INSETS,
              0,
              0));

      editPanel.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Description", "Description")),
          setContraints(
              0,
              3,
              1,
              1,
              0,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              LABEL_INSETS,
              0,
              0));
      editPanel.add(
          descriptionField,
          setContraints(
              1,
              3,
              GridBagConstraints.RELATIVE,
              1,
              0.3,
              0.3,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              TYPE_INSETS,
              0,
              0));

      editPanel.add(
          getCancelButton(),
          setContraints(
              1,
              4,
              1,
              1,
              0,
              0,
              GridBagConstraints.NORTHEAST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));
      editPanel.add(
          getSaveButton(),
          setContraints(
              2,
              4,
              1,
              1,
              0,
              0,
              GridBagConstraints.NORTHWEST,
              GridBagConstraints.NONE,
              TYPE_INSETS,
              0,
              0));

      period[0] = i18n.get(ServicesDlg.class, "year", "year111");
      period[1] = i18n.get(ServicesDlg.class, "month", "month");
      period[2] = i18n.get(ServicesDlg.class, "week", "week");
      period[3] = i18n.get(ServicesDlg.class, "day", "day");

      for (String element : period) {
        time.addItem(element);
      }
    }
    return editPanel;
  }

  /**
   * This method initializes jContentPane - the main panel of the dialog.
   *
   * @return javax.swing.JPanel
   */
  private JPanel getJContentPane() {
    if (panelRoot == null) {
      panelRoot = new JPanel();
      ;
      panelRoot.setLayout(cl);
      panelRoot.add(getDisplayPanel(), "display");
      panelRoot.add(getEditPanel(), "edit");
    }
    return panelRoot;
  }

  /**
   * This method initializes scrollPane.
   *
   * @return javax.swing.JScrollPane
   */
  private JScrollPane getJScrollPane() {
    if (scrollPane == null) {
      scrollPane = new JScrollPane();
      scrollPane.setViewportView(getServiceTable());
    }
    return scrollPane;
  }

  /**
   * This method initializes newButton.
   *
   * @return javax.swing.JButton newButton
   */
  private JButton getNewButton() {
    if (newButton == null) {
      newButton = new JButton();
      newButton.setText(i18n.get(ServicesDlg.class, "Add", "Add"));
      newButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {

              formatter = new DecimalFormat("0000");

              int idx = serviceHandler.getNewServiceId();

              idxField.setText(idx + "");
              frequencyField.setText(12 + "");
              time.setSelectedIndex(0);
              descriptionField.setText("");
              nameField.setText("");

              loadModeMeans((byte) -1, (byte) -1);

              nameField.setEditable(true);

              cl.show(panelRoot, "edit");

              serviceHandler.resetService();

              serviceHandler.displayService("");
              // setListening(true);
              serviceHandler.setListening(true);
            }
          });
    }
    return newButton;
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
    for (int i = 0; i < modeltable.getRowCount(); i++) {
      if (idService == Integer.valueOf((String) modeltable.getValueAt(i, 0))) {
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
      for (int i = 0; i < modeltable.getRowCount(); i++) {
        if (nameService.compareTo((String) modeltable.getValueAt(i, 1)) == 0) {
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

                s.setName(name);
                s.setFrequency(
                    Integer.valueOf(frequencyField.getText())
                        * constPeriod[time.getSelectedIndex()]);
                s.setMode(Byte.valueOf(modeField.getText()));
                s.setMeans(Byte.valueOf(meansField.getSelectedItem().toString()));
                s.setDescription(descriptionField.getText());

                serviceHandler.saveService(s);

                formatter = new DecimalFormat("00000");
                if (getRowInModelbyService(name) != -1) {
                  modeltable.removeRow(getRowInModelbyService(name));
                }

                fillInTableService(name);

                serviceHandler.mustBeSaved();
                buttonEnable();
                serviceHandler.setListening(false);
                cl.show(panelRoot, "display");
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
   * This method initializes ServiceList.
   *
   * @return javax.swing.JTable
   */
  private JTable getServiceTable() {

    if (serviceTable == null) {
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Index", "Index"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Name", "Name"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Mode", "Mode"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Means", "Means"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Frequency", "Freqyency"));
      modeltable.addColumn(i18n.get(ServicesDlg.class, "Service_Type", "Type"));

      Iterator<String> it = serviceHandler.getServiceNamesIterator();
      formatter = new DecimalFormat("00000");
      while (it.hasNext()) {
        // Get ID
        String key = it.next();

        fillInTableService(key);
      }

      for (int i = 0; i < modeltable.getRowCount(); ++i) {
        for (int j = 0; j < modeltable.getColumnCount(); ++j) {
          modeltable.isCellEditable(i, j);
        }
      }

      sorter = new TableSorter(modeltable);

      serviceTable =
          new JTable(sorter) {
            private static final long serialVersionUID = -7475265149923185555L;

            @Override
            public boolean isCellEditable(int row, int column) {
              return false;
            }
          };

      sorter.setTableHeader(serviceTable.getTableHeader());

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
                  buttonEnable();
                }
              });
    }
    return serviceTable;
  }

  /**
   * This method initializes this dialog.
   *
   * @return void
   */
  private void initialize() {
    this.setSize(500, 300);

    this.setContentPane(getJContentPane());
    cl.show(panelRoot, "display");
  }

  /**
   * Initializes the "mode" end "means" fields.
   *
   * @param mode Mode to add, or -1
   * @param means Means to add, or -1
   */
  public void loadModeMeans(int mode, int means) {
    modeField.setText(mode + "");
    if (mode == -1) {
      meansField.removeAllItems();
      meansField.addItem("-1");
      meansField.setSelectedIndex(0);
    } else {

      if (serviceHandler.getCurrentService().getMeans() != -1
          && means > serviceHandler.getCurrentService().getMeans()) {
        meansField.removeAllItems();
      }

      for (int i = 0; i < means; ++i) {
        meansField.addItem("" + (i + 1));
      }
      meansField.setSelectedIndex(meansField.getItemCount() - 1);
    }
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

  /**
   * This method initializes GridBagConstraints.
   *
   * @return GridBagConstraints
   */
  private GridBagConstraints setContraints(
      int param1,
      int param2,
      int param3,
      int param4,
      double param5,
      double param6,
      int param7,
      int param8,
      Insets param9,
      int param10,
      int param11) {
    if (gridBagConstraints == null) {
      gridBagConstraints = new GridBagConstraints();
    }

    gridBagConstraints.gridx = param1;
    gridBagConstraints.gridy = param2;
    gridBagConstraints.gridwidth = param3;
    gridBagConstraints.gridheight = param4;
    gridBagConstraints.weightx = param5;
    gridBagConstraints.weighty = param6;
    gridBagConstraints.anchor = param7;
    gridBagConstraints.fill = param8;
    gridBagConstraints.insets = param9;
    gridBagConstraints.ipadx = param10;
    gridBagConstraints.ipady = param11;

    return gridBagConstraints;
  }
  
  /**
   * .
   *
   * @hidden
   */
  @Override
  public void setVisible(boolean visible) {
    String serviceName = null;
    super.setVisible(visible);
    if (visible) {
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
      cl.show(panelRoot, "display");
    } else {
      serviceHandler.resetService();
    }
  }
}
