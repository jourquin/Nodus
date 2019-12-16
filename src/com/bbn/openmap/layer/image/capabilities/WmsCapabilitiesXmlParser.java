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

package com.bbn.openmap.layer.image.capabilities;

import java.util.Vector;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Sax parser used to retrieve the information gathered after a getCapabilities request.
 *
 * <p>The parser doesn't work with WMS 1.3 specs (which doesn't use latlonboundingbox anymore), so
 * that "&version=1.1" must for instance be added to the capabilities request.
 *
 * <p>The code of this parser is inspired from wmsClient 1.2, see
 * http://www.mancke-software.de/wmsClient/ for details." <br>
 *
 * @author Bart Jourquin
 */
public class WmsCapabilitiesXmlParser extends DefaultHandler {
  private String charBuff;

  @SuppressWarnings("rawtypes")
  private Vector<Vector> currPath;

  private boolean getMapIsParent;

  private boolean layerIsParent;

  private boolean mapIsParent;

  private WmsServerInfo server;

  private boolean serviceIsParent;

  /**
   * Receive notification of character data inside an element.
   *
   * @param ch char[]
   * @param start int
   * @param length int
   */
  @Override
  public void characters(char[] ch, int start, int length) {
    charBuff = new String(ch, start, length);
  }

  /**
   * Remove empty nodes.
   *
   * @param node Vector
   */
  @SuppressWarnings({"unchecked","rawtypes"})
  protected void eliminateInnerNodes(Vector node) {
    for (int i = 0; i < node.size(); i++) {
      CapabilitiesInnerTreeNode currChild = (CapabilitiesInnerTreeNode) node.elementAt(i);

      if (currChild.size() > 0) {
        eliminateInnerNodes(currChild);
      } else {
        node.setElementAt(new CapabilitiesTreeNode(currChild.getLayerInformation()), i);
      }
    }
  }

  /** Called when work is finished. */
  @Override
  public void endDocument() {
    if (currPath.elementAt(0).size() > 0) {
      eliminateInnerNodes(currPath.elementAt(0));
    }
  }

  /**
   * Handles the end of an element.
   *
   * @param uri String
   * @param localName String
   * @param name String
   */
  @SuppressWarnings("unchecked")
  @Override
  public void endElement(String uri, String localName, String name) {
    if (name.toLowerCase().equals("layer")) {
      Object child = currPath.remove(currPath.size() - 1);
      currPath.lastElement().add(child);
    } else if (layerIsParent
        && currPath.size() > 1
        && (name.toLowerCase().equals("srs")
            || name.toLowerCase().equals("title")
            || name.toLowerCase().equals("name")
            || name.toLowerCase().equals("abstract"))) {
      ((CapabilitiesInnerTreeNode) currPath.lastElement())
          .getLayerInformation()
          .setField(name.toLowerCase(), charBuff);
    } else if (name.toLowerCase().equals("getmap")) {
      getMapIsParent = false;
    } else if (name.toLowerCase().equals("service")) {
      serviceIsParent = false;
    } else if (name.toLowerCase().equals("map")) {
      mapIsParent = false;
    }

    if (name.toLowerCase().equals("style")) {
      layerIsParent = true;
    }

    if (getMapIsParent && name.toLowerCase().equals("format")) {
      server.addGetMapFormat(charBuff);
    }

    if (serviceIsParent && name.toLowerCase().equals("title")) {
      server.setServerName(charBuff);
    }
  }

  /**
   * Handles SAX parser exceptions.
   *
   * @param e SAXParseException
   * @throws SAXException SAX exception
   */
  @Override
  public void fatalError(SAXParseException e) throws SAXException {
    throw e;
  }

  /**
   * Returns the parsed data in a Vector.
   *
   * @return Vector
   */
  public Vector<?> getDataTree() {
    return currPath.elementAt(0);
  }

  /** Called at the beginning of the parsing process. */
  @Override
  public void startDocument() {
    server = new WmsServerInfo();

    currPath = new Vector<>();
    currPath.add(server);
  }

  /**
   * Start a new element.
   *
   * @param uri String
   * @param localName String
   * @param name String
   * @param attributes Attributes
   */
  @Override
  public void startElement(String uri, String localName, String name, Attributes attributes) {
    if (name.toLowerCase().equals("layer")) {
      WmsLayerInfo layerInfo = new WmsLayerInfo(server);

      if (attributes.getValue("queryable") == null) {
        layerInfo.setField("queryable", "0");
      } else {
        layerInfo.setField("queryable", attributes.getValue("queryable"));
      }

      if (currPath.size() > 1) {
        layerInfo.setParentLayer(
            ((CapabilitiesInnerTreeNode) currPath.lastElement()).getLayerInformation());
      }

      currPath.add(new CapabilitiesInnerTreeNode(layerInfo));
      layerIsParent = true;
    } else if (name.toLowerCase().equals("srs")
        || name.toLowerCase().equals("title")
        || name.toLowerCase().equals("name")
        || name.toLowerCase().equals("abstract")) {
      // Not implemented here. Is it useful?
    } else if (layerIsParent && name.toLowerCase().equals("latlonboundingbox")) {
      ((CapabilitiesInnerTreeNode) currPath.lastElement())
          .getLayerInformation()
          .setLatLonBoundingBox(
              Float.parseFloat(attributes.getValue("minx")),
                  Float.parseFloat(attributes.getValue("miny")),
              Float.parseFloat(attributes.getValue("maxx")),
                  Float.parseFloat(attributes.getValue("maxy")));
    } else {
      if (name.toLowerCase().equals("style")) {
        layerIsParent = false;
      }

      if (name.toLowerCase().equals("getmap")) {
        getMapIsParent = true;
      } else if (name.toLowerCase().equals("service")) {
        serviceIsParent = true;
      } else if (name.toLowerCase().equals("map")) {
        mapIsParent = true;
      }
    }

    if (getMapIsParent && name.toLowerCase().equals("onlineresource")) {
      server.setGetMapUrl(attributes.getValue("xlink:href"));
    }

    if (mapIsParent && name.toLowerCase().equals("get")) {
      server.setGetMapUrl(attributes.getValue("onlineResource"));
    }

    if (mapIsParent && name.toLowerCase().equals("jpeg")) {
      server.addGetMapFormat("image/jpeg");
    } else if (mapIsParent && name.toLowerCase().equals("png")) {
      server.addGetMapFormat("image/png");
    } else if (mapIsParent && name.toLowerCase().equals("gif")) {
      server.addGetMapFormat("image/gif");
    }
  }
}
