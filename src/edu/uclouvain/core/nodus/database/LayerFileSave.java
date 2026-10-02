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

package edu.uclouvain.core.nodus.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Replaces a layer's companion files, such as {@code .shp}, {@code .shx}, and {@code .dbf}, with
 * recovery copies available if installation fails or the application is interrupted.
 *
 * <p>Use one instance per save attempt in a try-with-resources block. Register each changed file
 * with {@link #stage(String)}, write it completely, close its writer, and then call {@link
 * #commit()} once. Original files remain untouched during staging. Commit backs up all affected
 * originals before installing any replacement; close rolls back an unsuccessful installation or
 * removes recovery files after success. Files not registered for staging are not part of this save.
 *
 * <pre>{@code
 * try (LayerFileSave save = new LayerFileSave(projectDirectory, "links")) {
 *   Path attributes = save.stage("links.dbf");
 *   // Write and close the complete DBF at attributes before committing.
 *   save.commit();
 * }
 * }</pre>
 *
 * <p>The recovery directory is {@code .<layer>.nodus-save} inside the project directory. It
 * contains staged files, {@code <filename>.bak} copies, and an {@code originals.properties}
 * manifest recording which destinations existed. Marker files describe the recovery action:
 *
 * <ul>
 *   <li>No {@code ready} marker: installation has not begun; temporary files can be discarded.
 *   <li>{@code ready} without {@code committed}: restore originals and remove destinations that did
 *       not exist before the save. Keep recovery copies if restoration fails.
 *   <li>{@code committed}: installation or rollback completed; only cleanup remains.
 * </ul>
 *
 * <p>Call {@link #recover(Path, String)} before reading a layer after an interruption. Construction
 * also performs recovery before beginning another save. This class acquires no locks and is not
 * thread-safe: the caller must exclude other readers, writers, and recovery attempts for the layer,
 * using the project lock and appropriate synchronization within the application.
 *
 * <p>Replacement is recoverable, not atomic across the complete file set. Individual moves request
 * filesystem atomicity when available, but fall back to ordinary replacement otherwise. No files or
 * directories are explicitly synced to durable storage; this is not a power-loss guarantee or a
 * replacement for project backups. Sufficient space is needed for staged files and original copies.
 */
public class LayerFileSave implements AutoCloseable {
  /** Existing project directory containing the destination files. */
  private final Path directory;

  /** Staging and recovery directory for this layer and save attempt. */
  private final Path journal;

  /** Registered sidecar basenames, in installation order. */
  private final List<String> names = new ArrayList<>();

  /**
   * Recovers any previous attempt for this layer and creates a fresh staging directory.
   *
   * @param directory existing project directory containing the layer files
   * @param table layer basename without a sidecar extension or directory components
   * @throws IOException if recovery or creation of the staging directory fails
   * @throws IllegalArgumentException if {@code table} contains directory components
   */
  public LayerFileSave(Path directory, String table) throws IOException {
    this.directory = directory;
    journal = journal(directory, table);
    recover(directory, table);
    Files.createDirectory(journal);
  }

  /**
   * Registers a changed sidecar and returns the path at which the caller must write it.
   *
   * <p>This method does not create the file. Register each basename only once and only before
   * calling {@link #commit()}; fully write and close every registered file before committing. Use
   * actual sidecar names, such as {@code links.dbf}, rather than the journal's reserved metadata
   * filenames.
   *
   * @param name destination basename including its extension, without directory components
   * @return staging path inside this save's recovery directory
   * @throws IllegalArgumentException if {@code name} contains directory components
   */
  public Path stage(String name) {
    if (!Path.of(name).getFileName().toString().equals(name)) {
      throw new IllegalArgumentException("Expected a sidecar basename");
    }
    names.add(name);
    return journal.resolve(name);
  }

  /**
   * Installs every registered sidecar in registration order and marks the save as committed.
   *
   * <p>All staged files must be regular files. Existing destinations are copied before the manifest
   * and {@code ready} marker are written; installation begins only afterwards. The {@code
   * committed} marker is written only after every replacement succeeds. Recovery files remain until
   * close.
   *
   * <p>Call at most once per instance. If this method throws, some destinations may already have
   * been replaced: the enclosing try-with-resources block must call {@link #close()} to restore
   * them. If execution is interrupted before close, the next {@link #recover(Path, String)}
   * performs that work.
   *
   * @throws IOException if validation, backup, manifest writing, installation, or commit marking
   *     fails
   */
  public void commit() throws IOException {
    Properties originals = new Properties();
    for (String name : names) {
      if (!Files.isRegularFile(journal.resolve(name))) {
        throw new IOException("Missing staged sidecar: " + name);
      }
      Path target = directory.resolve(name);
      boolean exists = Files.exists(target);
      if (exists) {
        if (!Files.isRegularFile(target)) {
          throw new IOException("Not a regular file: " + target);
        }
        Files.copy(target, journal.resolve(name + ".bak"), StandardCopyOption.COPY_ATTRIBUTES);
      }
      originals.setProperty(name, Boolean.toString(exists));
    }
    try (OutputStream output = Files.newOutputStream(journal.resolve("originals.properties"))) {
      originals.store(output, "Layer save recovery files");
    }
    Files.createFile(journal.resolve("ready"));
    for (String name : names) {
      install(journal.resolve(name), directory.resolve(name));
    }
    Files.createFile(journal.resolve("committed"));
  }

  /**
   * Moves one fully written staging file into place, replacing the destination if it exists.
   *
   * <p>Requests an atomic move first and retries with an ordinary move if the filesystem does not
   * support it. This hook allows tests to inject failures between sidecar installations. Overrides
   * must report failures so that the enclosing save can recover the original file set.
   *
   * @param staged source file in the recovery directory
   * @param target destination file in the project directory
   * @throws IOException if the file cannot be installed
   */
  protected void install(Path staged, Path target) throws IOException {
    try {
      Files.move(
          staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Restores an interrupted save or removes temporary files from a completed or unstarted one.
   *
   * <p>Does nothing if no recovery directory exists. Call before loading layer files, while holding
   * the same exclusive access required for saving. A failed restoration retains the backups so the
   * operation can be retried after the underlying file-access problem is resolved; do not load a
   * potentially mixed file set or delete the recovery directory after such a failure.
   *
   * @param directory project directory containing the layer files
   * @param table layer basename without a sidecar extension or directory components
   * @throws IOException if restoration or journal cleanup fails
   * @throws IllegalArgumentException if {@code table} contains directory components
   */
  public static void recover(Path directory, String table) throws IOException {
    Path journal = journal(directory, table);
    if (Files.exists(journal)) {
      recoverJournal(directory, journal);
    }
  }

  /** Derives the stable journal path used to find this layer's interrupted save on a later open. */
  private static Path journal(Path directory, String table) {
    if (!Path.of(table).getFileName().toString().equals(table)) {
      throw new IllegalArgumentException("Expected a layer basename");
    }
    return directory.resolve("." + table + ".nodus-save");
  }

  /**
   * Applies the marker protocol to an existing journal, then removes it.
   *
   * <p>Restoration copies backups instead of moving them, so a failure leaves the complete recovery
   * set available for retry. The commit marker also marks a completed rollback: writing it before
   * cleanup ensures an interrupted cleanup does not try to restore from already deleted backups.
   */
  private static void recoverJournal(Path directory, Path journal) throws IOException {
    if (Files.exists(journal.resolve("ready")) && !Files.exists(journal.resolve("committed"))) {
      Properties originals = new Properties();
      try (InputStream input = Files.newInputStream(journal.resolve("originals.properties"))) {
        originals.load(input);
      }
      for (String name : originals.stringPropertyNames()) {
        if (!Path.of(name).getFileName().toString().equals(name)) {
          throw new IOException("Invalid sidecar in recovery journal: " + name);
        }
        if (Boolean.parseBoolean(originals.getProperty(name))) {
          Files.copy(
              journal.resolve(name + ".bak"),
              directory.resolve(name),
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.COPY_ATTRIBUTES);
        } else {
          Files.deleteIfExists(directory.resolve(name));
        }
      }
      // Mark rollback completed before deleting backups; interruption during cleanup is safe.
      Files.createFile(journal.resolve("committed"));
    }
    try (Stream<Path> files = Files.walk(journal)) {
      // Keep the commit marker until all backups have been removed.
      List<Path> paths =
          files.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
      for (Path path : paths) {
        if (!path.equals(journal) && !path.equals(journal.resolve("committed"))) {
          Files.delete(path);
        }
      }
    }
    Files.deleteIfExists(journal.resolve("committed"));
    Files.delete(journal);
  }

  /**
   * Finishes this save attempt by rolling back uncommitted installation or cleaning its journal.
   *
   * <p>If commit was never started, only staging files are discarded. If installation failed, the
   * original files are restored, with restoration errors propagated and recovery copies retained.
   * After successful commit, cleanup errors are logged rather than reported as save failures; a
   * later recovery attempt retries cleanup without undoing the saved files.
   *
   * <p>Call once, normally through try-with-resources. Do not reuse the instance after closing it.
   *
   * @throws IOException if recovery or cleanup of an uncommitted attempt fails
   */
  @Override
  public void close() throws IOException {
    if (Files.exists(journal.resolve("committed"))) {
      try {
        recoverJournal(directory, journal);
      } catch (IOException cleanup) {
        // Installation already succeeded. A later open/save retries only journal cleanup.
        System.err.println(
            "Layer saved; could not clean recovery directory " + journal + ": " + cleanup);
      }
    } else {
      recoverJournal(directory, journal);
    }
  }
}
