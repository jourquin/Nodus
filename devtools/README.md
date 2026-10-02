# Nodus development tools

This directory contains the formatter, lexer generator, Checkstyle configuration,
installer libraries and test annotations used by the project. Run the Ant commands
below from the repository root, using a JDK 11 or later and a full Apache Ant 1.10.6+
installation. The [test guide](../test/README.md) covers Ant and Eclipse setup.

## Build and test targets

The generated [build.xml](../build.xml) imports [build-user.xml](../build-user.xml),
which defines the custom targets. After exporting the Eclipse project to Ant again,
check that `build.xml` still contains `<import file="build-user.xml"/>`.

| Command | Result |
| --- | --- |
| `ant` or `ant build` | Compiles application and tests; no test execution or JAR packaging. |
| `ant Test` | Builds the application and runs all JUnit 5 unit and integration tests. |
| `ant -f build-tests.xml` | Runs the same suite; this file's default target is `Test`. |
| `ant Jar` | Rebuilds and packages `nodus9.jar`, including a new build ID; does not run tests. |
| `ant Installer` | Runs tests, then builds help, the application JAR, API docs and installer. |
| `ant ServicesWorkflowHtml` | Regenerates service workflow HTML when Pandoc is available. |

The installer is written to `BinaryDistribution/Nodus9-install.jar`. Test failures
stop the `Installer` target before packaging. Use `ant -projecthelp` to list the
available targets; `build-user.xml` itself has no default target.

## Java formatting and Checkstyle

[google-java-format-eclipse-plugin_1.6.0.jar](google-java-format-eclipse-plugin_1.6.0.jar)
is the bundled [google-java-format](https://github.com/google/google-java-format)
Eclipse plugin. Install it in Eclipse's `dropins` directory, restart Eclipse, and
select it as the formatter implementation in the Java code style preferences.

[nodus_checks.xml](nodus_checks.xml) is the project's Checkstyle configuration.
The root [.checkstyle](../.checkstyle) file already selects it as **Nodus checks**.
It is based on Google Java Style, with project-specific settings including:

- A 100-character Java line limit, with the explicit package/import/URL exemptions
  listed in `LineLength.ignorePattern`.
- Two-space basic indentation and four-space continuation indentation.
- `AbbreviationAsWordInName.allowedAbbreviationLength=4` for acronym-heavy names.
- Uppercase letters permitted in package segments after the first segment, for
  OpenMap packages. They are accepted by the pattern, not reported at info level.
- Warning severity by default; see the XML for all naming and Javadoc settings.

Formatting and Checkstyle are separate checks. `ant Test` does not run Checkstyle.

## SQL lexer generation

[JFlex.jar](JFlex.jar) contains **JFlex 1.4.1**. The `ant JFlex` target opens its
graphical generator. The lexer specification is
[NodusSQLTokenMaker.flex](../src/edu/uclouvain/core/nodus/database/sql/NodusSQLTokenMaker.flex),
which defines SQL syntax highlighting, including Nodus-specific commands.

Follow the regeneration instructions in that file's header: the generated scanner
needs the documented adjustments to `zzRefill`, `yyreset` and `zzBuffer` before use.
The ordinary build uses the existing Java scanner and does not regenerate it.

## Installer libraries

[IzPack/](IzPack/) contains **IzPack 5.2.4** and its bundled dependencies. The
`Installer` target uses these libraries with
[installer.xml](../installer/installer.xml). The
[local XML schemas](IzPack/schema/README.md) support offline validation of the
installer definition and language packs.

## Test dependencies

[junit/](junit/README.md) contains API Guardian 1.1.2 and its license. JUnit Jupiter,
JUnit Platform and OpenTest4J are bundled under `lib/groovy/`. The JUnit launcher
Ant task must come from the installed Ant distribution (`ant-junitlauncher.jar`).

## Markdown help generation

[Pandoc](https://pandoc.org/) is an external tool and is not bundled here. Run
`ant ServicesWorkflowHtml` to convert
[services-workflow.md](../doc/services/services-workflow.md) to its HTML help page.
If Pandoc is outside Ant's search path, supply its location:

```sh
ant -Dpandoc.executable=/path/to/pandoc ServicesWorkflowHtml
```

The target checks the usual Homebrew locations and Ant's environment path. If Pandoc
is unavailable, generation is skipped with a warning. Conversion failures also produce
a warning without aborting the build. `Installer` invokes this target automatically.

See the [license inventory](../licenses/LICENSES.md) for bundled components' licenses.
