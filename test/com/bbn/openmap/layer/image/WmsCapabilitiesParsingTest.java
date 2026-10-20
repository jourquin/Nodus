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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.layer.image.capabilities.CapabilitiesInnerTreeNode;
import com.bbn.openmap.layer.image.capabilities.ICapabilitiesNodeInterface;
import com.bbn.openmap.layer.image.capabilities.WmsCapabilitiesXmlParser;
import com.bbn.openmap.layer.image.capabilities.WmsLayerInfo;
import com.bbn.openmap.layer.image.capabilities.WmsServerInfo;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.xml.sax.SAXException;

/** Checks capabilities semantics independently of HTTP, rendering and dialog construction. */
class WmsCapabilitiesParsingTest {
  @TestFactory
  List<DynamicTest> namespacesAndArbitraryXlinkPrefixesPreserveWms13Metadata() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String prefix : List.of("", "wms:", "maps:")) {
      for (String link : List.of("xlink", "link")) {
        tests.add(
            dynamicTest(
                prefix + "/" + link,
                () -> {
                  String xml =
                      document13()
                          .replace("xmlns:xlink", "xmlns:" + link)
                          .replace("xlink:href", link + ":href");
                  if (!prefix.isEmpty()) {
                    xml =
                        xml.replace(
                                "xmlns=", "xmlns:" + prefix.substring(0, prefix.length() - 1) + "=")
                            .replaceAll("(<[/]?)([A-Za-z])", "$1" + prefix + "$2");
                  }
                  final Vector<?> tree = parse(xml);
                  final CapabilitiesInnerTreeNode root =
                      assertInstanceOf(CapabilitiesInnerTreeNode.class, tree.firstElement());
                  final WmsLayerInfo info = root.getLayerInformation();
                  final WmsServerInfo server = info.getServerInformation();
                  assertEquals(1, tree.size());
                  assertEquals("Belgian maps", server.getServerName());
                  assertEquals(
                      "https://maps.example.test/get?token=a&mode=b", server.getGetMapUrl());
                  assertEquals(List.of("image/png", "image/jpeg"), server.getGetMapFormats());
                  assertEquals("Belgium", info.getField("title"));
                  assertEquals("EPSG:4326", info.getField("srs"));
                  assertArrayEquals(new float[] {2, 49, 7, 52}, info.getLatLonBoundingBox());
                  assertEquals(2, root.size());
                  final WmsLayerInfo roads = info(root.get(0));
                  assertEquals("roads", roads.getField("name"));
                  assertEquals("Roads & bridges", roads.getField("title"));
                  assertEquals("Transport links", roads.getField("abstract"));
                  assertSame(info, roads.getParentLayer());
                  assertSame(server, roads.getServerInformation());
                  assertArrayEquals(new float[] {2, 49, 7, 52}, roads.getLatLonBoundingBox());
                  final WmsLayerInfo ports = info(root.get(1));
                  assertEquals("ports", ports.getField("name"));
                  assertArrayEquals(new float[] {3, 50, 5, 51}, ports.getLatLonBoundingBox());
                }));
      }
    }
    return tests;
  }

  @Test
  void nestedAndForeignMetadataCannotReplaceLayerIdentityOrAddPhantomLayers() throws Exception {
    final String xml =
        document13()
            .replace(
                "<Abstract>Transport links</Abstract>",
                "<Abstract>Transport links</Abstract>"
                    + "<Attribution><Title>Owner</Title></Attribution>"
                    + "<Style><Name>default</Name><Title>Style</Title>"
                    + "<Abstract>Style text</Abstract></Style>"
                    + "<vendor:Title xmlns:vendor='urn:vendor'>Vendor title</vendor:Title>"
                    + "<vendor:Layer xmlns:vendor='urn:vendor'>"
                    + "<Name>phantom</Name></vendor:Layer>");
    final CapabilitiesInnerTreeNode root = (CapabilitiesInnerTreeNode) parse(xml).firstElement();
    assertEquals(2, root.size());
    final WmsLayerInfo roads = info(root.get(0));
    assertEquals("roads", roads.getField("name"));
    assertEquals("Roads & bridges", roads.getField("title"));
    assertEquals("Transport links", roads.getField("abstract"));
    assertNull(info(root.get(1)).getField("abstract"));
  }

  @Test
  void wms11SelectsFirstGetBindingAndIgnoresPostAndOtherOperations() throws Exception {
    final String xml =
        Files.readString(Path.of("test/fixtures/wms/capabilities-1.1.1.xml"))
            .replace(
                "</Get></HTTP>",
                "</Get><Post><OnlineResource xlink:href='https://post.test/'/>"
                    + "</Post><Get><OnlineResource xlink:href='https://second.test/'/></Get></HTTP>")
            .replace(
                "</Request>",
                "<GetFeatureInfo><Format>text/html</Format><DCPType><HTTP><Get>"
                    + "<OnlineResource xlink:href='https://info.test/'/></Get></HTTP></DCPType>"
                    + "</GetFeatureInfo></Request>");
    final WmsServerInfo server = info(parse(xml).firstElement()).getServerInformation();
    assertEquals("https://maps.example.test/wms?token=a&mode=b", server.getGetMapUrl());
    assertEquals(List.of("image/png", "image/jpeg"), server.getGetMapFormats());
  }

  @Test
  void legacyMapRequestsStillParseWithoutLeakingOtherFormats() throws Exception {
    final String xml =
        "<WMT_MS_Capabilities><Service><Title>Legacy</Title></Service>"
            + "<Capability><Request><Map><Format><PNG/><JPEG/><GIF/></Format>"
            + "<DCPType><HTTP><Get onlineResource='https://legacy.test/'/>"
            + "</HTTP></DCPType></Map></Request><Layer><Name>land</Name>"
            + "<LatLonBoundingBox minx='-180' miny='-90' maxx='180' maxy='90'/>"
            + "</Layer></Capability></WMT_MS_Capabilities>";
    final WmsLayerInfo layer = info(parse(xml).firstElement());
    assertEquals("land", layer.getField("name"));
    assertEquals("https://legacy.test/", layer.getServerInformation().getGetMapUrl());
    assertEquals(
        List.of("image/png", "image/jpeg", "image/gif"),
        layer.getServerInformation().getGetMapFormats());
    assertArrayEquals(new float[] {-180, -90, 180, 90}, layer.getLatLonBoundingBox());
  }

  @TestFactory
  List<DynamicTest> invalidGeographicBoundsProduceCheckedParsingFailures() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean modern : new boolean[] {false, true}) {
      for (String invalid : List.of("bad", "NaN", "Infinity", "missing")) {
        tests.add(
            dynamicTest(
                "modern=" + modern + "/" + invalid,
                () -> {
                  final String xml;
                  if (modern) {
                    xml =
                        document13()
                            .replace(
                                "<westBoundLongitude>2</westBoundLongitude>",
                                invalid.equals("missing")
                                    ? ""
                                    : "<westBoundLongitude>" + invalid + "</westBoundLongitude>");
                  } else {
                    xml =
                        "<WMT_MS_Capabilities><Capability><Layer><Name>land</Name>"
                            + "<LatLonBoundingBox "
                            + (invalid.equals("missing") ? "" : "minx='" + invalid + "' ")
                            + "miny='49' maxx='7' maxy='52'/></Layer>"
                            + "</Capability></WMT_MS_Capabilities>";
                  }
                  assertThrows(SAXException.class, () -> parse(xml));
                }));
      }
    }
    return tests;
  }

  private static WmsLayerInfo info(Object node) {
    return ((ICapabilitiesNodeInterface) node).getLayerInformation();
  }

  private static String document13() throws Exception {
    return Files.readString(Path.of("test/fixtures/wms/capabilities-1.3.0.xml"));
  }

  private static Vector<?> parse(String xml) throws Exception {
    try (ByteArrayInputStream input =
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      return WmsCapabilitiesXmlParser.parse(input).getDataTree();
    }
  }
}
