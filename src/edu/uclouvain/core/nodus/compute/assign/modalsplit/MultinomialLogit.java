/*
 * Copyright (c) 1991-2020 Université catholique de Louvain
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
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.Iterator;
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

  @Override
  public String getName() {
    return "MNL";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "MNL", "Multinomial logit");
  }

  @Override
  public void initialize(
      int currentGroup, NodusProject nodusProject, AssignmentParameters assignmentParameters) {
    super.initialize(currentGroup, nodusProject, assignmentParameters);
    warningAlreadyDisplayed = false;
  }

  @Override
  public boolean split(ODCell odCell, List<PathsForMode> pathsLists) {

    /*
     * Compute the market marketShare for each mode
     */
    double denominator = 0.0;
    Iterator<PathsForMode> plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      double d = Math.exp(-modalPaths.cheapestPathWeights.getCost());
      denominator += d;
    }

    // Compute the market marketShare per mode
    plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();

      double v = Math.exp(-modalPaths.cheapestPathWeights.getCost()) / denominator;
      if (Double.isNaN(v)) {
        v = 0.0;
      }
      modalPaths.marketShare = v;
    }

    // Compute the market marketShare per path for each mode
    plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();

      // Denominator for this mode
      denominator = 0.0;
      Iterator<Path> it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        double d = Math.exp(-path.weights.getCost());
        if (d == 0) {
          if (!warningAlreadyDisplayed) {
            warningAlreadyDisplayed = true;
            String msg =
                i18n.get(
                    ModalSplitMethod.class, "calibrate", "Weights are too high. Please calibrate");
            String title = i18n.get(ModalSplitMethod.class, "MNL", "Multinomial logit");
            JOptionPane pane = new JOptionPane(msg, JOptionPane.WARNING_MESSAGE);
            JDialog dialog = pane.createDialog(getNodusProject().getMainFrame(), title);
            dialog.setModal(false);
            dialog.setVisible(true);
            return false;
          }
        }
        denominator += d;
      }

      // Spread over each path of this mode
      it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();

        double v = Math.exp(-path.weights.getCost()) / denominator;
        if (Double.isNaN(v)) {
          v = 0.0;
        }

        path.marketShare = v * modalPaths.marketShare;
      }
    }
    return true;
  }
}
