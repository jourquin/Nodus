/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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


/*
 * This is a sample script that illustrates the possibility to run an assignment.
 */

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.FastMFAssignment;

public class _Assignment {

  NodusProject nodusProject;

  public _Assignment(NodusMapPanel nodusMapPanel) {
    // Get the project and be sure it is open
    nodusProject = nodusMapPanel.getNodusProject();
    if (!nodusProject.isOpen()) {
      return;
    }
    
    System.out.println("Assignment...");
    
    // Describe the assignment to run
    AssignmentParameters ap = new AssignmentParameters(nodusProject);
 
    ap.setCostMarkup(0.15);
    ap.setNbIterations(2);
    ap.setScenario((byte) 99);
    ap.setCostFunctions("MNL.costs");
    ap.setODMatrix("OD");
    ap.setSavePaths(false);
    ap.setDetailedPaths(false);
    ap.setWhereStmt("");
    ap.setModalSplitMethodName("MNL");
    ap.setScenarioDescription("MNL assignment from Groovy script");

    FastMFAssignment assignment = new FastMFAssignment(ap);
    assignment.run();

    System.out.println("Done.");
  }
}

// Uncomment to use as script
new _Assignment(nodusMapPanel);
