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

package edu.uclouvain.core.nodus.services;

import com.bbn.openmap.event.NavMouseMode;
import com.bbn.openmap.event.SelectMouseMode;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.gui.ServiceEditorDlg;
import java.awt.Graphics;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import javax.swing.JOptionPane;

/**
 * This class manages the editing process for services.
 *
 * @author Galina Iassinovskaia
 */
// TODO (services) Still buggy. Test in interaction with the drawing tool
public class ServiceEditor {

  private static final int TYPE_LINK = 1;
  private static final int TYPE_NODE = 0;

  /** Current service to edit. */
  private Service currentService;

  private ServiceEditorDlg serviceEditorDlg;

  /** The painting environment, that will correspond to the Mapbean. */
  private Graphics graphics;

  private Connection jdbcConnection;

  private NodusEsriLayer[] linkLayer;

  private boolean listening = false;

  boolean mustBeSaved = false;

  private NodusEsriLayer[] nodeLayer;

  private NodusProject nodusProject;

  private String serviceHeaderTableName;

  private String serviceLinkDetailTableName;

  /** TreeMap that contains the services. */
  private TreeMap<String, Service> services = new TreeMap<>();

  private String serviceStopDetailTableName;

  /**
   * Creates a new ServiceEditor.
   *
   * @param nodusProject A Nodus project.
   */
  public ServiceEditor(NodusProject nodusProject) {

    this.nodusProject = nodusProject;
    // nodusMapPanel = nodusProject.getNodusMapPanel();

    graphics = nodusProject.getNodusMapPanel().getMapBean().getGraphics();

    linkLayer = nodusProject.getLinkLayers();
    nodeLayer = nodusProject.getNodeLayers();

    // Prepare table names
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_SERVICE;
    String name = nodusProject.getLocalProperty(NodusC.PROP_SERVICE_TABLE_PREFIX, defValue);

    serviceHeaderTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_HEADER);
    serviceLinkDetailTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_LINK_DETAIL);
    serviceStopDetailTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_STOP_DETAIL);

    // Load the stored services
    loadService();

    // Prepare the GUI
    serviceEditorDlg = new ServiceEditorDlg(this);
  }

  /**
   * Adds or removes a link to the service.
   *
   * @param omg The link to add or remove. Must be an OMPoly
   * @param record The DBF Record attached to the link.
   * @return True on success
   */
  public boolean addOrRemoveLink(OMGraphic omg, List<Object> record) {

    // Only polylines can be taken into account
    if (!(omg instanceof OMPoly)) {
      return false;
    }

    // All the chunks must belong to the same mode
    int mode = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE));
    int means = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MEANS));

    if (currentService.getNbLinks() == 0) {
      currentService.setMode((byte) JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE)));
    } else {
      if (JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE)) != currentService.getMode()) {
        return false;
      }
    }

    int node1 =
        JDBCUtils.getInt(
            record.get(NodusC.DBF_IDX_NODE1)); // this.setLocationRelativeTo(nodusMapPanel);
    int node2 = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_NODE2));

    int n1 = getNbOccurences(node1);
    int n2 = getNbOccurences(node2);

    /* Add or remove chunk */
    if (currentService.contains(omg)) {
      /*
       * Remove rules: - one node must have only one occurrence - the other node must have two
       * occurrences, only one element in the line. Thus, only the end chunks can be removed
       */
      omg.deselect();
      currentService.removeChunk(omg);
    } else {

      /*
       * Add rules: - a node cannot have more than one occurence, otherwise is an (unallowed) fork -
       * at least one node must have one occurence or this chunk is the first one
       */
      if (n1 + n2 > 0 || currentService.getNbLinks() == 0) {
        currentService.addChunk(omg);
        addStopNode(n1, node1);
        addStopNode(n2, node2);
      } else {
        return false;
      }
    } // this.setLocationRelativeTo(nodusMapPanel);
    serviceEditorDlg.loadModeMeans((byte) mode, (byte) means);
    paintService(true);

    mustBeSaved = true;
    return true;
  }

  /**
   * Adds a services list to the given link.
   *
   * @param servicesList The list of services to associate to a link.
   * @param linkId The ID of the link the services list must be associated to.
   */
  public void addServicesToLink(int linkId, LinkedList<String> servicesList) {
    OMGraphic omglink = getOMGraphic(linkId, TYPE_LINK);

    Iterator<String> it = servicesList.iterator();
    while (it.hasNext()) {
      String serviceName = it.next();
      Service s = services.get(serviceName);
      s.addChunk(omglink);
    }
  }

  /** Adds a stop node to the service. */
  private void addStopNode(int occurences, int nodeId) {
    if (occurences < 1) {
      if (getTranship(nodeId) == NodusC.HANDLING_ALL
          || getTranship(nodeId) == NodusC.HANDLING_CHANGE_SERVICE) {
        currentService.addStop(nodeId);
      }
    }
  }

  /** Notify that the service needs to be saved. */
  public void mustBeSaved() {
    mustBeSaved = true;
  }

  /** Closes the service manager and saves the service in tha database if needed. */
  public void close() {

    if (!mustBeSaved) {
      return;
    }

    // Create new tables if needed
    resetServicesTables();

    try {

      jdbcConnection = nodusProject.getMainJDBCConnection();
      // connect to database and execute query
      String sqlStmt = "INSERT INTO " + serviceHeaderTableName + " VALUES(?,?,?,?,?,?)";
      PreparedStatement pstmt1 = jdbcConnection.prepareStatement(sqlStmt);

      sqlStmt = "INSERT INTO " + serviceLinkDetailTableName + " VALUES(?,?)";
      PreparedStatement pstmt2 = jdbcConnection.prepareStatement(sqlStmt);

      sqlStmt = "INSERT INTO " + serviceStopDetailTableName + " VALUES(?,?)";
      PreparedStatement pstmt3 = jdbcConnection.prepareStatement(sqlStmt);

      Iterator<String> it1 = getServiceNamesIterator();
      while (it1.hasNext()) {
        // Header
        String name = it1.next();
        Service s = services.get(name);
        pstmt1.setInt(1, s.getId());
        pstmt1.setString(2, name);
        pstmt1.setInt(3, s.getMode());
        pstmt1.setInt(4, s.getMeans());
        pstmt1.setInt(5, s.getFrequency());
        String description = s.getDescription();
        if (description != null && description.length() > 40) {
          description = description.substring(0, 40);
        }
        pstmt1.setString(6, description);
        pstmt1.executeUpdate();
        // Stops
        try {
          Iterator<Integer> it = s.getStopNodes().iterator();
          while (it.hasNext()) {
            pstmt3.setInt(1, s.getId());
            pstmt3.setInt(2, it.next());
            pstmt3.executeUpdate();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        // Chunks
        Iterator<OMGraphic> it2 = s.getLinks().iterator();
        while (it2.hasNext()) {
          // Get the num of the graphic
          int num = getOMGraphicID(it2.next(), TYPE_LINK);

          if (num != -1) {
            pstmt2.setInt(1, s.getId());
            pstmt2.setInt(2, num);
            pstmt2.executeUpdate();
          }
        }
      }

      pstmt1.close();
      pstmt2.close();
      pstmt3.close();
      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }

    services.clear();
  }

  /**
   * Returns the NodusMapPanel associated to the project.
   *
   * @return The NodusMapPanel
   */
  public NodusMapPanel getNodusMapPanel() {
    return nodusProject.getNodusMapPanel();
  }

  /**
   * Retrieves the frequency of a given service.
   *
   * @param serviceId The ID of the service.
   * @return The frequency on the service.
   */
  public int frequencyByService(int serviceId) {

    Iterator<String> it = getServiceNamesIterator();
    while (it.hasNext()) {
      Service s = getService(it.next());
      if (s.getId() == serviceId) {
        return s.getFrequency();
      }
    }

    return 0;
  }

  /**
   * Returns the service currently handled by this ServiceEditor.
   *
   * @return The current service.
   */
  public Service getCurrentService() {
    return currentService;
  }

  /**
   * Retrieves the transportation means for the given service ID.
   *
   * @param serviceId service number
   * @return The transportation means of service.
   */
  public int getMeansForService(int serviceId) {
    Iterator<Service> it = services.values().iterator();
    Service s = null;
    while (it.hasNext()) {
      s = it.next();
      if (s.getId() == serviceId) {
        break;
      }
    }
    return s.getMeans();
  }

  /**
   * Returns the number of occurrences of the given node ID in the current service.
   *
   * @param nodeId The ID of the node.
   * @return The number of occurrences of the node in the current service.
   */
  private int getNbOccurences(int nodeId) {

    Iterator<OMGraphic> it = currentService.getLinks().iterator();
    int nbOccurences = 0;
    while (it.hasNext()) {
      OMGraphic omg = it.next();
      // Get the end nodes of this graphic
      for (NodusEsriLayer element : linkLayer) {
        int idx = element.getEsriGraphicList().indexOf(omg);
        if (idx != -1) {
          int n1 = JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NODE1));

          int n2 = JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NODE2));
          if (nodeId == n1 || nodeId == n2) {
            nbOccurences++;
          }
          break;
        }
      }
    }

    return nbOccurences;
  }

  /**
   * Gets a numeric ID for a new service.
   *
   * @return The first available numeric ID.
   */
  public int getNewServiceId() {

    int newId = 1;

    do {
      // Iterate over the values in the map
      Iterator<Service> it = services.values().iterator();
      boolean found = false;
      while (it.hasNext()) {
        // Get value
        Service line = it.next();
        if (line.getId() == newId) {
          found = true;
          break;
        }
      }

      if (!found) {
        return newId;
      } else {
        newId++;
      }
    } while (true);
  }

  /**
   * Get the graphic associated to the given link ID.
   *
   * @param linkId The ID of the link (polyline).
   * @param type Type of the element 0 = Node, 1 = Link.
   * @return The graphic, or null if not found.
   */
  private OMGraphic getOMGraphic(int linkId, int type) {
    switch (type) {
      case TYPE_NODE:
        for (NodusEsriLayer element : nodeLayer) {
          int idx = element.getNumIndex(linkId);
          if (idx != -1) {
            return element.getEsriGraphicList().getOMGraphicAt(idx);
          }
        }
        break; // findbug ??

      case TYPE_LINK:
        for (NodusEsriLayer element : linkLayer) {
          int idx = element.getNumIndex(linkId);
          if (idx != -1) {
            return element.getEsriGraphicList().getOMGraphicAt(idx);
          }
        }
        break;
      default:
        break;
    }

    System.err.println("Line chunk " + linkId + " not found!");
    return null;
  }

  /**
   * Gets the ID associated to a given graphic.
   *
   * @param omg The graphic for which the ID must be found.
   * @param type Type of the element 0 = Node, 1 = Polyline
   * @return the ID of the graphic, or -1 if not found.
   */
  private int getOMGraphicID(OMGraphic omg, int type) {
    switch (type) {
      case TYPE_NODE:
        for (NodusEsriLayer element : nodeLayer) {
          int idx = element.getEsriGraphicList().indexOf(omg);
          if (idx != -1) {
            return JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NUM));
          }
        }
        break;

      case TYPE_LINK:
        for (NodusEsriLayer element : linkLayer) {

          int idx = element.getEsriGraphicList().indexOf(omg);
          if (idx != -1) {
            return JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NUM));
          }
        }
        break;

      default:
        break;
    }

    System.err.println("Graphic not found!");
    return -1;
  }

  /**
   * Returns the Service which name is passed as parameter.
   *
   * @param serviceName The name of the service.
   * @return The Service, or null if not found.
   */
  public Service getService(String serviceName) {
    return services.get(serviceName);
  }

  /**
   * Returns the name of the table that contains the headers of the services.
   *
   * @return The table name.
   */
  public String getServiceHeaderTableName() {
    return serviceHeaderTableName;
  }

  /**
   * Finds the ID of the service which name is passed as parameter.
   *
   * @param serviceName The name of the service.
   * @return The ID of the service, or -1 if not found.
   */
  public int getServiceId(String serviceName) {

    Service l = services.get(serviceName);
    if (l == null) {
      return -1;
    }
    return l.getId();
  }

  /**
   * Retrieves the list of the service ID that use a given link.
   *
   * @param linkId The ID of the link.
   * @return Linked list of service ID.
   */
  public LinkedList<Integer> getServicesForLink(int linkId) {
    LinkedList<Integer> serviceIdx = null;

    OMGraphic omg = getOMGraphic(linkId, TYPE_LINK);

    Iterator<String> it = getServiceNamesIterator();
    while (it.hasNext()) {
      String currentName = it.next();
      Service s = services.get(currentName);
      if (s.contains(omg)) {
        if (serviceIdx == null) {
          serviceIdx = new LinkedList<>();
        }
        serviceIdx.add(s.getId());
      }
    }
    return serviceIdx;
  }

  /**
   * Returns the name of the details table for the service.
   *
   * @return The name of the details detable.
   */
  public String getServiceLinkDetailTableName() {
    return serviceLinkDetailTableName;
  }

  /**
   * Retrieves the list of the names of the services that use a given link.
   *
   * @param linkId The ID of the link.
   * @return Linked list of service names.
   */
  public LinkedList<String> getServiceNamesForLink(int linkId) {
    LinkedList<String> serviceNames = new LinkedList<>();

    OMGraphic omg = getOMGraphic(linkId, TYPE_LINK);

    Iterator<String> it = getServiceNamesIterator();
    while (it.hasNext()) {
      String currentName = it.next();
      Service s = services.get(currentName);
      if (s.contains(omg)) {
        serviceNames.add(currentName);
      }
    }
    return serviceNames;
  }

  /**
   * Retrieves a Map containing the list of services that pass along a given node.
   *
   * @param nodeId The ID of the node to check.
   * @return A TreeMap containing the services. The Boolean value associated to each name is set to
   *     true if the service stops at this node.
   */
  public TreeMap<String, Boolean> getServiceNamesForNode(int nodeId) {

    TreeMap<String, Boolean> serviceNames = new TreeMap<>();
    ;
    List<Object> values;
    for (NodusEsriLayer element : linkLayer) {
      for (int i = 0; i < element.getModel().getRowCount(); i++) {
        values = element.getModel().getRecord(i);
        int node1Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE1));
        int node2Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE2));
        if (nodeId == node1Id || nodeId == node2Id) {
          int linkId = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));
          OMGraphic omg = getOMGraphic(linkId, TYPE_LINK);
          Iterator<String> it = getServiceNamesIterator();
          while (it.hasNext()) {
            String currentName = it.next();
            Service s = services.get(currentName);
            if (s.contains(omg)) {
              if (s.contains(nodeId)) {
                if (!serviceNames.containsKey(currentName) || serviceNames.get(currentName)) {
                  serviceNames.put(s.getName(), true);
                }
              } else {
                serviceNames.put(s.getName(), false);
              }
            }
          }
        }
      }
    }
    return serviceNames;
  }

  /**
   * Gets an iterator on the service names.
   *
   * @return The iterator on the service names.
   */
  public Iterator<String> getServiceNamesIterator() {
    return services.keySet().iterator();
  }

  /**
   * Returns the name of the table that contains the details of the stop nodes.
   *
   * @return The name of the table that contains the details of the stop nodes.
   */
  public String getServiceStopDetailTableName() {
    return serviceStopDetailTableName;
  }

  /**
   * Retrieves the value of the "tranship" field of a node.
   *
   * @param nodeId The ID of a node.
   * @return The value of the "tranship" field of the node.
   */
  public int getTranship(int nodeId) {
    for (NodusEsriLayer element : nodeLayer) {
      int idx = element.getNumIndex(nodeId);
      if (idx != -1) {
        OMGraphic omg = element.getEsriGraphicList().getOMGraphicAt(idx);
        int id = element.getEsriGraphicList().indexOf(omg);
        if (id != -1) {
          return JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_TRANSHIP));
        }
      }
    }

    return -1;
  }

  /**
   * Tests if the service editor GUI is currently displayed.
   *
   * @return True if the dialog box is visible.
   */
  public boolean isGUIVisible() {
    return serviceEditorDlg.isVisible();
  }

  /**
   * Returns true if the service manager is listening, i.e. if links can be added or removed.
   *
   * @return True if links can be added or removed.
   */
  public boolean isListening() {
    return listening;
  }

  /**
   * Checks if a node is a stop point for a service.
   *
   * @param nodeId The ID of a node.
   * @param serviceId The ID of a service
   * @return True if the node corresponds to a stop point along the service.
   */
  public boolean isNodeStopService(int nodeId, int serviceId) {

    Iterator<String> it = getServiceNamesIterator();
    while (it.hasNext()) {
      Service s = getService(it.next());
      if (s.getStopNodes().contains(nodeId) && s.getId() == serviceId) {
        return true;
      }
    }

    return false;
  }

  /** Load the services from the database. */
  private void loadService() {

    // Tables must exists
    if (!JDBCUtils.tableExists(serviceHeaderTableName)) {
      return;
    }

    // OK, load
    try {
      // connect to database and execute query
      jdbcConnection = nodusProject.getMainJDBCConnection();

      Statement stmt1 = jdbcConnection.createStatement();
      Statement stmt2 = jdbcConnection.createStatement();
      Statement stmt3 = jdbcConnection.createStatement();
      String sqlStmt = "SELECT * FROM " + serviceHeaderTableName;
      ResultSet rs1 = stmt1.executeQuery(sqlStmt);

      // Retrieve result of query : header
      while (rs1.next()) {

        int idService = JDBCUtils.getInt(rs1.getObject(1));
        String nameService = (String) rs1.getObject(2);
        byte mode = (byte) JDBCUtils.getInt(rs1.getObject(3));
        byte means = (byte) JDBCUtils.getInt(rs1.getObject(4));
        int frequency = JDBCUtils.getInt(rs1.getObject(5));
        String type = (String) rs1.getObject(6);

        Service s = new Service(idService, nameService, mode, means, frequency, type);
        // Retrieve the list of chunks for this line
        String sqlStmt2 =
            "SELECT "
                + NodusC.DBF_LINK
                + " FROM "
                + serviceLinkDetailTableName
                + " WHERE "
                + NodusC.DBF_SERVICE_INDEX
                + " = "
                + idService;
        ResultSet rs2 = stmt2.executeQuery(sqlStmt2);
        while (rs2.next()) {
          OMGraphic omg = getOMGraphic(JDBCUtils.getInt(rs2.getObject(1)), TYPE_LINK);
          s.addChunk(omg);
        }
        rs2.close();

        String sqlStmt3 =
            "SELECT "
                + NodusC.DBF_STOP
                + " FROM "
                + serviceStopDetailTableName
                + " WHERE "
                + NodusC.DBF_SERVICE_INDEX
                + " = "
                + idService;
        ResultSet rs3 = stmt3.executeQuery(sqlStmt3);
        while (rs3.next()) {
          s.addStop(JDBCUtils.getInt(rs3.getObject(1)));
        }
        rs3.close();

        // Put loaded line in hashmap
        services.put(nameService, s);
      }
      rs1.close();
      stmt1.close();
      stmt2.close();
      stmt3.close();

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Displays a service given by its name.
   *
   * @param serviceName Name of the service to display.
   */
  public void displayService(String serviceName) {

    paintService(false);

    // Get the line to edit
    Service s = services.get(serviceName);
    if (s == null) {
      s = new Service(getNewServiceId());
      if (serviceName.compareTo("") != 0) {
        services.put(serviceName, s);
      }
    }
    currentService = s;
    if (serviceEditorDlg.isVisible()) {

      serviceEditorDlg.selectService(serviceName);
      // setListening(true);
    }
    paintService(true);
  }

  /** Associate the service ID to the real links. */
  public void loadServicesForVirtualNetwork() {
    // * Clear the already loaded lines
    for (NodusEsriLayer element : linkLayer) {
      Iterator<?> it = element.getEsriGraphicList().iterator();
      while (it.hasNext()) {
        OMGraphic omg = (OMGraphic) it.next();
        RealLink rl = (RealLink) omg.getAttribute(0);
        rl.clearServices();
      }
    }

    /* Load the real links with the list of line id they belong to */
    Iterator<Service> it = services.values().iterator();
    while (it.hasNext()) {
      Service s = it.next();
      Iterator<OMGraphic> it2 = s.getLinks().iterator();
      while (it2.hasNext()) {
        OMGraphic omg = it2.next();
        RealLink rl = (RealLink) omg.getAttribute(0);
        rl.addService(s.getId());
      }
    }
  }

  /**
   * Update the display of the service.
   *
   * @param select If true, the service will be selected (highlighted) on the map.
   */
  public void paintService(boolean select) {
    if (currentService == null || currentService.getNbLinks() == 0) {
      return;
    }
    Iterator<OMGraphic> it = currentService.getLinks().iterator();
    while (it.hasNext()) {
      OMGraphic omg = it.next();
      omg.setSelectPaint(java.awt.Color.GREEN);
      omg.setSelected(select);
      omg.render(graphics);
    }
  }

  /**
   * Removes service from the list.
   *
   * @param serviceName Name of the service to remove.
   */
  public void removeService(String serviceName) {

    // Get the service to edit
    Service s = services.get(serviceName);

    if (s != null) {
      currentService = s;
      paintService(false);
      services.remove(serviceName);
      currentService = null;
      setListening(false);
      mustBeSaved();
    }
  }

  /** Resets the currently edited service. */
  public void resetService() {
    if (currentService != null) {
      setListening(false);
      paintService(false);
      currentService = null;
      for (NodusEsriLayer element : linkLayer) {
        element.repaint();
      }
    }
  }

  /** Prepares empty header/detail database tables to store the services. */
  public void resetServicesTables() {

    // Create header tables
    JDBCField[] fields = new JDBCField[6];
    fields[0] = new JDBCField(NodusC.DBF_SERVICE_INDEX, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_SERVICE, "VARCHAR(30)");
    fields[2] = new JDBCField(NodusC.DBF_MODE, "NUMERIC(2,0)");
    fields[3] = new JDBCField(NodusC.DBF_MEANS, "NUMERIC(2,0)");
    fields[4] = new JDBCField(NodusC.DBF_FREQUENCY, "NUMERIC(5,0)");
    fields[5] = new JDBCField(NodusC.DBF_TYPE, "VARCHAR(30)");
    JDBCUtils.createTable(serviceHeaderTableName, fields);

    // Create details table
    fields = new JDBCField[2];
    fields[0] = new JDBCField(NodusC.DBF_SERVICE_INDEX, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_LINK, "NUMERIC(10,0)");
    JDBCUtils.createTable(serviceLinkDetailTableName, fields);

    // Create details table
    fields = new JDBCField[2];
    fields[0] = new JDBCField(NodusC.DBF_SERVICE_INDEX, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_STOP, "NUMERIC(10,0)");
    JDBCUtils.createTable(serviceStopDetailTableName, fields);
  }

  /**
   * Saves a service in the list.
   *
   * @param service The service to save.
   */
  public void saveService(Service service) {
    if (services.get(service.getName()) != null) {
      services.remove(service.getName());
    }
    services.put(service.getName(), service);
  }

  /**
   * Set the state of the editing process.
   *
   * @param listening If true, links can be added/removed.
   */
  public void setListening(boolean listening) {
    this.listening = listening;
    if (listening) {
      // Set the appropriate mouse mode
      nodusProject.getNodusMapPanel().setActiveMouseMode(SelectMouseMode.modeID);
    } else {
      nodusProject.getNodusMapPanel().setActiveMouseMode(NavMouseMode.modeID);
    }
  }

  /**
   * Sets the stops of the service.
   *
   * @param nodeId The ID of the node.
   * @param serviceNames A TreeMap containing all the services the node belongs to.
   */
  public void setStops(int nodeId, TreeMap<String, Boolean> serviceNames) {
    Iterator<String> it = serviceNames.keySet().iterator();
    while (it.hasNext()) {
      String serviceName = it.next();
      Service s = services.get(serviceName);
      if (s.contains(nodeId)) {
        s.removeStop(nodeId);
      }
      if (serviceNames.get(serviceName)) {
        s.addStop(nodeId);
      }
    }

    mustBeSaved();
  }

  /** Displays the GUI. */
  public void showGUI() {
    serviceEditorDlg.setVisible(true);
  }
}
