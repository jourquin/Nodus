# Nodus: advanced use and development

This guide is for power users and developers configuring Nodus or working on its code.
For a software overview, installation, compilation and platform-specific tips,
see the [main README](README.md).

- [Memory allocation and JVM options](#memory-allocation)
- [Session settings in NodusC.java](#session-settings)
- [Local database servers and external JDBC connections](#local-database-servers-and-external-jdbc-connections)
- [Assignment information dialogs](#assignment-information-dialogs)
- [Assignment runtime audit](#assignment-runtime-audit)
- [Tests and continuous integration](#tests-and-continuous-integration)
- [Code quality tools](#code-quality-tools)

## Memory allocation

Nodus is written in Java. Therefore, it uses the memory allocation system provided by the Java Virtual Machine (JVM).
In particular, the maximum memory allocated to the software must be defined by the user if the default
values are not appropriate. This can be set using the -Xms and -Xmx command line parameters
passed to the JVM. Please refer to the JVM documentation for detailed information on these switches.

By default, Nodus uses the following strategy:

- If the physical memory of the computer it runs on is at most 4 GB, no -Xms (minimum heap) value is set.
Otherwise it is set to 2 GB.
- The maximum heap that can be claimed for (-Xmx) is set to 50% of the physical memory, with a maximum
of 6 GB.
- If Nodus runs on a 32-bit JVM (not recommended), -Xmx is limited to 1.4 GB and -Xms is not set.

These values are stored in "jvmargs.sh" or "jvmargs.bat", a file created in the installation directory by
Nodus at launch time if it doesn't exist yet. This file can be edited if other values (or even other JVM parameters)
are desired.

Since Nodus 8.4, version-dependent JVM parameters are also added dynamically in this file. This includes,
since Nodus 9.0, `--illegal-access=deny` with Java 9 to 16, and `--enable-native-access=ALL-UNNAMED` with Java 24
or later to enable native access for classpath libraries used by Nodus. With Java 24 or later,
the `sun.misc.Unsafe` warning policy is also set explicitly for compatibility
with libraries that still use deprecated memory-access methods.

Older releases could save `--illegal-access` permanently in a single-line `JVMARGS`
assignment. `SetJVMArgs` now upgrades these legacy files automatically, keeping custom
heap sizes and other arguments, and saving the original as `jvmargs.sh.bak` or
`jvmargs.bat.bak`. The replacement selects its flags at each launch, so Java 17 and
later (including Java 27) receive no `--illegal-access` option. Custom multiline
scripts and single assignments containing shell commands or variable expansion are
left intact; remove an unconditional obsolete option manually in those files.

`SetJVMArgs` also adds a macOS-only
`--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED` option to generated
`jvmargs.sh` files for Groovy's native console fullscreen support. It upgrades the
previous generated shell format automatically, preserving custom JVM arguments
and the first backup. Custom multiline scripts stay unchanged; add the option
manually if needed.

## Session settings

Public settings in [NodusC.java](src/edu/uclouvain/core/nodus/NodusC.java) can be set
from the startup [nodus.groovy](nodus.groovy) script or the Groovy console. The examples
below control assignment reporting for the current session. Put them in the startup
script to apply them whenever Nodus starts.

### Assignment information dialogs

Informational assignment completion dialogs are enabled by default. To disable them for
the current Nodus session, add this to the startup `nodus.groovy` script or run it from Groovy:

```groovy
edu.uclouvain.core.nodus.NodusC.displayAssignmentInformationDialogs = false
```

Set it back to `true` to restore the dialogs. The setting is read when an assignment finishes;
it does not change calculations, saved results or completion metadata. Errors and warnings,
including reaching the maximum iteration count without convergence, remain visible.

### Assignment runtime audit

Set `NodusC.displayComputingTimes = true` to print a timing summary to standard output for each
assignment. The switch defaults to `false` and is sampled at the beginning of `Assignment.run()`.
It can also be enabled from a Groovy script before running assignments:

```groovy
edu.uclouvain.core.nodus.NodusC.displayComputingTimes = true
```

The summary identifies the algorithm, scenario, configured thread count and completion status,
and reports seconds for:

- Total elapsed computation and saving, including final path batches, indexes and commits.
- Virtual-network initialization and generation.
- Cost and duration evaluation, including parser initialization, all iterations, OD classes,
  time slices and Frank–Wolfe line-search evaluations.
- Paths and flow assignment: elapsed wall time across active workers, plus summed worker time
  excluding their database writer calls. This includes graph preparation, path reconstruction,
  modal split and flow updates.
- Database output: writer time for table creation, row preparation, path batches, equilibrium
  path updates, virtual-network results, indexes and commits.

Path writes overlap the parallel assignment phase, so the reported rows must not be added together.
Summed worker time can exceed total elapsed time and includes waiting for the writer lock; it is
not CPU time. Total elapsed time also includes unlisted work such as loading demand and converting
volumes to vehicles. Completion dialogs and post-assignment scripts are excluded. Failed or
cancelled runs print a partial audit, excluding subsequent failure cleanup.

## Local database servers and external JDBC connections

When no `jdbc.url` is specified in the `.nodus` file, Nodus manages the selected
built-in database. HSQLDB and H2 listen only on `127.0.0.1`, so R, Python,
and other JDBC clients on the same computer can connect while the project is open.
They are not accessible from another computer. The default ports remain 9001 for
HSQLDB and 9092 for H2; override them with `hsqldbserverport` or
`h2serverport`. A port conflict aborts project opening rather
than connecting to the database service already using that port.

Existing database credentials are preserved. The demo's HSQLDB scripts still use
`SA` and an empty password through `localhost`; `127.0.0.1` can also be used explicitly
to avoid differences in IPv6 hostname resolution. Loopback access does not isolate
the database from other users or processes on the same computer.

Nodus creates H2 project databases locally, then exposes only the project database
through TCP. TCP clients can create tables and modify data according to their database
privileges, but cannot create additional databases. H2's binding is configured at
application startup because H2 caches this setting.

An explicit `jdbc.url` selects an independently managed database. Nodus passes the
configured URL, driver, username, and password to JDBC without changing the hostname
or URL options. It neither starts nor stops that server and does not offer server
shutdown/compaction on project close. This applies to local and remote MariaDB,
MySQL, PostgreSQL, and other JDBC databases, including separately managed HSQLDB
and H2 instances. For a Nodus-managed built-in server, omit `jdbc.url` and
use the port properties above instead.

For MySQL/MariaDB projects, Nodus sends a JDBC validation ping every minute while
the project is open. This keeps a normally idle server session active and detects a
lost connection. The interval must be shorter than the server's `wait_timeout`;
idle transaction timeouts may still close a session. MariaDB Connector/J 3.x enables
TCP keepalive by default, but TCP keepalive does not reset the server's SQL session timeout.
Connector/J 3.x also removed the `autoReconnect` URL option, so adding it to
`jdbc.url` will not recover a lost session.

Once a project session is lost, Nodus warns once and prevents saving network layer
edits to DBF files. Closing discards those unsaved network edits and does not save
pending service changes or run the project close script. Reopen the project through
**File > Open project** with **Re-import** checked to rebuild the database tables
from the DBF files. Nodus does not silently replace the session: the old connection
may have uncommitted work, and editors and database utilities still refer to it.

## Tests and continuous integration

Use JDK 11 or later and a full Apache Ant 1.10.6+ installation, including
`ant-junitlauncher.jar` in Ant's `lib` directory. From the repository root, run:

```sh
ant -f build-tests.xml
```

The default target compiles the application and runs the JUnit 5 suite in `test/`.
The suite includes unit tests, database integration tests, complete assignments and
OpenMap workflow tests. It runs without a graphical desktop or an existing project
database; database tests create private in-memory H2 or HSQLDB databases, and file
tests use temporary directories. A failing test makes the command fail.

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

In Eclipse, refresh the project and use **Run As > JUnit Test** on the `test` source
folder or an individual test class. For Ant, right-click `build-tests.xml` and choose
**Run As > Ant Build**. The `Test` target in `build-user.xml` forwards to the dedicated
build file, so `ant Test` also runs the suite. `ant Installer` runs the suite before
packaging and aborts if it fails.

[GitHub Actions](.github/workflows/tests.yml) runs the full suite on Temurin Java 11,
25 and 27 on Ubuntu for pushes and pull requests, and saves the reports as downloadable
artifacts. See the [test guide](test/README.md) for coverage, Eclipse runtime setup
and guidelines for adding tests, and its
[CI instructions](test/README.md#continuous-integration-on-github) for activation
and required status checks.

## Code quality tools

The [Checkstyle](https://checkstyle.org) and [SpotBugs](https://spotbugs.github.io)
plugins are used in Eclipse to follow the Google Java coding standard and look for
bugs in Java code.

Since Nodus 8.4, OpenAI [Codex](https://github.com/openai/codex) has been used to detect
potential bugs, optimize algorithms and create unit tests.
