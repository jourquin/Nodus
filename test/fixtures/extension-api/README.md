# Extension API compatibility baseline

`api.txt` records the public/protected constructors, methods, fields, generic signatures,
modifiers, checked exceptions and direct supertypes of `NodusMapPanel`, `NodusProject`
and `NodusConsole` before their implementation was split into internal helpers.
`ExtensionApiCompatibilityTest` compares the current classes with that baseline.

`legacy-extension.jar` contains `LegacyExtension` and its `Panel` subclass, compiled
with `javac --release 11` against the original, pre-refactoring classes. The exact
source is `LegacyExtension.java.txt`; the suffix prevents the normal test compilation
from recompiling the fixture against the current API. The JAR contains no Nodus
implementation classes or external libraries. Tests load this existing bytecode with
both a regular class loader and Nodus's plugin loader.

Keep the baseline and JAR unchanged for internal refactors. Changing a baseline to
make a failing test pass defeats the compatibility check. If an intentional API
migration requires a new baseline, retain the previous fixture for the compatibility
policy that still applies and document the migration separately.

The plugin exercises the original map-panel entry point, the protected constructor,
subclass callbacks, project access, mutable collections with concrete return types,
style resources, console visibility, and plugin disposal. The tests also execute
Groovy property access and overloaded methods. They do not claim that every possible
third-party extension, private reflective access, or serialized Swing object is covered.
