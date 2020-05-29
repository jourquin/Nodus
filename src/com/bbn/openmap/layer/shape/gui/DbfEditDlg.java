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

package com.bbn.openmap.layer.shape.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.location.BasicLocation;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.Length;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.exclusions.gui.ExclusionDlg;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.swing.AbstractCellEditor;
import javax.swing.ImageIcon;
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
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * This dialog box displays the content of a record of an Esri Dbf file and allows to edit it.
 *
 * @author Bart Jourquin
 */
public class DbfEditDlg extends EscapeDialog implements ShapeConstants {

  /**
   * Private class used control the type of data entered in a cell. Only a data compatible with the
   * related column in the .dbf file can be entered
   */
  class DbfTableCellEditor extends AbstractCellEditor implements TableCellEditor {
    static final long serialVersionUID = 4767595905907656102L;

    private DbfTableModel model;

    private JButton okButton;

    private Object oldValue;

    private int rowIndex;

    // This is the component that will handle the editing of the cell value
    private JComponent textField = new JTextField();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    // This method is called when editing is completed.
    // It must return the new value to be stored in the cell.
    @Override
    public Object getCellEditorValue() {
      return ((JTextField) textField).getText();
    }

    // This method is called when a cell value is edited by the user.
    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int rowIndex, int colIndex) {
      this.rowIndex = rowIndex;
      oldValue = value;

      // Configure the component with the specified value
      ((JTextField) textField).setText((String) value);
      textField.setBorder(LineBorder.createGrayLineBorder());
      textField.setForeground(Color.red);

      // Return the configured component
      return textField;
    }

    public void setModel(DbfTableModel m) {
      model = m;
      dateFormat.setLenient(false);
    }

    public void setOkButton(JButton b) {
      okButton = b;
    }

    /**
     * This method is called just before the cell value is saved. If the value is not valid, false
     * should be returned.
     */
    @Override
    public boolean stopCellEditing() {
      String value = (String) getCellEditorValue();

      if (value.length() > model.getLength(rowIndex)) {
        String message =
            MessageFormat.format(
                i18n.get(DbfEditDlg.class, "Value_too_long_max", "Value too long (max {0})"),
                String.valueOf(model.getLength(rowIndex)));

        JOptionPane.showMessageDialog(null, message, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
        okButton.setEnabled(false);

        return false;
      }

      // Test validity of numeric values
      if (model.getType(rowIndex) == DbfTableModel.TYPE_NUMERIC) {
        // Test if valid value
        value = value.trim();

        for (int j = 0; j < value.length(); j++) {
          if (value.charAt(j) < '0' || value.charAt(j) > '9') {
            if (value.charAt(j) != '.') {
              JOptionPane.showMessageDialog(
                  null,
                  i18n.get(
                      DbfEditDlg.class, "Numerical_value_expected", "Numerical value expected"),
                  NodusC.APPNAME,
                  JOptionPane.ERROR_MESSAGE);

              okButton.setEnabled(false);

              return false;
            } else {
              if (model.getDecimalCount(rowIndex) == 0) {
                JOptionPane.showMessageDialog(
                    null,
                    i18n.get(DbfEditDlg.class, "Integer_value_expected", "Integer value expected"),
                    NodusC.APPNAME,
                    JOptionPane.ERROR_MESSAGE);

                okButton.setEnabled(false);

                return false;
              }
            }
          }
        }
      }

      // Test validity of date (must be in YYYYMMDD format
      if (model.getType(rowIndex) == DbfTableModel.TYPE_DATE) {
        boolean error = false;

        value = value.trim();
        if (value.length() == 8) {

          try {
            dateFormat.parse(value);
          } catch (ParseException pe) {
            error = true;
          }

        } else {
          error = true;
        }

        if (error) {
          JOptionPane.showMessageDialog(
              null,
              i18n.get(
                  DbfEditDlg.class, "Date_expected", "Valid date in the YYYYMMDD format expected"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          okButton.setEnabled(false);

          return false;
        }
      }

      // Is this a changed value?
      if (!value.equals(oldValue)) {
        isTableChanged = true;
      }

      okButton.setEnabled(true);

      return super.stopCellEditing();
    }
  }

  /**
   * Private class that controls the editing of a cell. Only the values of the "editable" cells can
   * be modified. These are displayed in blue, instead of black.
   */
  static class DbfTableCellRenderer extends JLabel implements TableCellRenderer, ShapeConstants {
    static final long serialVersionUID = -9222285849574421581L;

    NodusEsriLayer layer;

    private int nbUneditables;

    public DbfTableCellRenderer(int nbUneditables) {
      this.nbUneditables = nbUneditables;
    }

    @Override
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
      // Must be overridden
    }

    @Override
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
      // Must be overridden
    }

    // This method is called each time a cell in a column using this renderer needs to be rendered.
    @Override
    public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int rowIndex,
        int colIndex) {
      // Configure the component with the specified value
      setText(value.toString());

      // Set the editable values in blue
      boolean isEditable = true;

      if (rowIndex < nbUneditables) {
        isEditable = false;
      }

      if (isEditable) {
        setForeground(Color.blue);
      } else {
        setForeground(Color.black);
      }

      // Since the renderer is a component, return itself
      return this;
    }

    @Override
    public void revalidate() {
      // Must be overridden
    }

    public void setLayer(NodusEsriLayer l) {
      layer = l;
    }

    // The following methods override the defaults for performance reasons
    @Override
    public void validate() {
      // Must be overridden
    }
  }

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 1898200016552197983L;

  private JButton cancelButton = new JButton();

  private JButton copyButton = new JButton();

  private JTable dbfTable;

  private DbfTableCellEditor dbfTableCellEditor;

  private JButton exclusionsButton = new JButton();

  private JScrollPane fieldsScrollPane = new JScrollPane();

  private String[] handling;

  private boolean isStyleChanged = false;

  private boolean isEnabledChanged = false;

  private boolean isTableChanged = false;

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private int nbUneditableFields;

  private NodusEsriLayer nodusEsriLayer;

  private int objectNum;

  String oldLabel = null;

  private int oldStyle;

  private Object[] oldValues;

  private OMGraphic omGraphic;

  private JButton pasteButton = new JButton();

  private JLabel sampleLabel = new JLabel();

  private int sampleLabelHeight;

  private int sampleLabelWidth;

  private BufferedImage sampleStyleImage = null;

  private JButton saveButton = new JButton();

  private JButton servicesButton = null;

  private JComboBox<String> styleComboBox = new JComboBox<String>();

  private JLabel styleLabel = new JLabel();

  private JComboBox<String> transhipComboBox = null;

  private JLabel transhipLabel = null;

  private JButton virtualNetworkButton = new JButton();

  private JCheckBox transitCheckBox =
      new JCheckBox(i18n.get(DbfEditDlg.class, "Transit", "Allow transit"));

  private JCheckBox enabledCheckBox =
      new JCheckBox(i18n.get(DbfEditDlg.class, "Enabled", "Enabled"));

  /**
   * The constructor needs the NodusEsriLayer the record to edit refers to.
   *
   * @param layer NodusEsriLayer
   */
  public DbfEditDlg(NodusEsriLayer layer) {
    super(layer.getNodusMapPanel().getMainFrame(), "", true);

    nodusEsriLayer = layer;

    // Some of the fields can not be edited in order not to break the integrity of the database
    nbUneditableFields = 5;
    if (nodusEsriLayer.getType() == SHAPE_TYPE_POINT) {
      nbUneditableFields = 3;
    }

    // Same action as escape key or cancel button
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            cancel();
            setVisible(false);
          }
        });

    initialize();
    getRootPane().setDefaultButton(cancelButton);

    GridBagConstraints virtualNetworkButtonConstraints =
        new GridBagConstraints(
            4,
            0,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    virtualNetworkButtonConstraints.gridy = 4;
    mainPanel.add(virtualNetworkButton, virtualNetworkButtonConstraints);
    cancelButton.requestFocus();
  }

  /**
   * Closes the dialog, discarding any change made in the record.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    cancel();
    setVisible(false);
  }

  /*
   * This function is called whenever a Component belonging to this Dialog
   * (or the Dialog itself) gets the KEY_PRESSED event.
   */
  @Override
  public void keyPressed(KeyEvent e) {
    int code = e.getKeyCode();
    if (code == KeyEvent.VK_ESCAPE) {
      // Key pressed is the ESCAPE key. Hide this Dialog.
      cancel();
    }
    super.keyPressed(e);
  }

  private void cancel() {
    // Put the old values back in table
    for (int i = 0; i < nodusEsriLayer.getModel().getColumnCount(); i++) {
      nodusEsriLayer
          .getModel()
          .setValueAt(oldValues[i], nodusEsriLayer.getSelectedGraphicIndex(), i);
    }
    nodusEsriLayer.setCanceled(true);
  }

  /**
   * Copy the content of this record and enables the paste button.
   *
   * @param e ActionEvent
   */
  private void copyButton_actionPerformed(ActionEvent e) {
    // Just keep the index of the current selected graphic
    nodusEsriLayer.setCopyRecordIndex(nodusEsriLayer.getSelectedGraphicIndex());
    pasteButton.setEnabled(true);
  }

  /**
   * Draws a sample type of node or link, which index passed as parameter.
   *
   * @param index int
   */
  private void drawSample(int index) {
    NodusOMGraphic model =
        nodusEsriLayer.getNodusMapPanel().getNodusProject().getStyle(omGraphic, index);

    if (sampleStyleImage == null) {
      sampleStyleImage =
          new BufferedImage(sampleLabelWidth, sampleLabelHeight, BufferedImage.TYPE_INT_ARGB);
    }

    sampleStyleImage.createGraphics();

    Graphics2D g2 = (Graphics2D) sampleStyleImage.getGraphics();
    g2.setBackground(Color.white);
    g2.clearRect(0, 0, sampleLabelWidth, sampleLabelHeight);

    Shape shape = null;

    if (omGraphic instanceof EsriPolyline) {
      // Draw a sample link
      if (model.isMatted()) {
        // Normal rendering
        BasicStroke bs = (BasicStroke) model.getStroke();
        g2.setStroke(new BasicStroke(bs.getLineWidth() + 2f));
        g2.setPaint(model.getMattingPaint());
        g2.draw(
            new Line2D.Double(
                10.0,
                sampleLabelHeight / 2.0,
                sampleLabelWidth / 2.0 - 5.0,
                sampleLabelHeight / 2.0));

        // Rendering for alternative values
        g2.setPaint(model.getAltMattingPaint());
        g2.draw(
            new Line2D.Double(
                sampleLabelWidth / 2.0 + 5,
                sampleLabelHeight / 2.0,
                sampleLabelWidth - 10.0,
                sampleLabelHeight / 2.0));
      }

      g2.setStroke(model.getStroke());

      // Normal rendering
      g2.setPaint(model.getLineColor());
      g2.draw(
          new Line2D.Double(
              10.0,
              sampleLabelHeight / 2.0,
              sampleLabelWidth / 2.0 - 5.0,
              sampleLabelHeight / 2.0));

      // Rendering for alternative value
      g2.setPaint(model.getAltLinePaint());
      g2.draw(
          new Line2D.Double(
              sampleLabelWidth / 2.0 + 5.0,
              sampleLabelHeight / 2.0,
              sampleLabelWidth - 10.0,
              sampleLabelHeight / 2.0));
    } else {
      // Draw a sample node
      // Normal rendering
      g2.setStroke(model.getStroke());

      int radius = model.getRadius();
      int x = sampleLabelWidth / 3 - radius;
      int y = sampleLabelHeight / 2 - radius;
      g2.setPaint(model.getFillColor());

      if (model.isOval()) {
        g2.fillOval(x, y, 2 * radius, 2 * radius);
        shape = new Ellipse2D.Float(x, y, 2 * radius, 2 * radius);
      } else {
        g2.fillRect(x, y, 2 * radius, 2 * radius);
        shape = new Rectangle2D.Float(x, y, 2 * radius, 2 * radius);
      }

      g2.setPaint(model.getLineColor());
      g2.draw(shape);

      // Rendering for alternative values
      g2.setStroke(model.getStroke());
      radius = model.getRadius();
      x = 2 * sampleLabelWidth / 3 - radius;
      g2.setPaint(model.getAltFillPaint());

      if (model.isOval()) {
        g2.fillOval(x, y, 2 * radius, 2 * radius);
        shape = new Ellipse2D.Float(x, y, 2 * radius, 2 * radius);
      } else {
        g2.fillRect(x, y, 2 * radius, 2 * radius);
        shape = new Rectangle2D.Float(x, y, 2 * radius, 2 * radius);
      }

      g2.setPaint(model.getAltLinePaint());
      g2.draw(shape);
    }

    g2.dispose();

    Image image = Toolkit.getDefaultToolkit().createImage(sampleStyleImage.getSource());
    sampleLabel.setIcon(new ImageIcon(image));
  }

  /**
   * Opens a dialog-box in which the local (node) exclusions can be edited.
   *
   * @param e ActionEvent
   */
  private void exclusionsButton_actionPerformed(ActionEvent e) {
    ExclusionDlg excDlg = new ExclusionDlg(this, nodusEsriLayer, objectNum);
    excDlg.setVisible(true);
  }

  /**
   * This method initializes the "service" button.
   *
   * @return javax.swing.JButton
   */
  private JButton getServicesButton() {
    if (servicesButton == null) {
      servicesButton = new JButton();
      servicesButton.setText(i18n.get(DbfEditDlg.class, "Services", "Services"));

      if (nodusEsriLayer.getType() == SHAPE_TYPE_POLYLINE
          && nodusEsriLayer
                  .getNodusMapPanel()
                  .getNodusProject()
                  .getServiceEditor()
                  .getServiceNamesForLink(objectNum)
                  .size()
              == 0) {
        servicesButton.setEnabled(false);
      }
      if (nodusEsriLayer.getType() == SHAPE_TYPE_POINT) {
        if (nodusEsriLayer
                .getNodusMapPanel()
                .getNodusProject()
                .getServiceEditor()
                .getTranship(objectNum)
            == 0) {
          servicesButton.setEnabled(false);
        }
        if (nodusEsriLayer
                .getNodusMapPanel()
                .getNodusProject()
                .getServiceEditor()
                .getServiceNamesForNode(objectNum)
                .size()
            == 0) {
          servicesButton.setEnabled(false);
        }
      }
      servicesButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              ServicesDlg dlg = new ServicesDlg(nodusEsriLayer, objectNum);
              dlg.setVisible(true);
            }
          });
    }
    return servicesButton;
  }

  /**
   * This method initializes transhipComboBox.
   *
   * @return javax.swing.JComboBox
   */
  private JComboBox<String> getTranshipComboBox() {
    if (transhipComboBox == null) {
      transhipComboBox = new JComboBox<String>();
      transhipComboBox.addItemListener(
          new java.awt.event.ItemListener() {
            @Override
            public void itemStateChanged(java.awt.event.ItemEvent e) {
              JComboBox<?> cb = (JComboBox<?>) e.getSource();

              // Update the table with selected item
              if (e.getStateChange() == ItemEvent.SELECTED) {
                // Get the affected item
                int idx = cb.getSelectedIndex();
                String value = String.valueOf(idx);
                // Update table (third row)
                dbfTable.setValueAt(value, 2, 1);
                isTableChanged = true;
              }
            }
          });
    }
    return transhipComboBox;
  }

  /**
   * Called by default constructor. Setup a dialog box and displays the record in two columns : the
   * first with the field names, the second with the associated values.
   */
  private void initialize() {

    if (nodusEsriLayer == null) {
      return;
    }

    DbfTableModel model = nodusEsriLayer.getModel();
    if (model == null) {
      return;
    }
    List<Object> values = model.getRecord(nodusEsriLayer.getSelectedGraphicIndex());

    oldValues = values.toArray();

    // Get the num of the object for later use;
    objectNum = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));
    GridBagConstraints servicesButtonConstraints = new GridBagConstraints();
    servicesButtonConstraints.gridx = 0;
    servicesButtonConstraints.insets = new Insets(5, 5, 5, 5);
    servicesButtonConstraints.gridy = 2;

    GridBagConstraints transhipLabelConstraints = new GridBagConstraints();
    transhipLabelConstraints.gridx = 0;
    transhipLabelConstraints.anchor = GridBagConstraints.EAST;
    transhipLabelConstraints.insets = new Insets(5, 5, 5, 5);
    transhipLabelConstraints.gridy = 1;
    transhipLabel = new JLabel();
    transhipLabel.setText(i18n.get(DbfEditDlg.class, "Tranship", "Tranship :"));

    GridBagConstraints exclusionsButtonConstraints =
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 0),
            0,
            0);
    exclusionsButtonConstraints.gridy = 2;
    exclusionsButtonConstraints.insets = new Insets(5, 5, 5, 5);
    exclusionsButtonConstraints.anchor = GridBagConstraints.CENTER;
    exclusionsButtonConstraints.gridx = 1;

    GridBagConstraints fieldsScrollPaneConstraints =
        new GridBagConstraints(
            0,
            1,
            7,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 0),
            0,
            0);
    fieldsScrollPaneConstraints.gridy = 3;

    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.weightx = 1.0D;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.insets = new Insets(5, 5, 5, 5);
    gridBagConstraints.anchor = GridBagConstraints.CENTER;
    gridBagConstraints.gridx = 1;
    mainPanel.setLayout(mainPanelGridBagLayout);

    // Create a table with 2 columns and as many rows than fields in the dbf
    // and in which not all cells are editable. Tooltips are set to
    // display data types
    if (nodusEsriLayer != null) {
      dbfTable =
          new JTable(nodusEsriLayer.getModel().getColumnCount(), 2) {

            private static final long serialVersionUID = 8017718735481112955L;

            @Override
            public boolean isCellEditable(int rowIndex, int colIndex) {
              // Field names are not editable
              if (colIndex == 0) {
                return false;
              }

              // Some of the fields are not editable
              if (rowIndex < nbUneditableFields) {
                return false;
              }

              return true;
            }

            @Override
            public Component prepareRenderer(
                TableCellRenderer renderer, int rowIndex, int colIndex) {
              Component c = super.prepareRenderer(renderer, rowIndex, colIndex);

              if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                String dataType = i18n.get(DbfEditDlg.class, "Field_name", "Field name");

                if (colIndex == 1) {
                  if (nodusEsriLayer.getModel().getType(rowIndex) == DbfTableModel.TYPE_NUMERIC) {
                    dataType =
                        "Numeric " + String.valueOf(nodusEsriLayer.getModel().getLength(rowIndex));

                    if (nodusEsriLayer.getModel().getDecimalCount(rowIndex) > 0) {
                      dataType +=
                          "("
                              + String.valueOf(nodusEsriLayer.getModel().getDecimalCount(rowIndex))
                              + ")";
                    }
                  } else {
                    dataType =
                        "Char " + String.valueOf(nodusEsriLayer.getModel().getLength(rowIndex));
                  }
                }

                jc.setToolTipText(dataType);
              }

              return c;
            }
          };

      // add a cell editor to second column
      dbfTableCellEditor = new DbfTableCellEditor();
      dbfTableCellEditor.setModel(nodusEsriLayer.getModel());
      dbfTableCellEditor.setOkButton(saveButton);
      TableColumn col = dbfTable.getColumnModel().getColumn(1);
      col.setCellEditor(dbfTableCellEditor);

      DbfTableCellRenderer tcr = new DbfTableCellRenderer(nbUneditableFields);
      tcr.setLayer(nodusEsriLayer);
      col.setCellRenderer(tcr);
    }

    dbfTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    dbfTable.setColumnSelectionAllowed(false);

    if (nodusEsriLayer != null && nodusEsriLayer.getCopyRecordIndex() == -1) {
      pasteButton.setEnabled(false);
    }

    virtualNetworkButton.setText(i18n.get(DbfEditDlg.class, "Virtual_network", "Virtual network"));
    virtualNetworkButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            virtualNetworkButton_actionPerformed(e);
          }
        });

    fieldsScrollPane.setPreferredSize(new Dimension(-1, 200));
    styleLabel.setText(i18n.get(DbfEditDlg.class, "Style", "Style") + " :");

    sampleLabel.setText("");
    sampleLabel.setSize(new Dimension(200, 30));

    styleComboBox.addItemListener(
        new java.awt.event.ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            styleComboBox_itemStateChanged(e);
          }
        });

    setContentPane(mainPanel);
    fieldsScrollPane.setViewportView(dbfTable);

    GridBagConstraints gbcChckbxTransit = new GridBagConstraints();
    gbcChckbxTransit.anchor = GridBagConstraints.WEST;
    gbcChckbxTransit.gridwidth = 2;
    gbcChckbxTransit.insets = new Insets(5, 5, 5, 5);
    gbcChckbxTransit.gridx = 4;
    gbcChckbxTransit.gridy = 1;

    transitCheckBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            boolean transit = transitCheckBox.isSelected();

            // Get the current value of the tranship operation
            int idx = transhipComboBox.getSelectedIndex();
            if (!transit) {
              idx += 5;
            }

            // Update the value (>= 5 if no transit is allowed)
            String value = String.valueOf(idx);

            // Update table (third row)
            dbfTable.setValueAt(value, NodusC.DBF_IDX_TRANSHIP, 1);
            isTableChanged = true;
          }
        });
    mainPanel.add(transitCheckBox, gbcChckbxTransit);

    GridBagConstraints gbcChckbxEnabled = new GridBagConstraints();
    gbcChckbxEnabled.anchor = GridBagConstraints.WEST;
    gbcChckbxEnabled.gridwidth = 2;
    gbcChckbxEnabled.insets = new Insets(5, 5, 5, 5);
    gbcChckbxEnabled.gridx = 4;
    gbcChckbxEnabled.gridy = 0;
    enabledCheckBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            boolean enabled = enabledCheckBox.isSelected();
            // Update the value
            String value = "1";
            if (!enabled) {
              value = "0";
            }

            // Update table (third row)
            dbfTable.setValueAt(value, NodusC.DBF_IDX_ENABLED, 1);
            isEnabledChanged = true;
          }
        });
    mainPanel.add(enabledCheckBox, gbcChckbxEnabled);

    exclusionsButton.setText(i18n.get(DbfEditDlg.class, "Exclusions", "Exclusions"));
    mainPanel.add(exclusionsButton, exclusionsButtonConstraints);

    GridBagConstraints cancelButtonConstraints = new GridBagConstraints();
    cancelButtonConstraints.anchor = GridBagConstraints.EAST;
    cancelButtonConstraints.insets = new Insets(5, 5, 5, 5);
    cancelButtonConstraints.gridx = 0;
    cancelButtonConstraints.gridy = 4;

    cancelButton.setText(i18n.get(DbfEditDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    GridBagConstraints pasteButtonConstraints =
        new GridBagConstraints(
            2,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHEAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    pasteButtonConstraints.anchor = GridBagConstraints.WEST;
    pasteButtonConstraints.gridx = 5;

    pasteButton.setText(i18n.get(DbfEditDlg.class, "Paste", "Paste"));
    pasteButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            pasteButton_actionPerformed(e);
          }
        });

    GridBagConstraints copyButtonConstraints =
        new GridBagConstraints(
            1,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    copyButtonConstraints.gridx = 4;

    copyButton.setText(i18n.get(DbfEditDlg.class, "Copy", "Copy"));
    copyButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            copyButton_actionPerformed(e);
          }
        });
    mainPanel.add(copyButton, copyButtonConstraints);
    mainPanel.add(pasteButton, pasteButtonConstraints);
    mainPanel.add(cancelButton, cancelButtonConstraints);

    GridBagConstraints saveButtonConstraints =
        new GridBagConstraints(
            1,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);

    saveButton.setText(i18n.get(DbfEditDlg.class, "Save", "Save"));
    saveButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveButton_actionPerformed(e);
          }
        });
    mainPanel.add(saveButton, saveButtonConstraints);
    exclusionsButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            exclusionsButton_actionPerformed(e);
          }
        });
    mainPanel.add(
        styleLabel,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        styleComboBox,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(fieldsScrollPane, fieldsScrollPaneConstraints);
    mainPanel.add(
        sampleLabel,
        new GridBagConstraints(
            2,
            0,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(getTranshipComboBox(), gridBagConstraints);
    mainPanel.add(transhipLabel, transhipLabelConstraints);
    mainPanel.add(getServicesButton(), servicesButtonConstraints);
    if (nodusEsriLayer.getType() != SHAPE_TYPE_POINT) {
      exclusionsButton.setEnabled(false);
    }

    // Get user defined data attached to the selected graphic
    omGraphic =
        nodusEsriLayer
            .getEsriGraphicList()
            .getOMGraphicAt(nodusEsriLayer.getSelectedGraphicIndex());

    String length = "";

    float l = -1;

    if (omGraphic instanceof EsriPolyline) {
      l = NodusEsriLayer.getLength((EsriPolyline) omGraphic, Length.KM); // lengthUnit);
      transhipComboBox.setVisible(false);
      transhipLabel.setVisible(false);
      transitCheckBox.setVisible(false);
    } else {
      // Fill with possible transhipment operations
      handling = new String[5];
      handling[NodusC.HANDLING_NONE] =
          i18n.get(DbfEditDlg.class, "No_operation", "0 - No operation");
      handling[NodusC.HANDLING_ALL] =
          i18n.get(DbfEditDlg.class, "All_operations", "1 - All operations");
      handling[NodusC.HANDLING_TRANSHIP] =
          i18n.get(DbfEditDlg.class, "Transhipment_only", "2 - Transhipment only");
      handling[NodusC.HANDLING_LOAD_UNLOAD] =
          i18n.get(DbfEditDlg.class, "Loading_Unloading_only", "3 - Loading/Unloading only");
      handling[NodusC.HANDLING_CHANGE_SERVICE] =
          i18n.get(DbfEditDlg.class, "Change_Service_only", "4 - Change Line only");

      for (String element : handling) {
        transhipComboBox.addItem(element);
      }

      // Value of tranship in dbf
      int t = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_TRANSHIP));

      // If the valur of t is larger than 4, it means that no transit is permitted
      boolean transit = true;
      if (t >= 5) {
        t -= 5;
        transit = false;
      }
      transitCheckBox.setSelected(transit);

      if (t >= 0 && t < 5) {
        transhipComboBox.setSelectedIndex(t);
      }

      enabledCheckBox.setVisible(false);
    }

    if (l != -1) {
      length = " (" + l + " " + Length.KM.getAbbr() + ")";
    }

    setTitle(nodusEsriLayer.getTableName() + length);

    // Fill the first column with field names, and the second with the values
    for (int i = 0; i < nodusEsriLayer.getModel().getColumnCount(); i++) {
      dbfTable.setValueAt(nodusEsriLayer.getModel().getColumnName(i), i, 0);

      // Now put value in relevant format
      String value = values.get(i).toString();
      value = value.trim();

      if (nodusEsriLayer.getModel().getType(i) == DbfTableModel.TYPE_NUMERIC) {
        int n = nodusEsriLayer.getModel().getDecimalCount(i);
        if (n == 0) {
          value = "" + JDBCUtils.getInt(value);
        }
      }

      dbfTable.setValueAt(value, i, 1);

      if (i == NodusC.DBF_IDX_ENABLED && omGraphic instanceof EsriPolyline) {
        if (value.equals("1")) {
          enabledCheckBox.setSelected(true);
        } else {
          enabledCheckBox.setSelected(false);
        }
      }
    }

    // Set the column titles
    TableColumn tc = dbfTable.getColumn("A");
    tc.setHeaderValue(i18n.get(DbfEditDlg.class, "Field", "Field"));

    tc = dbfTable.getColumn("B");
    tc.setHeaderValue(i18n.get(DbfEditDlg.class, "Value", "Value"));

    // Fill styles combo and display sample of current style
    sampleLabelWidth = sampleLabel.getWidth();
    sampleLabelHeight = sampleLabel.getHeight();

    int index = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_STYLE));
    oldStyle = index;

    for (int i = 0;
        i < nodusEsriLayer.getNodusMapPanel().getNodusProject().getNbStyles(omGraphic);
        i++) {
      styleComboBox.addItem(Integer.toString(i));
    }

    styleComboBox.setSelectedIndex(index);
    drawSample(index);

    // The layer should be redrawn if the label is changed
    RealNetworkObject rnbo = (RealNetworkObject) omGraphic.getAttribute(0);

    BasicLocation bl = null;

    if (rnbo != null) {
      bl = rnbo.getLocation();
    }

    if (bl != null) {
      int n = nodusEsriLayer.getLocationHandler().getLocationFieldIndex();

      if (n >= 0) {
        oldLabel =
            nodusEsriLayer
                .getModel()
                .getValueAt(nodusEsriLayer.getSelectedGraphicIndex(), n)
                .toString();
      }
    }
    pack();
  }

  /**
   * Paste the content of a previously copied record in the current record.
   *
   * @param e ActionEvent
   */
  private void pasteButton_actionPerformed(ActionEvent e) {
    // Copy the editable fields from source into current record
    List<?> values = nodusEsriLayer.getModel().getRecord(nodusEsriLayer.getCopyRecordIndex());

    for (int i = nbUneditableFields; i < nodusEsriLayer.getModel().getColumnCount(); i++) {
      String value = values.get(i).toString();
      value = value.trim();

      if (nodusEsriLayer.getModel().getType(i) == DbfTableModel.TYPE_NUMERIC) {
        int n = nodusEsriLayer.getModel().getDecimalCount(i);

        if (n == 0 && value.indexOf('.') != -1) {
          value = value.substring(0, value.indexOf('.'));
        }
      }

      dbfTable.setValueAt(value, i, 1);
    }

    // Also copy style (which is only editable via the combo box...)
    String style = values.get(NodusC.DBF_IDX_STYLE).toString();
    style = style.substring(0, style.indexOf('.'));
    dbfTable.setValueAt(style, NodusC.DBF_IDX_STYLE, 1);
    styleComboBox.setSelectedIndex(JDBCUtils.getInt(style));

    // Same for "enabled"
    String enabled = values.get(NodusC.DBF_IDX_ENABLED).toString();
    enabled = enabled.substring(0, enabled.indexOf('.'));
    dbfTable.setValueAt(enabled, NodusC.DBF_IDX_ENABLED, 1);

    // Tell that values are changed and reset "clipboard"
    isTableChanged = true;
    nodusEsriLayer.setCopyRecordIndex(-1);
    pasteButton.setEnabled(false);
  }

  /**
   * Updates the record in the DbfTable of the EsriLayer and stores the record in the DBF file
   * through a JDBC transaction. The dialog is then closed.
   *
   * @param e ActionEvent
   */
  private void saveButton_actionPerformed(ActionEvent e) {
    if (isTableChanged || isStyleChanged || isEnabledChanged) {
      // Put the new values in the table.
      // We know they are correct because they were tested in the cell editor
      for (int i = 0; i < nodusEsriLayer.getModel().getColumnCount(); i++) {
        String value = dbfTable.getValueAt(i, 1).toString();

        if (nodusEsriLayer.getModel().getType(i) == DbfTableModel.TYPE_NUMERIC) {
          Double d = Double.valueOf(value);
          nodusEsriLayer.getModel().setValueAt(d, nodusEsriLayer.getSelectedGraphicIndex(), i);
        } else {
          nodusEsriLayer.getModel().setValueAt(value, nodusEsriLayer.getSelectedGraphicIndex(), i);
        }
      }

      // Build SQL command
      Object cell;
      String sqlStmt = "UPDATE ";
      sqlStmt += nodusEsriLayer.getTableName() + " SET ";

      for (int i = 0; i < nodusEsriLayer.getModel().getColumnCount(); i++) {
        sqlStmt +=
            JDBCUtils.getQuotedCompliantIdentifier(nodusEsriLayer.getModel().getColumnName(i))
                + '=';
        cell = nodusEsriLayer.getModel().getValueAt(nodusEsriLayer.getSelectedGraphicIndex(), i);

        byte type = nodusEsriLayer.getModel().getType(i);

        if (type == DbfTableModel.TYPE_NUMERIC) {
          if (nodusEsriLayer.getModel().getDecimalCount(i) > 0) {
            sqlStmt += JDBCUtils.getDouble(cell);

          } else {
            sqlStmt += JDBCUtils.getInt(cell);
          }
        } else if (type == DbfTableModel.TYPE_CHARACTER) {
          sqlStmt += '\'' + cell.toString() + '\'';
        } else if (type == DbfTableModel.TYPE_DATE) {
          sqlStmt += JDBCUtils.getDate(cell.toString());
        }
        if (i < nodusEsriLayer.getModel().getColumnCount() - 1) {
          sqlStmt += ", ";
        }
      }

      int num =
          JDBCUtils.getInt(
              nodusEsriLayer
                  .getModel()
                  .getValueAt(nodusEsriLayer.getSelectedGraphicIndex(), NodusC.DBF_IDX_NUM));
      sqlStmt += " WHERE " + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM) + " = " + num;

      nodusEsriLayer.executeUpdateSqlStmt(sqlStmt);
      nodusEsriLayer.setDirtyDbf(true);

      // Get the label attached to this graphic and update it
      String newLabel = null;

      // Normally no test must be done on the type of AppObject
      RealNetworkObject rnbo = (RealNetworkObject) omGraphic.getAttribute(0);

      if (rnbo != null) {
        BasicLocation bl = rnbo.getLocation();

        if (bl != null) {
          int n = nodusEsriLayer.getLocationHandler().getLocationFieldIndex();

          if (n >= 0) {
            newLabel =
                nodusEsriLayer
                    .getModel()
                    .getValueAt(nodusEsriLayer.getSelectedGraphicIndex(), n)
                    .toString();
            bl.setName(newLabel);
          }
        }
      } else {
        // Force the labels to be reloaded if a new link or node is
        // added
        nodusEsriLayer.getLocationHandler().reloadData();
      }

      // Update the display if needed
      if (isStyleChanged || oldLabel != null && !oldLabel.equals(newLabel)) {
        nodusEsriLayer.attachStyles();
        nodusEsriLayer.doPrepare();
      }

      // Avoid re-entrance
      isTableChanged = false;
    }

    nodusEsriLayer.setCanceled(false);
    setVisible(false);
  }

  /**
   * Updates the sample node or link drawn when another style is chosen.
   *
   * @param e ItemEvent
   */
  private void styleComboBox_itemStateChanged(ItemEvent e) {
    int index = styleComboBox.getSelectedIndex();
    drawSample(index);
    dbfTable.setValueAt(Integer.toString(index), NodusC.DBF_IDX_STYLE, 1);
    // TableColumn col = dbfTable.getColumnModel().getColumn(1);
    if (index == oldStyle) {
      isStyleChanged = false;
    } else {
      isStyleChanged = true;
    }
  }

  private void virtualNetworkButton_actionPerformed(ActionEvent e) {
    setAlwaysOnTop(false);

    VirtualNetworkViewerDlg virtDlg = new VirtualNetworkViewerDlg(nodusEsriLayer, objectNum);
    virtDlg.setVisible(true);
  }
}
