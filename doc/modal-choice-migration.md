# Modal-choice plugins in Nodus 9.0

All modal-choice implementations, estimators and their dialog now live in
`edu.uclouvain.core.nodus.compute.modalsplit`, alongside `compute.assign`.
The previous `compute.assign.modalsplit` package is removed.
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
It reads the selected cost file for route costs and fits only the selected embedded method
using the mapped observed modal OD tables. Each run saves one parameter table named after the
selected cost file: `NodusEstimated.costs` gives `NodusEstimated_params`.
The table has `param_key`, `param_value` and `param_type` columns. 
The cost file receives a single `@paramTable=<table>` property. The estimation report is saved beside it as
`<cost-file-stem>_params.txt`, for example `NodusEstimated_params.txt`. To assign with the fitted parameters, select the updated cost file in the assignment dialog. Its parameter table selects and locks the embedded modal method automatically; estimation does not change scenario settings.

If the optional merged OD table already exists, choose **Cancel** to return to the
estimation dialog, **Overwrite** to replace it, or **Skip merging** to estimate and save
the model without changing that table. Skipping applies to the current run; the entered
table name remains available for the next estimation.

Selecting **Estimate pivots** adds bounded utility constants for nonreference modes at the
mode–origin–destination–commodity-group level. The behavioral coefficients are estimated first;
the pivots are then fitted against observed modal OD quantities using iterative log-ratio updates.
The reference mode's correction stays zero; the dialog's maximum absolute pivot defaults to 8
and can be changed before estimation. Pivot rows use `pivot.<mode>.<origin>.<destination>.<group>`;
the table stores the chosen bound in `@nodus.pivotMaxAbs`. Absent pivot keys are interpreted as
zero during assignment. For scenario assignments the stored constants stay fixed
while route costs and behavioral utilities change. Re-estimating a table replaces its previous
rows after confirmation. Existing projects without `@paramTable` can still read legacy coefficient
keys from their cost files.

For logit and probit, **Conditional** is checked by default: one coefficient of
`ln(cost)` is estimated per commodity group, alongside modal constants. Uncheck it
to estimate a separate cost coefficient for every mode, including the reference mode
(only its constant is fixed at zero). The choice is saved as an estimation preference;
assignment reads the coefficients from the parameter table or legacy cost file.
The checkbox is disabled for **Proportional**, which estimates modal adjustment factors
and fixes the common log-cost coefficient at -1.

Logit fits constrain each cost coefficient to be at most zero. A coefficient at the
zero bound means the fitted modal utility does not respond to cost; the estimation
report identifies these modes, and standard errors at the boundary need caution.
Probit fits start with negative cost coefficients and still fail if a converged
coefficient is zero or positive. Failed runs do not replace existing saved parameters.

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
