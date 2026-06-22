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
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A closeable classpath for one plugin scope.
 *
 * <p>One instance should be kept alive as long as the plugins loaded from it are alive: -
 * application plugins: until Nodus exits; - project plugins: until the project is closed; -
 * modal-split plugins: until the project is closed.
 *
 * @author Bart Jourquin
 */
public final class PluginClassPath implements AutoCloseable {

  private final ArrayList<File> jarFiles;
  private URLClassLoader classLoader;
  
  /**
   * Creates a new plugin class path from the given directory.
   *
   * @param directoryName the name of the directory containing the plugin jar files
   * @throws IOException if an I/O error occurs while collecting jar files
   */
  public PluginClassPath(String directoryName) throws IOException {
    this(new File(directoryName), PluginClassPath.class.getClassLoader());
  }

  /**
   * Creates a new plugin class path from the given directory and parent class loader.
   *
   * @param directory the directory containing the plugin jar files
   * @param parent the parent class loader to delegate to
   * @throws IOException if an I/O error occurs while collecting jar files
   */
  public PluginClassPath(File directory, ClassLoader parent) throws IOException {
    jarFiles = collectJarFiles(directory);

    URL[] urls = new URL[jarFiles.size()];
    for (int i = 0; i < jarFiles.size(); i++) {
      urls[i] = jarFiles.get(i).toURI().toURL();
    }

    classLoader = URLClassLoader.newInstance(urls, parent);
  }

  /**
   * Returns an unmodifiable list of the jar files in this plugin class path.
   *
   * @return an unmodifiable list of the jar files in this plugin class path
   */
  public List<File> getJarFiles() {
    return Collections.unmodifiableList(jarFiles);
  }

  /**
   * Loads a class with the given name from the plugin class path.
   *
   * @param className the fully qualified name of the class to load
   * @return the Class object representing the loaded class
   * @throws ClassNotFoundException if the class cannot be found in the plugin class path
   * @throws IllegalStateException if the plugin class path has already been closed
   */
  public Class<?> loadClass(String className) throws ClassNotFoundException {
    if (classLoader == null) {
      throw new IllegalStateException("Plugin class path is already closed");
    }
    return classLoader.loadClass(className);
  }

  @Override
  public void close() {
    try {
      if (classLoader != null) {
        classLoader.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      classLoader = null;
      jarFiles.clear();
    }
  }

  private static ArrayList<File> collectJarFiles(File directory) throws IOException {
    ArrayList<File> jars = new ArrayList<>();

    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      return jars;
    }

    File[] files =
        directory.listFiles(
            new FileFilter() {
              @Override
              public boolean accept(File pathname) {
                String name = pathname.getName();
                return name.endsWith(".jar") || name.endsWith(".jar.lnk");
              }
            });

    if (files == null) {
      return jars;
    }

    Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

    for (File file : files) {
      File resolvedFile = file;

      if (FileUtils.isWindowsShortcut(file)) {
        resolvedFile = FileUtils.getWindowsRealFile(file);
      }

      if (resolvedFile != null && resolvedFile.exists() && resolvedFile.isFile()) {
        jars.add(resolvedFile.getCanonicalFile());
      }
    }

    return jars;
  }
}
