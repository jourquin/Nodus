# A simple user defined modal split method, as a plugin for Nodus

This is an example of how to develop specific modal split methods as plugin's for Nodus. This one is a conditional logit, 
which utility function is explained in the documentation of the "demo" project.

- The explanatory variable is gathered from an uncalibrated multimodal assignment, i.e., the total travel cost for all the modes
 and origin-destination pairs. This information is read from the assignment "header" table, along with the expected quantities for 
 each mode (in the modal OD matrices). The result is written in the "mlogit_input.dbf" file, created in the project directory by 
 the "CreateMLogitInput.groovy" script.

- The estimators are then computed using by the "MLogit.R" script. To run it, [R](https://www.r-project.org/) must be installed on 
your computer, along with the [foreign](https://cran.r-project.org/package=foreign) and
[mlogit](https://cran.r-project.org/package=mlogit) packages. The script produces the "mlogit.txt" file, containing the output of
the estimated models (one for each group of commodities). The estimators are also stored in "mlogit_coefs.txt". 
The content of this file can be cut & pasted in a Nodus cost file. 

- The MLogit.java file contains the source code of a user defined modal split method that uses these estimators. It can be compiled 
to generate the MLogit.jar file (which can already be found in the "demo" project directory). This plugin reads the parameters 
stored in the Nodus cost file and applies them to compute the utility of each alternative mode and to estimate their modal share. 

- The logit model can also be solved using the [Biogeme](https://biogeme.epfl.ch) toolbox. This is illustrated by the "MLogit.py" 
[Python](https://www.python.org) script. Run the "CreateBiogeme.sql" script in Nodus to format the "wide format data" of the 
"mnlogit_input" table to use it with Biogeme. This Python script also illustrates the use of the [Py4J](https://www.py4j.org) bridge 
that allows connecting your Python environment to a running Nodus instance. In this example, the input data is fetched from the 
database used by Nodus through its JDBC connection. 
