#JFlowMap companion application

[FlowMap](http://www.visualisingdata.com/resources/jflowmap/) is a research prototype developed
at the University of Fribourg in which one experiment with  various visualization techniques for spatial
 interactions, i.e. interactions between pairs of geographic locations. These can be migrations, 
 movement of goods and people, network traffic, or any kind of entities "flowing" between locations. 

Spatial interactions are often represented as origin-destination data, meaning that only the origins, 
the destinations and the magnitudes of the flows are known, but not the exact flow routes.

The goal of the work is to develop a tool which would help to explore and analyze temporal changes 
in origin-destination data. 

JFlowMap can therefore be used to visualize the origin-destination matrixes used by Nodus. The
example *JFlowMap.groovy* script can be used to prepare data for JFlowMap. Run the script
from within Nodus (you may have to modify the SQL query to set the name of the OD matrix to 
use. A *JFlowMap.jfmv* file will be created in the project's directory. Copy *cntry02.shp* in the same
directory and launch JFlowMap. Then, open from the menu the view configuration files (.jfmv). 