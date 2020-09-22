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

import com.sun.xml.bind.v2.schemagen.xmlschema.List;

import java.awt.Frame;
import java.util.Iterator;

import javax.swing.JFrame;

import org.uclouvain.gtm.util.gui.JResourcesMonitor;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.Display;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.EdidUtil;
import oshi.util.FormatUtil;

/**
 * Some utilities to gather hardware information.
 *
 * @author Bart Jourquin
 */
public class HardwareUtils {

  static SystemInfo si = new SystemInfo();

  /** Display the resources monitor. */
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

    HardwareAbstractionLayer hal = si.getHardware();
    CentralProcessor cpu = hal.getProcessor();

    return cpu.getLogicalProcessorCount();
  }

  public static String getOsInfo() {
    StringBuilder sb = new StringBuilder();

    OperatingSystem os = si.getOperatingSystem();

    return os.toString();
  }

  public static String getComputerInfo() {

    ComputerSystem computerSystem = si.getHardware().getComputerSystem();

    return computerSystem.getManufacturer() + " " + computerSystem.getModel();
  }

  public static String getProcessorInfo() {
    StringBuilder sb = new StringBuilder();
    CentralProcessor proc = si.getHardware().getProcessor();
    sb.append(proc.toString());

    return sb.toString();
  }

  public static String getDisplayInfo() {
    StringBuilder sb = new StringBuilder();
    java.util.List<Display> displays = si.getHardware().getDisplays();
    if (displays.isEmpty()) {
      sb.append("None detected.");
    } else {
      int i = 0;
      for (Display display : displays) {
        byte[] edid = display.getEdid();
        byte[][] desc = EdidUtil.getDescriptors(edid);
        String name = "Display " + i;
        for (byte[] b : desc) {
          if (EdidUtil.getDescriptorType(b) == 0xfc) {
            name = EdidUtil.getDescriptorText(b);
          }
        }
        if (i++ > 0) {
          sb.append('\n');
        }
        sb.append(name).append(": ");
        int hSize = EdidUtil.getHcm(edid);
        int vSize = EdidUtil.getVcm(edid);
        sb.append(
            String.format("%d x %d cm (%.1f x %.1f in)", hSize, vSize, hSize / 2.54, vSize / 2.54));
      }
    }
    return sb.toString();
  }

  public static String getGraphicsCardInfo() {

    java.util.List<GraphicsCard> graphicsCards = si.getHardware().getGraphicsCards();
    Iterator<GraphicsCard> it = graphicsCards.iterator();
    String s = "";
    while (it.hasNext()) {
      GraphicsCard gc = it.next();
      s += gc.getVendor() + " ";
      s += gc.getName();
      if (it.hasNext()) {
    	  s += System.lineSeparator();
      }
    }

    return s;
  }

  public static String getTotalMemoryInfo() {
    GlobalMemory memory = si.getHardware().getMemory();

    return FormatUtil.formatBytes(memory.getTotal());
  }

  public static String getAvailableMemoryInfo() {
    GlobalMemory memory = si.getHardware().getMemory();

    return FormatUtil.formatBytes(memory.getAvailable());
  }

  public static String getFileSystemInfo() {

    FileSystem fileSystem = si.getOperatingSystem().getFileSystem();
    java.util.List<OSFileStore> fileStores = fileSystem.getFileStores();

    for (OSFileStore store : fileStores) {
      System.out.println(store.getName());

      System.out.println(store.getType());

      long usable = store.getUsableSpace();
      long total = store.getTotalSpace();

      long usablePct = Math.round(((double) usable / (double) total) * 100);

      // System.out.println(usablePct + "/" + total);

      System.out.println();
    }

    return "";
  }
}
