# SQLConsole compatibility baseline

`api.txt` records the public/protected API and direct supertypes of `SQLConsole`
before its implementation was split into internal helpers. The API test in
`ExtensionApiCompatibilityTest` compares the current class with this snapshot.

`legacy-sql-console.jar` contains an external subclass compiled with
`javac --release 11` against the original class. Its source is preserved as
`LegacySQLConsole.java.txt` so normal test compilation cannot rebuild it against
the refactored API. The JAR contains only that subclass, with no Nodus classes
or dependencies. `SQLConsoleCompatibilityTest` loads the original bytecode,
runs a batch, and verifies that direct overwrite commands still invoke its
protected dialog override on the EDT.

Keep these fixtures unchanged during internal refactors. They cover the supported
API and subclass calls, not reflective access to private implementation details.
