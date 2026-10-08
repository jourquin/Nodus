#
# Copyright (c) 1991-2026 Université catholique de Louvain
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

require(J4R)

# Read the key saved by Nodus for the explicitly enabled project bridge.
preferences <- path.expand("~/.nodus9.properties")
if (!file.exists(preferences)) {
    stop(paste("Start Nodus first; preferences file is missing:", preferences))
}
settings <- readLines(preferences, warn=FALSE, encoding="latin1")
keyLines <- settings[startsWith(settings, "bridge.j4r.key=")]
if (length(keyLines) == 0) {
    stop(paste("No bridge.j4r.key in", preferences))
}
keyText <- sub("^[^=]*=", "", tail(keyLines, 1))
rKey <- suppressWarnings(as.integer(keyText))
if (!grepl("^[0-9]+$", keyText) || is.na(rKey) || rKey <= 0) {
    stop(paste("Invalid bridge.j4r.key in", preferences))
}
connectToJava(port = 18000:18001, internalPort = 50000:50001,
              public=TRUE, key=rKey)

# Get the entry point, an instance of a NodusMapPanel
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
firstLayer <- nodes[1]
firstLayer$setVisible(FALSE)

# Display the data associated to the 10 first nodes of the first node layer
cat("\nData associated to the 10 first nodes of the first layer:")
dbf <- firstLayer$getModel()
records = data.frame()
for (i in 1:dbf$getRowCount()) {
	record <- callJavaMethod(dbf,"getRecord", as.integer(i))
	rawData <- getAllValuesFromArray(callJavaMethod(record,"toArray"))
	records <- rbind(records, rawData, stringsAsFactors=FALSE)
	if (i == 10) {
		break
	}
}
colnames(records) <- NULL
print(records, col.names=FALSE)

shutdownClient()
