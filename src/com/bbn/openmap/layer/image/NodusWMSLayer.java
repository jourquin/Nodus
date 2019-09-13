/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package com.bbn.openmap.layer.image;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.image.capabilities.CapabilitiesInnerTreeNode;
import com.bbn.openmap.layer.image.capabilities.WmsCapabilitiesXmlParser;
import com.bbn.openmap.layer.image.capabilities.WmsLayerInfo;
import com.bbn.openmap.layer.image.gui.WmsLayersChooserDlg;
import com.bbn.openmap.proj.Gnomonic;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The NodusWMSLayer extends the original OpenMap WMSLayer. Its main new feature is to allow the
 * user to interactively select the layers to display in a list obtained from the WMS server by
 * means of a "getcapabilities".
 *
 * <p>See the WMS specifications and the OpenMap documentation for more details.
 *
 * @author Bart Jourquin
 */
public class NodusWMSLayer extends WMSLayer {

  /** Display a message in a separate thread. */
  class ThreadedDialog extends Thread {

    String msg;

    public ThreadedDialog(String message) {
      super();
      msg = message;
    }

    @Override
    public void run() {
      JOptionPane.showMessageDialog(null, msg, "WMS", JOptionPane.ERROR_MESSAGE);
    }
  }

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 4799752099609388953L;

  private boolean capabilitiesAlreadyFetched = false;

  //Is the server reachable ?
  private boolean isServerReachable;

  private String prefix;

  private Properties properties;

  private Vector<Object> treeData = null;

  /**
   * Intercepts the original OpenMap method in order to reject Gnomonic projections.
   *
   * @param p Projection
   * @return String
   */
  @Override
  public String createQueryString(Projection p) {

    // We need info about the server
    if (!getCapabilities()) {
      return null;
    }

    String s = super.createQueryString(p);

    if (p instanceof Gnomonic) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(
              NodusWMSLayer.class,
              "No_Gnomonic_projections",
              "WMS query doesn't support Gnomonic projections"),
          i18n.get(NodusWMSLayer.class, "Error", "Error"),
          JOptionPane.ERROR_MESSAGE);

      return null;
    }

    return s;
  }

  @Override
  public void doPrepare() {
    if (isServerReachable) {
      super.doPrepare();
    }
  }

  /**
   * Asks the WMS server for its capabilities.
   *
   * @return True on success.
   */
  public boolean getCapabilities() {

    // Just call it once
    if (capabilitiesAlreadyFetched) {
      return true;
    }
    capabilitiesAlreadyFetched = true;

    StringBuffer buf = new StringBuffer(queryHeader);

    if (vendorSpecificNames != null) {
      if (vendorSpecificValues != null) {
        StringTokenizer nameTokenizer = new StringTokenizer(vendorSpecificNames, ",");
        StringTokenizer valueTokenizer = new StringTokenizer(vendorSpecificValues, ",");
        String paramName = null;
        String paramValue = null;
        while (nameTokenizer.hasMoreTokens()) {
          try {
            paramName = nameTokenizer.nextToken();
            paramValue = valueTokenizer.nextToken();
            buf.append("&").append(paramName).append("=").append(paramValue);
          } catch (NoSuchElementException e) {
            e.printStackTrace();
          }
        }
      }
    }

    buf.append("?" + "&REQUEST=GetCapabilities" + "&version=" + wmsVersion);

    java.net.URL url = null;

    try {
      url = new java.net.URL(buf.toString());
      java.net.HttpURLConnection urlc = (java.net.HttpURLConnection) url.openConnection();
      if (urlc == null || urlc.getContentType() == null) {
        Thread thread =
            new ThreadedDialog(
                MessageFormat.format(
                    i18n.get(
                        NodusWMSLayer.class,
                        "Couldn_t_connect_to",
                        "{0}: \n\nCouldn''t connect to {1}"),
                    getName(),
                    getServerName()));
        thread.start();
        return false;
      }

      // Try to parse the stream
      WmsCapabilitiesXmlParser cparser = new WmsCapabilitiesXmlParser();

      SAXParserFactory spf = SAXParserFactory.newInstance();
      spf.setNamespaceAware(true);
      spf.setValidating(true);
      try {
        SAXParser parser = spf.newSAXParser();
        parser.parse(new InputSource(urlc.getInputStream()), cparser);
      } catch (ParserConfigurationException e) {
        e.printStackTrace();
      } catch (SAXException e) {
        e.printStackTrace();
      }

      // Retrieve the parsed data
      Vector<?> constructedDataTree = null;

      try {
        constructedDataTree = cparser.getDataTree();
      } catch (Exception ex1) {
        // Create and start the thread
        Thread thread =
            new ThreadedDialog(
                i18n.get(
                    NodusWMSLayer.class,
                    "Could not parse capabilities",
                    "Could not parse capabilities"));
        thread.start();

        return false;
      }

      if (constructedDataTree == null || constructedDataTree.size() == 0) {
        // Create and start the thread
        Thread thread =
            new ThreadedDialog(
                i18n.get(
                    NodusWMSLayer.class,
                    "Error_on_parsing_the_server_description",
                    "Error on parsing the server description"));
        thread.start();

        return false;
      }

      treeData = new Vector<>();
      treeData.add(constructedDataTree.elementAt(0));
    } catch (java.net.MalformedURLException murle) {
      Thread thread =
          new ThreadedDialog("NodusWMSLayer: URL \"" + buf.toString() + "\" is malformed.");
      thread.start();

      return false;
    } catch (java.io.IOException ioe) {
      Thread thread =
          new ThreadedDialog(
              MessageFormat.format(
                  i18n.get(
                      NodusWMSLayer.class,
                      "Couldn_t_connect_to",
                      "{0}: \n\nCouldn''t connect to {1}"),
                  getName(),
                  getServerName()));
      thread.start();
      return false;
    }

    // Find the name of the server in treeData
    CapabilitiesInnerTreeNode ctsn = (CapabilitiesInnerTreeNode) treeData.elementAt(0);
    WmsLayerInfo wli = ctsn.getLayerInformation();
    super.wmsServer = wli.getServerInformation().getGetMapUrl();

    return true;
  }

  /**
   * Adds a "Choose layers" button to the original OpenMap WMSLayer getGUI().
   *
   * @return Component
   */
  @Override
  public java.awt.Component getGUI() {

    if (!isServerReachable) {
      return null;
    }

    final JPanel panel = (JPanel) super.getGUI();

    // Add a new button to existent panel
    JButton layersButton =
        new JButton(i18n.get(NodusWMSLayer.class, "Choose_layers", "Choose layers"));

    final NodusWMSLayer _this = this;
    layersButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            // Fetch layers on server
            Cursor oldCursor = panel.getCursor();
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            boolean ok = getCapabilities();
            panel.setCursor(oldCursor);
            if (ok) {
              WmsLayersChooserDlg dlg = new WmsLayersChooserDlg(_this);
              // Hide the GUI
              Component c = panel.getParent();
              while (c != null) {
                c.setVisible(false);
                c = c.getParent();
              }
              dlg.setVisible(true);
            }
          }
        });

    panel.add(layersButton);
    return panel;
  }

  /**
   * Returns the prefix used for the layer in the properties.
   *
   * @return String
   */
  public String getPrefix() {
    return prefix;
  }

  /**
   * Returns the properties.
   *
   * @return Properties
   */
  public Properties getProperties() {
    return properties;
  }

  /**
   * Returns the information tree relative to the fetched capabilities (list of layers and
   * associated information).
   *
   * @return Vector
   */
  public Vector<Object> getTreeData() {
    return treeData;
  }

  /**
   * Intercepts the original setProperties() in order to have access to the properties to store the
   * available and selected WMS layers.
   *
   * @param prefix String
   * @param p Properties
   */
  @Override
  public void setProperties(String prefix, Properties p) {
    properties = p;
    this.prefix = PropUtils.getScopedPropertyPrefix(prefix);

    // Just to avoid ugly java message during reprojections
    p.setProperty(prefix + "interruptable", "false");

    // Test if server is reachable
    String key =
        p.getProperty(PropUtils.getScopedPropertyPrefix(prefix) + WMSLayer.WMSServerProperty, null);
    if (key == null) {
      isServerReachable = false;
    } else {
      isServerReachable = NetUtils.pingHost(key);
    }

    super.setProperties(prefix, p);
  }

  /**
   * Updates the display, using the information stored in the properties.
   *
   * @param p Properties
   */
  public void updateLayer(Properties p) {
    layers = p.getProperty(prefix + WMSLayer.LayersProperty);
    doPrepare();
  }
}
