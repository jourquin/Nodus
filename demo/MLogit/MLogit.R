# Copyright (c) 1991-2025 Université catholique de Louvain,
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
# along with this program.  If not, see http://www.gnu.org/licenses/.
#-------------------------------------------------------------------------------


# This R script estimates the parameters of a conditional logit model
# based on a single variable gathered from an uncalibrated assignment.
# See https://cran.r-project.org/web/packages/mlogit/vignettes/mlogit.pdf

library(Formula)
library(mlogit)
library(RJDBC)

# Groups of commodities to estimate
groups <- c(0, 1)

# Set working directory to this script location
try({
  this.dir <- dirname(parent.frame(2)$ofile)
  setwd(this.dir)
},
silent = TRUE
)

# Create JDBC connection to the HSQLDB database engine. Note that Nodus
# must run with the "demo" project loaded.
drv <- JDBC("org.hsqldb.jdbcDriver",
            "../../lib/hsqldb.jar")
conn <- dbConnect(drv, "jdbc:hsqldb:hsql://localhost/demo", "SA", "")            

# Load data and transform all column name to lower cases
data <- dbReadTable(conn, "MLOGIT_INPUT")
names(data)[] <- tolower(names(data)[])


# Prepare output files : the first will contain the results of the model,
# the second only the estimated parameters (can be directly copied in the cost function file)
txtFile1 <- "mlogit.txt"
txtFile2 <- "mlogit_coefs.txt"

if (file.exists(txtFile1)) {
  file.remove(txtFile1)
}

if (file.exists(txtFile2)) {
  file.remove(txtFile2)
}

for (i in 1:length(groups)) {
  group <- groups[i]
  
  print(paste("Solving model for group", group))
  
  
  # Get the data for current group
  x <- data[data$grp == group, ]
  
  # Replace NA values for quantities
  x$qty1[is.na(x$qty1)] <- 0
  x$qty2[is.na(x$qty2)] <- 0
  x$qty3[is.na(x$qty3)] <- 0
  
  # Replace null quantities with a small one
  smallQty <- 0.001
  x$qty1[x$qty1 == 0] <- smallQty
  x$qty2[x$qty2 == 0] <- smallQty
  x$qty3[x$qty3 == 0] <- smallQty
  
  # Replace missing costs with an high value
  highValue <- max(x$cost1, x$cost2, x$cost3, na.rm = TRUE) * 100
  x$cost1[is.na(x$cost1)] <- highValue
  x$cost2[is.na(x$cost2)] <- highValue
  x$cost3[is.na(x$cost3)] <- highValue
  
  # Create wideData data, with one record per mode for each OD pair
  wideData <- data.frame()
  for (mode in 1:3) {
    wd <- data.frame(mode = integer(nrow(x)))
    wd$mode <- mode
    
    wd$cost.1 <- x$cost1
    wd$cost.2 <- x$cost2
    wd$cost.3 <- x$cost3
    
    wd$qtytot <- x$qtytot
    wd$tons <- x$qty1
    if (mode == 2) {
      wd$tons <- x$qty2
    } else if (mode == 3) {
      wd$tons <- x$qty3
    }
    
    wideData <- rbind(wideData, wd)
  }
  
  
  # Create the "long" format data
  longData <-
    dfidx(wideData,
                choice = "mode",
                shape = "wide",
                varying = 2:4
    )
  
  # Define the formula to estimate (logs of costs + intercept)
  f <- Formula(mode ~ log(cost) | 1 | 1)
  
  # Solve the model
  model <- mlogit(f, longData, weights = tons)
  
  # Retrieve the output of the model for this group and save it
  sink(txtFile1, append = TRUE)
  print(paste("Summary for group", group))
  print(summary(model))
  cat("\n\n\n")
  sink()
  
  # Retrieve the coefficients and save them to be useable the Nodus MLogit plugin.
  sink(txtFile2, append = TRUE)
  
  for (j in 1:3) {
    # There is no intercept for the reference mode (1)
    if (j > 1) {
      coefName <- paste("(Intercept):", j, sep = "")
      coefValue <- coef(model)[coefName]
      nodusVarName <- paste("(intercept).", j, ".", group, sep = "")
      cat(nodusVarName, " = ", coef(model)[coefName], "\n")
    }
    # Conditional logit => same estimator for all modes
    coefName <- "log(cost)"
    coefValue <- coef(model)[coefName]
    nodusVarName <- paste("log(cost).", j, ".", group, sep = "")
    cat(nodusVarName, " = ", coef(model)[coefName], "\n")
  }
  sink()
}
