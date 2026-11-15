package com.bbn.openmap.layer.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.dbf.LayerAttributeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Regressions use real DBFs and the production layer loader, including non-mandatory fields. */
@ResourceLock("JDBCUtils")
class NodusEsriLayerAttributeValidationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> invalidNumbersIdentifyThePhysicalRecordAndLeaveTheDbfUnmodified() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      for (boolean mandatory : new boolean[] {false, true}) {
        for (String value : List.of("", "**********", "NaN", "Infinity", "bad", "1E999")) {
          tests.add(
              dynamicTest(
                  "links=" + links + "/mandatory=" + mandatory + "/value=" + value,
                  () -> {
                    try (LayerTestProject project = new LayerTestProject(directory, links)) {
                      int column = mandatory ? 0 : links ? 10 : 4;
                      String field = mandatory ? "NUM" : "AMOUNT";
                      Path file = directory.resolve("features.dbf");
                      byte[] bad = replaceSecondRecord(file, column, value);
                      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
                      try {
                        LayerAttributeException error =
                            assertThrows(
                                LayerAttributeException.class,
                                () -> reloaded.setProject(project, "features"));
                        assertTrue(
                            error.getMessage().contains(file.toString()), error.getMessage());
                        assertTrue(error.getMessage().contains(field), error.getMessage());
                        assertTrue(
                            error.getMessage().contains("Record: 2")
                                || error.getMessage().contains("Enregistrement : 2"),
                            error.getMessage());
                        assertArrayEquals(
                            bad,
                            Files.readAllBytes(file),
                            "Invalid attributes must not be silently replaced with zero");
                      } finally {
                        reloaded.dispose();
                      }
                    }
                  }));
        }
      }
    }
    return tests;
  }

  @Test
  void aRealNumericZeroIsValidAndIsNotTreatedAsMissing() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      Path file = directory.resolve("features.dbf");
      byte[] zero = replaceSecondRecord(file, 4, "0.000");
      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
      try {
        reloaded.setProject(project, "features");
        assertEquals(0d, reloaded.getModel().getValueAt(1, 4));
        assertArrayEquals(zero, Files.readAllBytes(file));
      } finally {
        reloaded.dispose();
      }
    }
  }

  @Test
  void aDeletedPrecedingRecordDoesNotShiftTheReportedRecordNumber() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      Path file = directory.resolve("features.dbf");
      byte[] data = replaceSecondRecord(file, 4, "bad");
      int header =
          Short.toUnsignedInt(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(8));
      data[header] = (byte) '*';
      Files.write(file, data);
      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
      try {
        LayerAttributeException error =
            assertThrows(
                LayerAttributeException.class, () -> reloaded.setProject(project, "features"));
        assertTrue(error.getMessage().contains("Record: 2"), error.getMessage());
        assertTrue(error.getMessage().contains("AMOUNT"), error.getMessage());
        assertArrayEquals(data, Files.readAllBytes(file));
      } finally {
        reloaded.dispose();
      }
    }
  }

  @Test
  void anEmptyDateRetainsTheLegacyDefaultWithoutANullPointerFailure() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      replaceSecondRecord(directory.resolve("features.dbf"), 5, "");
      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
      try {
        reloaded.setProject(project, "features");
        assertEquals(
            new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date(0)),
            reloaded.getModel().getValueAt(1, 5));
      } finally {
        reloaded.dispose();
      }
    }
  }

  private static byte[] replaceSecondRecord(Path file, int column, String value) throws Exception {
    byte[] data = Files.readAllBytes(file);
    ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    int offset =
        Short.toUnsignedInt(header.getShort(8)) + Short.toUnsignedInt(header.getShort(10)) + 1;
    for (int i = 0; i < column; i++) offset += data[32 + i * 32 + 16] & 255;
    int width = data[32 + column * 32 + 16] & 255;
    byte[] text = value.getBytes(StandardCharsets.US_ASCII);
    assertTrue(text.length <= width);
    Arrays.fill(data, offset, offset + width, (byte) ' ');
    System.arraycopy(text, 0, data, offset + width - text.length, text.length);
    Files.write(file, data);
    return data;
  }
}
