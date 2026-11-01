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

package edu.uclouvain.core.nodus.database;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Bounded JDBC batching with source positions retained for honest failure diagnostics. */
public final class ImportBatch {
  private final PreparedStatement statement;
  private final boolean batching;
  private final int limit;
  private final String position;
  private final ImportProgress progress;
  private final List<Long> rows = new ArrayList<>();

  /**
   * Uses the driver's batch support and the project's configured batch size.
   *
   * @param connection borrowed loader connection, including any driver wrappers
   * @param statement borrowed insertion statement, owned by the loader
   * @param limit maximum records per JDBC batch
   * @param position source-position label, such as CSV record
   * @param progress import progress and cancellation control
   * @throws SQLException if the driver cannot report batch support
   */
  public ImportBatch(
      Connection connection,
      PreparedStatement statement,
      int limit,
      String position,
      ImportProgress progress)
      throws SQLException {
    this.statement = statement;
    batching = connection.getMetaData().supportsBatchUpdates();
    this.limit = Math.max(1, limit);
    this.position = position;
    this.progress = progress;
  }

  /**
   * Submits the bound parameters, retaining the original source row or record number.
   *
   * @param row original one-based source position
   * @throws SQLException if cancelled or insertion fails
   */
  public void add(long row) throws SQLException {
    progress.checkCancelled();
    if (batching) {
      try {
        statement.addBatch();
      } catch (SQLException error) {
        throw located(error, row, row);
      }
      rows.add(row);
      if (rows.size() >= limit) {
        flush();
      }
    } else {
      try {
        statement.executeUpdate();
      } catch (SQLException error) {
        throw located(error, row, row);
      }
    }
    progress.loaded();
  }

  /**
   * Flushes queued records without committing the transaction.
   *
   * @throws SQLException if cancelled or the batch fails
   */
  public void flush() throws SQLException {
    progress.checkCancelled();
    if (rows.isEmpty()) {
      return;
    }
    try {
      int[] counts = statement.executeBatch();
      if (counts != null) {
        for (int count : counts) {
          if (count == Statement.EXECUTE_FAILED) {
            throw new BatchUpdateException("Driver reported failed batch records", counts);
          }
        }
      }
      statement.clearBatch();
      rows.clear();
    } catch (SQLException error) {
      long first = rows.get(0);
      long last = rows.get(rows.size() - 1);
      if (error instanceof BatchUpdateException) {
        long[] counts = ((BatchUpdateException) error).getLargeUpdateCounts();
        if (counts != null && counts.length <= rows.size()) {
          int firstFailed = -1;
          int lastFailed = -1;
          boolean valid = true;
          for (int i = 0; i < counts.length; i++) {
            if (counts[i] == Statement.EXECUTE_FAILED) {
              if (firstFailed < 0) {
                firstFailed = i;
              }
              lastFailed = i;
            } else if (counts[i] < 0 && counts[i] != Statement.SUCCESS_NO_INFO) {
              valid = false;
            }
          }
          if (valid && firstFailed >= 0) {
            first = rows.get(firstFailed);
            last = rows.get(lastFailed);
          } else if (valid && counts.length > 0 && counts.length < rows.size()) {
            first = last = rows.get(counts.length);
          }
        }
      }
      throw located(error, first, last);
    }
  }

  private SQLException located(SQLException error, long first, long last) {
    String where = position + " " + first + (first == last ? "" : "–" + last);
    return new SQLException(
        where + ": " + (error.getMessage() == null ? error.toString() : error.getMessage()),
        error.getSQLState(),
        error.getErrorCode(),
        error);
  }
}
