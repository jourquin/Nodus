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
import edu.uclouvain.core.nodus.NodusProject;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * This small utility is used to handle concurrent access to a project.
 *
 * @author Bart Jourquin
 */
public class ProjectLocker {

  private static FileChannel channel;

  private static boolean debug = false;

  private static FileLock lock = null;

  private static String lockerFileName;

  /** Default constructor. */
  public ProjectLocker() {}

  /**
   * Creates a lock in the project'directory if possible.
   *
   * @param project A Nodus project.
   * @return False on error.
   */
  public static boolean createLock(NodusProject project) {

    lockerFileName =
        project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
            + project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME)
            + NodusC.TYPE_LOCK;

    FileChannel newChannel = null;
    FileLock newLock = null;

    try {
      newChannel =
          FileChannel.open(
              Path.of(lockerFileName), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

      // Non-blocking lock attempt.
      // Returns null if another process already holds the lock.
      newLock = newChannel.tryLock();

      if (newLock == null) {
        if (debug) {
          System.out.println("Project already locked");
        }

        newChannel.close();
        return false;
      }

      // Store only after successful locking.
      channel = newChannel;
      lock = newLock;

      if (debug) {
        System.out.println("Project locked");
      }

      return true;

    } catch (OverlappingFileLockException e) {
      // The same JVM already holds a lock on this file.
      if (debug) {
        System.out.println("Project already locked by this application");
      }

      closeQuietly(newLock);
      closeQuietly(newChannel);
      return false;

    } catch (IOException | SecurityException e) {
      if (debug) {
        e.printStackTrace();
      }

      closeQuietly(newLock);
      closeQuietly(newChannel);
      return false;
    }
  }

  private static void closeQuietly(FileLock lock) {
    if (lock != null) {
      try {
        lock.close();
      } catch (IOException ignored) {
        // Ignore cleanup failure
      }
    }
  }

  private static void closeQuietly(FileChannel channel) {
    if (channel != null) {
      try {
        channel.close();
      } catch (IOException ignored) {
        // Ignore cleanup failure
      }
    }
  }

  /** Releases the lock for the current user. */
  public static void releaseLock() {
    try {
      if (lock != null) {
        lock.release();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      lock = null;

      try {
        if (channel != null) {
          channel.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        channel = null;
        lockerFileName = null;
      }
    }
  }
}
