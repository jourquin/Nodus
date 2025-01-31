/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Simple utility class used to generate a time-stamp to associate to the jar file. The "jar"
 * section of ant build file will compile and launch this class, which creates a "builid.properties"
 * file into the "classes" directory, before the complete application jar is build. The latest will
 * thus contain the just created properties file. The ant task finally deletes the properties file,
 * so that it will not exist for a launch of the application from within Eclipse.
 *
 * @author Bart Jourquin
 */
public class BuildIdGenerator {

  private static final String key = "buildid";

  private static final String propFile = "buildid.properties";

  /** Default constructor. */
  public BuildIdGenerator() {}

  /**
   * Called from the ant task.
   *
   * @param args No arguments are needed.
   */
  public static void main(String[] args) {
    BuildIdGenerator generator = new BuildIdGenerator();
    generator.generateBuildId();
  }

  /**
   * Creates a properties file in the classes directory, that will contain the current time stamp.
   */
  private void generateBuildId() {
    Properties properties = new Properties();

    properties.put(key, getStamp());

    // Write properties file.
    try {
      String f = this.getClass().getPackage().getName();
      String fileName = "classes/" + f.replace('.', '/') + "/" + propFile;
      properties.store(new FileOutputStream(fileName), null);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Gets the content of the "buildid.properties" file if it exists (only in the application jar
   * file).
   *
   * @return The build ID or the current time stamp if called from within the (Eclipse) IDE..
   */
  public String getBuildId() {
    Properties properties = new Properties();

    try {
      InputStream is = getClass().getResourceAsStream(propFile);
      if (is == null) {
        return "IDE Build " + getStamp();
      }
      properties.load(is);
      return "JAR Build " + properties.getProperty(key);
    } catch (IOException ex) {

      return "IDE Build " + getStamp();
    }
  }

  /**
   * Returns the string that represents the time stamp used as build ID.
   *
   * @return build ID
   */
  private String getStamp() {
    // BuildID
    Date date = new Date();
    Format formatter = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
    return formatter.format(date);
  }

  /**
   * Returns the date stamp of the jar.
   *
   * @return A string with the yyyyMMdd format, or null on error.
   */
  public String getJarBuildId() {
    Properties properties = new Properties();

    try {
      InputStream is = getClass().getResourceAsStream(propFile);
      if (is == null) {
        return null;
      }
      properties.load(is);
      String s = properties.getProperty(key);
      s = s.replace(".", "");
      s = s.substring(0, 8);
      return s;

    } catch (IOException ex) {
      return null;
    }
  }
}
