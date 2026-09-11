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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.swing.JOptionPane;

/*
 * Create shortest-path service lines from an OD matrix.
 *
 * Purpose
 * -------
 * This script is intended for Nodus users who already have a project with an OD matrix and want
 * to quickly create a first set of service lines. It reads the OD table, extracts the distinct
 * origin-destination relations, computes the shortest physical path for each relation, and stores
 * the result in the Nodus service tables.
 *
 * Typical use
 * -----------
 * 1. Open the Nodus project that contains the OD matrix and the transport network.
 * 2. Adjust the editable parameters below:
 *    - odTableName: name of the OD matrix table.
 *    - mode: transport mode used to compute and create the services.
 *    - means: transport means used to compute and create the services; use -1 for all means.
 *    - frequencyPerWeek: weekly service frequency to assign to every generated service.
 *    - previewOnly: set to true for a dry run.
 * 3. Run this script from Nodus.
 * 4. Review the generated services in Project | Edit services.
 * 5. If the mode/means is service-constrained, make sure the corresponding SERVICELINES,
 *    stp and sw cost/duration functions are defined in the cost functions file.
 *
 * OD table requirements
 * ---------------------
 * The OD table must be a valid Nodus OD matrix table. At minimum, it must contain the standard
 * fields grp, org, dst and qty. The quantity values are not used by this script; the table is used
 * only to know which origin-destination relations exist.
 *
 * What is generated
 * -----------------
 * - One service is generated for each distinct unordered OD pair. If the table contains both
 *   A->B and B->A, only one service is created.
 * - The lower node ID is used as the service origin and the higher node ID as the service
 *   destination. This keeps service names stable and avoids duplicate reverse services.
 * - The route is the shortest path by physical link length on enabled links whose mode matches
 *   the selected mode and whose means value supports the selected means. With means = -1, the
 *   route is computed for means 1, and the resulting service applies to every means supported by
 *   every link of the complete line.
 * - The service name is built as:
 *     origin-destination-mode-means-annualFrequency
 * - The service stop nodes are initially limited to the service origin and destination. Additional
 *   stops can be edited later with the node fields editor Services button.
 *
 * Existing service tables
 * -----------------------
 * If service tables already exist, the script asks whether to:
 * - Add the generated services to the existing ones.
 * - Clear the currently loaded services before generating.
 * - Cancel the operation.
 *
 * The generated services are saved to the SQL service tables at the end of the script. In preview
 * mode, no service table is modified and no save is performed.
 *
 * Practical notes
 * ---------------
 * - If no path can be found for one OD pair, that service is skipped and the script continues with
 *   the next pair.
 * - This script does not create intermediate stops. It creates a first operational service set
 *   that can then be refined in the graphical services editor.
 * - The service frequency stored by Nodus is annualized. Here it is computed as
 *   frequencyPerWeek * 52.
 */
public class CreateShortestPathServicesFromOD_ {

  /*
   * Editable parameters.
   *
   * These values are the normal customization points for power users. They can be edited without
   * touching the rest of the script.
   */

  /*
   * Name of the OD matrix table to scan. The table name is checked and converted to the database
   * compliant spelling used by the current project database.
   */
  String odTableName = "OD";

  /*
   * Mode and means used both for shortest-path computation and for the generated service headers.
   * The shortest path will use only enabled links of this mode that support this means. Set means
   * to TransportService.ALL_MEANS (-1) to make each generated service available to all means
   * supported over its complete line.
   */
  int mode = 1;
  int means = 1; // Or TransportService.ALL_MEANS.

  /*
   * Weekly frequency assigned to each generated service. Nodus stores service frequency as an
   * annual value, so the script saves frequencyPerWeek * 52.
   */
  int frequencyPerWeek = 5;

  /*
   * Optional parameters.
   *
   * Set previewOnly to true to print what would be created without modifying or saving the service
   * tables. This is useful before running the script on a large OD matrix.
   */
  boolean previewOnly = false;

  public CreateShortestPathServicesFromOD_(NodusMapPanel nodusMapPanel) {

    new NodusConsole();

    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    if (!nodusProject.isOpen()) {
      System.err.println("No Nodus project is open.");
      return;
    }

    odTableName = JDBCUtils.getCompliantIdentifier(odTableName);
    if (!JDBCUtils.tableExists(odTableName)) {
      System.err.println("OD table " + odTableName + " not found.");
      return;
    }
    odTableName = getValidOdTableName(nodusProject, odTableName);
    if (odTableName == null) {
      System.err.println(
          "The selected table is not a valid OD table. It must contain the fields " +
              NodusC.DBF_GROUP +
              ", " +
              NodusC.DBF_ORIGIN +
              ", " +
              NodusC.DBF_DESTINATION +
              " and " +
              NodusC.DBF_QUANTITY +
              ".");
      return;
    }

    // Service frequencies are stored as annual values in the service header table.
    int annualFrequency = getAnnualFrequency(frequencyPerWeek);
    ServiceHandler serviceHandler = nodusProject.getServiceHandler();
    if (!prepareServiceTables(serviceHandler)) {
      System.out.println("Service generation canceled.");
      return;
    }

    int created = 0;
    int failed = 0;
    int skipped = 0;
    HashSet<String> generatedOdPairKeys = new HashSet<String>();

    try {
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();
      String sqlStmt =
          "SELECT " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN) +
              ", " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION) +
              " FROM " +
              JDBCUtils.getQuotedCompliantIdentifier(odTableName) +
              " GROUP BY " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN) +
              ", " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION) +
              " ORDER BY " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN) +
              ", " +
              JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION);

      try (Statement stmt = jdbcConnection.createStatement();
          ResultSet rs = stmt.executeQuery(sqlStmt)) {
        while (rs.next()) {
          int odOriginNodeId = JDBCUtils.getInt(rs.getObject(1));
          int odDestinationNodeId = JDBCUtils.getInt(rs.getObject(2));

          /*
           * Treat A->B and B->A as the same relation. This generates one stable service name per
           * unordered OD pair and avoids duplicate reverse services.
           */
          int originNodeId = Math.min(odOriginNodeId, odDestinationNodeId);
          int destinationNodeId = Math.max(odOriginNodeId, odDestinationNodeId);
          String odPairKey = originNodeId + "-" + destinationNodeId;
          if (generatedOdPairKeys.contains(odPairKey)) {
            skipped++;
            System.out.println(
                "Skipped reverse OD relation " +
                    odOriginNodeId +
                    "-" +
                    odDestinationNodeId +
                    " because service " +
                    odPairKey +
                    " was already generated.");
            continue;
          }
          generatedOdPairKeys.add(odPairKey);

          String serviceName =
              originNodeId + "-" + destinationNodeId + "-" + mode + "-" + means + "-" +
                  annualFrequency;

          try {
            LinkedList<Integer> linkIds =
                serviceHandler.findShortestServicePath(
                    originNodeId, destinationNodeId, mode, means);

            if (previewOnly) {
              System.out.println(
                  "Preview: " +
                      serviceName +
                      " would use " +
                      linkIds.size() +
                      " links.");
            } else {
              LinkedList<Integer> stopNodeIds = new LinkedList<Integer>();

              /*
               * Initial stops are limited to the end nodes. Additional service stops can be added
               * afterwards from the node fields editor.
               */
              stopNodeIds.add(originNodeId);
              stopNodeIds.add(destinationNodeId);

              TransportService service =
                  serviceHandler.createOrReplaceServiceFromLinkIds(
                      serviceHandler.getNewServiceId(),
                      serviceName,
                      mode,
                      means,
                      annualFrequency,
                      linkIds,
                      stopNodeIds,
                      false,
                      false);
              System.out.println(
                  "Created service " +
                      service.getId() +
                      " (" +
                      service.getName() +
                      ") with " +
                      service.getNbLinks() +
                      " links.");
            }
            created++;
          } catch (Exception ex) {
            failed++;
            System.err.println(
                "Could not create service " +
                    serviceName +
                    " from " +
                    originNodeId +
                    " to " +
                    destinationNodeId +
                    ": " +
                    ex.getMessage());
          }
        }
      }

      if (!previewOnly && !serviceHandler.savePendingChanges()) {
        throw new IllegalStateException("Generated services could not be saved.");
      }

      System.out.println(
          "Done. Services created: " +
              created +
              ". OD relations skipped: " +
              skipped +
              ". OD relations failed: " +
              failed +
              ".");
    } catch (Exception ex) {
      System.err.println("Could not generate services from OD table: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private boolean prepareServiceTables(ServiceHandler serviceHandler) {
    /*
     * Existing service tables are not silently overwritten. The user decides whether the generated
     * services are appended to the current service set or whether the loaded services are cleared
     * before generation.
     */
    boolean serviceTablesExist =
        JDBCUtils.tableExists(serviceHandler.getServiceHeaderTableName())
            || JDBCUtils.tableExists(serviceHandler.getServiceLinkDetailTableName())
            || JDBCUtils.tableExists(serviceHandler.getServiceStopDetailTableName());
    if (!serviceTablesExist || previewOnly) {
      return true;
    }

    Object[] options = ["Add", "Clear", "Cancel"] as Object[];
    int answer =
        JOptionPane.showOptionDialog(
            null,
            "Service tables already exist. Add generated lines to the existing services, or clear " +
                "the service tables before generating?",
            "Generate services from OD matrix",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

    if (answer == 0) {
      return true;
    }
    if (answer == 1) {
      clearLoadedServices(serviceHandler);
      return true;
    }
    return false;
  }

  private void clearLoadedServices(ServiceHandler serviceHandler) {
    LinkedList<String> serviceNames = new LinkedList<String>();
    Iterator<String> it = serviceHandler.getServiceNamesIterator();
    while (it.hasNext()) {
      serviceNames.add(it.next());
    }
    for (String serviceName : serviceNames) {
      serviceHandler.removeService(serviceName);
    }
  }

  private int getAnnualFrequency(int weeklyFrequency) {
    long annualFrequency = (long) weeklyFrequency * 52;
    if (annualFrequency < 0) {
      return 0;
    }
    if (annualFrequency > 99999) {
      return 99999;
    }
    return annualFrequency;
  }

  private String getValidOdTableName(NodusProject nodusProject, String requestedTableName) {
    Vector<String> validTableNames = ODReader.getValidODTables(nodusProject);
    for (String validTableName : validTableNames) {
      if (validTableName.equalsIgnoreCase(requestedTableName)) {
        return validTableName;
      }
    }
    return null;
  }
}

new CreateShortestPathServicesFromOD_(nodusMapPanel);
