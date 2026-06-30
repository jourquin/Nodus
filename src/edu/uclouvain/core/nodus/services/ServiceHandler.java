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

package edu.uclouvain.core.nodus.services;

import com.bbn.openmap.Environment;
import com.bbn.openmap.event.NavMouseMode;
import com.bbn.openmap.event.SelectMouseMode;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.gui.ServicesDlg;
import java.awt.Graphics;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JOptionPane;

/**
 * This class manages the editing process for services.
 *
 * @author Galina Iassinovskaia and Bart Jourquin
 */
public class ServiceHandler {

  private static I18n i18n = Environment.getI18n();

  private static final int TYPE_LINK = 1;
  private static final int TYPE_NODE = 0;

  /** Current service to edit. */
  private TransportService currentService;

  private ServicesDlg serviceEditorDlg;

  private Connection jdbcConnection;

  private NodusEsriLayer[] linkLayer;

  private boolean listening = false;

  boolean mustBeSaved = false;

  private NodusEsriLayer[] nodeLayer;

  private NodusProject nodusProject;

  private String servicesHeaderTableName;

  private String servicesLinksTableName;

  /** TreeMap that contains the services. */
  private TreeMap<String, TransportService> services = new TreeMap<>();

  private String serviceStopsTableName;

  /**
   * Creates a new ServiceHandler.
   *
   * @param nodusProject A Nodus project.
   */
  public ServiceHandler(NodusProject nodusProject) {

    this.nodusProject = nodusProject;
    // nodusMapPanel = nodusProject.getNodusMapPanel();

    linkLayer = nodusProject.getLinkLayers();
    nodeLayer = nodusProject.getNodeLayers();

    // Prepare table names
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_SERVICES;
    String name = nodusProject.getLocalProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, defValue);

    servicesHeaderTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_HEADER);
    servicesLinksTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_SERVICES_LINKS);
    serviceStopsTableName = JDBCUtils.getCompliantIdentifier(name + NodusC.SUFFIX_SERVICES_STOPS);

    // Load the stored services
    loadService();

    // Prepare the GUI
    serviceEditorDlg = new ServicesDlg(this);
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
      logServiceLineEdit(
          "Log_Selected_graphic_not_link",
          "Selected graphic cannot be added because it is not a link");
      return false;
    }

    // All the chunks must belong to the same mode
    int linkId = getLinkId(record);
    int mode = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE));
    int means = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MEANS));

    if (currentService.getNbLinks() == 0) {
      currentService.setMode((byte) JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE)));
    } else {
      if (JDBCUtils.getInt(record.get(NodusC.DBF_IDX_MODE)) != currentService.getMode()) {
        logServiceLineEdit(
            "Log_Mode_mismatch",
            "{0} cannot be added because its mode {1} differs from the service line mode {2}",
            formatLink(linkId),
            formatIdentifier(mode),
            formatIdentifier(currentService.getMode()));
        return false;
      }
    }

    int node1 = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_NODE1));
    int node2 = JDBCUtils.getInt(record.get(NodusC.DBF_IDX_NODE2));

    int n1 = getNbOccurences(node1);
    int n2 = getNbOccurences(node2);

    /* Add or remove chunk */
    if (currentService.contains(omg)) {
      if (currentService.getNbLinks() > 1 && n1 > 1 && n2 > 1) {
        logServiceLineEdit(
            "Log_Link_inside_line",
            "{0} cannot be removed because it is inside the current service line",
            formatLink(linkId));
        return false;
      }
      omg.deselect();
      currentService.removeChunk(omg);
      removeUnusedStops();
      logServiceLineEdit("Log_Link_removed", "{0} removed from line", formatLink(linkId));
    } else {

      if (currentService.getNbLinks() == 0) {
        if (!isValidServiceEndpoint(node1) && !isValidServiceEndpoint(node2)) {
          logServiceLineEdit(
              "Log_First_link_requires_operations",
              "{0} cannot be added because the first link of a line must be connected to a node"
                  + " with operations",
              formatLink(linkId));
          return false;
        }
        currentService.addChunk(omg);
        addStopNode(n1, node1);
        addStopNode(n2, node2);
        logServiceLineEdit("Log_Link_added", "{0} added to line", formatLink(linkId));
      } else {
        if (n1 > 0 && n2 > 0) {
          logServiceLineEdit(
              "Log_Both_end_nodes_in_line",
              "{0} cannot be added because both end nodes already belong to the service line",
              formatLink(linkId));
          return false;
        }

        if (n1 > 0 || n2 > 0) {
          currentService.addChunk(omg);
          addStopNode(n1, node1);
          addStopNode(n2, node2);
          logServiceLineEdit("Log_Link_added", "{0} added to line", formatLink(linkId));
        } else {
          logServiceLineEdit(
              "Log_Not_connected_to_one_end",
              "{0} cannot be added because it is not connected to exactly one node of the current"
                  + " service line",
              formatLink(linkId));
          return false;
        }
      }
    } // this.setLocationRelativeTo(nodusMapPanel);
    serviceEditorDlg.loadModeMeans((byte) mode, (byte) means);
    repaintLinkLayers();
    paintService(true);

    mustBeSaved = true;
    if (serviceEditorDlg != null) {
      serviceEditorDlg.markServicesChanged();
    }
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
      TransportService s = services.get(serviceName);
      s.addChunk(omglink);
    }
  }

  /** Adds a stop node to the service. */
  private void addStopNode(int occurences, int nodeId) {
    if (occurences < 1) {
      if (isValidServiceEndpoint(nodeId) && !currentService.contains(nodeId)) {
        currentService.addStop(nodeId);
      }
    }
  }

  /** Formats and prints a service line edit message for the terminal. */
  private void logServiceLineEdit(String key, String defaultPattern, Object... args) {
    String message =
        MessageFormat.format(i18n.get(ServiceHandler.class, key, defaultPattern), args);
    System.out.println(
        MessageFormat.format(
            i18n.get(ServiceHandler.class, "Log_Prefix", "[Services] {0}"), message));
  }

  /** Returns the link ID stored in a DBF record. */
  private int getLinkId(List<Object> record) {
    return JDBCUtils.getInt(record.get(NodusC.DBF_IDX_NUM));
  }

  /** Formats a link ID for service line edit messages. */
  private String formatLink(int linkId) {
    if (linkId == Integer.MIN_VALUE) {
      return i18n.get(ServiceHandler.class, "Log_Unknown_link", "Link <unknown>");
    }
    return MessageFormat.format(
        i18n.get(ServiceHandler.class, "Log_Link", "Link {0}"), formatIdentifier(linkId));
  }

  /** Formats an identifier without locale-specific grouping separators. */
  private String formatIdentifier(int identifier) {
    return Integer.toString(identifier);
  }

  /** Notify that the service needs to be saved. */
  public void mustBeSaved() {
    mustBeSaved = true;
  }

  /**
   * Saves pending service changes immediately, if any.
   *
   * @return True if there was nothing to save or if the save succeeded.
   */
  public boolean savePendingChanges() {
    if (!mustBeSaved) {
      return true;
    }
    if (saveServices()) {
      mustBeSaved = false;
      return true;
    }
    return false;
  }

  /** Discards pending service changes and reloads the services stored in the database. */
  public void discardPendingChanges() {
    resetService();
    if (services != null) {
      services.clear();
    }
    loadService();
    mustBeSaved = false;
  }

  /** Closes the service manager and saves the services in the database if needed. */
  public void close() {

    try {
      if (mustBeSaved) {
        savePendingChanges();
      }
    } finally {
      dispose();
    }
  }

  /** Releases references held by the service manager. */
  public void dispose() {
    resetService();
    listening = false;
    mustBeSaved = false;

    if (serviceEditorDlg != null) {
      serviceEditorDlg.disposeFromServiceHandler();
      serviceEditorDlg = null;
    }

    if (services != null) {
      services.clear();
    }

    jdbcConnection = null;
    linkLayer = null;
    nodeLayer = null;
    nodusProject = null;
    servicesHeaderTableName = null;
    servicesLinksTableName = null;
    serviceStopsTableName = null;
  }

  /** Saves the services in the database. */
  private boolean saveServices() {
    Savepoint savepoint = null;

    try {
      jdbcConnection = nodusProject.getMainJDBCConnection();
      if (jdbcConnection == null) {
        return false;
      }

      // Create new tables if needed
      resetServicesTables();

      // HSQLDB invalidates savepoints when resetServicesTables() drops/recreates the tables.
      // Protect only the following data insertion phase.
      if (!jdbcConnection.getAutoCommit()) {
        savepoint = jdbcConnection.setSavepoint();
      }

      String servicesHeaderSql =
          "INSERT INTO "
              + JDBCUtils.getQuotedCompliantIdentifier(servicesHeaderTableName)
              + " VALUES(?,?,?,?,?)";
      String servicesLinksSql =
          "INSERT INTO "
              + JDBCUtils.getQuotedCompliantIdentifier(servicesLinksTableName)
              + " VALUES(?,?)";
      String serviceStopsSql =
          "INSERT INTO "
              + JDBCUtils.getQuotedCompliantIdentifier(serviceStopsTableName)
              + " VALUES(?,?)";

      try (PreparedStatement pstmt1 = jdbcConnection.prepareStatement(servicesHeaderSql);
          PreparedStatement pstmt2 = jdbcConnection.prepareStatement(servicesLinksSql);
          PreparedStatement pstmt3 = jdbcConnection.prepareStatement(serviceStopsSql)) {

        Iterator<String> it1 = getServiceNamesIterator();
        while (it1.hasNext()) {
          // Header
          String name = it1.next();
          TransportService s = services.get(name);
          pstmt1.setInt(1, s.getId());
          pstmt1.setString(2, name);
          pstmt1.setInt(3, s.getMode());
          pstmt1.setInt(4, s.getMeans());
          pstmt1.setInt(5, s.getFrequency());
          pstmt1.executeUpdate();

          // Stops
          Iterator<Integer> it = s.getStopNodes().iterator();
          while (it.hasNext()) {
            pstmt3.setInt(1, s.getId());
            pstmt3.setInt(2, it.next());
            pstmt3.executeUpdate();
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
      }

      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }
      return true;
    } catch (Exception ex) {
      if (jdbcConnection != null) {
        try {
          if (!jdbcConnection.getAutoCommit() && savepoint != null) {
            jdbcConnection.rollback(savepoint);
          }
        } catch (SQLException rollbackEx) {
          rollbackEx.printStackTrace();
        }
      }

      JOptionPane.showMessageDialog(null, ex.toString(), "SQL error", JOptionPane.ERROR_MESSAGE);
      return false;
    }
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
      TransportService s = getService(it.next());
      if (s.getId() == serviceId) {
        return s.getFrequency();
      }
    }

    return 0;
  }

  /**
   * Returns the service currently handled by this ServiceHandler.
   *
   * @return The current service.
   */
  public TransportService getCurrentService() {
    return currentService;
  }

  /**
   * Retrieves the transportation means for the given service ID.
   *
   * @param serviceId service number
   * @return The transportation means of service.
   */
  public int getMeansForService(int serviceId) {
    Iterator<TransportService> it = services.values().iterator();
    while (it.hasNext()) {
      TransportService s = it.next();
      if (s.getId() == serviceId) {
        return s.getMeans();
      }
    }
    return 0;
  }

  /**
   * Returns the number of occurrences of the given node ID in the current service.
   *
   * @param nodeId The ID of the node.
   * @return The number of occurrences of the node in the current service.
   */
  private int getNbOccurences(int nodeId) {
    return getNbOccurences(currentService, nodeId);
  }

  /**
   * Returns the number of occurrences of the given node ID in a service.
   *
   * @param service The service to inspect.
   * @param nodeId The ID of the node.
   * @return The number of occurrences of the node in the service.
   */
  private int getNbOccurences(TransportService service, int nodeId) {

    Iterator<OMGraphic> it = service.getLinks().iterator();
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
   * Checks if all service end nodes allow operations.
   *
   * @param service The service to check.
   * @return True if the service has at least two valid end nodes and no invalid end node.
   */
  public boolean hasValidEndNodes(TransportService service) {
    return getServiceLineValidationMessage(service) == null;
  }

  /**
   * Validates a service line and returns a user-readable reason if it is invalid.
   *
   * @param service The service to check.
   * @return null if the service line is valid, otherwise the reason why it is invalid.
   */
  public String getServiceLineValidationMessage(TransportService service) {
    if (service == null || service.getNbLinks() == 0) {
      return i18n.get(ServiceHandler.class, "InvalidLine_No_links", "the line has no links");
    }

    LinkedList<Integer> endNodes = new LinkedList<>();
    LinkedList<int[]> serviceEdges = new LinkedList<>();
    Set<Integer> serviceNodes = new HashSet<>();
    Iterator<OMGraphic> it = service.getLinks().iterator();
    while (it.hasNext()) {
      OMGraphic link = it.next();
      int[] nodes = getLinkEndpointNodeIds(link);
      if (nodes == null) {
        return i18n.get(
            ServiceHandler.class,
            "InvalidLine_Link_not_found",
            "one of the links was not found in the network");
      }
      int[] linkMode = getLinkIdAndMode(link);
      if (linkMode == null) {
        return i18n.get(
            ServiceHandler.class,
            "InvalidLine_Link_not_found",
            "one of the links was not found in the network");
      }
      if (linkMode[1] != service.getMode()) {
        return MessageFormat.format(
            i18n.get(
                ServiceHandler.class,
                "InvalidLine_Mode_mismatch",
                "{0} has mode {1}, but the service uses mode {2}"),
            formatLink(linkMode[0]),
            formatIdentifier(linkMode[1]),
            formatIdentifier(service.getMode()));
      }
      serviceEdges.add(nodes);
      serviceNodes.add(nodes[0]);
      serviceNodes.add(nodes[1]);
      if (getNbOccurences(service, nodes[0]) == 1 && !endNodes.contains(nodes[0])) {
        endNodes.add(nodes[0]);
      }
      if (getNbOccurences(service, nodes[1]) == 1 && !endNodes.contains(nodes[1])) {
        endNodes.add(nodes[1]);
      }
    }

    if (!isConnectedService(serviceEdges)) {
      return i18n.get(
          ServiceHandler.class,
          "InvalidLine_Disconnected",
          "the links do not form a single connected line");
    }

    if (serviceEdges.size() > serviceNodes.size() - 1) {
      return i18n.get(ServiceHandler.class, "InvalidLine_Cycle", "the line contains a cycle");
    }

    if (endNodes.size() < 2) {
      return i18n.get(
          ServiceHandler.class,
          "InvalidLine_Not_enough_end_nodes",
          "the line has less than two end nodes");
    }

    LinkedList<Integer> invalidEndNodes = new LinkedList<>();
    Iterator<Integer> endNodeIterator = endNodes.iterator();
    while (endNodeIterator.hasNext()) {
      Integer endNode = endNodeIterator.next();
      if (!isValidServiceEndpoint(endNode)) {
        invalidEndNodes.add(endNode);
      }
    }

    if (!invalidEndNodes.isEmpty()) {
      return MessageFormat.format(
          i18n.get(
              ServiceHandler.class,
              "InvalidLine_Invalid_end_nodes",
              "end node(s) {0} do not allow operations"),
          formatNodeList(invalidEndNodes));
    }

    return null;
  }

  /** Checks if all service links belong to a single connected graph component. */
  private boolean isConnectedService(LinkedList<int[]> serviceEdges) {
    if (serviceEdges.isEmpty()) {
      return false;
    }

    Set<Integer> connectedNodes = new HashSet<>();
    LinkedList<int[]> remainingEdges = new LinkedList<>(serviceEdges);
    int[] firstEdge = remainingEdges.removeFirst();
    connectedNodes.add(firstEdge[0]);
    connectedNodes.add(firstEdge[1]);

    boolean expanded;
    do {
      expanded = false;
      Iterator<int[]> edgeIterator = remainingEdges.iterator();
      while (edgeIterator.hasNext()) {
        int[] edge = edgeIterator.next();
        if (connectedNodes.contains(edge[0]) || connectedNodes.contains(edge[1])) {
          connectedNodes.add(edge[0]);
          connectedNodes.add(edge[1]);
          edgeIterator.remove();
          expanded = true;
        }
      }
    } while (expanded);

    return remainingEdges.isEmpty();
  }

  /**
   * Checks if a node can be used as the start or end of a service.
   *
   * @param nodeId The node ID.
   * @return True if transport operations are allowed at this node.
   */
  private boolean isValidServiceEndpoint(int nodeId) {
    int handling = getTranship(nodeId);
    if (handling > NodusC.SERVICE_CHANGE) {
      handling -= NodusC.SERVICE_CHANGE + 1;
    }
    return handling > NodusC.HANDLING_NONE && handling <= NodusC.SERVICE_CHANGE;
  }

  /**
   * Returns the endpoint node IDs of a service link.
   *
   * @param link The link.
   * @return The two endpoint node IDs, or null if the link is not found.
   */
  private int[] getLinkEndpointNodeIds(OMGraphic link) {
    for (NodusEsriLayer element : linkLayer) {
      int idx = element.getEsriGraphicList().indexOf(link);
      if (idx != -1) {
        return new int[] {
          JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NODE1)),
          JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NODE2))
        };
      }
    }

    return null;
  }

  /**
   * Returns the link ID and mode of a service link.
   *
   * @param link The link.
   * @return The link ID and mode, or null if the link is not found.
   */
  private int[] getLinkIdAndMode(OMGraphic link) {
    for (NodusEsriLayer element : linkLayer) {
      int idx = element.getEsriGraphicList().indexOf(link);
      if (idx != -1) {
        return new int[] {
          JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_NUM)),
          JDBCUtils.getInt(element.getModel().getValueAt(idx, NodusC.DBF_IDX_MODE))
        };
      }
    }

    return null;
  }

  /** Formats node IDs without locale-specific grouping separators. */
  private String formatNodeList(LinkedList<Integer> nodeIds) {
    StringBuilder builder = new StringBuilder();
    Iterator<Integer> it = nodeIds.iterator();
    while (it.hasNext()) {
      if (builder.length() != 0) {
        builder.append(", ");
      }
      builder.append(formatIdentifier(it.next()));
    }
    return builder.toString();
  }

  /** Removes stop nodes that no longer belong to any selected service link. */
  private void removeUnusedStops() {
    Iterator<Integer> it = currentService.getStopNodes().iterator();
    while (it.hasNext()) {
      if (getNbOccurences(it.next()) == 0) {
        it.remove();
      }
    }
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
      Iterator<TransportService> it = services.values().iterator();
      boolean found = false;
      while (it.hasNext()) {
        // Get value
        TransportService line = it.next();
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
        break;

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
   * Returns the TransportService which name is passed as parameter.
   *
   * @param serviceName The name of the service.
   * @return The TransportService, or null if not found.
   */
  public TransportService getService(String serviceName) {
    return services.get(serviceName);
  }

  /**
   * Returns the name of the table that contains the headers of the services.
   *
   * @return The table name.
   */
  public String getServiceHeaderTableName() {
    return servicesHeaderTableName;
  }

  /**
   * Finds the ID of the service which name is passed as parameter.
   *
   * @param serviceName The name of the service.
   * @return The ID of the service, or -1 if not found.
   */
  public int getServiceId(String serviceName) {

    TransportService l = services.get(serviceName);
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
    LinkedList<Integer> serviceIds = null;

    OMGraphic omg = getOMGraphic(linkId, TYPE_LINK);

    Iterator<String> it = getServiceNamesIterator();
    while (it.hasNext()) {
      String currentName = it.next();
      TransportService s = services.get(currentName);
      if (s.contains(omg)) {
        if (serviceIds == null) {
          serviceIds = new LinkedList<>();
        }
        serviceIds.add(s.getId());
      }
    }
    return serviceIds;
  }

  /**
   * Returns the name of the details table for the service.
   *
   * @return The name of the details detable.
   */
  public String getServiceLinkDetailTableName() {
    return servicesLinksTableName;
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
      TransportService s = services.get(currentName);
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
            TransportService s = services.get(currentName);
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
    return serviceStopsTableName;
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
      TransportService s = getService(it.next());
      if (s.getStopNodes().contains(nodeId) && s.getId() == serviceId) {
        return true;
      }
    }

    return false;
  }

  /** Load the services from the database. */
  private void loadService() {

    // Tables must exists
    if (!JDBCUtils.tableExists(servicesHeaderTableName)) {
      return;
    }

    // OK, load
    try {
      // connect to database and execute query
      jdbcConnection = nodusProject.getMainJDBCConnection();

      try (Statement stmt1 = jdbcConnection.createStatement();
          Statement stmt2 = jdbcConnection.createStatement();
          Statement stmt3 = jdbcConnection.createStatement();
          ResultSet rs1 =
              stmt1.executeQuery(
                  "SELECT * FROM "
                      + JDBCUtils.getQuotedCompliantIdentifier(servicesHeaderTableName))) {

        // Retrieve result of query : header
        while (rs1.next()) {

          int idService = JDBCUtils.getInt(rs1.getObject(1));
          String nameService = (String) rs1.getObject(2);
          byte mode = (byte) JDBCUtils.getInt(rs1.getObject(3));
          byte means = (byte) JDBCUtils.getInt(rs1.getObject(4));
          int frequency = JDBCUtils.getInt(rs1.getObject(5));

          TransportService s = new TransportService(idService, nameService, mode, means, frequency);
          // Retrieve the list of chunks for this line
          String sqlStmt2 =
              "SELECT "
                  + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK)
                  + " FROM "
                  + JDBCUtils.getQuotedCompliantIdentifier(servicesLinksTableName)
                  + " WHERE "
                  + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ID)
                  + " = "
                  + idService;
          try (ResultSet rs2 = stmt2.executeQuery(sqlStmt2)) {
            while (rs2.next()) {
              int linkId = JDBCUtils.getInt(rs2.getObject(1));
              OMGraphic omg = getOMGraphic(linkId, TYPE_LINK);
              if (omg != null) {
                s.addChunk(omg);
              }
            }
          }

          String sqlStmt3 =
              "SELECT "
                  + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_STOP)
                  + " FROM "
                  + JDBCUtils.getQuotedCompliantIdentifier(serviceStopsTableName)
                  + " WHERE "
                  + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ID)
                  + " = "
                  + idService;
          try (ResultSet rs3 = stmt3.executeQuery(sqlStmt3)) {
            while (rs3.next()) {
              s.addStop(JDBCUtils.getInt(rs3.getObject(1)));
            }
          }

          // Put loaded line in hashmap
          services.put(nameService, s);
        }
      }

      validateLoadedServices();

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }
  }

  /** Warns the user about invalid services loaded from the database. */
  private void validateLoadedServices() {
    if (services.isEmpty()) {
      return;
    }

    ArrayList<String> invalidServiceNames = new ArrayList<>();
    StringBuilder invalidServices = new StringBuilder();
    Iterator<TransportService> it = services.values().iterator();
    while (it.hasNext()) {
      TransportService service = it.next();
      String reason = getServiceLineValidationMessage(service);
      if (reason != null) {
        invalidServiceNames.add(service.getName());
        invalidServices
            .append(
                MessageFormat.format(
                    i18n.get(
                        ServiceHandler.class,
                        "InvalidLine_List_item",
                        "Line {0} ({1}) is not valid because {2}."),
                    formatIdentifier(service.getId()),
                    service.getName(),
                    reason))
            .append('\n');
      }
    }

    if (invalidServiceNames.isEmpty()) {
      return;
    }

    int answer =
        JOptionPane.showConfirmDialog(
            nodusProject.getMainFrame(),
            MessageFormat.format(
                i18n.get(
                    ServiceHandler.class,
                    "InvalidLine_Delete_question",
                    "The following service lines are invalid:\n\n{0}\nDelete them from the"
                        + " database tables?"),
                invalidServices.toString()),
            NodusC.APPNAME,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

    if (answer == JOptionPane.YES_OPTION) {
      Iterator<String> invalidServiceNameIterator = invalidServiceNames.iterator();
      while (invalidServiceNameIterator.hasNext()) {
        services.remove(invalidServiceNameIterator.next());
      }
      saveServices();
      mustBeSaved = false;
    }
  }

  /**
   * Displays a service given by its name.
   *
   * @param serviceName Name of the service to display.
   */
  public void displayService(String serviceName) {

    paintService(false);
    repaintLinkLayers();

    // Get the line to edit
    TransportService s = services.get(serviceName);
    if (s == null) {
      s = new TransportService(getNewServiceId());
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

  /** Repaints all link layers so deselected service links are actually cleared from the map. */
  private void repaintLinkLayers() {
    for (NodusEsriLayer element : linkLayer) {
      element.repaint();
    }
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
    Iterator<TransportService> it = services.values().iterator();
    while (it.hasNext()) {
      TransportService s = it.next();
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
    if (currentService == null || currentService.getNbLinks() == 0 || nodusProject == null) {
      return;
    }

    Graphics serviceGraphics = nodusProject.getNodusMapPanel().getMapBean().getGraphics();
    if (serviceGraphics == null) {
      return;
    }

    try {
      Iterator<OMGraphic> it = currentService.getLinks().iterator();
      while (it.hasNext()) {
        OMGraphic omg = it.next();
        if (omg != null) {
          omg.setSelectPaint(java.awt.Color.GREEN);
          omg.setSelected(select);
          omg.render(serviceGraphics);
        }
      }
    } finally {
      serviceGraphics.dispose();
    }
  }

  /**
   * Removes service from the list.
   *
   * @param serviceName Name of the service to remove.
   */
  public void removeService(String serviceName) {

    // Get the service to edit
    TransportService s = services.get(serviceName);

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
      repaintLinkLayers();
    }
  }

  /** Prepares empty header/detail database tables to store the services. */
  public void resetServicesTables() {

    // Create header tables
    JDBCField[] fields = new JDBCField[5];
    fields[0] = new JDBCField(NodusC.DBF_ID, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_SERVICE_NAME, "VARCHAR(30)");
    fields[2] = new JDBCField(NodusC.DBF_MODE, "NUMERIC(2,0)");
    fields[3] = new JDBCField(NodusC.DBF_MEANS, "NUMERIC(2,0)");
    fields[4] = new JDBCField(NodusC.DBF_FREQUENCY, "NUMERIC(5,0)");
    // fields[5] = new JDBCField(NodusC.DBF_DESCRIPTION, "VARCHAR(30)");
    JDBCUtils.createTable(servicesHeaderTableName, fields);

    // Create details table
    fields = new JDBCField[2];
    fields[0] = new JDBCField(NodusC.DBF_ID, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_LINK, "NUMERIC(10,0)");
    JDBCUtils.createTable(servicesLinksTableName, fields);

    // Create details table
    fields = new JDBCField[2];
    fields[0] = new JDBCField(NodusC.DBF_ID, "NUMERIC(4,0)");
    fields[1] = new JDBCField(NodusC.DBF_STOP, "NUMERIC(10,0)");
    JDBCUtils.createTable(serviceStopsTableName, fields);
  }

  /**
   * Saves a service in the list.
   *
   * @param service The service to save.
   */
  public void saveService(TransportService service) {
    Iterator<Map.Entry<String, TransportService>> it = services.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, TransportService> entry = it.next();
      TransportService existingService = entry.getValue();
      if (entry.getKey().equals(service.getName()) || existingService.getId() == service.getId()) {
        it.remove();
      }
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
      TransportService s = services.get(serviceName);
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
