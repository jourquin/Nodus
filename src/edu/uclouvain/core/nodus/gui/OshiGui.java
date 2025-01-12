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

package edu.uclouvain.core.nodus.gui;

import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JMenuBar;
import oshi.SystemInfo;
import oshi.demo.gui.Config;
import oshi.demo.gui.FileStorePanel;
import oshi.demo.gui.InterfacePanel;
import oshi.demo.gui.MemoryPanel;
import oshi.demo.gui.OsHwTextPanel;
import oshi.demo.gui.OshiJPanel;
import oshi.demo.gui.ProcessPanel;
import oshi.demo.gui.ProcessorPanel;
import oshi.demo.gui.UsbPanel;

/**
 * Basic Swing class to demonstrate potential uses for OSHI in a monitoring GUI. Not ready for
 * production use and intended as inspiration/examples. Based on the code provided in oshi-demo
 */
public class OshiGui extends EscapeDialog {

  /** . */
  private static final long serialVersionUID = 1529204256236867382L;

  /** . */
  private JButton hwAndOsPaneButton;

  /** . */
  private SystemInfo si = new SystemInfo();

  /** Initialize the hardware text info panel. */
  private OsHwTextPanel osHwTextPanel = new OsHwTextPanel(si);

  /** Initialize the RAM panel. */
  private MemoryPanel memoryPanel = new MemoryPanel(si);

  /** Initialize the CPU panel. */
  private ProcessorPanel processorPanel = new ProcessorPanel(si);

  /** Initialize the file system panel. */
  private FileStorePanel fileStorePanel = new FileStorePanel(si);

  /** Initialize the processes panel. */
  private ProcessPanel processPanel = new ProcessPanel(si);

  /** Initialize the USB panel. */
  private UsbPanel usbPanel = new UsbPanel(si);

  /** Initialize the network interfaces panel. */
  private InterfacePanel interfacePanel = new InterfacePanel(si);

  /** Creates a dialog containing several panes with hardware and OS informations. */
  public OshiGui() {
    super((Frame) null, "OSHI monitor", false);
    init();

    displayHwTextInfo();
  }

  private void displayHwTextInfo() {
    hwAndOsPaneButton.doClick();
  }

  private void init() {
    setSize(Config.GUI_WIDTH, Config.GUI_HEIGHT);
    setResizable(true);
    setLocationByPlatform(true);
    setLayout(new BorderLayout());
    // Add a menu bar
    JMenuBar menuBar = new JMenuBar();

    // Assign the first menu option to be clicked on visibility
    hwAndOsPaneButton = getPanelButton("OS & HW Info", 'O', "Hardware & OS Summary", osHwTextPanel);
    menuBar.add(hwAndOsPaneButton);
    // Add later menu items
    menuBar.add(getPanelButton("Memory", 'M', "Memory Summary", memoryPanel));
    menuBar.add(getPanelButton("CPU", 'C', "CPU Usage", processorPanel));
    menuBar.add(getPanelButton("FileStores", 'F', "FileStore Usage", fileStorePanel));
    menuBar.add(getPanelButton("Processes", 'P', "Processes", processPanel));
    menuBar.add(getPanelButton("USB Devices", 'U', "USB Device list", usbPanel));
    menuBar.add(getPanelButton("Network", 'N', "Network Params and Interfaces", interfacePanel));

    setJMenuBar(menuBar);
    setVisible(true);
  }

  private JButton getPanelButton(String title, char mnemonic, String toolTip, OshiJPanel panel) {
    JButton button = new JButton(title);
    button.setMnemonic(mnemonic);
    button.setToolTipText(toolTip);
    button.addActionListener(
        e -> {
          Container contentPane = getContentPane();
          if (contentPane.getComponents().length <= 0 || contentPane.getComponent(0) != panel) {
            resetMainGui();
            getContentPane().add(panel);
            refreshMainGui();
          }
        });

    return button;
  }

  private void resetMainGui() {
    getContentPane().removeAll();
  }

  private void refreshMainGui() {
    revalidate();
    repaint();
  }
}
