# Nodus 8.2 <a href="https://zenodo.org/badge/latestdoi/111554354"><img src="https://zenodo.org/badge/111554354.svg" alt="DOI"></a>


Nodus is a transportation network modeling software especially designed for multimodal and
intermodal freight transport. It is developed at the Center for Operations Research and 
Econometrics ([CORE](https://uclouvain.be/fr/node/4474)) of the Université catholique de Louvain
([UCLouvain](https://uclouvain.be/en/index.html), Belgium). The software is developed  and maintained mainly by 
[Pr Bart Jourquin](https://uclouvain.be/en/directories/bart.jourquin). 
  
Beside this [GitHib Pages website](http://nodus.uclouvain.be), the **Nodus installer and sources can be downloaded from** 
[GitHub](https://github.com/jourquin/Nodus/releases).

## Introduction

Nodus ([Screenshots](http://htmlpreview.github.io/?https://github.com/jourquin/Nodus/blob/master/doc/images/screenshots.html)) 
implements the "Virtual networks" methodology developed at UCLouvain, an alternative to the classical "four steps" 
technique to model multimodal and intermodal transport flows over networks, as it combines the "modal choice" 
and "assignment" phases of the latter in a single step.

This methodology has already led to numerous policy-oriented studies on large scale multimodal 
freight transport networks, such as:

- Regional freight transport planning
- Cost benefit analysis for transport infrastructure
- Optimal locations for intermodal terminals
- Impact of climate change on inland waterways transport
- Internalization of external costs and its potential impact on modal choice
- Estimation of market areas of container hubs
- ...

Numerous scientific articles have been written in which Nodus was used. Most of these papers,
along with contributions to congresses and seminars can be found on 
[Research Gate](https://www.researchgate.net/profile/B_Jourquin).

## Key features

- Compatible with GIS standards: shape files and web mapping, using [OpenMap](http://openmap-java.org/).
- Parallelized algorithms: able to handle very large networks.
- Multi-plaform: Linux-Mac-Windows.
- Open API: available through scripting (using the [Groovy](http://groovy-lang.org/) language,
[Python](https://www.python.org) through a [Py4J](https://www.py4j.org/index.html) bridge or
[R](https://www.r-project.org) through a [J4R](https://sourceforge.net/p/repiceasource/wiki/J4R/) bridge) 
or plugins (in Java jar files).
- JDBC: compatible with most DBMS’s. Shipped with [HSQLDB](http://hsqldb.org/),
[H2](http://h2database.com/) and [Apache Derby](https://db.apache.org/derby/).  
- Flexible: user defined database fields, variables, cost functions, mode choice models…

See also the [documentation](http://htmlpreview.github.io/?https://github.com/jourquin/Nodus/blob/master/doc/help.html) and
the [Demo project](https://github.com/jourquin/Nodus/blob/master/demo).

## History of the releases

- 7.0 - November 2017: First open source version of Nodus.
- 7.1 - November 2018: Upgrade to Groovy 2.5.x.
- 7.2 - February 2020: Upgrade to Groovy 3.x.
- 8.0 - February 2021: Introduce time functions (in addition to cost functions). Simplified API for modal-choice plugins. 
Many under the hood improvements.
- 8.1 - April 2021: Runs on Java 16 and allows Python scripting through a Py4J bridge and R scripting through a J4R bridge in addition to Groovy.
- 8.2 - February 2022: Runs on Java 17, but now needs Java 11 or above to run. Runs HSQLDB, H2 and Derby in server mode to allow for
external connections. Upgrade to Groovy 4.

See the [change log](changelog.md) for a detailed build history.

## Install and use

Download the [Nodus installer](https://github.com/jourquin/Nodus/releases).
As the software is written in the [Java](https://java.com/en/download/) programming language, the 
latest must be installed on your computer. Since version 8.2, Nodus needs Java 11 or later. Depending on your system, either (double) click
on "Nodus8-install.jar" or run "java -jar Nodus8-install.jar" from your shell console.

Once installed, Nodus can be launched using
- "nodus8.sh" on Linux
- "Nodus8.app" or "nodus8.sh" on macOS
- "Nodus 8" shortcut or "nodus8.bat" on Windows

The software has a modern and integrated user-friendly GUI. Complete reference and user guides
are not available, but the API is fully documented. 
A documented sample Nodus project can be found in the "[demo](https://github.com/jourquin/Nodus/blob/master/demo)" directory. 

> **Note on JDK**: Nodus is very demanding in terms of computing resources, especially when it comes to assignment. Experience
shows that the choice of the JDK used can have a significant impact on calculation times. On average, 
[GraalVM](https://www.graalvm.org) performs an assignment 25% faster than a “classic” OpenJDK virtual machine.  

> **Note for macOS users**: recent releases of macOS (Catalina and later) introduce more security controls via Gatekeeper. MacOS may complain 
> about the fact that the "Nodus8-installer.jar" is not developed by
> a recognized developer. A simple workaround is to run the installer from a terminal 
> (``java -jar Nodus8-Installer.jar``). Moreover, if you want to use
> Nodus projects that are stored in "special" folders, such as the Desktop for instance, 
> **full disk access must be granted to the /bin/sh shell** at the OS level 
> (add entry in Preferences > Security & Privacy > Privacy > Full Disk Access).

## Memory allocation

Nodus is written in Java. Therefore, it uses the memory allocation system provided by the Java Virtual Machine (JVM). 
In particular, the maximum memory allocated to the software must be defined by the user if the default
values are not appropriate. This can be set using the -Xms and -Xmx command line parameters 
passed to the JVM. Please refer to the JVM documentation for a detailed information on these switches. 

By default, Nodus uses the following strategy:
- If the physical memory of the computer it runs on is at most 4Go large, no -Xms (minimum heap) value is set. 
Otherwise it is set to 2Go.
- The maximum heap that can be claimed for (-Xmx) is set to 50% of the physical memory, with a maximum 
of 6Go.
- If Nodus runs on a 32bits JVM (not recommended), -Xmx is limited to 1.4Gb and -Xms is not set.

These values are stored in "jvmargs.sh" or "jvmargs.bat", a file created in the installation directory by
Nodus at launch time if it doesn't exist yet. This file can be edited if other values (or even other JVM parameters)
are desired.
   
## License

You can redistribute it and/or modify Nodus 8.x under the terms of the GNU General Public License 
as published by the Free Software Foundation, either [version 3](https://www.gnu.org/licenses/gpl-3.0.html)
of the License, or (at your option) any later version. 

Note that the NODUS name and logo are trademarks of UCLouvain and are **not** covered by the GPL license. 
Use of the trademark is governed by this [Trademark Policy](https://github.com/jourquin/Nodus/blob/master/Trademark%20Policy.md).

## Build from sources

The Nodus distribution can be built from the sources. Therefore, you need a Java Development Kit 
([JDK version 11](http://www.oracle.com/technetwork/java/javase/downloads/index.html)) or above. 
You also need Apache [Ant](http://ant.apache.org/). Be sure JAVA_HOME points to your JDK and 
that ant is in your OS path.

Once the GitHib Nodus sources fetched, open a terminal and go to the root of the project. Type:

```
ant Installer
```
This will compile the project, generate the JavaDoc for the API and bundle all what is needed into 
the installer jar file.

It is also possible to only compile the main nodus8.jar using:
 
```
ant Jar
```
This can be useful for instance when some bugs have been fixed in the source tree or some new
enhancements have been introduced without any change in the used external libraries.

You can also import Nodus as an [Eclipse](http://www.eclipse.org/) project.

## Code robustness

The [Checkstyle](https://checkstyle.org) and [SpotBugs](https://spotbugs.github.io) plugins are used in Eclipse 
in order to write code that adheres to the Google Java coding standard and to look for bugs in Java code.
   
## Uninstall

The software doesn't modify the "registry" of any supported OS (Mac OS, Linux or Windows). Just
delete the installation directory to remove the software from your system.  

You can also delete the small ".nodus8.properties" file that is located at the root of your "home" dir.   

## How to cite?

Jourquin, Bart. (2022) Nodus, the Transportation Network Modeling Software Designed for Multimodal and Intermodal 
Freight Transport. http://nodus.uclouvain.be. [DOI 10.5281/zenodo.3634540](https://doi.org/10.5281/zenodo.3634540).



