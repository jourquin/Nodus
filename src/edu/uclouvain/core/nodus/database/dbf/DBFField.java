/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.database.dbf;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A database field. Its attributes are name, type, length, and decimal count. These fields must be
 * conform to DBase file fields.
 *
 * <p>Adapted from original free code by SV Consulting (not existing anymore).
 *
 * @author Bart Jourquin
 */
public class DBFField {

  private int decimalCount = 0;

  private DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols();

  private int length = 0;

  private String name = null;

  private char type = 0;

  private boolean standardDouble = true;

  /**
   * Construct a database field with a given name, type, length, and decimal count.
   *
   * @param name The name of the field.
   * @param type The type of the field.
   * @param length The length of the field.
   * @param decimalCount The number of places after the decimal dot.
   * @throws DBFException On error
   */
  public DBFField(String name, char type, int length, int decimalCount) throws DBFException {
    this(name, type, length, decimalCount, true);
  }

  /**
   * Construct a database field with a given name, type, length, and decimal count.
   *
   * @param name The name of the field.
   * @param type The type of the field.
   * @param length The length of the field.
   * @param decimalCount The number of places after the decimal dot.
   * @param standardDouble If false, accepts non standard double fields found in some
   *     (non-conformal) shape files.
   * @throws DBFException On error
   */
  public DBFField(String name, char type, int length, int decimalCount, boolean standardDouble)
      throws DBFException {

    this.standardDouble = standardDouble;

    if (name.length() > 10) {
      throw new DBFException("The field name is more than 10 characters long: " + name);
    }

    if (type != 'C' && type != 'N' && type != 'L' && type != 'D' && type != 'F') {
      throw new DBFException("The field type is not a valid. Got: " + type);
    }

    if (length < 1) {
      throw new DBFException("The field length should be a positive integer. Got: " + length);
    }

    if (type == 'C' && length >= 254) {
      throw new DBFException(
          "The field length should be less than 254 characters for character fields. Got: "
              + length);
    }

    if (type == 'N' && length >= 21 && standardDouble) {
      throw new DBFException(
          "The field length should be less than 21 digits for numeric fields. Got: " + length);
    }

    if (type == 'L' && length != 1) {
      throw new DBFException(
          "The field length should be 1 character for logical fields. Got: " + length);
    }

    if (type == 'D' && length != 8) {
      throw new DBFException(
          "The field length should be 8 characaters for date fields. Got: " + length);
    }

    if (type == 'F' && length >= 21) {
      throw new DBFException(
          "The field length should be less than 21 digits for floating point fields. Got: "
              + length);
    }

    if (decimalCount < 0) {
      throw new DBFException(
          "The field decimal count should not be a negative integer. Got: " + decimalCount);
    }

    if ((type == 'C' || type == 'L' || type == 'D') && decimalCount != 0) {
      throw new DBFException(
          "The field decimal count should be 0 for character, logical, and date fields. Got: "
              + decimalCount);
    }

    if (decimalCount > length - 1 && standardDouble) {
      throw new DBFException(
          "The field decimal count should be less than the length - 1. Got: " + decimalCount);
    } else {
      this.name = name;
      this.type = type;
      this.length = length;
      this.decimalCount = decimalCount;
      decimalFormatSymbols.setDecimalSeparator('.');

      return;
    }
  }

  /**
   * Formats a value to its String representation, based on the field format.
   *
   * @param object The object to parse.
   * @return The string representation of the object, in accordance with the format of the field.
   * @throws DBFException On error
   */
  public String format(Object object) throws DBFException {
    if (type == 'N' || type == 'F') {
      if (object == null) {
        object = new Double(0.0D);
      }

      if (object instanceof Number) {
        Number number = (Number) object;
        StringBuffer stringbuffer = new StringBuffer(getLength());

        for (int i = 0; i < getLength(); i++) {
          stringbuffer.append("#");
        }

        if (getDecimalCount() > 0) {
          stringbuffer.setCharAt(getLength() - getDecimalCount() - 1, '.');
        }

        DecimalFormat decimalformat =
            new DecimalFormat(stringbuffer.toString(), decimalFormatSymbols);
        String s1 = decimalformat.format(number);
        int k = getLength() - s1.length();

        if (k < 0) {
          throw new DBFException(
              "Value " + number + " cannot fit in pattern: '" + stringbuffer + "'.");
        }

        StringBuffer stringbuffer2 = new StringBuffer(k);

        for (int l = 0; l < k; l++) {
          stringbuffer2.append(" ");
        }

        return stringbuffer2 + s1;
      } else {
        throw new DBFException("Expected a Number, got " + object.getClass() + ".");
      }
    }

    if (type == 'C') {
      if (object == null) {
        object = "";
      }

      if (object instanceof String) {
        String s = (String) object;

        if (s.length() > getLength()) {
          throw new DBFException("'" + object + "' is longer than " + getLength() + " characters.");
        }

        StringBuffer stringbuffer1 = new StringBuffer(getLength() - s.length());

        for (int j = 0; j < getLength() - s.length(); j++) {
          stringbuffer1.append(' ');
        }

        return s + stringbuffer1;
      } else {
        throw new DBFException("Expected a String, got " + object.getClass() + ".");
      }
    }

    if (type == 'L') {
      if (object == null) {
        object = Boolean.valueOf(false);
      }

      if (object instanceof Boolean) {
        Boolean boolean1 = (Boolean) object;

        return boolean1.booleanValue() ? "Y" : "N";
      } else {
        throw new DBFException("Expected a Boolean, got " + object.getClass() + ".");
      }
    }

    if (type == 'D') {
      if (object == null) {
        object = new Date();
      }

      if (object instanceof Date) {
        Date date = (Date) object;
        SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMdd");

        return simpledateformat.format(date);
      } else {
        throw new DBFException("Expected a Date, got " + object.getClass() + ".");
      }
    } else {
      throw new DBFException("Unrecognized JDBFField type: " + type);
    }
  }

  /**
   * Returns the decimal count of the field.
   *
   * @return The number of places after the decimal dot.
   */
  public int getDecimalCount() {
    return decimalCount;
  }

  /**
   * Sets the number of places after the decimal dot.
   *
   * @param decimalCount The number of places after the decimal dot.
   */
  public void setDecimalCount(int decimalCount) {
    this.decimalCount = decimalCount;
  }

  /**
   * Returns the length of the field.
   *
   * @return The length of the field.
   */
  public int getLength() {
    return length;
  }

  /**
   * Sets the length of the field (number of decimal places before the decimal dot + number of
   * places after the decimal dot + one (foe the dot).
   *
   * @param length The length of the field.
   */
  public void setLength(int length) {
    this.length = length;
  }

  /**
   * Returns the name of the field.
   *
   * @return The name of the field.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the type of the field.
   *
   * @return The type of the field.
   */
  public char getType() {
    return type;
  }

  /**
   * Parses a formatted String and returns an object of type, corresponding to this field.
   *
   * @param formattedValue The string representation of the formatted value.
   * @return The object (to be written in a .dbf file).
   * @throws DBFException On error
   */
  public Object parse(String formattedValue) throws DBFException {
    formattedValue = formattedValue.trim();

    if (type == 'N' || type == 'F') {
      if (formattedValue.equals("") || formattedValue.startsWith("*")) { // Empty or null
        formattedValue = "0";
      }

      try {
        if (getDecimalCount() == 0) {
          return new Long(formattedValue);
        } else {
          return new Double(formattedValue);
        }
      } catch (NumberFormatException numberformatexception) {
        // Found in an ETIS dbf file. Ad hoc fix :-(
        if (!standardDouble && formattedValue.contains("1.#INF")) {
          if (decimalCount == 0) {
            return new Long(0);
          } else {
            return new Double(0);
          }
        } else {
          throw new DBFException(numberformatexception);
        }
      }
    }

    if (type == 'C') {
      return formattedValue;
    }

    if (type == 'L') {
      if (formattedValue.equals("Y")
          || formattedValue.equals("y")
          || formattedValue.equals("T")
          || formattedValue.equals("t")) {
        return Boolean.valueOf(true);
      }

      if (formattedValue.equals("N")
          || formattedValue.equals("n")
          || formattedValue.equals("F")
          || formattedValue.equals("f")) {
        return Boolean.valueOf(false);
      } else {
        throw new DBFException("Unrecognized value for logical field: " + formattedValue);
      }
    }

    if (type == 'D') {
      SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMdd");

      try {
        if ("".equals(formattedValue)) {
          return null;
        } else {
          return simpledateformat.parse(formattedValue);
        }
      } catch (ParseException parseexception) {
        // throw new JDBFException(parseexception);
        return null;
      }
    } else {
      throw new DBFException("Unrecognized JDBFField type: " + type);
    }
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the String representation of the JDBField.
   *
   * @return String representation of the JDBField.
   */
  @Override
  public String toString() {
    return name + " " + type + "(" + length + "," + decimalCount + ")";
  }
}
