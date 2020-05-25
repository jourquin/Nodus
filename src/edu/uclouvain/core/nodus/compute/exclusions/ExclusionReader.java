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

package edu.uclouvain.core.nodus.compute.exclusions;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.swing.JOptionPane;

/**
 * Loads the content of the exclusion table.
 *
 * @author Bart Jourquin
 */
public class ExclusionReader {

  private static I18n i18n = Environment.getI18n();

  private boolean isOk = true;

  private int nbRecords = 0;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private String tableName;

  private VirtualNetwork virtualNet;

  /**
   * Initializes the exclusions reader.
   *
   * @param vnet The virtual network
   */
  public ExclusionReader(VirtualNetwork vnet) {
    nodusProject = vnet.getNodusProject();
    nodusMapPanel = nodusProject.getNodusMapPanel();
    virtualNet = vnet;

    // Create SQL statement
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue);

    // Exclusions may not exist
    if (!JDBCUtils.tableExists(tableName)) {
      return;
    }

    String sql = "SELECT COUNT(*) FROM " + tableName;

    // Fetch number of records to read
    try {
      // Open connection
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sql);
      rs.next();
      nbRecords = rs.getInt(1);
      rs.close();
      stmt.close();

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null, e.getMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      isOk = false;
    }
  }

  /**
   * Returns true if the list of exclusions is not empty.
   *
   * @return True if exclusions are associated to this virtual network.
   */
  public boolean hasExclusions() {
    if (nbRecords == 0) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * Returns true if the exclusions are successfully loaded in the virtual network.
   *
   * @return True on success.
   */
  public boolean loadExclusions() {
    if (!isOk) {
      return false;
    }

    // Load all exclusions for current group and for group 0
    String sql =
        "SELECT "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
            + " FROM "
            + tableName;

    // connect to database and execute query
    int group = -1;

    int num = -1;

    int mode1 = -1;

    int means1 = -1;

    int mode2 = -1;

    int means2 = -1;

    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sql);

      VirtualNodeList[] vnl = virtualNet.getVirtualNodeLists();

      nodusMapPanel.startProgress(nbRecords);

      while (rs.next()) {
        if (!nodusMapPanel.updateProgress(
            i18n.get(ExclusionReader.class, "Loading_exclusions", "Loading exclusions"))) {

          return false;
        }

        for (int i = 1; i <= 6; i++) {
          Object obj = rs.getObject(i);
          int value = JDBCUtils.getInt(obj);

          if (value == Integer.MIN_VALUE) {
            return false;
          }

          switch (i) {
            case 1:
              group = value;
              break;

            case 2:
              num = value;
              break;

            case 3:
              mode1 = value;
              break;

            case 4:
              means1 = value;
              break;

            case 5:
              mode2 = value;
              break;

            case 6:
              means2 = value;
              break;

            default:
              break;
          }
        }

        // add the exclusion to the relevant list
        int originIndex = virtualNet.getNodeIndexInVirtualNodeList(num, true);

        if (originIndex != -1) {
          Exclusion exc = new Exclusion(group, num, mode1, means1, mode2, means2);
          vnl[originIndex].addExclusion(exc);
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception e) {
      nodusMapPanel.stopProgress();
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

      return false;
    }

    nodusMapPanel.stopProgress();

    return true;
  }
}
