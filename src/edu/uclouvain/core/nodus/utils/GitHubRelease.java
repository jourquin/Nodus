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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Simple utility that checks if a newer release of Nodus is available on GitHub.
 *
 * @author Bart Jourquin
 */
public class GitHubRelease {

  private static I18n i18n = Environment.getI18n();

  /** Returns a desktop instance only when URI browsing is supported on this runtime. */
  private static Desktop getBrowseDesktop() {
    if (!Desktop.isDesktopSupported()) {
      return null;
    }

    try {
      Desktop desktop = Desktop.getDesktop();
      if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        return null;
      }
      return desktop;
    } catch (SecurityException | UnsupportedOperationException e) {
      return null;
    }
  }

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
    SwingWorker<ReleaseCheckResult, Void> worker =
        new SwingWorker<ReleaseCheckResult, Void>() {
          @Override
          protected ReleaseCheckResult doInBackground() {
            return getReleaseCheckResult(autoCheck);
          }

          @Override
          protected void done() {
            try {
              displayReleaseCheckResult(parent, autoCheck, get());
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };

    worker.execute();
  }

  /** Computes the release-check outcome without touching Swing components. */
  private static ReleaseCheckResult getReleaseCheckResult(boolean autoCheck) {
    ReleaseCheckResult result = new ReleaseCheckResult();

    // Don't test an IDE build
    BuildIdGenerator generator = new BuildIdGenerator();
    String jarBuildId = generator.getJarBuildId();
    if (jarBuildId == null) {
      if (!autoCheck) {
        result.message =
            i18n.get(
                GitHubRelease.class, "IDEBuild", "The current running instance is an IDE build");
      }
      return result;
    }

    if (!isConnectedToInternet()) {
      if (!autoCheck) {
        result.message =
            i18n.get(GitHubRelease.class, "NoInternetConnection", "No Internet connection");
      }
      return result;
    }

    try {
      JSONObject gitHubInfo = getLatestBuildInfoFromGitHub();

      // Get latest version
      String remoteVersion = ((String) gitHubInfo.get("tag_name")).replaceFirst("^v", "");

      // Get version of running app
      String currentVersion = NodusC.VERSION;

      boolean newReleaseAvailable = false;

      if (!remoteVersion.equals(currentVersion)) {
        newReleaseAvailable = true;
        result.message =
            MessageFormat.format(
                i18n.get(
                    GitHubRelease.class,
                    "NewVersionAvailable",
                    "Nodus version {0} is available on"),
                remoteVersion);
        result.url = NodusC.nodusUrl;

      } else {
        // A new build may be available.
        // Accept release names such as:
        // "Build20260607", "Build 20260607", "Nodus Build20260607", etc.
        String releaseName = (String) gitHubInfo.get("name");

        Pattern buildPattern = Pattern.compile("(?i)\\bbuild\\s*(\\d+)\\b");
        Matcher matcher = buildPattern.matcher(releaseName);

        if (matcher.find()) {
          String remoteBuildId = matcher.group(1);
          int remoteBuild = Integer.parseInt(remoteBuildId);

          if (Integer.parseInt(jarBuildId) < remoteBuild) {
            newReleaseAvailable = true;
            result.message =
                MessageFormat.format(
                    i18n.get(
                        GitHubRelease.class,
                        "NewBuildAvailabe",
                        "Nodus version {0} build {1} is available on"),
                    remoteVersion,
                    remoteBuildId);
            result.url = NodusC.nodusUrl;
          }
        } else if (!autoCheck) {
          result.message =
              MessageFormat.format(
                  i18n.get(
                      GitHubRelease.class,
                      "CannotParseBuildId",
                      "Could not determine the latest build id from release name \"{0}\""),
                  releaseName);
        }
      }

      if (!newReleaseAvailable && !autoCheck && result.message == null) {
        result.message = i18n.get(GitHubRelease.class, "UpToDate", "Nodus is up-to-date");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  /** Displays the worker result on the EDT. */
  private static void displayReleaseCheckResult(
      JDialog parent, boolean autoCheck, ReleaseCheckResult result) {
    if (result == null || result.message == null) {
      return;
    }

    if (result.url != null) {
      displayInformationMessage(parent, result.message, result.url, autoCheck);
    } else {
      JOptionPane.showMessageDialog(parent, result.message);
    }
  }

  /** Simple value object used to communicate worker results back to the EDT. */
  private static class ReleaseCheckResult {
    private String message;
    private String url;
  }

  /**
   * Display an information message in a non modal dialog with a clickable URL.
   *
   * @param parent The parent dialog if there is one.
   * @param message The message to display
   * @param url The URL
   */
  private static void displayInformationMessage(
      JDialog parent, String message, String url, boolean inBackground) {
    // Information message must be non modal
    message = "<html>" + message + " <a href=\"" + url + "\">" + url + "</a></html>";
    JLabel label = new JLabel(message);

    // Make URL clickable
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
    label.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            Desktop desktop = getBrowseDesktop();
            if (desktop == null) {
              JOptionPane.showMessageDialog(
                  null,
                  i18n.get(
                      GitHubRelease.class,
                      "DesktopBrowseUnsupported",
                      "Desktop browsing is not supported on this system"),
                  NodusC.APPNAME,
                  JOptionPane.ERROR_MESSAGE);
              return;
            }

            Thread t =
                new Thread(
                    () -> {
                      try {
                        desktop.browse(new java.net.URI(url));
                      } catch (Exception ex) {
                        ex.printStackTrace();
                      }
                    },
                    "Nodus-ReleaseBrowser");
            t.setDaemon(true);
            t.start();
          }
        });

    Runnable showDialog =
        () -> {
          Frame owner = resolveNotificationOwner(parent);
          if (inBackground && owner == null) {
            return;
          }
          showInformationDialog(owner, label, inBackground);
        };

    if (inBackground) {
      showWhenOwnerVisible(parent, showDialog);
    } else {
      showDialog.run();
    }
  }

  /** Returns the best visible owner for a release-check notification dialog. */
  private static Frame resolveNotificationOwner(JDialog parent) {
    if (parent != null && parent.getOwner() instanceof Frame) {
      Frame parentOwner = (Frame) parent.getOwner();
      if (parentOwner.isShowing()) {
        return parentOwner;
      }
    }

    Frame[] frames = Frame.getFrames();
    for (Frame frame : frames) {
      if (frame != null && frame.isShowing()) {
        return frame;
      }
    }

    return null;
  }

  /** Shows the information dialog once the application main window is visible. */
  private static void showWhenOwnerVisible(JDialog parent, Runnable showDialog) {
    if (resolveNotificationOwner(parent) != null) {
      showDialog.run();
      return;
    }

    final int maxAttempts = 50;
    final int[] attempts = {0};
    Timer timer = new Timer(100, null);
    timer.addActionListener(
        e -> {
          attempts[0]++;
          Frame owner = resolveNotificationOwner(parent);
          if (owner != null) {
            ((Timer) e.getSource()).stop();
            showDialog.run();
          } else if (attempts[0] >= maxAttempts) {
            ((Timer) e.getSource()).stop();
          }
        });
    timer.setRepeats(true);
    timer.start();
  }

  /** Creates and shows the clickable release information dialog. */
  private static void showInformationDialog(Frame owner, JLabel label, boolean inBackground) {
    JOptionPane pane =
        new JOptionPane(
            label, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, null, null);
    JDialog dialog = pane.createDialog(owner, NodusC.APPNAME);
    dialog.setModal(!inBackground);
    if (owner != null) {
      dialog.setLocationRelativeTo(owner);
      dialog.setAlwaysOnTop(true);
    }
    dialog.setVisible(true);
    if (owner != null) {
      dialog.toFront();
      dialog.setAlwaysOnTop(false);
    }
  }

  /**
   * Retrieve the latest release information on GitHub.
   *
   * @return A JSON object with all the info returned by GitHub.
   * @throws Exception If something went wrong while fetching the info.
   */
  private static JSONObject getLatestBuildInfoFromGitHub() throws Exception {

    HttpURLConnection conn = null;
    try {
      URL url = new URL(NodusC.gitHubUrlString);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

      int responseCode = conn.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new RuntimeException("Erreur HTTP : " + responseCode);
      }

      StringBuilder response = new StringBuilder();
      try (BufferedReader br =
          new BufferedReader(
              new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          response.append(line);
        }
      }

      JSONParser parser = new JSONParser();
      return (JSONObject) parser.parse(response.toString());
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  /**
   * Test for an Internet connection.
   *
   * @return True if GitHub is reachable.
   */
  private static boolean isConnectedToInternet() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet request = new HttpGet(NodusC.gitHubUrlString);
      return httpClient.execute(
          request,
          response -> {
            int statusCode = response.getCode();
            return statusCode >= 200 && statusCode < 300;
          });
    } catch (IOException e) {
      return false;
    }
  }
}
