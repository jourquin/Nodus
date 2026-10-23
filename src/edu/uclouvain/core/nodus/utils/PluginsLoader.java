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

import edu.uclouvain.core.nodus.NodusPlugin;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the plugins that are found in a given directory.
 *
 * @author Bart Jourquin
 */
public class PluginsLoader implements AutoCloseable {

  /* A place where to store the plugins found by this loader instance. */
  private final LinkedList<Class<NodusPlugin>> availablePlugins =
      new LinkedList<Class<NodusPlugin>>();

  private final PluginClassPath pluginClassPath;

  /**
   * Returns the plugins found by this loader instance.
   *
   * @return A LinkedList of available plugins.
   */
  public LinkedList<Class<NodusPlugin>> getAvailablePlugins() {
    return availablePlugins;
  }

  // private File directory;

  /**
   * Loads all the available plugins.
   *
   * @param pluginDir The path to the directory that contains the Nodus project.
   */
  public PluginsLoader(String pluginDir) {
    this(pluginDir, Collections.emptySet());
  }

  /**
   * Loads plugins without inspecting or resolving classes from rejected archives.
   *
   * @param pluginDir directory containing plugin JARs
   * @param excludedJars canonical files skipped during modal-plugin preparation
   */
  public PluginsLoader(String pluginDir, Collection<File> excludedJars) {
    PluginClassPath classPath = null;

    File directory = new File(pluginDir);
    if (!directory.exists()) {
      pluginClassPath = null;
      return;
    }

    if (!directory.isDirectory()) {
      System.err.println(directory + " is not a directory");
      pluginClassPath = null;
      return;
    }

    try {
      classPath = new PluginClassPath(directory, PluginsLoader.class.getClassLoader(), excludedJars);
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    pluginClassPath = classPath;

    if (pluginClassPath != null) {
      loadJars();
    }
  }

  /** Load all the jars in the directory and looks for plugins. */
  private void loadJars() {
    if (pluginClassPath == null) {
      return;
    }

    for (File jarFile : pluginClassPath.getJarFiles()) {
      loadPlugins(jarFile);
    }
  }

  /**
   * Searches for instances of NodusPlugin in a jar.
   *
   * @param pathToJar Path to jar file
   */
  @SuppressWarnings("unchecked")
  private void loadPlugins(File pathToJar) {
    try (JarFile jarFile = new JarFile(pathToJar)) {
      Enumeration<JarEntry> e = jarFile.entries();

      while (e.hasMoreElements()) {
        JarEntry je = e.nextElement();

        if (je.isDirectory() || !je.getName().endsWith(".class")) {
          continue;
        }

        String className = je.getName().substring(0, je.getName().length() - 6);
        className = className.replace('/', '.');

        try {
          Class<?> loadedClass = pluginClassPath.loadClass(className);
          if (NodusPlugin.class.isAssignableFrom(loadedClass)
              && loadedClass != NodusPlugin.class
              && Modifier.isPublic(loadedClass.getModifiers())
              && !Modifier.isAbstract(loadedClass.getModifiers())) {
            /*
             * Make sure the plugin can be instantiated later by NodusMapPanel,
             * but do not create an instance here.
             */
            loadedClass.getConstructor();
            if (!availablePlugins.contains(loadedClass)) {
              availablePlugins.add((Class<NodusPlugin>) loadedClass);
            }
          }
        } catch (NoSuchMethodException ex) {
          // Helper classes and plugins without a public no-argument constructor are ignored.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
          System.err.println("Could not inspect plugin class " + className + " in " + pathToJar);
          error.printStackTrace();
        }
      }
    } catch (IOException e) {
      System.err.println("Could not inspect plugin jar " + pathToJar);
      e.printStackTrace();
    }
  }

  @Override
  public void close() {
    availablePlugins.clear();

    if (pluginClassPath != null) {
      pluginClassPath.close();
    }
  }
}
