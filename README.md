# Nodus 7.0

Nodus is a transportation network modeling software especially designed for multimodal and
intermodal freight transport. It is developed at the Center for Operations Research and 
Econometrics [CORE](https://uclouvain.be/fr/node/4474) of the Université catholique de Louvain
([UCL](http://www.uclouvain.be/), Belgium)). The software is developed  and maintained mainly by 
[Pr Bart Jourquin](https://uclouvain.be/en/directories/bart.jourquin). 
  

## Introduction

Nodus ([Screenshots](http://htmlpreview.github.com/?https://github.com/jourquin/Nodus/blob/master/doc/images/screenshots.html)) implements the "Virtual networks" methodology 
developed at UCL, an alternative to the classical "four steps" technique to model multimodal and 
intermodal transport flows over networks, as it combines the "modal-choice" and "assignment" phases 
of the latter in a single step.

This methodology has already lead to numerous policy-oriented studies on large scale multi-modal 
freight transport networks, such as:

- Regional freight transport planning
- Cost benefit analysis for transport infrastructures
- Optimal locations for intermodal terminals
- Impact of climate change on inland waterways transport
- Internalization of external costs and its potential impact on modal choice
- Estimation of market areas of container hubs
- ...

Numerous scientific articles has been written in which Nodus was used. Most of these papers,
along with contributions to congresses and seminars can be found on 
[Research Gate](https://www.researchgate.net/profile/B_Jourquin).

## Key features

- Compatible with GIS standards: shape files and web mapping, using [OpenMap](http://openmap-java.org/).
- Parallelized algorithms: able to handle very large networks.
- Portable: Linux-Mac-Windows
- Open API: available through scripting (using the [Groovy](http://groovy-lang.org/) language) or plugin’s
- JDBC: compatible with most DBMS’s. Shipped with [HSQLDB](http://hsqldb.org/),
[H2](http://h2database.com/) and [Apache Derby](https://db.apache.org/derby/).  
- Flexible: user defined database fields, variables, cost functions, mode choice models…

See also the [documentation](http://htmlpreview.github.com/?https://github.com/jourquin/Nodus/blob/master/doc/help.html).

## Install and use

Download the [Nodus installer](https://github.com/jourquin/Nodus/releases).
As the software is written in the [Java](https://java.com/en/download/) programming language, the 
latest must be installed on your computer. Nodus needs Java 7 or later.

The software has a modern and integrated user friendly GUI. Complete reference and user guides
are not available, but the API is fully documented. A  sample Nodus project can
be found in the "demo" directory. 
   

## License

You can redistribute it and/or modify Nodus 7.x under the terms of the GNU General Public License 
as published by the Free Software Foundation, either [version 3](https://www.gnu.org/licenses/gpl-3.0.html)
of the License, or (at your option) any later version. 

## Build from sources

The Nodus distribution can be build from the sources. Therefore, you need a Java Development Kit 
([JDK version 7](http://www.oracle.com/technetwork/java/javase/downloads/index.html)) or above. 
You also need Apache [Ant](http://ant.apache.org/). Be sure JAVA_HOME points to your JDK and 
that ant is in your OS path.

Once the GitHib Nodus sources fetched, open a terminal and go to the root of the project. Type:

```
ant Installer
```
This will compile the project, generate the JavaDoc for the API and bundle all what is needed into 
the installer jar file.

It is also possible to only compile the main nodus7.jar using:
 
```
ant Jar
```
This can be useful for instance when some bugs have been fixed in the source tree or some new
enhancements have been introduced without any change in the used external libraries.

You can also import Nodus as an [Eclipse](http://www.eclipse.org/) project.
   
## Uninstall

The software doesn't modify the "registry" of any supported OS (Mac OS, Linux or Windows). Just
delete the installation directory to remove the software from your system. You can
also delete the small ".nodus7.properties" file that is located at the root of your "home" dir.   

