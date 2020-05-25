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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AllOrNothingAssignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

public class AddDistancesToODTable_ {

	/**
	 * The name of the OD table.
	 */
	String odTableName = "od";

	/**
	 * The number of parallel threads (depends on CPU cores
	 */
	int nbThreads = 2;

	public AddDistancesToODTable_(NodusMapPanel nodusMapPanel) {
		NodusProject nodusProject = nodusMapPanel.getNodusProject();
		if (nodusProject.isOpen()) {

			new NodusConsole();
			System.out.println("Add distances to OD matrix");
			System.out.println("--------------------------\n");
			try {
				// get JDBC connection to the project's database
				Connection jdbcConnection = nodusProject.getMainJDBCConnection();

				// Get a database compliant table name (upper/lower case)
				odTableName = JDBCUtils.getCompliantIdentifier(odTableName);

				// Create a temporary table that will contain all the OD pairs
				System.out.println("Create temporary OD table");

				String tmpOD = "tmpOD";
				JDBCField[] field = new JDBCField[4];
				field[0] = new JDBCField(NodusC.DBF_GROUP, "NUMERIC(2,0)");
				field[1] = new JDBCField(NodusC.DBF_ORIGIN, "NUMERIC(10,0)");
				field[2] = new JDBCField(NodusC.DBF_DESTINATION, "NUMERIC(10,0)");
				field[3] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(10,0)");

				JDBCUtils.createTable(tmpOD, field);
				String org = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN);
				String dst = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION);

				// Query the existing OD table to fill the temporary one
				String sqlStmt = "SELECT " + org + ", " + dst + " FROM " + odTableName + " GROUP BY " + org + ", " + dst;

				Statement stmt = jdbcConnection.createStatement();
				PreparedStatement pStmt = jdbcConnection
				        .prepareStatement("insert into " + JDBCUtils.getQuotedCompliantIdentifier(tmpOD) + " values(?,?,?,?)");

				ResultSet rs = stmt.executeQuery(sqlStmt);
				while (rs.next()) {
					pStmt.setInt(1, 0); // All for same group 0
					pStmt.setInt(2, JDBCUtils.getInt(rs.getObject(1)));
					pStmt.setInt(3, JDBCUtils.getInt(rs.getObject(2)));
					pStmt.setInt(4, 1); // Just 1 ton
					pStmt.executeUpdate();
				}
				pStmt.close();

				// Create properties for the cost function to use. The cost is
				// simply equal to the distance

				System.out.println("Create cost functions");

				Properties costFunctions = new Properties();

				for (int i = 0; i < NodusC.MAXMM; i++) { // For all the
					// possible modes
					String key, value;

					// Loading
					key = "ld." + i + ",1";
					value = "0";
					costFunctions.setProperty(key, value);

					// Unloading
					key = "ul." + i + ",1";
					value = "0";
					costFunctions.setProperty(key, value);

					// Transit
					key = "tr." + i + ",1";
					value = "0";
					costFunctions.setProperty(key, value);

					// Moving
					key = "mv." + i + ",1";
					value = NodusC.VARNAME_LENGTH;
					costFunctions.setProperty(key, value);
				}

				// Perform an AoN assignment, keeping the detailed path headers
				System.out.println("Compute distances");

				int scenario = 99;
				AssignmentParameters ap = new AssignmentParameters(nodusProject);
				ap.setScenario(scenario);
				ap.setSavePaths(true);
				ap.setDetailedPaths(false);
				ap.setWhereStmt("");
				ap.setODMatrix(tmpOD);
				ap.setCostFunctions(costFunctions);
				ap.setConfirmDelete(false);
				ap.setThreads(nbThreads);

				AllOrNothingAssignment aon = new AllOrNothingAssignment(ap);
				aon.run();

				// Retrieve the path header info to create the new od table
				System.out.println("Create new OD table");

				String odDst = JDBCUtils.getQuotedCompliantIdentifier(odTableName + "_dst");
				JDBCUtils.dropTable(odTableName + "_dst");
				field = new JDBCField[5];
				field[0] = new JDBCField(NodusC.DBF_GROUP, "NUMERIC(2,0)");
				field[1] = new JDBCField(NodusC.DBF_ORIGIN, "NUMERIC(10,0)");
				field[2] = new JDBCField(NodusC.DBF_DESTINATION, "NUMERIC(10,0)");
				field[3] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(10,0)");
				field[4] = new JDBCField(NodusC.DBF_LENGTH, "NUMERIC(8,3)");

				JDBCUtils.createTable(odDst, field);

				System.out.println("Fill new OD table");

				// Example query:
				// insert into od_dst('grp', 'org', 'dst', 'qty', 'length')
				// select od.grp, od.org, od.dst, od.qty,
				// path99_header.length from od
				// inner join path99_header on od.org = path99_header.org
				// and od.dst = path99_header.dst

				String grp = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP);
				String qty = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY);
				String length = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LENGTH);

				sqlStmt = "INSERT INTO " + odDst + "(" + grp + ", " + org + ", " + dst + ", " + qty + ", " + length + ")";

				org = JDBCUtils.getCompliantIdentifier(NodusC.DBF_ORIGIN);
				dst = JDBCUtils.getCompliantIdentifier(NodusC.DBF_DESTINATION);
				grp = JDBCUtils.getCompliantIdentifier(NodusC.DBF_GROUP);
				qty = JDBCUtils.getCompliantIdentifier(NodusC.DBF_QUANTITY);
				length = JDBCUtils.getCompliantIdentifier(NodusC.DBF_LENGTH);

				String defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
				String name = nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, defValue);
				String pathHeaderTableName = JDBCUtils.getCompliantIdentifier(name + scenario + NodusC.SUFFIX_HEADER);

				sqlStmt += " SELECT " + odTableName + "." + grp + ", " + odTableName + "." + org + ", " + odTableName + "." + dst;
				sqlStmt += ", ROUND(" + odTableName + "." + qty + ",0) , " + pathHeaderTableName + "." + length + " FROM ";
				sqlStmt += odTableName + " INNER JOIN " + pathHeaderTableName + " ON " + odTableName + "." + org + " = ";
				sqlStmt += pathHeaderTableName + "." + org + " AND " + odTableName + ".";
				sqlStmt += dst + " = " + pathHeaderTableName + "." + dst;

				stmt.executeUpdate(sqlStmt);

				stmt.close();

				// Remove assignment output tables
				nodusProject.removeScenario(scenario);

				// Remove temporary od table
				JDBCUtils.dropTable(tmpOD);

				// Done!
				System.out.println("New table " + odDst + " created.");

			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
	}

}

new AddDistancesToODTable_(nodusMapPanel);