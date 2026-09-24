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

package edu.uclouvain.core.nodus.compute.costs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import parsii.eval.Scope;
import parsii.tokenizer.ParseException;

/** Regression tests for changing variable bindings between cost evaluations. */
class CostExpressionCacheTest {
  private final CostExpressionCache cache = new CostExpressionCache();
  private final Scope scope = new Scope().withStrictLookup(true);

  @Test
  void evaluatesAFormulaWithCurrentVariableValues() throws ParseException {
    scope.create("distance").setValue(12);
    scope.create("rate").setValue(2.5);
    scope.create("handling").setValue(7);
    assertEquals(37, cache.evaluate("distance * rate + handling", scope), 1e-12);
    scope.getVariable("distance").setValue(20);
    scope.getVariable("rate").setValue(3);
    assertEquals(67, cache.evaluate("distance * rate + handling", scope), 1e-12);
  }

  @Test
  void readsARecreatedVariableInsteadOfTheOldBinding() throws ParseException {
    scope.create("cost").setValue(4);
    assertEquals(8, cache.evaluate("2 * cost", scope), 1e-12);
    scope.remove("cost");
    scope.create("cost").setValue(9);
    assertEquals(18, cache.evaluate("2 * cost", scope), 1e-12);
  }

  @Test
  void rejectsAMissingVariableOnTheFirstEvaluation() {
    assertThrows(ParseException.class, () -> cache.evaluate("missing + 1", scope));
  }

  @Test
  void rejectsARemovedVariableEvenWhenItWasSimplifiedAway() throws ParseException {
    scope.create("cost").setValue(5);
    assertEquals(0, cache.evaluate("0 * cost", scope), 1e-12);
    scope.remove("cost");
    assertThrows(ParseException.class, () -> cache.evaluate("0 * cost", scope));
    scope.create("cost").setValue(9);
    assertEquals(0, cache.evaluate("0 * cost", scope), 1e-12);
  }

  @Test
  void rejectsARemovedLiveVariableAndRecoversWhenItReturns() throws ParseException {
    scope.create("cost").setValue(5);
    assertEquals(6, cache.evaluate("cost + 1", scope), 1e-12);
    scope.remove("cost");
    assertThrows(ParseException.class, () -> cache.evaluate("cost + 1", scope));
    scope.create("cost").setValue(12);
    assertEquals(13, cache.evaluate("cost + 1", scope), 1e-12);
  }

  @Test
  void reparsesWhenAConstantChangesValue() throws ParseException {
    scope.create("rate").makeConstant(2);
    assertEquals(6, cache.evaluate("3 * rate", scope), 1e-12);
    scope.remove("rate");
    scope.create("rate").makeConstant(5);
    assertEquals(15, cache.evaluate("3 * rate", scope), 1e-12);
  }

  @Test
  void handlesTransitionsBetweenConstantAndMutableVariables() throws ParseException {
    scope.create("rate").setValue(2);
    assertEquals(6, cache.evaluate("3 * rate", scope), 1e-12);
    scope.getVariable("rate").makeConstant(4);
    assertEquals(12, cache.evaluate("3 * rate", scope), 1e-12);
    scope.remove("rate");
    scope.create("rate").setValue(7);
    assertEquals(21, cache.evaluate("3 * rate", scope), 1e-12);
    scope.getVariable("rate").setValue(8);
    assertEquals(24, cache.evaluate("3 * rate", scope), 1e-12);
  }

  @Test
  void usesTheCurrentScopeWhenTheSameFormulaIsReused() throws ParseException {
    scope.create("cost").setValue(2);
    Scope other = new Scope().withStrictLookup(true);
    other.create("cost").setValue(10);
    assertEquals(3, cache.evaluate("cost + 1", scope), 1e-12);
    assertEquals(11, cache.evaluate("cost + 1", other), 1e-12);
    assertEquals(3, cache.evaluate("cost + 1", scope), 1e-12);
  }

  @Test
  void keepsDifferentFormulasIndependent() throws ParseException {
    scope.create("cost").setValue(2);
    assertEquals(3, cache.evaluate("cost + 1", scope), 1e-12);
    assertEquals(6, cache.evaluate("cost * 3", scope), 1e-12);
    scope.getVariable("cost").setValue(4);
    assertEquals(5, cache.evaluate("cost + 1", scope), 1e-12);
    assertEquals(12, cache.evaluate("cost * 3", scope), 1e-12);
  }

  @Test
  void reportsMalformedFormulas() {
    assertThrows(ParseException.class, () -> cache.evaluate("2 * (3 +", scope));
  }
}
