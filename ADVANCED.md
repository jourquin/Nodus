# Nodus: advanced use and development

This guide is for advanced users and developers who configure Nodus or work on its code.
For an overview of the software, installation and build instructions, and platform-specific tips,
see the [main README](README.md).

- [Memory allocation and JVM options](#memory-allocation)
- [Application and project settings in NodusC.java](#application-and-project-settings)
- [Local database servers and external JDBC connections](#local-database-servers-and-external-jdbc-connections)
- [Assignment information dialogs](#assignment-information-dialogs)
- [Assignment runtime audit](#assignment-runtime-audit)
- [Tests and continuous integration](#tests-and-continuous-integration)
- [Code quality tools](#code-quality-tools)

## Memory allocation and JVM arguments

Nodus is written in Java and uses the Java Virtual Machine (JVM) to manage memory.
If the default memory limits do not suit your projects, adjust the initial and maximum heap
sizes using the JVM's `-Xms` and `-Xmx` command-line options. See your JVM's documentation
for details on these options.

By default, Nodus uses the following strategy:

- On computers with at most 4 GB of physical memory, `-Xms` (the initial heap size) is not set.
  Otherwise, it is set to 2 GB.
- The maximum heap size (`-Xmx`) is set to 50% of physical memory, up to a limit of 6 GB.
- On a 32-bit JVM (not recommended), `-Xmx` is limited to 1.4 GB and `-Xms` is not set.

These values are stored in `jvmargs.sh` or `jvmargs.bat` in the installation directory.
Nodus creates the file at launch if it does not already exist. Edit it to change the memory
limits or add other JVM options.

Since Nodus 8.4, the file also selects JVM options dynamically according to the Java version.
Since Nodus 9.0, these include `--illegal-access=deny` for Java 9–16 and
`--enable-native-access=ALL-UNNAMED` for Java 24 or later. The latter enables native access
for classpath libraries used by Nodus. On Java 24 or later, the `sun.misc.Unsafe` warning
policy is also set explicitly for compatibility with libraries that still use deprecated
memory-access methods.

Older releases could save `--illegal-access` permanently in a single-line `JVMARGS`
assignment. `SetJVMArgs` now upgrades these legacy files automatically, preserving custom
heap sizes and other arguments and saving the original as `jvmargs.sh.bak` or
`jvmargs.bat.bak`. The replacement selects its flags at each launch, so Java 17 and
later (including Java 27) receive no `--illegal-access` option. Custom multiline
scripts and single-line assignments containing shell commands or variable expansion are
left intact. In those files, manually remove any obsolete option that is applied unconditionally.

`SetJVMArgs` also adds a macOS-only
`--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED` option to generated
`jvmargs.sh` files to support full-screen mode in Groovy's native console. It upgrades the
previous generated shell format automatically, preserving custom JVM arguments
and the original backup. Custom multiline scripts remain unchanged; add the option
manually if needed.

## Application and project settings

Public settings in [NodusC.java](https://github.com/jourquin/Nodus/blob/master/src/edu/uclouvain/core/nodus/NodusC.java) can be configured
in the startup [nodus.groovy](nodus.groovy) script, a project's `[projectname].groovy` file,
or the Groovy console.

Settings are applied in the following order:

1. The values declared in `NodusC` provide the defaults.
2. Changes made by the startup `nodus.groovy` script become the application defaults for that session.
3. Each project starts with those application defaults; its own Groovy script can override them.

Before another project is loaded, all public mutable `NodusC` settings are restored to the application
defaults, even if the next project has no Groovy file. This includes timing reports, assignment
dialogs, estimation diagnostics, map options and the Exact MF algorithm selector. Changes made
in a project script or the Groovy console remain in effect until the next project is loaded.
A project's closing script still sees its current settings; canceling project closure leaves
those settings in place.

When you close a project, open another one, or quit, Nodus waits for any active assignment,
estimation or performance-indicator computation to finish before running the closing script
and releasing project resources. You can still cancel the computation through its normal
progress controls. This keeps the database connection and loaded layers available until
the worker has finished its cleanup.

Interrupted assignment and cost-parser coordinators also wait for every worker to terminate before
releasing the graph, output tables or project computation gate. A worker finishing a slow database
operation can therefore delay cancellation. Worker errors invalidate the computation; partial results
are not accepted merely because a worker thread has terminated.

Put a setting in the startup script to apply it to every project, or in a project's script
to apply it only to that project. The startup script runs once when the application launches.
It does not run again when you switch projects. Disabling reports leaves existing HTML reports intact.

### Assignment information dialogs

Assignment completion dialogs are enabled by default. To disable these informational dialogs for all
projects, add this to the startup `nodus.groovy` script:

```groovy
edu.uclouvain.core.nodus.NodusC.displayAssignmentInformationDialogs = false
```

Set it back to `true` to restore the dialogs. The setting is read when an assignment finishes;
it does not change calculations, saved results or completion metadata. Errors and warnings,
including warnings when the maximum iteration count is reached without convergence, remain visible.

### Assignment runtime audit

Set `NodusC.displayComputingTimes = true` to save a timing report as an HTML file in the project
directory for each assignment. The setting defaults to `false` and is read at the beginning of
`Assignment.run()`.
It can also be enabled from a Groovy script before running assignments:

```groovy
edu.uclouvain.core.nodus.NodusC.displayComputingTimes = true
```

Set `NodusC.displayComputingTimes = false` before starting an assignment to disable new reports.
Existing HTML files remain unchanged. `NodusC.displayMapComputingTimes` is independent: it controls
map preparation timings printed when panning or zooming and does not control assignment reports.

The report identifies the algorithm, scenario, configured thread count and completion status.
It reports the following durations in seconds:

- Total elapsed time for computation and saving, including final path batches, indexes and commits.
- Virtual-network initialization and generation.
- Cost and duration evaluation, including parser initialization, all iterations, OD classes, time slices and Frank–Wolfe line-search evaluations.
- Path and flow assignment: elapsed wall time across active workers, plus summed worker time excluding their database writer calls. This includes graph preparation, path reconstruction, modal split and flow updates.
- Database output: writer time for table creation, row preparation, path batches, equilibrium path updates, virtual-network results, indexes and commits.

Path writes overlap with the parallel assignment phase, so the reported rows must not be added together.
Summed worker time can exceed total elapsed time and includes waiting for the writer lock; it is
not CPU time. Total elapsed time also includes work not listed separately, such as loading demand and converting
volumes to vehicles. Completion dialogs and post-assignment scripts are excluded. Failed or
canceled runs save a partial audit, excluding subsequent failure cleanup.

## Local database servers and external JDBC connections

When no `jdbc.url` is specified in the `.nodus` file, Nodus manages the selected
built-in database. HSQLDB and H2 listen only on `127.0.0.1`, so R, Python,
and other JDBC clients on the same computer can connect while the project is open.
They are not accessible from another computer. The default ports are 9001 for
HSQLDB and 9092 for H2; override them with `hsqldbserverport` or
`h2serverport`. If a port is already in use, Nodus aborts project opening
rather than connecting to the database service using that port.

Existing database credentials are preserved. The demo's HSQLDB scripts still use
`SA` with an empty password and connect through `localhost`; `127.0.0.1` can also be used explicitly
to avoid differences in IPv6 hostname resolution. Loopback access does not isolate
the database from other users or processes on the same computer.

Nodus creates H2 project databases locally, then exposes only the project database
over TCP. TCP clients can create tables and modify data according to their database
privileges, but cannot create additional databases. H2's binding is configured at
application startup because H2 caches this setting.

An explicit `jdbc.url` selects an independently managed database. Nodus passes the
configured URL, driver, username, and password to JDBC without changing the hostname
or URL options. It neither starts nor stops that server and does not offer server
shutdown or compaction when the project closes. This applies to local and remote MariaDB,
MySQL, PostgreSQL, and other JDBC databases, including separately managed HSQLDB
and H2 instances. For a Nodus-managed built-in server, omit `jdbc.url` and
use the port properties above instead.

### MySQL/MariaDB storage engines

InnoDB is recommended for Nodus projects using MySQL or MariaDB. Nodus uses transactions and
savepoints to recover from failed edits and saves. MyISAM and other nontransactional engines
cannot provide that rollback protection. For a new database, use the bundled MariaDB driver:

```properties
jdbc.driver=org.mariadb.jdbc.Driver
jdbc.url=jdbc:mariadb://127.0.0.1:3306/DatabaseName
jdbc.user=your-username
jdbc.password=your-password
```

The database and account must already exist. The MariaDB driver can connect to MariaDB or MySQL
servers using this URL scheme. InnoDB is the standard server default, so no engine option is
normally needed. If the administrator selected another default, append
`?sessionVariables=default_storage_engine=InnoDB` to select InnoDB for this connection's new tables.
See MariaDB's [default-storage-engine setting](https://mariadb.com/docs/server/server-management/variables-and-modes/server-system-variables#default_storage_engine).
Explicit connection settings are honored, including a legacy MyISAM default. Changing
`default_storage_engine` affects newly created tables only; existing tables require a separate,
explicit conversion. Nodus never converts them automatically.

After imports and the project-opening script finish, Nodus checks the storage engines used by
base tables in the selected database, along with the session's default engine. It warns once
per project opening if any table or the session default uses a nontransactional engine. This
includes mixed InnoDB/MyISAM databases and empty databases with a nontransactional default.
Unknown engine capabilities or failed checks also produce a warning, but the project can still
finish opening. The check runs on a background thread, reads only metadata, and leaves
tables, session settings and pending transactions unchanged. The warning does not disable editing.

## Tests and continuous integration

Use JDK 11 or later and a full Apache Ant 1.10.6+ installation, including
`ant-junitlauncher.jar` in Ant's `lib` directory. From the repository root, run:

```sh
ant -f build-tests.xml
```

The default target compiles the application and runs the JUnit 5 suite in `test/`.
The suite includes unit tests, database integration tests, complete assignments and
OpenMap workflow tests. It runs without a graphical desktop or an existing project
database; database tests create private temporary H2 or HSQLDB databases, and file
tests use temporary directories. The command fails if any test fails.

Text and XML reports are written to `test-build/reports/`. Test classes are kept
separate from application classes and are not included in `nodus9.jar`.

To run a single test class:

```sh
ant -f build-tests.xml '-Dtest.includes=**/CostExpressionCacheTest.class' Test
```

To remove test output:

```sh
ant -f build-tests.xml CleanTests
```

In Eclipse, refresh the project and select **Run As > JUnit Test** for the `test` source
folder or an individual test class. For Ant, right-click `build-tests.xml` and choose
**Run As > Ant Build**. The `Test` target in `build-user.xml` forwards to the dedicated
build file, so `ant Test` also runs the suite. `ant Installer` runs the suite before
packaging and aborts if it fails.

[GitHub Actions](https://github.com/jourquin/Nodus/blob/master/.github/workflows/tests.yml) runs the full suite on Ubuntu with
Temurin Java 11, 25 and 27 for pushes and pull requests, and saves the reports as downloadable
artifacts. See the [test guide](https://github.com/jourquin/Nodus/blob/master/test/README.md) for coverage, Eclipse runtime setup
and guidelines for adding tests, and its
[CI instructions](https://github.com/jourquin/Nodus/blob/master/test/README.md#continuous-integration-on-github) for activation
and required status checks.

## Code quality tools

The [Checkstyle](https://checkstyle.org) plugin is used in Eclipse to check adherence to the
Google Java coding standard. The [SpotBugs](https://spotbugs.github.io) plugin checks Java code
for potential bugs.

Since Nodus 8.4, OpenAI [Codex](https://github.com/openai/codex) has been used to detect
potential bugs, optimize algorithms and create unit tests.
