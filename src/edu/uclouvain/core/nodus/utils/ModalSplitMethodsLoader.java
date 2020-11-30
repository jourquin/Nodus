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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JOptionPane;

/**
 * Loads the embedded standard model split methods and any valid user defined method found in the
 * jar files stored in the project directory.
 *
 * @author Bart Jourquin
 */
public class ModalSplitMethodsLoader {
  /** A place where to store the loaded methods. */
  private static LinkedList<ModalSplitMethod> availableModalSplitMethods = new LinkedList<>();

  /**
   * Returns the available modal split methods.
   * 
   * @return A linked list that contains the available modal split methods.
   */
  public static LinkedList<ModalSplitMethod> getAvailableModalSplitMethods() {
    return availableModalSplitMethods;
  }

  private File directory;

  private String[] standardModalSpliMethods = {"MultinomialLogit", "Proportional", "Abraham"};

  private NodusProject nodusProject;

  private Class<?>[] paramTypes = {NodusProject.class};

  /**
   * Loads all the available modal split methods.
   *
   * @param nodusProject The Nodus project the plugins must be loaded for.
   */
  public ModalSplitMethodsLoader(NodusProject nodusProject) {

    this.nodusProject = nodusProject;

    availableModalSplitMethods.clear();

    // Load the standard classes
    for (String standardModalSpliMethod : standardModalSpliMethods) {

      try {
        Class<?> c =
            Class.forName(
                "edu.uclouvain.core.nodus.compute.assign.modalsplit." + standardModalSpliMethod);
        Constructor<?> cons = c.getConstructor(paramTypes);
        Object o = cons.newInstance(nodusProject);
        if (o instanceof ModalSplitMethod) {
          availableModalSplitMethods.add((ModalSplitMethod) o);
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    String projectDirectory = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    directory = new File(projectDirectory);

    if (!directory.exists()) {
      return;
    }

    if (directory.isDirectory()) {
      loadJars();
    } else {
      System.err.println(directory + " is not a directory");
    }
  }

  /** Load all the jars in the directory and looks for modal split methods. */
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

        loadUserDefinedModalSplitMethods(jarFiles[i].getAbsolutePath());

      } catch (FileNotFoundException ex) {
        System.out.println(ex.toString());
      } catch (IOException ex) {
        System.out.println(ex.toString());
      }
    }
  }

  /** Searches for instances of ModalSplitMethod in a jar. */
  private void loadUserDefinedModalSplitMethods(String pathToJar) {

    String className;
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
        className = je.getName().substring(0, je.getName().length() - 6);
        className = className.replace('/', '.');
        Class<?> c = cl.loadClass(className);

        Constructor<?> cons = null;
        try {
          cons = c.getConstructor(paramTypes);

        } catch (NoSuchMethodException e1) {
          continue;
        } catch (SecurityException e1) {
          e1.printStackTrace();
        }

        Object o = null;

        try {
          if (nodusProject != null) {
            o = cons.newInstance(nodusProject);
          }
        } catch (InstantiationException e1) {
          new NodusConsole();
          javax.swing.SwingUtilities.invokeLater(
              new Runnable() {
                public void run() {
                  e1.printStackTrace();
                }
              });
        } catch (IllegalAccessException e1) {
          new NodusConsole();
          javax.swing.SwingUtilities.invokeLater(
              new Runnable() {
                public void run() {
                  e1.printStackTrace();
                }
              });
        } catch (IllegalArgumentException e1) {
          new NodusConsole();
          javax.swing.SwingUtilities.invokeLater(
              new Runnable() {
                public void run() {
                  e1.printStackTrace();
                }
              });
        } catch (InvocationTargetException e1) {
          String s = "The " + c.getName() + " plugin is not compatible with this version of Nodus.";
          JOptionPane.showMessageDialog(null, s, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
          continue;
        }

        if (o instanceof ModalSplitMethod) {
          availableModalSplitMethods.add((ModalSplitMethod) o);
        }
      }
      jarFile.close();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns the ModalSplitMethod which name is given as parameter.
   *
   * @param methodName String
   * @return ModalSplitMethod
   */
  public static ModalSplitMethod getModalSplitMethod(String methodName) {
    LinkedList<ModalSplitMethod> ll = getAvailableModalSplitMethods();
    Iterator<ModalSplitMethod> it = ll.iterator();

    while (it.hasNext()) {
      ModalSplitMethod modalSplitMethod = it.next();
      // Is this the method we are looking for ?
      if (modalSplitMethod.getName().equals(methodName)) {
        return modalSplitMethod;
      }
    }

    System.err.println("Modal split method not found. This should not be possible!");
    return null;
  }
}
