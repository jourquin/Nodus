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

package edu.uclouvain.core.nodus.utils;

import java.awt.Frame;
import javax.swing.JFrame;
import org.uclouvain.gtm.util.gui.JResourcesMonitor;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Some utilities to gather hardware information.
 *
 * @author Bart Jourquin
 */
public class HardwareUtils {

  /** Display the reources monitor. */
  public static void displayRessourcesMonitor() {
    // Only create a instance if none exists
    Frame[] frames = Frame.getFrames();

    for (Frame element : frames) {
      if (element instanceof JFrame) {
        JFrame f = (JFrame) element;
        if (f.getClass().toString().endsWith("ResourcesMonitor") && f.isVisible()) {
          return;
        }
      }
    }

    JResourcesMonitor mm = new JResourcesMonitor();
    mm.setVisible(true);
  }

  /**
   * Return the number of physical cores of the computer.
   *
   * @return The number of physical cores.
   */
  public static int getNbPhysicalCores() {
    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    CentralProcessor cpu = hal.getProcessor();
    return cpu.getPhysicalProcessorCount();
  }

  /**
   * Return the number of logical cores of the computer.
   *
   * @return The number of logical cores.
   */
  public static int getNbLogicalCores() {
    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    CentralProcessor cpu = hal.getProcessor();

    return cpu.getLogicalProcessorCount();
  }
}
