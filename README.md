# Nodus 9.0 <a href="https://zenodo.org/badge/latestdoi/111554354"><img src="https://zenodo.org/badge/111554354.svg" alt="DOI"></a>

**(Work in progress. Latest release is Nodus 8.6)**

Nodus is a transportation network modeling software especially designed for multimodal and
intermodal freight transport. It is developed at the Center for Operations Research and
Econometrics ([CORE](https://www.uclouvain.be/en/research-institutes/lidam/core)) of the Université catholique de Louvain
([UCLouvain](https://uclouvain.be/en/index.html), Belgium). The software is developed  and maintained mainly by
[Pr Bart Jourquin](https://www.uclouvain.be/fr/people/bart.jourquin).

Beside this [GitHub Pages website](http://nodus.uclouvain.be), the **Nodus installer and sources can be downloaded from**
[GitHub](https://github.com/jourquin/Nodus/releases).

## Introduction

Nodus ([Screenshots](https://nodus.uclouvain.be/doc/images/screenshots.html))
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
- Multiplatform: Linux, macOS and Windows.
- Open API: available through scripting (using the [Groovy](http://groovy-lang.org/) language,
[Python](https://www.python.org) through a [Py4J](https://www.py4j.org/index.html) bridge or
[R](https://www.r-project.org) through a [J4R](https://sourceforge.net/p/repiceasource/wiki/J4R/) bridge)
or plugins (in Java jar files).
- JDBC: compatible with most DBMS’s. Shipped with [HSQLDB](http://hsqldb.org/) and
[H2](http://h2database.com/).
- Flexible: user defined database fields, variables, cost functions, mode choice models…

See also the [documentation](https://nodus.uclouvain.be/doc/help.html) and
the [Demo project](https://github.com/jourquin/Nodus/blob/master/demo).

## Install and use

Download the [Nodus installer](https://github.com/jourquin/Nodus/releases).
Nodus requires Java 11 or later (a full JRE or JDK, not a headless installation).
Depending on your system, double-click `Nodus9-install.jar` or run it from a terminal:

```sh
java -jar Nodus9-install.jar
```

Once installed, launch Nodus using:

- `nodus9.sh` on Linux
- `Nodus9.app` or `nodus9.sh` on macOS
- The `Nodus 9` shortcut or `nodus9.bat` on Windows

The software has a modern and integrated user-friendly GUI. Complete reference and user guides
are not available, but the API is fully documented through Javadoc.
A documented sample Nodus project can be found in the "[demo](https://github.com/jourquin/Nodus/blob/master/demo)" directory.

> **Note on JDK**: Nodus is very demanding in terms of computing resources, especially when it comes to assignment. Experience
shows that the choice of the JDK used can have a significant impact on calculation times. On average,
[GraalVM](https://www.graalvm.org) performs an assignment 25% faster than a “classic” OpenJDK virtual machine.

> **Note for macOS users**: recent releases of macOS (Catalina and later) introduce more security controls via Gatekeeper. MacOS may complain
> about the fact that the "Nodus9-install.jar" is not developed by
> a recognized developer. A simple workaround is to run the installer from a terminal
> (``java -jar Nodus9-install.jar``). Moreover, if you want to use
> Nodus projects that are stored in "special" folders, such as the Desktop for instance,
> **full disk access must be granted to the /bin/sh shell** at the OS level
> (add entry in Preferences > Security & Privacy > Privacy > Full Disk Access).

Nodus selects default memory limits at launch. For larger projects or custom JVM settings,
see [memory allocation in the advanced guide](ADVANCED.md#memory-allocation).

## Build from sources

You need a Java Development Kit (JDK) version 11 or later and a full
[Apache Ant](https://ant.apache.org/) 1.10.6+ installation, including
`ant-junitlauncher.jar` in Ant's `lib` directory. Set `JAVA_HOME` to your JDK
and make sure `ant` is on your system path.

Fetch the Nodus sources, open a terminal in the project root, and run:

```sh
ant Installer
```

This compiles the application and runs the full test suite first. A compilation error,
test failure or test execution error aborts the build. If the tests pass, Ant generates
the API Javadoc and packages the installer jar file.

To compile and package only the main `nodus9.jar`, run:

```sh
ant Jar
```

This is useful for source changes that do not change the external libraries.
To run the tests separately, use `ant Test`.

You can also import Nodus as an [Eclipse](https://www.eclipse.org/) project.

## Advanced use and development

The [advanced guide](ADVANCED.md) covers memory and JVM options, public `NodusC.java`
settings for Groovy scripts, assignment timing diagnostics, testing and developer tools.

## History of the releases

- 7.0 - November 2017: First open source version of Nodus.
- 7.1 - November 2018: Upgrade to Groovy 2.5.x.
- 7.2 - February 2020: Upgrade to Groovy 3.x.
- 8.0 - February 2021: Introduce time functions (in addition to cost functions). Simplified API for modal-choice plugins.
Many under the hood improvements.
- 8.1 - April 2021: Runs on Java 16 and allows Python scripting through a Py4J bridge and R scripting through a J4R bridge in addition to Groovy.
- 8.2 - February 2022: Tested on Java 17, but now needs Java 11 or above to run. Runs HSQLDB, H2 and Derby in server mode to allow for
external connections. Upgrade to Groovy 4.
- 8.3 - November 2025: Tested with Java 25, but still runs on Java 11 and Groovy is upgraded to version 5.
- 8.4 - June 2026: The Frank-Wolfe based algorithms are reintroduced. Major code refactoring with a focus on code robustness.
- 8.5 - September 2026: Lines & services are reintroduced with a completely redesigned workflow based on Virtual Network Version 4.
- 9.0 - October 2026: Adds embedded modal-choice parameter estimation for univariate logit, probit and proportional methods and modal choice 
performance measurement indicators. It is also a performance-focused release, highlighting faster assignments (at least four times faster on a set of selected projects), more responsive map navigation and faster database operations. From the developers' perspective, a series of unit tests have been added with an automatic continuous integration workflow. The modal-choice API is moved into its own package. Therefore, custom modal-choice
plugin sources need updated imports. Nodus can upgrade eligible existing plugin JARs without recompilation, keeping a `.jar.nodus8` backup; see the
[Nodus 9.0 migration guide](https://github.com/jourquin/Nodus/blob/master/doc/modal-choice-migration.md).


See the [change log](changelog.md) for a detailed build history.

## Uninstall

The software doesn't modify the "registry" of any supported OS (Mac OS, Linux or Windows). Just
delete the installation directory to remove the software from your system.

You can also delete the small ".nodus9.properties" file that is located at the root of your "home" dir.
The empty ".nodus9.properties.lock" file used to coordinate simultaneous starts can also be deleted
after all Nodus instances have closed.
On first launch, Nodus 9 copies ".nodus8.properties" if the new file does not yet exist.
The old file is left unchanged; remove it too if you no longer need your Nodus 8 preferences.

## License

You can redistribute it and/or modify Nodus under the terms of the GNU General Public License
as published by the Free Software Foundation, either [version 3](https://www.gnu.org/licenses/gpl-3.0.html)
of the License, or (at your option) any later version.

Note that the NODUS name and logo are trademarks of UCLouvain and are **not** covered by the GPL license.
Use of the trademark is governed by this [Trademark Policy](https://github.com/jourquin/Nodus/blob/master/Trademark%20Policy.md).

## How to cite?

Jourquin, Bart. (2026) Nodus, the Transportation Network Modeling Software Designed for Multimodal and Intermodal
Freight Transport. http://nodus.uclouvain.be. [DOI 10.5281/zenodo.21336779](https://doi.org/10.5281/zenodo.21336779).
