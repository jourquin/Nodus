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

package edu.uclouvain.core.nodus.compute.results;

import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JOptionPane;

/** Reads OD quantities for signed node-result rendering. */
public class NodeResults implements ShapeConstants {
  private final NodusMapPanel nodusMapPanel;
  private final NodusProject nodusProject;
  private final ResultDisplay display;
  private final boolean export;

  /**
   * Creates a node-result display for the current project and map extent.
   *
   * @param mapPanel map receiving the results
   * @param relativeToView scale using only results in the current view
   * @param export export the numeric values to DBF, retaining two decimal places
   */
  public NodeResults(NodusMapPanel mapPanel, boolean relativeToView, boolean export) {
    nodusMapPanel = mapPanel;
    nodusProject = mapPanel.getNodusProject();
    display = new ResultDisplay(mapPanel, relativeToView);
    this.export = export;
  }

  /**
   * Reads and accumulates node quantities without retaining values from an earlier query.
   *
   * @param sqlStmt query returning node ID and quantity
   * @return true on successful display and optional export
   */
  public boolean readOD(String sqlStmt) {
    nodusMapPanel.setBusy(true);
    try {
      NodusEsriLayer[] layers = nodusProject.getNodeLayers();
      Map<Integer, RealNetworkObject> objects = ResultDisplay.objects(layers);
      Map<RealNetworkObject, Double> values = new LinkedHashMap<>();
      try (Statement statement = nodusProject.getMainJDBCConnection().createStatement();
          ResultSet rows = statement.executeQuery(sqlStmt)) {
        while (rows.next()) {
          RealNetworkObject object = objects.get(rows.getInt(1));
          if (object != null) {
            ResultDisplay.add(values, object, ResultDisplay.value(rows, 2));
          }
        }
      }
      return display.apply(layers, values, false, export, -1);
    } catch (Exception failure) {
      showError(failure.getMessage());
      return false;
    } finally {
      nodusMapPanel.setBusy(false);
    }
  }

  void showError(String message) {
    JOptionPane.showMessageDialog(nodusMapPanel, message, "SQL error", JOptionPane.ERROR_MESSAGE);
  }
}
