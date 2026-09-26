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

package com.bbn.openmap.dataAccess.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Schema rules run without constructing a window; validation messages are captured. */
class NodusMetaDbfTableModelTest {
  @TestFactory
  List<DynamicTest> mandatoryFieldsAreProtectedForBothLayerTypes() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "link fields" : "node fields",
              () -> {
                Model model = new Model(links);
                int mandatory = model.getRowCount() - 2;
                for (int row = 0; row < mandatory; row++) {
                  for (int col = 0; col < 4; col++) {
                    assertFalse(model.isCellEditable(row, col));
                  }
                }
                assertFalse(model.isCellEditable(mandatory, 0));
                assertFalse(model.isCellEditable(mandatory, 1));
                assertTrue(model.isCellEditable(mandatory, 2));
                assertTrue(model.isCellEditable(mandatory, 3));
                model.setWritable(false);
                assertFalse(model.isCellEditable(mandatory, 2));
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> duplicatesAreRejectedRegardlessOfCase() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String name : List.of("LABEL", "label")) {
      tests.add(
          dynamicTest(
              name,
              () -> {
                Model model = new Model(false);
                model.addBlankRecord();
                int row = model.getRowCount() - 1;
                final Object original = model.getValueAt(row, 0);
                model.setValueAt(name, row, 0);
                assertEquals(original, model.getValueAt(row, 0));
                assertEquals(1, model.errors.size());
                assertFalse(model.errors.get(0).isBlank());
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> supportedFieldTypesAcceptNamesAndDbfCodes() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Object type :
        List.of(
            "character",
            "numeric",
            "date",
            DbfTableModel.TYPE_CHARACTER,
            DbfTableModel.TYPE_NUMERIC,
            DbfTableModel.TYPE_DATE)) {
      tests.add(
          dynamicTest(
              type.toString(),
              () -> {
                Model model = new Model(false);
                model.addBlankRecord();
                int row = model.getRowCount() - 1;
                model.setValueAt(type, row, 1);
                assertTrue(model.errors.isEmpty());
                String expected =
                    type.equals("date") || type.equals(DbfTableModel.TYPE_DATE)
                        ? "date"
                        : type.equals("numeric") || type.equals(DbfTableModel.TYPE_NUMERIC)
                            ? "numeric"
                            : "character";
                assertEquals(expected, model.getValueAt(row, 1));
                if (expected.equals("date")) {
                  model.setValueAt(25, row, 2);
                  model.setValueAt(4, row, 3);
                  assertEquals(8, model.getValueAt(row, 2));
                  assertEquals(0, model.getValueAt(row, 3));
                }
              }));
    }
    return tests;
  }

  @Test
  void unsupportedTypesLeaveThePendingFieldUnchanged() {
    Model model = new Model(false);
    model.addBlankRecord();
    int row = model.getRowCount() - 1;
    final Object original = model.getValueAt(row, 1);
    for (Object type : List.of("logical", "memo", DbfTableModel.TYPE_LOGICAL)) {
      model.setValueAt(type, row, 1);
      assertEquals(original, model.getValueAt(row, 1));
    }
    assertEquals(3, model.errors.size());
  }

  @Test
  void returningToOriginalWidthUnlocksOtherFieldsWithoutChangingSourceSchema() {
    Model model = new Model(false);
    model.setValueAt(25, 3, 2);
    assertTrue(model.isCellEditable(3, 2));
    assertFalse(model.isCellEditable(4, 2));
    assertEquals(20, model.original.getLength(3));
    model.setValueAt(20, 3, 2);
    assertTrue(model.isCellEditable(4, 2));
    assertTrue(model.errors.isEmpty());
  }

  @Test
  void addedFieldsHaveUniqueNamesAndDoNotChangeTheSourceUntilCommitted() {
    Model model = new Model(false);
    final int originalColumns = model.original.getColumnCount();
    model.addBlankRecord();
    final String first = model.getValueAt(model.getRowCount() - 1, 0).toString();
    model.addBlankRecord();
    String second = model.getValueAt(model.getRowCount() - 1, 0).toString();
    assertFalse(first.equalsIgnoreCase(second));
    assertEquals(originalColumns, model.original.getColumnCount());
    assertEquals(originalColumns + 2, model.getRowCount());
    assertTrue(model.isCellEditable(model.getRowCount() - 1, 0));
    assertFalse(model.isCellEditable(model.getRowCount() - 2, 2));
  }

  private static DbfTableModel source(boolean links) {
    String[] mandatory = links ? NodusC.LINKS_MANDATORY_NAMES : NodusC.NODES_MANDATORY_NAMES;
    DbfTableModel source = new DbfTableModel(mandatory.length + 2);
    for (int col = 0; col < source.getColumnCount(); col++) {
      source.setColumnName(
          col,
          col < mandatory.length ? mandatory[col] : col == mandatory.length ? "LABEL" : "OTHER");
      source.setType(col, DbfTableModel.TYPE_CHARACTER);
      source.setLength(col, 20);
      source.setDecimalCount(col, (byte) 0);
    }
    return source;
  }

  private static final class Model extends NodusMetaDbfTableModel {
    private static final long serialVersionUID = 1L;
    final List<String> errors = new ArrayList<>();
    final DbfTableModel original;

    Model(boolean links) {
      this(links, source(links));
    }

    Model(boolean links, DbfTableModel original) {
      super(
          new NodusEsriLayer() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getType() {
              return links ? ShapeConstants.SHAPE_TYPE_POLYLINE : ShapeConstants.SHAPE_TYPE_POINT;
            }
          },
          original);
      this.original = original;
    }

    @Override
    protected void reportValidationError(String message) {
      errors.add(message);
    }
  }
}
