#!/usr/bin/python3

#
# Copyright (c) 1991-2021 Université catholique de Louvain
#
# <p>Center for Operations Research and Econometrics (CORE)
#
# <p>http://www.uclouvain.be
#
# <p>This file is part of Nodus.
#
# <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
# General Public License as published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# <p>You should have received a copy of the GNU General Public License along with this program. If
# not, see http://www.gnu.org/licenses/.
#


from py4j.java_gateway import JavaGateway

def run():
    
    # Connect to Nodus
    gateway = JavaGateway()
    
    # Get the Nodus project from the main entry point, which is a NodusMapPanel
    nodusMapPanel = gateway.entry_point
    nodus_project = nodusMapPanel.getNodusProject()
    
    # Test if a project is loaded
    if not nodus_project.isOpen():
        print("No project is open.")
        return
       
    # Print the name of the project 
    print("Name of the project:", nodus_project.getName())
    print()
    
    # List all the node layers of the project
    print("List of node layers:")
    nodes = nodus_project.getNodeLayers()
    for i in range(len(nodes)):
        layer = nodes[i]
        print("- " + layer.getName())
    print()
    
    # Don't display the first node layer
    firstLayer = nodes[0]
    firstLayer.setVisible(False)
    
    # Display the data associated to the 10 first nodes of the first node layer
    print("Data associated to the 10 first nodes of the first layer:")
    dbf = firstLayer.getModel()
    c = dbf.getColumnCount()
    for i in range(dbf.getRowCount()):
        if i == 10:
            break
        record = dbf.getRecord(i)
        s = "- "
        for j in range(c):
            s += str(record.get(j)) + " "
        print(s)

if __name__ == "__main__":
    run()