# A simple user defined modal split method, as a plugin for Nodus

This is an example of how to develop specific modal split methods as plugin's for Nodus. This one is a conditional logit, 
which utility function is explained in the documentation of the "demo" project.

- The explanatory variable (cost) is gathered from an uncalibrated multimodal assignment, i.e., the total travel cost for all the
modes and origin-destination pairs. This information is read from the assignment "header" table, along with the expected quantities
for each mode (in the modal OD matrixes). The result is written in the "mlogit_input" table by the "CreateMLogitInput.groovy" 
script.

- The estimators are then computed using by the "MLogit.R" script. To run it, [R](https://www.r-project.org/) must be installed on 
your computer, along with the [RJDBC](https://cran.r-project.org/package=RJDBC) and
[mlogit](https://cran.r-project.org/package=mlogit) packages. The script produces the "mlogit.txt" file, containing the output of
the estimated models (one for each group of commodities). The estimators are also stored in "mlogit_coefs.txt". 
The content of this file can be cut & pasted in a Nodus cost file. 

- The MLogit.java file contains the source code of a user defined modal split method that uses these estimators. It can be compiled 
to generate the MLogit.jar file (which can already be found in the "demo" project directory). This plugin reads the estimators 
stored in the Nodus cost file and applies them to compute the utility of each alternative mode and to estimate their modal share. 

- The logit model can also be solved using the [Biogeme](https://biogeme.epfl.ch) toolbox. This is illustrated by the "MLogit.py" 
[Python](https://www.python.org) script. Before running the Python script, run the "CreateBiogeme.sql" script in Nodus to create the
"biogeme_input" table, a "wide" format presentation of the "mnlogit_input" table. 
