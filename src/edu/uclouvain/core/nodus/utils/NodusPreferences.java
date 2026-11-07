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

import edu.uclouvain.core.nodus.NodusC;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Stores application preferences in {@code .nodus9.properties} in the user's home directory.
 *
 * <p>On first use, an existing {@code .nodus8.properties} file is copied unchanged if the new file
 * does not exist. Subsequent loads and saves use only the new file, leaving Nodus 8 preferences
 * intact. An existing Nodus 9 file, including an empty one, always takes precedence.
 */
public final class NodusPreferences {

  /** Current application preferences filename. */
  private static final String FILE_NAME = ".nodus9.properties";
  private static final String RECENT_PROJECT_PREFIX = "recentProject.";
  private static final int RECENT_PROJECT_LIMIT = 5;

  /** Bridge credentials in this file must be readable only by the current user on POSIX systems. */
  private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS =
      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  /** Prevents instantiation of this persistence utility. */
  private NodusPreferences() {}

  /**
   * Loads preferences, copying the previous version's file when needed.
   *
   * @param home User's home directory
   * @return Saved preferences, or empty preferences when neither version's file exists
   * @throws IOException If the migration or reading an existing file fails
   */
  public static Properties load(Path home) throws IOException {
    Path file = home.resolve(FILE_NAME);
    Path legacyFile = home.resolve(".nodus8.properties");
    if (Files.notExists(file) && Files.isRegularFile(legacyFile)) {
      try {
        Files.copy(legacyFile, file);
      } catch (FileAlreadyExistsException ex) {
        // Another Nodus instance created the file; use it without replacing its preferences.
      }
    }

    Properties properties = new Properties();
    if (Files.notExists(file)) {
      return properties;
    }
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    }
    String lastPath = properties.getProperty(NodusC.PROP_LAST_PATH);
    String lastProject = properties.getProperty(NodusC.PROP_LAST_PROJECT);
    if (getRecentProjects(properties).isEmpty() && lastPath != null && lastProject != null) {
      File project = new File(lastPath, lastProject);
      if (project.isFile()) {
        rememberRecentProject(properties, project.getPath());
      }
    }
    return properties;
  }

  /**
   * Returns remembered project file paths, newest first.
   *
   * @param properties Application preferences containing the recent-project entries
   * @return Remembered project file paths, newest first, without duplicates
   */
  public static List<String> getRecentProjects(Properties properties) {
    Set<String> projects = new LinkedHashSet<>();
    for (int i = 0; i < RECENT_PROJECT_LIMIT; i++) {
      String path = properties.getProperty(RECENT_PROJECT_PREFIX + i);
      if (path != null && !path.isBlank()) {
        projects.add(path);
      }
    }
    return new ArrayList<>(projects);
  }

  /**
   * Promotes a successfully opened project and keeps at most five entries.
   *
   * @param properties Application preferences to update
   * @param projectPath File path of the successfully opened project
   */
  public static void rememberRecentProject(Properties properties, String projectPath) {
    File project = new File(projectPath).getAbsoluteFile();
    String path = project.getPath();
    try {
      // The project filename determines its database name, including when it is a symlink.
      path = new File(project.getParentFile().getCanonicalFile(), project.getName()).getPath();
    } catch (IOException ex) {
      // The original absolute path can still be reopened if the directory becomes available.
    }

    List<String> projects = getRecentProjects(properties);
    projects.remove(path);
    projects.add(0, path);
    for (int i = 0; i < RECENT_PROJECT_LIMIT; i++) {
      String key = RECENT_PROJECT_PREFIX + i;
      if (i < projects.size()) {
        properties.setProperty(key, projects.get(i));
      } else {
        properties.remove(key);
      }
    }
  }

  /**
   * Saves application state to the Nodus 9 file without modifying the previous version's file.
   * Writes a replacement file so readers see complete preferences; its POSIX permissions are
   * owner-only because bridge credentials are stored here.
   *
   * @param home User's home directory
   * @param properties Current application preferences
   * @throws IOException If the preferences cannot be written
   */
  public static void save(Path home, Properties properties) throws IOException {
    FileAttribute<?>[] attributes =
        Files.getFileStore(home).supportsFileAttributeView("posix")
            ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PRIVATE_PERMISSIONS)}
            : new FileAttribute<?>[0];
    Path temporary = Files.createTempFile(home, ".nodus9-", ".tmp", attributes);
    try {
      try (OutputStream out = Files.newOutputStream(temporary)) {
        properties.store(out, null);
      }
      Path destination = home.resolve(FILE_NAME);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Restricts access to an existing preferences file without rewriting its contents. */
  static void protect(Path home) throws IOException {
    Path file = home.resolve(FILE_NAME);
    if (Files.exists(file) && Files.getFileStore(file).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(file, PRIVATE_PERMISSIONS);
    }
  }
}
