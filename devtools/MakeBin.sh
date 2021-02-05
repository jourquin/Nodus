# This is tentative to compile Nodus binaries using GraalVM's native-image tool
# Does'nt work for now :
# - the H2 jar cannot be included due to some, variable declarations issues
# - AWT is not included in the binary (known issue by the GraalVM community)
# - Initialization problem with the DBF lib. Solved using the --initialize-at-build-time option ?
# 

#--allow-incomplete-classpath \


JARS=lib/openmap.jar
JARS=$JARS:lib/xchart-3.6.6.jar
JARS=$JARS:lib/rsyntaxtextarea-3.1.1.jar
JARS=$JARS:lib/JCheckList.jar
JARS=$JARS:lib/JResourcesMonitor.jar
JARS=$JARS:lib/commons-io-2.6.jar
JARS=$JARS:lib/guava-19.0.jar
JARS=$JARS:lib/jung-api-2.1.1.jar
JARS=$JARS:lib/jung-algorithms-2.1.1.jar
JARS=$JARS:lib/jung-graph-impl-2.1.1.jar
JARS=$JARS:lib/jung-visualization-2.1.1.jar
JARS=$JARS:lib/groovy/qdox-1.12.1.jar
JARS=$JARS:lib/parsii-4.0.jar
JARS=$JARS:lib/xmlbeans-3.1.0.jar
JARS=$JARS:lib/jtouchbar-1.0.0.jar
JARS=$JARS:lib/derbyLocale_fr.jar
JARS=$JARS:lib/derby.jar
JARS=$JARS:lib/hsqldb.jar
JARS=$JARS:lib/h2-1.4.200.jar
JARS=$JARS:lib/groovy/bsf-2.4.0.jar
JARS=$JARS:lib/groovy/commons-cli-1.4.jar
JARS=$JARS:lib/groovy/commons-logging-1.2.jar
JARS=$JARS:lib/groovy/gpars-1.2.1.jar
JARS=$JARS:lib/groovy/hamcrest-core-1.3.jar
JARS=$JARS:lib/groovy/ivy-2.5.0.jar
JARS=$JARS:lib/groovy/jansi-1.18.jar
JARS=$JARS:lib/groovy/javax.servlet-api-3.0.1.jar
JARS=$JARS:lib/groovy/jline-2.14.6.jar
JARS=$JARS:lib/groovy/jsp-api-2.0.jar
JARS=$JARS:lib/groovy/jsr166y-1.7.0.jar
JARS=$JARS:lib/groovy/junit-4.13.jar
JARS=$JARS:lib/groovy/multiverse-core-0.7.0.jar
JARS=$JARS:lib/groovy/opentest4j-1.2.0.jar
JARS=$JARS:lib/groovy/org.abego.treelayout.core-1.0.1.jar
JARS=$JARS:lib/groovy/ST4-4.1.jar
JARS=$JARS:lib/groovy/extras-jaxb/activation-1.1.1.jar
JARS=$JARS:lib/groovy/extras-jaxb/jaxb-api-2.3.0.jar
JARS=$JARS:lib/groovy/extras-jaxb/jaxb-core-2.3.0.1.jar
JARS=$JARS:lib/groovy/extras-jaxb/jaxb-impl-2.3.0.1.jar
JARS=$JARS:lib/commons-collections4-4.4.jar
JARS=$JARS:lib/commons-compress-1.20.jar
JARS=$JARS:lib/commons-csv-1.8.jar
JARS=$JARS:lib/poi-4.1.2.jar
JARS=$JARS:lib/poi-ooxml-4.1.2.jar
JARS=$JARS:lib/poi-ooxml-schemas-4.1.2.jar
JARS=$JARS:jdbcDrivers/postgresql-42.2.10.jar
JARS=$JARS:jdbcDrivers/sqlite-jdbc-3.30.1.jar
JARS=$JARS:lib/jna-5.6.0.jar
JARS=$JARS:lib/jna-platform-5.6.0.jar
JARS=$JARS:lib/slf4j-api-1.7.9.jar
JARS=$JARS:lib/slf4j-nop-1.7.9.jar
JARS=$JARS:lib/javadbf4nodus-1.12.2.jar
JARS=$JARS:jdbcDrivers/mariadb-java-client-2.7.0.jar
JARS=$JARS:lib/groovy/ant-1.10.8.jar
JARS=$JARS:lib/groovy/ant-antlr-1.10.8.jar
JARS=$JARS:lib/groovy/ant-junit-1.10.8.jar
JARS=$JARS:lib/groovy/ant-launcher-1.10.8.jar
JARS=$JARS:lib/groovy/groovy-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-ant-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-astbuilder-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-bsf-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-cli-commons-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-cli-picocli-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-console-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-datetime-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-dateutil-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-docgenerator-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-groovydoc-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-groovysh-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-jaxb-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-jmx-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-json-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-jsr223-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-macro-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-nio-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-servlet-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-sql-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-swing-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-templates-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-test-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-test-junit5-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-testng-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-xml-3.0.6.jar
JARS=$JARS:lib/groovy/groovy-yaml-3.0.6.jar
JARS=$JARS:lib/groovy/jackson-annotations-2.11.2.jar
JARS=$JARS:lib/groovy/jackson-core-2.11.2.jar
JARS=$JARS:lib/groovy/jackson-databind-2.11.2.jar
JARS=$JARS:lib/groovy/jackson-dataformat-yaml-2.11.2.jar
JARS=$JARS:lib/groovy/javaparser-core-3.16.1.jar
JARS=$JARS:lib/groovy/jcommander-1.78.jar
JARS=$JARS:lib/groovy/junit-jupiter-api-5.7.0.jar
JARS=$JARS:lib/groovy/junit-jupiter-engine-5.7.0.jar
JARS=$JARS:lib/groovy/junit-platform-commons-1.7.0.jar
JARS=$JARS:lib/groovy/junit-platform-engine-1.7.0.jar
JARS=$JARS:lib/groovy/junit-platform-launcher-1.7.0.jar
JARS=$JARS:lib/groovy/snakeyaml-1.26.jar
JARS=$JARS:lib/groovy/testng-7.3.0.jar
JARS=$JARS:lib/groovy/xstream-1.4.13.jar
JARS=$JARS:lib/oshi-core-5.3.6.jar



native-image \
--initialize-at-build-time=edu.uclouvain.core.nodus.database.dbf.DBFWriter,edu.uclouvain.core.nodus.database.dbf.DBFReader,edu.uclouvain.core.nodus.database.dbf.DBFBase,edu.uclouvain.core.nodus.database.dbf.DBFUtils \
--report-unsupported-elements-at-runtime \
--allow-incomplete-classpath \
--no-fallback  \
--install-exit-handlers \
-cp classes:$JARS \
edu.uclouvain.core.nodus.Nodus nodus8bin
