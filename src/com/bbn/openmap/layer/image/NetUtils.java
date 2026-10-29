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

package com.bbn.openmap.layer.image;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Convenience class with network utilities.
 *
 * @author Bart Jourquin
 */
public class NetUtils {

  private static I18n i18n = Environment.getI18n();

  /** Only currently open warnings; all access is confined to the Swing event-dispatch thread. */
  private static final Map<String, JDialog> unreachableWarnings = new HashMap<>();

  /** Default constructor. */
  public NetUtils() {}

  /**
   * Tests if a given server is reachable.
   *
   * @param server Full URL of the server, as used in OpenMap properties for WMS or Tile layers.
   * @return True if reachable.
   */
  public static boolean pingHost(String server) {

    URL url;
    int port = 80;
    String host = "";

    try {
      url = new URL(server);
      port = url.getPort();
      if (port == -1) {
        port = url.getDefaultPort();
      }
      host = url.getHost();
    } catch (MalformedURLException e1) {
      e1.printStackTrace();
      return false;
    }

    if (host.isBlank() || port < 0) {
      return false;
    }

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1000);
      return true;
    } catch (IOException e) {

      String s =
          MessageFormat.format(
              i18n.get(NetUtils.class, "Not_reachable", "Server \"{0}\" is not reachable"), host);
      final String serverHost = host;
      SwingUtilities.invokeLater(() -> showUnreachableWarning(serverHost, s));

      return false;
    }
  }

  /**
   * Coalesces repeated host warnings on the EDT while retaining visibility during project loading.
   */
  private static void showUnreachableWarning(String host, String message) {
    final String key = host.toLowerCase(Locale.ROOT);
    JDialog existing = unreachableWarnings.get(key);
    if (existing != null && existing.isDisplayable()) {
      existing.toFront();
      return;
    }
    JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE);
    JDialog dialog = pane.createDialog(null, i18n.get(NodusWMSLayer.class, "Warning", "Warning"));
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            // An older close event must not remove a later warning created for the same host.
            unreachableWarnings.remove(key, dialog);
          }
        });
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    pane.addPropertyChangeListener(
        JOptionPane.VALUE_PROPERTY,
        event -> {
          if (event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
            dialog.dispose();
          }
        });
    dialog.setModal(false);
    dialog.setAlwaysOnTop(true);
    unreachableWarnings.put(key, dialog);
    try {
      dialog.setVisible(true);
    } catch (RuntimeException | Error failure) {
      unreachableWarnings.remove(key, dialog);
      dialog.dispose();
      throw failure;
    }
  }
}
