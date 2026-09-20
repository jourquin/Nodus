# Regression checks

## Assignment timing checks

Run `sh devtools/tests/run-computing-times-test.sh` from the project root with a JDK 11 or later.
The standalone checks use a controlled clock and two threads to verify overlapping worker times,
database-time attribution, repeated cost passes, resetting between assignments, disabled auditing
and partial-run reporting. Compilation uses a temporary directory and requires no external libraries.

## Cost parser cache checks

`CostParserCacheTest.java` checks formula reuse, changing variable values, movement/node
transitions, formula overrides, custom functions and parser errors. Its inputs are in-memory test
layers and formulas; it does not open or modify a project or database. Run it after changing the
cost parser or its expression cache. Run the following commands from the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/CostParserCacheTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.costs.CostParserCacheTest
rm -r "$nodus_test_dir"
```
