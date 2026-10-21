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

package edu.uclouvain.core.nodus.database.xls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.ExportFileFaults;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Real XLS/XLSX serialization, complete workbook closure and spill-file cleanup on late failures.
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_OUT")
@ResourceLock("SYSTEM_ERR")
@ResourceLock("POI_TEMP_FILES")
@Timeout(60)
class ExcelExportPreservationTest {
  private static final byte[] ORIGINAL =
      "previous export\r\nété 東京".getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> failedSerializationStreamsAndWorkbookClosurePreserveOutputAndPermitRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean xlsx : new boolean[] {false, true}) {
      for (boolean existing : new boolean[] {false, true}) {
        for (String phase :
            List.of(
                "open",
                "write",
                "flush",
                "close",
                "replace",
                "unsupported",
                "workbook write",
                "workbook close")) {
          tests.add(
              dynamicTest(
                  xlsx + "/existing=" + existing + "/" + phase,
                  () -> {
                    final Path folder = Files.createTempDirectory(directory, "excel-");
                    try (DatabaseFixture f = new DatabaseFixture(folder)) {
                      f.execute("CREATE TABLE data(id NUMERIC(6),label VARCHAR(40))");
                      try (PreparedStatement insert =
                          f.connection.prepareStatement("INSERT INTO data VALUES(?,?)")) {
                        for (int row = 1; row <= 300; row++) {
                          insert.setInt(1, row);
                          insert.setString(2, "été 東京 " + row);
                          insert.addBatch();
                        }
                        insert.executeBatch();
                      }
                      final Path target = folder.resolve("data." + (xlsx ? "xlsx" : "xls"));
                      if (existing) {
                        Files.write(target, ORIGINAL);
                      }
                      final Path spill = Files.createDirectory(folder.resolve("poi"));
                      TempFile.setTempFileCreationStrategy(
                          new DefaultTempFileCreationStrategy(spill.toFile()));
                      try {
                        final int[] closed = {0};
                        final Supplier<Workbook> books = () -> trackedWorkbook(xlsx, phase, closed);
                        final ExportFileFaults files = new ExportFileFaults(phase);
                        assertFalse(
                            ExportFileFaults.quiet(
                                () ->
                                    ExportXLS.exportTable(f.project, "data", xlsx, files, books)));
                        assertEquals(phase.equals("open") ? 0 : 1, closed[0]);
                        assertEquals(!phase.equals("open"), files.closed);
                        assertFalse(Files.exists(files.temporary));
                        if (!phase.equals("open")) {
                          assertTrue(files.bytes > 0, "Failure must follow actual output");
                        }
                        if (existing) {
                          assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
                        } else {
                          assertFalse(Files.exists(target));
                        }
                        assertEquals(
                            existing ? List.of(target) : List.of(),
                            ExportFileFaults.regularFiles(folder));
                        assertTrue(ExportXLS.exportTable(f.project, "data", xlsx));
                        try (Workbook workbook = WorkbookFactory.create(target.toFile())) {
                          final var sheet = workbook.getSheetAt(0);
                          assertEquals(301, sheet.getPhysicalNumberOfRows());
                          assertTrue(
                              sheet.getRow(0).getCell(0).getStringCellValue().contains(",N,"));
                          for (int row = 1; row <= 300; row++) {
                            assertEquals(row, sheet.getRow(row).getCell(0).getNumericCellValue());
                            assertEquals(
                                "été 東京 " + row, sheet.getRow(row).getCell(1).getStringCellValue());
                          }
                        }
                        assertEquals(List.of(target), ExportFileFaults.regularFiles(folder));
                        assertFalse(f.connection.isClosed());
                      } finally {
                        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy());
                      }
                    }
                  }));
        }
      }
    }
    return tests;
  }

  private static Workbook trackedWorkbook(boolean xlsx, String phase, int[] closed) {
    final Workbook real = xlsx ? new SXSSFWorkbook(null, 100, true, false) : new HSSFWorkbook();
    return (Workbook)
        Proxy.newProxyInstance(
            Workbook.class.getClassLoader(),
            new Class<?>[] {Workbook.class},
            (ignored, method, args) -> {
              final Object result;
              try {
                result = method.invoke(real, args);
              } catch (InvocationTargetException failure) {
                throw failure.getCause();
              }
              if (method.getName().equals("close")) {
                closed[0]++;
              }
              if (method.getName().equals("write") && phase.equals("workbook write")
                  || method.getName().equals("close") && phase.equals("workbook close")) {
                throw new IOException("Injected " + phase);
              }
              return result;
            });
  }
}
