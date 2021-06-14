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

import NodusUtils as nu
import pandas as pd
import numpy as np
import biogeme.biogeme as bio
import biogeme.database as db
import biogeme.models as models
from biogeme.expressions import Beta
from py4j.java_gateway import JavaGateway, Py4JNetworkError

# This Python script estimates the parameters of a condtional logit model
# based on a single variabls gathered from an uncalibrated assignment using
# the Biogeme toolbox:
# Bierlaire, M. (2020). A short introduction to PandasBiogeme. Technical report TRANSP-OR 200605. Transport and Mobility Laboratory, ENAC, EPFL.
#
# The input data is a table created by the "CreateBiogemeInput.sql" script, that transforms
# the "wide format" data stored in the "mlogit_input" table created for the R script.
#
# This Python script also shows how to connect Python to a running instance of Nodus, from which the
# input data is fetched through the JDBC connection used by Nodus. 


def run():
    
    # Connect to Nodus (must be running with a Py4J server launched)
    try:
        gateway = JavaGateway(eager_load=True)    
    except Py4JNetworkError:
        print("Nodus is not listening.")
        return
    except Exception:
        print("Another type of problem... maybe with the JVM.")
        return
    
    # A Nodus project must be loaded
    nodusMapPanel = gateway.entry_point
    nodusProject = nodusMapPanel.getNodusProject()
    if (not nodusProject.isOpen()):
        print("No open Nodus project found.")
        return
    
    # Use the Nodus JDBC connection
    conn = nu.getNodusJDBCConnection(gateway)
      
    # Solve for the two groups present in the input table
    for g in range(2): 
      
        # Load the data for the current group into a data frame
        df = pd.read_sql_query("select * from biogeme_input where grp = " + str(g), conn)
        
        # Replace NA values 
        df = df.fillna(df.max())
        
        # Convert column names to lower case 
        df.columns = map(str.lower, df.columns)
         
        # Use column names as Python variables 
        database = db.Database('data', df)
        globals().update(database.variables)
            
        # Transform costs to their logs
        df['cost1'] = np.log(df['cost1'])
        df['cost2'] = np.log(df['cost2'])
        df['cost3'] = np.log(df['cost3'])
    
        # Parameters to estimate (use same names as for the R solution) 
        INTERCEPT1 = Beta('(intercept).1', 0, None, None, 1)
        INTERCEPT2 = Beta('(intercept).2', 0, None, None, 0)
        INTERCEPT3 = Beta('(intercept).3', 0, None, None, 0)
        B_COST = Beta('log(cost)', 0, None, None, 0)
    
        # Utility functions
        V1 = INTERCEPT1 + B_COST * cost1   
        V2 = INTERCEPT2 + B_COST * cost2
        V3 = INTERCEPT3 + B_COST * cost3 
        V = {1: V1, 2: V2, 3: V3}
        
        # Run the weighted conditional logit
        av = {1: avail1, 2: avail2, 3: avail3}
        logprob = models.loglogit(V, av, choice)
        formulas = {'loglike': logprob, 'weight': qty}
        biogeme = bio.BIOGEME(database, formulas)
        biogeme.modelName = 'LogCost'
        results = biogeme.estimate()
    
        # Format the results for a cut&paste into a Nodus costs file
        betas = results.getBetaValues()
        keys = list(betas.keys())
        values = list(betas.values())
        for i in range(len(keys)):
            k = keys[i]
            v = values[i]
            if "intercept" in k:
                print (str(k) + "." + str(g) + " = " + str(v))
            else:
                # Repeat the same estimator for the 3 modes
                for m in range(3):
                    print (str(k) + "." + str(m+1) + "." + str(g) + " = " + str(v))
            

if __name__ == "__main__":
    run()
