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

package edu.uclouvain.core.nodus.utils;

import java.awt.Frame;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.JFrame;
import org.uclouvain.gtm.util.gui.JResourcesMonitor;

/**
 * Some utilities to gather system information with the Java runtime API.
 *
 * @author Bart Jourquin
 */
public class HardwareUtils {

  private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();

  /** Default constructor. */
  public HardwareUtils() {}

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
   * Return the number of logical cores of the computer.
   *
   * @return The number of logical cores.
   */
  public static int getNbCores() {
    return Math.max(1, OS_BEAN.getAvailableProcessors());
  }

  /**
   * Returns the name and version of the OS.
   *
   * @return Name and version of the OS.
   */
  public static String getOsInfo() {
    return OS_BEAN.getName() + " " + OS_BEAN.getVersion() + " (" + OS_BEAN.getArch() + ")";
  }

  /**
   * Returns the host name of the computer.
   *
   * @return Host name of the computer system.
   */
  public static String getComputerInfo() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return System.getProperty("user.name", "Unknown host");
    }
  }

  /**
   * Returns a description of the CPU information available through the Java API.
   *
   * @return Description of the available CPU information.
   */
  public static String getProcessorInfo() {
    return "Architecture: "
        + OS_BEAN.getArch()
        + System.lineSeparator()
        + "Logical processors: "
        + getNbCores();
  }

  /**
   * Returns a description of the computer system display(s), when available.
   *
   * @return Description of the display(s).
   */
  public static String getDisplayInfo() {
    return "Not available with the Java API.";
  }

  /**
   * Returns the description of installed graphics card(s), when available.
   *
   * @return Description of the graphics card(s).
   */
  public static String getGraphicsCardInfo() {
    return "Not available with the Java API.";
  }

  /**
   * Returns the maximum amount of memory the JVM will attempt to use.
   *
   * @return The maximum JVM heap size as a long integer.
   */
  public static long getTotalMemory() {
    return Runtime.getRuntime().maxMemory();
  }

  /**
   * Returns the maximum amount of memory the JVM will attempt to use.
   *
   * @return The maximum JVM heap size with the appropriate unit suffix.
   */
  public static String getTotalMemoryInfo() {
    return formatBytes(getTotalMemory());
  }

  /**
   * Returns the amount of JVM heap still available before reaching the maximum heap size.
   *
   * @return The available JVM heap size with the appropriate unit suffix.
   */
  public static String getAvailableMemoryInfo() {
    Runtime runtime = Runtime.getRuntime();
    long availableMemory = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
    return formatBytes(availableMemory);
  }

  private static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }

    String[] units = {"KB", "MB", "GB", "TB"};
    double value = bytes;
    int unitIndex = -1;
    do {
      value = value / 1024;
      unitIndex++;
    } while (value >= 1024 && unitIndex < units.length - 1);

    return String.format("%.1f %s", value, units[unitIndex]);
  }
}
