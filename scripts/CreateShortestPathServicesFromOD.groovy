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

public class CreateShortestPathServicesFromOD_ {

  /*
   * Editable parameters.
   *
   * odTableName must name an OD matrix table containing at least the standard Nodus fields:
   * grp, org, dst and qty. One service line is generated for each distinct org/dst pair, even if
   * several groups exist for the same pair.
   */
  String odTableName = "OD";
  int mode = 1;
  int means = 1;
  int frequencyPerWeek = 5;

  /*
   * Optional parameters.
   *
   * Set previewOnly to true to print what would be created without saving service tables.
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

    int annualFrequency = getAnnualFrequency(frequencyPerWeek);
    ServiceHandler serviceHandler = nodusProject.getServiceHandler();
    if (!prepareServiceTables(serviceHandler)) {
      System.out.println("Service generation canceled.");
      return;
    }

    int created = 0;
    int failed = 0;

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
          int originNodeId = JDBCUtils.getInt(rs.getObject(1));
          int destinationNodeId = JDBCUtils.getInt(rs.getObject(2));
          String serviceName =
              originNodeId + "-" + destinationNodeId + "-" + mode + "-" + means + "-" +
                  frequencyPerWeek;

          try {
            LinkedList<Integer> linkIds =
                serviceHandler.findShortestServicePath(originNodeId, destinationNodeId, mode, means);

            if (previewOnly) {
              System.out.println(
                  "Preview: " +
                      serviceName +
                      " would use " +
                      linkIds.size() +
                      " links.");
            } else {
              LinkedList<Integer> stopNodeIds = new LinkedList<Integer>();
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
          "Done. Services created: " + created + ". OD relations failed: " + failed + ".");
    } catch (Exception ex) {
      System.err.println("Could not generate services from OD table: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private boolean prepareServiceTables(ServiceHandler serviceHandler) {
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
