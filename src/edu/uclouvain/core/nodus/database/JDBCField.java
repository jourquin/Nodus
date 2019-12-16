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
 * Utility class used to store the needed information to create a JDBC field.
 *
 * @author Bart Jourquin
 */
public class JDBCField {

  /** Field name. */
  private String fieldName;

  /** Field type. */
  private String fieldType;

  /**
   * Creates a new JDBCField with a given name and type.
   *
   * @param fieldName The name of the field.
   * @param fieldType The type of the field.
   */
  public JDBCField(String fieldName, String fieldType) {
    this.fieldName = fieldName;
    this.fieldType = fieldType;
  }

  /**
   * Returns the name of the field or null if this is not a valid field.
   *
   * @return The name of the field.
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Returns the type of the field or null if it is not a valid field.
   *
   * @return The type of the field.
   */
  public String getFieldType() {
    return fieldType;
  }
}
