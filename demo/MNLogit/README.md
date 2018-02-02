###A simple modal split method plugin

This is an example of how to develop specific modal split methods as plugin's for Nodus. This one is a multinomial logit, which utility function is explained in the documentation of the "demo" project.

- The explanatory variables are gathered from an uncalibrated multimodal assignment, i.e., the total travel cost and duration for all the modes and origin-destination pairs. This information is read from the assignment "header" table, along with the expected quantities for each mode (in the modal OD matrices). The result is written in the "mnlogit_input" table created by the "CreateMNLogitInput.groovy" script.

- The estimators are then computed using this table by the "MNLogit.R" script. To run it, [R](https://www.r-project.org/) must be installed on your computer, with the "RJDBC", "mnlogit" and "mlogit" packages. The script produces the "mnlogit.txt" file, containing the output of the estimated models (one for each group of commodities). The estimated parameters are also stored in "mnlogit_coefs.txt". The content of this file can be cut pasted in a Nodus cost file. 

- The MNLogit.java file can be compiled to generate the MNLogit.jar file (which can be found in the "demo" project directory). This plugin reads the parameters stored in the Nodus cost file and applies them to compute the utility of each alternative mode and to estimate their modal share. 
