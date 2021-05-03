#!/usr/bin/python3

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
    centroids = nodes[0]
    centroids.setVisible(False)
    
    # Display the data associated to the 10 first nodes of the first node layer
    print("Data associated to the 10 first nodes of the first layer:")
    dbf = centroids.getModel()
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