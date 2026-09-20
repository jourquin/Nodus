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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import parsii.eval.Expression;
import parsii.eval.Parser;
import parsii.eval.Scope;
import parsii.eval.Variable;
import parsii.tokenizer.ParseException;

/**
 * Reuses parsed cost expressions within one CostParser. Instances are confined to its worker.
 *
 * <p>Expressions own stable variable bindings because the live scope removes and recreates
 * variables when switching between movement and node operations. Only referenced variables are
 * copied before evaluation. Their availability is checked even if Parsii simplified them away, so a
 * cached expression still reports unknown variables in the current scope.
 */
final class CostExpressionCache {

  private final Map<String, CompiledExpression> expressions = new HashMap<>();

  /** Evaluates a cached formula with the current scope's values and strict lookup rules. */
  double evaluate(String formula, Scope source) throws ParseException {
    CompiledExpression compiled = expressions.get(formula);
    if (compiled == null || !compiled.updateVariables(source)) {
      BindingScope bindings = new BindingScope(source);
      Expression expression = Parser.parse(formula, bindings);
      compiled =
          new CompiledExpression(expression, bindings.variables.values().toArray(new Variable[0]));
      expressions.put(formula, compiled);
    }
    return compiled.expression.evaluate();
  }

  /** Copies variables as they are resolved by Parsii, retaining its constant-folding behavior. */
  private static final class BindingScope extends Scope {
    private final Scope source;
    private final Map<String, Variable> variables = new LinkedHashMap<>();

    BindingScope(Scope source) {
      this.source = source;
    }

    @Override
    public Variable getVariable(String name) {
      Variable variable = variables.get(name);
      if (variable == null) {
        Variable original = source.getVariable(name);
        variable = create(name);
        if (original.isConstant()) {
          variable.makeConstant(original.getValue());
        } else {
          variable.setValue(original.getValue());
        }
        variables.put(name, variable);
      }
      return variable;
    }
  }

  private static final class CompiledExpression {
    private final Expression expression;
    private final Variable[] variables;

    CompiledExpression(Expression expression, Variable[] variables) {
      this.expression = expression;
      this.variables = variables;
    }

    /** Returns false when a fresh parse is needed to validate or simplify changed bindings. */
    boolean updateVariables(Scope source) {
      for (Variable variable : variables) {
        Variable current = source.find(variable.getName());
        if (current == null || current.isConstant() != variable.isConstant()) {
          return false;
        }
        if (variable.isConstant()) {
          if (Double.doubleToLongBits(current.getValue())
              != Double.doubleToLongBits(variable.getValue())) {
            return false;
          }
        } else {
          variable.setValue(current.getValue());
        }
      }
      return true;
    }
  }
}
