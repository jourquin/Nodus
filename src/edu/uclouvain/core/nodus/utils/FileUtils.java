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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;

/**
 * A small collection of convenient static methods for file handling.
 *
 * @author Bart Jourquin
 */
public class FileUtils {

  /** Default constructor. */
  public FileUtils() {}

  /**
   * Copy a file to another.
   *
   * @param from The name of the source file, including its path.
   * @param to The name of the destination file, including its path.
   * @return True on success.
   */
  public static boolean copyFile(String from, String to) {
    try {
      Files.copy(
          Path.of(from),
          Path.of(to),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      return true;
    } catch (IOException e) {
      System.out.println(e.toString());
      return false;
    }
  }

  /**
   * Deletes a file or a directory (with its content).
   *
   * @param file The file or directory to delete.
   * @throws IOException On error
   */
  public static void deleteFile(File file) throws IOException {

    if (file == null) {
      return;
    }

    Path root = file.toPath();

    // Keep the previous behavior: deleting a non-existing file is a no-op.
    if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }

    Files.walkFileTree(
        root,
        new SimpleFileVisitor<Path>() {

          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(path);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException exception)
              throws IOException {
            if (exception != null) {
              throw exception;
            }

            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Returns the file to which the passed Windows shortcut corresponds to.
   *
   * @param shortcut The Windows shortcut.
   * @return File The file the Windows shortcut refers to.
   */
  public static File getWindowsRealFile(File shortcut) {

    WindowsShortcut ws;
    try {
      ws = new WindowsShortcut(shortcut.getCanonicalFile());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }

    return new File(ws.getRealFilename());
  }

  /**
   * Returns true if the passed file is a Windows shortcut (valid .lnk file).
   *
   * @param fileToTest A file.
   * @return True if this file corresponds to a Windows shortcut.
   * @throws IOException On error
   */
  public static boolean isWindowsShortcut(File fileToTest) throws IOException {
    return WindowsShortcut.isPotentialValidLink(fileToTest);
  }
}
