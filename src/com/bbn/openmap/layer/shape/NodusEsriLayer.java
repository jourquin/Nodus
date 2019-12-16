/**
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

package com.bbn.openmap.layer.shape;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.EsriShapeExport;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.dataAccess.shape.input.ShxInputStream;
import com.bbn.openmap.dataAccess.shape.output.ShpOutputStream;
import com.bbn.openmap.dataAccess.shape.output.ShxOutputStream;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.layer.shape.gui.DbfEditDlg;
import com.bbn.openmap.layer.shape.gui.SelectPropertiesDlg;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Length;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.real.RealNode;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.services.ServiceEditor;
import edu.uclouvain.core.nodus.swing.GUIUtils;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Paint;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * This layer extends the original OpenMap EsriLayer, allowing the edition of the content of the DBF
 * records associated to the shapes. It is also one of the core elements of Nodus.
 *
 * @author Bart Jourquin
 */
public class NodusEsriLayer extends FastEsriLayer implements ShapeConstants {

  private static final long serialVersionUID = -3838171020002907199L;

  /** I18N mechanism. */
  private static final I18n i18n = Environment.getI18n();

  /** Used for cut&paste. */
  private int copyRecordIndex = -1;

  /**
   * Variables that control what must be saved: - DirtyShp must be set to true when an OMGraphic is
   * added, deleted or moved - DirtyDbf must be set to true when a dbf record is modified, added or
   * deleted.
   */
  private boolean dirtyDbf = false;

  /**
   * Variables that control what must be saved: - DirtyShp must be set to true when an OMGraphic is
   * added, deleted or moved - DirtyDbf must be set to true when a dbf record is modified, added or
   * deleted.
   */
  private boolean dirtyShp = false;

  /**
   * Let the user the possibility to display or not the dbf fields of a given. OMGraphic in gesture
   * mode
   */
  private boolean displayDbfTooltips = true;

  /** This variables decides which type of rendering is to be used. */
  private boolean displayResults = false;

  /** Used to know if an edit operation was canceled. */
  private boolean isCanceled;

  /** Set to true when the layers are ready to be painted. */
  private boolean isReady = false;

  private JDBCUtils jdbcUtils;

  /**
   * Name of the variable that can be used in the cost functions and that represents this layer. The
   * variable itself is a 0/1 integer value
   */
  private String layerVarName = null;

  /** List of links that have sample origin and destination. */
  private LinkedList<Integer> linksToRemove = null;

  /** Location handler that is associated withb this layer. */
  private NodusLocationHandler nodusLocationHandler = null;

  /** NodusProject associated to this layer. */
  private NodusProject nodusProject;

  /**
   * Used to store the correspondence between a node or link number and its entry in the
   * omGraphicList/dbfTableModel.
   */
  private HashMap<Integer, Integer> numIndex = new HashMap<>();

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

  /**
   * Let the user the possibility to render the graphics according to the "style" field of the dbf
   * file.
   */
  private boolean renderStyles = false;

  /** Used in doPrepare() to detect if the styles must be be refreshed. */
  private float previousScale = -1;

  private float previousRenderingScaleThreshold;
  private boolean stylesMustBeRefreshed = true;

  /** All what we need to connect to the relevant dbf file. */
  private String tableName;

  /** All what we need to connect to the relevant dbf file. */
  private String tablePath;

  /** Reference to this class needed by a callback. */
  private NodusEsriLayer thisNodusEsriLayer;

  /** Query string used to filter EsriGraphics. */
  private String whereStmt = "";

  /**
   * Used by the integrity tester to store links which have same origin and destination.
   *
   * @param num Num of link to remove
   */
  public void addLinkToRemove(int num) {
    if (linksToRemove == null) {
      linksToRemove = new LinkedList<>();
    }

    linksToRemove.add(num);
  }

  /** Inserts a new num/index couple in the NumIndex HashMap. */
  private void addNumIndexForLastRecord() {
    int index = getModel().getRowCount() - 1;
    int num = JDBCUtils.getInt(getModel().getValueAt(index, NodusC.DBF_IDX_NUM));
    numIndex.put(Integer.valueOf(num), Integer.valueOf(index));
  }

  /**
   * Adds a node to the layer without opening the Dbf editor.
   *
   * @param point The node to add.
   * @param newNum The ID to associate to this new node.
   * @return True if added
   */
  public boolean addRecord(EsriPoint point, int newNum) {
    return addRecord(point, newNum, false);
  }

  /**
   * Adds a node to the layer and opens or not the Dbf editor.
   *
   * @param point The node to add.
   * @param newNum The ID to associate to this new node.
   * @param displayGUI If true, the DBF editor will be displayed
   * @return True if added
   */
  public boolean addRecord(EsriPoint point, int newNum, boolean displayGUI) {
    OMGraphicList list = getEsriGraphicList();

    if (list != null) {
      synchronized (list) {
        point.generate(getProjection());
        list.add(point);

        getModel().addBlankRecord();
        updateNumIndex();

        // Set new number at the right place in the new record
        Double num = Double.valueOf(newNum);
        getModel().setValueAt(num, getModel().getRowCount() - 1, NodusC.DBF_IDX_NUM);
        addNumIndexForLastRecord();

        // Set style (default = 0)
        getModel()
            .setValueAt(Double.valueOf(0), getModel().getRowCount() - 1, NodusC.DBF_IDX_STYLE);

        // Set transhipment state (default = 0)
        getModel()
            .setValueAt(Double.valueOf(0), getModel().getRowCount() - 1, NodusC.DBF_IDX_TRANSHIP);

        // Create sql stmt
        String sqlStmt =
            "INSERT INTO "
                + getTableName()
                + " ("
                + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
                + ") VALUES ("
                + num.toString()
                + ")";
        executeUpdateSqlStmt(sqlStmt);

        isCanceled = false;
        if (displayGUI) {
          // Now edit this new record
          graphicIndex = list.size() - 1;

          DbfEditDlg dbfDlg = new DbfEditDlg(this);
          Point p = MouseInfo.getPointerInfo().getLocation();
          dbfDlg.setLocation(p);
          GUIUtils.keepDialogInScreen(dbfDlg);
          dbfDlg.setVisible(true);
        }

        if (isCanceled) {
          removeLastRecord();
          return false;
        } else {
          dirtyShp = true;
          dirtyDbf = true;
          reloadLabels();
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Adds a link to the layer and opens the Dbf editor.
   *
   * @param link The link to add
   * @param newNumber The ID of the new link
   * @param numNode1 The ID of the origin node
   * @param numNode2 The ID of the destination node
   * @return True if added
   */
  public boolean addRecord(EsriPolyline link, int newNumber, int numNode1, int numNode2) {
    return addRecord(link, newNumber, numNode1, numNode2, true);
  }

  /**
   * Adds a link to the layer.
   *
   * @param link The link to add
   * @param newNumber The ID of the new link
   * @param numNode1 The ID of the origin node
   * @param numNode2 The ID of the destination node
   * @param displayGUI If true, opens the Dbf editor
   * @return True if added
   */
  public boolean addRecord(
      EsriPolyline link, int newNumber, int numNode1, int numNode2, boolean displayGUI) {

    OMGraphicList list = getEsriGraphicList();
    if (list != null) {
      synchronized (list) {
        list.add(link);
        getModel().addBlankRecord();
        updateNumIndex();

        // Set new number at the right place in the new record
        Double num = Double.valueOf(newNumber);
        getModel().setValueAt(num, getModel().getRowCount() - 1, NodusC.DBF_IDX_NUM);

        // Set style (default = 0)
        getModel()
            .setValueAt(Double.valueOf(0), getModel().getRowCount() - 1, NodusC.DBF_IDX_STYLE);

        // Set "enabled" (default = 1)
        getModel()
            .setValueAt(Double.valueOf(1), getModel().getRowCount() - 1, NodusC.DBF_IDX_ENABLED);

        // We also have to set the end nodes,
        Double node = Double.valueOf(numNode1);
        getModel().setValueAt(node, getModel().getRowCount() - 1, NodusC.DBF_IDX_NODE1);
        node = Double.valueOf(numNode2);
        getModel().setValueAt(node, getModel().getRowCount() - 1, NodusC.DBF_IDX_NODE2);
        addNumIndexForLastRecord();

        // Create sql stmt
        String sqlStmt =
            "INSERT INTO "
                + getTableName()
                + " ("
                + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
                + ") VALUES ("
                + num.toString()
                + ")";
        executeUpdateSqlStmt(sqlStmt);

        sqlStmt =
            "UPDATE "
                + getTableName()
                + " SET "
                + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE1)
                + " = "
                + numNode1
                + ", "
                + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NODE2)
                + " = "
                + numNode2
                + " WHERE "
                + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
                + " = "
                + newNumber;
        executeUpdateSqlStmt(sqlStmt);

        isCanceled = false;
        if (displayGUI) {
          // Now edit this new record
          graphicIndex = list.size() - 1;

          DbfEditDlg dbfDlg = new DbfEditDlg(this);
          Point p = MouseInfo.getPointerInfo().getLocation();
          dbfDlg.setLocation(p);
          GUIUtils.keepDialogInScreen(dbfDlg);
          dbfDlg.setVisible(true);
        }

        if (isCanceled) {
          removeLastRecord();
          return false;
        } else {
          dirtyShp = true;
          dirtyDbf = true;
          reloadLabels();
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Adds a node or link for which the record is known.
   *
   * @param graphic OMGraphic The node or link to add.
   * @param record ArrayList The record to associate to this new node or link.
   */
  public void addRecord(EsriPolyline graphic, List<Object> record) {
    OMGraphicList list = getEsriGraphicList();
    list.add(graphic);
    getModel().addRecord(record);
    updateNumIndex();

    String sqlStmt = "INSERT INTO ";
    sqlStmt += getTableName() + " VALUES( ";

    for (int i = 0; i < getModel().getColumnCount(); i++) {
      Object cell = record.get(i);
      byte type = getModel().getType(i);

      if (type == DbfTableModel.TYPE_NUMERIC) {
        if (getModel().getDecimalCount(i) > 0) {
          sqlStmt += JDBCUtils.getDouble(cell);
        } else {
          sqlStmt += JDBCUtils.getInt(cell);
        }
      } else {
        String s = cell.toString();
        sqlStmt += '\'' + s.replaceAll("'", "''") + '\'';
      }

      if (i < getModel().getColumnCount() - 1) {
        sqlStmt += ", ";
      }
    }

    sqlStmt += ")";
    
    executeUpdateSqlStmt(sqlStmt);

    reloadLabels();
    dirtyDbf = true;
    dirtyShp = true;
  }

  /**
   * Applies a SQL query to filter the objects to display.
   *
   * @param whereStmt A SQL query string ("WHERE ...")
   */
  public void applyWhereFilter(String whereStmt) {
    // Save the "where" statement
    this.whereStmt = whereStmt;
    nodusProject.setLocalProperty(tableName + NodusC.PROP_WHERESTMT, this.whereStmt);

    // Create complete query string
    String sqlstmt =
        "SELECT " + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM) + " FROM " + tableName;

    // Add a where condition if it exists
    if (this.whereStmt.length() > 0) {
      sqlstmt += " WHERE " + this.whereStmt;
    }

    stylesMustBeRefreshed = true;

    /*
     * First prepare the existing list of graphics. If a "WHERE" statement is found in the query
     * string, all the graphics must be set to non-visible, then only the graphics that are in the
     *  result query set will be set to visible. If no "WHERE" statement is found, all the
     *  graphics are set to be visible.
     */
    if (sqlstmt.indexOf("WHERE") < 0) {
      setVisibility(true);

      return;
    }

    /*
     * We have to query the database...
     */
    setVisibility(false);

    try {
      // connect to database and execute query
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sqlstmt);

      // Retrieve result of query
      EsriGraphicList list = getEsriGraphicList();

      while (rs.next()) {
        // Index for 'num' in graphicList
        int index = getNumIndex(JDBCUtils.getInt(rs.getObject(1)));

        OMGraphic omGraphic = list.getOMGraphicAt(index);
        omGraphic.setVisible(true);
      }
      rs.close();
      stmt.close();

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          null,
          ex.toString(),
          i18n.get(NodusEsriLayer.class, "SQL_error", "SQL error"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /** Attach the rendering style to all the graphics of the list. */
  public void attachStyles() {
    EsriGraphicList list = getEsriGraphicList();
    Iterator<OMGraphic> lit = list.iterator();
    int index = 0;

    while (lit.hasNext()) {
      OMGraphic omGraphic = lit.next();
      if (omGraphic.isVisible() || displayResults) {
        omGraphic.setSelected(false);
        attachStyle(omGraphic, index);
      }
      index++;
    }
  }

  /**
   * Set the rendering style to a given OMGraphic.
   *
   * @param omGraphic The graphic to which the style must be attached.
   * @param index The index of this graphic in the graphic list.
   */
  public void attachStyle(OMGraphic omGraphic, int index) {

    NodusOMGraphic model = getStyle(omGraphic, index);

    if (model == null) {
      return;
    }

    float scale = getNodusMapPanel().getMapBean().getScale();
    float renderingThreshold = getNodusMapPanel().getRenderingScaleThreshold();

    // Only display basic geometry with a color
    if (!renderStyles || (scale > renderingThreshold && renderingThreshold != -1)) {
      if (omGraphic instanceof EsriPolyline) {
        // Draw 1 pixel line, with default color
        EsriPolyline epl = (EsriPolyline) omGraphic;
        epl.setStroke(new BasicStroke(1));
        epl.setLinePaint(model.getDefaultLinePaint());
        epl.setMatted(false);
      } else {
        /*
         * Draw a small empty rectangle, with default color
         */
        EsriPoint point = (EsriPoint) omGraphic;
        point.setOval(false);
        point.setRadius(2);
        point.setLinePaint(model.getDefaultLinePaint());
        point.setFillPaint(null);
      }
      return;
    }

    if (omGraphic instanceof EsriPolyline) {
      /* Render links */
      EsriPolyline epl = (EsriPolyline) omGraphic;
      Paint lineColor = model.getLineColor();
      Paint matingColor = model.getMattingPaint();

      /*
       * If a result is attached to this graphic, create a new stroke with the width
       *  stored as result.
       */
      if (displayResults) {

        RealLink rl = (RealLink) omGraphic.getAttribute(0);

        if (rl.getSize() != 0) {
          float width = rl.getSize();

          /* Make a visual difference between positive and negative values,
           * using the alternative colors.
           */
          if (width < 0) {
            width *= -1;
            lineColor = model.getAltLinePaint();
            matingColor = model.getAltMattingPaint();
          }

          BasicStroke bs = (BasicStroke) model.getStroke();
          epl.setStroke(
              new BasicStroke(
                  width,
                  bs.getEndCap(),
                  bs.getLineJoin(),
                  bs.getMiterLimit(),
                  bs.getDashArray(),
                  bs.getDashPhase()));
          epl.setVisible(true);
        } else {
          epl.setVisible(false);
        }
      } else {
        epl.setStroke(model.getStroke());
      }

      epl.setLinePaint(lineColor);
      epl.setMattingPaint(matingColor);
      epl.setMatted(model.isMatted());
    } else {
      /* Render nodes */
      EsriPoint p = (EsriPoint) omGraphic;
      int radius;

      Paint lineColor = model.getLineColor();
      Paint fillColor = model.getFillColor();

      if (displayResults) {
        RealNetworkObject rnbo = (RealNetworkObject) omGraphic.getAttribute(0);
        radius = (int) rnbo.getSize();

        if (radius == 0) {
          p.setVisible(false);
        } else {
          /*
           * Make a visual difference between positive and negative values,
           * using the alternative colors.
           */
          if (radius < 0) {
            radius *= -1;
            lineColor = model.getAltLinePaint();
            fillColor = model.getAltFillPaint();
          }
        }
      } else {
        radius = model.getRadius();
      }

      p.setRadius(radius);
      p.setLinePaint(lineColor);
      p.setFillPaint(fillColor);
      p.setOval(model.isOval());
      p.setStroke(model.getStroke());
    }
  }

  /**
   * This methods updates the styles depending in the rendering scale threshold before calling the
   * original method.
   */
  @Override
  public void doPrepare() {
    isReady = false;

    float currentScale = getNodusMapPanel().getMapBean().getScale();
    float renderingScaleThreshold = getNodusMapPanel().getRenderingScaleThreshold();

    // First call
    if (stylesMustBeRefreshed) {
      previousScale = currentScale;
      previousRenderingScaleThreshold = renderingScaleThreshold;
    } else {
      if (previousRenderingScaleThreshold != renderingScaleThreshold) {
        stylesMustBeRefreshed = true;
        previousRenderingScaleThreshold = renderingScaleThreshold;
      } else {
        if (previousScale > renderingScaleThreshold && currentScale <= renderingScaleThreshold) {
          stylesMustBeRefreshed = true;
        } else {
          if (previousScale <= renderingScaleThreshold && currentScale > renderingScaleThreshold) {
            stylesMustBeRefreshed = true;
          }
        }
        previousScale = currentScale;
      }
    }

    if (stylesMustBeRefreshed) {
      attachStyles();
      stylesMustBeRefreshed = false;
    }

    isReady = true;
    super.doPrepare();
  }

  /** Called by getGUI(). Displays the table model and allows to edit its structure */
  private void editTableModel() {
    // Get the original table structure if needed
    if (originalColumCount == -1) {
      getOriginalTableStructure();
    }
    getModel().showGUI(tableName, DbfTableModel.MODIFY_COLUMN_MASK | DbfTableModel.DONE_MASK);
  }

  /**
   * Commits an update statement to the DBMS.
   *
   * @param sqlStmt The SQL update sentence.
   */
  public void executeUpdateSqlStmt(final String sqlStmt) {
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      stmt.executeUpdate(sqlStmt);
      stmt.close();
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
  }

  /**
   * Creates a new shape file that contains a subset of the one associated with this layer. The
   * WHERE statement is used in a SQL query to filter the shapes to extract.
   *
   * @param toFile Name of the new shapefile to create
   * @param whereStmt SQL where statement used to filter the shapes to export
   * @return True on success.
   */
  public boolean extract(String toFile, String whereStmt) {

    // Create query string
    String sqlstmt =
        "SELECT "
            + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + " FROM "
            + tableName
            + " WHERE "
            + whereStmt;

    // Create an EsriGraphic list that will hold the shapes to save
    EsriGraphicList egl;

    if (getType() == SHAPE_TYPE_POLYLINE) {
      egl = new EsriPolylineList();
    } else {
      egl = new EsriPointList();
    }

    // Create a dbfTableModel for the shapes to save
    DbfTableModel tableModel = getModel().headerClone();

    try {
      // connect to database and execute query
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sqlstmt);

      // Retrieve result of query
      EsriGraphicList list = getEsriGraphicList();

      while (rs.next()) {
        // Index for 'num' in graphicList
        int index = getNumIndex(JDBCUtils.getInt(rs.getObject(1)));

        // Get graphic and save it in list
        OMGraphic omGraphic = list.getOMGraphicAt(index);
        egl.add(omGraphic);

        // Get dbf record and save it in table
        tableModel.addRecord(getModel().getRecord(index));
      }

      rs.close();
      stmt.close();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          null,
          ex.getMessage(),
          i18n.get(NodusEsriLayer.class, "SQL_error", "SQL error"),
          JOptionPane.ERROR_MESSAGE);

      return false;
    }

    /*
     * Save the extracted shapes, but save dbf file separately as openMap routine is broken...
     */
    EsriShapeExport exporter = new EsriShapeExport(egl, (DbfTableModel) null, tablePath + toFile);
    exporter.setWriteDBF(false);
    exporter.export();
    egl.clear();

    // Save the dbf file
    ExportDBF.exportTable(nodusProject, toFile + NodusC.TYPE_DBF, tableModel);

    return true;
  }

  /**
   * Fix non standard ShapeFiles, written by OpenMap.
   *
   * @param shapeFileName The name of the shapefile
   */
  private void fixShapeFile(String shapeFileName) {

    try {
      URL shx = PropUtils.getResourceOrFileOrURL(shapeFileName + ".shx");
      InputStream is = shx.openStream();
      ShxInputStream pis = new ShxInputStream(is);
      int[][] index = pis.getIndex();
      is.close();

      // Do not test empty shapefiles
      if (index[0].length == 0) {
        return;
      }

      RandomAccessFile raf = new RandomAccessFile(shapeFileName + ".shp", "rw");
      raf.seek(24);
      int contentLength = raf.readInt();

      int indexedContentLength = index[0][index[0].length - 1] + index[1][index[1].length - 1];

      if (contentLength != indexedContentLength) {
        raf.seek(24);
        raf.writeInt(indexedContentLength);
      }
      raf.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Get the index of the "copied" record.
   *
   * @return int Index of the record to copy.
   */
  public int getCopyRecordIndex() {
    return copyRecordIndex;
  }

  /**
   * Returns the state of the display of the DbfTooltips.
   *
   * @return boolean True if the Dbf tooltips must be displayed.
   */
  public boolean getDisplayDbfToolTips() {
    return displayDbfTooltips;
  }

  /**
   * Simple GUI that displays two buttons : one for the properties, another for the table structure.
   *
   * @return Component
   */
  @Override
  public Component getGUI() {

    final JPanel holder = new JPanel(new GridLayout(0, 1));

    JButton selectProperties =
        new JButton(i18n.get(NodusEsriLayer.class, "Select_properties", "Select properties"));

    holder.add(selectProperties);
    selectProperties.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            SelectPropertiesDlg dlg = new SelectPropertiesDlg(thisNodusEsriLayer);

            // Hide the GUI
            Component c = holder.getParent();
            while (c != null) {
              c.setVisible(false);
              c = c.getParent();
            }
            dlg.setVisible(true);
          }
        });

    JButton editDatabase =
        new JButton(i18n.get(NodusEsriLayer.class, "Edit_database", "Edit database"));
    holder.add(editDatabase);
    editDatabase.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // Hide the GUI
            Component c = holder.getParent();
            while (c != null) {
              c.setVisible(false);
              c = c.getParent();
            }
            editTableModel();
          }
        });

    return holder;
  }

  /**
   * Returns the name of the variable that will represent this layer in the cost functions.
   *
   * <p>The variable itself can contain a 0 or 1 value.
   *
   * <p>A cost function can thus be related to the layer in which an object is stored. For instance,
   * if variable name is "MyLayer", a cost function could be "MyLayer*(someCostExpression) +
   * (1-MyLayer)*(SomeOtherCostExpression)". Thus, "SomeCostExpression" will be applied to the
   * objects of MyLayer and "SomeOtherCostExpression" to objects belonging to other layers
   *
   * @return The "layer" variable name.
   */
  public String getLayerVariableName() {
    if (layerVarName == null) {
      String key = getTableName() + NodusC.PROP_VARIABLE_NAME;
      layerVarName = nodusProject.getLocalProperty(key, getTableName());
    }

    return layerVarName;
  }

  /**
   * Returns the NodusLocationHandler associated to this layer.
   *
   * @return NodusLocationHandler
   */
  public NodusLocationHandler getLocationHandler() {
    return nodusLocationHandler;
  }

  /**
   * Computes total length of a Polyline expressed in kilometers.
   *
   * @param epl EsriPolyline
   * @param unit Length
   * @return float
   */
  public static float getLength(EsriPolyline epl, Length unit) {
    float length = 0;
    double[] ll = epl.getLatLonArray();
    int nbPairs = ll.length / 2;

    for (int k = 0; k < nbPairs - 1; k++) {
      length +=
          GreatCircle.sphericalDistance(ll[k * 2], ll[k * 2 + 1], ll[k * 2 + 2], ll[k * 2 + 3]);
    }

    return unit.fromRadians(length);
  }

  /**
   * Returns the main frame this layer belongs to.
   *
   * @return nodusMapPanel
   */
  public NodusMapPanel getNodusMapPanel() {
    return nodusProject.getNodusMapPanel();
  }

  /**
   * Returns a HashMap which retrieved the index (position) of a given node or link ID in the
   * ShapeFile.
   *
   * @return The HashMap.
   */
  public HashMap<Integer, Integer> getIndex() {
    return numIndex;
  }

  /**
   * Retrieves the index of an object in the ShapeFile from the "num" field in the .dbf file. Return
   * -1 if not found.
   *
   * @param num The ID of a node or link.
   * @return The index of the object in the ShapeFile.
   */
  public int getNumIndex(int num) {
    if (numIndex.size() == 0) {
      updateNumIndex();
    }

    Object o = numIndex.get(Integer.valueOf(num));

    if (o == null) {
      return -1;
    }

    Integer index = (Integer) o;

    return index.intValue();
  }

  /**
   * As the table structure can be modified, one need its original structure in order to test any
   * change.
   */
  private void getOriginalTableStructure() {
    originalColumCount = getModel().getColumnCount();
    originalLength = new int[originalColumCount];
    originalDecimalcount = new byte[originalColumCount];
    originalType = new byte[originalColumCount];
    originalName = new String[originalColumCount];

    for (int i = 0; i < originalColumCount; i++) {
      originalDecimalcount[i] = getModel().getDecimalCount(i);
      originalLength[i] = getModel().getLength(i);
      originalName[i] = getModel().getColumnName(i);
      originalType[i] = getModel().getType(i);
    }
  }

  /**
   * Returns the index of the selected graphic.
   *
   * @return int The index of the selected graphic in the ShapeFile.
   */
  public int getSelectedGraphicIndex() {
    return graphicIndex;
  }

  /**
   * Returns the Style of a give OMGraphic, embedded in a NodusOmGraphic.
   *
   * @param omg OMGraphic
   * @param index int
   * @return NodusOMGraphic
   */
  public NodusOMGraphic getStyle(OMGraphic omg, int index) {
    int style = 0;

    List<Object> values = getModel().getRecord(index);

    style = (int) Float.parseFloat(values.get(NodusC.DBF_IDX_STYLE).toString());

    int nbStyles = nodusProject.getNbStyles(omg);

    if (nbStyles > 0) {
      if (style < 0 || style >= nbStyles) {
        System.err.println(NodusC.DBF_STYLE + " value out of bounds");

        return null;
      }
    }

    return nodusProject.getStyle(omg, style);
  }

  /**
   * Returns the state of the display of the style rendering.
   *
   * @return boolean True if the styles of the graphics must be rendered.
   */
  public boolean getStyleRendering() {
    return renderStyles;
  }

  /**
   * Returns the name of the shape files (without any path or extensions).
   *
   * @return String
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * Returns the path to the shape files.
   *
   * @return String
   */
  public String getTablePath() {
    return tablePath;
  }

  /**
   * Returns a string representing the content of the .dbf record of a given graphic. The returned
   * string is HTML code, or null if the tooltips must not be displayed.
   *
   * @param omg The graphic for which the description must be displayed.
   * @return String HTML code or null.
   */
  @Override
  public String getToolTipTextFor(OMGraphic omg) {

    if (displayDbfTooltips) {
      return super.getToolTipTextFor(omg);
    } else {
      return null;
    }
  }

  /**
   * Returns the type (PolyLine or Point) of the graphics stored in this layer.
   *
   * @return SHAPE_TYPE_POLYLINE or SHAPE_TYPE_POINT.
   */
  @Override
  public int getType() {
    int type = SHAPE_TYPE_POLYLINE;
    EsriGraphicList egl = getEsriGraphicList();

    if (egl instanceof EsriPointList) {
      type = SHAPE_TYPE_POINT;
    }

    return type;
  }

  /**
   * Returns the current WHERE statement used to filter the graphics to display.
   *
   * @return String
   */
  public String getWhereStmt() {
    return whereStmt;
  }

  /**
   * Tests if the OmGraphics, the table model or the table structure were modified.
   *
   * @return True if something was modified since last save.
   */
  public boolean isDirty() {
    if (dirtyShp || dirtyDbf || isTableStructureChanged()) {
      return true;
    }

    return false;
  }

  /**
   * Returns the state of the "results" display.
   *
   * @return True if the layer currently displays a "result" (OD or assignment).
   */
  public boolean isDisplayResults() {
    return displayResults;
  }

  /**
   * Returns the state of the layer.
   *
   * @return True if the graphics are ready to be displayed.
   */
  public boolean isReady() {
    return isReady;
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

    // else ...
    if (getModel().getColumnCount() != originalColumCount) {
      return true;
    }

    for (int i = 0; i < getModel().getColumnCount(); i++) {
      if (originalDecimalcount[i] != getModel().getDecimalCount(i)) {
        return true;
      }

      if (originalLength[i] != getModel().getLength(i)) {
        return true;
      }

      if (!originalName[i].equals(getModel().getColumnName(i))) {
        return true;
      }

      if (originalType[i] != getModel().getType(i)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Tests the existence of a given node or link number.
   *
   * @param num int
   * @return True if objet ID "num" exists in this layer.
   */
  public boolean numExists(int num) {
    if (numIndex == null) {
      updateNumIndex();
    }

    Integer n = Integer.valueOf(num);

    if (numIndex != null && numIndex.get(n) == null) {
      return false;
    }

    return true;
  }

  /** Removes all the links for which node1 = node2 (loops). */
  public void purgeLinks() {
    if (linksToRemove != null) {
      Iterator<Integer> it = linksToRemove.iterator();
      logger.info("Purge project");
      while (it.hasNext()) {
        Integer linkNum = it.next();

        // Get index of object to remove
        Integer idx = numIndex.get(linkNum);

        if (idx != null) {
          System.out.println(
              MessageFormat.format(
                  i18n.get(NodusEsriLayer.class, "Removing_link", "Removing link {0}"),
                  linkNum.intValue()));
          removeRecord(idx.intValue(), false);
        }
      }

      linksToRemove.clear();
      linksToRemove = null;
      System.gc();
    }
  }

  /** Forces the associated LocationHandler to reload its data. */
  public void reloadLabels() {
    if (nodusLocationHandler != null) {
      if (nodusLocationHandler.isShowLocations() || nodusLocationHandler.isShowNames()) {
        nodusLocationHandler.reset();
        nodusLocationHandler.reloadData();
        nodusLocationHandler.getLayer().doPrepare();
      }
    }
  }

  /**
   * Removes the last created record. Used when an edit operation on a new created link or nodes is
   * canceled.
   */
  void removeLastRecord() {
    // Find index and num of last recoord
    int index = getModel().getRowCount() - 1;

    // Remove the shape in list
    EsriGraphicList list = getEsriGraphicList();

    synchronized (list) {
      list.remove(index);
    }

    // Keep the ID of the record for further use
    int num = JDBCUtils.getInt(getModel().getValueAt(index, NodusC.DBF_IDX_NUM));

    // Remove the dbf record in memory
    getModel().remove(index);

    // remove NumIndex(num);
    updateNumIndex();

    // Remove the dbf record in the database table
    String sqlStmt =
        "DELETE FROM "
            + getTableName()
            + " WHERE "
            + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + " = "
            + num;
    executeUpdateSqlStmt(sqlStmt);
  }

  /**
   * Removes the given graphic.
   *
   * @param index The index of the graphic to remove.
   */
  public void removeRecord(int index) {
    removeRecord(index, true);
  }

  /**
   * Removes the given graphic.
   *
   * @param index The index of the graphic to remove.
   * @param refreshLabels If true, forces the labels to be refreshed.
   */
  public void removeRecord(int index, boolean refreshLabels) {
    EsriGraphicList list = getEsriGraphicList();

    synchronized (list) {
      list.remove(index);

      // Find 'num' field value for this index
      int num = JDBCUtils.getInt(getModel().getValueAt(index, NodusC.DBF_IDX_NUM));

      getModel().remove(index);

      // removeNumIndex(num);
      updateNumIndex();

      String sqlStmt =
          "DELETE FROM "
              + getTableName()
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + " = "
              + num;
      executeUpdateSqlStmt(sqlStmt);
      dirtyShp = true;
      dirtyDbf = true;
    }

    if (refreshLabels) {
      reloadLabels();
    }
  }

  /**
   * The rollback mechanism just deletes the SQL table associated with the .dbf file. In order to
   * keep the .dbf file and the SQL table synchronized, the latest must be deleted if the user
   * decides not to save the changes he made into the dbfTable.
   */
  public void rollback() {
    if (dirtyDbf) {
      jdbcUtils.dropTable(getTableName());
    }
  }

  /**
   * The Save method writes the data into .shp, .shx and .dbf files. It also updates the imported
   * .dbf tables.
   */
  public void save() {

    nodusProject.getNodusMapPanel().setBusy(true);

    // ESRI shape file
    if (dirtyShp) {
      String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

      if (getModel().getRowCount() > 0) {
        String filePath = path + tableName;
        File shpFile = new File(filePath + NodusC.TYPE_SHP);
        File shxFile = new File(filePath + NodusC.TYPE_SHX);
        try {
          ShpOutputStream pos = new ShpOutputStream(new FileOutputStream(shpFile));
          int[][] indexData = pos.writeGeometry(getEsriGraphicList());
          ShxOutputStream xos = new ShxOutputStream(new FileOutputStream(shxFile));
          xos.writeIndex(indexData, getEsriGraphicList().getType());
        } catch (IOException ex) {
          System.out.println(ex.toString());
        }

      } else { // Openmap doesn't properly save empty shape files!
        ProjectFilesTools.createEmptyLayer(path, tableName, getType(), path + tableName);
      }
    }

    boolean isStructureChanged = isTableStructureChanged();

    // Associated .dbf file (broken in OpenMap 4.5.4)
    if (dirtyDbf || isStructureChanged) {
      ExportDBF.exportTable(nodusProject, tableName + NodusC.TYPE_DBF, getModel());
    }

    if (isStructureChanged) {
      // Reimport table
      ImportDBF.importTable(nodusProject, tableName);
    }

    // Reset the 'dirty' state
    dirtyShp = false;
    dirtyDbf = false;
    getOriginalTableStructure();

    nodusProject.getNodusMapPanel().setBusy(false);
  }

  /**
   * Saves a given record in the database.
   *
   * @param num The ID of the node or link to save
   */
  void saveRecord(int num) {
    Object cell;
    String sqlStmt = "UPDATE ";
    sqlStmt += getTableName() + " SET ";

    for (int i = 0; i < getModel().getColumnCount(); i++) {
      sqlStmt += getModel().getColumnName(i) + '=';
      cell = getModel().getValueAt(getSelectedGraphicIndex(), i);

      byte type = getModel().getType(i);

      if (type == DbfTableModel.TYPE_NUMERIC) {
        if (getModel().getDecimalCount(i) > 0) {
          sqlStmt += JDBCUtils.getDouble(cell);
        } else {
          sqlStmt += JDBCUtils.getInt(cell);
        }
      } else {
        sqlStmt += '\'' + cell.toString() + '\'';
      }

      if (i < getModel().getColumnCount() - 1) {
        sqlStmt += ", ";
      }
    }

    sqlStmt += " WHERE " + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM) + " = " + num;
    executeUpdateSqlStmt(sqlStmt);
    dirtyDbf = true;
  }

  /**
   * Intercepts <i>mouseClicked </i> events to display a <b>DbfDlg </b> in order to edit the DBF
   * record associated with the selected shape.
   *
   * <p>A double click opens the DBF editor.
   *
   * <p>A single click, while the service editor is listening, adds or removes the graphic to the
   * service.
   *
   * @param omgl The graphic to select.
   */
  @Override
  public void select(OMGraphicList omgl) {
    EsriGraphicList list = getEsriGraphicList();

    ServiceEditor serviceEditor = nodusProject.getServiceEditor();
    graphicIndex = -1;

    MouseEvent me = getMouseEventInterpreter().getCurrentMouseEvent();

    if (list != null) {

      OMGraphic omg = omgl.get(0);

      if (omg != null) {

        // graphicIndex has to be set before selectEntry called.
        graphicIndex = list.indexOf(omg);

        if (graphicIndex != -1) {
          // Double click for database editing
          if (me.getClickCount() == 2) {
            omg.select();
            omg.render(getGraphics());

            if (omg.isVisible()) {
              // Display the dialog close to the graphic to edit
              DbfEditDlg dbfDlg = new DbfEditDlg(this);

              Point p = me.getLocationOnScreen();
              dbfDlg.setLocation(p);
              GUIUtils.keepDialogInScreen(dbfDlg);
              dbfDlg.setVisible(true);

              omg.deselect();
              omg.render(getGraphics());

              // Repaint the line that is being edited if there is one
              nodusProject.getServiceEditor().paintService(true);
              repaint();
            }

          } else {
            if (me.getButton() == MouseEvent.BUTTON1 && serviceEditor.isListening()) {
              List<Object> record = getModel().getRecord(graphicIndex);

              if (serviceEditor.addOrRemoveLink(omg, record)) {
                repaint();
              }

            } else {
              me.consume();
            }
          }
        }
      }
    }
  }

  /**
   * Used by DbfEditDlg to let this layer know if an edit operation was canceled.
   *
   * @param canceled True to cancel the edition.
   */
  public void setCanceled(boolean canceled) {
    isCanceled = canceled;
  }

  /**
   * Save the index of the record that is copied.
   *
   * @param index Index of the record
   */
  public void setCopyRecordIndex(int index) {
    copyRecordIndex = index;
  }

  /**
   * Must be called once there is any change in the .dbf records.
   *
   * @param flag True to tell that the .dbf file will need to be saved.
   */
  public void setDirtyDbf(boolean flag) {
    dirtyDbf = flag;
  }

  /**
   * Must be called when there is any change in the OMGraphic.
   *
   * @param flag True to tell that the .shp file will need to be saved.
   */
  public void setDirtyShp(boolean flag) {
    dirtyShp = flag;
  }

  /**
   * Enables/disables the display of the DbfTooltips.
   *
   * @param displayDbfTooltips True or false
   */
  public void setDisplayDbfToolTips(boolean displayDbfTooltips) {
    this.displayDbfTooltips = displayDbfTooltips;
    nodusProject.setLocalProperty(tableName + NodusC.PROP_DBF_TOOLTIPS, displayDbfTooltips);
  }

  /**
   * Enables/disables the "results" display.
   *
   * @param displayResults True or false
   */
  public void setDisplayResults(boolean displayResults) {
    this.displayResults = displayResults;
  }

  /**
   * Associates a NodusLocationHandler to this layer.
   *
   * @param nodusLocationHandler Location handler
   */
  public void setLocationHandler(NodusLocationHandler nodusLocationHandler) {
    this.nodusLocationHandler = nodusLocationHandler;
  }

  /**
   * Creates the layer and associates it with the Nodus project.
   *
   * @param nodusProject Nodus project
   * @param layerName Layer name
   */
  public void setProject(NodusProject nodusProject, String layerName) {

    this.nodusProject = nodusProject;
   
    if (layerName == null) {
      System.err.println("NodusEsriLayer.setProject: layer name is null");
      return;
    }

    // Basic settings needed to create a valid EsriLayer
    jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());

    tablePath = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    tableName = nodusProject.getLocalProperty(layerName + NodusC.PROP_NAME);

    /*
     * Fix the shape file if bugged (shape created/ files written by means of
     * OpenMap versions prior to 4.6.4 where wrong).
     */
    fixShapeFile(tablePath + tableName);

    Properties p = new Properties();
    String shapeFile = tablePath + tableName + NodusC.TYPE_SHP;
    String indexFile = tablePath + tableName + NodusC.TYPE_SHX;
    String dbfFile = tablePath + tableName + NodusC.TYPE_DBF;
    p.setProperty(layerName + NodusC.TYPE_SHP, shapeFile);
    p.setProperty(layerName + NodusC.TYPE_SHX, indexFile);
    p.setProperty(layerName + NodusC.TYPE_DBF, dbfFile);

    setProperties(layerName, p);

    // Restore saved settings
    displayDbfTooltips =
        Boolean.valueOf(nodusProject.getLocalProperty(tableName + NodusC.PROP_DBF_TOOLTIPS, "true"))
            .booleanValue();
    renderStyles =
        Boolean.valueOf(
                nodusProject.getLocalProperty(tableName + NodusC.PROP_RENDER_STYLES, "true"))
            .booleanValue();
    whereStmt = nodusProject.getLocalProperty(tableName + NodusC.PROP_WHERESTMT, "");

    // Verify if dbf table must be imported in database
    if (!jdbcUtils.tableExists(layerName)) {
      nodusProject
          .getNodusMapPanel()
          .setText(
              MessageFormat.format(
                  i18n.get(NodusEsriLayer.class, "Importing", "Importing \"{0}\" in database"),
                  layerName));
      ImportDBF.importTable(nodusProject, layerName);
    }

    /* Attach a RealLink or a RealNode object to the graphic... */
    EsriGraphicList list = getEsriGraphicList();
    Iterator<OMGraphic> lit = list.iterator();
    while (lit.hasNext()) {
      OMGraphic omGraphic = lit.next();
      if (omGraphic instanceof EsriPolyline) {
        omGraphic.putAttribute(0, new RealLink());
      } else {
        omGraphic.putAttribute(0, new RealNode());
      }
    }

    thisNodusEsriLayer = this;

    applyWhereFilter(whereStmt);

    updateNumIndex();

    setConsumeEvents(true);
  }

  /**
   * Enables/disables the rendering of the styles of the graphics.
   *
   * @param renderStyles True or false
   */
  public void setStyleRendering(boolean renderStyles) {
    this.renderStyles = renderStyles;
    nodusProject.setLocalProperty(tableName + NodusC.PROP_RENDER_STYLES, renderStyles);
  }

  /**
   * Set/resets the visibility of the graphics in the list.
   *
   * @param visibility True or false
   */
  void setVisibility(boolean visibility) {
    EsriGraphicList list = getEsriGraphicList();
    Iterator<OMGraphic> lit = list.iterator();

    while (lit.hasNext()) {
      OMGraphic omg = lit.next();
      omg.setVisible(visibility);
    }
  }

  /**
   * This method updates the content of the DbfTable (and writes it to disk) with the content of the
   * DBMS table.
   *
   * @return True if the table was successfully updated and saved.
   */
  public boolean updateDbfTableModel() {
    try {
      Connection con = nodusProject.getMainJDBCConnection();

      DbfTableModel model = getModel();

      Statement stmt = con.createStatement();

      String sqlStmt = "SELECT * FROM " + tableName;
      ResultSet rs = stmt.executeQuery(sqlStmt);
      ResultSetMetaData rsmd = rs.getMetaData();
      int nbColumns = rsmd.getColumnCount();

      // TODO Add more tests to UpdateDbfTableModel.
      // Or, better, allow for structure change (but not the mandatory fields)

      // - nb rows
      // - table structure
      if (nbColumns != model.getColumnCount()) {
        System.err.println(
            i18n.get(
                NodusEsriLayer.class,
                "Incompatible_table_structure",
                "Incompatible table structure."));
        return false;
      }

      Object[] o = new Object[nbColumns];

      // Retrieve result of query
      while (rs.next()) {
        int num = -1;

        for (int i = 0; i < nbColumns; i++) {
          o[i] = rs.getObject(i + 1);

          if (i == 0) {
            num = JDBCUtils.getInt(o[i]);
          }
        }

        // Get record that corresponds to this num
        int index = getNumIndex(num);

        if (index == -1) {
          System.err.println(
              MessageFormat.format(
                  i18n.get(NodusEsriLayer.class, "Record_not_found", "Record {0} not found in {1}"),
                  num,
                  tableName));

          return false;
        }

        // Update the record in the dbftableModel
        for (int i = 0; i < model.getColumnCount(); i++) {
          model.setValueAt(o[i], index, i);
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      System.out.println(ex.toString());
      return false;
    }

    // isReady = false;
    doPrepare();

    dirtyDbf = false;

    return true;
  }

  /** HashMap to retrieve the object index from the "num" field in .dbf. */
  private void updateNumIndex() {
    numIndex.clear();

    DbfTableModel model = getModel();

    for (int i = 0; i < model.getRowCount(); i++) {
      int num = JDBCUtils.getInt(getModel().getValueAt(i, NodusC.DBF_IDX_NUM));
      numIndex.put(Integer.valueOf(num), Integer.valueOf(i));
    }
  }
}
