/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

package com.bbn.openmap.layer.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.omGraphics.OMGraphic;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** SQL changes must remain associated with the correct geometry and compatible DBF schema. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_ERR)
@Timeout(20)
class NodusEsriLayerDatabaseTest {
  @TempDir Path directory;

  @Test
  void sqlRowsAreMatchedByIdentifierAndDatesAndDecimalsArePreserved() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final OMGraphic original = project.graphic(30);
      project.execute("CREATE TABLE reordered AS SELECT * FROM features ORDER BY NUM");
      project.execute("DROP TABLE features");
      project.execute("ALTER TABLE reordered RENAME TO features");
      assertEquals(
          List.of(List.of("10"), List.of("20"), List.of("30")),
          project.rows("SELECT NUM FROM features"));
      project.execute(
          "UPDATE features SET LABEL='changed ' || NUM, AMOUNT=-NUM-0.375, "
              + "\"DAY\"=DATE '2024-02-29'");
      assertTrue(project.layer.updateDbfTableModel());
      DbfTableModel model = project.layer.getModel();
      for (int num : new int[] {30, 10, 20}) {
        int row = project.layer.getNumIndex(num);
        assertEquals("changed " + num, model.getValueAt(row, 3));
        assertEquals(-num - 0.375, ((Number) model.getValueAt(row, 4)).doubleValue(), 1e-12);
        assertEquals("20240229", model.getValueAt(row, 5));
      }
      assertSame(original, project.graphic(30));
      assertEquals(0, project.layer.getNumIndex(30));
      assertEquals(1, project.labelRefreshes);
      assertFalse(project.connection.isClosed());
      project.execute("UPDATE features SET AMOUNT=0 WHERE NUM=10");
      assertTrue(project.layer.updateDbfTableModel());
      assertEquals(0, ((Number) model.getValueAt(1, 4)).doubleValue());
      assertEquals(2, project.labelRefreshes);
    }
  }

  @TestFactory
  List<DynamicTest> incompatibleSqlChangesLeaveDbfUntouched() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String sql :
        List.of(
            "DELETE FROM features WHERE NUM=20",
            "ALTER TABLE features ADD extra INTEGER",
            "ALTER TABLE features ALTER COLUMN LABEL RENAME TO renamed",
            "ALTER TABLE features ALTER COLUMN LABEL VARCHAR(40)",
            "ALTER TABLE features ALTER COLUMN AMOUNT NUMERIC(12,4)",
            "UPDATE features SET AMOUNT=1234567.125 WHERE NUM=20")) {
      tests.add(
          dynamicTest(
              sql,
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, false)) {
                  final List<List<Object>> before = project.records();
                  project.execute(sql);
                  assertRejected(project);
                  assertEquals(before, project.records());
                  assertEquals(0, project.labelRefreshes);
                  assertEquals(2, project.layer.getNumIndex(20));
                  assertFalse(project.connection.isClosed());
                }
              }));
    }
    return tests;
  }

  @Test
  void unknownIdentifierRejectsTheWholeRefreshWithoutPartialChanges() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final List<List<Object>> before = project.records();
      project.execute("UPDATE features SET LABEL='new label'");
      project.execute("UPDATE features SET NUM=99 WHERE NUM=20");
      assertRejected(project);
      assertEquals(before, project.records());
      assertEquals(-1, project.layer.getNumIndex(99));
      assertEquals(0, project.labelRefreshes);
    }
  }

  @Test
  void duplicateIdentifiersRejectTheRefreshWithoutLosingAnOriginalRecord() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final List<List<Object>> before = project.records();
      project.execute("UPDATE features SET NUM=30 WHERE NUM=20");
      assertRejected(project);
      assertEquals(before, project.records());
      assertEquals(2, project.layer.getNumIndex(20));
    }
  }

  private static void assertRejected(LayerTestProject project) {
    final PrintStream original = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(captured);
      assertFalse(project.layer.updateDbfTableModel());
    } finally {
      System.setErr(original);
    }
    assertFalse(bytes.toString(StandardCharsets.UTF_8).isBlank(), "Rejected data must be reported");
  }
}
