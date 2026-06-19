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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Univariate Multinomial Logit, using -Cost as utility.
 *
 * @author Bart Jourquin
 */
public class MultinomialLogit extends ModalSplitMethod {

  private I18n i18n = Environment.getI18n();

  private boolean warningAlreadyDisplayed = false;

  /**
   * Default constructor. Calls the super class.
   *
   * @param nodusProject Nodus project to associate to this method.
   */
  public MultinomialLogit(NodusProject nodusProject) {
    super(nodusProject);
  }


  @Override
  public String getName() {
    return "MNL";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "MNL", "Multinomial logit");
  }

  @Override
  public void initializeGroup(int currentGroup) {
    super.initializeGroup(currentGroup);
    warningAlreadyDisplayed = false;
  }

  /** Displays the calibration warning once for the current group. */
  private void displayCalibrationWarning() {
    if (warningAlreadyDisplayed) {
      return;
    }

    warningAlreadyDisplayed = true;
    String msg =
        i18n.get(ModalSplitMethod.class, "calibrate", "Weights are too high. Please calibrate");
    String title = i18n.get(ModalSplitMethod.class, "MNL", "Multinomial logit");
    JOptionPane pane = new JOptionPane(msg, JOptionPane.WARNING_MESSAGE);
    JDialog dialog = pane.createDialog(getNodusProject().getMainFrame(), title);
    dialog.setModal(false);
    dialog.setVisible(true);
  }

  @Override
  public boolean split(ODCell odCell, List<PathsForMode> pathsLists) {

    if (pathsLists.isEmpty()) {
      return false;
    }

    // Subtracting the lowest cost keeps at least one exponential equal to one and prevents
    // numerical underflow when all costs are large.
    double lowestModalCost = Double.POSITIVE_INFINITY;
    for (PathsForMode modalPaths : pathsLists) {
      double cost = modalPaths.cheapestPathWeights.getCost();
      if (Double.isFinite(cost)) {
        lowestModalCost = Math.min(lowestModalCost, cost);
      }
    }

    if (!Double.isFinite(lowestModalCost)) {
      displayCalibrationWarning();
      return false;
    }

    double denominator = 0.0;
    for (PathsForMode modalPaths : pathsLists) {
      double cost = modalPaths.cheapestPathWeights.getCost();
      if (Double.isFinite(cost)) {
        denominator += Math.exp(lowestModalCost - cost);
      }
    }

    for (PathsForMode modalPaths : pathsLists) {
      double cost = modalPaths.cheapestPathWeights.getCost();
      modalPaths.marketShare =
          Double.isFinite(cost) ? Math.exp(lowestModalCost - cost) / denominator : 0.0;
    }

    // Compute the market marketShare per path for each mode
    for (PathsForMode modalPaths : pathsLists) {
      double lowestPathCost = Double.POSITIVE_INFINITY;
      for (Path path : modalPaths.pathList) {
        double cost = path.weights.getCost();
        if (Double.isFinite(cost)) {
          lowestPathCost = Math.min(lowestPathCost, cost);
        }
      }

      if (!Double.isFinite(lowestPathCost)) {
        displayCalibrationWarning();
        return false;
      }

      denominator = 0.0;
      for (Path path : modalPaths.pathList) {
        double cost = path.weights.getCost();
        if (Double.isFinite(cost)) {
          denominator += Math.exp(lowestPathCost - cost);
        }
      }

      for (Path path : modalPaths.pathList) {
        double cost = path.weights.getCost();
        double pathShare =
            Double.isFinite(cost) ? Math.exp(lowestPathCost - cost) / denominator : 0.0;
        path.marketShare = pathShare * modalPaths.marketShare;
      }
    }
    return true;
  }
}
