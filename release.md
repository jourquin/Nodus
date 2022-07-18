# Nodus 8.2

Nodus 8.0, which first build was released on March 4, 2021, introduced a series of new features, among which:

- The possibility to define **transit time** functions in addition to cost functions;
- An improved management of **node rules** (operations that are permitted or not at each node of the network);
- An improved **graphics rendering** mode;
- An **increased compatibility** with non legacy .DBF files used by geographic information systems;
- …

Nodus 8.1 introduced the possibility to develop Python and R scripts in addition to Groovy scripts. It also runs on Java 16.

Nodus 8.2 runs also on Java 17, but needs Java 11 or above to run. This version also embeds H2 version 2. Existing projects using the H2 database
will need a migration of the database : https://www.h2database.com/html/migration-to-v2.html. The embedded HSQLDB, H2 and Derby
engines now run in server mode, allowing for external connections from another JDBC client. 

The CHANGELOG.MD file contains an exhaustive list of the improvements made to the software.

This version is **compatible with projects developed with Nodus 7.x**. However, specific modal choice plugins need to be adapted to the new API.
