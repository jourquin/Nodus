/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

/**
 * Utility class used to store the needed information to create a JDBC index.
 *
 * @author Bart Jourquin
 */
public class JDBCIndex {

  private String indexFieldName;
  private String indexName;
  private String tableName;

  /**
   * Create a new JDBC index.
   *
   * @param tableName The name of the table the index must be created for.
   * @param indexName The name of the index.
   * @param indexFieldName The field name of the index must be created for.
   */
  public JDBCIndex(String tableName, String indexName, String indexFieldName) {
    this.tableName = tableName;
    this.indexName = indexName;
    this.indexFieldName = indexFieldName;
  }

  /**
   * Returns the name of the field the index must be created for.
   *
   * @return The name of the indexed field.
   */
  public String getIndexFieldName() {
    return indexFieldName;
  }

  /**
   * Returns the name of the index.
   *
   * @return The name of the index.
   */
  public String getIndexName() {
    return tableName + "_" + indexName;
  }

  /**
   * Returns the name of the table the index is created for.
   *
   * @return The name of the table.
   */
  public String getTableName() {
    return tableName;
  }
}
