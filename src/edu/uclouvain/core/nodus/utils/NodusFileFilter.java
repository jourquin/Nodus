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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * Convenience class used to filter the displayed files file choosers.
 *
 * @author Bart Jourquin
 */
public class NodusFileFilter extends FileFilter {

  private static I18n i18n = Environment.getI18n();

  /**
   * Extracts the extension from the given file.
   *
   * @param file The file for which the extension must be retrieved.
   * @return The file extension.
   */
  private static String getExtensionFromFileName(File file) {
    String ext = null;
    String s = file.getName();
    int i = s.lastIndexOf('.');

    if (i > 0 && i < s.length() - 1) {
      ext = s.substring(i + 1).toLowerCase();
    }

    return ext;
  }

  /* Explanation string to display */
  String description = i18n.get(NodusFileFilter.class, "All_files", "All Files");

  /* Extension to display */
  String extension = "*.*";

  /**
   * Default constructor.
   */
  public NodusFileFilter() {

  }

  /**
   * Creates a new filter for the given extension and explanation string.
   *
   * @param extension File extension
   * @param explanation Description of the file type.
   */
  public NodusFileFilter(String extension, String explanation) {
    setExtension(extension);
    description = explanation;
  }

  /**
   * Returns true if the given file must be displayed or if the given file is a directory.
   *
   * @param file The file to test.
   * @return True if the file has to be retained or is a directory.
   */
  @Override
  public boolean accept(File file) {
    if (file.isDirectory()) {
      return true;
    }

    String extension = getExtensionFromFileName(file);

    if (extension != null) {
      if (extension.equalsIgnoreCase(this.extension)) {
        return true;
      } else {
        return false;
      }
    }

    return false;
  }

  /**
   * Returns the description string that will appear in the file chooser.
   *
   * @return Description of the file type.
   */
  @Override
  public String getDescription() {
    return description;
  }

  /**
   * Returns the file extension.
   *
   * @return The file extension.
   */
  public String getExtension() {
    return extension;
  }

  /**
   * Returns the file description.
   *
   * @param description The file description.
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Sets the file extension to check.
   *
   * @param extension The file extension to check.
   */
  public void setExtension(String extension) {
    this.extension = extension;
    // If the extension starts with a dot, remove it
    if (this.extension.startsWith(".")) {
      this.extension = this.extension.substring(1);
    }
  }
}
