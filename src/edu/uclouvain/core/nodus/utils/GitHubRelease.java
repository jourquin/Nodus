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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Simple utility that checks if a newer release of Nodus is available on GitHub.
 *
 * @author Bart Jourquin
 */
public class GitHubRelease {

  private static I18n i18n = Environment.getI18n();

  /** . */
  public GitHubRelease() {}

  /** Check for new release after being sure an Internet connection is available. */
  public static void checkForNewerRelease() {
    checkForNewerRelease(null, true);
  }

  /**
   * Check for new release after being sure an Internet connection is available.
   *
   * @param parent Parent dialog.
   */
  public static void checkForNewerRelease(JDialog parent) {
    checkForNewerRelease(parent, false);
  }

  /**
   * Check for new release after being sure an Internet connection is available.
   *
   * @param parent Parent dialog.
   */
  private static void checkForNewerRelease(JDialog parent, boolean autoCheck) {

    // Don't test a IDE build
    BuildIdGenerator generator = new BuildIdGenerator();
    String jarBuildId = generator.getJarBuildId();
    if (jarBuildId == null) {
      if (!autoCheck) {
        JOptionPane.showMessageDialog(
            parent,
            i18n.get(
                GitHubRelease.class, "IDEBuild", "The current running instance is an IDE build"));
      }
      return;
    }

    if (!isConnectedToInternet()) {
      if (!autoCheck) {
        JOptionPane.showMessageDialog(
            parent,
            i18n.get(GitHubRelease.class, "NoInternetConnection", "No Internet connection"));
      }
      return;
    }

    try {
      JSONObject gitHubInfo = getLatestBuildInfoFromGitHub();

      // Get latest version
      String remoteVersion = ((String) gitHubInfo.get("tag_name")).replace("v", "");

      // Get version of running app
      String currentVersion = NodusC.VERSION;

      if (!remoteVersion.equals(currentVersion)) {
        String message =
            MessageFormat.format(
                i18n.get(
                    GitHubRelease.class,
                    "NewVersionAvailable",
                    "Nodus version {0} is available on"),
                remoteVersion);
        displayInformationMessage(message, NodusC.nodusUrl, autoCheck);
      } else { // A new build may be available
        // Get latest BuildID
        String name = ((String) gitHubInfo.get("name")).toLowerCase();
        name = name.replace("build", "");
        int remoteBuild = Integer.parseInt(name);

        if (Integer.parseInt(jarBuildId) < remoteBuild) {
          String message =
              MessageFormat.format(
                  i18n.get(
                      GitHubRelease.class,
                      "NewBuildAvailabe",
                      "Nodus version {0} build {1} is available on"),
                  remoteVersion,
                  name);
          displayInformationMessage(message, NodusC.nodusUrl, autoCheck);
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Display an information message in a non modal dialog with a clickable URL.
   *
   * @param message The message to display
   * @param url The URL
   */
  private static void displayInformationMessage(String message, String url, boolean inBackground) {
    // Information message must be non modal
    message = "<html>" + message + " <a href=\"" + url + "\">" + url + "</a></html>";
    JLabel label = new JLabel(message);

    // Make URL clickable
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
    label.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            try {
              Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        });

    // Create the non modal dialog box
    JOptionPane pane =
        new JOptionPane(
            label, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, null, null);
    JDialog dialog = pane.createDialog(null, NodusC.APPNAME);
    if (inBackground) {
      dialog.setModal(false);
    } else {
      dialog.setModal(true);
    }
    dialog.setVisible(true);
  }

  /**
   * Retrieve the latest release information on GitHub.
   *
   * @return A JSON object with all the info returned by GitHub.
   * @throws Exception If something went wrong while fetching the info.
   */
  private static JSONObject getLatestBuildInfoFromGitHub() throws Exception {

    URL url = new URL(NodusC.gitHubUrlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("Erreur HTTP : " + conn.getResponseCode());
    }

    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuilder response = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) {
      response.append(line);
    }
    br.close();

    JSONParser parser = new JSONParser();
    return (JSONObject) parser.parse(response.toString());
  }

  /**
   * Test for an Internet connection.
   *
   * @return True if GitHub is reachable.
   */
  private static boolean isConnectedToInternet() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet request = new HttpGet(NodusC.gitHubUrlString);
      ClassicHttpResponse response = httpClient.execute(request, response1 -> response1);
      int statusCode = response.getCode();

      return statusCode >= 200 && statusCode < 300;
    } catch (IOException e) {
      return false;
    }
  }
}
