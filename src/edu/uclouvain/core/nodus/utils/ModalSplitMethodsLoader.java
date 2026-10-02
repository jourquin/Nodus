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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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

  private String[] standardModalSpliMethods = {
    "MultinomialLogit", "Proportional", "MultinomialProbit"
  };

  private NodusProject nodusProject;

  private final Consumer<String> incompatibilityReporter;

  /** Confirmation callback receiving the original JAR and the proposed Nodus 8 backup. */
  private final BiPredicate<File, File> upgradeConfirmation;

  private Class<?>[] paramTypes = {NodusProject.class};

  /**
   * Loads all the available modal split methods.
   *
   * @param nodusProject The Nodus project the plugins must be loaded for.
   */
  public ModalSplitMethodsLoader(NodusProject nodusProject) {
    this(
        nodusProject,
        ModalSplitMethodsLoader::showIncompatibility,
        ModalSplitMethodsLoader::confirmUpgrade);
  }

  /**
   * Loads modal methods with a caller-supplied destination for binary incompatibility warnings.
   *
   * <p>The reporter is called synchronously when a plugin cannot link to its required classes.
   * Supplying a reporter replaces the default terminal output and warning dialog for these
   * failures. Automatic upgrades are declined by this overload, allowing automated callers to
   * collect diagnostics without opening Swing dialogs or changing project files.
   *
   * @param nodusProject project whose modal plugins should be loaded
   * @param incompatibilityReporter nonnull consumer of user-readable incompatibility messages
   */
  public ModalSplitMethodsLoader(
      NodusProject nodusProject, Consumer<String> incompatibilityReporter) {
    this(
        nodusProject,
        incompatibilityReporter,
        (jar, backup) -> {
          incompatibilityReporter.accept(
              jar.getName()
                  + " uses the Nodus 8 modal-choice API.\n"
                  + "Update its modal-choice imports to "
                  + "edu.uclouvain.core.nodus.compute.modalsplit,\n"
                  + "recompile it against nodus9.jar and replace the old plugin JAR.");
          return false;
        });
  }

  /**
   * Loads modal methods with explicit control over legacy-plugin migration and error reporting.
   *
   * <p>The confirmation callback is invoked before any project classes load. Returning true permits
   * conversion of references to the unchanged modal API, saving the original as {@code
   * plugin.jar.nodus8}. Returning false leaves the archive unchanged and skips it. A current plugin
   * never triggers confirmation; unsupported archives are reported without being changed.
   *
   * @param nodusProject Project whose modal plugins should be loaded
   * @param incompatibilityReporter Nonnull destination for incompatibility or migration errors
   * @param upgradeConfirmation Nonnull callback receiving the original JAR and proposed backup
   */
  public ModalSplitMethodsLoader(
      NodusProject nodusProject,
      Consumer<String> incompatibilityReporter,
      BiPredicate<File, File> upgradeConfirmation) {
    this.nodusProject = nodusProject;
    this.incompatibilityReporter = Objects.requireNonNull(incompatibilityReporter);
    this.upgradeConfirmation = Objects.requireNonNull(upgradeConfirmation);

    disposeAvailableModalSplitMethods();

    // Load the standard classes
    for (String standardModalSpliMethod : standardModalSpliMethods) {

      try {
        Class<?> c =
            Class.forName("edu.uclouvain.core.nodus.compute.modalsplit." + standardModalSpliMethod);
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

      // Finish approved conversions before this loader can cache any classes or open any JARs.
      Set<File> skipped = upgradeLegacyPlugins(classPath);
      for (File jarFile : classPath.getJarFiles()) {
        if (!skipped.contains(jarFile)) {
          loadUserDefinedModalSplitMethods(jarFile, classPath);
        }
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

  /** Detects the old modal API and offers migration once per archive, before class loading. */
  private Set<File> upgradeLegacyPlugins(PluginClassPath classPath) {
    Set<File> skipped = new HashSet<>();
    for (File jar : classPath.getJarFiles()) {
      try {
        if (!ModalPluginUpgrader.needsUpgrade(jar.toPath())) {
          continue;
        }
        File backup = ModalPluginUpgrader.backup(jar.toPath()).toFile();
        if (backup.exists()) {
          throw new IOException(
              "The backup already exists: " + backup + ". It will not be overwritten.");
        }
        if (upgradeConfirmation.test(jar, backup)) {
          ModalPluginUpgrader.upgrade(jar.toPath(), classPath.getJarFiles());
        } else {
          skipped.add(jar);
          // Declining the interactive prompt needs no second dialog.
        }
      } catch (IOException | SecurityException ex) {
        skipped.add(jar);
        incompatibilityReporter.accept(
            jar.getName() + " could not be upgraded.\n" + ex.getMessage());
      }
    }
    return skipped;
  }

  /** Asks permission on the EDT; closing the prompt or running headlessly leaves the JAR alone. */
  private static boolean confirmUpgrade(File jar, File backup) {
    if (GraphicsEnvironment.isHeadless()) {
      return false;
    }
    boolean[] accepted = {false};
    Runnable prompt =
        () -> {
          I18n i18n = Environment.getI18n();
          String message =
              i18n.get(
                      ModalSplitMethodsLoader.class,
                      "UpgradeMessage",
                      "This plugin uses the Nodus 8 modal-choice API.\n"
                          + "Upgrade it for Nodus 9 without recompiling?\n")
                  + "\n"
                  + jar
                  + "\n\n"
                  + i18n.get(
                      ModalSplitMethodsLoader.class, "UpgradeBackup", "Original JAR saved as:")
                  + "\n"
                  + backup;
          String upgrade = i18n.get(ModalSplitMethodsLoader.class, "Upgrade", "Upgrade");
          String skip = i18n.get(ModalSplitMethodsLoader.class, "SkipUpgrade", "Skip plugin");
          accepted[0] =
              JOptionPane.showOptionDialog(
                      null,
                      message,
                      NodusC.APPNAME,
                      JOptionPane.YES_NO_OPTION,
                      JOptionPane.QUESTION_MESSAGE,
                      null,
                      new String[] {upgrade, skip},
                      upgrade)
                  == 0;
        };
    if (SwingUtilities.isEventDispatchThread()) {
      prompt.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(prompt);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (InvocationTargetException ex) {
        System.err.println("Unable to display the plugin upgrade prompt: " + ex.getCause());
      }
    }
    return accepted[0];
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
          if (e1.getCause() instanceof LinkageError) {
            reportIncompatiblePlugin(pathToJar, (LinkageError) e1.getCause());
            continue;
          }
          String s = "The " + c.getName() + " plugin is not compatible with this version of Nodus.";
          JOptionPane.showMessageDialog(null, s, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
          continue;
        }

        if (o instanceof ModalSplitMethod) {
          availableModalSplitMethods.add((ModalSplitMethod) o);
        }
      }
    } catch (LinkageError error) {
      reportIncompatiblePlugin(pathToJar, error);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Reports an incompatible JAR while allowing other modal plugins and the project to load. */
  private void reportIncompatiblePlugin(File jar, LinkageError error) {
    String reason = error.toString().replace('/', '.');
    String message = jar.getName() + " could not be loaded by " + NodusC.APPNAME + ".\n";
    if (reason.contains("edu.uclouvain.core.nodus.compute.assign.modalsplit")) {
      message +=
          "Update its modal-choice imports to edu.uclouvain.core.nodus.compute.modalsplit,\n"
              + "recompile it against nodus9.jar and replace the old plugin JAR.";
    } else {
      message += "Recompile the plugin and check its dependencies.\n" + reason;
    }
    incompatibilityReporter.accept(message);
  }

  /** Presents incompatibility diagnostics during ordinary interactive project loading. */
  private static void showIncompatibility(String message) {
    System.err.println(message);
    if (!GraphicsEnvironment.isHeadless()) {
      JOptionPane.showMessageDialog(null, message, NodusC.APPNAME, JOptionPane.WARNING_MESSAGE);
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

  /**
   * Disposes every loaded method, then closes its project-scoped class path.
   *
   * <p>Detach both registries before invoking plugin code so repeated or reentrant cleanup cannot
   * dispose the same objects again. Class paths remain open until all method disposers have been
   * attempted, since those disposers may still need plugin classes or resources. Runtime and
   * linkage failures are reported individually without interrupting the remaining cleanup or the
   * caller's project cleanup. Fatal VM errors are not suppressed.
   */
  public static void disposeAvailableModalSplitMethods() {
    ModalSplitMethod[] methods = availableModalSplitMethods.toArray(new ModalSplitMethod[0]);
    PluginClassPath[] classPaths = modalSplitClassPaths.toArray(new PluginClassPath[0]);
    availableModalSplitMethods.clear();
    modalSplitClassPaths.clear();

    try {
      for (ModalSplitMethod method : methods) {
        if (method != null) {
          try {
            method.dispose();
          } catch (RuntimeException | LinkageError failure) {
            System.err.println(
                "Could not dispose modal split method " + method.getClass().getName());
            failure.printStackTrace();
          }
        }
      }
    } finally {
      for (PluginClassPath classPath : classPaths) {
        if (classPath != null) {
          try {
            classPath.close();
          } catch (RuntimeException | LinkageError failure) {
            System.err.println("Could not close modal split plugin class path");
            failure.printStackTrace();
          }
        }
      }
    }
  }
}
