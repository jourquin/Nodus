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

import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.Arrays;

/** Generates a large ODS package in a small heap using rows supplied on demand. */
public final class OdsStreamingProbe {
  static final int ROWS = 20000;
  static final int COLUMNS = 45;

  private OdsStreamingProbe() {}

  /**
   * Writes the test spreadsheet without retaining previously written rows.
   *
   * @param args the output ODS path
   * @throws Exception if the export fails
   */
  public static void main(String[] args) throws Exception {
    String[] headers = new String[COLUMNS];
    boolean[] numerical = new boolean[COLUMNS];
    Arrays.fill(numerical, true);
    for (int c = 0; c < COLUMNS; c++) {
      headers[c] = "C" + c + ",N,10,0";
    }
    int[] row = {0};
    try (ResultSet rows =
            (ResultSet)
                Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[] {ResultSet.class},
                    (proxy, method, values) -> {
                      switch (method.getName()) {
                        case "next":
                          return ++row[0] <= ROWS;
                        case "getObject":
                          return row[0] + (Integer) values[0];
                        case "close":
                          return null;
                        default:
                          throw new UnsupportedOperationException(method.getName());
                      }
                    });
        OutputStream output = Files.newOutputStream(Path.of(args[0]))) {
      OdsCodec.write("data", headers, numerical, rows, output);
    }
    System.out.println("Wrote " + ROWS + " rows, " + COLUMNS + " columns");
  }
}
