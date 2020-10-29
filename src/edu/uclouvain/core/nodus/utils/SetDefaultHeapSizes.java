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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This utility is called in order to set reasonable values for the -Xmx and -Xms JVM parameters in
 * a "jvmargs.sh" or "jvmargs.bat" file if it doesn't exist yet.
 *
 * <p>The following rules are used:
 *
 * <p>- The minimum heap size is set (to 2Go) only if the computer has more than 4Go RAM.
 *
 * <p>- The maximum heap is set to 50% of the RAM, with a maximum of 6Go.
 *
 * @author Bart Jourquin
 */
public class SetDefaultHeapSizes {

  /**
   * Entry point.
   *
   * @param args Not used
   */
  public static void main(String[] args) {
    new SetDefaultHeapSizes();
  }

  /** Sets the default heap values in the launcher script. */
  public SetDefaultHeapSizes() {

    String envtFileName = getEnvFileName();

    // Do nothing if it already exists
    File file = new File(envtFileName);
    if (file.exists()) {
      return;
    }

    // Get the heap settings
    String heapSettings = getDefaultHeapSettings();

    // Update the script
    createScript(envtFileName, heapSettings);
  }

  /**
   * Returns a String containing the default heap settings for the available RAM in this computer.
   *
   * @return A String with the heap settings (-Xmx????m -Xms???m)
   */
  private String getDefaultHeapSettings() {
    long mb = 1024 * 1024;
    long gb = 1024 * mb;

    // Get architecture
    int arch = Integer.parseInt(System.getProperty("sun.arch.data.model"));

    // Get total memory
    long memorySize = HardwareUtils.getTotalMemory();
    
    // Define the default max heap size
    long maxHeap = memorySize / 2;
    if (maxHeap > 6 * gb) {
      maxHeap = 6 * gb;
    }

    if (arch == 32 && maxHeap > 1.4 * gb) {
      maxHeap = Math.round(1.4 * gb);
    }

    String heapSettings = "-Xmx" + maxHeap / mb + "m";

    if (memorySize / gb >= 4 && arch != 32) {
      heapSettings += " -Xms" + 2 * gb / mb + "m";
    }

    return heapSettings;
  }

  /**
   * Returns the name of the file in which the JVMARGS environment variable is set.
   *
   * @return Full path and file name
   */
  private String getEnvFileName() {
    // Get script file name
    String homeDir = System.getProperty("NODUS_HOME", ".");
    String scriptFileName = homeDir + File.separator + "jvmargs";

    if (isRunningOnWindows()) {
      scriptFileName += ".bat";
    } else {
      scriptFileName += ".sh";
    }
    return scriptFileName;
  }

  /**
   * Tests if running on Windows on not.
   *
   * @return True if Windows, false if Linux or macOS
   */
  private boolean isRunningOnWindows() {
    String os = System.getProperty("os.name");
    if (os.startsWith("Windows")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Creates the shell script with the defined heap values.
   *
   * @param scriptFileName The path to the file to update
   * @param heaSettings The heap settings
   */
  private void createScript(String scriptFileName, String heapSettings) {

    try {
      FileWriter fileWriter = new FileWriter(scriptFileName);
      BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

      String line;
      if (isRunningOnWindows()) {
        line = "set JVMARGS=" + heapSettings;
      } else {
        line = "JVMARGS=\"" + heapSettings + "\"";
      }

      bufferedWriter.write(line);
      bufferedWriter.close();
      fileWriter.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
