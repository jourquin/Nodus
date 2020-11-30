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

package edu.uclouvain.core.nodus.helpbrowser;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

/**
 * Launches the system default web browser to display the online help or the API JavaDoc.
 *
 * @author Bart Jourquin
 */
public class HelpBrowser  {

  private URI uri;
  private URL url;
  private static I18n i18n = Environment.getI18n();

  /**
   * Launches the system default web browser.
   *
   * @param isHelp If true, the help main entry will be displayed. If false, the API doc will be
   *     displayed.
   */
  public void launchBrowser(boolean isHelp) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {

      try {
        String homeDir = System.getProperty("NODUS_HOME", ".");
        homeDir = new java.io.File(homeDir).getCanonicalPath();
        String helpFileName;

        if (isHelp) {
          // Try to find the localized help file
          String tag = Locale.getDefault().toLanguageTag();
          helpFileName =
              homeDir + File.separator + "doc" + File.separator + "help_" + tag + ".html";
          File f = new File(helpFileName);
          if (!f.exists()) {
            helpFileName = homeDir + File.separator + "doc" + File.separator + "help.html";
          }
        } else {
          helpFileName = homeDir + File.separator + "api" + File.separator + "index.html";
        }

        url = new URL("file:///" + helpFileName);
        uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);

      } catch (URISyntaxException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      Thread t =
          new Thread() {
            @Override
            public void run() {
              try {
                Desktop.getDesktop().browse(uri);
              } catch (Exception e) {
                System.err.println(e.getMessage());
              }
            }
          };
      t.start();
    } else {

      System.err.println(
          i18n.get(
              HelpBrowser.class,
              "Desktop_not_supported",
              "Desktop not supported by this version of Java"));
    }
  }
}
