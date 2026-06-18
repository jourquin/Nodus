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
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

  /** Project-scoped class paths that define user modal-split methods. */
  private static LinkedList<PluginClassPath> modalSplitClassPaths = new LinkedList<>();

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

    disposeAvailableModalSplitMethods();

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
    PluginClassPath classPath = null;

    try {
      classPath = new PluginClassPath(directory, ModalSplitMethodsLoader.class.getClassLoader());

      int nbMethodsBefore = availableModalSplitMethods.size();

      for (File jarFile : classPath.getJarFiles()) {
        loadUserDefinedModalSplitMethods(jarFile, classPath);
      }

      /*
       * Keep the class loader alive only if it actually loaded at least one
       * project-scoped modal-split method instance.
       */
      if (availableModalSplitMethods.size() > nbMethodsBefore) {
        modalSplitClassPaths.add(classPath);
        classPath = null;
      }
    } catch (IOException ex) {
      System.out.println(ex.toString());
    } finally {
      if (classPath != null) {
        classPath.close();
      }
    }
  }

  /** Searches for instances of ModalSplitMethod in a jar. */
  private void loadUserDefinedModalSplitMethods(File pathToJar, PluginClassPath classPath) {

    String className;
    try (JarFile jarFile = new JarFile(pathToJar)) {
      Enumeration<JarEntry> e = jarFile.entries();

      while (e.hasMoreElements()) {
        JarEntry je = (JarEntry) e.nextElement();
        if (je.isDirectory() || !je.getName().endsWith(".class")) {
          continue;
        }
        // -6 because of .class
        className = je.getName().substring(0, je.getName().length() - 6);
        className = className.replace('/', '.');
        Class<?> c = classPath.loadClass(className);

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

  /** Dispose resources. */
  public static void disposeAvailableModalSplitMethods() {
    for (ModalSplitMethod method : availableModalSplitMethods) {
      if (method != null) {
        method.dispose();
      }
    }

    availableModalSplitMethods.clear();

    /*
     * Close the project-scoped modal-split class loaders after method.dispose(),
     * because dispose() may still need classes/resources from the plugin.
     */
    for (PluginClassPath classPath : modalSplitClassPaths) {
      if (classPath != null) {
        classPath.close();
      }
    }

    modalSplitClassPaths.clear();
  }
}
