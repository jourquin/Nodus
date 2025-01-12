/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package com.bbn.openmap.tools.drawing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphicConstants;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PaletteHelper;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.swing.VerticalFlowLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Iterator;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * The NodusDrawingToolLauncher extends the OMDrawingToolLauncher to better fit the needs of Nodus:
 * there is only the choice between points and polylines, and the "create" button is replaced by a
 * "create/move/delete" button.
 *
 * @author Bart Jourquin
 */
public class NodusOMDrawingToolLauncher extends OMDrawingToolLauncher
    implements OMGraphicConstants {
  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 1550789873018047426L;

  /** . */
  private JComboBox<JComponent> nodusDrawingTool;

  /** . */
  private NodusMapPanel nodusMapPanel;

  /**
   * Constructor.
   *
   * @param nodusMapPanel The NodusMapPanel this drawing tool launcher is attached to.
   */
  public NodusOMDrawingToolLauncher(NodusMapPanel nodusMapPanel) {
    super();
    this.nodusMapPanel = nodusMapPanel;
  }

  /**
   * Intercepts the original getGUI() method to let the choice between points and polylines, and
   * replaces the "create" button by a "create/move/delete" button.
   */
  @SuppressWarnings("unchecked")
  @Override
  public void resetGUI() {
    removeAll();
    getWindowSupport()
        .setTitle(i18n.get(NodusOMDrawingToolLauncher.class, "Map_editor", "Map editor"));
    VerticalFlowLayout vfl = new VerticalFlowLayout();
    setLayout(vfl);
    panel3 = new JPanel(); // needed by super class for the drawing attributes

    // Add requestors
    JPanel requestorsPanel = PaletteHelper.createPaletteJPanel("");
    requestorsPanel.setBorder(new EmptyBorder(5, 5, 0, 5));

    String[] requestorNames = new String[drawingToolRequestors.size()];
    for (int i = 0; i < requestorNames.length; i++) {
      requestorNames[i] = drawingToolRequestors.elementAt(i).getName();
    }
    requestors = new JComboBox<String>(requestorNames);
    requestors.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JComboBox<?> jcb = (JComboBox<?>) e.getSource();
            String currentChoice = (String) jcb.getSelectedItem();
            setCurrentRequestor(currentChoice);
          }
        });

    nodusDrawingTool = (JComboBox<JComponent>) getToolWidgets(true);
    requestorsPanel.add(nodusDrawingTool);

    // Buttons
    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    buttonsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    buttonsPanel.setAlignmentY(Component.BOTTOM_ALIGNMENT);

    // Replace original button witha Add-Change button
    createButton =
        new JButton(i18n.get(NodusOMDrawingToolLauncher.class, "Add_Change", "Add - Change"));
    createButton.setActionCommand(CreateCmd);
    createButton.addActionListener(this);

    // Close button
    JButton close = new JButton(i18n.get(OMDrawingToolLauncher.class, "dismiss", "Close"));
    close.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancel();
          }
        });

    buttonsPanel.add(createButton);
    buttonsPanel.add(close);

    // Add panels
    add(requestorsPanel);
    add(buttonsPanel);

    // Editing the network is not allowed when results are displayed or if no project is open.
    windowSupport.addComponentListener(
        new ComponentListener() {

          @Override
          public void componentShown(ComponentEvent e) {

            if (areLayersEditable()) {
              createButton.setEnabled(true);
            } else {
              createButton.setEnabled(false);
            }
          }

          @Override
          public void componentResized(ComponentEvent e) {
            // Nothing to do
          }

          @Override
          public void componentMoved(ComponentEvent e) {
            // Nothing to do
          }

          @Override
          public void componentHidden(ComponentEvent e) {
            // Nothing to do
          }
        });
  }

  /** Cancel edit operation and close window. */
  public void cancel() {
    if (drawingTool != null) {
      ((NodusOMDrawingTool) drawingTool).cancel();
      getWindowSupport().killWindow();
    }
  }

  /**
   * Returns true if results are currently displayed on the map.
   *
   * @return True if at least one layer currently displays results.
   */
  private boolean areLayersEditable() {
    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    if (nodusProject.isOpen()) {
      NodusEsriLayer[] layers = nodusProject.getLinkLayers();
      for (int i = 0; i < layers.length; i++) {
        if (layers[i].isDisplayResults()) {
          return false;
        }
      }

      layers = nodusProject.getNodeLayers();
      for (int i = 0; i < layers.length; i++) {
        if (layers[i].isDisplayResults()) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  /**
   * Tool interface method. The retrieval tool's interface. This method creates a button that will
   * bring up the LauncherPanel.
   *
   * @return String The key for this tool.
   */
  @Override
  public Container getFace() {
    JToolBar jtb = null;
    if (getUseAsTool()) {
      jtb = new com.bbn.openmap.gui.GridBagToolBar();
      // "Drawing Tool Launcher";
      JButton drawingToolButton =
          new JButton(
              new ImageIcon(
                  OMDrawingToolLauncher.class.getResource("Drawing.gif"),
                  i18n.get(
                      OMDrawingToolLauncher.class,
                      "drawingToolButton",
                      I18n.TOOLTIP,
                      "Drawing Tool Launcher")));
      drawingToolButton.setToolTipText(
          i18n.get(
              OMDrawingToolLauncher.class,
              "drawingToolButton",
              I18n.TOOLTIP,
              "Drawing Tool Launcher"));
      drawingToolButton.addActionListener(getActionListener());

      // Ugly trick, because the toggle-buttons for ht mouse modes don't have a border on Mac
      if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
        LookAndFeel lf = UIManager.getLookAndFeel();
        if (lf.isNativeLookAndFeel()) {
          drawingToolButton.setBorder(null);
        }
      }
      jtb.add(drawingToolButton);
    }
    return jtb;
  }

  /**
   * Selects the Link tool and performs a "create" action if asked.
   *
   * @param pressButton if true, the action will be performed.
   */
  public void selectLinkTool(boolean pressButton) {
    for (Iterator<?> it = getLoaders(); it.hasNext(); ) {
      LoaderHolder lh = (LoaderHolder) it.next();

      if (lh.loader instanceof NodusOMPolyLoader) {
        nodusDrawingTool.setSelectedItem(lh.prettyName);
        if (pressButton) {
          actionPerformed(new ActionEvent(createButton, 1, CreateCmd));
        }
        break;
      }
    }
  }

  /**
   * Selects the Node tool and performs a "create" action if asked.
   *
   * @param pressButton if true, the action will be performed.
   */
  public void selectNodeTool(boolean pressButton) {
    for (Iterator<?> it = getLoaders(); it.hasNext(); ) {
      LoaderHolder lh = (LoaderHolder) it.next();

      if (lh.loader instanceof NodusOMPointLoader) {
        nodusDrawingTool.setSelectedItem(lh.prettyName);
        if (pressButton) {
          actionPerformed(new ActionEvent(createButton, 1, CreateCmd));
        }
        break;
      }
    }
  }

  /**
   * Enables or not the control.
   *
   * @param enable Enables if true.
   */
  public void setBusy(boolean enable) {
    createButton.setEnabled(!enable);
    nodusDrawingTool.setEnabled(!enable);
  }
}
