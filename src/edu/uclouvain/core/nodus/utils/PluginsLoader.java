/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the plugins that are found in a given directory.
 *
 * @author Bart Jourquin
 */
public class PluginsLoader {
  /* A place where to store the loaded plugins */
  private static LinkedList<Class<NodusPlugin>> availablePlugins =
      new LinkedList<Class<NodusPlugin>>();

  /**
   * Returns a list of available plugin's.
   *
   * @return A LinkedList of available pugin's.
   */
  public LinkedList<Class<NodusPlugin>> getAvailablePlugins() {
    return availablePlugins;
  }

  private File directory;

  /**
   * Loads all the available plugins.
   *
   * @param pluginDir The path to the directory that contains the Nodus project.
   */
  public PluginsLoader(String pluginDir) {

    availablePlugins.clear();

    directory = new File(pluginDir);

    if (!directory.exists()) {
      return;
    }

    if (directory.isDirectory()) {
      loadJars();
    } else {
      System.err.println(directory + " is not a directory");
    }
  }

  /** Load all the jars in the directory and looks for plugins. */
  private void loadJars() {

    File[] jarFiles =
        directory.listFiles(
            new FileFilter() {
              @Override
              public boolean accept(File pathname) {
                boolean accept = false;
                accept = pathname.getName().endsWith(".jar");

                if (!accept) { // Accept also Windows shortcuts...
                  accept = pathname.getName().endsWith(".jar.lnk");
                }

                return accept;
              }
            });

    if (jarFiles == null) {
      return;
    }

    for (int i = 0; i < jarFiles.length; i++) {

      try {
        // Resolve the case of Windows shortcuts
        if (FileUtils.isWindowsShortcut(jarFiles[i])) {
          jarFiles[i] = FileUtils.getWindowsRealFile(jarFiles[i]);
          if (jarFiles[i] == null) {
            continue;
          }
        }

        // Test existence (could be a dead link...)
        if (!jarFiles[i].exists()) {
          continue;
        }

        // Load jar file
        JarLoader jl = new JarLoader(jarFiles[i].getAbsolutePath());
        jl.loadJarClasses();

        loadPlugins(jarFiles[i].getAbsolutePath());

      } catch (FileNotFoundException ex) {
        System.out.println(ex.toString());
      } catch (IOException ex) {
        System.out.println(ex.toString());
      }
    }
  }

  /**
   * Searches for instances of NodusPlugin in a jar.
   *
   * @param pathToJar Path to jar file
   */
  @SuppressWarnings("unchecked")
  private void loadPlugins(String pathToJar) {

    try {
      JarFile jarFile = new JarFile(pathToJar);
      Enumeration<JarEntry> e = jarFile.entries();

      URL[] urls = {new URL("jar:file:" + pathToJar + "!/")};
      URLClassLoader cl = URLClassLoader.newInstance(urls);

      while (e.hasMoreElements()) {
        JarEntry je = (JarEntry) e.nextElement();
        if (je.isDirectory() || !je.getName().endsWith(".class")) {
          continue;
        }

        // -6 because of .class
        String className = je.getName().substring(0, je.getName().length() - 6);
        className = className.replace('/', '.');
        Class<?> loadedClass = null;
        try {
          loadedClass = cl.loadClass(className);
        } catch (Error e1) {
          System.err.println(className);
        } catch (Exception e1) {
          System.err.println(className);
        }

        if (loadedClass != null) {
          try {
            Constructor<?> cons = loadedClass.getConstructor();
            Object o = cons.newInstance();
            if (o instanceof NodusPlugin) {
              availablePlugins.add((Class<NodusPlugin>) loadedClass);
            }
          } catch (Exception ex) {
            /*
             * The jar may contain classes that are not plugins. Do nothing.
             * This exception is thrown by cons.newInstance().
             */
          }
        }
      }
      jarFile.close();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
