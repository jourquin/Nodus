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

import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/** Resolves and validates a sheet's schema before any destination rows are removed. */
final class SpreadsheetImportPlan {
  private static final Pattern DESCRIPTOR =
      Pattern.compile("([^,]+),\\s*([CN]),\\s*(\\d+)(?:,\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
  final List<Column> columns;
  final int firstDataRow;
  final boolean replacement;

  private SpreadsheetImportPlan(List<Column> columns, int firstDataRow, boolean replacement) {
    this.columns = columns;
    this.firstDataRow = firstDataRow;
    this.replacement = replacement;
  }

  static SpreadsheetImportPlan read(
      Connection connection, String table, Sheet sheet, boolean header) throws SQLException {
    Row first = sheet.getRow(sheet.getFirstRowNum());
    int width = 0;
    for (Row row : sheet) {
      for (Cell cell : row) {
        if (cell.getCellType() != CellType.BLANK) {
          width = Math.max(width, cell.getColumnIndex() + 1);
        }
      }
    }
    int firstData = sheet.getFirstRowNum();
    boolean described = false;
    if (first != null) {
      for (Cell cell : first) {
        if (cell.getCellType() == CellType.STRING
            && cell.getStringCellValue().matches("(?is).*,\\s*[CN]\\s*,.*")) {
          described = true;
        }
      }
    }
    List<Column> columns = new ArrayList<>();
    if (described || header) {
      if (first == null || first.getLastCellNum() < 1) {
        throw new SQLException("Spreadsheet has no header row");
      }
      int headerWidth = first.getLastCellNum();
      if (width > headerWidth) {
        throw problem(sheet, width - 1, "data extends beyond the header");
      }
      width = headerWidth;
      Set<String> names = new HashSet<>();
      for (int i = 0; i < width; i++) {
        Cell cell = first.getCell(i);
        if (cell == null || cell.getCellType() != CellType.STRING) {
          throw problem(first.getRowNum(), i, "expected a non-empty column name");
        }
        String name = cell.getStringCellValue().trim();
        Column column = new Column(name, i);
        if (described) {
          Matcher matcher = DESCRIPTOR.matcher(name);
          if (!matcher.matches()) {
            throw problem(first.getRowNum(), i, "invalid DBF column description: " + name);
          }
          name = matcher.group(1).trim();
          column = new Column(name, i);
          try {
            column.precision = Integer.parseInt(matcher.group(3));
            boolean number = matcher.group(2).equalsIgnoreCase("N");
            if (number != (matcher.group(4) != null)) {
              throw new IllegalArgumentException();
            }
            column.scale = number ? Integer.parseInt(matcher.group(4)) : 0;
            if (column.precision < 1 || column.scale > column.precision) {
              throw new IllegalArgumentException();
            }
            column.type = number ? Types.DECIMAL : Types.VARCHAR;
            column.definition =
                number
                    ? "DECIMAL(" + column.precision + "," + column.scale + ")"
                    : "VARCHAR(" + column.precision + ")";
          } catch (IllegalArgumentException invalid) {
            throw problem(first.getRowNum(), i, "invalid DBF size or scale: " + name);
          }
        }
        if (name.isEmpty() || !names.add(name.toUpperCase(Locale.ROOT))) {
          throw problem(first.getRowNum(), i, "empty or duplicate column name: " + name);
        }
        columns.add(column);
      }
      firstData++;
    }
    boolean existing = JDBCUtils.tableExists(connection, table);
    if (!described && existing) {
      List<Column> target = existingColumns(connection, table);
      if (width != target.size() && !(width == 0 && !header)) {
        String message =
            "sheet has " + width + " columns; table " + table + " expects " + target.size();
        throw width > target.size()
            ? problem(sheet, target.size(), message)
            : problem(sheet.getFirstRowNum(), width, message);
      }
      for (Column column : target) {
        if (header) {
          column.source = -1;
          for (Column source : columns) {
            if (column.name.equalsIgnoreCase(source.name)) {
              column.source = source.source;
              break;
            }
          }
          if (column.source < 0) {
            throw new SQLException(
                "Spreadsheet header does not contain table column: " + column.name);
          }
        }
      }
      columns = target;
    } else if (!described) {
      if (width == 0) {
        throw new SQLException("Cannot infer columns from an empty spreadsheet");
      }
      if (!header) {
        for (int i = 0; i < width; i++) {
          columns.add(new Column("COL" + (i + 1), i));
        }
      }
      for (Column column : columns) {
        column.infer(sheet, firstData);
      }
    }
    SpreadsheetImportPlan plan =
        new SpreadsheetImportPlan(columns, firstData, described || !existing);
    for (Row row : sheet) {
      if (row.getRowNum() >= firstData) {
        for (Column column : columns) {
          column.value(row);
        }
      }
    }
    return plan;
  }

  String createStatement(String table) {
    List<String> definitions = new ArrayList<>();
    for (Column column : columns) {
      definitions.add(
          JDBCUtils.getQuotedCompliantIdentifier(column.name) + " " + column.definition);
    }
    return "CREATE TABLE "
        + JDBCUtils.getQuotedCompliantIdentifier(table)
        + " ("
        + String.join(",", definitions)
        + ")";
  }

  private static List<Column> existingColumns(Connection connection, String table)
      throws SQLException {
    List<Column> columns = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(table) + " WHERE 1=0")) {
      ResultSetMetaData metadata = rows.getMetaData();
      for (int i = 0; i < metadata.getColumnCount(); i++) {
        Column column = new Column(metadata.getColumnName(i + 1), i);
        column.type = metadata.getColumnType(i + 1);
        column.precision = metadata.getPrecision(i + 1);
        column.scale = metadata.getScale(i + 1);
        column.nullable = metadata.isNullable(i + 1) != ResultSetMetaData.columnNoNulls;
        columns.add(column);
      }
    }
    return columns;
  }

  private static SQLException problem(Sheet sheet, int column, String message) {
    for (Row row : sheet) {
      Cell cell = row.getCell(column);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        return problem(row.getRowNum(), column, message);
      }
    }
    return new SQLException(message);
  }

  private static SQLException problem(int row, int column, String message) {
    return new SQLException(
        "Spreadsheet row " + (row + 1) + ", column " + (column + 1) + ": " + message);
  }

  private static Object readCell(Cell cell) {
    if (cell == null) {
      return null;
    }
    CellType type =
        cell.getCellType() == CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
    switch (type) {
      case BLANK:
        return null;
      case STRING:
        return cell.getStringCellValue();
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          LocalDateTime date = cell.getLocalDateTimeCellValue();
          String format = cell.getCellStyle().getDataFormatString().toLowerCase(Locale.ROOT);
          if (!format.contains("y") && !format.contains("d")) {
            return date.toLocalTime();
          }
          return date.toLocalTime().equals(LocalTime.MIDNIGHT) ? date.toLocalDate() : date;
        }
        return BigDecimal.valueOf(cell.getNumericCellValue());
      default:
        throw new IllegalArgumentException("cell has an error or no cached formula result");
    }
  }

  /** One destination column and its corresponding source cell index. */
  static final class Column {
    final String name;
    int source;
    int type;
    int precision;
    int scale;
    boolean nullable = true;
    boolean textConversion;
    String definition;

    Column(String name, int source) {
      this.name = name;
      this.source = source;
    }

    void infer(Sheet sheet, int firstData) throws SQLException {
      String kind = "";
      int length = 1;
      int integers = 0;
      for (Row row : sheet) {
        if (row.getRowNum() < firstData) {
          continue;
        }
        Object value;
        try {
          value = readCell(row.getCell(source));
        } catch (RuntimeException invalid) {
          throw problem(row.getRowNum(), source, invalid.getMessage());
        }
        if (value == null) {
          continue;
        }
        length = Math.max(length, value.toString().length());
        String next =
            value instanceof BigDecimal
                ? "number"
                : value instanceof Boolean
                    ? "boolean"
                    : value instanceof LocalDate
                        ? "date"
                        : value instanceof LocalTime
                            ? "time"
                            : value instanceof LocalDateTime ? "timestamp" : "text";
        if (kind.isEmpty()) {
          kind = next;
        } else if (!kind.equals(next)) {
          kind =
              (kind.equals("date") && next.equals("timestamp"))
                      || (kind.equals("timestamp") && next.equals("date"))
                  ? "timestamp"
                  : "text";
        }
        if (value instanceof BigDecimal) {
          BigDecimal number = ((BigDecimal) value).stripTrailingZeros();
          integers = Math.max(integers, number.precision() - number.scale());
          scale = Math.max(scale, Math.max(0, number.scale()));
        }
      }
      precision = Math.max(1, integers + scale);
      if (kind.equals("number") && precision <= 38) {
        type = scale == 0 && integers <= 18 ? Types.BIGINT : Types.DECIMAL;
        definition = type == Types.BIGINT ? "BIGINT" : "DECIMAL(" + precision + "," + scale + ")";
      } else if (kind.equals("boolean")) {
        type = Types.BOOLEAN;
        definition = "BOOLEAN";
      } else if (kind.equals("date")) {
        type = Types.DATE;
        definition = "DATE";
      } else if (kind.equals("time")) {
        type = Types.TIME;
        definition = "TIME(3)";
      } else if (kind.equals("timestamp")) {
        type = Types.TIMESTAMP;
        definition = "TIMESTAMP(3)";
      } else {
        type = Types.VARCHAR;
        precision = length;
        textConversion = true;
        definition = "VARCHAR(" + length + ")";
      }
    }

    Object value(Row row) throws SQLException {
      try {
        Object value = readCell(row.getCell(source));
        if (value == null) {
          if (!nullable) {
            throw new IllegalArgumentException("blank cell for a NOT NULL column");
          }
          return null;
        }
        switch (type) {
          case Types.CHAR:
          case Types.VARCHAR:
          case Types.LONGVARCHAR:
          case Types.NCHAR:
          case Types.NVARCHAR:
          case Types.LONGNVARCHAR:
          case Types.CLOB:
          case Types.NCLOB:
            if (!(value instanceof String) && !textConversion) {
              throw new IllegalArgumentException("expected a text cell");
            }
            String text = value.toString();
            if (precision > 0 && text.codePointCount(0, text.length()) > precision) {
              throw new IllegalArgumentException("text exceeds column length " + precision);
            }
            return text;
          case Types.TINYINT:
          case Types.SMALLINT:
          case Types.INTEGER:
          case Types.BIGINT:
          case Types.DECIMAL:
          case Types.NUMERIC:
          case Types.FLOAT:
          case Types.REAL:
          case Types.DOUBLE:
            if (!(value instanceof BigDecimal)) {
              throw new IllegalArgumentException("expected a numeric cell");
            }
            BigDecimal number = (BigDecimal) value;
            if (type == Types.DECIMAL || type == Types.NUMERIC) {
              number = number.setScale(scale, RoundingMode.UNNECESSARY);
              if (precision > 0 && number.precision() > precision) {
                throw new IllegalArgumentException("number exceeds precision " + precision);
              }
            } else if (type == Types.BIGINT) {
              number.longValueExact();
            } else if (type == Types.INTEGER || type == Types.SMALLINT || type == Types.TINYINT) {
              int integer = number.intValueExact();
              if ((type == Types.SMALLINT
                      && (integer < Short.MIN_VALUE || integer > Short.MAX_VALUE))
                  || (type == Types.TINYINT
                      && (integer < Byte.MIN_VALUE || integer > Byte.MAX_VALUE))) {
                throw new IllegalArgumentException("integer exceeds column range");
              }
            }
            return number;
          case Types.BOOLEAN:
          case Types.BIT:
            if (value instanceof Boolean) {
              return value;
            }
            throw new IllegalArgumentException("expected a boolean cell");
          case Types.DATE:
            return value instanceof LocalDate ? value : LocalDate.parse(value.toString());
          case Types.TIME:
            return value instanceof LocalTime ? value : LocalTime.parse(value.toString());
          case Types.TIMESTAMP:
            return value instanceof LocalDate
                ? ((LocalDate) value).atStartOfDay()
                : value instanceof LocalDateTime ? value : LocalDateTime.parse(value.toString());
          default:
            throw new IllegalArgumentException("unsupported destination JDBC type " + type);
        }
      } catch (RuntimeException invalid) {
        throw problem(row.getRowNum(), source, name + ": " + invalid.getMessage());
      }
    }
  }
}
