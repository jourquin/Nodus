package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class DbfEncodingIntegrationTest {
  private static final Charset CP1252 = Charset.forName("windows-1252");
  private static final String TEXT = "Été – coût €";
  @TempDir Path directory;

  @Test
  void cpgOverridesHeaderAndExplicitOverrideWinsOverCpg() throws Exception {
    Path file = write("legacy", CP1252);
    assertEquals(CP1252, DbfFiles.charset(file, null));
    Files.writeString(directory.resolve("legacy.CPG"), "65001\n");
    assertEquals(StandardCharsets.UTF_8, DbfFiles.charset(file, null));
    assertThrows(
        DBFException.class,
        () -> {
          try (DBFReader reader = DbfFiles.reader(file, null)) {
            reader.nextRecord();
          }
        });
    try (DBFReader reader = DbfFiles.reader(file, "1252")) {
      assertEquals(TEXT, reader.nextRecord()[1]);
    }
    Files.writeString(directory.resolve("legacy.CPG"), "not-a-charset");
    assertThrows(java.io.IOException.class, () -> DbfFiles.charset(file, null));
    assertEquals(CP1252, DbfFiles.charset(file, "windows-1252"));
  }

  @Test
  void utf8FallbackAndCpgReadNamesAndTextIndependentlyOfJvmDefaults() throws Exception {
    Path file = write("utf", StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, DbfFiles.charset(file, null));
    byte[] bytes = Files.readAllBytes(file);
    bytes[29] = 3; // A misleading CP1252 header must not override explicit CPG metadata.
    Files.write(file, bytes);
    Files.writeString(DbfFiles.cpg(file), "\uFEFFUTF-8\n");
    DbfFiles.Table table = DbfFiles.readModel(file, null);
    assertEquals(StandardCharsets.UTF_8, table.charset);
    assertEquals("LABEL", table.model.getColumnName(1));
    assertEquals(TEXT, table.model.getValueAt(0, 1));
  }

  @Test
  void layerOverrideTakesPrecedenceOverProjectOverride() throws Exception {
    Properties settings = new Properties();
    NodusProject project =
        new NodusProject(null) {
          @Override
          public String getLocalProperty(String key, String fallback) {
            return settings.getProperty(key, fallback);
          }
        };
    settings.setProperty("dbfCharset", "UTF-8");
    settings.setProperty("legacy.dbfCharset", "1252");
    Path file = write("legacy", CP1252);
    try (DBFReader reader = DbfFiles.reader(project, file)) {
      assertEquals(TEXT, reader.nextRecord()[1]);
    }
    settings.remove("legacy.dbfCharset");
    assertThrows(
        DBFException.class,
        () -> {
          try (DBFReader reader = DbfFiles.reader(project, file)) {
            reader.nextRecord();
          }
        });
  }

  @Test
  void importsAndExportsMixedEncodingsAndKeepsExistingFilesOnUnrepresentableText()
      throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      final Path legacy = write("legacy", CP1252);
      write("utf", StandardCharsets.UTF_8);
      assertTrue(ImportDBF.importTable(database.project, "legacy"));
      assertTrue(ImportDBF.importTable(database.project, "utf"));
      try (var statement = database.connection.createStatement();
          var rows =
              statement.executeQuery("SELECT label FROM legacy UNION ALL SELECT label FROM utf")) {
        int count = 0;
        while (rows.next()) {
          assertEquals(TEXT, rows.getString(1));
          count++;
        }
        assertEquals(2, count);
      }
      assertTrue(ExportDBF.exportTable(database.project, "legacy"));
      assertEquals("windows-1252\n", Files.readString(DbfFiles.cpg(legacy)));
      try (DBFReader reader = DbfFiles.reader(legacy, null)) {
        assertEquals(TEXT, reader.nextRecord()[1]);
      }
      byte[] before = Files.readAllBytes(legacy);
      final byte[] cpg = Files.readAllBytes(DbfFiles.cpg(legacy));
      database.execute("UPDATE legacy SET label='漢字'");
      assertFalse(ExportDBF.exportTable(database.project, "legacy"));
      assertArrayEquals(before, Files.readAllBytes(legacy));
      assertArrayEquals(cpg, Files.readAllBytes(DbfFiles.cpg(legacy)));
      assertFalse(Files.exists(directory.resolve(".legacy.nodus-save")));
    }
  }

  @Test
  void deletedRowsRemainAlignedInGeometryModelButAreSkippedBySqlImport() throws Exception {
    Path file = write("deleted", StandardCharsets.UTF_8);
    byte[] bytes = Files.readAllBytes(file);
    int headerSize = (bytes[8] & 0xff) | ((bytes[9] & 0xff) << 8);
    bytes[headerSize] = '*';
    Files.write(file, bytes);
    DbfFiles.Table table = DbfFiles.readModel(file, null);
    assertEquals(1, table.model.getRowCount());
    assertEquals(TEXT, table.model.getValueAt(0, 1));
    try (DBFReader reader = DbfFiles.reader(file, null)) {
      assertNull(reader.nextRecord());
    }
  }

  private Path write(String name, Charset charset) {
    Path file = directory.resolve(name + ".dbf");
    DBFField[] fields = {new DBFField("ID", 'N', 5, 0), new DBFField("LABEL", 'C', 80, 0)};
    try (DBFWriter writer = new DBFWriter(file.toString(), fields, charset)) {
      writer.addRecord(new Object[] {1, TEXT});
    }
    return file;
  }
}
