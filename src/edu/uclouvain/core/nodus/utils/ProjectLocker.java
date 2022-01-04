/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

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

  /**
   * Creates a lock in the project'directory if possible.
   *
   * @param project A Nodus project.
   * @return False on error.
   */
  @SuppressWarnings("resource")
  public static boolean createLock(NodusProject project) {

    // Is there a lock file?
    lockerFileName =
        project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
            + project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME)
            + NodusC.TYPE_LOCK;
    File f = new File(lockerFileName);
    if (f.exists()) {
      // Is project locked?
      try {

        RandomAccessFile rf = new RandomAccessFile(lockerFileName, "rw");
        FileChannel channel = rf.getChannel();
        if (channel.tryLock() == null) {
          if (debug) {
            System.out.println("Project already locked");
          }
          channel.close();
          rf.close();
          return false;
        }
        channel.close();
        rf.close();

      } catch (FileNotFoundException e) {
        // If lock belongs to another user (permission denied)
        if (debug) {
          System.out.println("Permission denied");
        }
        return false;
      } catch (IOException e) {
        e.printStackTrace();
        return false;
      }
    }

    // Try to create a new lock
    try {
      channel = new RandomAccessFile(lockerFileName, "rw").getChannel();
      lock = channel.lock();
      if (debug) {
        System.out.println("Project locked");
      }
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /** Releases the lock for the current user. */
  public static void releaseLock() {
    if (lock != null) {
      try {

        lock.release();
        lock = null;
        channel.close();
        
        File f = new File(lockerFileName);
        f.delete();

        if (debug) {
          System.out.println("Project lock released");
        }

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
