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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import sun.tools.jar.Manifest;

/**
 * Jar classLoader used for the Nodus plugin mechanism.
 *
 * @author Bart Jourquin
 */
public class JarLoader {

  private Vector<String> classList = new Vector<>();

  private InputStream jarStream = null;

  private NodusClassLoader classLoader = null;

  private Vector<String> serList = new Vector<>();

  /**
   * Returns the type of the content.
   * 
   * @param is InputStream to check.
   * @return The MIME type of the content of the stream.
   * @throws IOException On error
   */
  private static String getContentType(InputStream is) throws IOException {
    String type = URLConnection.guessContentTypeFromStream(is);

    if (type == null) {
      is.mark(10);

      int c1 = is.read();
      int c2 = is.read();
      is.reset();

      if (c1 == 172 && c2 == 237) {
        type = "application/java-serialized-object";
      }
    }
    return type;
  }

  /**
   * Creates a new JarLoader from its file name.
   * 
   * @param jarName The name of the jar file to load.
   * @throws FileNotFoundException On error
   */
  public JarLoader(String jarName) throws FileNotFoundException {

    InputStream is = new FileInputStream(jarName);
    jarStream = new BufferedInputStream(is);
    classLoader = NodusClassLoader.classLoader;
  }

  /** Loads the classes that are in the jar into a list. */
  public void loadJarClasses() {
    ZipInputStream zis = null;

    try {
      zis = new ZipInputStream(jarStream);

      for (ZipEntry ent = null; (ent = zis.getNextEntry()) != null; ) {
        String name = ent.getName();
        String type = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedInputStream bufis = new BufferedInputStream(zis);

        for (int i = bufis.read(); i != -1; i = bufis.read()) {
          baos.write(i);
        }

        byte[] buf = baos.toByteArray();

        if (Manifest.isManifestName(name)) {
          type = "manifest/manifest";
        }

        if (type == null) {
          InputStream tmpStream = new ByteArrayInputStream(buf);
          type = getContentType(tmpStream);
          tmpStream.close();
        }

        if (type == null) {
          type = "input-stream/input-stream";
        }

        classLoader.putLocalResource(name, buf, type);

        if (type.startsWith("application/java-serialized-object")) {
          String sername = name.substring(0, name.length() - 4);
          sername = sername.replace('/', '.');
          serList.addElement(sername);
        } else if (type.startsWith("application/java-vm")) {
          String classname = name.substring(0, name.length() - 6);
          classname = classname.replace('/', '.');
          classLoader.setDefinition(classname, buf);
          classList.addElement(classname);
        } else if (type.equals("manifest/manifest")) {
          // Manifest : do nothing
        } else if (type.equals("input-stream/input-stream")) {
          // Is probably a stored directory structure. Do nothing...
        } else if (name.startsWith(".")) {
          // Hidden files. Do nothing
        } else {
          System.err.println("ZipEntry " + name + " has unsupported mimetype " + type);
        }
      }
    } catch (IOException e) {
      System.err.println("IOException loading archive: " + e);
      e.printStackTrace();
    } catch (Throwable ex) {
      System.err.println("Caught " + ex + " in loadit()");
      ex.printStackTrace();
    } finally {
      if (zis != null) {
        try {
          zis.close();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }

    classLoader.applyDefinitions(classList);
  }
}
