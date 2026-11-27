# Nodus plugins

The installer includes the plugin source and compile scripts. To build either plugin, use a
JDK 11 or newer with `javac` and `jar` on your PATH and keep `nodus9.jar` and `lib/` in the Nodus
root directory. The scripts place their JARs in this directory. Restart Nodus to load them.


## Sample plugin

From this directory, run `Sample\compile.bat` on Windows or `./Sample/compile.sh` on macOS or Linux.


## Parquet Reader

Choose **Tools → Parquet Reader** to open a local Parquet file in a read-only grid. The reader
shows the first 100 rows by default. Change **Rows to display** to refresh the preview. When
a project is open, the file chooser starts in its directory. You can also open files without
a project. The reader supports the same compression codecs as the Nodus Parquet importer
(GZIP, Snappy, and uncompressed).

From this directory, run `ParquetReader\compile.bat` on Windows or
`./ParquetReader/compile.sh` on macOS or Linux.
