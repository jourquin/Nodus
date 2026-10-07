# Licenses of Nodus and bundled libraries

Nodus source code is distributed under the GNU General Public License, version 3 or
later; see the root [LICENSE](../LICENSE). Third-party components retain their own
licenses, including OpenMap code and other adapted sources with their original headers.

This inventory covers the JAR families currently stored in `lib/`, `lib/groovy/`,
`jdbcDrivers/` and `devtools/`, including optional Groovy components and build/test tools.
Versions describe the checked-in files, not the latest upstream releases. License names
were checked against bundled license texts, manifests, Maven metadata and upstream sources.
The tables summarize the main component licenses; embedded third-party notices still apply.

Common license texts in this directory include [Apache-2.0](ApacheV2.license),
[LGPL-2.1](LGPL_v2_1.license), [LGPL-3.0](LGPL_v3.license), [H2](H2.license),
[HSQLDB](HSQLDB-BSD.license), [OpenMap](OpenMap.license), [JUNG](BSD-jung.license),
[Py4J](BSD-Py4J.license), [parsii](MIT-Parsii.license) and [SLF4J](MIT-slf4j.license).
Other license and attribution texts are provided inside individual JARs, commonly under
`META-INF/LICENSE*`, `META-INF/NOTICE*` or `META-INF/licenses/`, or by the linked upstream
projects. This index does not replace those texts.

## Application libraries in `lib/`

| Component / JAR family | Bundled version | License |
| --- | --- | --- |
| [Apache Commons][commons] `commons-codec-*` | 1.18.0 | Apache-2.0 |
| Apache Commons `commons-collections4-*` | 4.5.0-M2 | Apache-2.0 |
| Apache Commons `commons-compress-*` | 1.26.2 | Apache-2.0 |
| Apache Commons `commons-csv-*` | 1.14.0 | Apache-2.0 |
| Apache Commons `commons-io-*` | 2.19.0 | Apache-2.0 |
| [Apache HttpComponents][http] `httpclient5-*` | 5.4.1 | Apache-2.0 |
| Apache HttpComponents `httpcore5-*`, `httpcore5-h2-*` | 5.3.1 | Apache-2.0 |
| [Guava][guava] `guava-*` | 19.0 | Apache-2.0 |
| [J4R][j4r] `j4r-*` | 1.1.1 | LGPL-3.0 |
| [JavaDBF4Nodus][javadbf] `javadbf4nodus-*` | 1.12.3 | LGPL-3.0 |
| [JCheckList][jchecklist] `JCheckList.jar` | Unversioned filename | Apache-2.0 |
| [JFreeChart][jfreechart] `jfreechart-*` | 1.5.3 | LGPL-2.1-or-later |
| [json-simple][json-simple] `json-simple-*` | 1.1.1 | Apache-2.0 |
| [JResourcesMonitor][monitor] `JResourcesMonitor.jar` | Unversioned filename | Apache-2.0 |
| [JUNG][jung] `jung-*` | 2.1.1 | BSD-3-Clause |
| [Apache Log4j][log4j] `log4j-api-*` | 2.17.2 | Apache-2.0 |
| Apache Log4j `log4j-to-slf4j-*` | 2.18.0 | Apache-2.0 |
| [OpenMap][openmap] `openmap-*` | 6.0 | OpenMap Software License Agreement |
| [parsii][parsii] `parsii-*` | 5.0.1 | MIT |
| [Apache POI][poi] `poi-*`, `poi-ooxml-*`, `poi-ooxml-full-*` | 5.5.1 | Apache-2.0 |
| [Py4J][py4j] `py4j*.jar` | 0.10.9.2 | BSD-3-Clause |
| [RSyntaxTextArea][rsyntax] `rsyntaxtextarea-*` | 3.4.0 | BSD-3-Clause |
| [SLF4J][slf4j] `slf4j-api-*`, `slf4j-nop-*` | 1.7.35 | MIT |
| [XChart][xchart] `xchart-*` | 3.8.8 | Apache-2.0 |
| [Apache XMLBeans][xmlbeans] `xmlbeans-*` | 5.1.1 | Apache-2.0 |

## Database engines and JDBC drivers

The built-in engines are in `lib/`; the optional MariaDB and PostgreSQL drivers are
in `jdbcDrivers/`.

| Component / JAR family | Bundled version | License |
| --- | --- | --- |
| [Apache Derby][derby] `derby*.jar` | 10.15.2.0 | Apache-2.0 |
| [H2][h2] `h2-*` | 2.4.240 | MPL-2.0 OR EPL-1.0 |
| [HSQLDB][hsqldb] `hsqldb-*` | 2.7.4 | HSQLDB BSD-style license |
| [MariaDB Connector/J][mariadb] `mariadb-java-client-*` | 3.5.3 | LGPL-2.1-or-later |
| [PostgreSQL JDBC][postgresql] `postgresql-*` | 42.7.3 | BSD-2-Clause |

Derby's version is recorded in its bundle metadata despite its unversioned filenames.
The PostgreSQL JAR also includes BSD-2-Clause notices for its embedded SCRAM and
string-preparation dependencies.

## Groovy distribution and companion libraries in `lib/groovy/`

The Apache license of Groovy does not cover every separately bundled dependency.
JUnit 4 and JUnit 5, in particular, use different Eclipse Public License versions.
Some of these libraries serve optional Groovy modules rather than Nodus's main code.

| Component / JAR family | Bundled version | License |
| --- | --- | --- |
| [Apache Groovy][groovy] `groovy-*` | 5.0.6 | Apache-2.0 |
| [Apache Ant][ant] `ant-*` | 1.10.17 | Apache-2.0 |
| [Apache Commons CLI][commons] `commons-cli-*` | 1.11.0 | Apache-2.0 |
| [GPars][gpars] `gpars-*` | 1.2.1 | Apache-2.0 |
| [Hamcrest][hamcrest] `hamcrest-core-*` | 1.3 | BSD-3-Clause |
| [Apache Ivy][ivy] `ivy-*` | 2.5.3 | Apache-2.0 |
| [Jackson][jackson] `jackson-annotations-*` | 2.21 | Apache-2.0 |
| Jackson `jackson-core-*`, `jackson-databind-*`, `jackson-dataformat-*` | 2.21.2 | Apache-2.0 |
| [JLine][jline] `jline-*`, `jansi-*` | 3.30.9 | BSD-3-Clause |
| [JavaParser][javaparser] `javaparser-core-*` | 3.28.0 | Apache-2.0 OR LGPL-3.0 |
| [JCommander][jcommander] `jcommander-*` | 1.83 | Apache-2.0 |
| [JNA][jna] `jna-*` | 5.18.1 | Apache-2.0 OR LGPL-2.1-or-later |
| [jQuery][jquery] `jquery-*` | 3.7.1 | MIT |
| [JSR166y][jsr166] `jsr166y-*` | 1.7.0 | Public domain |
| [JUnit 4][junit4] `junit-4*` | 4.13.2 | EPL-1.0 |
| [JUnit 5][junit5] `junit-jupiter-*` | 5.14.3 | EPL-2.0 |
| JUnit Platform `junit-platform-*` | 1.14.3 | EPL-2.0 |
| [Multiverse][multiverse] `multiverse-core-*` | 0.7.0 | Apache-2.0 |
| [MXParser][mxparser] `mxparser-*` | 1.2.2 | Indiana University Extreme! Lab license 1.2 |
| [OpenTest4J][opentest4j] `opentest4j-*` | 1.3.0 | Apache-2.0 |
| [abego TreeLayout][treelayout] `org.abego.treelayout.core-*` | 1.0.3 | BSD-3-Clause |
| [QDox][qdox] `qdox-*` | 2.2.0 | Apache-2.0 |
| [SLF4J][slf4j] `slf4j-api-*` | 2.0.17 | MIT |
| [SnakeYAML][snakeyaml] `snakeyaml-*` | 2.5 | Apache-2.0 |
| [TestNG][testng] `testng-*` | 7.12.0 | Apache-2.0 |
| [XStream][xstream] `xstream-*` | 1.4.21 | BSD-3-Clause |

Groovy's embedded `META-INF/LICENSE` and `META-INF/NOTICE` also describe incorporated
code and documentation assets under additional licenses. The JLine distribution's
`jansi-3.30.9.jar` declares BSD-3-Clause in its manifest.

MXParser's bundled license requires this acknowledgement:

> This product includes software developed by the Indiana University Extreme! Lab.
> For further information please visit http://www.extreme.indiana.edu/

## Development, installer and test tools in `devtools/`

| Component / JAR family | Bundled version | License |
| --- | --- | --- |
| [google-java-format][formatter] Eclipse plugin | 1.6.0 | Apache-2.0; see embedded tools below |
| [JFlex][jflex] `JFlex.jar` | 1.4.1 | GPL; predates the BSD-licensed releases |
| [IzPack][izpack] `IzPack/izpack-*` | 5.2.4 | Apache-2.0 |
| Apache Commons `IzPack/commons-compress-*` | 1.27.1 | Apache-2.0 |
| Apache Commons `IzPack/commons-io-*` | 2.18.0 | Apache-2.0 |
| Apache Commons `IzPack/commons-lang3-*` | 3.17.0 | Apache-2.0 |
| [JLine][jline] `IzPack/jline-*` | 2.14.6 | BSD-3-Clause |
| [PicoContainer][pico] `IzPack/picocontainer-*` | 2.15.2 | BSD-style license |
| [XZ for Java][xz] `IzPack/xz-*` | 1.10 | 0BSD |
| [API Guardian][apiguardian] `junit/apiguardian-api-*` | 1.1.2 | Apache-2.0 |

The formatter file is `google-java-format-eclipse-plugin_1.6.0.jar`. It embeds
`google-java-format-1.6.jar` and `guava-22.0.jar` (Apache-2.0), plus
`javac-shaded-9+181-r4173-1.jar` ([OpenJDK GPL-2.0 with the Classpath exception][javac]).
The bundled JFlex reports version 1.4.1; its license must not be confused with the
[BSD license introduced in JFlex 1.5.0][jflex-history].
IzPack's license is also supplied as [IzPack-Licence.txt](../devtools/IzPack/IzPack-Licence.txt),
and API Guardian's as [devtools/junit/LICENSE](../devtools/junit/LICENSE).

Pandoc, Eclipse, Checkstyle and the JDK used for development are installed separately.
They are not part of the JAR inventory above. The same applies to a system Ant
installation; the Ant companion JARs bundled with Groovy are listed separately.



[commons]: https://commons.apache.org/
[http]: https://hc.apache.org/
[guava]: https://github.com/google/guava
[j4r]: https://sourceforge.net/p/repiceasource/wiki/J4R/
[javadbf]: https://github.com/jourquin/javadbf
[jchecklist]: https://github.com/jourquin/JCheckList
[jfreechart]: https://github.com/jfree/jfreechart
[json-simple]: https://github.com/fangyidong/json-simple
[monitor]: https://github.com/jourquin/JResourcesMonitor
[jung]: https://github.com/jrtom/jung
[log4j]: https://logging.apache.org/log4j/2.x/
[openmap]: http://openmap-java.org/
[parsii]: https://github.com/scireum/parsii
[poi]: https://poi.apache.org/
[py4j]: https://www.py4j.org/
[rsyntax]: https://github.com/bobbylight/RSyntaxTextArea
[slf4j]: https://www.slf4j.org/license.html
[xchart]: https://github.com/knowm/XChart
[xmlbeans]: https://xmlbeans.apache.org/
[derby]: https://db.apache.org/derby/
[h2]: https://github.com/h2database/h2database/blob/version-2.4.240/LICENSE.txt
[hsqldb]: https://hsqldb.org/web/hsqlLicense.html
[mariadb]: https://github.com/mariadb-corporation/mariadb-connector-j
[postgresql]: https://jdbc.postgresql.org/about/license/
[groovy]: https://groovy-lang.org/license.html
[ant]: https://ant.apache.org/license.html
[gpars]: https://www.gpars.org/
[hamcrest]: https://github.com/hamcrest/JavaHamcrest
[ivy]: https://ant.apache.org/ivy/
[jackson]: https://github.com/FasterXML/jackson
[jline]: https://github.com/jline/jline3
[javaparser]: https://github.com/javaparser/javaparser
[jcommander]: https://github.com/cbeust/jcommander
[jna]: https://github.com/java-native-access/jna/blob/master/LICENSE
[jquery]: https://jquery.org/license/
[jsr166]: https://gee.cs.oswego.edu/dl/concurrency-interest/
[junit4]: https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt
[junit5]: https://github.com/junit-team/junit5/blob/r5.14.3/LICENSE.md
[multiverse]: https://github.com/pveentjer/Multiverse/blob/master/LICENSE
[mxparser]: https://github.com/x-stream/mxparser/blob/master/LICENSE.txt
[opentest4j]: https://github.com/ota4j-team/opentest4j
[treelayout]: https://github.com/abego/treelayout
[qdox]: https://github.com/paul-hammant/qdox
[snakeyaml]: https://bitbucket.org/snakeyaml/snakeyaml
[testng]: https://github.com/testng-team/testng
[xstream]: https://x-stream.github.io/license.html
[formatter]: https://github.com/google/google-java-format
[javac]: https://github.com/google/error-prone-javac/blob/master/LICENSE
[jflex]: https://jflex.de/
[jflex-history]: https://jflex.de/history.html
[izpack]: https://izpack.org/
[pico]: https://picocontainer.com/project.html
[xz]: https://tukaani.org/xz/java.html
[apiguardian]: https://github.com/apiguardian-team/apiguardian
