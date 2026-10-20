/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.compute.results.ResultsFixture.Links;
import edu.uclouvain.core.nodus.compute.results.ResultsFixture.Nodes;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class ResultCalculationTest {
  private static final String PATH_QUERY = "SELECT id,qty,grp,mode,means FROM results";
  @TempDir Path directory;

  @FunctionalInterface
  private interface Example {
    void run(ResultsFixture fixture, boolean links) throws Exception;
  }

  private Stream<DynamicTest> examples(String label, Example example) {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        links ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ", links=" + links + ": " + label,
                                () -> {
                                  try (ResultsFixture f = new ResultsFixture(directory, hsql)) {
                                    example.run(f, links);
                                    f.assertClean();
                                  }
                                })));
  }

  private boolean display(
      ResultsFixture f, boolean links, boolean relative, boolean export, String sql) {
    return links
        ? new Links(f, relative, export).displayVolumes(sql)
        : new Nodes(f, relative, export).readOD(sql);
  }

  @TestFactory
  Stream<DynamicTest> quantitiesAndVehiclesReachTheCorrectObjectsAcrossLayers() {
    return Stream.of("qty", "veh")
        .flatMap(
            column ->
                examples(
                    column,
                    (f, links) -> {
                      f.add(30, 12.345, 5, 1, 0);
                      f.add(30, 2, 2, 2, 0);
                      f.add(10, -40.125, -9, 1, 0);
                      f.add(20, 0.125, 1, 1, 0);
                      f.add(50, 5000, 5000, 1, 0);
                      f.add(999, 9000, 9000, 1, 0);
                      final boolean quantities = column.equals("qty");
                      assertTrue(
                          display(f, links, false, false, "SELECT id," + column + " FROM results"));
                      assertEquals(quantities ? 14.345 : 7, f.object(links, 30).getResult(), 1e-10);
                      assertEquals(
                          quantities ? -40.125 : -9, f.object(links, 10).getResult(), 1e-10);
                      assertEquals(quantities ? 0.125 : 1, f.object(links, 20).getResult(), 1e-10);
                      assertEquals(0, f.object(links, 40).getSize());
                      assertFalse(f.graphic(links, 40).isVisible());
                      assertEquals(Color.RED, f.graphic(links, 10).getLinePaint());
                      assertTrue(f.object(links, 10).getSize() < 0);
                      assertTrue(
                          Math.abs(f.object(links, 10).getSize()) > f.object(links, 30).getSize());
                      assertFalse((links ? f.links : f.nodes)[2].isDisplayResults());
                      assertTrue(
                          display(
                              f,
                              links,
                              false,
                              false,
                              "SELECT id," + column + " FROM results WHERE id=20"));
                      assertEquals(0, f.object(links, 30).getResult());
                      assertEquals(links ? 11 : 20, f.object(links, 20).getSize());
                      assertTrue(
                          display(
                              f,
                              links,
                              false,
                              false,
                              "SELECT id," + column + " FROM results WHERE 1=0"));
                      for (int id : new int[] {10, 20, 30, 40}) {
                        assertEquals(0, f.object(links, id).getResult());
                        assertEquals(0, f.object(links, id).getSize());
                        assertFalse(f.graphic(links, id).isVisible());
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> zeroAndEqualSignedValuesHaveFiniteBoundedSizesAndCanReappear() {
    return Stream.of(0, 5, -5)
        .flatMap(
            value ->
                examples(
                    "equal " + value,
                    (f, links) -> {
                      f.add(30, value, value, 1, 0);
                      f.add(10, value, value, 1, 0);
                      final Links lr = new Links(f, false, false);
                      final Nodes nr = new Nodes(f, false, false);
                      final String sql = "SELECT id,qty FROM results";
                      assertTrue(links ? lr.displayVolumes(sql) : nr.readOD(sql));
                      for (int id : new int[] {10, 30}) {
                        assertEquals(value.doubleValue(), f.object(links, id).getResult());
                        assertEquals(
                            value == 0 ? 0 : Math.copySign(links ? 11 : 20, value),
                            f.object(links, id).getSize());
                      }
                      f.execute("UPDATE results SET qty=0");
                      assertTrue(links ? lr.displayVolumes(sql) : nr.readOD(sql));
                      assertFalse(f.graphic(links, 30).isVisible());
                      f.execute("UPDATE results SET qty=-7 WHERE id=30");
                      assertTrue(links ? lr.displayVolumes(sql) : nr.readOD(sql));
                      assertTrue(f.graphic(links, 30).isVisible());
                      assertEquals(Color.RED, f.graphic(links, 30).getLinePaint());
                      assertEquals(links ? -11 : -20, f.object(links, 30).getSize());
                    }));
  }

  @TestFactory
  Stream<DynamicTest> scalingRespectsTheViewLayerVisibilityAndSqlFilters() {
    return examples(
        "relative scaling and filters",
        (f, links) -> {
          f.add(30, 20, 2, 1, 0);
          f.add(10, 10, 1, 1, 0);
          f.add(40, 1000, 100, 1, 0); // Outside the view.
          f.add(50, 5000, 500, 1, 0); // Hidden layer.
          assertTrue(display(f, links, false, false, "SELECT id,qty FROM results"));
          final float globalSize = f.object(links, 30).getSize();
          assertTrue(display(f, links, true, false, "SELECT id,qty FROM results"));
          assertEquals(links ? 11 : 20, f.object(links, 30).getSize());
          assertEquals(links ? 6 : 10, f.object(links, 10).getSize());
          assertTrue(globalSize < f.object(links, 30).getSize());
          final com.bbn.openmap.layer.shape.NodusEsriLayer layer = (links ? f.links : f.nodes)[0];
          layer.applyWhereFilter("NUM=10");
          assertTrue(display(f, links, true, false, "SELECT id,qty FROM results"));
          assertFalse(f.graphic(links, 30).isVisible());
          assertEquals(links ? 11 : 20, f.object(links, 10).getSize());
          layer.applyWhereFilter("");
          assertTrue(display(f, links, true, false, "SELECT id,qty FROM results"));
          assertTrue(f.graphic(links, 30).isVisible());
        });
  }

  @TestFactory
  Stream<DynamicTest> repeatedPathLinksAccumulateQuantitiesAndRoundedUpVehicleCounts() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(0, 1)
                    .map(
                        units ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + " path units=" + units,
                                () -> {
                                  try (ResultsFixture f = new ResultsFixture(directory, hsql)) {
                                    f.add(30, 10.125, 0, 1, 0);
                                    f.add(30, 5.25, 0, 2, 0);
                                    f.add(10, 40.5, 0, 1, 0);
                                    f.add(20, -0.625, 0, 1, 0);
                                    f.add(999, 9000, 0, 1, 0);
                                    final Links lr = new Links(f, false, false);
                                    lr.units = units;
                                    assertTrue(
                                        lr.displayPath(
                                            "SELECT id,qty,grp,mode,means FROM results"));
                                    assertEquals(
                                        units == 0 ? 15.375 : 4, f.object(true, 30).getResult());
                                    assertEquals(
                                        units == 0 ? 40.5 : 5, f.object(true, 10).getResult());
                                    assertEquals(
                                        units == 0 ? -0.625 : -1, f.object(true, 20).getResult());
                                    assertTrue(
                                        f.object(true, 30).getSize()
                                            < f.object(true, 10).getSize());
                                    assertEquals(
                                        11,
                                        ((BasicStroke) f.graphic(true, 10).getStroke())
                                            .getLineWidth());
                                    assertTrue(lr.displayPath(PATH_QUERY + " WHERE id=20"));
                                    assertEquals(0, f.object(true, 30).getResult());
                                    assertEquals(-11, f.object(true, 20).getSize());
                                    f.assertClean();
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> exportsRetainTwoDecimalsAndTheLayerRowOrder() {
    return examples(
        "DBF precision",
        (f, links) -> {
          f.add(30, 12.345, 2, 1, 0);
          f.add(10, -6.126, -1, 1, 0);
          f.add(20, 0.125, 1, 1, 0);
          assertTrue(display(f, links, false, true, "SELECT id,qty FROM results ORDER BY id"));
          assertEquals(12.345, f.object(links, 30).getResult());
          final String prefix = links ? "links_" : "nodes_";
          final Map<Integer, BigDecimal> exported = new LinkedHashMap<>();
          try (DBFReader reader =
              new DBFReader(directory.resolve(prefix + "a_results.dbf").toString())) {
            assertEquals(2, reader.getFieldCount());
            assertEquals(2, reader.getField(1).getDecimalCount());
            final List<Integer> ids = new ArrayList<>();
            while (reader.hasNextRecord()) {
              Object[] row = reader.nextRecord();
              int id = ((Number) row[0]).intValue();
              ids.add(id);
              exported.put(id, (BigDecimal) row[1]);
            }
            assertEquals(List.of(30, 10), ids);
          }
          assertEquals(0, new BigDecimal("12.35").compareTo(exported.get(30)));
          assertEquals(0, new BigDecimal("-6.13").compareTo(exported.get(10)));
          if (!links) {
            assertEquals(
                Math.abs(f.object(false, 30).getSize()),
                ((OMPoint) f.graphic(false, 30)).getRadius());
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> queryFailuresRetainThePreviousValuesAndReleaseBusyState() {
    return examples(
        "SQL and null failures",
        (f, links) -> {
          f.add(30, 5, 1, 1, 0);
          final Links lr = new Links(f, false, false);
          final Nodes nr = new Nodes(f, false, false);
          final String sql = "SELECT id,qty FROM results";
          assertTrue(links ? lr.displayVolumes(sql) : nr.readOD(sql));
          assertFalse(
              links
                  ? lr.displayVolumes("SELECT missing FROM results")
                  : nr.readOD("SELECT missing FROM results"));
          assertEquals(5, f.object(links, 30).getResult());
          f.execute("UPDATE results SET qty=NULL");
          assertFalse(links ? lr.displayVolumes(sql) : nr.readOD(sql));
          assertEquals(5, f.object(links, 30).getResult());
          assertEquals(2, (links ? lr.errors : nr.errors).size());
          assertFalse(f.panel.busy);
        });
  }
}
