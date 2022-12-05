/*
 * Copyright (c) 1991-2022 Université catholique de Louvain, 
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 * 
 * This file is part of Nodus.
 * 
 * Nodus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.gui.SQLConsole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Generates a wide data table that will be used by the R script that estimates the parameters for
 * the multinomial logit model specification.
 *
 * @author Bart Jourquin
 */
public class CreateMLogitInput_ {

  // Path table of the uncalibrated assignment
  String srcTable = "demo_path5_header";

  // Output table that will be used by the R script
  String dstTable = "mlogit_input";

  // OD table for road
  String od1Table = "od_road";

  //OD table for iww
  String od2Table = "od_iww";

  //OD table for rail
  String od3Table = "od_rail";

  /**
   * Convenient private class used to store information about the cheapest path per grp, org, dst
   * and mode. nodusMapPanel
   *
   * @author Bart Jourquin
   */
  class Record {
    int grp;
    int org;
    int dst;
    int mode;
    double cost;

    /**
     * Constructor.
     *
     * @param grp Group of commodities
     * @param org Origin node
     * @param dst Destination node
     * @param mode Transportation mode
     * @param cost Total cost on this route
     */
    public Record(
        int grp,
        int org,
        int dst,
        int mode,
        double cost) {
      this.grp = grp;
      this.org = org;
      this.dst = dst;
      this.mode = mode;
      this.cost = cost;
    }

    /**
     * Called when a cheaper alternative is found for this grp, org, dst and mode combination.
     *
     * @param cost New total cost
     */
    public void update(double cost) {
      this.cost = cost;
    }
  }

  /**
   * Real work starts here.
   *
   * @param nodusMapPanel The Nodus map panel
   */
  public CreateMLogitInput_(NodusMapPanel nodusMapPanel) {

    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    if (nodusProject.isOpen()) {

      // Get basic data from the uncalibrated assignment in a temporary table
      String[] sqlCommands1 = [
        "@@srcTable := " + srcTable,\
        "drop table if exists tmp2",\
        "create table tmp2 as (select grp, org, dst,\n"\
            + "ldcost+ulcost+trcost+tpcost+stcost+swcost+mvcost as cost,\n"\
            + "ldmode from @@srcTable) with data",\
        "create index if not exists tmp2idx on tmp2 (grp,org,dst,ldmode,cost)"\
      ];

      SQLConsole sqlConsole = new SQLConsole(nodusMapPanel.getNodusProject(), false);
      boolean success = sqlConsole.runBatch(sqlCommands1);
      if (!success) {
          return;
      }

      // Create a table containing, for each grp, org, dst, mode, the data for the cheapest alternative
      System.out.println(
          "Keep the cheapest alternative for each grp, org, dst and mode combination.");

      HashMap<String, Record> hm = new HashMap<String, Record>();
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();

      try {
        Statement stmt = jdbcConnection.createStatement();
        String sqlStmt =
            "select grp, org, dst, ldmode, cost from tmp2";
        ResultSet rs = stmt.executeQuery(sqlStmt);
        while (rs.next()) {
          int idx = 1;
          int grp = JDBCUtils.getInt(rs.getObject(idx++));
          int org = JDBCUtils.getInt(rs.getObject(idx++));
          int dst = JDBCUtils.getInt(rs.getObject(idx++));
          int mode = JDBCUtils.getInt(rs.getObject(idx++));
          double cost = JDBCUtils.getFloat(rs.getObject(idx++));

          String key = grp + "-" + org + "-" + dst + "-" + mode;
          Record record = hm.get(key);
          if (record == null) {
            hm.put(key, new Record(grp, org, dst, mode, cost));
          } else {
            if (cost < record.cost) {
              record.update(cost);
            }
          }
        }
        rs.close();
        stmt.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }

      // Save the content of the hashmap
      JDBCField[] fields = new JDBCField[5];
      int idx = 0;
      fields[idx++] = new JDBCField("grp", "NUMERIC(2)");
      fields[idx++] = new JDBCField("org", "NUMERIC(10)");
      fields[idx++] = new JDBCField("dst", "NUMERIC(10)");
      fields[idx++] = new JDBCField("ldmode", "NUMERIC(2)");
      fields[idx++] = new JDBCField("cost", "NUMERIC(10,3)");
      
      JDBCUtils.createTable("tmp", fields);
      String sqlStmt = "INSERT INTO tmp  VALUES (?,?,?,?,?)";

      try {
        PreparedStatement prepStmt = jdbcConnection.prepareStatement(sqlStmt);
        Set<Entry<String, Record>> hashSet = hm.entrySet();
        for (Entry<String, Record> entry : hashSet) {
          Record record = entry.getValue();

          idx = 1;
          prepStmt.setInt(idx++, record.grp);
          prepStmt.setInt(idx++, record.org);
          prepStmt.setInt(idx++, record.dst);
          prepStmt.setInt(idx++, record.mode);
          prepStmt.setDouble(idx++, record.cost);

          prepStmt.executeUpdate();
        }

        prepStmt.close();
        if (!jdbcConnection.getAutoCommit()) {
          jdbcConnection.commit();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      System.out.println("table created");

      // Create wide table, with the observed quantities per mode
      String[] sqlCommands2 = [
        "create index if not exists tmpidx on tmp (grp,org,dst)",\
        "@@dstTable := " + dstTable,\
        "@@od1Table := " + od1Table,\
        "@@od2Table := " + od2Table,\
        "@@od3Table := " + od3Table,\
        "# Create wide table",\
        "drop table if exists tmp2",\
        "create table tmp2 as (select org, dst, grp from tmp group by org,dst,grp order by org,dst,grp) with data",\
        "alter table tmp2 add cost1 DECIMAL(13,3)",\
        "alter table tmp2 add cost2 DECIMAL(13,3)",\
        "alter table tmp2 add cost3 DECIMAL(13,3)",\
        "alter table tmp2 add qty1 DECIMAL(13,3) default 0",\
        "alter table tmp2 add qty2 DECIMAL(13,3) default 0",\
        "alter table tmp2 add qty3 DECIMAL(13,3) default 0",\
        "create index if not exists tmp2idx on tmp2 (grp,org,dst)",\
        "#Make sure OD tables have one single entry for each grp,org,dst combination",\
        "drop table if exists od1",\
        "create table od1 as (select grp,org,dst, sum(qty) as qty from @@od1Table group by grp, org, dst) with data",\
        "drop table if exists od2",\
        "create table od2 as (select grp,org,dst, sum(qty) as qty from @@od2Table group by grp, org, dst) with data",\
        "drop table if exists od3",\
        "create table od3 as (select grp,org,dst, sum(qty) as qty from @@od3Table group by grp, org, dst) with data",\
        "create index if not exists od1idx on od1 (grp,org,dst)",\
        "create index if not exists od2idx on od2 (grp,org,dst)",\
        "create index if not exists od3idx on od3 (grp,org,dst)",\
        "# Update qty",\
        "update tmp2 set qty1 = (select qty from od1 where tmp2.grp=od1.grp and tmp2.org=od1.org and tmp2.dst = od1.dst)",\
        "update tmp2 set qty2 = (select qty from od2 where tmp2.grp=od2.grp and tmp2.org=od2.org and tmp2.dst = od2.dst)",\
        "update tmp2 set qty3 = (select qty from od3 where tmp2.grp=od3.grp and tmp2.org=od3.org and tmp2.dst = od3.dst)",\
        "# Update costs",\
        "update tmp2 set cost1 = (select cost from tmp where tmp2.grp=tmp.grp and tmp2.org=tmp.org and tmp2.dst = tmp.dst and tmp.ldmode=1)",\
        "update tmp2 set cost2 = (select cost from tmp where tmp2.grp=tmp.grp and tmp2.org=tmp.org and tmp2.dst = tmp.dst and tmp.ldmode=2)",\
        "update tmp2 set cost3 = (select cost from tmp where tmp2.grp=tmp.grp and tmp2.org=tmp.org and tmp2.dst = tmp.dst and tmp.ldmode=3)",\
        "# Clean temporary tables",\
        "drop table if exists od1",\
        "drop table if exists od2",\
        "drop table if exists od3",\
        "drop table if exists tmp",\
        "# Rename final table",\
        "drop table if exists @@dstTable",\
        "alter table tmp2 rename to @@dstTable"\
      ];

      success = sqlConsole.runBatch(sqlCommands2);
      if (!success) {
          return;
      }

      System.out.println("Done.");
    }
  }
}
 
new CreateMLogitInput_(nodusMapPanel);
 
