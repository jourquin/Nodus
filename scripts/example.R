require(J4R)

# Connect to the server previously launched by Nodus
connectToJava(port = 18000:18001, internalPort = 50000:50001, public=T, key=212)

# get the entry point, an instance of a NodusMapPanel
nodusMapPanel <- getMainInstance()

# Get the loaded project in Nodus
nodusProject <- nodusMapPanel$getNodusProject()

# Test if a project is loaded
if (!nodusProject$isOpen()) {
	stop("No project is open.")
}

# Print the name of the project 
cat(paste("Name of the project:", nodusProject$getName(), "\n"))

# List all the node layers of the project
cat("\nList of node layers:\n")
nodes = getAllValuesFromArray(nodusProject$getNodeLayers())
for (i in 1:length(nodes)) {
	layer <- nodes[i]
	cat(paste("- ", layer$getName(), "\n"))
}

# Don't display the first node layer
centroids <- nodes[1]
centroids$setVisible(FALSE)

# Display the data associated to the 10 first nodes of the first node layer
cat("\nData associated to the 10 first nodes of the first layer:")
dbf <- centroids$getModel()
records = data.frame()
for (i in 1:dbf$getRowCount()) {
	record <- callJavaMethod(dbf,"getRecord", as.integer(i))
	rawData <- getAllValuesFromArray(callJavaMethod(record,"toArray"))
	records <- rbind(records, rawData)
	if (i == 10) {
		break
	}
}
colnames(records) <- NULL
print(records, col.names=FALSE)

shutdownClient()
