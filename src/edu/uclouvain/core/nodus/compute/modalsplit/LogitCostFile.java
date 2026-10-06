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

package edu.uclouvain.core.nodus.compute.modalsplit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes fitted coefficients and a diagnostic report without replacing transport cost expressions.
 *
 * <p>Logit, probit and proportional coefficients have separate namespaces and generated sections.
 * For the selected method, newly fitted groups replace all previous entries for those groups,
 * including obsolete mode entries; coefficients of other groups are carried forward. Other models'
 * coefficients and generated reports remain intact. The selected method's generated report is
 * replaced as a whole, so its retained older groups do not gain new fit diagnostics.
 *
 * <p>Input is a Java properties file in ISO-8859-1. The writer preserves user comments,
 * noncoefficient logical lines, continued expressions and the existing newline convention, escaping
 * non-ASCII report characters. The caller supplies newline-terminated comment text for the report
 * and a complete set of validated fitted entries, including each group's reference-mode property.
 *
 * <p>A sibling temporary file is written first. Interruption and a byte comparison with the source
 * and destination snapshots are checked before saving; supported POSIX permissions are retained.
 * Existing destinations are replaced atomically, while new destinations are moved into place
 * without requesting replacement. An edited source or destination aborts saving. The temporary file
 * is deleted on failure. Snapshot checks detect conflicts but are not filesystem locks: callers
 * should avoid editing either file concurrently with saving.
 *
 * <p>This helper owns neither the database nor in-memory assignment properties. It returns the
 * properties loaded from the replacement so {@link LogitCalibration} can refresh them after saving.
 */
final class LogitCostFile {
  private LogitCostFile() {}

  /**
   * Source and destination selected before estimation, including the destination's prior content.
   */
  static final class Target {
    final Path source;
    final Path file;
    final byte[] original;
    private final byte[] previous;

    private Target(Path source, Path file, byte[] original, byte[] previous) {
      this.source = source.toAbsolutePath().normalize();
      this.file = file.toAbsolutePath().normalize();
      this.original = original;
      this.previous = previous;
    }

    /** Returns true only when saving over a different existing file needs user confirmation. */
    boolean requiresConfirmation() {
      return !source.equals(file) && previous != null;
    }

    /** Refuses changed, removed or newly created destinations after selection/confirmation. */
    void checkUnchanged() throws IOException {
      if (!Arrays.equals(original, Files.readAllBytes(source))) {
        throw new IOException(
            "Source cost file changed since selection; estimates were not saved: " + source);
      }
      boolean unchanged =
          previous == null
              ? Files.notExists(file)
              : Files.isRegularFile(file) && Arrays.equals(previous, Files.readAllBytes(file));
      if (!unchanged) {
        throw new IOException(
            "Output cost file changed since selection; estimates were not saved: " + file);
      }
    }
  }

  /**
   * Validates the source and snapshots the output before the overwrite prompt or estimation.
   *
   * @param source Existing file providing all transport costs and other model coefficients
   * @param output File to create or replace, possibly the source itself
   * @return Destination snapshot used both for confirmation and conflict detection
   * @throws IOException If either path cannot be read or the output cannot be written
   */
  static Target target(Path source, Path output) throws IOException {
    if (source == null || !Files.isRegularFile(source) || !Files.isReadable(source)) {
      throw new IOException("Cannot read source cost functions file: " + source);
    }
    if (output == null) {
      throw new IOException("An output cost functions file is required");
    }
    Path file = output.toAbsolutePath().normalize();
    if (!Files.isDirectory(file.getParent()) || !Files.isWritable(file.getParent())) {
      throw new IOException("Cannot write output cost functions file: " + file);
    }
    byte[] original = Files.readAllBytes(source);
    if (Files.notExists(file)) {
      return new Target(source, file, original, null);
    }
    if (!Files.isRegularFile(file) || !Files.isReadable(file) || !Files.isWritable(file)) {
      throw new IOException("Cannot update output cost functions file: " + file);
    }
    return new Target(source, file, original, Files.readAllBytes(file));
  }

  /**
   * Writes logit entries using the same guarded replacement as the model-selecting overload.
   *
   * @param file selected cost file
   * @param original bytes captured before estimation
   * @param fitted complete fitted entries for the groups being replaced
   * @param report newline-terminated properties comments
   * @return all properties loaded from the replacement
   * @throws IOException if reading, conflict checking or atomic saving fails
   */
  static Properties save(Path file, byte[] original, Properties fitted, String report)
      throws IOException {
    return save(file, original, fitted, report, "MNL");
  }

  /**
   * Replaces the selected method's generated section after checking the original snapshot.
   *
   * @param file selected cost file, whose parent holds the temporary replacement
   * @param original bytes captured before fitting, used to detect intervening edits
   * @param fitted complete fitted entries; each key ends with its commodity-group ID
   * @param report newline-terminated comment lines to insert before the coefficients
   * @param method stable short name: MNL, MNP or Proportional
   * @return all properties loaded from the saved replacement
   * @throws IOException on malformed generated sections, source changes or filesystem failure
   * @throws IllegalArgumentException for an unsupported method
   * @throws java.util.concurrent.CancellationException if interrupted before replacement
   */
  static Properties save(
      Path file, byte[] original, Properties fitted, String report, String method)
      throws IOException {
    return save(new Target(file, file, original, original), original, fitted, report, method);
  }

  /**
   * Builds the output from the source snapshot, preserving the destination only for conflict
   * checks.
   *
   * @param target Output selected before estimation; any required overwrite must be approved
   * @param original Source bytes captured before routing
   * @param fitted Complete fitted entries for the groups being replaced
   * @param report Newline-terminated comment lines
   * @param method Stable modal-method identifier
   * @return All properties loaded from the saved output
   * @throws IOException If a file changed or saving fails
   */
  static Properties save(
      Target target, byte[] original, Properties fitted, String report, String method)
      throws IOException {
    final Path file = target.file;
    if (!LogitCalibrationSettings.supportsMethod(method)) {
      throw new IllegalArgumentException("Unsupported calibration method: " + method);
    }
    final String begin = "# BEGIN NODUS ESTIMATED " + method;
    final String end = "# END NODUS ESTIMATED " + method;
    Properties existing = new Properties();
    existing.load(new ByteArrayInputStream(original));
    Set<String> groups = new TreeSet<>();
    fitted
        .stringPropertyNames()
        .forEach(key -> groups.add(key.substring(key.lastIndexOf('.') + 1)));
    Properties coefficients = new Properties();
    for (String key : existing.stringPropertyNames()) {
      if (isCoefficient(key, method) && !groups.contains(key.substring(key.lastIndexOf('.') + 1))) {
        coefficients.setProperty(key, existing.getProperty(key));
      }
    }
    coefficients.putAll(fitted);
    String source = new String(original, StandardCharsets.ISO_8859_1);
    final String newline = source.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder preserved = new StringBuilder();
    StringBuilder logical = new StringBuilder();
    boolean generated = false;
    for (String line : source.split("(?<=\n)", -1)) {
      String trimmed = line.strip();
      if (logical.length() == 0 && trimmed.equals(begin)) {
        generated = true;
      }
      if (generated) {
        if (trimmed.equals(end)) {
          generated = false;
        }
        continue;
      }
      logical.append(line);
      String content = line.replaceFirst("[\\r\\n]+$", "");
      int slashes = 0;
      for (int i = content.length() - 1; i >= 0 && content.charAt(i) == '\\'; i--) {
        slashes++;
      }
      if (slashes % 2 == 1 && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
        continue;
      }
      preserveNonCoefficient(preserved, logical.toString(), method);
      logical.setLength(0);
    }
    if (generated) {
      throw new IOException("Unterminated generated " + method + " section in " + file);
    }
    if (logical.length() > 0) {
      preserveNonCoefficient(preserved, logical.toString(), method);
    }
    if (preserved.length() > 0 && preserved.charAt(preserved.length() - 1) != '\n') {
      preserved.append(newline);
    }
    preserved.append(begin).append(newline);
    // Properties files are ISO-8859-1. Escape non-ASCII metadata rather than losing table names.
    for (char character : report.replace("\r\n", "\n").toCharArray()) {
      if (character == '\n') {
        preserved.append(newline);
      } else if (character > 127) {
        preserved.append(String.format("\\u%04x", (int) character));
      } else {
        preserved.append(character);
      }
    }
    for (String key : new TreeSet<>(coefficients.stringPropertyNames())) {
      preserved.append(key).append(" = ").append(coefficients.getProperty(key)).append(newline);
    }
    preserved.append(end).append(newline);
    byte[] replacement = preserved.toString().getBytes(StandardCharsets.ISO_8859_1);
    Properties result = new Properties();
    result.load(new ByteArrayInputStream(replacement));
    Path temporary = Files.createTempFile(file.getParent(), ".nodus-logit-", ".tmp");
    try {
      Files.write(temporary, replacement);
      try {
        Path permissions = target.previous == null ? target.source : file;
        Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(permissions));
      } catch (UnsupportedOperationException ignored) {
        // POSIX permissions are not available on every supported filesystem.
      }
      if (Thread.currentThread().isInterrupted()) {
        throw new java.util.concurrent.CancellationException("Modal calibration canceled");
      }
      if (!Arrays.equals(original, Files.readAllBytes(target.source))) {
        throw new IOException(
            "Cost file changed during modal estimation; estimates were not saved");
      }
      target.checkUnchanged();
      if (target.previous == null) {
        // No replacement is authorized if a destination appears after the selection.
        Files.move(temporary, file);
      } else {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
    return result;
  }

  /** Preserves a complete logical properties line unless it defines this model's coefficients. */
  private static void preserveNonCoefficient(StringBuilder output, String block, String method)
      throws IOException {
    Properties parsed = new Properties();
    parsed.load(new StringReader(block));
    if (parsed.stringPropertyNames().stream().noneMatch(key -> isCoefficient(key, method))) {
      output.append(block);
    }
  }

  /**
   * Recognizes only this model's coefficient/reference namespace, leaving transport formulas
   * intact.
   */
  private static boolean isCoefficient(String key, String method) {
    if ("Proportional".equals(method)) {
      return key.matches("proportional\\.costFactor\\.\\d+\\.\\d+")
          || key.matches("proportional\\.reference\\.\\d+");
    }
    if ("MNP".equals(method)) {
      return key.matches("probit\\.(?:\\(intercept\\)|log\\(cost\\))\\.\\d+\\.\\d+")
          || key.matches("probit\\.reference\\.\\d+");
    }
    return key.matches("(?:\\(intercept\\)|log\\(cost\\))\\.\\d+\\.\\d+")
        || key.matches("mnl\\.reference\\.\\d+");
  }

  /** Replaces old embedded estimates with the single database-table reference. */
  static Properties saveParameterTable(Target target, String table) throws IOException {
    ModalParameterTable.validateName(table);
    target.checkUnchanged();
    String source = new String(target.original, StandardCharsets.ISO_8859_1);
    String newline = source.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder preserved = new StringBuilder();
    StringBuilder logical = new StringBuilder();
    boolean generated = false;
    for (String line : source.split("(?<=\n)", -1)) {
      String trimmed = line.strip();
      if (trimmed.startsWith("# BEGIN NODUS ESTIMATED ")) {
        generated = true;
      }
      if (generated) {
        if (trimmed.startsWith("# END NODUS ESTIMATED ")) {
          generated = false;
        }
        continue;
      }
      logical.append(line);
      String content = line.replaceFirst("[\\r\\n]+$", "");
      int slashes = 0;
      for (int i = content.length() - 1; i >= 0 && content.charAt(i) == '\\'; i--) {
        slashes++;
      }
      if (slashes % 2 == 1 && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
        continue;
      }
      preserveNonModalParameter(preserved, logical.toString());
      logical.setLength(0);
    }
    if (generated) {
      throw new IOException("Unterminated generated modal estimation section");
    }
    if (logical.length() > 0) {
      preserveNonModalParameter(preserved, logical.toString());
    }
    if (preserved.length() > 0 && preserved.charAt(preserved.length() - 1) != '\n') {
      preserved.append(newline);
    }
    preserved.append(ModalParameterTable.POINTER).append('=').append(table).append(newline);
    byte[] replacement = preserved.toString().getBytes(StandardCharsets.ISO_8859_1);
    Path temporary = Files.createTempFile(target.file.getParent(), ".nodus-params-", ".tmp");
    try {
      Files.write(temporary, replacement);
      try {
        Files.setPosixFilePermissions(
            temporary, Files.getPosixFilePermissions(target.file));
      } catch (UnsupportedOperationException ignored) {
        // Filesystems without POSIX permissions retain their defaults.
      }
      target.checkUnchanged();
      if (Thread.currentThread().isInterrupted()) {
        throw new java.util.concurrent.CancellationException("Modal estimation canceled");
      }
      Files.move(temporary, target.file, StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    Properties result = new Properties();
    result.load(new ByteArrayInputStream(replacement));
    return result;
  }

  private static void preserveNonModalParameter(StringBuilder output, String block)
      throws IOException {
    Properties parsed = new Properties();
    parsed.load(new StringReader(block));
    if (parsed.stringPropertyNames().stream().noneMatch(key ->
        key.equals(ModalParameterTable.POINTER)
            || isCoefficient(key, "MNL")
            || isCoefficient(key, "MNP")
            || isCoefficient(key, "Proportional"))) {
      output.append(block);
    }
  }
}
