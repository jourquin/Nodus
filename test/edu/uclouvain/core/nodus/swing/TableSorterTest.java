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

package edu.uclouvain.core.nodus.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

/** Observable model/view behavior on the EDT, with actual JTable headers and no windows. */
@Timeout(10)
class TableSorterTest {
  private static final Class<?>[] TYPES = {String.class, Integer.class, String.class};

  @TestFactory
  Stream<DynamicTest> readsEditsAndEditabilityFollowTheViewToModelMapping() {
    return Stream.of(TableSorter.NOT_SORTED, TableSorter.ASCENDING, TableSorter.DESCENDING)
        .map(
            direction ->
                dynamicTest(
                    "direction=" + direction,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model = sample();
                              TableSorter sorter = new TableSorter(model);
                              sorter.setSortingStatus(1, direction);
                              List<String> expected =
                                  direction == TableSorter.ASCENDING
                                      ? List.of("b", "c", "a")
                                      : direction == TableSorter.DESCENDING
                                          ? List.of("a", "c", "b")
                                          : List.of("a", "b", "c");
                              assertIds(sorter, expected);
                              assertIds(model, List.of("a", "b", "c"));
                              assertEquals(3, sorter.getRowCount());
                              assertEquals(3, sorter.getColumnCount());
                              assertSame(model, sorter.getTableModel());
                              for (int column = 0; column < 3; column++) {
                                assertEquals("Column " + column, sorter.getColumnName(column));
                                assertEquals(TYPES[column], sorter.getColumnClass(column));
                              }
                              for (int view = 0; view < sorter.getRowCount(); view++) {
                                String id = (String) sorter.getValueAt(view, 0);
                                assertEquals(id.equals("a"), sorter.isCellEditable(view, 2));
                                sorter.setValueAt("edited " + id, view, 2);
                                int row = model.index(id);
                                assertEquals(row, model.lastEditedRow);
                                assertEquals(2, model.lastEditedColumn);
                                assertEquals("edited " + id, model.getValueAt(row, 2));
                              }
                              assertIds(sorter, expected);
                            })));
  }

  @TestFactory
  Stream<DynamicTest> nullsAndTiesHaveStableOrderForComparableAndLexicalValues() {
    return Stream.of(String.class, Integer.class, Token.class)
        .flatMap(
            type ->
                Stream.of(TableSorter.ASCENDING, TableSorter.DESCENDING)
                    .map(
                        direction ->
                            dynamicTest(
                                type.getSimpleName() + ", direction=" + direction,
                                () ->
                                    onEdt(
                                        () -> {
                                          Object two =
                                              type == Integer.class
                                                  ? 2
                                                  : type == String.class ? "2" : new Token("2");
                                          Object ten =
                                              type == Integer.class
                                                  ? 10
                                                  : type == String.class ? "10" : new Token("10");
                                          MutableModel model =
                                              new MutableModel(
                                                  new Class<?>[] {String.class, type},
                                                  new Object[][] {
                                                    {"two", two},
                                                    {"nil1", null},
                                                    {"ten", ten},
                                                    {"two again", two},
                                                    {"nil2", null}
                                                  });
                                          TableSorter sorter = new TableSorter(model);
                                          sorter.setSortingStatus(1, direction);
                                          boolean numeric = type == Integer.class;
                                          List<String> expected =
                                              direction == TableSorter.ASCENDING
                                                  ? numeric
                                                      ? List.of(
                                                          "nil1", "nil2", "two", "two again", "ten")
                                                      : List.of(
                                                          "nil1", "nil2", "ten", "two", "two again")
                                                  : numeric
                                                      ? List.of(
                                                          "ten", "two", "two again", "nil1", "nil2")
                                                      : List.of(
                                                          "two",
                                                          "two again",
                                                          "ten",
                                                          "nil1",
                                                          "nil2");
                                          assertIds(sorter, expected);
                                          assertIds(
                                              model,
                                              List.of("two", "nil1", "ten", "two again", "nil2"));
                                        }))));
  }

  @TestFactory
  Stream<DynamicTest> emptyAndAllNullModelsCanBeSortedAndRepopulated() {
    return Stream.of(false, true)
        .flatMap(
            empty ->
                Stream.of(1, -1)
                    .map(
                        direction ->
                            dynamicTest(
                                "empty=" + empty + ", direction=" + direction,
                                () ->
                                    onEdt(
                                        () -> {
                                          MutableModel model =
                                              new MutableModel(
                                                  TYPES,
                                                  empty
                                                      ? new Object[0][]
                                                      : new Object[][] {
                                                        {"a", null, null}, {"b", null, null}
                                                      });
                                          TableSorter sorter = new TableSorter(model);
                                          sorter.setSortingStatus(1, direction);
                                          assertIds(sorter, empty ? List.of() : List.of("a", "b"));
                                          model.insert(0, new Object[] {"new", 2, null});
                                          assertIds(
                                              sorter,
                                              empty
                                                  ? List.of("new")
                                                  : direction == 1
                                                      ? List.of("a", "b", "new")
                                                      : List.of("new", "a", "b"));
                                        }))));
  }

  @Test
  void compoundSortingUsesDirectiveOrderAndRemainsStableOnEqualKeys() throws Exception {
    onEdt(
        () -> {
          MutableModel model =
              new MutableModel(
                  new Class<?>[] {String.class, String.class, Integer.class},
                  new Object[][] {
                    {"b", "B", 2},
                    {"a3", "A", 3},
                    {"a1", "A", 1},
                    {"a3 again", "A", 3},
                    {"nil2", null, 2},
                    {"nil", null, null}
                  });
          TableSorter sorter = new TableSorter(model);
          sorter.setSortingStatus(1, TableSorter.ASCENDING);
          sorter.setSortingStatus(2, TableSorter.DESCENDING);
          assertIds(sorter, List.of("nil2", "nil", "a3", "a3 again", "a1", "b"));
          sorter.setSortingStatus(2, TableSorter.NOT_SORTED);
          assertIds(sorter, List.of("nil2", "nil", "a3", "a1", "a3 again", "b"));
          sorter.setSortingStatus(1, TableSorter.NOT_SORTED);
          assertIds(sorter, List.of("b", "a3", "a1", "a3 again", "nil2", "nil"));
        });
  }

  @Test
  void replacingAndRemovingAComparatorInvalidatesPreviouslySortedRows() throws Exception {
    onEdt(
        () -> {
          MutableModel model =
              new MutableModel(
                  new Class<?>[] {String.class, String.class},
                  new Object[][] {{"long", "bbbb"}, {"short", "z"}, {"nil", null}});
          TableSorter sorter = new TableSorter(model);
          sorter.setSortingStatus(1, TableSorter.ASCENDING);
          assertIds(sorter, List.of("nil", "long", "short"));
          final List<TableModelEvent> events = events(sorter);
          sorter.setColumnComparator(
              String.class,
              (Comparator<String>)
                  (a, b) -> {
                    assertTrue(
                        a != null && b != null,
                        "The sorter must handle nulls before the comparator");
                    return Integer.compare(a.length(), b.length());
                  });
          assertIds(sorter, List.of("nil", "short", "long"));
          assertRefresh(events);
          events.clear();
          sorter.setColumnComparator(String.class, Comparator.reverseOrder());
          assertIds(sorter, List.of("nil", "short", "long"));
          assertRefresh(events);
          events.clear();
          sorter.setColumnComparator(String.class, null);
          assertIds(sorter, List.of("nil", "long", "short"));
          assertRefresh(events);
        });
  }

  @TestFactory
  Stream<DynamicTest> descendingOrderAcceptsTheFullRangeOfComparatorResults() {
    return Stream.of(1, -1)
        .map(
            direction ->
                dynamicTest(
                    "direction=" + direction,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model = sample();
                              TableSorter sorter = new TableSorter(model);
                              sorter.setColumnComparator(
                                  Integer.class,
                                  (Comparator<Integer>)
                                      (a, b) ->
                                          a.equals(b)
                                              ? 0
                                              : a < b ? Integer.MIN_VALUE : Integer.MAX_VALUE);
                              sorter.setSortingStatus(1, direction);
                              assertIds(
                                  sorter,
                                  direction == 1 ? List.of("b", "c", "a") : List.of("a", "c", "b"));
                            })));
  }

  @TestFactory
  Stream<DynamicTest> nonSortingCellUpdatesMapRowsWithoutForcingAnotherSort() {
    return Stream.of(false, true)
        .map(
            materialized ->
                dynamicTest(
                    "mapping read=" + materialized,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model = sample();
                              TableSorter sorter = new TableSorter(model);
                              AtomicInteger comparisons = new AtomicInteger();
                              sorter.setColumnComparator(
                                  Integer.class,
                                  (Comparator<Integer>)
                                      (a, b) -> {
                                        comparisons.incrementAndGet();
                                        return Integer.compare(a, b);
                                      });
                              sorter.setSortingStatus(1, TableSorter.ASCENDING);
                              if (materialized) {
                                assertIds(sorter, List.of("b", "c", "a"));
                              }
                              final List<TableModelEvent> events = events(sorter);
                              int before = comparisons.get();
                              model.setValueAt("changed", 1, 2);
                              assertEquals(before, comparisons.get());
                              if (materialized) {
                                assertCell(events, sorter, 0, 2);
                              } else {
                                assertRefresh(events);
                              }
                              assertIds(sorter, List.of("b", "c", "a"));
                              events.clear();
                              before = comparisons.get();
                              model.setValueAt("changed again", 0, 2);
                              assertCell(events, sorter, 2, 2);
                              assertEquals(before, comparisons.get());
                            })));
  }

  @TestFactory
  Stream<DynamicTest> editsToTheSortKeyInvalidateBothRowMappingsIncludingNullChanges() {
    return Stream.of(1, -1)
        .flatMap(
            direction ->
                Stream.of(false, true)
                    .map(
                        useNull ->
                            dynamicTest(
                                "direction=" + direction + ", null=" + useNull,
                                () ->
                                    onEdt(
                                        () -> {
                                          MutableModel model = sample();
                                          TableSorter sorter = new TableSorter(model);
                                          sorter.setSortingStatus(1, direction);
                                          assertIds(
                                              sorter,
                                              direction == 1
                                                  ? List.of("b", "c", "a")
                                                  : List.of("a", "c", "b"));
                                          // Also materialize the reverse lookup before changing the
                                          // sort key.
                                          model.setValueAt("note", 0, 2);
                                          final List<TableModelEvent> events = events(sorter);
                                          model.setValueAt(useNull ? null : 4, 1, 1);
                                          assertRefresh(events);
                                          assertIds(
                                              sorter,
                                              direction == 1
                                                  ? useNull
                                                      ? List.of("b", "c", "a")
                                                      : List.of("c", "a", "b")
                                                  : useNull
                                                      ? List.of("a", "c", "b")
                                                      : List.of("b", "a", "c"));
                                          events.clear();
                                          model.setValueAt("after", 1, 2);
                                          assertCell(
                                              events,
                                              sorter,
                                              useNull
                                                  ? direction == 1 ? 0 : 2
                                                  : direction == 1 ? 2 : 0,
                                              2);
                                        }))));
  }

  @Test
  void editingASortKeyThroughTheViewChangesTheOriginalRowAndResorts() throws Exception {
    onEdt(
        () -> {
          MutableModel model = sample();
          TableSorter sorter = new TableSorter(model);
          sorter.setSortingStatus(1, TableSorter.ASCENDING);
          assertIds(sorter, List.of("b", "c", "a"));
          sorter.setValueAt(4, 0, 1);
          assertEquals(1, model.lastEditedRow);
          assertEquals(4, model.getValueAt(1, 1));
          assertIds(sorter, List.of("c", "a", "b"));
          sorter.setValueAt(null, 2, 1);
          assertEquals(1, model.lastEditedRow);
          assertIds(sorter, List.of("b", "c", "a"));
        });
  }

  @TestFactory
  Stream<DynamicTest> modelRowChangesInvalidateCachedIndexes() {
    return Stream.of(
            "insert",
            "delete",
            "insert column",
            "delete column",
            "rows updated",
            "row updated",
            "cells updated",
            "data changed",
            "delete all")
        .map(
            kind ->
                dynamicTest(
                    kind,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model = sample();
                              TableSorter sorter = new TableSorter(model);
                              sorter.setSortingStatus(1, TableSorter.ASCENDING);
                              assertIds(sorter, List.of("b", "c", "a"));
                              model.setValueAt("prime inverse", 0, 2);
                              final List<TableModelEvent> events = events(sorter);
                              List<String> expected;
                              if (kind.startsWith("insert")) {
                                model.rows.add(0, new Object[] {"new", 0, "new note"});
                                model.emit(
                                    0,
                                    0,
                                    kind.endsWith("column") ? 2 : TableModelEvent.ALL_COLUMNS,
                                    TableModelEvent.INSERT);
                                expected = List.of("new", "b", "c", "a");
                              } else if (kind.equals("delete all")) {
                                model.rows.clear();
                                model.emit(
                                    0, 2, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
                                expected = List.of();
                              } else if (kind.startsWith("delete")) {
                                model.rows.remove(2);
                                model.emit(
                                    2,
                                    2,
                                    kind.endsWith("column") ? 2 : TableModelEvent.ALL_COLUMNS,
                                    TableModelEvent.DELETE);
                                expected = List.of("b", "a");
                              } else if (kind.equals("cells updated")) {
                                model.rows.get(0)[2] = "changed a";
                                model.rows.get(1)[2] = "changed b";
                                model.emit(0, 1, 2, TableModelEvent.UPDATE);
                                expected = List.of("b", "c", "a");
                              } else if (kind.equals("row updated")) {
                                model.rows.get(0)[1] = 0;
                                model.emit(
                                    0, 0, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE);
                                expected = List.of("a", "b", "c");
                              } else {
                                model.rows.get(0)[1] = 0;
                                model.rows.get(1)[1] = 5;
                                if (kind.equals("rows updated")) {
                                  model.emit(
                                      0, 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE);
                                } else {
                                  model.fireTableDataChanged();
                                }
                                expected = List.of("a", "c", "b");
                              }
                              assertRefresh(events);
                              assertIds(sorter, expected);
                              assertEquals(model.getRowCount(), sorter.getRowCount());
                            })));
  }

  @Test
  void unsortedEventsKeepTheirOriginalRowRangeColumnAndType() throws Exception {
    onEdt(
        () -> {
          MutableModel model = sample();
          TableSorter sorter = new TableSorter(model);
          assertIds(sorter, List.of("a", "b", "c"));
          final List<TableModelEvent> events = events(sorter);
          model.setValueAt("note", 2, 2);
          assertEvent(events.get(0), 2, 2, 2, TableModelEvent.UPDATE);
          events.clear();
          model.insert(1, new Object[] {"new", 4, "n"});
          assertEvent(events.get(0), 1, 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT);
          assertIds(sorter, List.of("a", "new", "b", "c"));
          events.clear();
          model.rows.remove(3);
          model.emit(3, 3, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
          assertEvent(events.get(0), 3, 3, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
          assertIds(sorter, List.of("a", "new", "b"));
        });
  }

  @TestFactory
  Stream<DynamicTest> structureAndLegacyNullEventsCancelObsoleteSortColumns() {
    return Stream.of("structure", "null")
        .map(
            kind ->
                dynamicTest(
                    kind,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model = sample();
                              TableSorter sorter = new TableSorter(model);
                              sorter.setSortingStatus(2, TableSorter.ASCENDING);
                              assertIds(sorter, List.of("b", "c", "a"));
                              if (kind.equals("structure")) {
                                model.reset(
                                    new Class<?>[] {String.class}, new Object[][] {{"z"}, {"a"}});
                                assertEquals(1, sorter.getColumnCount());
                                assertIds(sorter, List.of("z", "a"));
                              } else {
                                model.fireTableChanged(null);
                                assertIds(sorter, List.of("a", "b", "c"));
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> replacingAModelClearsSortColumnsAndDetachesThePreviousListener() {
    return Stream.of(false, true)
        .map(
            detachFirst ->
                dynamicTest(
                    "detach first=" + detachFirst,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel old = sample();
                              TableSorter sorter = new TableSorter(old);
                              sorter.setSortingStatus(2, TableSorter.ASCENDING);
                              assertIds(sorter, List.of("b", "c", "a"));
                              final List<TableModelEvent> events = events(sorter);
                              if (detachFirst) {
                                sorter.setTableModel(null);
                                assertEquals(0, sorter.getRowCount());
                                assertEquals(0, sorter.getColumnCount());
                              }
                              MutableModel replacement =
                                  new MutableModel(
                                      new Class<?>[] {String.class}, new Object[][] {{"z"}, {"a"}});
                              sorter.setTableModel(replacement);
                              assertIds(sorter, List.of("z", "a"));
                              assertEquals(0, old.getTableModelListeners().length);
                              assertEquals(1, replacement.getTableModelListeners().length);
                              events.clear();
                              old.setValueAt("irrelevant", 0, 2);
                              assertTrue(events.isEmpty());
                              replacement.setValueAt("changed", 0, 0);
                              assertEquals("changed", sorter.getValueAt(0, 0));
                              assertEquals(1, events.size());
                              sorter.setTableModel(replacement);
                              assertEquals(1, replacement.getTableModelListeners().length);
                            })));
  }

  @Test
  void headerClicksUseModelColumnsAfterReorderingAndHonorShiftAndControl() throws Exception {
    onEdt(
        () -> {
          MutableModel model = sample();
          TableSorter sorter = new TableSorter(model);
          JTable table = new JTable(sorter);
          sorter.setTableHeader(table.getTableHeader());
          table.moveColumn(1, 0);
          click(table.getTableHeader(), 10, 0);
          assertIds(sorter, List.of("b", "c", "a"));
          click(table.getTableHeader(), 10, 0);
          assertIds(sorter, List.of("a", "c", "b"));
          click(table.getTableHeader(), 10, 0);
          assertIds(sorter, List.of("a", "b", "c"));
          click(table.getTableHeader(), 10, InputEvent.SHIFT_DOWN_MASK);
          assertIds(sorter, List.of("a", "c", "b"));
          // Add ID ascending as the secondary key without cancelling the descending numeric key.
          click(table.getTableHeader(), 85, InputEvent.CTRL_DOWN_MASK);
          assertIds(sorter, List.of("a", "c", "b"));
          // An ordinary click discards the old primary key and advances ID to descending.
          click(table.getTableHeader(), 85, 0);
          assertIds(sorter, List.of("c", "b", "a"));
        });
  }

  @TestFactory
  Stream<DynamicTest> headerClicksOutsideAnyColumnAreIgnored() {
    return Stream.of("negative", "past end", "empty")
        .map(
            kind ->
                dynamicTest(
                    kind,
                    () ->
                        onEdt(
                            () -> {
                              MutableModel model =
                                  kind.equals("empty")
                                      ? new MutableModel(new Class<?>[0], new Object[0][])
                                      : sample();
                              TableSorter sorter = new TableSorter(model);
                              JTable table = new JTable(sorter);
                              sorter.setTableHeader(table.getTableHeader());
                              final List<TableModelEvent> events = events(sorter);
                              click(
                                  table.getTableHeader(), kind.equals("negative") ? -1 : 10_000, 0);
                              assertTrue(events.isEmpty());
                              assertEquals(model.getRowCount(), sorter.getRowCount());
                            })));
  }

  @Test
  void headerReplacementAndDetachmentRestoreRenderersAndRemoveTheOldControl() throws Exception {
    onEdt(
        () -> {
          TableSorter sorter = new TableSorter(sample());
          JTable first = new JTable(sorter);
          JTable second = new JTable(sorter);
          JTableHeader old = first.getTableHeader();
          JTableHeader current = second.getTableHeader();
          final TableCellRenderer oldRenderer = old.getDefaultRenderer();
          final TableCellRenderer currentRenderer = current.getDefaultRenderer();
          int oldListeners = old.getMouseListeners().length;
          final int newListeners = current.getMouseListeners().length;
          sorter.setTableHeader(old);
          sorter.setTableHeader(old);
          assertEquals(oldListeners + 1, old.getMouseListeners().length);
          sorter.setTableHeader(current);
          assertSame(oldRenderer, old.getDefaultRenderer());
          assertEquals(oldListeners, old.getMouseListeners().length);
          assertEquals(newListeners + 1, current.getMouseListeners().length);
          click(old, 85, 0);
          assertIds(sorter, List.of("a", "b", "c"));
          click(current, 85, 0);
          assertIds(sorter, List.of("b", "c", "a"));
          JLabel label =
              (JLabel)
                  current
                      .getDefaultRenderer()
                      .getTableCellRendererComponent(second, "key", false, false, -1, 1);
          assertTrue(label.getIcon() != null);
          sorter.setTableHeader(null);
          assertSame(currentRenderer, current.getDefaultRenderer());
          assertEquals(newListeners, current.getMouseListeners().length);
          click(current, 85, 0);
          assertIds(sorter, List.of("b", "c", "a"));
        });
  }

  private static void onEdt(Runnable action) throws Exception {
    try {
      SwingUtilities.invokeAndWait(action);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw (Exception) cause;
    }
  }

  private static MutableModel sample() {
    return new MutableModel(TYPES, new Object[][] {{"a", 3, "z"}, {"b", 1, "a"}, {"c", 2, "m"}});
  }

  private static void assertIds(javax.swing.table.TableModel model, List<String> expected) {
    List<String> actual = new ArrayList<>();
    for (int row = 0; row < model.getRowCount(); row++) {
      actual.add((String) model.getValueAt(row, 0));
    }
    assertEquals(expected, actual);
  }

  private static List<TableModelEvent> events(TableSorter sorter) {
    List<TableModelEvent> result = new ArrayList<>();
    sorter.addTableModelListener(result::add);
    return result;
  }

  private static void assertRefresh(List<TableModelEvent> events) {
    assertEquals(1, events.size());
    assertEvent(
        events.get(0), 0, Integer.MAX_VALUE, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE);
  }

  private static void assertCell(
      List<TableModelEvent> events, TableSorter sorter, int row, int column) {
    assertEquals(1, events.size());
    assertSame(sorter, events.get(0).getSource());
    assertEvent(events.get(0), row, row, column, TableModelEvent.UPDATE);
  }

  private static void assertEvent(
      TableModelEvent event, int first, int last, int column, int type) {
    assertEquals(first, event.getFirstRow());
    assertEquals(last, event.getLastRow());
    assertEquals(column, event.getColumn());
    assertEquals(type, event.getType());
  }

  private static void click(JTableHeader header, int x, int modifiers) {
    header.dispatchEvent(
        new MouseEvent(
            header, MouseEvent.MOUSE_CLICKED, 1, modifiers, x, 5, 1, false, MouseEvent.BUTTON1));
  }

  /** Not Comparable: the documented fallback must compare its rendered string. */
  private static final class Token {
    private final String value;

    Token(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  private static final class MutableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private Class<?>[] types;
    final List<Object[]> rows = new ArrayList<>();
    int lastEditedRow = -1;
    int lastEditedColumn = -1;

    MutableModel(Class<?>[] types, Object[][] data) {
      this.types = types.clone();
      rows.addAll(Arrays.stream(data).map(Object[]::clone).collect(Collectors.toList()));
    }

    int index(String id) {
      for (int row = 0; row < rows.size(); row++) {
        if (rows.get(row)[0].equals(id)) {
          return row;
        }
      }
      throw new AssertionError("Missing row " + id);
    }

    void insert(int index, Object[] row) {
      rows.add(index, row);
      fireTableRowsInserted(index, index);
    }

    void reset(Class<?>[] types, Object[][] data) {
      this.types = types.clone();
      rows.clear();
      rows.addAll(Arrays.stream(data).map(Object[]::clone).collect(Collectors.toList()));
      fireTableStructureChanged();
    }

    void emit(int first, int last, int column, int type) {
      fireTableChanged(new TableModelEvent(this, first, last, column, type));
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return types.length;
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return types[column];
    }

    @Override
    public String getColumnName(int column) {
      return "Column " + column;
    }

    @Override
    public Object getValueAt(int row, int column) {
      return rows.get(row)[column];
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return rows.get(row)[0].equals("a") && column == 2;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      lastEditedRow = row;
      lastEditedColumn = column;
      rows.get(row)[column] = value;
      fireTableCellUpdated(row, column);
    }
  }
}
