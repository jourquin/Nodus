# Tiny master OD example

`sample_road.parquet` is a small, fully synthetic Snappy Parquet master with **8 rows**,
**26 available annual tonnes**, four declared NST/R groups (00, 01, 10 and 99) and
five fictional centroid IDs. The AA zone codes and traffic quantities are invented.
`sample_rows.sql` shows every input value. No real estimates or confidential data
are included.

## Try it in Nodus

1. Copy this folder to a writable location and open its `sample.nodus` project in Nodus.
   The included centroid and road shapefiles form a tiny illustrative network.
2. Open `ExtractMasterOD.groovy` from Nodus's `scripts` folder.
3. Leave `masterDirectory = ""`: the script obtains the copied project's directory
   from Nodus. Keep `tablePrefix = "SAMPLEOD"`, `nutsLevel = 3`, `commodityLevel = "NSTR"` and
   `missingPolicy = "AVAILABLE_WITH_REPORT"`, then run.
4. Inspect `SAMPLEOD_L3_NSTR_ROAD` in the SQL console and the JSON report in
   your copied project's `ODExtractionReports` folder.

The script ends with `new ExtractMasterOD(nodusMapPanel);`. Its constructor reads
the open project's `NodusC.PROP_PROJECT_DOTPATH`, so moving or copying the complete
sample folder requires no source-path edit. An explicit `masterDirectory` remains
available for a dataset stored outside the project folder. The research-delivery
copy may use `OD2024` as its table prefix; change that field to `SAMPLEOD` for this
example. No Python or helper JAR is needed.
Nodus may create its normal local database and project settings in the copied folder.

Use this separate sample project for the fictional zones. The extractor deliberately
rejects centroid IDs/codes/parents that differ from the catalogue. It does not add or
modify centroid layers in your research project. This example demonstrates extraction;
it supplies no calibrated costs or empirical validation of assignments.

## Expected results

All four combinations preserve **26 available tonnes**:

| `nutsLevel` | `commodityLevel` | Positive output rows |
| --- | --- | ---: |
| 3 | NSTR | 6 |
| 2 | NSTR | 3 |
| 3 | CHAPTER | 5 |
| 2 | CHAPTER | 2 |

For level 2 / CHAPTER, the complete positive output is:

| GRP | ORG | DST | QTY |
| ---: | ---: | ---: | ---: |
| 0 | 10 | 10 | 13.0 |
| 1 | 10 | 20 | 13.0 |

Other useful cases:

- `groups = java.util.Arrays.asList(0)` with `commodityLevel = "CHAPTER"` exports 13 tonnes.
- Detailed group 99 is source-absent. Selecting it produces an empty typed OD table
  with an explicit absence report, not an assertion of observed zero traffic.
- One row has an unavailable quantity; another contains only known components.
  Reports count three unestimated components in total.
- A 2.75-tonne proxy/spatial-review row demonstrates retained flags and overlapping
  report totals. One zero-tonne row is retained in the master and omitted from exports.
- `REQUIRE_COMPLETE` rejects the full sample. With detailed group 10 selected it
  succeeds and exports two level-3 rows totalling 13 tonnes.

To regenerate the assets during development, run
`python test/fixtures/master-od-sample/generate.py` from the Nodus checkout with
DuckDB and pyshp installed. The generator reads no research datasets or project
databases. It does not overwrite this README.

## Optional portable SQL example

`sample_rows.sql` can also create the eight sample records directly in an SQL
database. It uses an explicitly typed `CREATE TABLE sample` followed by eight
single-row `INSERT INTO sample (...) VALUES (...)` statements, with standard
`SMALLINT`, `BIGINT`, `INTEGER`, `DOUBLE PRECISION` and `BOOLEAN` types.
NULL quantities and TRUE/FALSE flags are retained. MySQL/MariaDB represent
`BOOLEAN` as a numeric boolean alias.

Run the complete file in a database where `sample` does not already exist. It
contains no DROP, TRUNCATE or database-creation commands. It creates a master-shaped
example table, not a four-column Nodus assignment table. Running the SQL is optional;
the normal Groovy extractor continues to read the existing Parquet file and catalogue.

The statement syntax targets HSQLDB, H2, MySQL/MariaDB and DuckDB; no table-value
constructor, CREATE TABLE AS shorthand or engine-specific export command is used.
The regression tests execute the file in H2 and HSQLDB and compare all eight rows,
all eleven columns and NULL values against `sample_road.parquet`.

Validation for this revision: H2 2.4.240, HSQLDB 2.7.4, MariaDB 13.0.2 and
DuckDB 2.0.0 all reproduced the eight rows and their 26 known annual tonnes.
The generator also reproduced every existing Parquet value, including NULLs.
MySQL 8.4 compatibility was checked against its official
[INSERT syntax](https://dev.mysql.com/doc/refman/8.4/en/insert.html) and
[numeric/boolean types](https://dev.mysql.com/doc/refman/8.4/en/numeric-type-syntax.html);
a separate MySQL server was not available for execution testing.
