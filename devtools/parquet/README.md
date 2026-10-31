# Parquet build and test dependencies

Hadoop client API/runtime 3.4.2 are retained here, outside `lib/`, so they are
excluded from the installer and normal runtime classpath. The API is needed by
`javac` to resolve overloads in Apache Parquet's public API. The client runtime is
needed by the standard Apache Parquet reader used in tests.

Test readers and fixture writers use Parquet's local-file APIs with
`PlainParquetConfiguration`. This avoids Hadoop filesystem authentication, which
calls `Subject.getSubject` and fails on recent JDKs, while retaining the standard
Apache reader as an independent check of Nodus exports.

Nodus exports use `PlainParquetConfiguration` and a JDK GZIP codec, without loading
Hadoop or parquet-floor at runtime. Imports use `LocalInputFile`, Parquet's row-group
reader, and local GZIP/Snappy/uncompressed decoders. `NodusReadOptions` is a small
bridge in `org.apache.parquet`: Parquet 1.16.0's public options builder unconditionally
loads its Hadoop input-format class, so the bridge calls the package-private
constructor directly. Keep the dependency pinned and compile/run the isolation
test when upgrading; constructor incompatibilities fail at compilation. The regression test launches a separate JVM
with the installed library layout, verifies Hadoop is absent, exports typed data,
imports it into H2/HSQLDB, and reads exports back with the standard Apache reader
in the parent test JVM. Additional fixtures cover Snappy/uncompressed input,
unsigned integers and nanosecond UTC timestamps.

Both JARs use the shared [Apache-2.0 license](../../licenses/ApacheV2.license).
Additional component terms and notices are consolidated in [NOTICE.txt](NOTICE.txt)
and remain inside each unmodified JAR. See the
[license inventory](../../licenses/LICENSES.md#table-format-dependency-downloads-and-checksums)
for Maven Central URLs and SHA-256 hashes.
