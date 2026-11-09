Nodus is open-source transportation network modeling software for multimodal and intermodal freight transport, developed at UCLouvain. Its Virtual Networks methodology combines modal choice and traffic assignment within a single modeling framework.

Since version 7.0 (first open-source release on GitHub, September 2018), Nodus has evolved in several key areas:

- **Transport modeling:** explicit transit-time functions alongside cost functions, more flexible rules for permitted operations at network nodes, restored Frank–Wolfe assignment algorithms, and a redesigned lines and services workflow based on Virtual Network Version 4.
- **Scripting and customization:** Python and R integration alongside Groovy, successive Groovy runtime upgrades, and a revised API for custom modal-choice methods.
- **Maps and data:** improved map rendering and navigation, broader support for GIS-generated DBF files, and local database servers that allow external tools to connect to project data.
- **Performance and reliability:** faster assignments and database operations, more responsive maps, and stronger handling of project files, imports, saves and resource cleanup.

# Nodus 9.0

First released on October 13, 2026, Nodus 9.0 adds embedded modal-choice parameter estimation for univariate logit, probit and proportional methods, together with indicators for assessing modal-choice performance.

It also delivers substantial performance improvements in assignments, map navigation and database operations, supported by automated tests and continuous integration.

# Nodus 9.1

First released on November 9, 2026, Nodus 9.1 further improves performance and reliability, including shortest-path computation, assignment cancellation, project lifecycle management and data saving.

# Compatibility and migration

Nodus 8.x preserves compatibility with projects developed for Nodus 7.x, but custom modal-choice plugins require adaptation to the revised API.

Nodus 9.x introduces a further package change for modal-choice plugins. Source code requires updated imports; eligible Nodus 8.x plugin JARs can be converted by Nodus without recompilation. See the [modal-choice migration guide](doc/modal-choice-migration.md).

Nodus requires Java 11 or later. Older projects using H2 version 1 require a database migration when moving to H2 version 2. Groovy scripts written for earlier runtimes may also require minor adjustments.

See the [changelog](changelog.md) for the detailed build history.
