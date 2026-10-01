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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence checks against real cost files, including comments and continued expressions. */
class LogitCostFileTest {
  @TempDir Path directory;

  @Test
  void preservesExpressionsCommentsAndOtherGroupsWithoutAccumulatingGeneratedSections()
      throws Exception {
    Path file = directory.resolve("model.costs");
    String user =
        "# Coût original\r\nmv.1,1 = LENGTH * \\\r\n    UNITCOST\r\n"
            + "# Backslash in a comment is not a continuation \\\r\nUNITCOST = 3\r\n";
    Files.write(
        file,
        (user + "log(cost).1.0 = -99\r\nlog(cost).9.0 = -99\r\n" + "log(cost).1.7 = -7\r\n")
            .getBytes(StandardCharsets.ISO_8859_1));
    Properties fitted = new Properties();
    fitted.setProperty("log(cost).1.0", "-2.0");
    fitted.setProperty("(intercept).1.0", "0.0");
    for (int run = 0; run < 2; run++) {
      Properties saved =
          LogitCostFile.save(file, Files.readAllBytes(file), fitted, "# Fit results\n");
      String text = Files.readString(file, StandardCharsets.ISO_8859_1);
      assertTrue(text.startsWith(user));
      assertEquals(1, text.split("# BEGIN NODUS ESTIMATED MNL", -1).length - 1);
      assertEquals("LENGTH * UNITCOST", saved.getProperty("mv.1,1"));
      assertEquals("3", saved.getProperty("UNITCOST"));
      assertEquals("-2.0", saved.getProperty("log(cost).1.0"));
      assertEquals("-7", saved.getProperty("log(cost).1.7"));
      assertEquals(null, saved.getProperty("log(cost).9.0"));
    }
  }

  @Test
  void modalUpdatesKeepEachOthersEstimatesAndReports() throws Exception {
    Path file = directory.resolve("both.costs");
    Files.writeString(
        file,
        "# User comment\nmv.1,1 = 3\nprobit.reference.7 = 1\nprobit.log(cost).1.7 = -7\n"
            + "proportional.costFactor.9.0 = 9\nproportional.costFactor.1.7 = 1\n");
    Properties mnl = new Properties();
    mnl.setProperty("log(cost).1.0", "-2");
    Properties probit = new Properties();
    probit.setProperty("probit.log(cost).1.0", "-3");
    Properties proportional = new Properties();
    proportional.setProperty("proportional.costFactor.1.0", "1");
    proportional.setProperty("proportional.reference.0", "1");
    for (int run = 0; run < 2; run++) {
      LogitCostFile.save(file, Files.readAllBytes(file), mnl, "# MNL report\n", "MNL");
      LogitCostFile.save(file, Files.readAllBytes(file), probit, "# MNP report\n", "MNP");
      Properties saved =
          LogitCostFile.save(
              file,
              Files.readAllBytes(file),
              proportional,
              "# Proportional report\n",
              "Proportional");
      assertEquals("1", saved.getProperty("probit.reference.7"));
      assertEquals("-7", saved.getProperty("probit.log(cost).1.7"));
      assertEquals("-3", saved.getProperty("probit.log(cost).1.0"));
      assertEquals("-2", saved.getProperty("log(cost).1.0"));
      assertEquals("1", saved.getProperty("proportional.costFactor.1.0"));
      assertEquals("1", saved.getProperty("proportional.costFactor.1.7"));
      assertEquals(null, saved.getProperty("proportional.costFactor.9.0"));
      String content = Files.readString(file);
      assertTrue(content.contains("# User comment\nmv.1,1 = 3\n"));
      assertTrue(content.contains("# MNL report\n"));
      assertTrue(content.contains("# MNP report\n"));
      assertTrue(content.contains("# Proportional report\n"));
      assertEquals(1, content.split("# BEGIN NODUS ESTIMATED MNL", -1).length - 1);
      assertEquals(1, content.split("# BEGIN NODUS ESTIMATED MNP", -1).length - 1);
      assertEquals(1, content.split("# BEGIN NODUS ESTIMATED Proportional", -1).length - 1);
    }
  }

  @Test
  void refusesToOverwriteAnEditMadeDuringEstimation() throws Exception {
    Path file = directory.resolve("model.costs");
    byte[] original = "mv.1,1 = 3\n".getBytes(StandardCharsets.ISO_8859_1);
    byte[] changed = "mv.1,1 = 4\n".getBytes(StandardCharsets.ISO_8859_1);
    Files.write(file, changed);
    Properties fitted = new Properties();
    fitted.setProperty("log(cost).1.0", "-2");
    assertThrows(java.io.IOException.class, () -> LogitCostFile.save(file, original, fitted, ""));
    assertArrayEquals(changed, Files.readAllBytes(file));
    try (java.util.stream.Stream<Path> files = Files.list(directory)) {
      assertEquals(1, files.count(), "Uncommitted temporary files must be removed");
    }
  }

  @Test
  void confirmationIsRequiredOnlyForADifferentExistingOutput() throws Exception {
    Path source = directory.resolve("uncalibrated.costs");
    Files.writeString(source, "mv.1,1 = 3\n");
    Path output = directory.resolve("mnl.costs");
    assertFalse(LogitCostFile.target(source, source).requiresConfirmation());
    assertFalse(LogitCostFile.target(source, output).requiresConfirmation());
    Files.writeString(output, "mv.1,1 = 99\n");
    assertTrue(LogitCostFile.target(source, output).requiresConfirmation());
    assertFalse(
        LogitCostFile.target(source, directory.resolve("./uncalibrated.costs"))
            .requiresConfirmation());
  }

  @Test
  void saveAsUsesTheSourceContentsAndLeavesTheSourceUntouched() throws Exception {
    Path source = directory.resolve("uncalibrated.costs");
    byte[] original =
        ("# Source comment\r\nmv.1,1 = LENGTH * \\\r\n    UNITCOST\r\n"
                + "UNITCOST = 3\r\nprobit.log(cost).1.0 = -7\r\n")
            .getBytes(StandardCharsets.ISO_8859_1);
    Files.write(source, original);
    Properties fitted = new Properties();
    fitted.setProperty("log(cost).1.0", "-2");
    for (boolean exists : new boolean[] {false, true}) {
      Path output = directory.resolve(exists ? "existing.costs" : "new.costs");
      if (exists) {
        Files.writeString(output, "# Unrelated output\nmv.1,1 = 99\nold.output = true\n");
      }
      Properties saved =
          LogitCostFile.save(
              LogitCostFile.target(source, output), original, fitted, "# Report\n", "MNL");
      assertArrayEquals(original, Files.readAllBytes(source));
      assertEquals("LENGTH * UNITCOST", saved.getProperty("mv.1,1"));
      assertEquals("-7", saved.getProperty("probit.log(cost).1.0"));
      assertEquals("-2", saved.getProperty("log(cost).1.0"));
      assertFalse(saved.containsKey("old.output"));
      String content = Files.readString(output);
      assertTrue(content.startsWith("# Source comment\r\n"));
      assertTrue(content.contains("# Report\r\n"));
    }
  }

  @Test
  void saveAsDoesNotOverwriteANewOrEditedDestinationAfterSelection() throws Exception {
    Path source = directory.resolve("uncalibrated.costs");
    Files.writeString(source, "mv.1,1 = 3\n");
    byte[] original = Files.readAllBytes(source);
    for (boolean exists : new boolean[] {false, true}) {
      Path output = directory.resolve(exists ? "existing.costs" : "new.costs");
      if (exists) {
        Files.writeString(output, "mv.1,1 = 5\n");
      }
      LogitCostFile.Target target = LogitCostFile.target(source, output);
      Files.writeString(output, "# Edited while fitting\nmv.1,1 = 8\n");
      assertThrows(
          java.io.IOException.class,
          () -> LogitCostFile.save(target, original, new Properties(), "", "MNL"));
      assertEquals("# Edited while fitting\nmv.1,1 = 8\n", Files.readString(output));
      assertArrayEquals(original, Files.readAllBytes(source));
    }
  }

  @Test
  void saveAsDetectsSourceEditsAndDoesNotCreateTheOutput() throws Exception {
    Path source = directory.resolve("uncalibrated.costs");
    Files.writeString(source, "mv.1,1 = 3\n");
    byte[] original = Files.readAllBytes(source);
    Path output = directory.resolve("mnl.costs");
    LogitCostFile.Target target = LogitCostFile.target(source, output);
    Files.writeString(source, "mv.1,1 = 8\n");
    assertThrows(
        java.io.IOException.class,
        () -> LogitCostFile.save(target, original, new Properties(), "", "MNL"));
    assertEquals("mv.1,1 = 8\n", Files.readString(source));
    assertFalse(Files.exists(output));
    try (java.util.stream.Stream<Path> files = Files.list(directory)) {
      assertEquals(1, files.count());
    }
  }

  @Test
  void settingsRoundTripTableNamesAndValidateTheReferenceChoice() {
    LogitCalibrationSettings settings =
        new LogitCalibrationSettings(3, Map.of(1, "Observed road: 2026", 3, "Rail \\\"été\\\""));
    LogitCalibrationSettings restored = LogitCalibrationSettings.decode(settings.encode());
    restored.validate();
    assertEquals(3, restored.getReferenceMode());
    assertEquals(settings.getTables(), restored.getTables());
    assertThrows(
        IllegalArgumentException.class,
        () -> new LogitCalibrationSettings(2, Map.of(1, "a", 3, "b")).validate());
    new LogitCalibrationSettings(2, Map.of(1, "a", 3, "b")).validate(false);
    assertThrows(
        IllegalArgumentException.class,
        () -> new LogitCalibrationSettings(2, Map.of(1, "a")).validate(false));
    assertThrows(IllegalArgumentException.class, () -> LogitCalibrationSettings.decode("broken"));
  }

  @Test
  void legacySettingsMigrateObservationsWithoutAssignmentFlags() {
    LogitCalibrationSettings legacy =
        LogitCalibrationSettings.decode(
            "{\"estimate\":true,\"sumObserved\":true,\"excludeSkippedDemand\":true,"
                + "\"referenceMode\":1,\"tables\":{\"1\":\"road\",\"2\":\"rail\"}}");
    assertEquals(Map.of(1, "road", 2, "rail"), legacy.getTables());
    assertEquals(1, legacy.getReferenceMode());
    assertFalse(legacy.encode().contains("sumObserved"));
    assertFalse(legacy.encode().contains("estimate"));
    assertFalse(legacy.encode().contains("excludeSkippedDemand"));
  }
}
