/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.results;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adds a period predicate without confusing quoted text, nested queries or OR conditions. */
final class ResultQueries {
  private ResultQueries() {}

  static String forTime(String query, int time) throws SQLException {
    return restrict(query, " time = " + time);
  }

  static String forRange(String query, int start, int end) throws SQLException {
    return restrict(query, " time >= " + start + " AND time < " + end);
  }

  private static String restrict(String query, String predicate) throws SQLException {
    String sql = query.stripTrailing();
    if (sql.endsWith(";")) {
      sql = sql.substring(0, sql.length() - 1);
    }
    List<Integer> tokens = new ArrayList<>();
    int depth = 0;
    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);
      if (ch == '\'' || ch == '"' || ch == '`' || ch == '[') {
        char end = ch == '[' ? ']' : ch;
        while (++i < sql.length()) {
          if (sql.charAt(i) == end) {
            if (i + 1 < sql.length() && sql.charAt(i + 1) == end) {
              i++;
            } else {
              break;
            }
          }
        }
      } else if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        while (i + 1 < sql.length() && sql.charAt(i + 1) != '\n' && sql.charAt(i + 1) != '\r') {
          i++;
        }
      } else if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        int comments = 1;
        i += 2;
        while (i < sql.length() && comments > 0) {
          if (i + 1 < sql.length() && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
            comments++;
            i += 2;
          } else if (i + 1 < sql.length() && sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
            comments--;
            i += 2;
          } else {
            i++;
          }
        }
        if (comments != 0) {
          throw new SQLException("Unterminated SQL block comment");
        }
        i--;
      } else if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
      } else if (Character.isJavaIdentifierStart(ch)) {
        int start = i;
        while (i + 1 < sql.length() && Character.isJavaIdentifierPart(sql.charAt(i + 1))) {
          i++;
        }
        if (depth == 0) {
          tokens.add(start);
          tokens.add(i + 1);
        }
      }
    }
    int where = -1;
    int tail = sql.length();
    for (int i = 0; i < tokens.size(); i += 2) {
      String word = sql.substring(tokens.get(i), tokens.get(i + 1)).toUpperCase(Locale.ROOT);
      if (List.of("UNION", "INTERSECT", "EXCEPT").contains(word)) {
        throw new SQLException("Playback queries must contain a single SELECT");
      }
      if (word.equals("WHERE")) {
        where = tokens.get(i + 1);
      } else if (tail == sql.length()
          && List.of("GROUP", "ORDER", "HAVING", "LIMIT", "OFFSET", "FETCH").contains(word)) {
        tail = tokens.get(i);
      }
    }
    if (where >= 0) {
      return sql.substring(0, where)
          + predicate
          + " AND ("
          + sql.substring(where, tail)
          + "\n) "
          + sql.substring(tail);
    }
    return sql.substring(0, tail) + "\nWHERE" + predicate + " " + sql.substring(tail);
  }
}
