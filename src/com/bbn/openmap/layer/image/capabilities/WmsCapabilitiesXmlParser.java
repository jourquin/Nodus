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

package com.bbn.openmap.layer.image.capabilities;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Sax parser used to retrieve the information gathered after a getCapabilities request.
 *
 * <p>Use {@link #parse(InputStream)} for untrusted XML. It configures the XML reader as well as the
 * callbacks; creating a handler alone does not secure a caller's SAX reader.
 *
 * <p>Supports legacy Map and WMS 1.1/1.3 GetMap descriptions, including namespaced layer metadata
 * and inherited geographic bounds. Only HTTP GET bindings are used by this client.
 *
 * <p>The code of this parser is inspired from wmsClient 1.2, see
 * http://www.mancke-software.de/wmsClient/ for details." <br>
 *
 * @author Bart Jourquin
 */
public class WmsCapabilitiesXmlParser extends DefaultHandler2 {
  private final Deque<Element> elements = new ArrayDeque<>();

  private Vector<Vector<Object>> currPath;

  private WmsServerInfo server;

  /** Keeps text scoped to its element, including split SAX character callbacks. */
  private static final class Element {
    final String name;
    final StringBuilder text = new StringBuilder();
    final String[] bounds;

    Element(String name) {
      this.name = name;
      bounds = name.equals("ex_geographicboundingbox") ? new String[4] : null;
    }
  }

  /**
   * Parses capabilities using the same restricted XML configuration as the WMS layer.
   *
   * <p>WMS 1.1 DOCTYPE declarations are accepted without fetching or validating their external
   * DTDs. Custom entities are rejected before expansion. No external DTD, entity, schema or
   * XInclude resource may be read. Unsupported security settings cause parsing to fail.
   *
   * @param input capabilities XML stream
   * @return the handler containing the completely parsed capabilities tree
   * @throws IOException if reading the capabilities fails
   * @throws SAXException if XML is malformed, unsafe or security settings are unsupported
   * @throws ParserConfigurationException if a secure parser cannot be created
   */
  public static WmsCapabilitiesXmlParser parse(InputStream input)
      throws IOException, SAXException, ParserConfigurationException {
    final SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);
    factory.setXIncludeAware(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    final SAXParser parser = factory.newSAXParser();
    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    final XMLReader reader = parser.getXMLReader();
    final WmsCapabilitiesXmlParser handler = new WmsCapabilitiesXmlParser();
    reader.setContentHandler(handler);
    reader.setErrorHandler(handler);
    reader.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
    reader.setEntityResolver(
        (publicId, systemId) -> {
          throw new SAXException("External XML resources are not allowed in WMS capabilities.");
        });
    reader.parse(new InputSource(input));
    return handler;
  }

  /**
   * Rejects custom internal entities, including recursive and exponential expansion declarations.
   *
   * @param name entity name
   * @param value entity value
   * @throws SAXException always, before the entity can be expanded
   */
  @Override
  public void internalEntityDecl(String name, String value) throws SAXException {
    throw new SAXException("Custom XML entities are not allowed in WMS capabilities.");
  }

  /**
   * Rejects external general and parameter entity declarations without resolving them.
   *
   * @param name entity name
   * @param publicId public identifier
   * @param systemId external identifier
   * @throws SAXException always, before the external resource can be read
   */
  @Override
  public void externalEntityDecl(String name, String publicId, String systemId)
      throws SAXException {
    throw new SAXException("External XML entities are not allowed in WMS capabilities.");
  }

  /**
   * Rejects unresolved references rather than silently accepting incomplete metadata.
   *
   * @param name skipped entity name
   * @throws SAXException always
   */
  @Override
  public void skippedEntity(String name) throws SAXException {
    throw new SAXException("Unresolved XML entity in WMS capabilities: " + name);
  }

  /**
   * Receive notification of character data inside an element.
   *
   * @param ch char[]
   * @param start int
   * @param length int
   */
  @Override
  public void characters(char[] ch, int start, int length) {
    if (!elements.isEmpty()) {
      elements.getLast().text.append(ch, start, length);
    }
  }

  /**
   * Remove empty nodes.
   *
   * @param node Vector
   */
  protected void eliminateInnerNodes(Vector<Object> node) {
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
  @Override
  public void endElement(String uri, String localName, String name) throws SAXException {
    final Element element = elements.removeLast();
    final String field = element.name;
    final String text = element.text.toString().trim();
    if (field.equals("layer")) {
      final Object child = currPath.remove(currPath.size() - 1);
      currPath.lastElement().add(child);
    } else if (pathEndsWith("layer") && currPath.size() > 1) {
      final WmsLayerInfo layer = currentLayer();
      switch (field) {
        case "srs":
        case "crs":
          layer.setField("srs", text);
          break;
        case "title":
        case "name":
        case "abstract":
          layer.setField(field, text.isEmpty() ? null : text);
          break;
        case "ex_geographicboundingbox":
          setBounds(layer, element.bounds);
          break;
        default:
          break;
      }
    } else if (!elements.isEmpty() && elements.getLast().bounds != null) {
      final String[] bounds = elements.getLast().bounds;
      switch (field) {
        case "westboundlongitude":
          bounds[0] = text;
          break;
        case "southboundlatitude":
          bounds[1] = text;
          break;
        case "eastboundlongitude":
          bounds[2] = text;
          break;
        case "northboundlatitude":
          bounds[3] = text;
          break;
        default:
          break;
      }
    } else if (field.equals("format") && pathEndsWith("getmap") && !text.isEmpty()) {
      server.addGetMapFormat(text);
    } else if (field.equals("title") && pathEndsWith("service")) {
      server.setServerName(text);
    }
  }

  private WmsLayerInfo currentLayer() {
    return ((CapabilitiesInnerTreeNode) currPath.lastElement()).getLayerInformation();
  }

  private boolean pathEndsWith(String... names) {
    final Iterator<Element> path = elements.descendingIterator();
    for (int i = names.length - 1; i >= 0; i--) {
      if (!path.hasNext() || !path.next().name.equals(names[i])) {
        return false;
      }
    }
    return true;
  }

  private static void setBounds(WmsLayerInfo layer, String[] bounds) throws SAXException {
    final float[] values = new float[4];
    for (int i = 0; i < values.length; i++) {
      try {
        if (bounds[i] == null) {
          throw new NumberFormatException("Missing coordinate");
        }
        values[i] = Float.parseFloat(bounds[i]);
        if (!Float.isFinite(values[i])) {
          throw new NumberFormatException("Nonfinite coordinate");
        }
      } catch (NumberFormatException e) {
        throw new SAXException("Invalid WMS geographic bounding box", e);
      }
    }
    layer.setLatLonBoundingBox(values[0], values[1], values[2], values[3]);
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
    elements.clear();
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
  public void startElement(String uri, String localName, String name, Attributes attributes)
      throws SAXException {
    String field = localName.isEmpty() ? name : localName;
    field = field.toLowerCase(Locale.ROOT);
    if (!uri.isEmpty() && !uri.equals("http://www.opengis.net/wms")) {
      field = "";
    }

    if (field.equals("layer")) {
      if (elements.isEmpty() || pathEndsWith("layer") || pathEndsWith("capability")) {
        final WmsLayerInfo layer = new WmsLayerInfo(server);
        layer.setField(
            "queryable",
            attributes.getValue("queryable") == null ? "0" : attributes.getValue("queryable"));
        if (currPath.size() > 1) {
          layer.setParentLayer(currentLayer());
        }
        currPath.add(new CapabilitiesInnerTreeNode(layer));
      } else {
        field = "";
      }
    } else if (field.equals("latlonboundingbox") && pathEndsWith("layer")) {
      setBounds(
          currentLayer(),
          new String[] {
            attributes.getValue("minx"),
            attributes.getValue("miny"),
            attributes.getValue("maxx"),
            attributes.getValue("maxy")
          });
    } else if (field.equals("onlineresource") && pathEndsWith("getmap", "dcptype", "http", "get")) {
      String url = attributes.getValue("http://www.w3.org/1999/xlink", "href");
      if (url == null) {
        url = attributes.getValue("xlink:href");
      }
      setGetMapUrl(url);
    } else if (field.equals("get") && pathEndsWith("map", "dcptype", "http")) {
      setGetMapUrl(attributes.getValue("onlineResource"));
    } else if (pathEndsWith("map", "format")) {
      switch (field) {
        case "jpeg":
          server.addGetMapFormat("image/jpeg");
          break;
        case "png":
          server.addGetMapFormat("image/png");
          break;
        case "gif":
          server.addGetMapFormat("image/gif");
          break;
        default:
          break;
      }
    }
    elements.addLast(new Element(field));
  }

  private void setGetMapUrl(String url) {
    if (server.getGetMapUrl() == null && url != null && !url.isBlank()) {
      server.setGetMapUrl(url.trim());
    }
  }
}
