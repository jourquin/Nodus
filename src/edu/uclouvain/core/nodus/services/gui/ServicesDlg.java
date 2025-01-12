/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.MessageFormat;
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

// TODO Load and save on id, not on name

/**
 * The service editor allows editing the lines and services.
 *
 * @author Bart Jourquin
 */
public class ServicesDlg extends EscapeDialog {

  private static final long serialVersionUID = 1L;

  private static I18n i18n = Environment.getI18n();

  private static final String LIST_CARD = "ListCard";
  private static final String EDITOR_CARD = "EditorCord";
  private static final Dimension EDITOR_CARD_SIZE = new Dimension(400, 220);

  /** . */
  private JButton cancelButton = null;

  /** . */
  CardLayout cards = new CardLayout();

  /** . */
  private JButton closeButton = null;

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

  /**
   * Creates the service editor dialog.
   *
   * @param serviceHandler The service handler.
   */
  public ServicesDlg(ServiceHandler serviceHandler) {
    super(
        serviceHandler.getNodusMapPanel().getMainFrame(),
        i18n.get(ServicesDlg.class, "Service_editor", "Services editor"),
        false);
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

  /** Enable or disable the buttons. */
  private void enableButtons() {
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
            f1.format(s.getFrequency())
          });
    }
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

              showCard(LIST_CARD);
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
              setVisible(false);
            }
          });
    }
    return closeButton;
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

              String serviceName =
                  (String) getServiceTable().getValueAt(getServiceTable().getSelectedRow(), 1);
              if (serviceName == null) {
                return;
              }

              serviceHandler.removeService(serviceName);
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

              TransportService s = serviceHandler.getCurrentService();
              nameField.setText(s.getName());
              idxField.setText(s.getId() + "");
              int[] temp = computeFrequencyUnit(s.getFrequency());
              frequencyField.setText(temp[0] + "");
              time.setSelectedIndex(temp[1]);
              loadModeMeans(s.getMode(), s.getMeans());

              serviceHandler.setListening(true);
              showCard(EDITOR_CARD);
            }
          });
    }
    return editButton;
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
      frequencyPanel.add(new JLabel(" per "));
      frequencyPanel.add(time);

      JPanel buttonsPanel = new JPanel();
      buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
      buttonsPanel.add(getCancelButton());
      buttonsPanel.add(getSaveButton());

      editorCard.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Index", "ID")),
          setContraints(
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

      editorCard.add(
          idxField,
          setContraints(
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

      editorCard.add(
          new JLabel(i18n.get(ServicesDlg.class, "Service_Name", "Name")),
          setContraints(
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

      editorCard.add(
          nameField,
          setContraints(
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

      editorCard.add(
          modeMeansPanel,
          setContraints(
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

      editorCard.add(
          frequencyPanel,
          setContraints(
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

      editorCard.add(
          buttonsPanel,
          setContraints(
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

      period[0] = i18n.get(ServicesDlg.class, "year", "year");
      period[1] = i18n.get(ServicesDlg.class, "month", "month");
      period[2] = i18n.get(ServicesDlg.class, "week", "week");
      period[3] = i18n.get(ServicesDlg.class, "day", "day");

      for (String element : period) {
        time.addItem(element);
      }
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

      listCard.add(
          getServicesScrollPane(),
          setContraints(
              0,
              1,
              5,
              1,
              0.1,
              0.1,
              GridBagConstraints.NORTH,
              GridBagConstraints.BOTH,
              new Insets(5, 5, 5, 5),
              200,
              0));

      listCard.add(
          getAddButton(),
          setContraints(
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

      listCard.add(
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
              new Insets(5, 5, 5, 5),
              0,
              0));

      listCard.add(
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
              new Insets(5, 5, 5, 5),
              0,
              0));

      listCard.add(
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
              new Insets(5, 5, 5, 5),
              0,
              0));

      listCard.add(
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

              int idx = serviceHandler.getNewServiceId();

              idxField.setText(idx + "");
              frequencyField.setText(12 + "");
              time.setSelectedIndex(0);
              descriptionField.setText("");
              nameField.setText("");

              loadModeMeans((byte) -1, (byte) -1);

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

                serviceHandler.saveService(s);

                if (getRowInModelbyService(name) != -1) {
                  servicesTableModel.removeRow(getRowInModelbyService(name));
                }

                fillServicesTable(name);

                serviceHandler.mustBeSaved();
                enableButtons();
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

      serviceTable.getColumnModel().getColumn(1).setPreferredWidth(300);

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
    // this.setSize(500, 300);

    this.setContentPane(getMainPane());
    cards.show(mainPanel, LIST_CARD);
    pack();
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
    // if (gridBagConstraints == null) {
    GridBagConstraints gbc = new GridBagConstraints();
    // }

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
      cards.show(mainPanel, LIST_CARD);
    } else {
      serviceHandler.resetService();
    }
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
