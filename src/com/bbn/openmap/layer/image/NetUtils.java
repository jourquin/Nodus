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

package com.bbn.openmap.layer.image;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.text.MessageFormat;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Convenience class with network utilities.
 *
 * @author Bart Jourquin
 */
public class NetUtils {

  private static I18n i18n = Environment.getI18n();

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
      port = url.getDefaultPort();
      host = url.getHost();
    } catch (MalformedURLException e1) {
      e1.printStackTrace();
    }

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1000);
      return true;
    } catch (IOException e) {

      String s =
          MessageFormat.format(
              i18n.get(NetUtils.class, "Not_reachable", "Server \"{0}\" in is not reachable"),
              host);
      JOptionPane pane = new JOptionPane(s, JOptionPane.WARNING_MESSAGE);
      JDialog dialog = pane.createDialog(null, i18n.get(NodusWMSLayer.class, "Warning", "Warning"));

      dialog.setModal(false); // this says not to block background components
      dialog.setVisible(true);

      return false;
    }
  }
}
