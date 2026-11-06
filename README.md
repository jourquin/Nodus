# Nodus 9.1 <a href="https://zenodo.org/badge/latestdoi/111554354"><img src="https://zenodo.org/badge/111554354.svg" alt="DOI"></a>

**Nodus 9.1 is a work in progress: it is already usable but has not yet been released.**

Nodus is transportation network modeling software designed specifically for multimodal and
intermodal freight transport. It is developed at the Center for Operations Research and
Econometrics ([CORE](https://www.uclouvain.be/en/research-institutes/lidam/core)) of the Université catholique de Louvain
([UCLouvain](https://uclouvain.be/en/index.html), Belgium). The software is developed and maintained primarily by
[Prof. Bart Jourquin](https://www.uclouvain.be/fr/people/bart.jourquin).

Visit the [GitHub Pages website](http://nodus.uclouvain.be) for documentation, and **download the Nodus installer and source code from**
[GitHub](https://github.com/jourquin/Nodus/releases).

## Introduction

Nodus ([screenshots](https://nodus.uclouvain.be/doc/images/screenshots.html))
implements the "Virtual Networks" methodology developed at UCLouvain. This approach is an
alternative to the traditional four-step model for multimodal and intermodal transport flows
over networks: it combines the modal-choice and assignment phases into a single step.

This methodology has been used in numerous policy-oriented studies of large-scale multimodal
freight transport networks, such as:

- Regional freight transport planning
- Cost-benefit analysis of transport infrastructure
- Optimal locations for intermodal terminals
- Impact of climate change on inland waterway transport
- Internalization of external costs and its potential impact on modal choice
- Estimation of market areas for container hubs

Numerous scientific articles describe research conducted using Nodus. Most of these papers,
along with contributions to conferences and seminars, can be found on
[ResearchGate](https://www.researchgate.net/profile/B_Jourquin).

## Key features

- Compatible with GIS standards: shapefiles and web mapping, using [OpenMap](http://openmap-java.org/).
- Parallel algorithms: capable of handling very large networks.
- Cross-platform: Linux, macOS and Windows.
- Open API: available through scripting (using the [Groovy](http://groovy-lang.org/) language,
  [Python](https://www.python.org) through a [Py4J](https://www.py4j.org/index.html) bridge, or
  [R](https://www.r-project.org) through a [J4R](https://sourceforge.net/p/repiceasource/wiki/J4R/) bridge)
  or plugins packaged as Java JAR files.
- Table exchange: CSV, DBF, XLS/XLSX, ODS, and Parquet.
- JDBC: compatible with most database management systems. Bundled with [HSQLDB](http://hsqldb.org/) and
  [H2](http://h2database.com/).
- Flexible: user-defined database fields, variables, cost functions, modal-choice models, and more.

See also the [documentation](https://nodus.uclouvain.be/doc/help.html) and
the [demo project](https://github.com/jourquin/Nodus/blob/master/demo).

## Installation and use

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

The software provides an integrated graphical user interface. Complete reference and user guides
are not available, but the API is fully documented with Javadoc.
A basic user guide and a documented sample Nodus project are available in the
[demo](https://github.com/jourquin/Nodus/blob/master/demo) directory.

> **JDK performance**: Nodus can require substantial computing resources, especially for assignments.
> Experience shows that the choice of JDK can have a significant impact on computation times. On average,
> [GraalVM](https://www.graalvm.org) completes assignments 25% faster than a standard OpenJDK virtual machine.

> **Note for macOS users**: macOS Catalina and later enforce additional security controls through Gatekeeper.
> macOS may warn that `Nodus9-install.jar` is from an unidentified developer. A simple workaround is to
> run the installer from a terminal (`java -jar Nodus9-install.jar`). To use Nodus projects stored in
> protected folders, such as the Desktop, **grant Full Disk Access to the `/bin/sh` shell** in macOS
> (add an entry under Preferences > Security & Privacy > Privacy > Full Disk Access).

Nodus selects default memory limits at launch. For larger projects or custom JVM settings,
see [memory allocation in the advanced guide](ADVANCED.md#memory-allocation).

## Building from source

You need a Java Development Kit (JDK) version 11 or later and a full
[Apache Ant](https://ant.apache.org/) 1.10.6+ installation, including
`ant-junitlauncher.jar` in Ant's `lib` directory. Set `JAVA_HOME` to your JDK
and make sure `ant` is on your system's `PATH`.

Download or clone the Nodus source code, open a terminal in the project root, and run:

```sh
ant Installer
```

This compiles the application and runs the full test suite first. A compilation error,
test failure or test execution error aborts the build. If the tests pass, Ant generates
the API Javadoc and packages the installer JAR file.

To compile and package only the main `nodus9.jar`, run:

```sh
ant Jar
```

This is useful for source changes that do not affect the external libraries.
To run the tests separately, use `ant Test`.

You can also import Nodus as an [Eclipse](https://www.eclipse.org/) project.

## Advanced use and development

The [advanced guide](ADVANCED.md) covers memory and JVM options, public `NodusC.java`
settings for Groovy scripts, assignment timing diagnostics, testing and developer tools.

## Release history

- 7.0 - November 2017: First open-source version of Nodus.
- 7.1 - November 2018: Upgrades to Groovy 2.5.x.
- 7.2 - February 2020: Upgrades to Groovy 3.x.
- 8.0 - February 2021: Introduces time functions alongside cost functions, simplifies the API
  for modal-choice plugins, and includes many internal improvements.
- 8.1 - April 2021: Runs on Java 16 and adds Python scripting through a Py4J bridge and R scripting
  through a J4R bridge, alongside Groovy.
- 8.2 - February 2022: Tested on Java 17; requires Java 11 or later. Runs HSQLDB, H2 and Derby
  in server mode to allow external connections. Upgrades to Groovy 4.
- 8.3 - November 2025: Tested with Java 25 while retaining Java 11 compatibility. Upgrades to Groovy 5.
- 8.4 - June 2026: Reintroduces the Frank-Wolfe-based algorithms and includes major code refactoring
  to improve robustness.
- 8.5 - September 2026: Reintroduces lines and services with a completely redesigned workflow
  based on Virtual Network Version 4.
- 9.0 - October 2026: Adds embedded modal-choice parameter estimation for univariate logit, probit
  and proportional methods, along with modal-choice performance indicators. Improves assignment
  speed (at least four times faster on a set of selected projects), map responsiveness and database
  performance. Adds unit tests and an automated continuous integration workflow. Moves the
  modal-choice API into its own package, so custom modal-choice plugin source code requires
  updated imports. Nodus can upgrade eligible existing plugin JARs without recompilation, keeping
  a `.jar.nodus8` backup; see the
  [Nodus 9.0 migration guide](https://nodus.uclouvain.be/doc/modal-choice-migration.html).
- 9.1 - November 2026: Improves performance and reliability, including an improved A* shortest-path
  algorithm.

See the [changelog](changelog.md) for a detailed build history.

## Uninstalling

The software does not modify the system registry on any supported operating system
(macOS, Linux or Windows). To uninstall it, delete the installation directory.

You can also delete the small `.nodus9.properties` file in your home directory.
The empty `.nodus9.properties.lock` file used to coordinate simultaneous starts can also be deleted
after all Nodus instances have closed.
On first launch, Nodus 9 copies `.nodus8.properties` if the new file does not yet exist.
The old file is left unchanged; remove it too if you no longer need your Nodus 8 preferences.

## License

You can redistribute Nodus and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, either [version 3](https://www.gnu.org/licenses/gpl-3.0.html)
of the License, or (at your option) any later version.

The NODUS name and logo are trademarks of UCLouvain and are **not** covered by the GPL.
Their use is governed by the [Trademark Policy](https://github.com/jourquin/Nodus/blob/master/Trademark%20Policy.md).

## How to cite Nodus

Jourquin, Bart. (2026) Nodus, the Transportation Network Modeling Software Designed for Multimodal and Intermodal
Freight Transport. http://nodus.uclouvain.be. [DOI 10.5281/zenodo.21336779](https://doi.org/10.5281/zenodo.21336779).
