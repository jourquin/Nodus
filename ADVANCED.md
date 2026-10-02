# Nodus: advanced use and development

This guide is for power users and developers configuring Nodus or working on its code.
For a software overview, installation, compilation and platform-specific tips,
see the [main README](README.md).

- [Memory allocation and JVM options](#memory-allocation)
- [Session settings in NodusC.java](#session-settings)
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
later (including Java 27) receive no `--illegal-access` option. Existing multi-line
scripts and assignments containing custom shell commands or variable expansion are
left intact; remove an unconditional obsolete option manually in those files.

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

## Import and layer-save recovery

DBF imports and Excel imports with a schema row load into a staging table before
replacing the destination. Engines such as H2 and HSQLDB commit schema changes
implicitly, so these imports use a separate connection: a successful replacement
is committed independently, while unrelated work on the project's connection
remains uncommitted. Commit or roll back pending changes to the destination before
replacing it, to avoid a lock conflict. Excel imports without a schema row replace
rows within the project's transaction and roll back only the import on failure.

Layer saves write changed `.shp`, `.shx` and `.dbf` files to a temporary recovery
directory named `.<layer>.nodus-save`. Existing files are backed up before any
replacement. A failed save restores the previous files and keeps the layer marked
as modified, so saving can be retried. It also prevents the project from closing
when the user has chosen to save. Each layer is saved independently.

Opening a layer or retrying its save recovers an interrupted replacement. If
restoration itself fails, the recovery directory is retained; resolve the file
access problem before retrying, and do not delete those backups. This mechanism
handles application interruption; it is not a guarantee against power loss or
storage failure and does not replace project backups.

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

[GitHub Actions](.github/workflows/tests.yml) runs the full suite on Temurin Java 11
and 25 on Ubuntu for pushes and pull requests, and saves the reports as downloadable
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
