# Copyright (c) 1991-2018 Université catholique de Louvain, 
# Center for Operations Research and Econometrics (CORE)
# http://www.uclouvain.be
# 
# This file is part of Nodus.
# 
# Nodus is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#-------------------------------------------------------------------------------


# This R script estimates the parameters of a multonomial logit model
# based on simple variables gathered from an uncalibrated assignment.
# See https://cran.r-project.org/web/packages/mlogit/vignettes/mlogit.pdf

library(RJDBC)
library(mlogit)

# Groups of commodities to estimate
groups = c(0, 1)

# Input table (created by the Groovy script)
inputTable <- "mlogit_input"

# Credentials and db name (note that hsqldb.jar must be on the path used by R)
userName = "SA"
pwd = ""
db = "../demo_hsqldb"

# Prepare output files : the first will contain the results of the model,
# the second only the estimated parameters (can be directly copied in the cost function file)
outputTable = "mnlogit"
txtFile1 = "mlogit.txt"
txtFile2 = "mlogit_coefs.txt"

if (file.exists(txtFile1))
  file.remove(txtFile1)

if (file.exists(txtFile2))
  file.remove(txtFile2)

for (i in 1:length(groups)) {
  group = groups[i]
  
  print(paste("Solving model for group", group))
  
  
  # Load the data
  drv <- JDBC("org.hsqldb.jdbcDriver", "./hsqldb.jar")
  con <-
    dbConnect(drv,
              paste("jdbc:hsqldb:file:", db, ";shutdown=true", sep = ""),
              user = "SA")
  
  sqlStmt = paste("select * from ", inputTable, "where grp = ", group)
  x = suppressWarnings(dbGetQuery(con, sqlStmt))
  dbDisconnect(con)
  
  # Transform all column name to lower cases
  names(x)[] <- tolower(names(x)[])
  
  # Transform durations in hours
  x$duration1 = x$duration1 / 3600
  x$duration2 = x$duration2 / 3600
  x$duration3 = x$duration3 / 3600
  
  # Replace NA values for quantities
  x$qty1[is.na(x$qty1)] = 0
  x$qty2[is.na(x$qty2)] = 0
  x$qty3[is.na(x$qty3)] = 0
  
  # Replace null quantities with a small one
  smallQty = 0.1
  x$qty1[x$qty1 == 0] = smallQty
  x$qty2[x$qty2 == 0] = smallQty
  x$qty3[x$qty3 == 0] = smallQty
  
  # Replace missing costs and durations with an high value
  highValue = .Machine$integer.max
  
  x$cost1[is.na(x$cost1)] = highValue
  x$cost2[is.na(x$cost2)] = highValue
  x$cost3[is.na(x$cost3)] = highValue
  
  x$duration1[is.na(x$duration1)] = highValue
  x$duration2[is.na(x$duration2)] = highValue
  x$duration3[is.na(x$duration3)] = highValue
  
  # Create wideData data, with one record per mode for each OD pair
  wideData <- data.frame()
  for (mode in 1:3) {
    wd <- data.frame(mode = integer(nrow(x)))
    wd$mode = mode
    
    wd$cost.1 = x$cost1
    wd$cost.2 = x$cost2
    wd$cost.3 = x$cost3
    
    wd$duration.1 = x$duration1
    wd$duration.2 = x$duration2
    wd$duration.3 = x$duration3
    
    wd$qtytot = x$qtytot
    wd$tons = x$qty1
    if (mode == 2)
      wd$tons = x$qty2
    else if (mode == 3)
      wd$tons = x$qty3
    
    wideData = rbind(wideData, wd)
  }
  
  # Create the "long" format data
  longData <-
    mlogit.data(wideData,
                choice = "mode",
                shape = "wide",
                varying = 2:7)
  
  # Define the formula to estimate (logs of cost and duration + intercept)
  f = formula(mode ~ 1 | 1 | log(cost) + log(duration))
  
  # Solve the model
  model = mlogit(f, longData, weights = tons)
  
  # Retrieve the output of the model for this group and save it
  sink(txtFile1, append = TRUE)
  print(paste("Summary for group", group))
  print(summary(model))
  cat("\n\n\n")
  sink()
  
  # Retrieve the coefficients and save them to be useable the Nodus MLogit plugin.
  sink(txtFile2, append = TRUE)
  
  c = coef(model)
  for (j in 1:length(c)) {
    paramName = substr(names(c[j]),3,nchar(names(c[j])))
    mode = substr(names(c[j]),1,1)
    paramName = paste(paramName, ".", mode, ".", group, sep = "")
    cat(paramName, " = ")
    cat(unname(c[j]), "\n")
  }
  cat("\n")
  sink()
  
}
