# Nodus: advanced use and development

This guide is for power users and developers configuring Nodus or working on its code.
For a software overview, installation, compilation and platform-specific tips,
see the [main README](README.md).

- [Memory allocation and JVM options](#memory-allocation)
- [Application and project settings in NodusC.java](#application-and-project-settings)
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

## Application and project settings

Public settings in [NodusC.java](https://github.com/jourquin/Nodus/blob/master/src/edu/uclouvain/core/nodus/NodusC.java) can be set
from the startup [nodus.groovy](nodus.groovy) script, a project's `[projectname].groovy` file,
or the Groovy console.

The settings follow this order:

1. The values declared in `NodusC` provide the defaults.
2. Changes made by the startup `nodus.groovy` become the application defaults for that session.
3. Each project starts with those application defaults; its own Groovy script can override them.

Before another project loads, all public mutable `NodusC` settings are restored to the application
defaults, even if the next project has no Groovy file. This includes timing reports, assignment
dialogs, estimation diagnostics, map options and the Exact MF algorithm selector. Changes made
from a project script or the Groovy console last until the next project loads. A project's close
script still sees its current settings; canceling project closure keeps those settings in place.

Closing a project, opening another one, or quitting waits for an active assignment, estimation
or KPI computation to finish before running the close script and releasing project resources.
The computation can still be canceled through its normal progress controls. This keeps the
database connection and loaded layers available until the worker has finished its cleanup.

Put a setting in the startup script to apply it to every project, or in a project's script to
apply it only there. The startup script is run once at application launch, not on every project
switch. Existing HTML reports are retained when reporting is disabled.

### Assignment information dialogs

Informational assignment completion dialogs are enabled by default. To disable them for all
projects, add this to the startup `nodus.groovy` script:

```groovy
edu.uclouvain.core.nodus.NodusC.displayAssignmentInformationDialogs = false
```

Set it back to `true` to restore the dialogs. The setting is read when an assignment finishes;
it does not change calculations, saved results or completion metadata. Errors and warnings,
including reaching the maximum iteration count without convergence, remain visible.

### Assignment runtime audit

Set `NodusC.displayComputingTimes = true` to save a timing report as an HTML file in the project
directory for each assignment. The switch defaults to `false` and is sampled at the beginning of
`Assignment.run()`.
It can also be enabled from a Groovy script before running assignments:

```groovy
edu.uclouvain.core.nodus.NodusC.displayComputingTimes = true
```

Set `NodusC.displayComputingTimes = false` before starting an assignment to disable new reports.
Existing HTML files remain unchanged. `NodusC.displayMapComputingTimes` is independent: it controls
map preparation timings printed during pan/zoom operations and does not control assignment reports.

The report identifies the algorithm, scenario, configured thread count and completion status,
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
cancelled runs save a partial audit, excluding subsequent failure cleanup.

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
Explicit connection settings continue to be honored, including a legacy MyISAM default. Changing
`default_storage_engine` affects newly created tables only; existing tables require a separate,
explicit conversion. Nodus never converts them automatically.

After imports and the project-open script finish, Nodus checks the actual base-table engines in
the selected database and the session's default engine. It warns once per project opening if
either uses a nontransactional engine, including mixed InnoDB/MyISAM databases and empty databases
with a nontransactional default. Unknown engine capabilities or failed checks also produce a
warning; opening can continue. The check runs off the UI thread, reads only metadata, and leaves
tables, session settings and pending transactions unchanged. The warning does not disable editing.

The dialog's **Do not show this warning again** checkbox hides subsequent storage-engine warnings
for this project. The choice is saved with the other project-local settings in `ProjectName.nodus.local` when
the project closes; other projects are unaffected. Metadata checks still run. To show the warning
again, remove `showStorageEngineWarning=false` from that project's local settings file or set it to
`true` while the project is closed.

InnoDB protects transactional data changes; schema operations can still cause implicit commits.
Nodus loads imports and assignment outputs into staging tables before replacing existing tables.
On MySQL/MariaDB, publication uses one grouped `RENAME TABLE` statement: the previous tables move
to private backup names and their replacements take the public names together. For assignments,
the group includes the virtual network, path headers and optional path details; an obsolete detail
table moves aside in the same statement. Other database products retain their existing publication
path.

If publication fails, Nodus inspects the actual table names and restores the previous set with one
inverse rename. This also handles a JDBC exception after the server executed the original rename.
If restoration cannot be verified or completed, backup tables remain available for recovery.
Backup cleanup after successful publication is optional: a cleanup error is reported without
undoing the completed import or assignment. Server-crash recovery guarantees still depend on the
server version and storage engine. Transaction guarantees also do not extend to external DBF/shape
files, whose saves have separate file-recovery logic.

Performance depends on the workload and configuration. Compare disposable copies of a project
with matching indexes, batch sizes and thread counts, an adequate InnoDB buffer pool, and normal
durability settings. Include both initial and warmed-up runs, and compare import, output-writing
and total assignment times, especially when detailed paths are saved. No fixed speed ratio is
assumed. See MariaDB's [storage-engine guidance](https://mariadb.com/docs/server/server-usage/storage-engines/choosing-the-right-storage-engine)
and [implicit-commit reference](https://mariadb.com/docs/server/reference/sql-statements/transactions/sql-statements-that-cause-an-implicit-commit).

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

Legacy node-rule tables with a two-digit numeric `scenario` column are widened when the rules
are loaded, allowing the full scenario range through 1000. Existing rules and unrelated pending
edits are preserved. On database engines whose schema changes commit automatically, the upgrade
uses a separate connection; a lock held by another editor may need to be released before retrying.

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

[GitHub Actions](https://github.com/jourquin/Nodus/blob/master/.github/workflows/tests.yml) runs the full suite on Temurin Java 11,
25 and 27 on Ubuntu for pushes and pull requests, and saves the reports as downloadable
artifacts. See the [test guide](https://github.com/jourquin/Nodus/blob/master/test/README.md) for coverage, Eclipse runtime setup
and guidelines for adding tests, and its
[CI instructions](https://github.com/jourquin/Nodus/blob/master/test/README.md#continuous-integration-on-github) for activation
and required status checks.

## Code quality tools

The [Checkstyle](https://checkstyle.org) and [SpotBugs](https://spotbugs.github.io)
plugins are used in Eclipse to follow the Google Java coding standard and look for
bugs in Java code.

Since Nodus 8.4, OpenAI [Codex](https://github.com/openai/codex) has been used to detect
potential bugs, optimize algorithms and create unit tests.
