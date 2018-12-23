/*******************************************************************************
 * Copyright (c) 1991-2019 Université catholique de Louvain, 
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.layer.shape.NodusEsriLayer;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Example script that prepares an input file for JFlowMap with data retrieved from an OD matrix
 */
public class JFlowMap_ {

	/*
	 * The name of the OD table
	 */
	String odTableName = "od";
	
	/*
	 * The shapefile that will be used as background  
	 */
	String backgroundShapeFileName = "bel_nuts2.shp";
	
  /*
   * Query string to retrieve the flows in the OD matrix (set the name of the OD matrix if needed)
   */
  String sqlStmt = "SELECT org, dst, SUM(qty) AS qty FROM " + odTableName + " GROUP BY org, dst";

  NodusProject nodusProject;
  String pathToProject;
  HashMap odHashMap = new HashMap();
  NodusEsriLayer[] nodesLayers;

  public JFlowMap_(NodusMapPanel nodusMapPanel) {
    nodusProject = nodusMapPanel.getNodusProject();
    pathToProject = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    nodesLayers = nodusProject.getNodeLayers();

    new NodusConsole();
    boolean error = true;
    if (prepareFlows()) {
      if (prepareNodes()) {
        if (prepareFlowMapProject()) {
          error = false;
        }
      }
    }
    if (error) {
      System.err.println("Failed to prepare the CSV files for JFlowMap.");
    } else {

      System.out.println("Done.");
    }
  }

  private boolean prepareFlowMapProject() {
    System.out.println("Generate the JFlowMap project file.");

    try {
      PrintWriter out = new PrintWriter(pathToProject + "JFlowMap.jfmv");
      out.println("view=flowmap");
      out.println("data=csv");
      out.println("data.csv.nodes.src=JFlowMapNodes.csv");
      out.println("data.csv.flows.src=JFlowMapFlows.csv");
      out.println("data.attrs.node.id=NodeNum");
      out.println("data.attrs.node.label=NodeNum");
      out.println("data.attrs.node.lat=Lat");
      out.println("data.attrs.node.lon=Lon");
      out.println("data.attrs.flow.origin=Org");
      out.println("data.attrs.flow.dest=Dst");
      out.println("data.attrs.flow.weight.csvList=Qty");
      out.println("view.flowmap.colorScheme=Gray red-green");
      out.println("map=shapefile");
      out.println("map.shapefile.src=" + backgroundShapeFileName); 
      out.println("map.projection=Mercator");
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private boolean prepareNodes() {
    System.out.println("Retrieve all the nodes for which there is a flow.");

    try {
      PrintWriter out = new PrintWriter(pathToProject + "JFlowMapNodes.csv");
      out.println("NodeNum, Lat, Lon");
      Set keys = odHashMap.keySet();
      Iterator it = keys.iterator();
      while (it.hasNext()) {
        int nodeNum = (Integer) it.next();

        // Find the layer and the position of this node in this layer
        for (int i = 0; i < nodesLayers.length; i++) {
          int index = nodesLayers[i].getNumIndex(nodeNum);
          if (index != -1) {
            EsriPoint graphic =
                (EsriPoint) nodesLayers[i].getEsriGraphicList().getOMGraphicAt(index);
            out.println(nodeNum + ", " + graphic.getLat() + ", " + graphic.getLon());
          }
        }
      }
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /** Read the OD matrix */
  private boolean prepareFlows() {

    Connection jdbcConnection = nodusProject.getMainJDBCConnection();

    System.out.println("Read Origin-Destination matrix.");
    try {
      PrintWriter out = new PrintWriter(pathToProject + "JFlowMapFlows.csv");
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);
      out.println("Org, Dst, Qty");
      while (rs.next()) {
        int org = rs.getInt(1);
        int dst = rs.getInt(2);
        long qty = rs.getLong(3);
        out.println(org + ", " + dst + "," + qty);

        // Keep a list of origins and destinations
        if (!odHashMap.containsKey(org)) {
          odHashMap.put(org, null);
        }
        if (!odHashMap.containsKey(dst)) {
          odHashMap.put(dst, null);
        }
      }
      rs.close();
      stmt.close();
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }
}

// Entry point. "nodusMapPanel" is passed by Nodus
new JFlowMap_(nodusMapPanel);
