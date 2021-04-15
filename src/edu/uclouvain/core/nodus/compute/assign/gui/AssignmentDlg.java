/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.assign.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AllOrNothingAssignment;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.DynamicTimeDependentAssignment;
import edu.uclouvain.core.nodus.compute.assign.ExactMFAssignment;
import edu.uclouvain.core.nodus.compute.assign.FastMFAssignment;
import edu.uclouvain.core.nodus.compute.assign.FrankWolfeAssignment;
import edu.uclouvain.core.nodus.compute.assign.IncFrankWolfeAssignment;
import edu.uclouvain.core.nodus.compute.assign.IncrementalAssignment;
import edu.uclouvain.core.nodus.compute.assign.MSAAssignment;
import edu.uclouvain.core.nodus.compute.assign.StaticAoNTimeDependentAssignment;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.gui.ProjectPreferencesDlg;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.utils.HardwareUtils;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Dialog box used to select the type of assignment to perform and the associated parameters.
 *
 * @author Bart Jourquin
 */
public class AssignmentDlg extends EscapeDialog {

  private class ModalSplitMethodName {
    private String name;
    private String prettyName;

    public ModalSplitMethodName(String prettyName, String name) {
      this.prettyName = prettyName;
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return prettyName;
    }
  }

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -5202158951878619321L;

  /** . */
  private JRadioButton allOrNothingRadioButton = new JRadioButton();

  /** . */
  private JPanel aonTab = new JPanel();

  /** . */
  private GridBagLayout aonTabgridBagLayout = new GridBagLayout();

  /** . */
  private JButton assignButton = new JButton();

  /** . */
  private ButtonGroup assignmentButtonGroup = new ButtonGroup();

  /** . */
  private JTabbedPane assignmentTabbedPane = new JTabbedPane();

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private JComboBox<String> costFunctionsComboBox = new JComboBox<>();

  /** . */
  private JLabel costFunctionsLabel = new JLabel();

  /** . */
  private JLabel costMarkUpLabel = new JLabel();

  /** . */
  private JSpinner costMarkupSpinner = new JSpinner();

  /** . */
  private JLabel descriptionLabel = new JLabel();

  /** . */
  private JTextField descriptionTextField = new JTextField();

  /** . */
  private JCheckBox detailedPathCheckBox = new JCheckBox();

  /** . */
  private JRadioButton dynamicTimeDependentRadioButton = null;

  /** . */
  private JPanel equilibriumTab = new JPanel();

  /** . */
  private GridBagLayout equilibriumTabGridBagLayout = new GridBagLayout();

  /** . */
  private JRadioButton exactMFRadioButton = new JRadioButton();

  /** . */
  private JRadioButton fastMFRadioButton = new JRadioButton();

  /** . */
  private JRadioButton frankWolfeRadioButton = new JRadioButton();

  /** . */
  private JCheckBox highlightedAreaCheckBox = new JCheckBox();

  /** . */
  private JRadioButton incFrankWolfeRadioButton = new JRadioButton();

  /** . */
  private JRadioButton incrementalRadioButton = new JRadioButton();

  /** . */
  private JLabel iterationLabel = new JLabel();

  /** . */
  private JSpinner iterationSpinner = new JSpinner();

  /** . */
  private JCheckBox keepCheapestOnlyCheckBox = null;

  /** . */
  private JCheckBox lostPathsCheckBox = null;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JLabel maxDetourLabel = null;

  /** . */
  private JSpinner maxDetourSpinner = null;

  /** . */
  private JLabel methodLabel = new JLabel();

  /** . */
  private JComboBox<ModalSplitMethodName> modalSplitMethodComboBox = null;

  /** . */
  private JLabel modalSplitMethodLabel = null;

  /** . */
  private ModalSplitMethodName[] modalSplitMethodNames;

  /** . */
  private JRadioButton msaRadioButton = new JRadioButton();

  /** . */
  private JPanel multiflowTab = new JPanel();

  /** . */
  private GridBagLayout multiflowTabGridBagLayout = new GridBagLayout();

  /** . */
  private NodusMapPanel nodusMapPanel = null;

  /** . */
  private Vector<String> odTables = null;

  /** . */
  private JComboBox<String> odTablesComboBox = new JComboBox<>();

  /** . */
  private JLabel odTablesLabel = new JLabel(i18n.get(AssignmentDlg.class, "OD_table", "OD table:"));

  /** . */
  private JCheckBox pathsCheckBox = new JCheckBox();

  /** . */
  private JTextField postAssignmentScriptTextField = new JTextField();

  /** . */
  private JCheckBox postAssignScriptChekbox = new JCheckBox();

  /** . */
  private JLabel precisionLabel = new JLabel();

  /** . */
  private JSpinner precisionSpinner = new JSpinner();

  /** . */
  private JButton preferencesButton = null;

  /** . */
  private JLabel scenarioLabel = new JLabel();

  /** . */
  private JPanel scenarioPanel = new JPanel();

  /** . */
  private GridBagLayout scenarioPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JSpinner scenarioSpinner = new JSpinner();

  /** . */
  private RSyntaxTextArea sqlLabel = new RSyntaxTextArea();

  /** . */
  private RSyntaxTextArea sqlTextArea = new RSyntaxTextArea();

  /** . */
  private JRadioButton staticAoNTimeDependentRadioButton = null;

  /** . */
  private JLabel threadsLabel = null;

  /** . */
  private JSpinner threadsSpinner = null;

  /** . */
  private JPanel timeDependentTab = new JPanel();

  /** . */
  private GridBagLayout timeDependentTabGridBagLayout = new GridBagLayout();

  /** . */
  private final JButton saveButton = new JButton();

  /** . */
  private int nbPhysicalCores = 1;

  /** . */
  private int nbLogicalCores = 1;

  /**
   * Initializes the dialog box.
   *
   * @param mapPanel NodusMapPanel
   */
  public AssignmentDlg(NodusMapPanel mapPanel) {
    super(mapPanel.getMainFrame(), "", true);
    setTitle(i18n.get(AssignmentDlg.class, "Assignment", "Assignment"));

    // Get the number of physical and logical cores
    nbPhysicalCores = HardwareUtils.getNbPhysicalCores();
    nbLogicalCores = HardwareUtils.getNbLogicalCores();

    nodusMapPanel = mapPanel;

    initialize();

    reloadState();
    updateOptions();

    setResizable(true);
    pack();
    setLocationRelativeTo(mapPanel);
    getRootPane().setDefaultButton(assignButton);
  }

  /**
   * Convenience method used to test the existence of a scenario. If it exists, the user is asked if
   * he wants to overwrite the existent tables in the database. Returns true if the scenario number
   * is accepted.
   *
   * @param scenario The ID of the scenario to assign.
   * @return True if the scenario is accepted.
   */
  private boolean acceptScenario(int scenario) {

    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    // Build table name
    String tableName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenario;
    tableName = JDBCUtils.getCompliantIdentifier(tableName);

    // If table doesn't exit, no problem
    if (!JDBCUtils.tableExists(tableName)) {
      return true;
    }

    int answer =
        JOptionPane.showConfirmDialog(
            this,
            i18n.get(
                VirtualNetworkWriter.class,
                "Clear_existent_assignment",
                "Clear existent assignment?"),
            i18n.get(
                VirtualNetworkWriter.class, "Scenario_already_exists", "Scenario already exists"),
            JOptionPane.YES_NO_OPTION);

    if (answer != JOptionPane.YES_OPTION) {
      return false;
    }

    return true;
  }

  /**
   * Enables the GUI components relevant for the AON assignment method.
   *
   * @param e ChangeEvent
   */
  private void allOrNothingRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Retrieved the selected assignment method and launches a new thread to do the actual computing
   * work.
   *
   * @param e ActionEvent
   */
  private void assignButton_actionPerformed(ActionEvent e) {

    int nbThreads = Integer.parseInt(threadsSpinner.getValue().toString());
    if (nbThreads > nbPhysicalCores) {
      int check =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  AssignmentDlg.class,
                  "Assign_with_many_threads",
                  "Assign with more threads than physical cores?"),
              NodusC.APPNAME,
              JOptionPane.OK_CANCEL_OPTION);

      if (check == JOptionPane.CANCEL_OPTION) {

        return;
      }
    }

    AssignmentParameters ap = new AssignmentParameters(nodusMapPanel.getNodusProject());

    /*
     * Retrieve info from the GUI components to fill the "assignment parameters" that will be passed
     * to the assignment methods
     */
    ap.setWhereStmt(sqlTextArea.getText());
    ap.setODMatrix(odTablesComboBox.getSelectedItem().toString().trim());
    ap.setCostFunctions(costFunctionsComboBox.getSelectedItem().toString().trim());
    ap.setNbIterations(Integer.parseInt(iterationSpinner.getValue().toString()));
    ap.setScenario(Integer.parseInt(scenarioSpinner.getValue().toString()));
    ap.setPrecision(Float.parseFloat(precisionSpinner.getValue().toString()));
    ap.setSavePaths(pathsCheckBox.isSelected());
    ap.setDetailedPaths(detailedPathCheckBox.isSelected());
    ap.setCostMarkup(Float.parseFloat(costMarkupSpinner.getValue().toString()));
    ap.setMaxDetourRatio(Float.parseFloat(maxDetourSpinner.getValue().toString()));
    ap.setRunPostAssignmentScript(postAssignScriptChekbox.isSelected());
    ap.setPostAssignmentScript(postAssignmentScriptTextField.getText());
    ap.setKeepOnlyCheapestIntermodalPath(keepCheapestOnlyCheckBox.isSelected());
    ap.setLogLostPaths(lostPathsCheckBox.isSelected());
    ap.setThreads(Integer.parseInt(threadsSpinner.getValue().toString()));
    ap.setScenarioDescription(descriptionTextField.getText());

    ModalSplitMethodName msmn = (ModalSplitMethodName) modalSplitMethodComboBox.getSelectedItem();
    if (msmn != null) {
      ap.setModalSplitMethodName(msmn.getName());
    }
    ap.setTimeDependent(false);

    ap.setLimitedToHighlightedArea(false);
    if (highlightedAreaCheckBox.isEnabled() && highlightedAreaCheckBox.isSelected()) {
      ap.setLimitedToHighlightedArea(true);
    }

    saveState();

    // Test if scenario already exists
    if (!acceptScenario(ap.getScenario())) {
      setVisible(false);
      return;
    }

    // Launch the assignment
    int assignmentMethod = getSelectedAssignmentMethod();
    Assignment as = null;
    switch (assignmentMethod) {
      case Assignment.ALL_OR_NOTHING:
        as = new AllOrNothingAssignment(ap);
        break;
      case Assignment.MSA:
        as = new MSAAssignment(ap);
        break;
      case Assignment.INCREMENTAL:
        as = new IncrementalAssignment(ap);
        break;
      case Assignment.FRANK_WOLFE:
        as = new FrankWolfeAssignment(ap);
        break;
      case Assignment.INCREMENTAL_FRANK_WOLFE:
        as = new IncFrankWolfeAssignment(ap);
        break;
      case Assignment.FAST_MULTI_FLOW:
        as = new FastMFAssignment(ap);
        break;
      case Assignment.EXACT_MULTI_FLOW:
        as = new ExactMFAssignment(ap);
        break;
      case Assignment.AON_TIME_DEPENDENT:
        ap.setTimeDependent(true);
        as = new StaticAoNTimeDependentAssignment(ap);
        break;
      case Assignment.DYNAMIC_TIME_DEPENDENT:
        ap.setTimeDependent(true);
        as = new DynamicTimeDependentAssignment(ap);
        break;
      default:
        break;
    }
    Thread t = new Thread(as);
    t.start();

    setVisible(false);
  }

  /**
   * Just close the assignment dialog box.
   *
   * @param e ActionEvent
   */
  private void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  private void dynamicTimeDependentRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Enables the GUI components relevant for the K-Shortest paths assignment method.
   *
   * @param e ChangeEvent
   */
  private void exactMFRadioButtonStateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Enables the GUI components relevant for the fast multi-flow assignment method
   * probabilisticSpreadRadioButton.setText( "Multinomial Logit");
   *
   * @param e ChangeEvent
   */
  private void fastMFRadioButtonStateChanged(ActionEvent e) {
    updateOptions();
  }

  /** List all the cost files available in the project. */
  private void fillCostFunctionsComboBox() {

    File dir =
        new File(nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
    FileFilter fileFilter = new WildcardFileFilter("*.costs");

    File[] files = dir.listFiles(fileFilter);
    if (files == null) {
      return;
    }

    List<String> items = new LinkedList<>();

    for (File file : files) {
      items.add(file.getName());
    }
    Collections.sort(items);

    Iterator<String> it = items.iterator();
    while (it.hasNext()) {
      costFunctionsComboBox.addItem(it.next());
    }
  }

  private void fillODTablesCombo() {

    final Cursor oldCursor = getCursor();
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    cancelButton.setEnabled(false);
    assignButton.setEnabled(false);
    preferencesButton.setEnabled(false);

    final Object currentSelection = odTablesComboBox.getSelectedItem();

    // Fill the combo of OD tables
    odTablesComboBox.removeAllItems();
    odTables = ODReader.getValidODTables(nodusMapPanel.getNodusProject());
    Iterator<String> it = odTables.iterator();
    while (it.hasNext()) {
      odTablesComboBox.addItem(it.next());
    }

    if (currentSelection != null) {
      odTablesComboBox.setSelectedItem(currentSelection);
    }

    setCursor(oldCursor);
    cancelButton.setEnabled(true);
    if (odTablesComboBox.getSelectedIndex() != -1) {
      assignButton.setEnabled(true);
    }

    preferencesButton.setEnabled(true);

    // Force a redraw of the popup menu
    int n = odTablesComboBox.getMaximumRowCount();
    odTablesComboBox.setMaximumRowCount(n - 1);
    odTablesComboBox.setMaximumRowCount(n);
  }

  /**
   * Enables the GUI components relevant for the Frank-Wolfe assignment method.
   *
   * @param e ChangeEvent
   */
  private void frankWolfeRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  private JRadioButton getDynamicTimeDependentRadioButton() {
    if (dynamicTimeDependentRadioButton == null) {
      dynamicTimeDependentRadioButton = new JRadioButton();
      dynamicTimeDependentRadioButton.setText(
          i18n.get(AssignmentDlg.class, "Dynamic_method", "Dynamic method"));
      dynamicTimeDependentRadioButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              dynamicTimeDependentRadioButton_stateChanged(e);
            }
          });
    }
    return dynamicTimeDependentRadioButton;
  }

  /**
   * This method initializes jCheckBox.
   *
   * @return javax.swing.JCheckBox
   */
  private JCheckBox getKeepCheapestOnlyCheckBox() {
    if (keepCheapestOnlyCheckBox == null) {
      keepCheapestOnlyCheckBox = new JCheckBox();
      keepCheapestOnlyCheckBox.setText(
          i18n.get(
              AssignmentDlg.class,
              "Keep_only_cheapest_intermodal_path",
              "Keep only cheapest intermodal path"));
      keepCheapestOnlyCheckBox.setOpaque(false);
    }
    return keepCheapestOnlyCheckBox;
  }

  /**
   * This method initializes lostPathsCheckBox.
   *
   * @return javax.swing.JCheckBox
   */
  private JCheckBox getLostPathsCheckBox() {
    if (lostPathsCheckBox == null) {
      lostPathsCheckBox = new JCheckBox();
      lostPathsCheckBox.setText(i18n.get(AssignmentDlg.class, "Log_lost_paths", "Log lost paths"));
      lostPathsCheckBox.setOpaque(false);
    }
    return lostPathsCheckBox;
  }

  /**
   * This method initializes costMarkupSpinner1.
   *
   * @return javax.swing.JSpinner
   */
  private JSpinner getMaxDetourSpinner() {
    if (maxDetourSpinner == null) {
      maxDetourSpinner = new JSpinner();
    }
    return maxDetourSpinner;
  }

  /**
   * This method initializes ModalSplitMethodComboBox.
   *
   * @return javax.swing.JComboBox
   */
  private JComboBox<ModalSplitMethodName> getModalSplitMethodComboBox() {
    if (modalSplitMethodComboBox == null) {

      // Retrieve the names of the available modal split methods
      LinkedList<ModalSplitMethod> ll = ModalSplitMethodsLoader.getAvailableModalSplitMethods();
      Iterator<ModalSplitMethod> it = ll.iterator();

      modalSplitMethodNames = new ModalSplitMethodName[ll.size()];
      for (int i = 0; i < modalSplitMethodNames.length; i++) {
        ModalSplitMethod c = it.next();
        modalSplitMethodNames[i] = new ModalSplitMethodName(c.getPrettyName(), c.getName());
      }

      modalSplitMethodComboBox = new JComboBox<ModalSplitMethodName>();
      modalSplitMethodComboBox.setModel(new DefaultComboBoxModel<>(modalSplitMethodNames));
    }
    return modalSplitMethodComboBox;
  }

  /**
   * Get the pretty name of a modal split method name.
   *
   * @param name The short name of the method.
   * @return ModalSplitMethodName
   */
  private ModalSplitMethodName getModalSplitPrettyName(String name) {

    if (name == null) {
      return null;
    }

    for (ModalSplitMethodName modalSplitMethodName : modalSplitMethodNames) {
      if (modalSplitMethodName.getName().equals(name)) {
        return modalSplitMethodName;
      }
    }

    System.err.println("'" + name + "' is not a valid modal split method name");
    return null;
  }

  /**
   * This method initializes jButton.
   *
   * @return javax.swing.JButton
   */
  private JButton getPreferencesButton() {
    if (preferencesButton == null) {
      preferencesButton = new JButton();
      preferencesButton.setText(i18n.get(AssignmentDlg.class, "Preferences", "Preferences"));
      preferencesButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              JDialog dlg = new ProjectPreferencesDlg(nodusMapPanel.getNodusProject());
              dlg.setVisible(true);
              reloadState();
            }
          });
    }
    return preferencesButton;
  }

  private int getSelectedAssignmentMethod() {
    int assignmentMethod = 0;

    // All or Nothing
    if (allOrNothingRadioButton.isSelected()) {
      assignmentMethod = 0;
    }

    // MSA
    if (msaRadioButton.isSelected()) {
      assignmentMethod = 1;
    }

    // Incremental
    if (incrementalRadioButton.isSelected()) {
      assignmentMethod = 2;
    }

    // Frank-Wolfe
    if (frankWolfeRadioButton.isSelected()) {
      assignmentMethod = 3;
    }

    // Incremental et FW
    if (incFrankWolfeRadioButton.isSelected()) {
      assignmentMethod = 4;
    }

    // Fast multi-flow
    if (fastMFRadioButton.isSelected()) {
      assignmentMethod = 5;
    }

    // Exact multi-flow
    if (exactMFRadioButton.isSelected()) {
      assignmentMethod = 6;
    }

    // Time dependent AoN
    if (staticAoNTimeDependentRadioButton.isSelected()) {
      assignmentMethod = 7;
    }

    // Dynamic time dependent
    if (dynamicTimeDependentRadioButton.isSelected()) {
      assignmentMethod = 8;
    }
    return assignmentMethod;
  }

  /**
   * This method initializes Static AON time dependent.
   *
   * @return javax.swing.JRadioButton
   */
  private JRadioButton getStaticAoNTimeDependentRadioButton() {
    if (staticAoNTimeDependentRadioButton == null) {
      staticAoNTimeDependentRadioButton = new JRadioButton();
      staticAoNTimeDependentRadioButton.setText(
          i18n.get(
              AssignmentDlg.class, "Static_All_Or_Nothing_method", "Static All Or Nothing method"));
      staticAoNTimeDependentRadioButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              staticAoNTimeDependentRadioButton_stateChanged(e);
            }
          });
    }
    return staticAoNTimeDependentRadioButton;
  }

  /**
   * Enables the GUI components relevant for the incremental + Frank-Wolfe assignment method.
   *
   * @param e ChangeEvent
   */
  private void incFrankWolfeRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Enables the GUI components relevant for the incremental assignment method.
   *
   * @param e ChangeEvent
   */
  private void incrementalRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Initializes the GUI components of the dialog box.
   *
   * @throws Exception On error
   */
  private void initialize() {

    GridBagConstraints staticAoNTimeDependentGridBagConstraints = new GridBagConstraints();
    staticAoNTimeDependentGridBagConstraints.gridx = 0;
    staticAoNTimeDependentGridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    staticAoNTimeDependentGridBagConstraints.insets = new Insets(5, 5, 5, 0);
    staticAoNTimeDependentGridBagConstraints.weightx = 0.0;
    staticAoNTimeDependentGridBagConstraints.weighty = 0.0;
    staticAoNTimeDependentGridBagConstraints.gridy = 0;

    GridBagConstraints dynamicTimeDependentGridBagConstraints = new GridBagConstraints();
    dynamicTimeDependentGridBagConstraints.weighty = 0.1;
    dynamicTimeDependentGridBagConstraints.weightx = 0.1;
    dynamicTimeDependentGridBagConstraints.gridx = 0;
    dynamicTimeDependentGridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    dynamicTimeDependentGridBagConstraints.insets = new Insets(5, 5, 5, 0);

    dynamicTimeDependentGridBagConstraints.gridy = 1;

    GridBagConstraints exactMFRadioButtongridBagConstraints =
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 15, 5),
            0,
            0);
    exactMFRadioButtongridBagConstraints.weightx = 0.1;
    exactMFRadioButtongridBagConstraints.weighty = 0.1;

    GridBagConstraints modalSplitMethodLabelgridBagConstraints = new GridBagConstraints();
    modalSplitMethodLabelgridBagConstraints.weightx = 0.1;
    modalSplitMethodLabelgridBagConstraints.anchor = GridBagConstraints.SOUTHWEST;
    modalSplitMethodLabelgridBagConstraints.gridy = 2;
    modalSplitMethodLabelgridBagConstraints.insets = new Insets(0, 10, 0, 5);
    modalSplitMethodLabelgridBagConstraints.gridx = 0;

    GridBagConstraints modalSplitMethodgridBagConstraints = new GridBagConstraints();
    modalSplitMethodgridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    modalSplitMethodgridBagConstraints.weightx = 0.1;
    modalSplitMethodgridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    modalSplitMethodgridBagConstraints.gridy = 3;
    modalSplitMethodgridBagConstraints.insets = new Insets(0, 5, 0, 5);
    modalSplitMethodgridBagConstraints.gridx = 0;

    GridBagConstraints costMarkUpLabelgridBagConstraints =
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 5),
            0,
            0);
    costMarkUpLabelgridBagConstraints.gridx = 1;
    costMarkUpLabelgridBagConstraints.fill = GridBagConstraints.NONE;
    costMarkUpLabelgridBagConstraints.insets = new Insets(5, 5, 0, 0);
    costMarkUpLabelgridBagConstraints.gridy = 3;

    GridBagConstraints iterationLabelConstraints =
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.1,
            0.1,
            GridBagConstraints.SOUTH,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 2, 5),
            0,
            0);
    iterationLabelConstraints.insets = new Insets(5, 5, 2, 5);

    GridBagConstraints scenarioSpinnerConstraints =
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTH,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 2, 5),
            0,
            0);
    scenarioSpinnerConstraints.insets = new Insets(0, 5, 5, 5);

    GridBagConstraints threadsLabelgridBagConstraints = new GridBagConstraints();
    threadsLabelgridBagConstraints.gridx = 0;
    threadsLabelgridBagConstraints.insets = new Insets(5, 5, 2, 5);
    threadsLabelgridBagConstraints.gridy = 4;

    threadsLabel = new JLabel();
    threadsLabel.setHorizontalAlignment(SwingConstants.CENTER);
    threadsLabel.setText(i18n.get(AssignmentDlg.class, "Threads", "Threads"));

    GridBagConstraints threadsSpinnerConstraints = new GridBagConstraints();
    threadsSpinnerConstraints.fill = GridBagConstraints.HORIZONTAL;
    threadsSpinnerConstraints.gridx = 0;
    threadsSpinnerConstraints.gridy = 5;
    threadsSpinnerConstraints.weightx = 0.5;
    threadsSpinnerConstraints.insets = new Insets(0, 5, 5, 5);
    threadsSpinner = new JSpinner();

    GridBagConstraints scenarioPanelConstraints =
        new GridBagConstraints(
            3,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 17),
            0,
            0);
    scenarioPanelConstraints.anchor = GridBagConstraints.NORTH;

    GridBagConstraints maxDetourSpinnerConstraints = new GridBagConstraints();
    maxDetourSpinnerConstraints.gridx = 2;
    maxDetourSpinnerConstraints.anchor = GridBagConstraints.EAST;
    maxDetourSpinnerConstraints.insets = new Insets(0, 0, 0, 5);
    maxDetourSpinnerConstraints.gridy = 4;

    GridBagConstraints maxDetourLabelConstraints = new GridBagConstraints();
    maxDetourLabelConstraints.gridx = 1;
    maxDetourLabelConstraints.insets = new Insets(5, 5, 5, 0);
    maxDetourLabelConstraints.anchor = GridBagConstraints.EAST;
    maxDetourLabelConstraints.gridy = 4;
    maxDetourLabel = new JLabel();
    maxDetourLabel.setText(i18n.get(AssignmentDlg.class, "Max_detour", "Max detour"));

    modalSplitMethodLabel = new JLabel();
    modalSplitMethodLabel.setText(
        i18n.get(AssignmentDlg.class, "ModalSplitMethod", "Modal split method:"));

    GridBagConstraints lostPathsCheckBoxConstraints = new GridBagConstraints();
    lostPathsCheckBoxConstraints.gridx = 0;
    lostPathsCheckBoxConstraints.anchor = GridBagConstraints.WEST;
    lostPathsCheckBoxConstraints.insets = new Insets(10, 10, 5, 5);
    lostPathsCheckBoxConstraints.gridy = 6;

    GridBagConstraints costMarkupSpinnerConstraints =
        new GridBagConstraints(
            1,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 0, 5),
            0,
            0);
    costMarkupSpinnerConstraints.gridx = 2;

    GridBagConstraints forceModalSplitCheckBoxConstraints =
        new GridBagConstraints(
            0,
            4,
            1,
            1,
            0.1,
            0.1,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    forceModalSplitCheckBoxConstraints.gridx = 0;
    forceModalSplitCheckBoxConstraints.gridy = 8;

    GridBagConstraints keepCheapestOnlyCheckBoxConstraints = new GridBagConstraints();
    keepCheapestOnlyCheckBoxConstraints.gridx = 0;
    keepCheapestOnlyCheckBoxConstraints.insets = new Insets(5, 5, 5, 5);
    keepCheapestOnlyCheckBoxConstraints.anchor = GridBagConstraints.WEST;
    keepCheapestOnlyCheckBoxConstraints.gridy = 4;

    GridBagConstraints highlightedAreaCheckBoxConstraints = new GridBagConstraints();
    highlightedAreaCheckBoxConstraints.gridx = 2;
    highlightedAreaCheckBoxConstraints.insets = new Insets(10, 10, 5, 10);
    highlightedAreaCheckBoxConstraints.anchor = GridBagConstraints.WEST;
    highlightedAreaCheckBoxConstraints.gridy = 6;

    GridBagConstraints pathsCheckBoxConstraints =
        new GridBagConstraints(
            0,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 10, 0, 5),
            0,
            0);
    pathsCheckBoxConstraints.insets = new Insets(0, 10, 5, 5);
    pathsCheckBoxConstraints.gridy = 7;
    pathsCheckBoxConstraints.anchor = GridBagConstraints.WEST;

    GridBagConstraints detailedCheckBoxConstraints =
        new GridBagConstraints(
            2,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 10, 10, 10),
            0,
            0);
    detailedCheckBoxConstraints.insets = new Insets(0, 10, 5, 10);
    detailedCheckBoxConstraints.gridy = 7;

    GridBagConstraints postAssignmentScriptTextFieldConstraints = new GridBagConstraints();
    postAssignmentScriptTextFieldConstraints.gridwidth = 3;
    postAssignmentScriptTextFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
    postAssignmentScriptTextFieldConstraints.gridy = 9;
    postAssignmentScriptTextFieldConstraints.anchor = GridBagConstraints.NORTHWEST;
    postAssignmentScriptTextFieldConstraints.insets = new Insets(0, 10, 5, 10);
    postAssignmentScriptTextFieldConstraints.gridx = 0;

    GridBagConstraints postAssignScriptChekboxConstraints = new GridBagConstraints();
    postAssignScriptChekboxConstraints.gridx = 0;
    postAssignScriptChekboxConstraints.anchor = GridBagConstraints.SOUTHWEST;
    postAssignScriptChekboxConstraints.insets = new Insets(0, 10, 5, 5);
    postAssignScriptChekboxConstraints.gridy = 8;
    GridBagConstraints fastMFRadioButtonBagConstraints =
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.1,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    fastMFRadioButtonBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    fastMFRadioButtonBagConstraints.weightx = 0.1;
    mainPanelGridBagLayout.rowWeights =
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    mainPanelGridBagLayout.columnWeights = new double[] {1.0, 0.0, 1.0, 0.0};
    mainPanel.setLayout(mainPanelGridBagLayout);

    // SwingTweaks.textPaneNimbusTweak(sqlLabel);
    sqlLabel.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    sqlLabel.setHighlightCurrentLine(false);
    sqlLabel.setEditable(false);
    sqlLabel.setBackground(mainPanel.getBackground());

    cancelButton.setText(i18n.get(AssignmentDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });

    sqlTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    sqlTextArea.setHighlightCurrentLine(false);

    allOrNothingRadioButton.setSelected(true);
    allOrNothingRadioButton.setText(
        i18n.get(AssignmentDlg.class, "All_or_Nothing", "All or Nothing"));
    allOrNothingRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            allOrNothingRadioButton_stateChanged(e);
          }
        });

    msaRadioButton.setText(
        i18n.get(AssignmentDlg.class, "Successive_Averages", "Successive Averages"));
    msaRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            msaRadioButton_stateChanged(e);
          }
        });

    incrementalRadioButton.setText(i18n.get(AssignmentDlg.class, "Incremental", "Incremental"));
    incrementalRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            incrementalRadioButton_stateChanged(e);
          }
        });

    frankWolfeRadioButton.setText(i18n.get(AssignmentDlg.class, "Frank_Wolfe", "Frank-Wolfe"));
    frankWolfeRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            frankWolfeRadioButton_stateChanged(e);
          }
        });

    incFrankWolfeRadioButton.setText(
        i18n.get(AssignmentDlg.class, "Incremental_Frank_Wolfe", "Incremental + Frank-Wolfe"));
    incFrankWolfeRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            incFrankWolfeRadioButton_stateChanged(e);
          }
        });

    exactMFRadioButton.setText(
        i18n.get(AssignmentDlg.class, "Multi_flow_Exact_method", "Multi-flow (Exact method)"));
    exactMFRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            exactMFRadioButtonStateChanged(e);
          }
        });

    fastMFRadioButton.setText(
        i18n.get(AssignmentDlg.class, "Multi_flow_Fast_method", "Multi-flow"));
    fastMFRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            fastMFRadioButtonStateChanged(e);
          }
        });

    pathsCheckBox.setText(i18n.get(AssignmentDlg.class, "Save_paths", "Save paths"));
    pathsCheckBox.setOpaque(false);
    detailedPathCheckBox.setText(i18n.get(AssignmentDlg.class, "Detailed_paths", "Detailed paths"));
    detailedPathCheckBox.setOpaque(false);
    pathsCheckBox.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent evt) {
            updateOptions();
          }
        });

    postAssignScriptChekbox.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent evt) {
            updateOptions();
          }
        });

    precisionLabel.setText(i18n.get(AssignmentDlg.class, "Precision", "Precision"));
    scenarioLabel.setHorizontalAlignment(SwingConstants.CENTER);
    scenarioLabel.setBorder(new LineBorder(Color.GRAY));
    scenarioLabel.setText(i18n.get(AssignmentDlg.class, "Scenario", "Scenario"));
    scenarioLabel.setOpaque(true);

    scenarioPanel.setLayout(scenarioPanelGridBagLayout);
    scenarioPanel.setBorder(new LineBorder(new Color(0, 0, 0), 1, true));

    aonTab.setLayout(aonTabgridBagLayout);
    equilibriumTab.setLayout(equilibriumTabGridBagLayout);

    assignmentTabbedPane.putClientProperty("pgs.isSubTab", Boolean.TRUE);
    assignmentTabbedPane.putClientProperty("pgs.isButtonStyle", Boolean.TRUE);

    multiflowTab.setLayout(multiflowTabGridBagLayout);

    timeDependentTab.setLayout(timeDependentTabGridBagLayout);

    timeDependentTab.add(
        getStaticAoNTimeDependentRadioButton(), staticAoNTimeDependentGridBagConstraints);
    timeDependentTab.add(
        getDynamicTimeDependentRadioButton(), dynamicTimeDependentGridBagConstraints);

    methodLabel.setText(
        MessageFormat.format(
            "{0}:", i18n.get(AssignmentDlg.class, "Assignment_method", "Assignment method")));

    costMarkUpLabel.setText(i18n.get(AssignmentDlg.class, "Cost_markup", "Cost markup (%)"));
    iterationLabel.setHorizontalAlignment(SwingConstants.CENTER);

    iterationLabel.setText(i18n.get(AssignmentDlg.class, "Iterations", "Iterations"));

    GridBagConstraints costFunctionsComboBoxConstraints = new GridBagConstraints();
    costFunctionsComboBoxConstraints.anchor = GridBagConstraints.NORTHWEST;
    costFunctionsComboBoxConstraints.insets = new Insets(0, 10, 5, 10);
    costFunctionsComboBoxConstraints.fill = GridBagConstraints.HORIZONTAL;
    costFunctionsComboBoxConstraints.gridx = 0;
    costFunctionsComboBoxConstraints.gridy = 3;
    fillCostFunctionsComboBox();

    GridBagConstraints odTableLabelConstraint = new GridBagConstraints();
    odTableLabelConstraint.anchor = GridBagConstraints.SOUTHWEST;
    odTableLabelConstraint.insets = new Insets(0, 15, 5, 5);
    odTableLabelConstraint.gridx = 2;
    odTableLabelConstraint.gridy = 2;
    mainPanel.add(odTablesLabel, odTableLabelConstraint);

    GridBagConstraints costFunctionsLabelConstraints = new GridBagConstraints();
    costFunctionsLabelConstraints.anchor = GridBagConstraints.SOUTHWEST;
    costFunctionsLabelConstraints.insets = new Insets(0, 15, 5, 5);
    costFunctionsLabelConstraints.gridx = 0;
    costFunctionsLabelConstraints.gridy = 2;
    mainPanel.add(costFunctionsLabel, costFunctionsLabelConstraints);
    costFunctionsLabel.setText(i18n.get(AssignmentDlg.class, "Cost_functions", "Cost functions :"));
    mainPanel.add(costFunctionsComboBox, costFunctionsComboBoxConstraints);

    GridBagConstraints odTablesComboBoxConstraints = new GridBagConstraints();
    odTablesComboBoxConstraints.insets = new Insets(0, 10, 5, 10);
    odTablesComboBoxConstraints.fill = GridBagConstraints.HORIZONTAL;
    odTablesComboBoxConstraints.gridx = 2;
    odTablesComboBoxConstraints.gridy = 3;
    mainPanel.add(odTablesComboBox, odTablesComboBoxConstraints);

    // Because filling the combo with available valid OD tables
    // can take a while, fill it on request only.
    // Several listeners are needed
    odTablesComboBox.addMouseListener(
        new java.awt.event.MouseListener() {
          @Override
          public void mouseClicked(MouseEvent e) {
            // Must be overridden
          }

          @Override
          public void mouseEntered(MouseEvent e) {
            // Must be overridden
          }

          @Override
          public void mouseExited(MouseEvent e) {
            // Must be overridden
          }

          @Override
          public void mousePressed(MouseEvent e) {
            if (odTables == null) {
              fillODTablesCombo();
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            // Must be overridden
          }
        });

    odTablesComboBox.addPopupMenuListener(
        new PopupMenuListener() {

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            if (odTables == null) {
              fillODTablesCombo();
            }
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            // Must be overridden
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            // Must be overridden
          }
        });

    odTablesComboBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {

            if (odTables == null) {
              fillODTablesCombo();
            }

            e.getSource();
            if (odTablesComboBox.getSelectedItem() != null) {
              String odTableName =
                  JDBCUtils.getCompliantIdentifier(odTablesComboBox.getSelectedItem().toString());
              sqlLabel.setText("SELECT * FROM " + odTableName + " WHERE");
              if (costFunctionsComboBox.getSelectedIndex() != -1
                  || odTablesComboBox.getSelectedIndex() != -1) {
                assignButton.setEnabled(true);
              }
            }
          }
        });

    mainPanel.add(
        sqlLabel,
        new GridBagConstraints(
            0,
            4,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 10, 5, 10),
            0,
            0));
    mainPanel.add(
        sqlTextArea,
        new GridBagConstraints(
            0,
            5,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.BOTH,
            new Insets(0, 10, 5, 10),
            0,
            0));
    setContentPane(mainPanel);

    mainPanel.add(pathsCheckBox, pathsCheckBoxConstraints);
    scenarioPanel.add(
        scenarioLabel,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 2, 5),
            0,
            0));
    mainPanel.add(scenarioPanel, scenarioPanelConstraints);
    mainPanel.add(
        assignmentTabbedPane,
        new GridBagConstraints(
            0,
            1,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.BOTH,
            new Insets(0, 10, 5, 5),
            0,
            0));
    assignmentTabbedPane.add(aonTab, i18n.get(AssignmentDlg.class, "A_or_N", "All or Nothing"));

    aonTab.add(
        allOrNothingRadioButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.1,
            0.1,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 0),
            0,
            0));
    assignmentTabbedPane.add(
        equilibriumTab, i18n.get(AssignmentDlg.class, "Equilibrium", "Equilibrium"));

    equilibriumTab.add(
        incrementalRadioButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.1,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    equilibriumTab.add(
        msaRadioButton,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.1,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    /*
     * The Frank-Wolfe based algorithms are buggy since ... ? As equilibrium assignments
     * are not really useful in the context of non urban transport, they are disabled
     * since Nodus 8.0
     */
    //    equilibriumTab.add(
    //        frankWolfeRadioButton,
    //        new GridBagConstraints(
    //            0,
    //            2,
    //            1,
    //            1,
    //            0.1,
    //            0.0,
    //            GridBagConstraints.NORTHWEST,
    //            GridBagConstraints.NONE,
    //            new Insets(5, 5, 5, 5),
    //            0,
    //            0));
    //    equilibriumTab.add(
    //        incFrankWolfeRadioButton,
    //        new GridBagConstraints(
    //            0,
    //            3,
    //            1,
    //            1,
    //            0.1,
    //            0.0,
    //            GridBagConstraints.NORTHWEST,
    //            GridBagConstraints.NONE,
    //            new Insets(5, 5, 5, 5),
    //            0,
    //            0));

    assignmentTabbedPane.add(
        multiflowTab, i18n.get(AssignmentDlg.class, "Multi_flow", "Multi-flow"));

    assignmentTabbedPane.add(
        timeDependentTab, i18n.get(AssignmentDlg.class, "Time_dependent", "Time dependent"));
    mainPanel.add(
        methodLabel,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 15, 5, 5),
            0,
            0));

    postAssignScriptChekbox.setText(
        i18n.get(
            AssignmentDlg.class,
            "Launch_post_assignment_script",
            "Launch post-assignment script:"));

    postAssignScriptChekbox.setOpaque(false);
    postAssignmentScriptTextField.setText("");
    mainPanel.add(detailedPathCheckBox, detailedCheckBoxConstraints);

    mainPanel.add(postAssignScriptChekbox, postAssignScriptChekboxConstraints);
    highlightedAreaCheckBox.setText(
        i18n.get(AssignmentDlg.class, "Limit_to_highlighted_area", "Limit to highlighted area"));
    highlightedAreaCheckBox.setOpaque(false);

    GridBagConstraints descriptionLabelConstraints = new GridBagConstraints();
    descriptionLabelConstraints.anchor = GridBagConstraints.SOUTHWEST;
    descriptionLabelConstraints.insets = new Insets(10, 10, 5, 5);
    descriptionLabelConstraints.gridx = 0;
    descriptionLabelConstraints.gridy = 10;
    descriptionLabel.setHorizontalAlignment(SwingConstants.LEFT);
    descriptionLabel.setText(i18n.get(AssignmentDlg.class, "Description", "Description:"));
    mainPanel.add(descriptionLabel, descriptionLabelConstraints);

    GridBagConstraints descriptionTextAreaConstraints = new GridBagConstraints();
    descriptionTextAreaConstraints.gridwidth = 4;
    descriptionTextAreaConstraints.insets = new Insets(0, 10, 10, 10);
    descriptionTextAreaConstraints.fill = GridBagConstraints.HORIZONTAL;
    descriptionTextAreaConstraints.gridx = 0;
    descriptionTextAreaConstraints.gridy = 11;
    mainPanel.add(descriptionTextField, descriptionTextAreaConstraints);

    GridBagConstraints preferencesButtonConstraints = new GridBagConstraints();
    preferencesButtonConstraints.anchor = GridBagConstraints.SOUTHWEST;
    preferencesButtonConstraints.gridx = 0;
    preferencesButtonConstraints.insets = new Insets(5, 10, 10, 10);
    preferencesButtonConstraints.gridy = 12;
    mainPanel.add(getPreferencesButton(), preferencesButtonConstraints);

    GridBagConstraints saveButtonConstraints = new GridBagConstraints();
    saveButtonConstraints.insets = new Insets(5, 5, 10, 5);
    saveButtonConstraints.gridx = 1;
    saveButtonConstraints.gridy = 12;
    saveButton.setText(i18n.get(AssignmentDlg.class, "Save", "Save"));
    saveButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            saveState();
            nodusMapPanel.updateScenarioComboBox(false);
          }
        });
    mainPanel.add(saveButton, saveButtonConstraints);

    GridBagConstraints assignButtonConstraints =
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 10, 10, 5),
            0,
            0);
    assignButtonConstraints.gridx = 2;
    assignButtonConstraints.anchor = GridBagConstraints.SOUTHEAST;
    assignButtonConstraints.insets = new Insets(5, 5, 10, 5);
    assignButtonConstraints.gridy = 12;
    mainPanel.add(assignButton, assignButtonConstraints);

    assignButton.setText(i18n.get(AssignmentDlg.class, "Assign", "Assign"));
    assignButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            assignButton_actionPerformed(e);
          }
        });

    mainPanel.add(postAssignmentScriptTextField, postAssignmentScriptTextFieldConstraints);
    mainPanel.add(highlightedAreaCheckBox, highlightedAreaCheckBoxConstraints);
    mainPanel.add(getLostPathsCheckBox(), lostPathsCheckBoxConstraints);
    multiflowTab.add(fastMFRadioButton, fastMFRadioButtonBagConstraints);
    multiflowTab.add(exactMFRadioButton, exactMFRadioButtongridBagConstraints);
    multiflowTab.add(costMarkUpLabel, costMarkUpLabelgridBagConstraints);
    multiflowTab.add(costMarkupSpinner, costMarkupSpinnerConstraints);

    multiflowTab.add(getKeepCheapestOnlyCheckBox(), keepCheapestOnlyCheckBoxConstraints);
    multiflowTab.add(maxDetourLabel, maxDetourLabelConstraints);
    multiflowTab.add(getMaxDetourSpinner(), maxDetourSpinnerConstraints);

    multiflowTab.add(modalSplitMethodLabel, modalSplitMethodLabelgridBagConstraints);
    multiflowTab.add(getModalSplitMethodComboBox(), modalSplitMethodgridBagConstraints);
    final SpinnerListModel iterationSpinnerModel =
        new SpinnerListModel(
            new String[] {
              "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
              "17", "18", "19", "20", "25", "30", "40", "50", "60", "70", "80", "90", "99"
            });
    final SpinnerListModel precisionSpinnerModel =
        new SpinnerListModel(
            new String[] {"0.05", "0.06", "0.07", "0.08", "0.09", "0.1", "0.15", "0.2"});

    final SpinnerListModel costMarkUpSpinnerModel =
        new SpinnerListModel(
            new String[] {
              "0.05", "0.10", "0.15", "0.20", "0.25", "0.30", "0.40", "0.50", "0.60", "0.70",
              "0.80", "0.90"
            });
    final SpinnerListModel maxDetourSpinnerModel =
        new SpinnerListModel(
            new String[] {
              "0.00", "1.10", "1.25", "1.50", "1.75", "2.0", "2.50", "3.0", "5.0", "10.0"
            });

    int min = 0;
    int max = NodusC.MAXSCENARIOS - 1;
    int defScenario = nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_SCENARIO, 0);

    SpinnerModel model = new SpinnerNumberModel(defScenario, min, max, 1);
    scenarioSpinner.setModel(model);
    scenarioSpinner.setOpaque(false);
    scenarioSpinner.addChangeListener(
        new ChangeListener() {
          // If a new scenario is chosen, try to reload the parameters
          // of a previous assignment for it
          @Override
          public void stateChanged(ChangeEvent e) {
            /*if (odTables == null) {
              fillODTablesCombo();
            }*/
            reloadState();
          }
        });

    scenarioPanel.add(scenarioSpinner, scenarioSpinnerConstraints);
    scenarioPanel.add(iterationLabel, iterationLabelConstraints);
    scenarioPanel.add(
        iterationSpinner,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 5, 5),
            0,
            0));

    scenarioPanel.add(threadsSpinner, threadsSpinnerConstraints);
    scenarioPanel.add(threadsLabel, threadsLabelgridBagConstraints);
    equilibriumTab.add(
        precisionLabel,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.5,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 5, 5),
            0,
            0));
    equilibriumTab.add(
        precisionSpinner,
        new GridBagConstraints(
            1,
            3,
            1,
            1,
            0.0,
            0.5,
            GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 0, 5, 5),
            0,
            0));

    iterationSpinner.setModel(iterationSpinnerModel);
    iterationSpinner.addChangeListener(
        new ChangeListener() {
          // Cost-markup is useful only if more than one path is computed
          // for each mode/means combination
          @Override
          public void stateChanged(ChangeEvent evt) {
            if (iterationSpinner.getValue().toString().equals("1")) {
              costMarkupSpinner.setEnabled(false);
              costMarkUpLabel.setEnabled(false);
            } else {
              costMarkupSpinner.setEnabled(true);
              costMarkUpLabel.setEnabled(true);
            }
          }
        });

    precisionSpinner.setModel(precisionSpinnerModel);
    costMarkupSpinner.setModel(costMarkUpSpinnerModel);
    maxDetourSpinner.setModel(maxDetourSpinnerModel);

    assignmentButtonGroup.add(allOrNothingRadioButton);
    assignmentButtonGroup.add(msaRadioButton);
    assignmentButtonGroup.add(frankWolfeRadioButton);
    assignmentButtonGroup.add(incFrankWolfeRadioButton);
    assignmentButtonGroup.add(exactMFRadioButton);
    assignmentButtonGroup.add(fastMFRadioButton);
    assignmentButtonGroup.add(incrementalRadioButton);
    assignmentButtonGroup.add(staticAoNTimeDependentRadioButton);

    assignmentButtonGroup.add(dynamicTimeDependentRadioButton);

    GridBagConstraints closeButtonConstraints =
        new GridBagConstraints(
            3,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 10, 10),
            0,
            0);
    closeButtonConstraints.anchor = GridBagConstraints.SOUTHEAST;
    closeButtonConstraints.insets = new Insets(0, 5, 10, 10);
    closeButtonConstraints.gridy = 12;
    mainPanel.add(cancelButton, closeButtonConstraints);
  }

  /**
   * Enables the GUI components relevant for the MSA assignment method.
   *
   * @param e ChangeEvent
   */
  private void msaRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /**
   * Restore the state of the components. Try to get those that were already stored for a scenario
   */
  private void reloadState() {

    // Suffix to test if a scenario specific value exists
    String scenarioSuffix = scenarioSpinner.getValue().toString();

    String defValue =
        nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTNAME)
            + NodusC.SUFFIX_VNET;
    String vnetTableName =
        nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_VNET_TABLE, defValue)
            + scenarioSuffix;

    if (JDBCUtils.tableExists(vnetTableName)) {
      scenarioLabel.setBackground(Color.orange);
    } else {
      scenarioLabel.setBackground(Color.green);
    }

    // Is there an OD table already associated with this scenario
    String odTableName =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_OD_TABLE + scenarioSuffix, null);
    if (odTableName == null) {
      odTableName = nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_OD_TABLE, null);
    }
    if (odTableName != null) {
      // The list of OD tables could not already be loaded
      if (odTables != null) {
        // System.err.println(odTables.indexOf(odTableName));
        odTablesComboBox.setSelectedItem(odTableName);
      } else {
        if (JDBCUtils.tableExists(odTableName)) {
          if (odTablesComboBox.getItemCount() > 0) {
            odTablesComboBox.removeAll();
          }
          odTablesComboBox.addItem(odTableName);
          odTablesComboBox.setSelectedItem(odTableName);
        }
      }
    }

    // Cost functions
    String costFunctions =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenarioSuffix, null);
    if (costFunctions == null) {
      String defaultValue =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTNAME)
              + NodusC.TYPE_COSTS;
      costFunctions =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_COST_FUNCTIONS, defaultValue);
    }
    costFunctionsComboBox.setSelectedItem(costFunctions);

    // Assignment tab
    int intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_TAB + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue = nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_TAB, 0);
    }
    assignmentTabbedPane.setSelectedIndex(intValue);

    // Assignment method
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_METHOD + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue = nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_METHOD, 0);
    }
    selectMethod(intValue);

    // Nb iterations
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_NB_ITERATIONS, 1);
    }
    setSpinnerDoubleValue(iterationSpinner, intValue);

    // Assignment precision
    double doubleValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_PRECISION + scenarioSuffix, -1.0);
    if (doubleValue == -1.0) {
      doubleValue =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_PRECISION, 0.05);
    }
    setSpinnerDoubleValue(precisionSpinner, doubleValue);

    // Cost mark-up
    doubleValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_COST_MARKUP + scenarioSuffix, -1.0);
    if (doubleValue == -1.0) {
      doubleValue = nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_COST_MARKUP, 0.10);
    }
    setSpinnerDoubleValue(costMarkupSpinner, doubleValue);

    // Max detour
    doubleValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_MAX_DETOUR + scenarioSuffix, -1.0);
    if (doubleValue == -1.0) {
      nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_MAX_DETOUR, 0.00);
    }
    setSpinnerDoubleValue(maxDetourSpinner, doubleValue);

    // Modal split method
    String stringValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + scenarioSuffix, null);
    if (stringValue == null) {
      stringValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD, null);
    }

    modalSplitMethodComboBox.setSelectedItem(getModalSplitPrettyName(stringValue));

    // Save path
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS, 0);
    }
    if (intValue == 1) {
      pathsCheckBox.setSelected(true);
    } else {
      pathsCheckBox.setSelected(false);
    }

    // Save detailed paths
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS, 0);
    }
    if (intValue == 1) {
      detailedPathCheckBox.setSelected(true);
    } else {
      detailedPathCheckBox.setSelected(false);
    }

    // Keep cheapest intermodal path only
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY, 0);
    }
    if (intValue == 1) {
      keepCheapestOnlyCheckBox.setSelected(true);
    } else {
      keepCheapestOnlyCheckBox.setSelected(false);
    }

    // Run post assignment script
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(
                NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + scenarioSuffix, -1);
    if (intValue == -1) {
      nodusMapPanel
          .getNodusProject()
          .getLocalProperty(NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT, 0);
    }
    if (intValue == 1) {
      postAssignScriptChekbox.setSelected(true);
    } else {
      postAssignScriptChekbox.setSelected(false);
    }

    // Log lost paths
    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS, 0);
    }
    if (intValue == 1) {
      lostPathsCheckBox.setSelected(true);
    } else {
      lostPathsCheckBox.setSelected(false);
    }

    // Limit assignment to highlighted area
    if (nodusMapPanel.isHighlightedAreaLayerVisible()) {
      highlightedAreaCheckBox.setEnabled(true);
    } else {
      highlightedAreaCheckBox.setEnabled(false);
    }

    intValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(
                NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + scenarioSuffix, -1);
    if (intValue == -1) {
      intValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA, 0);
    }
    if (intValue == 1) {
      highlightedAreaCheckBox.setSelected(true);
    } else {
      highlightedAreaCheckBox.setSelected(false);
    }

    // Post assignment script
    stringValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + scenarioSuffix);
    if (stringValue == null) {
      stringValue =
          nodusMapPanel
              .getNodusProject()
              .getLocalProperty(NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT, "");
    }

    postAssignmentScriptTextField.setText(stringValue);

    // Scenario description
    stringValue =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenarioSuffix, "");
    descriptionTextField.setText(stringValue);

    // Fill the "threads" spinner. The number of threads is limited to the number of cores
    String[] v = new String[nbLogicalCores];
    for (int i = 0; i < nbLogicalCores; i++) {
      v[i] = String.valueOf(i + 1);
    }
    SpinnerListModel threadsSpinnerModel = new SpinnerListModel(v);
    threadsSpinner.setModel(threadsSpinnerModel);
    intValue =
        nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_THREADS + scenarioSuffix, -1);
    // Default to number of physical cores
    if (intValue == -1) {
      intValue =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_THREADS, nbPhysicalCores);
    }
    if (intValue > nbLogicalCores) {
      intValue = nbLogicalCores;
    }
    stringValue = String.valueOf(intValue);
    try {
      threadsSpinner.setValue(stringValue);
    } catch (RuntimeException e1) {
      e1.printStackTrace();
    }

    // Query string
    String queryString =
        nodusMapPanel
            .getNodusProject()
            .getLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY + scenarioSuffix);
    if (queryString == null) {
      queryString =
          nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY, "");
    }
    sqlLabel.setText("SELECT * FROM " + odTableName + " WHERE");
    sqlTextArea.setText(queryString);

    // Assignment is possible only if there is a cost functions file and an OD matrix
    assignButton.setEnabled(true);
    if (costFunctionsComboBox.getSelectedIndex() == -1
        || odTablesComboBox.getSelectedIndex() == -1) {
      assignButton.setEnabled(false);
    }
  }

  /** Save the state of the values of the GUI components in the properties. */
  private void saveState() {

    // Only possible if all the parameters needed for an assignment are set
    if (!assignButton.isEnabled()) {
      return;
    }

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_SCENARIO, scenarioSpinner.getValue().toString());

    // Suffix to test if a scenario specific value exists
    String scenarioSuffix = scenarioSpinner.getValue().toString();

    String costFunctions = costFunctionsComboBox.getSelectedItem().toString();
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_COST_FUNCTIONS, costFunctions);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenarioSuffix, costFunctions);

    // Cost functions
    String odTableName = odTablesComboBox.getSelectedItem().toString().trim();
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_OD_TABLE, odTableName);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_OD_TABLE + scenarioSuffix, odTableName);

    int tab = assignmentTabbedPane.getSelectedIndex();
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_ASSIGNMENT_TAB, tab);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_TAB + scenarioSuffix, tab);

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_METHOD, getSelectedAssignmentMethod());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_METHOD + scenarioSuffix, getSelectedAssignmentMethod());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_NB_ITERATIONS, iterationSpinner.getValue().toString());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + scenarioSuffix,
            iterationSpinner.getValue().toString());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_PRECISION, precisionSpinner.getValue().toString());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_PRECISION + scenarioSuffix,
            precisionSpinner.getValue().toString());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_COST_MARKUP, costMarkupSpinner.getValue().toString());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_COST_MARKUP + scenarioSuffix, costMarkupSpinner.getValue().toString());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_MAX_DETOUR, maxDetourSpinner.getValue().toString());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_MAX_DETOUR + scenarioSuffix, maxDetourSpinner.getValue().toString());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_THREADS, threadsSpinner.getValue().toString());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_THREADS + scenarioSuffix, threadsSpinner.getValue().toString());

    ModalSplitMethodName msmn = (ModalSplitMethodName) modalSplitMethodComboBox.getSelectedItem();
    if (msmn != null) {
      nodusMapPanel
          .getNodusProject()
          .setLocalProperty(NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD, msmn.getName());
      nodusMapPanel
          .getNodusProject()
          .setLocalProperty(
              NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + scenarioSuffix, msmn.getName());
    }
    int i = 0;
    if (pathsCheckBox.isSelected()) {
      i = 1;
    }
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS + scenarioSuffix, i);

    i = 0;
    if (detailedPathCheckBox.isSelected()) {
      i = 1;
    }
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + scenarioSuffix, i);

    i = 0;
    if (keepCheapestOnlyCheckBox.isSelected()) {
      i = 1;
    }
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + scenarioSuffix, i);

    i = 0;
    if (lostPathsCheckBox.isSelected()) {
      i = 1;
    }
    nodusMapPanel.getNodusProject().setLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + scenarioSuffix, i);

    i = 0;
    if (postAssignScriptChekbox.isSelected()) {
      i = 1;
    }
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + scenarioSuffix, i);

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT, postAssignmentScriptTextField.getText());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + scenarioSuffix,
            postAssignmentScriptTextField.getText());

    i = 0;
    if (highlightedAreaCheckBox.isSelected()) {
      i = 1;
    }
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA, i);
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + scenarioSuffix, i);

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY, sqlTextArea.getText());
    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY + scenarioSuffix, sqlTextArea.getText());

    nodusMapPanel
        .getNodusProject()
        .setLocalProperty(
            NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenarioSuffix, descriptionTextField.getText());

    // Update title with current scenario number
    //nodusMapPanel.updateScenarioComboBox();
  }

  /**
   * Select one of the radio buttons corresponding to an assignment method given as parameter.
   *
   * @param method int
   */
  private void selectMethod(int method) {

    switch (method) {
      case Assignment.ALL_OR_NOTHING:
        allOrNothingRadioButton.setSelected(true);

        break;

      case Assignment.MSA:
        msaRadioButton.setSelected(true);

        break;

      case Assignment.INCREMENTAL:
        incrementalRadioButton.setSelected(true);

        break;

      case Assignment.FRANK_WOLFE:
        frankWolfeRadioButton.setSelected(true);

        break;

      case Assignment.INCREMENTAL_FRANK_WOLFE:
        incFrankWolfeRadioButton.setSelected(true);

        break;

      case Assignment.FAST_MULTI_FLOW:
        fastMFRadioButton.setSelected(true);

        break;

      case Assignment.EXACT_MULTI_FLOW:
        exactMFRadioButton.setSelected(true);
        break;

      case Assignment.AON_TIME_DEPENDENT:
        staticAoNTimeDependentRadioButton.setSelected(true);
        break;

      case Assignment.DYNAMIC_TIME_DEPENDENT:
        dynamicTimeDependentRadioButton.setSelected(true);
        break;
      default:
        break;
    }
  }

  /**
   * Select a numeric value in a JSpinner.
   *
   * @param spinner The spinner to set
   * @param value The value to set
   */
  private boolean setSpinnerDoubleValue(JSpinner spinner, double value) {

    SpinnerListModel slm = (SpinnerListModel) spinner.getModel();
    @SuppressWarnings("unchecked")
    List<String> listOfValues = (List<String>) slm.getList();

    Iterator<String> it = listOfValues.iterator();
    while (it.hasNext()) {
      String s = it.next();
      if (Double.parseDouble(s) == value) {
        spinner.setValue(s);
        return true;
      }
    }
    return false;
  }

  /**
   * Enables the GUI components relevant for the time dependent assignment methods.
   *
   * @param e ChangeEvent
   */
  private void staticAoNTimeDependentRadioButton_stateChanged(ActionEvent e) {
    updateOptions();
  }

  /** Enables the GUI components relevant for the selected assignment method. */
  private void updateOptions() {

    boolean enabled = false;
    if (nodusMapPanel == null) {
      enabled = true;
    }

    iterationLabel.setEnabled(enabled);
    iterationLabel.setText(i18n.get(AssignmentDlg.class, "Iterations", "Iterations"));
    iterationSpinner.setEnabled(enabled);
    precisionLabel.setEnabled(enabled);
    precisionSpinner.setEnabled(enabled);
    costMarkUpLabel.setEnabled(enabled);
    costMarkupSpinner.setEnabled(enabled);
    maxDetourLabel.setEnabled(enabled);
    maxDetourSpinner.setEnabled(enabled);
    keepCheapestOnlyCheckBox.setEnabled(enabled);

    if (pathsCheckBox.isSelected()) {
      detailedPathCheckBox.setEnabled(true);
    } else {
      detailedPathCheckBox.setEnabled(false);
    }

    if (postAssignScriptChekbox.isSelected()) {
      postAssignmentScriptTextField.setEditable(true);
    } else {
      postAssignmentScriptTextField.setEditable(false);
    }

    // Is the highlightedAreaLayer visible?
    if (nodusMapPanel != null) {
      if (nodusMapPanel.isHighlightedAreaLayerVisible()) {
        highlightedAreaCheckBox.setEnabled(true);
      } else {
        highlightedAreaCheckBox.setEnabled(false);
      }
    }

    if (msaRadioButton.isSelected()) {
      precisionLabel.setEnabled(true);
      precisionSpinner.setEnabled(true);
      iterationLabel.setEnabled(true);
      iterationSpinner.setEnabled(true);

      return;
    }

    if (incrementalRadioButton.isSelected()) {
      iterationLabel.setEnabled(true);
      iterationSpinner.setEnabled(true);

      return;
    }

    if (frankWolfeRadioButton.isSelected()) {
      precisionLabel.setEnabled(true);
      precisionSpinner.setEnabled(true);
      iterationLabel.setEnabled(true);
      iterationSpinner.setEnabled(true);

      return;
    }

    if (incFrankWolfeRadioButton.isSelected()) {
      precisionLabel.setEnabled(true);
      precisionSpinner.setEnabled(true);
      iterationLabel.setEnabled(true);
      iterationSpinner.setEnabled(true);

      return;
    }

    if (fastMFRadioButton.isSelected()) {
      iterationLabel.setEnabled(true);
      iterationLabel.setText(i18n.get(AssignmentDlg.class, "NbRoutes", "Nb routes"));
      iterationSpinner.setEnabled(true);
      costMarkUpLabel.setEnabled(true);
      costMarkupSpinner.setEnabled(true);
      keepCheapestOnlyCheckBox.setEnabled(true);
      maxDetourLabel.setEnabled(true);
      maxDetourSpinner.setEnabled(true);
      if (iterationSpinner.getValue().toString().equals("1")) {
        costMarkupSpinner.setEnabled(false);
        costMarkUpLabel.setEnabled(false);
      }

      return;
    }

    if (exactMFRadioButton.isSelected()) {
      iterationLabel.setEnabled(true);
      iterationLabel.setText(i18n.get(AssignmentDlg.class, "NbRoutes", "Nb routes"));
      iterationSpinner.setEnabled(true);
      costMarkUpLabel.setEnabled(true);
      costMarkupSpinner.setEnabled(true);
      keepCheapestOnlyCheckBox.setEnabled(true);
      maxDetourLabel.setEnabled(true);
      maxDetourSpinner.setEnabled(true);
      if (iterationSpinner.getValue().toString().equals("1")) {
        costMarkupSpinner.setEnabled(false);
        costMarkUpLabel.setEnabled(false);
      }

      return;
    }
  }
}
