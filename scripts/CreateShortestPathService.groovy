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

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

public class CreateShortestPathService_ {

  /*
   * Editable parameters.
   *
   * Frequency is annualized, as stored in the services database table.
   */
  int serviceId = 1;
  int originNodeId = 1;
  int destinationNodeId = 2;
  int mode = 1;
  int means = 1;
  int frequency = 365;

  /*
   * Optional parameters.
   *
   * Leave serviceName null or blank to use "Service <ID>". Set previewOnly to true to print the
   * computed path without saving a service. Set overwriteExistingService to true only when an
   * existing service with the same ID or name must be replaced.
   */
  String serviceName = null;
  boolean previewOnly = false;
  boolean overwriteExistingService = false;
  boolean saveImmediately = true;

  public CreateShortestPathService_(NodusMapPanel nodusMapPanel) {

    new NodusConsole();

    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    if (!nodusProject.isOpen()) {
      System.err.println("No Nodus project is open.");
      return;
    }

    ServiceHandler serviceHandler = nodusProject.getServiceHandler();

    try {
      LinkedList<Integer> linkIds =
          serviceHandler.findShortestServicePath(originNodeId, destinationNodeId, mode, means);

      System.out.println(
          "Shortest path from node "
              + originNodeId
              + " to node "
              + destinationNodeId
              + " for mode "
              + mode
              + ", means "
              + means
              + ":");
      System.out.println(linkIds);

      if (previewOnly) {
        System.out.println("Preview only: no service was saved.");
        return;
      }

      LinkedList<Integer> stopNodeIds = new LinkedList<Integer>();
      stopNodeIds.add(originNodeId);
      stopNodeIds.add(destinationNodeId);

      TransportService service =
          serviceHandler.createOrReplaceServiceFromLinkIds(
              serviceId,
              serviceName,
              mode,
              means,
              frequency,
              linkIds,
              stopNodeIds,
              overwriteExistingService,
              saveImmediately);

      System.out.println(
          "Saved service "
              + service.getId()
              + " ("
              + service.getName()
              + ") with "
              + service.getNbLinks()
              + " links and "
              + service.getNbStops()
              + " stops.");
    } catch (Exception ex) {
      System.err.println("Could not create the service: " + ex.getMessage());
      ex.printStackTrace();
    }
  }
}

new CreateShortestPathService_(nodusMapPanel);
