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

package com.bbn.openmap.layer.shape.jung.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.jung.JungVirtualLink;
import com.bbn.openmap.layer.shape.jung.JungVirtualNode;
import com.bbn.openmap.util.I18n;
import com.google.common.base.Function;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.RadialTreeLayout;
import edu.uci.ics.jung.algorithms.layout.TreeLayout;
import edu.uci.ics.jung.graph.DelegateForest;
import edu.uci.ics.jung.graph.Forest;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.VisualizationServer;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.LensMagnificationGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalLensGraphMouse;
import edu.uci.ics.jung.visualization.control.ScalingControl;
import edu.uci.ics.jung.visualization.renderers.Renderer;
import edu.uci.ics.jung.visualization.transform.AbstractLensSupport;
import edu.uci.ics.jung.visualization.transform.HyperbolicTransformer;
import edu.uci.ics.jung.visualization.transform.LayoutLensSupport;
import edu.uci.ics.jung.visualization.transform.MagnifyTransformer;
import edu.uci.ics.jung.visualization.transform.shape.HyperbolicShapeTransformer;
import edu.uci.ics.jung.visualization.transform.shape.MagnifyShapeTransformer;
import edu.uci.ics.jung.visualization.transform.shape.ViewLensSupport;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * This dialog box displays a graphical representation of all the virtual links for a given real
 * node or real link. The drawing API is based on the Java Universal Network/Graph Framework (JUNG).
 * <br>
 * See http://jung.sourceforge.net/ <br>
 *
 * @author Bart Jourquin
 */
// TODO The "node view" should also handle time dependent results
public class VirtualNetworkGraphViewerDlg extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 276007512376916496L;

  private FormattedTime[] availableTimes;

  private JButton closeButton;

  private int currentObjectId;

  private FormattedTime currentTime;

  /** Used when the virtual network for a node is not a centroid. */
  private JungVirtualNode fakeNode = null;

  private int frameSize;

  /** The graph. */
  private Forest<String, String> graph;

  /** provides a Hyperbolic lens for the model. */
  private AbstractLensSupport<String, String> hyperbolicLayoutSupport;

  /** provides a Hyperbolic lens for the view. */
  private AbstractLensSupport<String, String> hyperbolicViewSupport;

  /** Display (or not) the virtual network for a real node or real link. */
  private boolean isNode;

  private int labelToDisplay = 0;

  private List<JungVirtualLink> linksList;

  /** provides a magnification lens for the model. */
  private AbstractLensSupport<String, String> magnifyLayoutSupport;

  /** provides a magnification lens for the view. */
  private AbstractLensSupport<String, String> magnifyViewSupport;

  private int nbLinksToDisplay;

  private List<JungVirtualNode> nodesList;

  private int originNode = 0;

  private RadialTreeLayout<String, String> radialLayout;

  private VisualizationServer.Paintable rings;

  private TreeLayout<String, String> treeLayout;

  /** the visual component and renderer for the graph. */
  private VisualizationViewer<String, String> visualizationViewer;

  private Color transparentColor = new Color(255, 255, 255, 255);

  private boolean hasLD = false;
  private boolean hasUL = false;
  private boolean hasTR = false;
  private boolean hasTP = false;

  private Function<String, String> edgeLabel =
      new Function<String, String>() {
        @Override
        public String apply(String string) {

          // Use the right time period
          String s = string += "#" + currentTime.getSeconds();
          JungVirtualLink jvl = null;
          Iterator<JungVirtualLink> it = linksList.iterator();
          while (it.hasNext()) {
            JungVirtualLink element = it.next();
            if (element.toString().equals(s)) {
              jvl = element;
              break;
            }
          }

          if (jvl == null) {
            return "";
          }

          String color = "blue";
          if (jvl.getType() == VirtualLink.TYPE_LOAD || jvl.getType() == VirtualLink.TYPE_UNLOAD) {
            color = "red";
          }

          double value = 0;
          switch (labelToDisplay) {
            case 0:
              value = Math.round(jvl.getQuantity());
              break;
            case 1:
              value = Math.round(jvl.getVehicles());
              break;
            case 2:
              value = jvl.getUnitCost();
              break;
            default:
              break;
          }
          return "<html><font color=\"" + color + "\">" + value;
        }
      };

  /** Assigns a color to an edge, based on its type. */
  private Function<String, Paint> edgePaint =
      new Function<String, Paint>() {
        @Override
        public Paint apply(String s) {
          int type = getVirtualLinkType(s);
          switch (type) {
            case VirtualLink.TYPE_LOAD:
              return Color.RED;
            case VirtualLink.TYPE_UNLOAD:
              return Color.BLUE;
            case VirtualLink.TYPE_TRANSIT:
              return Color.DARK_GRAY;
            case VirtualLink.TYPE_TRANSHIP:
              return Color.GREEN;
            default: // Moving
              return Color.MAGENTA;
          }
        }
      };

  /** Assigns a color to an edge, based on its type. */
  private Function<String, Paint> arrowFillPaint =
      new Function<String, Paint>() {
        @Override
        public Paint apply(String s) {
          return Color.GRAY;
        }
      };

  /** Creates a "popup" label, with more info that the regular label displayed. */
  private Function<String, String> edgePopupLabel =
      new Function<String, String>() {
        @Override
        public String apply(String string) {
          // Use the right time period
          String s = string += "#" + currentTime.getSeconds();

          JungVirtualLink jvl = null;
          Iterator<JungVirtualLink> it = linksList.iterator();
          while (it.hasNext()) {
            JungVirtualLink element = it.next();
            if (element.toString().equals(s)) {
              jvl = element;
              break;
            }
          }

          if (jvl == null) {
            return "";
          }

          String label =
              "<html>"
                  + "From&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: "
                  + jvl.getOriginJungVirtualNode().toString()
                  + "<br>"
                  + ""
                  + "To&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: "
                  + jvl.getDestinationJungVirtualNode().toString()
                  + "<br>"
                  + ""
                  + "Quantity&nbsp;&nbsp;: "
                  + Math.round(jvl.getQuantity())
                  + "<br>"
                  + ""
                  + "Vehicles&nbsp;&nbsp;: "
                  + Math.round(jvl.getVehicles())
                  + "<br>"
                  + ""
                  + "Unit cost&nbsp;: "
                  + jvl.getUnitCost()
                  + "<br>"
                  + "</html>";
          return label;
        }
      };

  /** Assigns a stroke to an edge, based on its type. */
  private Function<String, Stroke> edgeStroke =
      new Function<String, Stroke>() {
        float[] dash = {4.0f};

        @Override
        public Stroke apply(String s) {
          int type = getVirtualLinkType(s);
          switch (type) {
            case VirtualLink.TYPE_LOAD:
              return new BasicStroke(2.0f);
            case VirtualLink.TYPE_UNLOAD:
              return new BasicStroke(2.0f);
            case VirtualLink.TYPE_TRANSIT:
              return new BasicStroke(
                  1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f);
            case VirtualLink.TYPE_TRANSHIP:
              return new BasicStroke(1.5f);
            default:
              return new BasicStroke(1.5f);
          }
        }
      };

  /** Assigns a label to a vertex, based on its type. */
  private Function<String, String> vertexLabel =
      new Function<String, String>() {
        @Override
        public String apply(String s) {
          StringTokenizer stringTokenizer = new StringTokenizer(s, ":");
          int node = Integer.parseInt(stringTokenizer.nextElement().toString());
          int link = Integer.parseInt(stringTokenizer.nextElement().toString());
          int mode = Integer.parseInt(stringTokenizer.nextElement().toString());
          int means = Integer.parseInt(stringTokenizer.nextElement().toString());
          int service = 0;
          if (stringTokenizer.hasMoreElements()) {
            service = Integer.parseInt(stringTokenizer.nextElement().toString());
          }
          if (isNode) {
            if (link == 0) {
              return "" + node;
            } else {
              if (service == 0) {
                return link + ":" + mode + ":" + means;
              } else {
                return link + ":" + mode + ":" + means + ":(" + service + ")";
              }
            }
          } else {
            if (service == 0) {
              return node + ":" + mode + ":" + means;
            } else {
              return node + ":" + mode + ":" + means + ":(" + service + ")";
            }
          }
        }
      };

  /** Assigns a color to a vertex, based on its type. */
  private Function<String, Paint> vertexPaint =
      new Function<String, Paint>() {
        @Override
        public Paint apply(String s) {
          return Color.LIGHT_GRAY;
          /* if (s.startsWith("-")) {
            return Color.RED;
          } else {
            return Color.BLUE;
          }*/
        }
      };

  /**
   * Creates a "popup" label for a vertex, with more info than the regular label that is displayed.
   */
  private Function<String, String> vertexPopupLabel =
      new Function<String, String>() {
        @Override
        public String apply(String s) {
          StringTokenizer stringTokenizer = new StringTokenizer(s, ":");
          int node = Integer.parseInt(stringTokenizer.nextElement().toString());
          int link = Integer.parseInt(stringTokenizer.nextElement().toString());
          int mode = Integer.parseInt(stringTokenizer.nextElement().toString());
          int means = Integer.parseInt(stringTokenizer.nextElement().toString());
          int service = 0;
          if (stringTokenizer.hasMoreElements()) {
            service = Integer.parseInt(stringTokenizer.nextElement().toString());
          }

          String label;
          if (service != 0) {
            label =
                "<html>Node&nbsp;&nbsp;&nbsp;&nbsp;: "
                    + node
                    + "<br>Link&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: "
                    + link
                    + "<br> Mode&nbsp;&nbsp;&nbsp;&nbsp;: "
                    + mode
                    + "<br>Means&nbsp;&nbsp;&nbsp;: "
                    + means
                    + "<br>Service&nbsp;: "
                    + service
                    + "<br></html>";
          } else {
            label =
                "<html>Node&nbsp;&nbsp;: "
                    + node
                    + "<br>Link&nbsp;&nbsp;&nbsp;: "
                    + link
                    + "<br> Mode&nbsp;&nbsp;: "
                    + mode
                    + "<br>Means&nbsp;: "
                    + means
                    + "<br></html>";
          }

          return label;
        }
      };

  /** Assigns a size to a vertex, based on its type. */
  private Function<String, Shape> vertexSize =
      new Function<String, Shape>() {
        @Override
        public Shape apply(String s) {
          Ellipse2D circle = new Ellipse2D.Double(-10, -10, 20, 20);

          // (Un)loading nodes are larger
          if (isLoadingNode(s)) {
            return AffineTransform.getScaleInstance(1.5, 1.5).createTransformedShape(circle);
          } else {
            return circle;
          }
        }
      };

  /**
   * Creates a new virtual network graph viewer dialog.
   *
   * @param parent The parent JDialog.
   * @param nodeList The list of JungVirtualNodes to display.Box controls =
   *     Box.createHorizontalBox();
   * @param linkList The list of JungVirtualLinks to display.
   * @param isNode True if the virtual nodes and virtual links are generated from a real node.
   */
  public VirtualNetworkGraphViewerDlg(
      JDialog parent,
      List<JungVirtualNode> nodeList,
      List<JungVirtualLink> linkList,
      boolean isNode) {
    super(parent, "", true);
    this.nodesList = nodeList;
    this.linksList = linkList;
    this.isNode = isNode;

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    frameSize = screenSize.width;
    if (screenSize.height < frameSize) {
      frameSize = screenSize.height;
    }
    frameSize = (int) (frameSize * 0.6);
    this.setSize(frameSize, frameSize);

    setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus7.png")));

    // Get all the time periods for which there is some traffic
    availableTimes = getAvailableTimes();
    currentTime = availableTimes[0];

    setContentPane(getGraphPanel());

    getRootPane().setDefaultButton(closeButton);
    closeButton.requestFocus();

    // Center on screen
    pack();
    int x = (screenSize.width - getWidth()) / 2;
    int y = (screenSize.height - getHeight()) / 2;
    setLocation(x, y);
  }

  /**
   * Creates the graph for a virtual network at a real link.
   *
   * @param pass Two passes are needed, one for each directions.
   */
  private void createTreeForLink(int pass) {

    if (pass == 0) {

      nbLinksToDisplay = getNbLinksToDisplay();

      // Add the moving links in one direction
      Iterator<JungVirtualLink> it2 = linksList.iterator();

      while (it2.hasNext()) {
        JungVirtualLink jvl = it2.next();

        // Keep the first node as origin
        if (originNode == 0) {
          originNode = jvl.getOriginJungVirtualNode().getNode();
          currentObjectId = jvl.getOriginJungVirtualNode().getLink();
          if (isTimeDependent()) {
            setTitle(
                MessageFormat.format(
                    i18n.get(
                        VirtualNetworkGraphViewerDlg.class,
                        "Virtual_network_at_link_time",
                        "Virtual network at link {0} at {1}"),
                    currentObjectId,
                    currentTime));
          } else {
            setTitle(
                MessageFormat.format(
                    i18n.get(
                        VirtualNetworkGraphViewerDlg.class,
                        "Virtual_network_at_link",
                        "Virtual network at link {0}"),
                    currentObjectId));
          }
        }

        if (jvl.getOriginJungVirtualNode().getNode() == originNode) {
          graph.addEdge(
              jvl.toString(false),
              jvl.getOriginJungVirtualNode().toString(true),
              jvl.getDestinationJungVirtualNode().toString(true));
        }
      }

    } else {
      // Add the reverse links
      Iterator<JungVirtualLink> it2 = linksList.iterator();
      while (it2.hasNext()) {
        JungVirtualLink jvl = it2.next();

        if (jvl.getOriginJungVirtualNode().getNode() != originNode) {
          graph.addEdge(
              jvl.toString(false),
              jvl.getOriginJungVirtualNode().toString(true),
              jvl.getDestinationJungVirtualNode().toString(true));
        }
      }
    }
  }

  /**
   * Creates the graph for a virtual network at a real node.
   *
   * @param pass Two passes are needed, one for each direction.
   */
  private void createTreeForNode(int pass) {

    if (pass == 0) {

      // Check which types of virtual links are present
      Iterator<JungVirtualLink> it = linksList.iterator();
      while (it.hasNext()) {
        JungVirtualLink jvl = it.next();

        if (jvl.getType() == VirtualLink.TYPE_LOAD) {
          hasLD = true;
        }

        if (jvl.getType() == VirtualLink.TYPE_UNLOAD) {
          hasUL = true;
        }

        if (jvl.getType() == VirtualLink.TYPE_TRANSIT) {
          hasTR = true;
        }

        if (jvl.getType() == VirtualLink.TYPE_TRANSHIP) {
          hasTP = true;
        }
      }

      /*
       * Add all the nodes
       */
      boolean isCentroid = false;
      Iterator<JungVirtualNode> it1 = nodesList.iterator();
      JungVirtualNode jvn = null;
      while (it1.hasNext()) {
        jvn = it1.next();
        graph.addVertex(jvn.toString());
        if (jvn.isCentroid()) {
          isCentroid = true;
        }
      }

      /* If this node is not a centroid, create a fake one and its links */
      if (!isCentroid) {
        fakeNode = new JungVirtualNode(jvn.getNode(), 0, (byte) 0, (byte) 0, 0);
        graph.addVertex(fakeNode.toString());

        // Create fake loading links
        it1 = nodesList.iterator();
        while (it1.hasNext()) {
          jvn = it1.next();
          JungVirtualLink jvl =
              new JungVirtualLink(fakeNode, jvn, 0, 0, 0, currentTime.getSeconds());
          linksList.add(jvl);
        }
      }
      currentObjectId = Math.abs(jvn.getNode());
      if (isTimeDependent()) {
        setTitle(
            MessageFormat.format(
                i18n.get(
                    VirtualNetworkGraphViewerDlg.class,
                    "Virtual_network_at_node_time",
                    "Virtual network at node {0} at {1}"),
                currentObjectId,
                currentTime));
      } else {
        setTitle(
            MessageFormat.format(
                i18n.get(
                    VirtualNetworkGraphViewerDlg.class,
                    "Virtual_network_at_node",
                    "Virtual network at node {0}"),
                currentObjectId));
      }

      /* Add the (un)loading links, but all pointing to non loading/unloading. */
      Iterator<JungVirtualLink> it2 = linksList.iterator();
      while (it2.hasNext()) {
        JungVirtualLink jvl = it2.next();

        if (jvl.getType() == VirtualLink.TYPE_LOAD) {

          graph.addEdge(
              jvl.toString(false),
              jvl.getOriginJungVirtualNode().toString(),
              jvl.getDestinationJungVirtualNode().toString());
        }

        if (jvl.getType() == VirtualLink.TYPE_UNLOAD) {

          graph.addEdge(
              jvl.toString(false),
              jvl.getDestinationJungVirtualNode().toString(),
              jvl.getOriginJungVirtualNode().toString());
        }
      }
    } else {

      /* Remove the fakes */
      if (fakeNode != null) {
        Iterator<JungVirtualLink> it2 = linksList.iterator();
        while (it2.hasNext()) {
          JungVirtualLink jvl = it2.next();

          if (jvl.getOriginJungVirtualNode().equals(fakeNode)) {
            graph.removeEdge(jvl.toString(false));
          }
        }
        graph.removeVertex(fakeNode.toString());
      }

      /* Reverse the unloading links. */
      Iterator<JungVirtualLink> it2 = linksList.iterator();
      while (it2.hasNext()) {
        JungVirtualLink jvl = it2.next();

        if (jvl.getType() == VirtualLink.TYPE_UNLOAD) {
          graph.removeEdge(jvl.toString(false));
          graph.addEdge(
              jvl.toString(false),
              jvl.getOriginJungVirtualNode().toString(),
              jvl.getDestinationJungVirtualNode().toString());
        }
      }

      /* Add transit and transhipment links */
      it2 = linksList.iterator();
      while (it2.hasNext()) {
        JungVirtualLink jvl = it2.next();

        if (jvl.getType() == VirtualLink.TYPE_TRANSIT
            || jvl.getType() == VirtualLink.TYPE_TRANSHIP) {
          graph.removeEdge(jvl.toString(false));
          graph.addEdge(
              jvl.toString(false),
              jvl.getOriginJungVirtualNode().toString(),
              jvl.getDestinationJungVirtualNode().toString());
        }
      }
    }
  }

  /**
   * Returns a sorted array of the available time periods.
   *
   * @return A sorted array of the available time periods.
   */
  private FormattedTime[] getAvailableTimes() {

    // Keep all the available time periods
    Iterator<JungVirtualLink> it = linksList.iterator();
    HashMap<String, String> keys = new HashMap<>();
    while (it.hasNext()) {
      String k = (it.next()).toString();
      // Keep the "time" part of the key
      int beginIndex = k.lastIndexOf('#');
      k = k.substring(beginIndex + 1, k.length());

      if (keys.get(k) == null) {
        keys.put(k, k);
      }
    }

    // Create an ordered list of times
    Iterator<String> it2 = keys.values().iterator();
    LinkedList<Integer> ll = new LinkedList<>();
    while (it2.hasNext()) {
      ll.add(Integer.parseInt(it2.next()));
    }
    Collections.sort(ll);

    // Change to a formatted hour string
    LinkedList<FormattedTime> ll2 = new LinkedList<>();
    Iterator<Integer> it3 = ll.iterator();
    while (it3.hasNext()) {
      ll2.add(new FormattedTime(it3.next()));
    }
    return ll2.toArray(new FormattedTime[0]);
  }

  /**
   * Returns the panel.
   *
   * @return JPanel
   */
  @SuppressWarnings({"unchecked"})
  private JPanel getGraphPanel() {

    // create a simple graph for the demo
    graph = new DelegateForest<>();

    if (isNode) {
      createTreeForNode(0);
    } else {
      createTreeForLink(0);
    }

    /*
     * Tree layout for links, Radial layout with rings for nodes
     */
    treeLayout = new TreeLayout<>(graph, frameSize / (nbLinksToDisplay + 1), frameSize / 3);
    radialLayout = new RadialTreeLayout<>(graph, 180);
    radialLayout.setSize(new Dimension(frameSize, frameSize));
    if (isNode) {
      visualizationViewer =
          new VisualizationViewer<>(radialLayout, new Dimension(frameSize, frameSize));
      rings = new Rings(graph, radialLayout, visualizationViewer);
      visualizationViewer.addPreRenderPaintable(rings);
    } else {
      visualizationViewer =
          new VisualizationViewer<>(treeLayout, new Dimension(frameSize, frameSize));
    }
    visualizationViewer.setBackground(Color.white);

    /*
     * Creating the graph needs two passes in order to be able to display arrows in both directions
     * in a tree
     */

    if (isNode) {
      createTreeForNode(1);
    } else {
      createTreeForLink(1);
      // Draw the links horizontally
      rotate(visualizationViewer);
    }

    final ScalingControl scaler = new CrossoverScalingControl();
    JPanel content = new JPanel();
    content.setBackground(Color.WHITE);
    content.setLayout(new BorderLayout());
    content.add(visualizationViewer, BorderLayout.CENTER);
    GraphZoomScrollPane panel = new GraphZoomScrollPane(visualizationViewer);
    content.add(panel);
    DefaultModalGraphMouse<Object, Object> graphMouse = new DefaultModalGraphMouse<>();
    visualizationViewer.setGraphMouse(graphMouse);
    visualizationViewer.addKeyListener(graphMouse.getModeKeyListener());

    /*
     * Lens support
     */
    hyperbolicViewSupport =
        new ViewLensSupport<>(
            visualizationViewer,
            new HyperbolicShapeTransformer(
                visualizationViewer,
                visualizationViewer
                    .getRenderContext()
                    .getMultiLayerTransformer()
                    .getTransformer(Layer.VIEW)),
            new ModalLensGraphMouse());
    hyperbolicLayoutSupport =
        new LayoutLensSupport<>(
            visualizationViewer,
            new HyperbolicTransformer(
                visualizationViewer,
                visualizationViewer
                    .getRenderContext()
                    .getMultiLayerTransformer()
                    .getTransformer(Layer.LAYOUT)),
            new ModalLensGraphMouse());
    magnifyViewSupport =
        new ViewLensSupport<>(
            visualizationViewer,
            new MagnifyShapeTransformer(
                visualizationViewer,
                visualizationViewer
                    .getRenderContext()
                    .getMultiLayerTransformer()
                    .getTransformer(Layer.VIEW)),
            new ModalLensGraphMouse(new LensMagnificationGraphMousePlugin(1.f, 6.f, .2f)));
    magnifyLayoutSupport =
        new LayoutLensSupport<>(
            visualizationViewer,
            new MagnifyTransformer(
                visualizationViewer,
                visualizationViewer
                    .getRenderContext()
                    .getMultiLayerTransformer()
                    .getTransformer(Layer.LAYOUT)),
            new ModalLensGraphMouse(new LensMagnificationGraphMousePlugin(1.f, 6.f, .2f)));
    hyperbolicLayoutSupport
        .getLensTransformer()
        .setLensShape(hyperbolicViewSupport.getLensTransformer().getLensShape());
    magnifyViewSupport
        .getLensTransformer()
        .setLensShape(hyperbolicLayoutSupport.getLensTransformer().getLensShape());
    magnifyLayoutSupport
        .getLensTransformer()
        .setLensShape(magnifyViewSupport.getLensTransformer().getLensShape());

    String[] labels = {
      i18n.get(VirtualNetworkGraphViewerDlg.class, "Label_Quantity", "Label : Quantity"),
      i18n.get(VirtualNetworkGraphViewerDlg.class, "Label_Vehicles", "Label : Vehicles"),
      i18n.get(VirtualNetworkGraphViewerDlg.class, "Label_Unitcost", "Label : Unit cost")
    };

    JComboBox<String> labelComboBox = new JComboBox<>(labels);
    labelComboBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JComboBox<String> cb = (JComboBox<String>) e.getSource();
            labelToDisplay = cb.getSelectedIndex();
            visualizationViewer.repaint();
          }
        });

    JComboBox<FormattedTime> timeComboBox = new JComboBox<>(availableTimes);
    timeComboBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {

            JComboBox<String> cb = (JComboBox<String>) e.getSource();
            currentTime = availableTimes[cb.getSelectedIndex()];

            if (isNode) {
              if (isTimeDependent()) {
                setTitle(
                    MessageFormat.format(
                        i18n.get(
                            VirtualNetworkGraphViewerDlg.class,
                            "Virtual_network_at_node_time",
                            "Virtual network at node {0} at time {1}"),
                        currentObjectId,
                        currentTime));
              } else {
                setTitle(
                    MessageFormat.format(
                        i18n.get(
                            VirtualNetworkGraphViewerDlg.class,
                            "Virtual_network_at_node",
                            "Virtual network at node {0}"),
                        currentObjectId));
              }
            } else {
              if (isTimeDependent()) {
                setTitle(
                    MessageFormat.format(
                        i18n.get(
                            VirtualNetworkGraphViewerDlg.class,
                            "Virtual_network_at_link_time",
                            "Virtual network at link {0} at time {1}"),
                        currentObjectId,
                        currentTime));
              } else {
                setTitle(
                    MessageFormat.format(
                        i18n.get(
                            VirtualNetworkGraphViewerDlg.class,
                            "Virtual_network_at_link",
                            "Virtual network at link {0}"),
                        currentObjectId));
              }
            }

            visualizationViewer.repaint();
          }
        });

    JComboBox<?> modeComboBox = graphMouse.getModeComboBox();
    modeComboBox.addItemListener(graphMouse.getModeListener());
    graphMouse.setMode(ModalGraphMouse.Mode.TRANSFORMING);

    JButton plusButton = new JButton("+");
    plusButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            scaler.scale(visualizationViewer, 1.1f, visualizationViewer.getCenter());
          }
        });
    JButton minusButton = new JButton("-");
    minusButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            scaler.scale(visualizationViewer, 1 / 1.1f, visualizationViewer.getCenter());
          }
        });

    JRadioButton normalGlass =
        new JRadioButton(i18n.get(VirtualNetworkGraphViewerDlg.class, "None", "None"));
    normalGlass.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              if (hyperbolicViewSupport != null) {
                hyperbolicViewSupport.deactivate();
              }
              if (hyperbolicLayoutSupport != null) {
                hyperbolicLayoutSupport.deactivate();
              }
              if (magnifyViewSupport != null) {
                magnifyViewSupport.deactivate();
              }
              if (magnifyLayoutSupport != null) {
                magnifyLayoutSupport.deactivate();
              }
            }
          }
        });

    JRadioButton hyperViewGlass =
        new JRadioButton(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Hyperbolic_View", "Hyperbolic View"));
    hyperViewGlass.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            hyperbolicViewSupport.activate(e.getStateChange() == ItemEvent.SELECTED);
            hyperbolicViewSupport.getLens().setPaint(Color.LIGHT_GRAY);
          }
        });
    JRadioButton hyperModelGlass =
        new JRadioButton(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Hyperbolic_Layout", "Hyperbolic Layout"));
    hyperModelGlass.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            hyperbolicLayoutSupport.activate(e.getStateChange() == ItemEvent.SELECTED);
            hyperbolicLayoutSupport.getLens().setPaint(Color.LIGHT_GRAY);
          }
        });
    JRadioButton magnifyViewGlass =
        new JRadioButton(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Magnified_View", "Magnified View"));
    magnifyViewGlass.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            magnifyViewSupport.activate(e.getStateChange() == ItemEvent.SELECTED);
            magnifyViewSupport.getLens().setPaint(Color.LIGHT_GRAY);
          }
        });
    JRadioButton magnifyModelGlass =
        new JRadioButton(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Magnified_Layout", "Magnified Layout"));
    magnifyModelGlass.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            magnifyLayoutSupport.activate(e.getStateChange() == ItemEvent.SELECTED);
            magnifyLayoutSupport.getLens().setPaint(Color.LIGHT_GRAY);
          }
        });

    ButtonGroup magnifiersGroup = new ButtonGroup();
    magnifiersGroup.add(normalGlass);
    magnifiersGroup.add(hyperModelGlass);
    magnifiersGroup.add(hyperViewGlass);
    magnifiersGroup.add(magnifyModelGlass);
    magnifiersGroup.add(magnifyViewGlass);
    normalGlass.setSelected(true);

    JButton resetButton =
        new JButton(i18n.get(VirtualNetworkGraphViewerDlg.class, "Reset", "Reset"));
    resetButton.addActionListener(
        new ActionListener() {

          @Override
          public void actionPerformed(ActionEvent e) {
            visualizationViewer
                .getRenderContext()
                .getMultiLayerTransformer()
                .getTransformer(Layer.LAYOUT)
                .setToIdentity();
            visualizationViewer
                .getRenderContext()
                .getMultiLayerTransformer()
                .getTransformer(Layer.VIEW)
                .setToIdentity();
          }
        });

    closeButton = new JButton(i18n.get(VirtualNetworkGraphViewerDlg.class, "Close", "Close"));
    closeButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // dispose();
            setVisible(false);
          }
        });

    graphMouse.addItemListener(hyperbolicLayoutSupport.getGraphMouse().getModeListener());
    graphMouse.addItemListener(hyperbolicViewSupport.getGraphMouse().getModeListener());
    graphMouse.addItemListener(magnifyLayoutSupport.getGraphMouse().getModeListener());
    graphMouse.addItemListener(magnifyViewSupport.getGraphMouse().getModeListener());

    int nbRows = 2;
    if (isTimeDependent()) {
      nbRows = 3;
    }
    JPanel scaleGrid = new JPanel(new GridLayout(nbRows, 1, 5, 5));
    scaleGrid.add(plusButton);
    scaleGrid.add(minusButton);
    if (isTimeDependent()) {
      scaleGrid.setBorder(
          BorderFactory.createTitledBorder(
              i18n.get(VirtualNetworkGraphViewerDlg.class, "Zoom_time", "Zoom & time")));
      scaleGrid.add(timeComboBox);
    } else {
      scaleGrid.setBorder(
          BorderFactory.createTitledBorder(
              i18n.get(VirtualNetworkGraphViewerDlg.class, "Zoom", "Zoom")));
    }

    JPanel hyperControls = new JPanel(new GridLayout(3, 2));
    hyperControls.setBorder(
        BorderFactory.createTitledBorder(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Examiner_Lens", "Examiner Lens")));
    hyperControls.add(normalGlass);
    hyperControls.add(labelComboBox);
    hyperControls.add(hyperModelGlass);
    hyperControls.add(magnifyModelGlass);
    hyperControls.add(hyperViewGlass);
    hyperControls.add(magnifyViewGlass);

    JPanel modeBox = new JPanel(new GridLayout(3, 1, 5, 5));
    modeBox.setBorder(
        BorderFactory.createTitledBorder(
            i18n.get(VirtualNetworkGraphViewerDlg.class, "Mode", "Mode")));
    modeBox.add(modeComboBox);
    modeBox.add(resetButton);
    modeBox.add(closeButton);

    Box controls = Box.createHorizontalBox();
    controls.add(scaleGrid);
    controls.add(hyperControls);
    controls.add(modeBox);

    content.add(controls, BorderLayout.SOUTH);

    /*
     * Control how vertices and edges are displayed
     */
    visualizationViewer.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
    visualizationViewer.getRenderContext().setVertexShapeTransformer(vertexSize);
    visualizationViewer.getRenderContext().setVertexLabelTransformer(vertexLabel);
    visualizationViewer
        .getRenderer()
        .getVertexLabelRenderer()
        .setPosition(Renderer.VertexLabel.Position.AUTO);
    visualizationViewer.setVertexToolTipTransformer(vertexPopupLabel);

    visualizationViewer.getRenderContext().setArrowFillPaintTransformer(arrowFillPaint);
    visualizationViewer.getRenderContext().setEdgeDrawPaintTransformer(edgePaint);
    visualizationViewer.getRenderContext().setEdgeStrokeTransformer(edgeStroke);
    visualizationViewer.getRenderContext().setEdgeLabelTransformer(edgeLabel);
    visualizationViewer.getRenderContext().getEdgeLabelRenderer().setRotateEdgeLabels(true);
    visualizationViewer.setEdgeToolTipTransformer(edgePopupLabel);

    // Add legend
    JPanel legend = new JPanel();
    legend.setBackground(transparentColor);
    if (isNode) {
      // Loading
      if (hasLD) {
        JLabel labelLd =
            new JLabel(i18n.get(VirtualNetworkGraphViewerDlg.class, "Loading", "Loading"));
        labelLd.setBackground(transparentColor);
        labelLd.setForeground(Color.RED);
        legend.add(labelLd);
      }

      // UnLoading
      if (hasUL) {
        JLabel labelUl =
            new JLabel(i18n.get(VirtualNetworkGraphViewerDlg.class, "Unloading", "Unloading"));
        labelUl.setBackground(transparentColor);
        labelUl.setForeground(Color.BLUE);
        legend.add(labelUl);
      }

      // Transit
      if (hasTR) {
        JLabel labelTr =
            new JLabel(i18n.get(VirtualNetworkGraphViewerDlg.class, "Transit", "Transit"));
        labelTr.setBackground(transparentColor);
        labelTr.setForeground(Color.DARK_GRAY);
        legend.add(labelTr);
      }

      // Transhipment
      if (hasTP) {
        JLabel labelTp =
            new JLabel(
                i18n.get(VirtualNetworkGraphViewerDlg.class, "Transhipment", "Transhipment"));
        labelTp.setBackground(transparentColor);
        labelTp.setForeground(Color.GREEN);
        legend.add(labelTp);
      }

    } else {

      // Moving
      JLabel labelMv = new JLabel(i18n.get(VirtualNetworkGraphViewerDlg.class, "Moving", "Moving"));
      labelMv.setBackground(transparentColor);
      labelMv.setForeground(Color.MAGENTA);
      legend.add(labelMv);
    }
    visualizationViewer.add(legend);

    return content;
  }

  /**
   * Returns the number of horizontal pairs of nodes to display. Used to compute the right size of
   * the graph
   *
   * @return The number of horizontal pairs of nodes to display.
   */
  private int getNbLinksToDisplay() {

    Iterator<JungVirtualLink> it = linksList.iterator();
    HashMap<String, String> keys = new HashMap<>();
    while (it.hasNext()) {
      JungVirtualLink jvl = it.next();

      long mms1 = jvl.getOriginJungVirtualNode().getModeMeansServiceKey();
      long mms2 = jvl.getDestinationJungVirtualNode().getModeMeansServiceKey();

      if (mms1 > mms2) {
        long tmp = mms1;
        mms1 = mms2;
        mms2 = tmp;
      }
      String key = "" + mms1 + mms2;
      if (keys.get(key) == null) {
        keys.put(key, key);
      }
    }

    return keys.size();
  }

  /**
   * Returns the type of virtual link.
   *
   * @param vl String representation of a virtual link.
   * @return Type of virtual link.
   */
  private int getVirtualLinkType(String vl) {

    StringTokenizer stringTokenizer = new StringTokenizer(vl, "#");
    String vn1 = stringTokenizer.nextElement().toString();
    String vn2 = stringTokenizer.nextElement().toString();

    stringTokenizer = new StringTokenizer(vn1, ":");
    final int node1 = Integer.parseInt(stringTokenizer.nextElement().toString());
    stringTokenizer.nextElement().toString();
    final int mode1 = Integer.parseInt(stringTokenizer.nextElement().toString());
    final int means1 = Integer.parseInt(stringTokenizer.nextElement().toString());

    stringTokenizer = new StringTokenizer(vn2, ":");
    final int node2 = Integer.parseInt(stringTokenizer.nextElement().toString());
    stringTokenizer.nextElement().toString();
    final int mode2 = Integer.parseInt(stringTokenizer.nextElement().toString());
    final int means2 = Integer.parseInt(stringTokenizer.nextElement().toString());

    if (mode1 == 0) {
      return VirtualLink.TYPE_LOAD;
    }
    if (mode2 == 0) {
      return VirtualLink.TYPE_UNLOAD;
    }
    if (Math.abs(node1) != Math.abs(node2)) {
      return VirtualLink.TYPE_MOVE;
    }
    if (mode1 == mode2 && means1 == means2) {
      return VirtualLink.TYPE_TRANSIT;
    }
    return VirtualLink.TYPE_TRANSHIP;
  }

  /**
   * Returns true if a node is a loading point.
   *
   * @param vl String representation fa virtual link.
   * @return True if this virtual link corresponds to a loading operation.
   */
  private boolean isLoadingNode(String vl) {
    StringTokenizer stringTokenizer = new StringTokenizer(vl, ":");
    stringTokenizer.nextElement().toString();
    int link = Integer.parseInt(stringTokenizer.nextElement().toString());
    if (link == 0) {
      return true;
    }
    return false;
  }

  /**
   * Return true if at least one valid time period is associated to the current object.
   *
   * @return True if at least one valid time period exists.
   */
  private boolean isTimeDependent() {
    boolean b = false;
    if (availableTimes.length > 1 || availableTimes[0].getSeconds() != 0) {
      b = true;
    }
    return b;
  }

  /**
   * Rotate the view with 90°.
   *
   * @param The visualization viewer.
   */
  private void rotate(VisualizationViewer<String, String> vv) {
    Layout<String, String> layout = vv.getModel().getGraphLayout();
    Dimension d = layout.getSize();
    Point2D center = new Point2D.Double(d.width / 2, d.height / 2);
    vv.getRenderContext()
        .getMultiLayerTransformer()
        .getTransformer(Layer.LAYOUT)
        .rotate(-Math.PI / 2, center);
  }

  /*  @Override
  public void setVisible(boolean b) {
   System.out.println("3");
      super.setVisible(b);
  }*/
}
