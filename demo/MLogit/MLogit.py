#!/usr/bin/python3

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

import os
import tempfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
HSQLDB_JAR = SCRIPT_DIR.parent.parent / "lib" / "hsqldb-2.7.4.jar"
MPLCONFIGDIR = Path(tempfile.gettempdir()) / "nodus-matplotlib"
MPLCONFIGDIR.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(MPLCONFIGDIR))
UNAVAILABLE_UTILITY = -1000

try:
    import jaydebeapi as jdbc
    import pandas as pd
    import numpy as np
    import biogeme.biogeme as bio
    import biogeme.database as db
    import biogeme.models as models
    from biogeme.expressions import Beta, Variable
    from biogeme.results_processing import get_pandas_estimated_parameters
except ModuleNotFoundError as exc:
    package_names = {
        "jaydebeapi": "JayDeBeApi",
        "pandas": "pandas",
        "numpy": "numpy",
        "biogeme": "biogeme",
    }
    missing_package = package_names.get(exc.name, exc.name)
    raise SystemExit(
        f"Missing Python package: {missing_package}\n"
        "Install the demo dependencies with:\n"
        "  python3 -m pip install -r requirements.txt\n"
        "or install the missing package directly with:\n"
        f"  python3 -m pip install {missing_package}"
    ) from exc

# This Python script estimates the parameters of a conditional logit model
# based on a single variable gathered from an uncalibrated assignment using
# the Biogeme toolbox (https://biogeme.epfl.ch)
#
# The input data is the "wide format" mlogit_input table created by CreateMLogitInput.groovy.
# Each row represents one OD pair and one commodity group. The observed quantities by mode are
# converted to modal shares, and Biogeme estimates the model with one row per OD pair.
#
# This Python script also shows how to connect Python to a running Nodus database engine (HSQLDB in this case)
# using a JDBC connection.

def run():
    os.chdir(SCRIPT_DIR)

    # Connect to the Nodus HSQLDB server (must be running)
    conn = jdbc.connect("org.hsqldb.jdbcDriver",
                           "jdbc:hsqldb:hsql://localhost/demo",
                           ["SA", ""],
                           str(HSQLDB_JAR),)
     
    # Solve for the two groups present in the input table
    for g in range(2): 
        
        # Load the wide data for the current group into a data frame
        curs = conn.cursor()
        curs.execute("select * from mlogit_input where grp = " + str(g))
        columns = [desc[0] for desc in curs.description] # Get column headers
        # Convert the list of tuples to a df
        wide_df = pd.DataFrame(curs.fetchall(), columns=columns)
        curs.close()

        # Convert column names to lower case 
        wide_df.columns = map(str.lower, wide_df.columns)

        for column in ['qty1', 'qty2', 'qty3']:
            wide_df[column] = wide_df[column].fillna(0)

        if wide_df.empty:
            print(f"No data for group {g}.")
            continue

        # A mode is available when its cost is present and strictly positive.
        for mode in range(1, 4):
            cost_column = f'cost{mode}'
            wide_df[f'avail{mode}'] = (
                wide_df[cost_column].notna() & (wide_df[cost_column] > 0)
            ).astype(int)

            inconsistent_rows = (wide_df[f'avail{mode}'] == 0) & (wide_df[f'qty{mode}'] > 0)
            if inconsistent_rows.any():
                raise ValueError(
                    f"Group {g} contains {inconsistent_rows.sum()} rows with "
                    f"qty{mode} > 0 while cost{mode} is missing or not positive."
                )

        wide_df['total_qty'] = wide_df[['qty1', 'qty2', 'qty3']].sum(axis=1)
        wide_df = wide_df[wide_df['total_qty'] > 0].copy()
        if wide_df.empty:
            print(f"No positive quantities for group {g}.")
            continue

        for mode in range(1, 4):
            wide_df[f'share{mode}'] = wide_df[f'qty{mode}'] / wide_df['total_qty']

        cost_columns = ['cost1', 'cost2', 'cost3']
        high_value = wide_df[cost_columns].where(wide_df[cost_columns] > 0).max(skipna=True).max() * 1000
        if not np.isfinite(high_value) or high_value <= 0:
            raise ValueError(f"No strictly positive costs found for group {g}.")
        wide_df[cost_columns] = wide_df[cost_columns].fillna(high_value)
        for column in cost_columns:
            wide_df[column] = wide_df[column].where(wide_df[column] > 0, high_value)

        # Transform costs to their logs
        wide_df['cost1'] = np.log(wide_df['cost1'])
        wide_df['cost2'] = np.log(wide_df['cost2'])
        wide_df['cost3'] = np.log(wide_df['cost3'])

        # The row weight is the total observed flow for the OD pair and commodity group.
        wide_df['weight'] = wide_df['total_qty']

        database = db.Database('data', wide_df)
        cost1 = Variable('cost1')
        cost2 = Variable('cost2')
        cost3 = Variable('cost3')
        avail1 = Variable('avail1')
        avail2 = Variable('avail2')
        avail3 = Variable('avail3')
        share1 = Variable('share1')
        share2 = Variable('share2')
        share3 = Variable('share3')
        weight = Variable('weight')
        
        # Parameters to estimate (use same names as for the R solution) 
        INTERCEPT1 = Beta('(Intercept).1', 0, None, None, 1)
        INTERCEPT2 = Beta('(Intercept).2', 0, None, None, 0)
        INTERCEPT3 = Beta('(Intercept).3', 0, None, None, 0)
        B_COST = Beta('log(cost)', 0, None, None, 0)
    
        # Utility functions. Unavailable modes receive a very negative utility so their
        # probability is effectively zero while the share-weighted likelihood remains finite.
        V1 = avail1 * (INTERCEPT1 + B_COST * cost1) + (1 - avail1) * UNAVAILABLE_UTILITY
        V2 = avail2 * (INTERCEPT2 + B_COST * cost2) + (1 - avail2) * UNAVAILABLE_UTILITY
        V3 = avail3 * (INTERCEPT3 + B_COST * cost3) + (1 - avail3) * UNAVAILABLE_UTILITY
        V = {1: V1, 2: V2, 3: V3}
        
        # Run the weighted conditional logit on the wide table.
        #
        # Each OD row contributes the observed modal-share weighted log-likelihood:
        #   share1 * log(P1) + share2 * log(P2) + share3 * log(P3)
        # multiplied by the total observed quantity of the row.
        logprob = (
            share1 * models.loglogit(V, None, 1)
            + share2 * models.loglogit(V, None, 2)
            + share3 * models.loglogit(V, None, 3)
        )
        formulas = {'loglike': logprob, 'weight': weight}
        biogeme = bio.BIOGEME(
            database,
            formulas,
            generate_html=False,
            generate_yaml=False,
            save_iterations=False,
        )
        biogeme.model_name = 'LogCost-' + str(g)
        results = biogeme.estimate()
        
        with pd.option_context('expand_frame_repr', False):
            print(get_pandas_estimated_parameters(estimation_results=results))
            
    conn.close()
        
if __name__ == "__main__":
    run()
