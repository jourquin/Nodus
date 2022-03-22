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

package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.layer.highlightedarea.HighlightedAreaLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.Length;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.costs.CostParser;
import edu.uclouvain.core.nodus.compute.costs.CostParserWorker;
import edu.uclouvain.core.nodus.compute.costs.CostParserWorkerParameters;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Main class used by Nodus to generate virtual networks on the basis of Esri layers.
 *
 * @author Bart Jourquin
 */
public class VirtualNetwork {

  private class NodeLayerAndRowIndex {

    /** Index of the record inside the layer pointed by LayerIndex. */
    public int rowInLayer;

    /** Index of the real node in the VNL structure. */
    public int indexInVirtualNodeList;

    /** Index of the layer in the array of nodes layers. */
    public int layerIndex;

    /**
     * Just initializes the three public variables with the given values.
     *
     * @param layerIndex int
     * @param rowInLayer int
     * @param indexInVirtualNodeList int
     */
    public NodeLayerAndRowIndex(int layerIndex, int rowInLayer, int indexInVirtualNodeList) {
      this.layerIndex = layerIndex;
      this.rowInLayer = rowInLayer;
      this.indexInVirtualNodeList = indexInVirtualNodeList;
    }
  }

  private static I18n i18n = Environment.getI18n();

  private int assignmentEndTime = -1;

  /* Time dependent assignments specific */
  private int assignmentStartTime = -1;

  private int[][] availableModeMeans = null;

  private Properties costFunctions = null;

  /* Formater used in progress-bar display */
  private NumberFormat formatter = new DecimalFormat("0.00000");

  /*
   * Data structure used to represent a virtual network in a form that is suitable for the use with
   * the algorithm of Dijkstra (shortest paths)
   */
  private AdjacencyNode[][] graph = null;

  /* Vector that will contain the groups to assign */
  private byte[] groups = null;

  /** Length of task. Used to initialize a progress bar */
  private int lengthOfTask;

  /*
   * Table of mode/means for which service lines must be generated. Free flow if a Mode/Means
   * combination contains "false"
   */
  private boolean[][] linesForModeMeans = new boolean[NodusC.MAXMM][NodusC.MAXMM];

  /* Content of the .dbf tables associated to the link layers */
  private DbfTableModel[] linksDbf = null;

  /* Set of Esri layers that contain the real links */
  private NodusEsriLayer[] linksEsriLayer;

  /* Number of link layers in the project */
  private int nbLinkLayers = 0;

  /* Number of od classes that are defined */
  private byte nbODClasses;

  /* Number of real links in the project */
  private int nbRealLinks = 0;

  /* Number of real nodes in the project */
  private int nbRealNodes = 0;

  private int nbTimeSlices = 1;

  /* Number of generated virtual links */
  private int nbVirtualLinks = 1;

  /* Number of generated virtual nodes */
  private int nbVirtualNodes = 1;

  /* Hash table used to associate a node number with its index in the data structure */
  private HashMap<Integer, NodeLayerAndRowIndex> nodeIndex = null;

  /* Content of the .dbf tables associated to the node layers */
  private DbfTableModel[] nodesDbf = null;

  /* Set of Esri layers that contain the real nodes */
  private NodusEsriLayer[] nodesEsriLayer;

  /* Main frame of the application */
  private NodusMapPanel nodusMapPanel;

  /* Opened project for which a virtual network must be generated */
  private NodusProject nodusProject;

  /**
   * Returns the Nodus project.
   *
   * @return A Nodus project
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  private boolean[] odClassHasDemand = null;

  private int scenario;

  private int timeSliceDuration = 0;

  /* Basic structure used for virtual networks. */
  private VirtualNodeList[] vnl = null;

  /**
   * Initializes a new virtual network.
   *
   * @param ap The AssignmentParameters.
   */
  public VirtualNetwork(AssignmentParameters ap) {

    nodusProject = ap.getNodusProject();
    this.nodusMapPanel = nodusProject.getNodusMapPanel();

    scenario = ap.getScenario();

    // Get the cost functions
    costFunctions = ap.getCostFunctions();

    linksEsriLayer = nodusProject.getLinkLayers();
    nodesEsriLayer = nodusProject.getNodeLayers();

    // Get the table models for nodes and links
    nodesDbf = new DbfTableModel[nodesEsriLayer.length];

    for (int i = 0; i < nodesEsriLayer.length; i++) {
      nodesDbf[i] = nodesEsriLayer[i].getModel();
      nbRealNodes += nodesDbf[i].getRowCount();
    }

    nbLinkLayers = linksEsriLayer.length;
    linksDbf = new DbfTableModel[nbLinkLayers];

    for (int i = 0; i < linksEsriLayer.length; i++) {
      linksDbf[i] = linksEsriLayer[i].getModel();
      nbRealLinks += linksDbf[i].getRowCount();
    }

    // Compute a task length for the progress bar
    lengthOfTask = 2 * nbRealNodes + nbRealLinks;

    // Re(set) the information assigned to each real link
    for (int i = 0; i < linksEsriLayer.length; i++) {
      EsriGraphicList egl = linksEsriLayer[i].getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();
      int j = 0;

      while (it.hasNext()) {
        OMGraphic omg = it.next();

        // Compute the length of the real link
        float length = 0;
        float speed = 0;

        // Get the node1 real num
        List<Object> values = linksDbf[i].getRecord(j);

        int node1 = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE1));

        if (omg instanceof EsriPolyline) {
          EsriPolyline epl = (EsriPolyline) omg;
          length = NodusEsriLayer.getLength(epl, Length.KM);
          speed = JDBCUtils.getFloat(values.get(NodusC.DBF_IDX_SPEED));
        }

        // Be sure we have a RealLink attached to this OMGraphic
        if (omg.getAttribute(0) == null) {
          omg.putAttribute(0, new RealLink());
        }

        // Initializes the real link with the relevant values
        RealLink rl = (RealLink) omg.getAttribute(0);
        rl.setOriginNodeId(node1);
        rl.setLength(length);
        rl.setSpeed(speed);
        // rl.setDuration(3600 * length / speed);
        rl.resetPassengerCarUnits();

        j++;
      }
    }

    // Load the service ID associated to each real link
    nodusProject.getServiceHandler().loadServicesForVirtualNetwork();

    // Filter the objects to generate
    if (ap.isLimitedToHighlightedArea()) {
      filterRealNetworkObjects(true);
    } else {
      filterRealNetworkObjects(false);
    }
  }

  /**
   * Creates a queue of cost parser workers and a pool of threads that will handle these workers.
   *
   * @param iteration The iteration for which the costs must be computed.
   * @param scenario Scenario.
   * @param odClass The OD class for which the costs must be computed.
   * @param timeSlice The time slice for which the costs must be computed.
   * @param nbThreads The number of thread to create in the pool.
   * @return True on success.
   */
  public boolean computeCosts(
      int iteration, int scenario, byte odClass, byte timeSlice, int nbThreads) {

    if (vnl == null) {
      return false;
    }

    // Create the work queue
    WorkQueue queue = new WorkQueue();

    // Create a set of worker threads
    CostParserWorker[] worker = new CostParserWorker[nbThreads];
    for (int i = 0; i < worker.length; i++) {
      worker[i] = new CostParserWorker(queue);
    }

    // long start = System.currentTimeMillis();

    // Add the works to the queue
    for (byte groupIndex = 0; groupIndex < getNbGroups(); groupIndex++) {
      CostParser costParser =
          new CostParser(
              costFunctions, nodusProject, scenario, groups[groupIndex], odClass, timeSlice);
      if (!costParser.isInitialized()) {
        // Display the error message
        JOptionPane.showMessageDialog(
            null, costParser.getErrorMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
        return false;
      }

      CostParserWorkerParameters cpp =
          new CostParserWorkerParameters(
              worker, nodusProject, scenario, odClass, groupIndex, this, costParser);
      queue.addWork(cpp);
    }

    // Add special end-of-stream markers to terminate the workers
    for (int i = 0; i < worker.length; i++) {
      queue.addWork(WorkQueue.NO_MORE_WORK);
    }

    nodusMapPanel.startProgress(getNbGroups() * vnl.length);

    // Start the threads on the now correctly initialized parsers
    for (CostParserWorker element : worker) {
      element.start();
    }

    // Wait until all the works are completed
    for (int i = 0; i < nbThreads; i++) {
      try {
        worker[i].join();
      } catch (InterruptedException ex) {
        ex.printStackTrace();
      }
    }

    nodusMapPanel.stopProgress();

    // Test if everything was OK
    for (int i = 0; i < nbThreads; i++) {
      if (worker[i].isCancelled()) {
        if (worker[i].getErrorMessage() != null) {
          JOptionPane.showMessageDialog(
              null, worker[i].getErrorMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
        }
        return false;
      }
    }

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + ((end - start) / 1000));

    return true;
  }

  /**
   * Creates a queue of cost parser workers and a pool of threads that will handle these workers.
   *
   * @param iteration The iteration for which the costs must be computed.
   * @param scenario Scenario.
   * @param odClass The OD class for which the costs must be computed.
   * @param nbThreads The number of thread to create in the pool.
   * @return True on success.
   */
  public boolean computeCosts(int iteration, int scenario, byte odClass, int nbThreads) {
    return computeCosts(iteration, scenario, odClass, (byte) -1, nbThreads);
  }

  /**
   * Select the real network objects that will be considered during the assignment.
   *
   * @param isLimitedToHighlightedArea If true, only the objects that are located in the (enabled)
   *     highlighted area will be considered during the assignment.
   */
  private void filterRealNetworkObjects(boolean isLimitedToHighlightedArea) {

    // Reset all the objects to make them available
    for (NodusEsriLayer element : linksEsriLayer) {
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();

      while (it.hasNext()) {
        OMGraphic omg = it.next();
        RealNetworkObject rl = (RealNetworkObject) omg.getAttribute(0);
        rl.setInHighlightedArea(true);
      }
    }

    for (NodusEsriLayer element : nodesEsriLayer) {
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();

      while (it.hasNext()) {
        OMGraphic omg = it.next();
        RealNetworkObject rl = (RealNetworkObject) omg.getAttribute(0);
        rl.setInHighlightedArea(true);
      }
    }

    // Keep only the objects in the highlighted area if needed.
    if (isLimitedToHighlightedArea && nodusMapPanel.isHighlightedAreaLayerVisible()) {
      HighlightedAreaLayer hlal = nodusMapPanel.getHighlightedAreaLayer();
      for (NodusEsriLayer element : linksEsriLayer) {
        EsriGraphicList egl = element.getEsriGraphicList();
        Iterator<OMGraphic> it = egl.iterator();

        while (it.hasNext()) {
          OMPoly omPoly = (OMPoly) it.next();
          double[] pts = omPoly.getLatLonArray();

          // Transform from radians to degrees!
          double orgLat = ProjMath.radToDeg(pts[0]);
          double orgLon = ProjMath.radToDeg(pts[1]);
          double dstLat = ProjMath.radToDeg(pts[pts.length - 2]);
          double dstLon = ProjMath.radToDeg(pts[pts.length - 1]);

          // Keep this link if its end-nodes are in highlighted area
          if (!hlal.isInHighlightedArea(orgLat, orgLon)
              || !hlal.isInHighlightedArea(dstLat, dstLon)) {
            RealNetworkObject rl = (RealNetworkObject) omPoly.getAttribute(0);
            rl.setInHighlightedArea(false);
          }
        }
      }

      for (NodusEsriLayer element : nodesEsriLayer) {
        EsriGraphicList egl = element.getEsriGraphicList();
        Iterator<OMGraphic> it = egl.iterator();

        while (it.hasNext()) {
          OMPoint omPoint = (OMPoint) it.next();

          double lat = omPoint.getLat();
          double lon = omPoint.getLon();

          // Keep this node if it is in highlighted area
          if (!hlal.isInHighlightedArea(lat, lon)) {
            RealNetworkObject rl = (RealNetworkObject) omPoint.getAttribute(0);
            rl.setInHighlightedArea(false);
          }
        }
      }
    }
  }

  /**
   * Computes the number of vehicles that correspond to the volumes (current and auxiliary) assigned
   * on the virtual links. These vehicles are computed accordingly to their capacity stored in the
   * cost functions file. The same file also can contain the "equivalent standard vehicles" that
   * correspond to the different transportation means used. This information is used to assign
   * standard vehicles on the real links (used to compute volume related cost functions).
   *
   * @param vehiclesParser The VehicleParser that holds the characteristics of the vehicles.
   * @return True on success.
   */
  public boolean volumesToVehicles(VehiclesParser vehiclesParser) {
    return volumesToVehicles(vehiclesParser, (byte) 0);
  }

  /**
   * Computes the number of vehicles that correspond to the volumes (current and auxiliary) assigned
   * on the virtual links during a given time slice. These vehicles are computed accordingly to
   * their capacity stored in the cost functions file. The same file also can contain the
   * "equivalent standard vehicles" that correspond to the different transportation means used. This
   * information is used to assign standard vehicles on the real links (used to compute volume
   * related cost functions). <br>
   * This method is only used by time dependent assignments.
   *
   * @param vehiclesParser The VehicleParser that holds the characteristics of the vehicles.
   * @param timeSlice The time slice to consider.
   * @return True on success.
   */
  public boolean volumesToVehicles(VehiclesParser vehiclesParser, byte timeSlice) {
    resetPassengerCarUnits();
    for (byte groupIndex = 0; groupIndex < getNbGroups(); groupIndex++) {

      int group = groups[groupIndex];
      for (VirtualNodeList element : vnl) {
        // Iterate through all the virtual nodes generated for this real node
        Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

        while (nodeLit.hasNext()) {
          VirtualNode vn = nodeLit.next();

          /*
           * Iterate through all the virtual links that start from this virtual node
           */
          Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

          while (linkLit.hasNext()) {
            VirtualLink vl = linkLit.next();

            double averageLoad;
            double pcu;

            // Vehicles are not computed for transhipment virtual links
            if (vl.getType() == VirtualLink.TYPE_MOVE
                || vl.getType() == VirtualLink.TYPE_LOAD
                || vl.getType() == VirtualLink.TYPE_UNLOAD
                || vl.getType() == VirtualLink.TYPE_TRANSIT) {

              // Take end virtual node to get mode/means for loading vlinks
              if (vl.getType() == VirtualLink.TYPE_LOAD) {
                averageLoad =
                    vehiclesParser.getAverageLoad(
                        group, vl.getEndVirtualNode().getMode(), vl.getEndVirtualNode().getMeans());
                pcu =
                    vehiclesParser.getPassengerCarUnits(
                        group, vl.getEndVirtualNode().getMode(), vl.getEndVirtualNode().getMeans());
              } else {
                averageLoad =
                    vehiclesParser.getAverageLoad(
                        group,
                        vl.getBeginVirtualNode().getMode(),
                        vl.getBeginVirtualNode().getMeans());
                pcu =
                    vehiclesParser.getPassengerCarUnits(
                        group,
                        vl.getBeginVirtualNode().getMode(),
                        vl.getBeginVirtualNode().getMeans());
              }

              vl.volumesToVehicles(groupIndex, timeSlice, averageLoad, pcu);
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Generates a Version 3 virtual network: <br>
   * - Version 1 : The one described in Jourquin B. and Beuthe M., Transportation Policy Analysis
   * with a GIS: The virtual Network of Freight Transportation in Europe, Transportation Research C,
   * Vol 4, pp. 359-371, n°6, 1996. <br>
   * - Version 2 : Used since Nodus 4, in which the virtual links are oriented.<br>
   * - Version 3 : Includes lines an services, as explained in Jourquin B., Iassinovskaia G.,
   * Lechien J., and Pinna J., Lines and services in a strategic multi-modal freight network model:
   * Methodology and application, presented to European Regional Science Association annual
   * congress, Liverpool (UK), August 2008 and to the European Transport Conference, Leiden (The
   * Netherlands), 6-8 October, 2008.
   *
   * @return boolean True on success.
   */
  public boolean generate() {

    nodusMapPanel.startProgress(lengthOfTask);

    loadLinesForModeMeans();

    /* Create array of linked lists */
    vnl = new VirtualNodeList[nbRealNodes];
    for (int i = 0; i < nbRealNodes; i++) {
      vnl[i] = null;
    }

    initializeNodeIndexMap();

    /* Generate the "moving" virtual links and their associated nodes */
    for (int i = 0; i < nbLinkLayers; i++) {
      EsriGraphicList egl = linksEsriLayer[i].getEsriGraphicList();

      for (int j = 0; j < linksDbf[i].getRowCount(); j++) {
        if (!nodusMapPanel.updateProgress(
            i18n.get(VirtualNetwork.class, "Moving_virtual_links", "Moving virtual links"))) {
          return false;
        }

        RealLink realLink = (RealLink) egl.getOMGraphicAt(j).getAttribute(0);

        if (!realLink.isInHighlightedArea()) {
          continue;
        }

        /* Get the relevant fields to build the virtual nodes */
        List<Object> values = linksDbf[i].getRecord(j);

        // Ignore not enabled links
        int enabled = Integer.valueOf(JDBCUtils.getInt(values.get(NodusC.DBF_IDX_ENABLED)));
        if (enabled == 0) {
          continue;
        }

        int link = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));

        Integer node1 = Integer.valueOf(JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE1)));

        Integer node2 = Integer.valueOf(JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE2)));

        byte mode = JDBCUtils.getByte(values.get(NodusC.DBF_IDX_MODE));

        byte means = JDBCUtils.getByte(values.get(NodusC.DBF_IDX_MEANS));
        LinkedList<Integer> services =
            nodusMapPanel.getNodusProject().getServiceHandler().getServicesForLink(link);

        /* iterate through all means of the link */
        for (byte k = 1; k <= means; k++) {
          /*
           * test the Mode Means is Line exclusive (if is exists lines for these mode and means,
           * for instance the railroads) (if not exclusive the traffic don't need to follow the
           * way of a line, for instance a car) then if is true, we make the virtual network with
           * the line number and force the traffic to follow the lines. and if is false, we make
           * the virtual network with a default number for line (default number = 0) so the
           * traffic is free.
           */
          if (isServiceForModeMeans(mode, k)) {

            if (services != null) {
              /*
               * test if the link has a line. If false a virtual Link is not created
               * for this real link.
               */
              Iterator<Integer> it = services.iterator();
              while (it.hasNext()) {
                /*
                 * A virtualLink is created for all thes line of the mode /means.
                 */
                int service = it.next();
                int meansByService =
                    nodusMapPanel.getNodusProject().getServiceHandler().getMeansForService(service);
                // Virtual nodes
                if (meansByService == k) {
                  NodeLayerAndRowIndex idx = nodeIndex.get(node1);
                  double lat = vnl[idx.indexInVirtualNodeList].getGraphic().getLat();
                  double lon = vnl[idx.indexInVirtualNodeList].getGraphic().getLon();

                  VirtualNode n1p =
                      new VirtualNode(
                          nbVirtualNodes++,
                          node1.intValue(),
                          link,
                          mode,
                          k,
                          (short) service,
                          lat,
                          lon);
                  vnl[idx.indexInVirtualNodeList].addVirtualNode(n1p);

                  VirtualNode n1n =
                      new VirtualNode(
                          nbVirtualNodes++,
                          -node1.intValue(),
                          link,
                          mode,
                          k,
                          (short) service,
                          lat,
                          lon);
                  vnl[idx.indexInVirtualNodeList].addVirtualNode(n1n);

                  idx = nodeIndex.get(node2);
                  lat = vnl[idx.indexInVirtualNodeList].getGraphic().getLat();
                  lon = vnl[idx.indexInVirtualNodeList].getGraphic().getLon();

                  VirtualNode n2p =
                      new VirtualNode(
                          nbVirtualNodes++,
                          node2.intValue(),
                          link,
                          mode,
                          k,
                          (short) service,
                          lat,
                          lon);
                  vnl[idx.indexInVirtualNodeList].addVirtualNode(n2p);

                  VirtualNode n2n =
                      new VirtualNode(
                          nbVirtualNodes++,
                          -node2.intValue(),
                          link,
                          mode,
                          k,
                          (short) service,
                          lat,
                          lon);
                  vnl[idx.indexInVirtualNodeList].addVirtualNode(n2n);

                  /*
                   * Moving virtual links are negative to positive oriented
                   */
                  n1n.add(new VirtualLink(nbVirtualLinks++, i, j, n1n, n2p, realLink));
                  n2n.add(new VirtualLink(nbVirtualLinks++, i, j, n2n, n1p, realLink));
                }
              }
            }
          } else {
            /*
             * A VirtualLink is created for the link, with not lines, because the
             * mode means aren't exclusive line.
             */

            // Virtual nodes
            NodeLayerAndRowIndex idx = nodeIndex.get(node1);
            double lat = vnl[idx.indexInVirtualNodeList].getGraphic().getLat();
            double lon = vnl[idx.indexInVirtualNodeList].getGraphic().getLon();

            VirtualNode n1p =
                new VirtualNode(
                    nbVirtualNodes++, node1.intValue(), link, mode, k, (short) 0, lat, lon);
            vnl[idx.indexInVirtualNodeList].addVirtualNode(n1p);

            VirtualNode n1n =
                new VirtualNode(
                    nbVirtualNodes++, -node1.intValue(), link, mode, k, (short) 0, lat, lon);
            vnl[idx.indexInVirtualNodeList].addVirtualNode(n1n);

            idx = nodeIndex.get(node2);
            lat = vnl[idx.indexInVirtualNodeList].getGraphic().getLat();
            lon = vnl[idx.indexInVirtualNodeList].getGraphic().getLon();

            VirtualNode n2p =
                new VirtualNode(
                    nbVirtualNodes++, node2.intValue(), link, mode, k, (short) 0, lat, lon);
            vnl[idx.indexInVirtualNodeList].addVirtualNode(n2p);

            VirtualNode n2n =
                new VirtualNode(
                    nbVirtualNodes++, -node2.intValue(), link, mode, k, (short) 0, lat, lon);
            vnl[idx.indexInVirtualNodeList].addVirtualNode(n2n);

            n1n.add(new VirtualLink(nbVirtualLinks++, i, j, n1n, n2p, realLink));
            n2n.add(new VirtualLink(nbVirtualLinks++, i, j, n2n, n1p, realLink));
          }
        }
      }
    }

    for (VirtualNodeList element : vnl) {
      if (!nodusMapPanel.updateProgress(
          i18n.get(
              VirtualNetwork.class, "Transhipment_virtual_links", "Transhipment virtual links"))) {

        return false;
      }

      LinkedList<VirtualNode> ll = element.getVirtualNodeList();
      Iterator<VirtualNode> lit1 = ll.iterator();

      while (lit1.hasNext()) {
        VirtualNode beginNode = lit1.next();
        Iterator<VirtualNode> lit2 = ll.listIterator(ll.indexOf(beginNode));

        while (lit2.hasNext()) {
          VirtualNode endNode = lit2.next();

          // These virtual links are always from + to -
          if (beginNode.getSign() != endNode.getSign()) {

            /*
             * Transhipment links are generated for transhipment nodes only and Transit
             * links are generated for same mode/means combinations
             */
            boolean n1 =
                nodusMapPanel
                    .getNodusProject()
                    .getServiceHandler()
                    .isNodeStopService(beginNode.getRealNodeId(false), beginNode.getService());
            if (beginNode.getService() == 0) {
              n1 = true;
            }

            boolean n2 =
                nodusMapPanel
                    .getNodusProject()
                    .getServiceHandler()
                    .isNodeStopService(endNode.getRealNodeId(false), endNode.getService());
            if (endNode.getService() == 0) {
              n2 = true;
            }

            if (element.isTranshipmentNode() && n1 && n2
                || beginNode.getModeMeansServiceKey() == endNode.getModeMeansServiceKey()
                || beginNode.getModeMeansKey() == endNode.getModeMeansKey()
                    && element.isChangingServiceNode()
                    && n1
                    && n2) {
              NodeLayerAndRowIndex idx = nodeIndex.get(Integer.valueOf(element.getRealNodeId()));

              // Find out which type of virtual link it is
              byte type;

              if (beginNode.getModeMeansServiceKey() == endNode.getModeMeansServiceKey()
                  && nodusMapPanel
                      .getNodusProject()
                      .getServiceHandler()
                      .isNodeStopService(beginNode.getRealNodeId(false), beginNode.getService())
                  && beginNode.getRealNodeId(false) == endNode.getRealNodeId(false)) {
                type = VirtualLink.TYPE_STOP;
              } else if (beginNode.getModeMeansServiceKey() == endNode.getModeMeansServiceKey()) {
                type = VirtualLink.TYPE_TRANSIT;
              } else if (beginNode.getModeMeansKey() == endNode.getModeMeansKey()) {
                type = VirtualLink.TYPE_SWITCH;
              } else {
                type = VirtualLink.TYPE_TRANSHIP;
              }

              if (type == VirtualLink.TYPE_TRANSHIP
                  || type == VirtualLink.TYPE_SWITCH
                  || beginNode.getRealLinkId() != endNode.getRealLinkId()) {

                // test if transit is allowed here
                boolean generateVirtualLink = true;
                if (type == VirtualLink.TYPE_TRANSIT && !element.isTransitAllowed()) {
                  generateVirtualLink = false;
                }

                if (generateVirtualLink) {
                  if (beginNode.getSign() == VirtualNode.POSITIVE) {
                    beginNode.add(
                        new VirtualLink(
                            nbVirtualLinks++,
                            idx.layerIndex,
                            idx.rowInLayer,
                            beginNode,
                            endNode,
                            type));
                  } else {
                    endNode.add(
                        new VirtualLink(
                            nbVirtualLinks++,
                            idx.layerIndex,
                            idx.rowInLayer,
                            endNode,
                            beginNode,
                            type));
                  }
                }
              }
            }
          }
        } // while (lit2.hasNext())
      } // while (lit1.hasNext())
    } // for (int i = 0 ; i < VN.length ; i++)

    for (VirtualNodeList element : vnl) {
      if (!nodusMapPanel.updateProgress(
          i18n.get(
              VirtualNetwork.class, "Un_Loading_virtual_links", "(Un)Loading virtual links"))) {

        return false;
      }

      double lat = element.getGraphic().getLat();
      double lon = element.getGraphic().getLon();

      if (element.isLoadingUnloadingNode()) {
        VirtualNode loadingNode =
            new VirtualNode(
                nbVirtualNodes++,
                -element.getRealNodeId(),
                0,
                (byte) 0,
                (byte) 0,
                (short) 0,
                lat,
                lon);
        VirtualNode unLoadingNode =
            new VirtualNode(
                nbVirtualNodes++,
                element.getRealNodeId(),
                0,
                (byte) 0,
                (byte) 0,
                (short) 0,
                lat,
                lon);

        // Create all (un)loading virtual links. Must be - to - or + to
        // + oriented
        Iterator<VirtualNode> lit = element.getVirtualNodeList().iterator();

        while (lit.hasNext()) {
          VirtualNode currentNode = lit.next();
          NodeLayerAndRowIndex idx = nodeIndex.get(Integer.valueOf(element.getRealNodeId()));
          boolean n =
              nodusMapPanel
                  .getNodusProject()
                  .getServiceHandler()
                  .isNodeStopService(currentNode.getRealNodeId(false), currentNode.getService());
          if (currentNode.getSign() == VirtualNode.NEGATIVE
              && (n || !isServiceForModeMeans(currentNode.getMode(), currentNode.getMeans()))) {
            VirtualLink vl =
                new VirtualLink(
                    nbVirtualLinks++,
                    idx.layerIndex,
                    idx.rowInLayer,
                    loadingNode,
                    currentNode,
                    VirtualLink.TYPE_LOAD);
            loadingNode.add(vl);
          }
          if (currentNode.getSign() == VirtualNode.POSITIVE
              && (n || !isServiceForModeMeans(currentNode.getMode(), currentNode.getMeans()))) {
            VirtualLink vl =
                new VirtualLink(
                    nbVirtualLinks++,
                    idx.layerIndex,
                    idx.rowInLayer,
                    currentNode,
                    unLoadingNode,
                    VirtualLink.TYPE_UNLOAD);
            currentNode.add(vl);
          }
        }

        /*
         * Add the two new nodes to the list. Put the loading virtual node as last node so its
         * easy to find it back when needed
         */
        element.addVirtualNode(unLoadingNode);
        element.addVirtualNode(loadingNode);

        element.setLoadingVirtualNodeNum(loadingNode.getId());
        element.setUnloadingVirtualNodeId(unLoadingNode.getId());
      }
    }

    // System.out.println(nbVirtualNodes + " " + nbVirtualLinks);
    // End of generation
    nodusMapPanel.stopProgress();
    return true;
  }

  /**
   * Generates the adjacency list for the generated virtual network for a given group index. This is
   * the structure that will be used by the shortest path algorithm.
   *
   * @param groupIndex The index of the group for which the adjacency list must be generated.
   * @return AdjacencyNode[] The adjacency list.
   */
  public synchronized AdjacencyNode[] generateAdjacencyList(byte groupIndex) {
    // Compute number of virtual nodes
    int nbNodes = 0;
    int nbAvailableModeMeans = 0;

    byte[][] mm = new byte[NodusC.MAXMM][NodusC.MAXMM];

    for (VirtualNodeList element : vnl) {
      nbNodes += element.getVirtualNodeList().size();
    }

    // Initialize if needed
    if (graph == null) {

      graph = new AdjacencyNode[getNbGroups()][];
      availableModeMeans = new int[getNbGroups()][];
    }

    // Create the adjacency vector
    graph[groupIndex] = new AdjacencyNode[nbNodes + 1];

    for (VirtualNodeList element : vnl) {
      // Iterate through all the virtual nodes generated for this real
      // node
      Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

      while (nodeLit.hasNext()) {
        VirtualNode vn = nodeLit.next();

        AdjacencyNode current = new AdjacencyNode(vn);
        graph[groupIndex][vn.getId()] = current;

        // Iterate through all the virtual links that start from this virtual node
        Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

        while (linkLit.hasNext()) {
          VirtualLink vl = linkLit.next();

          // Ignore excluded links
          if (vl.getCost(groupIndex) < 0) {
            continue;
          }

          // Keep the list of possible modes and means
          if (vl.getType() == VirtualLink.TYPE_LOAD) {
            int mode = vl.getEndVirtualNode().getMode();
            int means = vl.getEndVirtualNode().getMeans();
            if (mm[mode][means] == 0) {
              mm[mode][means] = 1;
              nbAvailableModeMeans++;
            }
          }

          // if (vl.getBeginNode() != vlorigin) {
          current.setNext(new AdjacencyNode(vl.getEndVirtualNode()), groupIndex, vl);
          current = current.nextNode;
          // }
        }
      }
    }

    // Build an array with all the available possible mode/means combinations
    availableModeMeans[groupIndex] = new int[nbAvailableModeMeans];
    int index = 0;
    for (int i = 0; i < NodusC.MAXMM; i++) {
      for (int j = 0; j < NodusC.MAXMM; j++) {
        if (mm[i][j] == 1) {
          availableModeMeans[groupIndex][index] = i * NodusC.MAXMM + j;
          index++;
        }
      }
    }

    return graph[groupIndex];
  }

  /**
   * Assignment end time, expressed in minutes after midnight.
   *
   * @return The assignment end time.
   */
  public int getAssignmentEndTime() {
    return assignmentEndTime;
  }

  /**
   * Assignment start time, expressed in minutes after midnight.
   *
   * @return The assignment starting time.
   */
  public int getAssignmentStartTime() {
    return assignmentStartTime;
  }

  /**
   * Returns an array of the available mode-means combination for a given group, identified by its
   * index.
   *
   * @param groupIndex The group index for which the available mode-means must be retrieved. The
   *     content of the returned array is a value that corresponds to a key computed as (mode *
   *     NodusC.MAXMM) + means.
   * @return The available mode-means
   */
  public synchronized int[] getAvailableModeMeans(byte groupIndex) {
    return availableModeMeans[groupIndex];
  }

  /**
   * Returns an array of bytes containing the ID of the groups to assign.
   *
   * @return An array of group ID.
   */
  public byte[] getGroups() {
    return groups;
  }

  /**
   * Returns the number of groups to assign.
   *
   * @return The number of groups to assign.
   */
  public int getNbGroups() {
    return groups.length;
  }

  /**
   * Returns the number of OD classes.
   *
   * @return The number of OD classes.
   */
  public byte getNbODClasses() {
    return nbODClasses;
  }

  /**
   * Returns the number of time slices (for time dependent assignments only).
   *
   * @return The number of time slices.
   */
  public int getNbTimeSlices() {
    return nbTimeSlices;
  }

  /**
   * Returns the number of generated virtual links.
   *
   * @return The number of generated virtual links in this virtual network.
   */
  public int getNbVirtualLinks() {
    return nbVirtualLinks;
  }

  /**
   * Returns the number of generated virtual nodes.
   *
   * @return The number of generated virtual nodes in this virtual network.
   */
  public int getNbVirtualNodes() {
    return nbVirtualNodes;
  }

  /**
   * Returns the index of a given real node in the virtual node list.
   *
   * @param realNodeId The ID of the real node.
   * @param lookForLoadingUnloadingNode If true, only loading or unloading nodes are considered.
   * @return Index of the real node in virtual node list, or -1 if not found.
   */
  public int getNodeIndexInVirtualNodeList(int realNodeId, boolean lookForLoadingUnloadingNode) {
    NodeLayerAndRowIndex index = nodeIndex.get(realNodeId);

    if (index == null) {
      return -1;
    } else {
      if (lookForLoadingUnloadingNode
          && !vnl[index.indexInVirtualNodeList].isLoadingUnloadingNode()) {
        return -1;
      }

      return index.indexInVirtualNodeList;
    }
  }

  /**
   * Returns the set of NodusEsriLayer that contain the real links.
   *
   * @return NodusEsriLayer[]
   */
  public NodusEsriLayer[] getRealLinks() {
    return linksEsriLayer;
  }

  /**
   * Returns the set of NodusEsriLayer layers that contain the real nodes.
   *
   * @return NodusEsriLayer[]
   */
  public NodusEsriLayer[] getRealNodes() {
    return nodesEsriLayer;
  }

  /**
   * Duration of a time slice, expressed in minutes.
   *
   * @return The duration of a time slice.
   */
  public int getTimeSliceDuration() {
    return timeSliceDuration;
  }

  /**
   * Return the value of a given variable in the cost functions set.
   *
   * @param varName Name of the variable to retrieve.
   * @param group Group of goods for which the variable has to be retrieved.
   * @param odClass OD class for which the variable has to be retrieved.
   * @return Value of the variable.
   */
  public double getValue(String varName, byte group, byte odClass) {

    /* if (vnl == null) {
      return Double.NaN;
    }*/

    return CostParser.getValue(costFunctions, varName, scenario, group, odClass);
  }

  /**
   * Returns the structure of the virtual network, i.e. an array of virtual node lists.
   *
   * @return VirtualNodeList[]
   */
  public VirtualNodeList[] getVirtualNodeLists() {
    return vnl;
  }

  /**
   * Creates an hash table associating an index in the VNL structure to all the real nodes numbers.
   */
  private void initializeNodeIndexMap() {
    nodeIndex = new HashMap<>(nbRealNodes);

    // Double upperBoundCosts;
    int index = 0;

    for (int i = 0; i < nodesDbf.length; i++) {
      EsriGraphicList egl = nodesEsriLayer[i].getEsriGraphicList();

      for (int j = 0; j < nodesDbf[i].getRowCount(); j++) {
        // "num to index" hashtable
        List<Object> values = nodesDbf[i].getRecord(j);

        int num = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));
        nodeIndex.put(Integer.valueOf(num), new NodeLayerAndRowIndex(i, j, index));

        // Initialize VN entry with new virtual node list
        int tranship = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_TRANSHIP));
        vnl[index] = new VirtualNodeList(num, tranship, (OMPoint) egl.getOMGraphicAt(j));

        index++;
      }
    }
  }

  /**
   * Tests if a service must be generated for a given mode/means combination.
   *
   * @param mode Mode to test.
   * @param means Means to test.
   * @return True if a service must be generated or false for free flow.
   */
  private boolean isServiceForModeMeans(byte mode, byte means) {
    return linesForModeMeans[mode][means];
  }

  /**
   * Loads the matrix of mode/means combinations for which services must be generated, i.e. if the
   * "SERVICELINE.mode,means" variable exists in the cost functions.
   */
  private void loadLinesForModeMeans() {

    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
        String key = NodusC.VARNAME_SERVICELINES + "." + mode + "," + means;

        String value = costFunctions.getProperty(key, "false");
        linesForModeMeans[mode][means] = Boolean.parseBoolean(value);
      }
    }
  }

  /**
   * Computes the first derivative of the objective function used in Frank-Wolfe like assignment
   * techniques. The iteration parameter is only used as information to pass to the progress-bar.
   * NaN is returned if task was aborted
   *
   * @param iteration The current iteration in the assignment.
   * @param approachedLambda An estimated/approached value of the descent.
   * @param threads Number of threads used for the assignment.
   * @return The first derivative of the objective function.
   */
  public double objectiveFunctionFirstDerivative(
      int iteration, double approachedLambda, int threads) {
    double firstDerivative = 0.0;

    nodusMapPanel.startProgress(vnl.length * getNbGroups() * nbODClasses);

    System.out.println(
        MessageFormat.format(
            i18n.get(
                VirtualNetwork.class,
                "Last_lambda_descent",
                "Iteration {0}: Last lambda descent={1}"),
            iteration,
            formatter.format(approachedLambda)));

    for (byte odClass = 0; odClass < nbODClasses; odClass++) {

      // Create the work queue
      WorkQueue queue = new WorkQueue();

      // Create a set of worker threads
      CostParserWorker[] worker = new CostParserWorker[threads];
      for (int i = 0; i < worker.length; i++) {
        worker[i] = new CostParserWorker(queue);
      }

      // Add the works to the queue
      for (byte groupIndex = 0; groupIndex < getNbGroups(); groupIndex++) {
        CostParser costParser =
            new CostParser(
                costFunctions, nodusProject, scenario, groups[groupIndex], odClass, (byte) -1);
        if (!costParser.isInitialized()) {
          nodusMapPanel.stopProgress();
          // Display the error message
          JOptionPane.showMessageDialog(
              null, costParser.getErrorMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
          return Double.NaN;
        }

        CostParserWorkerParameters cpp =
            new CostParserWorkerParameters(
                worker, nodusProject, scenario, odClass, groupIndex, this, costParser, true);
        queue.addWork(cpp);
      }

      // Add special end-of-stream markers to terminate the workers
      for (int i = 0; i < worker.length; i++) {
        queue.addWork(WorkQueue.NO_MORE_WORK);
      }

      // Start the threads on the now correctly initialized parsers
      for (CostParserWorker element : worker) {
        element.start();
      }

      // Wait until all the works are completed
      for (int i = 0; i < threads; i++) {
        try {
          worker[i].join();
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }

      // Test if everything was OK
      for (int i = 0; i < threads; i++) {
        if (worker[i].isCancelled()) {
          if (worker[i].getErrorMessage() != null) {
            JOptionPane.showMessageDialog(
                null, worker[i].getErrorMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
          }
          nodusMapPanel.stopProgress();
          return Double.NaN;
        }
      }

      // Add the zpm value computed for each group
      for (CostParserWorker element : worker) {
        firstDerivative += element.getFirstDerivative();
      }
    }

    nodusMapPanel.stopProgress();

    return firstDerivative;
  }

  /**
   * Returns true if the given OD class has demand stored in the OD matrix.
   *
   * @param odClass The OD class to test.
   * @return True if there is a demand for this class.
   */
  public boolean odClassHasDemand(byte odClass) {
    if (nbODClasses == 1) {
      return true;
    } else {
      return odClassHasDemand[odClass];
    }
  }

  /** Resets the vehicles assigned to the real links. */
  private void resetPassengerCarUnits() {
    for (NodusEsriLayer element : linksEsriLayer) {
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();

      while (it.hasNext()) {
        OMGraphic omg = it.next();
        RealLink rl = (RealLink) omg.getAttribute(0);
        rl.resetPassengerCarUnits();
      }
    }
  }

  /**
   * Sets the specific parameters for time dependent assignments.
   *
   * @param assignmentStartTime The assignment starting time, expressed in minutes after midnight.
   * @param assignmentEndTime The assignment end time, expressed in minutes after midnight.
   * @param timeSliceDuration The duration of a time slice, expressed in minutes.
   */
  public void setAssignmentTimeParameters(
      int assignmentStartTime, int assignmentEndTime, int timeSliceDuration) {
    this.assignmentStartTime = assignmentStartTime;
    this.assignmentEndTime = assignmentEndTime;
    this.timeSliceDuration = timeSliceDuration;
    nbTimeSlices = (assignmentEndTime - assignmentStartTime) / timeSliceDuration;
  }

  /**
   * Set the list of groups to assign in the virtual network. This method also prepares the virtual
   * to accept the right number of groups. This method can only be called when the virtual network
   * structure has been generated.
   *
   * @param groups An array of group ID.
   */
  public void setGroups(byte[] groups) {
    if (vnl == null) {
      System.out.println("VNL structure not yet generated!");

      return;
    }

    this.groups = groups;

    for (VirtualNodeList element : vnl) {
      // Iterate through all the virtual nodes generated for this real
      // node
      Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

      while (nodeLit.hasNext()) {
        VirtualNode vn = nodeLit.next();

        // Iterate through all the virtual links that start from this
        // virtual node
        Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

        while (linkLit.hasNext()) {
          VirtualLink vl = linkLit.next();
          vl.setNbGroups(groups.length, nbTimeSlices);
        }
      }
    }
  }

  /**
   * Sets the ID of the highest OD class encountered in the OD matrix.
   *
   * @param maxClass The ID of the highest OD class.
   */
  public void setMaxClass(byte maxClass) {

    nbODClasses = (byte) (maxClass + 1);
    odClassHasDemand = new boolean[maxClass + 1];
    for (int i = 0; i < maxClass; i++) {
      odClassHasDemand[i] = false;
    }

    // Detect the classes for which there is a demand
    for (VirtualNodeList element : vnl) {
      LinkedList<ODCell> odLine = element.getInitialDemandList();
      if (odLine != null) {
        Iterator<ODCell> it = odLine.iterator();
        while (it.hasNext()) {
          ODCell odCell = it.next();
          odClassHasDemand[odCell.getODClass()] = true;
        }
      }
    }
  }
}
