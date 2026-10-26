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

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.LabelLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Queries assignment results for map rendering and project-owned period playback. */
public class LinkResults implements ShapeConstants {
  static I18n i18n = Environment.getI18n();
  private final NodusMapPanel nodusMapPanel;
  private final NodusProject nodusProject;
  private final ResultDisplay display;
  private final boolean export;
  private int currentScenario = -1;
  private String currentCostFunctionsFileName;
  private VehiclesParser currentVehiclesParser;
  private Playback playback;
  private Connection playbackConnection;
  private boolean cleanupRegistered;
  private volatile boolean closed;

  /**
   * Creates a result display for the current project and map extent.
   *
   * @param mapPanel map receiving the results
   * @param relativeToView scale using only results in the current view
   * @param export export the numeric values to DBF, retaining two decimal places
   */
  public LinkResults(NodusMapPanel mapPanel, boolean relativeToView, boolean export) {
    nodusMapPanel = mapPanel;
    nodusProject = mapPanel.getNodusProject();
    display = new ResultDisplay(mapPanel, relativeToView);
    this.export = export;
  }

  private String getCostFunctionsFileName(int scenario) {
    String fallback =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
    String name = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS, fallback);
    name = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenario, name);
    return nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + name;
  }

  private VehiclesParser getVehiclesParser(int scenario) {
    String costFunctionsFileName = getCostFunctionsFileName(scenario);

    if (scenario != currentScenario
        || currentVehiclesParser == null
        || !costFunctionsFileName.equals(currentCostFunctionsFileName)) {
      Properties costFunctions = new Properties();
      try (FileInputStream inputStream = new FileInputStream(costFunctionsFileName.trim())) {
        costFunctions.load(inputStream);
      } catch (IOException ex) {
        ex.printStackTrace();
        return null;
      }

      VehiclesParser vehiclesParser = new VehiclesParser(scenario);
      for (byte group = 0; group < NodusC.MAXGROUPS; group++) {
        if (!vehiclesParser.loadVehicleCharacteristics(costFunctions, group)) {
          return null;
        }
      }

      currentScenario = scenario;
      currentCostFunctionsFileName = costFunctionsFileName;
      currentVehiclesParser = vehiclesParser;
    }

    return currentVehiclesParser;
  }

  Integer askForDisplayInterval() {
    String[] intervals = {
      i18n.get(LinkResults.class, "Press_enter", "Press 'Enter'"),
      i18n.get(LinkResults.class, "half_second", "0.5 second"),
      i18n.get(LinkResults.class, "1_second", "1 second"),
      i18n.get(LinkResults.class, "2_seconds", "2 seconds"),
      i18n.get(LinkResults.class, "5_seconds", "5 seconds")
    };

    String choice =
        (String)
            JOptionPane.showInputDialog(
                null,
                i18n.get(LinkResults.class, "Display_interval", "Display interval ?"),
                i18n.get(ResultsDlg.class, "Display_results", "Display results"),
                JOptionPane.QUESTION_MESSAGE,
                null,
                intervals,
                intervals[0]);

    // Canceled
    if (choice == null) {
      return null;
    }

    // Get the index of the choice
    int index = 0;
    for (int i = 0; i < intervals.length; i++) {
      if (choice.equals(intervals[i])) {
        index = i;
        break;
      }
    }

    switch (index) {
      case 1:
        return 500;
      case 2:
        return 1000;
      case 3:
        return 2000;
      case 4:
        return 5000;
      default:
        return 0;
    }
  }

  /**
   * Ask the user if he wants quantities or vehicles.
   *
   * @return 0 for quantities, 1 for vehicles.
   */
  Integer askForDisplayUnits() {
    String[] units = {
      i18n.get(LinkResults.class, "Quantities", "Quantities"),
      i18n.get(LinkResults.class, "Vehicles", "Vehicles"),
    };

    String choice =
        (String)
            JOptionPane.showInputDialog(
                null,
                i18n.get(LinkResults.class, "Display_units", "Display units ?"),
                i18n.get(ResultsDlg.class, "Display_results", "Display results"),
                JOptionPane.QUESTION_MESSAGE,
                null,
                units,
                units[0]);

    // Canceled
    if (choice == null) {
      return null;
    }

    // Get the index of the choice
    int index = 0;
    for (int i = 0; i < units.length; i++) {
      if (choice.equals(units[i])) {
        index = i;
        break;
      }
    }

    return index;
  }

  /**
   * Reads link quantities or vehicle counts from the first two SQL result columns.
   *
   * @param sqlStmt query returning link ID and value
   * @return true on successful display and optional export
   */
  public boolean displayVolumes(String sqlStmt) {
    if (playback != null) {
      playback.finish(false, true);
    }
    return displayVolumes(sqlStmt, -1);
  }

  boolean displayVolumes(String sqlStmt, int time) {
    nodusMapPanel.setBusy(true);
    try {
      NodusEsriLayer[] layers = nodusProject.getLinkLayers();
      Map<Integer, RealNetworkObject> objects = ResultDisplay.objects(layers);
      Map<RealNetworkObject, Double> values = new LinkedHashMap<>();
      String sql = time < 0 ? sqlStmt : ResultQueries.forTime(sqlStmt, time);
      Connection connection =
          playback == null ? nodusProject.getMainJDBCConnection() : playback.connection;
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(sql)) {
        while (rows.next()) {
          RealNetworkObject object = objects.get(rows.getInt(1));
          if (object != null) {
            ResultDisplay.add(values, object, ResultDisplay.value(rows, 2));
          }
        }
      }
      return display.apply(
          layers,
          values,
          true,
          export && playback == null,
          playback == null ? -1 : playback.magnitude);
    } catch (Exception failure) {
      showError(failure.getMessage());
      return false;
    } finally {
      nodusMapPanel.setBusy(false);
    }
  }

  /**
   * Accumulates each occurrence of a link in the selected paths.
   *
   * @param sqlStmt query returning link, quantity, commodity group, mode and means
   * @return true on successful display and optional export
   */
  public boolean displayPath(String sqlStmt) {
    if (playback != null) {
      playback.finish(false, true);
    }
    Integer units = askForDisplayUnits();
    if (units == null) {
      return false;
    }
    nodusMapPanel.setBusy(true);
    try {
      VehiclesParser vehicles =
          units == 1
              ? getVehiclesParser(nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0))
              : null;
      if (units == 1 && vehicles == null) {
        return false;
      }
      NodusEsriLayer[] layers = nodusProject.getLinkLayers();
      Map<Integer, RealNetworkObject> objects = ResultDisplay.objects(layers);
      Map<RealNetworkObject, Double> values = new LinkedHashMap<>();
      try (Statement statement = nodusProject.getMainJDBCConnection().createStatement();
          ResultSet rows = statement.executeQuery(sqlStmt)) {
        while (rows.next()) {
          RealNetworkObject object = objects.get(rows.getInt(1));
          if (object != null) {
            double value = ResultDisplay.value(rows, 2);
            if (units == 1) {
              double load = vehicles.getAverageLoad(rows.getInt(3), rows.getInt(4), rows.getInt(5));
              value = Math.copySign(Math.ceil(Math.abs(value) / load), value);
            }
            ResultDisplay.add(values, object, value);
          }
        }
      }
      return display.apply(layers, values, true, export, -1);
    } catch (Exception failure) {
      showError(failure.getMessage());
      return false;
    } finally {
      nodusMapPanel.setBusy(false);
    }
  }

  /**
   * Starts time-dependent result playback on the EDT.
   *
   * @param sqlStmt query returning link and value, grouped by link and time
   * @return true when playback starts or the interval prompt is canceled
   */
  public boolean displayTimeDependentFlows(String sqlStmt) {
    return displayTimeDependentFlows(sqlStmt, null);
  }

  /**
   * Starts playback for all assignment periods, including a final partial period.
   *
   * @param sqlStmt query returning link and value, grouped by link and time
   * @param onDone called once on completion, cancellation or failure, on the EDT
   * @return true when playback starts or the interval prompt is canceled
   */
  public boolean displayTimeDependentFlows(String sqlStmt, Consumer<Boolean> onDone) {
    if (!SwingUtilities.isEventDispatchThread()) {
      final boolean[] result = new boolean[1];
      try {
        SwingUtilities.invokeAndWait(() -> result[0] = displayTimeDependentFlows(sqlStmt, onDone));
        return result[0];
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(failure);
      } catch (InvocationTargetException failure) {
        throw new IllegalStateException(failure.getCause());
      }
    }
    if (playback != null) {
      playback.finish(false, true);
    }
    Integer interval = askForDisplayInterval();
    if (interval == null) {
      if (onDone != null) {
        onDone.accept(true);
      }
      return true;
    }
    final Playback session;
    try {
      if (closed || !nodusProject.isOpen() || nodusMapPanel.getNodusProject() != nodusProject) {
        throw new IllegalStateException("The playback project is closed");
      }
      Properties costs = new Properties();
      try (FileInputStream input =
          new FileInputStream(
              getCostFunctionsFileName(nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0))
                  .trim())) {
        costs.load(input);
      }
      int start = Integer.parseInt(costs.getProperty(NodusC.VARNAME_STARTTIME, "-1").trim());
      int end = Integer.parseInt(costs.getProperty(NodusC.VARNAME_ENDTIME, "-1").trim());
      int step = Integer.parseInt(costs.getProperty(NodusC.VARNAME_TIMESLICE, "-1").trim());
      if (start < 0 || end <= start || step <= 0 || interval < 0) {
        throw new IllegalArgumentException("Invalid assignment time settings");
      }
      ResultQueries.forTime(sqlStmt, start);
      final int boundary = end;
      double magnitude = 0;
      Map<Integer, RealNetworkObject> objects = ResultDisplay.objects(nodusProject.getLinkLayers());
      Set<RealNetworkObject> scaled = display.scalingObjects(nodusProject.getLinkLayers());
      final Connection connection = nodusProject.getMainJDBCConnection();
      playbackConnection = connection;
      try (Statement statement = connection.createStatement();
          ResultSet rows =
              statement.executeQuery(ResultQueries.forRange(sqlStmt, start, boundary))) {
        while (rows.next()) {
          if (scaled.contains(objects.get(rows.getInt(1)))) {
            magnitude = Math.max(magnitude, Math.abs(ResultDisplay.value(rows, 2)));
          }
        }
      }
      if (!cleanupRegistered) {
        nodusProject.registerProjectCleanup(
            "result-playback-" + UUID.randomUUID(), this::closePlayback);
        cleanupRegistered = true;
      }
      session =
          new Playback(sqlStmt, start, boundary, step, interval, magnitude, connection, onDone);
      playback = session;
    } catch (Exception failure) {
      showError(failure.getMessage());
      if (onDone != null) {
        onDone.accept(false);
      }
      return false;
    }
    return session.advance();
  }

  private void closePlayback() {
    closed = true;
    Runnable stop =
        () -> {
          if (playback != null) {
            playback.finish(false, true);
          }
        };
    if (SwingUtilities.isEventDispatchThread()) {
      stop.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(stop);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(failure);
      } catch (InvocationTargetException failure) {
        throw new IllegalStateException(failure.getCause());
      }
    }
  }

  /**
   * Checks whether a completion callback may still refresh this playback's project layers.
   *
   * @return false after project cleanup, connection closure or a change of project
   */
  public boolean isPlaybackProjectOpen() {
    try {
      return !closed
          && nodusProject.isOpen()
          && nodusMapPanel.getNodusProject() == nodusProject
          && (playbackConnection == null || !playbackConnection.isClosed());
    } catch (java.sql.SQLException failure) {
      return false;
    }
  }

  Timer createPlaybackTimer(int interval, Runnable step) {
    Timer timer = new Timer(interval, event -> step.run());
    timer.setRepeats(false);
    return timer;
  }

  void showError(String message) {
    JOptionPane.showMessageDialog(nodusMapPanel, message, "SQL error", JOptionPane.ERROR_MESSAGE);
  }

  void showPlaybackComplete() {
    JOptionPane.showMessageDialog(
        nodusMapPanel,
        i18n.get(LinkResults.class, "All_periods_displayed", "All the periods were displayed"),
        i18n.get(ResultsDlg.class, "Display_results", "Display results"),
        JOptionPane.INFORMATION_MESSAGE);
  }

  private final class Playback {
    final String query;
    final int end;
    final int duration;
    final int interval;
    final double magnitude;
    final Consumer<Boolean> onDone;
    final Connection connection;
    final LabelLayer label;
    final String oldText;
    final KeyAdapter keys;
    long nextTime;
    Timer timer;
    boolean finished;
    long generation;

    Playback(
        String query,
        int start,
        int end,
        int duration,
        int interval,
        double magnitude,
        Connection connection,
        Consumer<Boolean> onDone) {
      this.query = query;
      this.nextTime = start;
      this.end = end;
      this.duration = duration;
      this.interval = interval;
      this.magnitude = magnitude;
      this.onDone = onDone;
      this.connection = connection;
      LabelLayer found = null;
      for (Layer layer : nodusMapPanel.getLayerHandler().getLayers()) {
        if (layer instanceof LabelLayer) {
          found = (LabelLayer) layer;
          break;
        }
      }
      label = found;
      oldText = label == null ? null : label.getLabelText();
      keys =
          new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
              if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                finish(false, true);
              } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                advance();
              }
            }
          };
      nodusMapPanel.getMapBean().addKeyListener(keys);
      nodusMapPanel.getMapBean().requestFocus();
    }

    boolean advance() {
      if (finished) {
        return true;
      }
      if (timer != null) {
        timer.stop();
      }
      generation++;
      if (!isPlaybackProjectOpen()) {
        finish(false, true);
        return false;
      }
      if (nextTime >= end) {
        finish(true, true);
        return true;
      }
      int time = (int) nextTime;
      nextTime += duration;
      if (label != null) {
        String units =
            query.toLowerCase(Locale.ROOT).contains(NodusC.DBF_VEHICLES)
                ? i18n.get(LinkResults.class, "Vehicles_at", "Vehicles at")
                : i18n.get(LinkResults.class, "Volume_at", "Volume at");
        label.setLabelText(
            units + String.format(Locale.ROOT, " %02d:%02d", time / 60 % 24, time % 60));
        label.doPrepare();
      }
      if (!displayVolumes(query, time)) {
        finish(false, false);
        return false;
      }
      if (interval > 0) {
        final long expectedGeneration = generation;
        timer =
            createPlaybackTimer(
                interval,
                () -> {
                  if (!finished && generation == expectedGeneration) {
                    advance();
                  }
                });
        timer.start();
      }
      return true;
    }

    void finish(boolean completed, boolean success) {
      if (finished) {
        return;
      }
      finished = true;
      if (timer != null) {
        timer.stop();
      }
      nodusMapPanel.getMapBean().removeKeyListener(keys);
      playback = null;
      if (nodusMapPanel.getNodusProject() == nodusProject) {
        if (label != null) {
          label.setLabelText(oldText == null ? "" : oldText);
          label.doPrepare();
        }
        nodusMapPanel.resetText();
        if (nodusMapPanel.getMainFrame() != null) {
          nodusMapPanel.getMainFrame().requestFocus();
        }
      }
      if (completed) {
        showPlaybackComplete();
      }
      if (onDone != null) {
        onDone.accept(success);
      }
    }
  }
}
