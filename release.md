# Nodus 8.4

Nodus 8.0, which first build was released on March 4, 2021, introduced a series of new features, among which:

- The possibility of defining **transit time** functions in addition to cost functions;
- An improved management of **node rules** (operations that are permitted (or not) at each node of the network);
- An improved **graphics rendering** mode;
- An **increased compatibility** with non legacy .DBF files used by geographic information systems;
- …

Nodus 8.x versions:
- Nodus 8.1 (first build on April 26, 2021) introduced the possibility of developing Python and R scripts in addition to Groovy scripts.
- Nodus 8.2 (first build on February 18, 2022) requires Java 11 or above and embeds H2 version 2. Existing projects using the H2 database require a database migration: https://www.h2database.com/html/migration-to-v2.html. The embedded HSQLDB, H2 and Derby engines now run in server mode, allowing external connections from another JDBC client.
- Nodus 8.3 (first build on November 26, 2025) updates the embedded Groovy runtime to version 5.x. This version change is justified by the fact that, although unlikely, some scripts written in this language may need to be slightly adapted.
- Nodus 8.4 (first build on June 23, 2026) reintroduces the Frank-Wolfe based assignment algorithms, which was removed since 8.0 because they were buggy. It should primarily be seen as a stability and robustness update: several sensitive parts of the code were reviewed and strengthened, in particular the project open/close workflows, SQL console execution, time-dependent result display, and resource release. 

The CHANGELOG.MD file contains an exhaustive list of the improvements made to the software.

Nodus 8.x is **compatible** with projects developed for Nodus 7.x. However, specific modal choice plugins need to be adapted to the new API.
