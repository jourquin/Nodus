/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;


// Get the loaded project in Nodus
NodusProject nodusProject = nodusMapPanel.getNodusProject();

if (nodusProject.isOpen()) {
	// Print the name of the project
	System.out.println("Name of the project: " + nodusProject.getName() + "\n");

	// List all the node layers of the project
	System.out.println("List of node layers:");
	NodusEsriLayer[] nodes = nodusProject.getNodeLayers();
	for (int i = 0 ; i < nodes.length ; i++) {
		System.out.println("- " + nodes[i].getName());
	}
	System.out.println();

	// Don't display the first node layer
	NodusEsriLayer firstLayer = nodes[0];
	firstLayer.setVisible(false);

	// Display the data associated to the 10 first nodes of the first node layer
	System.out.println("Data associated to the 10 first nodes of the first layer:");
	DbfTableModel model = firstLayer.getModel();
	for (int i = 0 ; i < 10 ; i++) {
		if (i == 10) {
			break;
		}
		for (int j = 0 ; j < model.getColumnCount() ; j++) {
			System.out.print(model.getValueAt(i, j).toString() + " ");
		}

		System.out.println();
	}

}