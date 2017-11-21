/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Tentative util to dynamically modify the classpath at runtime, with save/restore capabilities.
 *
 * @author Bart Jourquin
 */
public class ClassPathHacker {
  private static final Class<?>[] parameters = new Class[] {URL.class};

  /**
   * Add a file or directory to the classpath.
   *
   * @param file File or directory (ended with a '/') to add to the classpath
   * @throws IOException On error
   */
  public static void addFile(File file) throws IOException {
    URI uri = file.toURI();
    addURL(uri.toURL());
  }

  /**
   * Add a file or directory to the classpath.
   *
   * @param fileName File or directory (ended with a '/') to add to the classpath
   * @throws IOException On error
   */
  public static void addFile(String fileName) throws IOException {
    File f = new File(fileName);
    addFile(f);
  }

  /**
   * Add a file or directory to the classpath.
   *
   * @param url File or directory (ended wih a '/') to add to the classpath
   * @throws IOException On error
   */
  public static void addURL(URL url) throws IOException {

    URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    Class<URLClassLoader> sysclass = URLClassLoader.class;

    try {
      Method method = sysclass.getDeclaredMethod("addURL", parameters);
      method.setAccessible(true);
      method.invoke(sysloader, new Object[] {url});
    } catch (Throwable t) {
      t.printStackTrace();
      throw new IOException("Error, could not add URL to system classloader");
    } // end try catch
  }

  /**
   * Get the active classpath.
   *
   * @return Context containing the classpath
   */
  public static ClassLoader getClassPath() {
    return Thread.currentThread().getContextClassLoader();
  }

  /**
   * Sets the classpath to a previously saved state.
   *
   * @param cl Context containing the classpath
   */
  public static void setClassPath(ClassLoader cl) {
    Thread.currentThread().setContextClassLoader(cl);
  }
}
