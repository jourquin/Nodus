/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.bbn.openmap.tools.drawing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.omGraphics.EditableOMPoint;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.event.EOMGEvent;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises the drawing-completion event that chooses between editing and adding a node. */
@ResourceLock("JDBCUtils")
class NodusDrawingToolSelectionTest {
  @TempDir Path directory;

  @Test
  void ordinaryClickOffersLinkEditingEvenAfterAMissedModifierRelease() throws Exception {
    try (NetworkTestProject project = project()) {
      // A shortcut opens a dialog, which receives the key release instead of the map panel.
      Field control = NodusMapPanel.class.getDeclaredField("controlPressed");
      control.setAccessible(true);
      control.setBoolean(project.panel, true);
      TestTool tool = new TestTool(project);
      tool.click(0, 0, 0);
      assertSame(project.links.getEsriGraphicList().getOMGraphicAt(0), tool.offered);
      assertTrue(tool.offered.isSelected());
      assertEquals(2, project.nodes.getModel().getRowCount());
    }
  }

  @Test
  void controlClickBypassesLinksOnlyForThatClick() throws Exception {
    assertModifierBypass(InputEvent.CTRL_DOWN_MASK);
  }

  @Test
  void commandClickBypassesLinksOnlyForThatClick() throws Exception {
    assertModifierBypass(InputEvent.META_DOWN_MASK);
  }

  private void assertModifierBypass(int modifiers) throws Exception {
    try (NetworkTestProject project = project()) {
      assertFalse(project.panel.isControlPressed());
      // The mouse event remains authoritative even if the map missed the key press as well.
      project.nodes.acceptNode = false;
      TestTool tool = new TestTool(project);
      tool.click(0, 0, modifiers);
      assertNull(tool.offered);
      tool.click(0, 0, 0);
      assertSame(project.links.getEsriGraphicList().getOMGraphicAt(0), tool.offered);
    }
  }

  @Test
  void endpointClickOffersTheNodeBeforeItsLink() throws Exception {
    try (NetworkTestProject project = project()) {
      TestTool tool = new TestTool(project);
      tool.click(0, -1, 0);
      assertSame(project.nodes.getEsriGraphicList().getOMGraphicAt(0), tool.offered);
      tool.click(0, -1, InputEvent.CTRL_DOWN_MASK);
      assertSame(project.nodes.getEsriGraphicList().getOMGraphicAt(0), tool.offered);
    }
  }

  @Test
  void emptySpaceStillAddsANode() throws Exception {
    try (NetworkTestProject project = project()) {
      TestTool tool = new TestTool(project);
      tool.click(.5, 0, 0);
      assertNull(tool.offered);
      assertEquals(3, project.nodes.getModel().getRowCount());
    }
  }

  @Test
  void hiddenLinkLayersAreNotOfferedForEditing() throws Exception {
    try (NetworkTestProject project = project()) {
      project.nodes.acceptNode = false;
      TestTool tool = new TestTool(project);
      project.links.setVisible(false);
      tool.click(0, 0, 0);
      assertNull(tool.offered);
      project.links.setVisible(true);
      tool.click(0, 0, 0);
      assertSame(project.links.getEsriGraphicList().getOMGraphicAt(0), tool.offered);
    }
  }

  @Test
  void highlightedServiceRouteStillOffersLinkEditing() throws Exception {
    try (NetworkTestProject project = project()) {
      OMGraphic link = project.links.getEsriGraphicList().getOMGraphicAt(0);
      TransportService service = new TransportService(1);
      service.addChunk(link);
      Field currentService = ServiceHandler.class.getDeclaredField("currentService");
      currentService.setAccessible(true);
      currentService.set(project.getServiceHandler(), service);
      Graphics2D graphics =
          new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB).createGraphics();
      try {
        project.getServiceHandler()
            .renderCurrentServiceOverlay(graphics, project.links.getVisibleEsriGraphicList());
      } finally {
        graphics.dispose();
      }
      TestTool tool = new TestTool(project);
      tool.click(0, 0, 0);
      assertSame(link, tool.offered);
      assertEquals(2, project.nodes.getModel().getRowCount());
      assertSame(link, service.getLinks().getFirst());
    }
  }

  private NetworkTestProject project() throws Exception {
    NetworkTestProject project =
        new NetworkTestProject(directory, new double[] {0, -1, 0, -.5, 0, .5, 0, 1});
    assertNotNull(project.nodes.prepare());
    assertNotNull(project.links.prepare());
    return project;
  }

  /** Keeps actual selection and node insertion, replacing only dialogs and launcher cleanup. */
  private static final class TestTool extends NodusOMDrawingTool {
    private static final long serialVersionUID = 1L;
    private final NetworkTestProject project;
    OMGraphic offered;

    TestTool(NetworkTestProject project) {
      super(project.panel, null);
      this.project = project;
      setProjection(project.panel.map.getProjection());
      setNodusLayers(project.getNodeLayers(), project.getLinkLayers());
      setBehaviorMask(DEACTIVATE_ASAP_BEHAVIOR_MASK);
    }

    void click(double latitude, double longitude, int modifiers) {
      offered = null;
      currentEditable = new EditableOMPoint(new OMPoint(latitude, longitude));
      Point2D screen = getProjection().forward(latitude, longitude);
      MouseEvent mouse =
          new MouseEvent(
              project.panel.map,
              MouseEvent.MOUSE_RELEASED,
              0,
              modifiers,
              (int) screen.getX(),
              (int) screen.getY(),
              1,
              false,
              MouseEvent.BUTTON1);
      eomgChanged(new EOMGEvent(currentEditable, null, null, mouse, EOMGEvent.EOMG_COMPLETE));
    }

    @Override
    protected byte getEditAction(OMGraphic graphic) {
      offered = graphic;
      return 0;
    }

    @Override
    public synchronized void cancel() {
      currentEditable = null;
    }
  }
}
