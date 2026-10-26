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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.utils.CommentedProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Exercises the real project's property writers and early open failures without desktop dialogs.
 */
@ResourceLock(Resources.SYSTEM_ERR)
@Isolated("Early project opening restores all mutable NodusC settings")
class NodusProjectPropertiesTest {
  private static final byte[] ORIGINAL =
      "# Coût\nkeep=before\nremove=old\n".getBytes(StandardCharsets.ISO_8859_1);
  @TempDir Path directory;

  @Test
  void projectSavesKeepCommentsAndPersistAddedEditedAndRemovedSettings() throws Exception {
    final Path file = Files.write(directory.resolve("model.nodus"), ORIGINAL);
    final CommentedProperties properties = properties();
    final NodusProject project = project(file, properties);
    project.setProperty("keep", "after");
    project.setProperty("new key", "café 東京\nsecond line");
    properties.remove("remove");
    project.saveProperties();
    assertEquals(properties, read(file));
    assertTrue(Files.readString(file, StandardCharsets.ISO_8859_1).startsWith("# Coût"));
    assertEquals("café 東京\nsecond line", project.getProperty("new key"));
    assertOnly(file);
  }

  @Test
  void failedSavePropagatesPreservesFileAndMemoryAndPreventsSuccessContinuation() throws Exception {
    final Path file = Files.write(directory.resolve("model.nodus"), ORIGINAL);
    final FailingProperties properties = new FailingProperties();
    properties.load(new ByteArrayInputStream(ORIGINAL));
    final NodusProject project = project(file, properties);
    project.setProperty("keep", "unsaved change");
    final AtomicInteger continued = new AtomicInteger();
    final UncheckedIOException failure =
        assertThrows(
            UncheckedIOException.class,
            () -> {
              project.saveProperties();
              continued.incrementAndGet();
            });
    assertEquals("simulated write failure", failure.getCause().getMessage());
    assertEquals(0, continued.get());
    assertArrayEquals(ORIGINAL, Files.readAllBytes(file));
    assertEquals("unsaved change", project.getProperty("keep"));
    assertOnly(file);
    properties.fail = false;
    project.saveProperties();
    assertEquals(properties, read(file));
    assertOnly(file);
  }

  @Test
  void anUnwritableDestinationIsReportedWithoutDiscardingEditedSettings() throws Exception {
    final Path destination = Files.createDirectory(directory.resolve("model.nodus"));
    final Path sentinel = Files.writeString(destination.resolve("sentinel"), "keep");
    final NodusProject project = project(destination, properties());
    project.setProperty("keep", "edited");
    assertThrows(UncheckedIOException.class, project::saveProperties);
    assertEquals("keep", Files.readString(sentinel));
    assertEquals("edited", project.getProperty("keep"));
    final Path retry = directory.resolve("retry.nodus");
    set(project, "projectResourceFileNameAndPath", retry.toString());
    project.saveProperties();
    assertEquals("edited", read(retry).getProperty("keep"));
  }

  @TestFactory
  List<DynamicTest> malformedExistingProjectsAreReportedWithoutOfferingToReplaceThem() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String invalid : List.of("key=\\u12", "key=\\uZZZZ")) {
      tests.add(
          dynamicTest(
              invalid,
              () -> {
                final Path file = Files.writeString(directory.resolve("model.nodus"), invalid);
                final HeadlessPanel panel = new HeadlessPanel();
                final OpeningProject project = new OpeningProject(panel);
                try {
                  SwingUtilities.invokeAndWait(() -> project.openProject(file.toString()));
                  assertEquals(1, project.errors.size());
                  assertTrue(project.errors.get(0) instanceof IllegalArgumentException);
                  assertFalse(project.isOpen());
                  final Field connection = NodusProject.class.getDeclaredField("jdbcConnection");
                  connection.setAccessible(true);
                  assertNull(connection.get(project));
                  assertEquals(invalid, Files.readString(file));
                  assertOnly(file);
                } finally {
                  panel.map.dispose();
                }
              }));
    }
    return tests;
  }

  @Test
  void anExistingUnreadableProjectIsNotTreatedAsAMissingFile() throws Exception {
    final Path file = Files.createDirectory(directory.resolve("model.nodus"));
    final Path sentinel = Files.writeString(file.resolve("sentinel"), "keep");
    final HeadlessPanel panel = new HeadlessPanel();
    final OpeningProject project = new OpeningProject(panel);
    try {
      SwingUtilities.invokeAndWait(() -> project.openProject(file.toString()));
      assertEquals(1, project.errors.size());
      assertTrue(project.errors.get(0) instanceof IOException);
      assertFalse(project.isOpen());
      assertEquals("keep", Files.readString(sentinel));
    } finally {
      panel.map.dispose();
    }
  }

  @Test
  void failedLocalSettingsSaveKeepsThePreviousFileAndPendingValuesForRetry() throws Exception {
    final Path main = directory.resolve("model.nodus");
    final Path local = Files.write(directory.resolve("model.nodus.local"), ORIGINAL);
    final HeadlessPanel panel = new HeadlessPanel();
    final NodusProject project = new NodusProject(panel);
    final FailingProperties pending = new FailingProperties();
    pending.setProperty("pending", "new value");
    set(project, "projectResourceFileNameAndPath", main.toString());
    set(project, "localProperties", pending);
    set(project, "nodeLayers", new NodusEsriLayer[0]);
    set(project, "linkLayers", new NodusEsriLayer[0]);
    final NodusLocationLayer labels = new NodusLocationLayer();
    labels.setName("labels");
    set(project, "labelsLayer", labels);
    final Method save = NodusProject.class.getDeclaredMethod("saveProjectLocalProperties");
    save.setAccessible(true);
    final PrintStream previousError = System.err;
    final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    final PrintStream captured = new PrintStream(errors, true, StandardCharsets.UTF_8);
    try {
      System.setErr(captured);
      save.invoke(project);
      assertTrue(errors.toString(StandardCharsets.UTF_8).contains("simulated write failure"));
      assertArrayEquals(ORIGINAL, Files.readAllBytes(local));
      assertEquals("new value", project.getLocalProperty("pending"));
      assertOnly(local);
      pending.fail = false;
      save.invoke(project);
      assertEquals("new value", read(local).getProperty("pending"));
      assertOnly(local);
    } finally {
      System.setErr(previousError);
      captured.close();
      panel.map.dispose();
    }
  }

  private static CommentedProperties properties() throws IOException {
    final CommentedProperties properties = new CommentedProperties();
    properties.load(new ByteArrayInputStream(ORIGINAL));
    return properties;
  }

  private static NodusProject project(Path file, CommentedProperties properties) throws Exception {
    final NodusProject project = new NodusProject(null);
    set(project, "projectResourceFileNameAndPath", file.toString());
    set(project, "projectProperties", properties);
    return project;
  }

  private static void set(NodusProject project, String name, Object value) throws Exception {
    final Field field = NodusProject.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(project, value);
  }

  private static Properties read(Path file) throws IOException {
    final Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    return properties;
  }

  private void assertOnly(Path file) throws IOException {
    try (java.util.stream.Stream<Path> files = Files.list(directory)) {
      assertEquals(List.of(file), files.collect(java.util.stream.Collectors.toList()));
    }
  }

  private static final class FailingProperties extends CommentedProperties {
    private static final long serialVersionUID = 1L;
    boolean fail = true;

    @Override
    public void store(OutputStream output, String header) throws IOException {
      if (fail) {
        output.write("partial".getBytes(StandardCharsets.ISO_8859_1));
        throw new IOException("simulated write failure");
      }
      super.store(output, header);
    }
  }

  private static final class OpeningProject extends NodusProject {
    final List<Throwable> errors = new ArrayList<>();

    OpeningProject(NodusMapPanel panel) {
      super(panel);
    }

    @Override
    void reportProjectOpenError(Throwable error) {
      errors.add(error);
    }
  }

  private static final class HeadlessPanel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final MapBean map = new MapBean(false);

    @Override
    public MapBean getMapBean() {
      return map;
    }
  }
}
