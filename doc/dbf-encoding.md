# DBF text encoding

Nodus resolves each DBF independently. SQL imports, project-layer attributes and
exports share the same policy:

1. An explicit `<table>.dbfCharset` project property, or the project-wide
   `dbfCharset` property if there is no table-specific override.
2. The companion `.cpg` file, including uppercase `.CPG` filenames.
3. A recognized language-driver/code-page byte in the DBF header.
4. UTF-8 when no encoding is declared.

The UTF-8 fallback keeps existing Nodus files without metadata compatible. It
cannot identify an unmarked file from another tool. Declare that file's encoding
with `.cpg` or an explicit override rather than relying on the operating system.
Explicit overrides also take precedence over incorrect metadata.

For example, in `ProjectName.nodus` or the project's local properties:

```properties
# Force this table's legacy Windows encoding:
road_links.dbfCharset=windows-1252

# Optional explicit encoding for all other project DBFs:
# dbfCharset=UTF-8
```

Use the DBF filename stem for the table-specific key. CPG files accept Java charset
names such as `UTF-8`, `windows-1252` and `ISO-8859-1`, and common numeric code pages
such as `65001` (UTF-8) and `1252`. Invalid or conflicting metadata is reported.
A global project override deliberately overrides metadata on every affected file;
prefer table-specific overrides in projects containing several encodings.

New files default to UTF-8. Existing file exports and layer saves retain their
resolved encoding and write matching CPG metadata. Network-layer saves use the
encoding captured when the layer was loaded. Reload a layer after changing its
encoding properties or metadata. DBF and CPG replacements participate in the same
recoverable save, so a failed export does not publish a mismatched pair.

Malformed input text or characters that cannot be represented in the output
encoding are reported instead of being replaced silently. Character-field widths
are measured in bytes. The existing truncation policy remains, but truncation cuts
at whole Unicode code points rather than splitting a surrogate pair.

The Windows launcher still defaults to UTF-8 for other application text. DBF text
handling no longer requires that JVM default to match the particular DBF file.

## javadbf4nodus 1.12.4

Each reader and writer retains its own effective charset. Opening a second file
with another charset does not change an already-open stream. The static
`DBFBase.setCharset` API remains available to configure the fallback for future
instances; `getEffectiveCharset()` reports an instance's actual encoding.

A library reader without an explicit charset detects the DBF language driver and
then uses its configured fallback. CPG resolution is performed by Nodus, which
passes the resulting charset explicitly to the reader or writer.
