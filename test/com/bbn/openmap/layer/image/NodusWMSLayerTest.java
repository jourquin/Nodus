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

package com.bbn.openmap.layer.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.layer.image.capabilities.CapabilitiesInnerTreeNode;
import com.bbn.openmap.layer.image.capabilities.CapabilitiesTreeNode;
import com.bbn.openmap.layer.image.capabilities.ICapabilitiesNodeInterface;
import com.bbn.openmap.layer.image.capabilities.WmsCapabilitiesXmlParser;
import com.bbn.openmap.layer.image.capabilities.WmsLayerInfo;
import com.bbn.openmap.layer.image.capabilities.WmsServerInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Exercises the production WMS XML policy and checks that unsafe/partial trees never get cached.
 */
@Tag("integration")
@Timeout(10)
class NodusWMSLayerTest {
  private static final String MAP_URL = "https://maps.example.test/wms?token=a&mode=b";
  private static final String CANARY = "PRIVATE_WMS_TEST_CANARY";

  @TempDir Path directory;

  @Test
  void normalCapabilitiesRetainMetadataNestedLayersAndInheritedBounds() throws Exception {
    assertCapabilities(parse(document()));
  }

  @TestFactory
  List<DynamicTest> wmsDoctypesAreAcceptedWithoutReadingTheirDtds() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (int variant = 0; variant < 3; variant++) {
      final int style = variant;
      tests.add(
          dynamicTest(
              "doctype " + style,
              () -> {
                try (CanaryServer server = new CanaryServer()) {
                  final String externalId =
                      style == 2
                          ? "SYSTEM 'WMS_MS_Capabilities.dtd'"
                          : (style == 0 ? "SYSTEM '" : "PUBLIC '-//OGC//DTD WMS 1.1.1//EN' '")
                              + server.resourceUrl()
                              + "'";
                  assertCapabilities(
                      parse(withDoctype("<!DOCTYPE WMT_MS_Capabilities " + externalId + ">")));
                  assertEquals(0, server.resourceRequests.get());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> externalGeneralAndParameterEntitiesCannotReadFilesOrHttpResources() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean parameter : new boolean[] {false, true}) {
      for (boolean publicId : new boolean[] {false, true}) {
        for (boolean file : new boolean[] {false, true}) {
          tests.add(
              dynamicTest(
                  "parameter=" + parameter + "/public=" + publicId + "/file=" + file,
                  () -> {
                    try (CanaryServer server = new CanaryServer()) {
                      final String content =
                          parameter ? "<!ENTITY leaked '" + CANARY + "'>" : CANARY;
                      final Path sentinel = Files.createTempFile(directory, "wms-canary-", ".xml");
                      Files.writeString(sentinel, content);
                      server.resource.set(content);
                      final String uri =
                          file ? sentinel.toUri().toASCIIString() : server.resourceUrl();
                      final String declaration =
                          "<!DOCTYPE WMT_MS_Capabilities [<!ENTITY "
                              + (parameter ? "% external" : "external")
                              + (publicId ? " PUBLIC 'urn:nodus:test:entity' '" : " SYSTEM '")
                              + uri
                              + "'>"
                              + (parameter ? "%external;" : "")
                              + "]>";
                      final String xml =
                          withDoctype(declaration)
                              .replace(
                                  "Coastal &amp; river &#233; maps",
                                  parameter ? "&leaked;" : "&external;");
                      final SAXException failure =
                          assertThrows(SAXException.class, () -> parse(xml));
                      assertTrue(failure.getMessage().startsWith("External XML entities"));
                      assertEquals(0, server.resourceRequests.get());
                    }
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> customAndRecursiveEntitiesAreRejectedBeforeExpansion() {
    final List<DynamicTest> tests = new ArrayList<>();
    final String[] declarations = {
      "<!ENTITY custom '" + CANARY + "'>",
      "<!ENTITY custom '&custom;'>",
      "<!ENTITY a '"
          + CANARY
          + "'><!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>"
          + "<!ENTITY custom '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>",
      "<!ENTITY % custom '<!ELEMENT Vendor ANY>'>%custom;"
    };
    for (int index = 0; index < declarations.length; index++) {
      final String declaration = declarations[index];
      tests.add(
          dynamicTest(
              "entity declaration " + index,
              () -> {
                final String xml =
                    withDoctype("<!DOCTYPE WMT_MS_Capabilities [" + declaration + "]>");
                final SAXException failure = assertThrows(SAXException.class, () -> parse(xml));
                assertTrue(failure.getMessage().startsWith("Custom XML entities"));
              }));
    }
    return tests;
  }

  @Test
  void referencesFromAnUnreadDtdCannotSilentlyDropMetadata() throws Exception {
    try (CanaryServer server = new CanaryServer()) {
      final String xml =
          withDoctype("<!DOCTYPE WMT_MS_Capabilities SYSTEM '" + server.resourceUrl() + "'>")
              .replace("Coastal &amp; river &#233; maps", "&undefined;");
      assertThrows(SAXException.class, () -> parse(xml));
      assertEquals(0, server.resourceRequests.get());
    }
  }

  @TestFactory
  List<DynamicTest> schemaAndXincludeLocationsDoNotFetchExternalResources() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (int variant = 0; variant < 3; variant++) {
      final int style = variant;
      tests.add(
          dynamicTest(
              "external location " + style,
              () -> {
                try (CanaryServer server = new CanaryServer()) {
                  final String xml;
                  if (style == 0) {
                    xml =
                        document()
                            .replace(
                                "version=\"1.1.1\"",
                                "version=\"1.1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                                    + " xsi:noNamespaceSchemaLocation=\""
                                    + server.resourceUrl()
                                    + "\"");
                  } else {
                    xml =
                        document()
                            .replace(
                                "<Capability>",
                                "<Capability><xi:include xmlns:xi='http://www.w3.org/2001/XInclude' href='"
                                    + server.resourceUrl()
                                    + "' parse='"
                                    + (style == 1 ? "xml" : "text")
                                    + "'/>");
                  }
                  assertCapabilities(parse(xml));
                  assertEquals(0, server.resourceRequests.get());
                }
              }));
    }
    return tests;
  }

  @Test
  void splitCharacterCallbacksRespectOffsetsAndPreserveServerAndLayerTitles() throws Exception {
    final WmsCapabilitiesXmlParser handler = new WmsCapabilitiesXmlParser();
    final AttributesImpl attributes = new AttributesImpl();
    handler.startDocument();
    handler.startElement("", "Service", "Service", attributes);
    handler.startElement("", "Title", "Title", attributes);
    characters(handler, "Coastal ");
    characters(handler, "& river é maps");
    handler.endElement("", "Title", "Title");
    handler.endElement("", "Service", "Service");
    handler.startElement("", "Layer", "Layer", attributes);
    handler.startElement("", "Title", "Title", attributes);
    characters(handler, "Roads ");
    characters(handler, "& bridges");
    handler.endElement("", "Title", "Title");
    handler.endElement("", "Layer", "Layer");
    handler.endDocument();
    final WmsLayerInfo layer =
        ((ICapabilitiesNodeInterface) handler.getDataTree().firstElement()).getLayerInformation();
    assertEquals("Roads & bridges", layer.getField("title"));
    assertEquals("Coastal & river é maps", layer.getServerInformation().getServerName());
  }

  @Test
  void layerUsesTheSecureParserAndCachesOnlySuccessfulCapabilities() throws Exception {
    try (CanaryServer server = new CanaryServer()) {
      server.capabilities.set(
          withDoctype("<!DOCTYPE WMT_MS_Capabilities SYSTEM '" + server.resourceUrl() + "'>"));
      final TestLayer layer = new TestLayer(server.capabilitiesUrl());
      assertTrue(layer.getCapabilities());
      assertEquals(1, server.capabilitiesRequests.get());
      assertEquals(0, server.resourceRequests.get());
      assertEquals(MAP_URL, layer.mapUrl());
      assertCapabilities(layer.getTreeData());
      final Vector<Object> tree = layer.getTreeData();
      server.capabilities.set("not XML");
      assertTrue(layer.getCapabilities());
      assertSame(tree, layer.getTreeData());
      assertEquals(1, server.capabilitiesRequests.get());
      assertTrue(layer.errors.isEmpty());
    }
  }

  @TestFactory
  List<DynamicTest> unsafeOrLateMalformedCapabilitiesAreNotPublishedAndCanBeRetried() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean unsafe : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "unsafe=" + unsafe,
              () -> {
                try (CanaryServer server = new CanaryServer()) {
                  server.capabilities.set(
                      unsafe
                          ? withDoctype(
                              "<!DOCTYPE WMT_MS_Capabilities [<!ENTITY external SYSTEM '"
                                  + server.resourceUrl()
                                  + "'>]>")
                          : document().replace("</WMT_MS_Capabilities>", "<unfinished>"));
                  final TestLayer layer = new TestLayer(server.capabilitiesUrl());
                  assertFalse(layer.getCapabilities());
                  assertNull(layer.getTreeData());
                  assertEquals(1, layer.errors.size());
                  assertEquals(0, server.resourceRequests.get());
                  server.capabilities.set(document());
                  assertTrue(layer.getCapabilities());
                  assertCapabilities(layer.getTreeData());
                  assertEquals(2, server.capabilitiesRequests.get());
                  assertEquals(1, layer.errors.size());
                }
              }));
    }
    return tests;
  }

  @Test
  void singleNamedLayerIsAlsoAValidCapabilitiesTree() throws Exception {
    try (CanaryServer server = new CanaryServer()) {
      final String xml = document();
      server.capabilities.set(
          xml.substring(0, xml.indexOf("      <Layer queryable=\"0\">"))
                  .replace(
                      "<Title>All layers</Title>", "<Name>only</Name><Title>Only layer</Title>")
              + xml.substring(xml.lastIndexOf("    </Layer>")));
      final TestLayer layer = new TestLayer(server.capabilitiesUrl());
      assertTrue(layer.getCapabilities());
      final CapabilitiesTreeNode node =
          assertInstanceOf(CapabilitiesTreeNode.class, layer.getTreeData().firstElement());
      assertEquals("only", node.getLayerInformation().getField("name"));
      assertEquals(MAP_URL, layer.mapUrl());
    }
  }

  private static String document() throws IOException {
    return Files.readString(Path.of("test/fixtures/wms/capabilities-1.1.1.xml"));
  }

  private static String withDoctype(String doctype) throws IOException {
    return document().replace("?>", "?>\n" + doctype);
  }

  private static Vector<?> parse(String xml) throws Exception {
    try (ByteArrayInputStream input =
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      return WmsCapabilitiesXmlParser.parse(input).getDataTree();
    }
  }

  private static void characters(WmsCapabilitiesXmlParser handler, String text) {
    handler.characters(("xx" + text + "yy").toCharArray(), 2, text.length());
  }

  private static void assertCapabilities(Vector<?> tree) {
    assertEquals(1, tree.size());
    final CapabilitiesInnerTreeNode root =
        assertInstanceOf(CapabilitiesInnerTreeNode.class, tree.firstElement());
    final WmsLayerInfo rootInfo = root.getLayerInformation();
    final WmsServerInfo server = rootInfo.getServerInformation();
    assertEquals("Coastal & river é maps", server.getServerName());
    assertEquals(MAP_URL, server.getGetMapUrl());
    assertEquals(List.of("image/png", "image/jpeg"), server.getGetMapFormats());
    assertEquals("All layers", rootInfo.getField("title"));
    assertEquals("EPSG:4326", rootInfo.getField("srs"));
    assertEquals("1", rootInfo.getField("queryable"));
    assertEquals(3, root.size());
    final WmsLayerInfo roads = ((ICapabilitiesNodeInterface) root.get(0)).getLayerInformation();
    assertEquals("roads", roads.getField("name"));
    assertEquals("Roads & bridges", roads.getField("title"));
    assertEquals("Transport links", roads.getField("abstract"));
    assertEquals("0", roads.getField("queryable"));
    assertSame(rootInfo, roads.getParentLayer());
    assertSame(server, roads.getServerInformation());
    assertArrayEquals(new float[] {-180, -90, 180, 90}, roads.getLatLonBoundingBox());
    final CapabilitiesInnerTreeNode regional =
        assertInstanceOf(CapabilitiesInnerTreeNode.class, root.get(1));
    assertEquals("Regional layers", regional.getLayerInformation().getField("title"));
    assertEquals(1, regional.size());
    final WmsLayerInfo ports =
        ((ICapabilitiesNodeInterface) regional.firstElement()).getLayerInformation();
    assertEquals("ports", ports.getField("name"));
    assertEquals("EPSG:3857", ports.getField("srs"));
    assertEquals("0", ports.getField("queryable"));
    assertSame(regional.getLayerInformation(), ports.getParentLayer());
    assertSame(server, ports.getServerInformation());
    assertArrayEquals(new float[] {2, 49, 6, 52}, ports.getLatLonBoundingBox());
    final WmsLayerInfo water = ((ICapabilitiesNodeInterface) root.get(2)).getLayerInformation();
    assertEquals("water", water.getField("name"));
    assertEquals("Water", water.getField("title"));
    assertSame(rootInfo, water.getParentLayer());
    assertArrayEquals(rootInfo.getLatLonBoundingBox(), water.getLatLonBoundingBox());
  }

  private static final class TestLayer extends NodusWMSLayer {
    private static final long serialVersionUID = 1L;
    final List<String> errors = new ArrayList<>();

    TestLayer(String url) {
      queryHeader = url;
    }

    String mapUrl() {
      return wmsServer;
    }

    @Override
    protected void reportCapabilitiesError(String message) {
      errors.add(message);
    }
  }

  private static final class CanaryServer implements AutoCloseable {
    final AtomicReference<String> capabilities = new AtomicReference<>();
    final AtomicReference<String> resource =
        new AtomicReference<>("<!ELEMENT WMT_MS_Capabilities ANY>");
    final AtomicInteger capabilitiesRequests = new AtomicInteger();
    final AtomicInteger resourceRequests = new AtomicInteger();
    final HttpServer server;

    CanaryServer() throws IOException {
      capabilities.set(document());
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/capabilities", exchange -> respond(exchange, capabilities, capabilitiesRequests));
      server.createContext("/resource", exchange -> respond(exchange, resource, resourceRequests));
      server.start();
    }

    String capabilitiesUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/capabilities";
    }

    String resourceUrl() {
      return "http://127.0.0.1:"
          + server.getAddress().getPort()
          + "/resource/WMS_MS_Capabilities.dtd";
    }

    private static void respond(
        HttpExchange exchange, AtomicReference<String> body, AtomicInteger requests)
        throws IOException {
      requests.incrementAndGet();
      final byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
      exchange.sendResponseHeaders(200, bytes.length);
      try {
        exchange.getResponseBody().write(bytes);
      } finally {
        exchange.close();
      }
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
