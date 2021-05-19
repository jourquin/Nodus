/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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
import java.util.Scanner;

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
 * <p>For 32bits JVM, max heap is set to 1.4Go
 *
 * <p>Since Nodus 8.0, the additional --illegal-access=permit flag is set if JVM version is >= 9.
 * This is not really needed if JVM version < 16, but needed for JAVA 16. This should be tackled
 * more seriously in the Nodus core code as some reflective accesses, such as those called to mimic
 * Mac OS L&F will no longer be allowed is future versions of the JVM.
 *
 * <p>Since Nodus 8.1, the additional -Dfile.encoding=UTF-8 flag is set (needed by J4R)
 *
 * @author Bart Jourquin
 */
public class SetJVMArgs {

  /**
   * Entry point.
   *
   * @param args Not used
   */
  public static void main(String[] args) {
    new SetJVMArgs();
  }

  /** Sets the default heap values in the launcher script. */
  public SetJVMArgs() {

    String envtFileName = getEnvFileName();

    File file = new File(envtFileName);
    if (!file.exists()) {

      // Get the heap settings and add UTF8 encoding flag
      String parameters = getDefaultHeapSettings() + " -Dfile.encoding=UTF8";

      // Update the script
      createScript(envtFileName, parameters);
    }

    // Set the --illegal-access=permit flag if needed
    setIllegalAccessFlag(envtFileName);
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
   * @param parameters The parameters passed to the JVM
   */
  private void createScript(String scriptFileName, String parameters) {

    try {
      FileWriter fileWriter = new FileWriter(scriptFileName);
      BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

      String line;
      if (isRunningOnWindows()) {
        line = "set JVMARGS=" + parameters;
      } else {
        line = "JVMARGS=\"" + parameters + "\"";
      }

      bufferedWriter.write(line);
      bufferedWriter.close();
      fileWriter.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Set the --illegal-access flag accordingly to the used JVM version at runtime.
   *
   * @param scriptFileName The name of the file that contains the JVMARGS
   */
  private void setIllegalAccessFlag(String scriptFileName) {
    String line = "";
    try {
      String value = "";
      Scanner scanner = new Scanner(new File(scriptFileName));
      while (scanner.hasNextLine()) {
        line = scanner.nextLine();
        if (line.contains("JVMARGS")) {
          // Get current value of args
          int idx = line.indexOf("=");
          value = line.substring(idx + 1);

          // Remove double quotes, if any
          value = value.replaceAll("\"", "");

          boolean isAtLeastJava9 = JavaVersionUtil.isJavaVersionAtLeast(9);
          String illelagAccessFlag = "--illegal-access=permit";
          if (value.contains(illelagAccessFlag)) {
            // Remove if JVM < 9
            if (!isAtLeastJava9) {
              value = value.replace("--illegal-access=permit", "");
            }
          } else {
            // Add if JVM >= 9
            if (isAtLeastJava9) {
              value = value + " " + illelagAccessFlag;
            }
          }
          value = value.trim();

          break;
        }
      }
      scanner.close();

      // Write the file
      FileWriter fileWriter = new FileWriter(scriptFileName);
      BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

      if (isRunningOnWindows()) {
        line = "set JVMARGS=" + value;
      } else {
        line = "JVMARGS=\"" + value + "\"";
      }

      bufferedWriter.write(line);
      bufferedWriter.close();
      fileWriter.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
