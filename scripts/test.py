#!/usr/bin/python3

from py4j.java_gateway import JavaGateway

def run():
    
    # Connect to Nodus
    gateway = JavaGateway()
    
    # Get the Nodus project from the main entry point, which is an instance of a NodusMapPanel
    nodusMapPanel = gateway.entry_point
    nodus_project = nodusMapPanel.getNodusProject()
    
    # Test if a project is loaded
    if not nodus_project.isOpen():
        print("No project is open.")
        return
       
    # Print the name of the project 
    print("Name of the project:", nodus_project.getName())
    
    # List all the node layers of the project
    nodes = nodus_project.getNodeLayers()
    for i in range(len(nodes)):
        layer = nodes[i]
        print(layer.getName())
    
    
    # Don't display the first node layer
    centroids = nodes[0]
    centroids.setVisible(False)

if __name__ == "__main__":
    run()