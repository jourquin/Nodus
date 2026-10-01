# Modal-choice plugins in Nodus 9.0

All modal-choice implementations, estimators and their dialog now live in
`edu.uclouvain.core.nodus.compute.modalsplit`, alongside `compute.assign`.
The previous `compute.assign.modalsplit` package is removed. This is a Java API
change: an existing compiled plugin needs its class references updated before it can load.

## Upgrade an existing plugin JAR

When a project contains a plugin using the old `ModalSplitMethod`, `Path` or
`PathsForMode` classes, Nodus offers **Upgrade** or **Skip plugin**. Accepting the
upgrade rewrites the compiled references to these unchanged API classes, checks
that the converted classes can load, then installs the converted JAR under its
original name. No Java compiler or plugin source is required.

The original archive is preserved byte for byte beside it as `pluginName.jar.nodus8`.
Nodus does not load this backup and never overwrites an existing backup. Converted
plugins load immediately; subsequent project openings do not prompt again. Skipping
leaves the JAR unchanged and continues loading the project without that modal method.

The converter updates class references, descriptors, generic signatures and literal
reflection names stored in Java's [class-file constant pool](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4).
It preserves other JAR resources and uses an atomic file replacement after validation
and backup. Conversion failures leave the original JAR in place.

This is a migration of the three shared API types, not a repair tool for arbitrary
binary incompatibilities.

## Update source code or rebuild manually

1. Replace `edu.uclouvain.core.nodus.compute.assign.modalsplit` with
   `edu.uclouvain.core.nodus.compute.modalsplit` in Java and Groovy imports and any
   fully qualified class names. Update reflection strings and external configuration
   that explicitly names those classes as well.
2. Recompile Java plugins against `nodus9.jar` and the supplied libraries. The
   `ModalSplitMethod` lifecycle and `split(ODCell, List<PathsForMode>)` signature
   are unchanged apart from the package names of their types. Update imports for
   `ModalSplitMethod`, `PathsForMode` and `Path` together.
3. Replace the old plugin JAR in the project directory with the recompiled JAR.
   Remove duplicate old copies, then reopen the project. Groovy source scripts
   need updated imports. The automatic JAR converter does not edit source scripts.

The updated [MLogit example](../demo/MLogit/MLogit.java) and its compilation scripts
show the new imports and build classpath.

## What stays compatible

The package move does not change OD tables, network tables, project properties or
cost-file parameter keys. No model needs re-estimation solely because
of the package move.

Estimation is a separate operation under **Project > Modal choice estimation**.
It reads the selected source cost file and writes the estimated parameters to the named output
cost file, defaulting to the same name. Subsequent assignments use their own selected cost file,
OD matrix and modal-choice method.

Groups without parameters for the selected embedded logit or probit model use
`V = -C` (cost factor 1, modal constants 0), after one warning per assignment.
For MNL, this preserves the earlier modal and exponential route shares. Probit
uses the same fallback utility with independent normal errors. Defaults are not
written to the cost file; saved log-cost coefficients continue to take precedence,
and incomplete or invalid saved coefficients remain errors.

Nodus 9.0 distributions use `nodus9.jar`, `nodus9.sh` / `nodus9.bat`, and the
`Nodus9` installation directory. Preferences are stored in `.nodus9.properties`
in the user's home directory.

## Why there are no deprecated package aliases

Modal plugins exchange mutable lists of `Path` and `PathsForMode` objects. Empty
subclasses cannot preserve both their old generic signatures and their original
inheritance relationships. Keeping binary compatibility would require data and
method adapters at the plugin boundary. Nodus 9.0 instead makes the package change
explicit and keeps one implementation and one extension API.

For the estimation architecture and input conventions, see
[the package documentation](../src/edu/uclouvain/core/nodus/compute/modalsplit/package-info.java).
