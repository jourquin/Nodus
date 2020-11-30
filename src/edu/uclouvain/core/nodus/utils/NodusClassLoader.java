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

import java.util.Hashtable;
import java.util.Vector;

/**
 * Class loader used for the plugin mechanism.
 *
 * @author Bart Jourquin
 */
public class NodusClassLoader extends ClassLoader {

  private Hashtable<String, byte[]> bytecodes = null;

  private String localResourceDirectory = null;

  private Hashtable<String, String> mimeHash = null;

  private Hashtable<String, byte[]> resourceHash = null;

  private static Hashtable<String, NodusClassLoader> loaders = new Hashtable<>();

  /** Returns a static class loader. */
  public static NodusClassLoader classLoader = createLoader("BeanBox", null);

  /**
   * Creates a class loader.
   *
   * @param cookie String
   * @param dir String
   * @return NodusClassLoader
   */
  public static NodusClassLoader createLoader(String cookie, String dir) {
    NodusClassLoader back = loaders.get(cookie);

    if (back != null) {
      if (!back.localResourceDirectory.equals(dir)) {
        throw new Error("internal error!");
      } else {
        return back;
      }
    } else {
      return new NodusClassLoader(cookie, dir);
    }
  }

  /**
   * Constructor.
   *
   * @param cookie String
   * @param dir String
   */
  private NodusClassLoader(String cookie, String dir) {
    resourceHash = new Hashtable<>();
    mimeHash = new Hashtable<>();
    bytecodes = new Hashtable<>();
    localResourceDirectory = dir;
    loaders.put(cookie, this);
  }

  private Class<?> applyDefinition(String name, boolean resolve) {
    byte[] buf = bytecodes.get(name);

    if (buf == null) {
      return null;
    }

    Class<?> c = null;
    try {
      c = super.defineClass(null, buf, 0, buf.length);
    } catch (Error e) {
      System.err.println(name);
    }

    if (c != null && resolve) {
      resolveClass(c);
    }

    return c;
  }

  /**
   * Used by JarLoader.
   *
   * @param classList List of classes
   */
  public synchronized void applyDefinitions(Vector<?> classList) {
    for (Object name : classList) {
      String classname = (String) name;
      Class<?> c = findLoadedClass(classname);

      if (c == null) {
        applyDefinition(classname, true);
      }
    }
  }

  /**
   * Used by JarLoader.
   *
   * @param name String
   * @param data byte[]
   * @param type String
   */
  void putLocalResource(String name, byte[] data, String type) {
    resourceHash.put(name, data);
    mimeHash.put(name, type);
  }

  /**
   * Used by JarLoader.
   *
   * @param name String
   * @param buf byte[]
   */
  public void setDefinition(String name, byte[] buf) {
    bytecodes.put(name, buf);
  }
}
