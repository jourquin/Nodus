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

import java.awt.GraphicsEnvironment;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

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
 * <p>Since Nodus 8.1, the additional -Dfile.encoding=UTF8 flag is set.
 *
 * <p>Since Nodus 8.4, version-dependent JVM flags are set dynamically in the generated JVM
 * arguments file.
 *
 * <p>Since Nodus 8.6, legacy single-assignment files are upgraded while preserving custom
 * arguments.
 *
 * <p>Since Nodus 8.1 Build 20220103, the Times font is also installed on Mac OS Monterey machines
 * if needed.
 *
 * @author Bart Jourquin
 */
public class SetJVMArgs {

  /** Treat quoted values as part of their argument, not as independent JVM options. */
  private static final Pattern JVM_ARGUMENT = Pattern.compile("(?:[^\\s\"']+|\"[^\"]*\"|'[^']*')+");

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
    try {
      if (!file.exists()) {
        // Get the heap settings and add UTF-8 encoding flag
        String parameters = getDefaultHeapSettings() + " -Dfile.encoding=UTF-8";
        createScript(file.toPath(), parameters, isRunningOnWindows());
      } else {
        upgradeLegacyScript(file.toPath(), isRunningOnWindows());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Install the Times font if needed
    installTimesFontIfNeeded();
  }

  /**
   * Upgrades the single-assignment format written by older releases. Multi-line scripts are left
   * alone because they may contain user-defined logic. The first backup is never overwritten.
   *
   * @param scriptFile The existing JVM arguments file.
   * @param windows Whether this is a Windows batch file.
   * @throws IOException If reading, backing up or writing the script fails.
   */
  static void upgradeLegacyScript(Path scriptFile, boolean windows) throws IOException {
    String script = Files.readString(scriptFile, Charset.defaultCharset()).strip();
    String assignment =
        windows
            ? "(?i)@?set[ \\t]+(?:\"JVMARGS=([^\\r\\n]*)\"|JVMARGS=([^\\r\\n]*))"
            : "(?:export[ \\t]+)?JVMARGS=\"([^\\r\\n]*)\"";
    Matcher match = Pattern.compile(assignment).matcher(script);
    if (!match.matches()) {
      return;
    }
    String parameters = match.group(1);
    if (parameters == null) {
      parameters = match.group(2);
    }
    // Do not reinterpret shell commands or user-defined variable expansion as static arguments.
    if (parameters.matches(".*[;&|<>`$%].*")) {
      return;
    }

    Matcher arguments = JVM_ARGUMENT.matcher(parameters);
    StringBuffer updated = new StringBuffer();
    boolean changed = false;
    while (arguments.find()) {
      if (arguments.group().matches("--illegal-access=(deny|permit|warn|debug)")) {
        arguments.appendReplacement(updated, "");
        changed = true;
      }
    }
    if (!changed) {
      return;
    }
    arguments.appendTail(updated);

    Path backup = scriptFile.resolveSibling(scriptFile.getFileName() + ".bak");
    if (!Files.exists(backup)) {
      Files.copy(scriptFile, backup);
    }
    createScript(scriptFile, updated.toString().strip(), windows);
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
    long memorySize = getTotalMemory();

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
   * Returns the total amount of RAM of the computer system.
   *
   * @return The total amount of RAM as a long integer.
   */
  private long getTotalMemory() {
    java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();

    if (bean instanceof com.sun.management.OperatingSystemMXBean) {
      return ((com.sun.management.OperatingSystemMXBean) bean).getTotalPhysicalMemorySize();
    }

    return 4L * 1024 * 1024 * 1024;
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
   * @param scriptFile The path to the file to update
   * @param parameters The parameters passed to the JVM
   * @param windows Whether to write a Windows batch file
   * @throws IOException If writing the script fails
   */
  static void createScript(Path scriptFile, String parameters, boolean windows) throws IOException {

    try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(scriptFile.toFile()))) {

      if (windows) {
        bufferedWriter.write("set \"JVMARGS=" + parameters + "\"");
        bufferedWriter.newLine();
        bufferedWriter.write("set JAVA_FEATURE=0");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "for /f \"tokens=3\" %%v in ('java.exe -version 2^>^&1 ^| findstr /i"
                + " \"version\"') do set JAVA_VERSION=%%~v");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "for /f \"tokens=1 delims=.-\" %%v in (\"%JAVA_VERSION%\") do set"
                + " JAVA_FEATURE=%%v");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if %JAVA_FEATURE% GEQ 9 if %JAVA_FEATURE% LEQ 16 set"
                + " \"JVMARGS=%JVMARGS% --illegal-access=deny\"");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if %JAVA_FEATURE% GEQ 24 set \"JVMARGS=%JVMARGS%"
                + " --enable-native-access=ALL-UNNAMED\"");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if %JAVA_FEATURE% GEQ 24 if %JAVA_FEATURE% LEQ 25 set"
                + " \"JVMARGS=%JVMARGS% --sun-misc-unsafe-memory-access=allow\"");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if %JAVA_FEATURE% GEQ 26 set \"JVMARGS=%JVMARGS%"
                + " --sun-misc-unsafe-memory-access=warn\"");
      } else {
        bufferedWriter.write("JVMARGS=\"" + parameters + "\"");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "JAVA_FEATURE=$(java -version 2>&1 | sed -n 's/.*version"
                + " \"\\([0-9][0-9]*\\).*/\\1/p' | head -n 1)");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if [ -n \"$JAVA_FEATURE\" ] && [ \"$JAVA_FEATURE\" -ge 9 ]"
                + " && [ \"$JAVA_FEATURE\" -le 16 ] 2>/dev/null; then");
        bufferedWriter.newLine();
        bufferedWriter.write("    JVMARGS=\"$JVMARGS --illegal-access=deny\"");
        bufferedWriter.newLine();
        bufferedWriter.write("fi");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if [ -n \"$JAVA_FEATURE\" ] && [ \"$JAVA_FEATURE\" -ge 24 ]" + " 2>/dev/null; then");
        bufferedWriter.newLine();
        bufferedWriter.write("    JVMARGS=\"$JVMARGS --enable-native-access=ALL-UNNAMED\"");
        bufferedWriter.newLine();
        bufferedWriter.write("fi");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if [ -n \"$JAVA_FEATURE\" ] && [ \"$JAVA_FEATURE\" -ge 24 ]"
                + " && [ \"$JAVA_FEATURE\" -le 25 ] 2>/dev/null; then");
        bufferedWriter.newLine();
        bufferedWriter.write("    JVMARGS=\"$JVMARGS --sun-misc-unsafe-memory-access=allow\"");
        bufferedWriter.newLine();
        bufferedWriter.write("fi");
        bufferedWriter.newLine();
        bufferedWriter.write(
            "if [ -n \"$JAVA_FEATURE\" ] && [ \"$JAVA_FEATURE\" -ge 26 ]" + " 2>/dev/null; then");
        bufferedWriter.newLine();
        bufferedWriter.write("    JVMARGS=\"$JVMARGS --sun-misc-unsafe-memory-access=warn\"");
        bufferedWriter.newLine();
        bufferedWriter.write("fi");
      }
    }
  }

  /** The Times font is not always installed on MacOS. */
  private void installTimesFontIfNeeded() {

    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) {
      return;
    }

    // Test if the Times font is installed
    GraphicsEnvironment g = null;
    PrintStream originalErr = System.err;

    try (PrintStream devNull = new PrintStream("/dev/null")) {
      // Intercept the macOS/font warning message
      System.setErr(devNull);
      g = GraphicsEnvironment.getLocalGraphicsEnvironment();
    } catch (FileNotFoundException e) {
      // If /dev/null cannot be opened, try without redirecting System.err
      try {
        g = GraphicsEnvironment.getLocalGraphicsEnvironment();
      } catch (RuntimeException e2) {
        e2.printStackTrace();
        return;
      }
    } finally {
      System.setErr(originalErr);
    }

    if (g == null) {
      return;
    }

    boolean isInstalled = false;
    String[] fonts = g.getAvailableFontFamilyNames();

    for (String font : fonts) {
      if ("Times".equals(font)) {
        isInstalled = true;
        break;
      }
    }

    // Install if needed
    if (!isInstalled) {
      String userDir = System.getProperty("user.home");
      File fontDir = new File(userDir + "/Library/Fonts/");

      if (!fontDir.exists() && !fontDir.mkdirs()) {
        System.err.println("Could not create font directory: " + fontDir);
        return;
      }

      File source = new File("share/times.ttf");
      File dest = new File(fontDir, "times.ttf");

      try {
        FileUtils.copyFile(source, dest);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
