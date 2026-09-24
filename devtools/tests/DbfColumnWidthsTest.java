package edu.uclouvain.core.nodus.database;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compares bulk DBF sizing with the former per-column queries on disposable databases. */
public final class DbfColumnWidthsTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static String q(String name) {
    return JDBCUtils.getQuotedCompliantIdentifier(name);
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }

  /** Counts real aggregate queries, checks resource closure and detects transaction changes. */
  private static final class Calls {
    int queries;
    int aggregates;
    int statements;
    int closedStatements;
    int results;
    int closedResults;
    int transactionChanges;
    boolean failQuery;

    Connection wrap(Connection connection) {
      return (Connection)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                if (Arrays.asList("commit", "rollback", "setAutoCommit", "close")
                    .contains(method.getName())) transactionChanges++;
                Object result = invoke(connection, method, args);
                if (!(result instanceof Statement)) return result;
                statements++;
                Statement statement = (Statement) result;
                return Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {Statement.class},
                    (sp, sm, sa) -> {
                      if (sm.getName().equals("close")) closedStatements++;
                      if (sm.getName().equals("executeQuery")) {
                        queries++;
                        if (((String) sa[0]).contains("MAX(")) aggregates++;
                        if (failQuery) throw new SQLException("Injected sizing failure");
                      }
                      Object value = invoke(statement, sm, sa);
                      if (!(value instanceof ResultSet)) return value;
                      results++;
                      return Proxy.newProxyInstance(
                          getClass().getClassLoader(),
                          new Class<?>[] {ResultSet.class},
                          (rp, rm, ra) -> {
                            if (rm.getName().equals("close")) closedResults++;
                            return invoke(value, rm, ra);
                          });
                    });
              });
    }

    void closed() {
      check(statements == closedStatements && results == closedResults, "JDBC resource leak");
      check(transactionChanges == 0, "Changed caller-owned connection/transaction");
    }
  }

  /** Independent copy of the old query and formatting algorithm, including NULL handling. */
  private static Map<String, Integer> oldWidths(
      Connection connection, String table, Map<String, Integer> columns) throws SQLException {
    Map<String, Integer> widths = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> column : columns.entrySet()) {
      int digits = column.getValue();
      DecimalFormat format = new DecimalFormat("#" + (digits > 0 ? "." + "0".repeat(digits) : ""));
      format.setMaximumFractionDigits(digits);
      int width = -1;
      for (String aggregate : new String[] {"MAX", "MIN"}) {
        try (Statement statement = connection.createStatement();
            ResultSet rs =
                statement.executeQuery(
                    "SELECT " + aggregate + "(" + q(column.getKey()) + ") FROM " + q(table))) {
          if (rs.next()) width = Math.max(width, format.format(rs.getDouble(1)).length());
        }
      }
      widths.put(column.getKey(), width);
    }
    return widths;
  }

  private static void compare(Connection connection, String table, Map<String, Integer> columns)
      throws Exception {
    Map<String, Integer> expected = oldWidths(connection, table, columns);
    Calls calls = new Calls();
    JDBCUtils.setConnection(calls.wrap(connection));
    check(expected.equals(JDBCUtils.getNumWidths(table, columns)), "Different widths: " + table);
    check(calls.queries == (columns.isEmpty() ? 0 : 1), "Expected one aggregate query");
    calls.closed();
    JDBCUtils.setConnection(connection);
  }

  private static void checkWidths(Connection connection) throws Exception {
    String table = "width samples";
    Map<String, Integer> columns = new LinkedHashMap<>();
    columns.put("order", 0);
    columns.put("fraction", 2);
    columns.put("all null", 4);
    columns.put("big value", 0);
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE "
              + q(table)
              + " ("
              + q("order")
              + " INTEGER, "
              + q("fraction")
              + " DECIMAL(18,4), "
              + q("all null")
              + " DECIMAL(18,4), "
              + q("big value")
              + " BIGINT)");
      compare(connection, table, columns); // Empty table.
      statement.executeUpdate("INSERT INTO " + q(table) + " VALUES (NULL, NULL, NULL, NULL)");
      compare(connection, table, columns); // All-NULL columns.
      statement.executeUpdate(
          "INSERT INTO " + q(table) + " VALUES (-123456, 9.9999, NULL, 9007199254740993)");
      statement.executeUpdate(
          "INSERT INTO " + q(table) + " VALUES (12, -999.9999, NULL, -9007199254740993)");
      Locale original = Locale.getDefault(Locale.Category.FORMAT);
      try {
        for (Locale locale : new Locale[] {Locale.US, Locale.FRANCE}) {
          Locale.setDefault(Locale.Category.FORMAT, locale);
          compare(connection, table, columns);
          check(JDBCUtils.getNumWidth(table, "fraction", 2) == 8, "Rounding/sign width changed");
        }
      } finally {
        Locale.setDefault(Locale.Category.FORMAT, original);
      }
      statement.executeUpdate("UPDATE " + q(table) + " SET " + q("order") + " = -123456789");
      check(JDBCUtils.getNumWidths(table, columns).get("order") == 10, "Stale width after edit");
    }
    compare(connection, "unused table", Collections.emptyMap());
    Calls calls = new Calls();
    calls.failQuery = true;
    JDBCUtils.setConnection(calls.wrap(connection));
    try {
      JDBCUtils.getNumWidths(table, columns);
      throw new AssertionError("Query failure was swallowed");
    } catch (SQLException expected) {
      check(expected.getMessage().contains("Injected"), "Unexpected SQL exception");
    } finally {
      calls.closed();
      JDBCUtils.setConnection(connection);
    }
  }

  private static final class Project extends NodusProject {
    final Connection connection;
    final Path directory;

    Project(Connection connection, Path directory) {
      super(null);
      this.connection = connection;
      this.directory = directory;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return directory.toString() + "/";
    }
  }

  private static final class Labels extends NodusLocationHandler {
    Labels() {
      super(null);
    }

    @Override
    public synchronized void reloadData() {}
  }

  private static final class Layer extends NodusEsriLayer {
    final DbfTableModel model;

    Layer(DbfTableModel model) {
      this.model = model;
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public void doPrepare() {}

    @Override
    public int getNumIndex(int num) {
      return num - 1;
    }
  }

  private static void checkCallers(Connection connection, Path directory) throws Exception {
    String table = "width_export";
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE "
              + q(table)
              + " ("
              + q("num")
              + " INTEGER, "
              + q("amount")
              + " DECIMAL(12,2), "
              + q("label")
              + " VARCHAR(12), "
              + q("stamp")
              + " DATE)");
      statement.executeUpdate("INSERT INTO " + q(table) + " VALUES (2, -1234.50, 'two', NULL)");
      statement.executeUpdate("INSERT INTO " + q(table) + " VALUES (1, 99.99, 'one', NULL)");
    }
    List<String> names = new ArrayList<>();
    List<Integer> lengths = new ArrayList<>();
    List<Integer> decimals = new ArrayList<>();
    Map<String, Integer> numeric = new LinkedHashMap<>();
    try (ResultSet rs = JDBCUtils.getColumns(table)) {
      while (rs.next()) {
        String name = rs.getString("COLUMN_NAME");
        String type = rs.getString("TYPE_NAME").toUpperCase(Locale.ROOT);
        names.add(name);
        decimals.add(rs.getInt("DECIMAL_DIGITS"));
        lengths.add(type.contains("CHAR") ? rs.getInt("COLUMN_SIZE") : 8);
        if (!type.contains("CHAR") && !type.contains("DATE"))
          numeric.put(name, rs.getInt("DECIMAL_DIGITS"));
      }
    }
    Map<String, Integer> expected = oldWidths(connection, table, numeric);
    for (int i = 0; i < names.size(); i++) {
      if (expected.containsKey(names.get(i))) lengths.set(i, expected.get(names.get(i)));
    }
    Calls calls = new Calls();
    Connection monitored = calls.wrap(connection);
    JDBCUtils.setConnection(monitored);
    Project project = new Project(monitored, directory);
    Method create =
        ExportDBF.class.getDeclaredMethod("createTable", NodusProject.class, String.class);
    create.setAccessible(true);
    try (DBFWriter writer = (DBFWriter) create.invoke(null, project, table)) {
      check(writer != null, "Could not create export header");
    }
    check(calls.aggregates == 1, "Export did not use one width query");
    try (DBFReader reader = new DBFReader(directory.resolve(table + ".dbf").toString())) {
      check(reader.getFieldCount() == 4, "Export lost columns");
      for (int i = 0; i < names.size(); i++) {
        check(
            reader.getField(i).getName().equalsIgnoreCase(names.get(i)),
            "Export reordered columns");
        check(reader.getField(i).getLength() == lengths.get(i), "Export changed field width");
        check(
            reader.getField(i).getDecimalCount()
                == (numeric.containsKey(names.get(i)) ? decimals.get(i) : 0),
            "Export changed decimal count");
      }
    }
    DbfTableModel model = new DbfTableModel(4);
    for (int i = 0; i < 4; i++) {
      model.setColumnName(i, names.get(i));
      model.setLength(i, lengths.get(i));
      model.setDecimalCount(i, decimals.get(i).byteValue());
      model.setType(
          i,
          i < 2
              ? DbfTableModel.TYPE_NUMERIC
              : (i == 2 ? DbfTableModel.TYPE_CHARACTER : DbfTableModel.TYPE_DATE));
    }
    model.addRecord(new ArrayList<>(Arrays.asList(1, 0, "", null)));
    model.addRecord(new ArrayList<>(Arrays.asList(2, 0, "", null)));
    Layer layer = new Layer(model);
    Field projectField = NodusEsriLayer.class.getDeclaredField("nodusProject");
    projectField.setAccessible(true);
    projectField.set(layer, project);
    Field tableField = NodusEsriLayer.class.getDeclaredField("tableName");
    tableField.setAccessible(true);
    tableField.set(layer, table);
    // Bypass the label handler's GUI-dependent constructor; DBF synchronization only calls
    // reloadData.
    Class<?> unsafe = Class.forName("sun.misc.Unsafe");
    Field allocator = unsafe.getDeclaredField("theUnsafe");
    allocator.setAccessible(true);
    layer.setLocationHandler(
        (Labels)
            unsafe
                .getMethod("allocateInstance", Class.class)
                .invoke(allocator.get(null), Labels.class));
    PrintStream oldError = System.err;
    try (PrintStream captured = new PrintStream(new ByteArrayOutputStream())) {
      System.setErr(captured); // Existing sync logs NULL dates and the expected rejected width.
      check(layer.updateDbfTableModel(), "Layer synchronization failed");
      check(calls.aggregates == 2, "Synchronization did not use one width query");
      check(
          JDBCUtils.getDouble(model.getValueAt(0, 1)) == 99.99
              && JDBCUtils.getDouble(model.getValueAt(1, 1)) == -1234.50,
          "Synchronization changed row mapping");
      model.setLength(1, 1);
      check(!layer.updateDbfTableModel(), "Synchronization accepted a too-narrow field");
    } finally {
      System.setErr(oldError);
      JDBCUtils.setConnection(connection);
    }
    calls.closed();
    Files.delete(directory.resolve(table + ".dbf"));
  }

  private static void benchmark(Connection connection, String engine) throws Exception {
    Map<String, Integer> columns = new LinkedHashMap<>();
    StringBuilder definition = new StringBuilder("CREATE TABLE width_bench (");
    for (int i = 0; i < 20; i++) {
      if (i > 0) definition.append(',');
      definition.append(q("value" + i)).append(" INTEGER");
      columns.put("value" + i, 0);
    }
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(definition.append(')').toString());
    }
    connection.setAutoCommit(false);
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO width_bench VALUES ("
                + String.join(",", Collections.nCopies(20, "?"))
                + ")")) {
      for (int row = 0; row < 100000; row++) {
        for (int col = 0; col < 20; col++)
          insert.setInt(col + 1, row % 2 == 0 ? row + col : -row - col);
        insert.addBatch();
        if (row % 1000 == 999) insert.executeBatch();
      }
    }
    connection.commit();
    long oldTime = 0;
    long bulkTime = 0;
    for (int pass = 0; pass < 5; pass++) {
      for (int order = 0; order < 2; order++) {
        boolean bulk = (pass + order) % 2 == 0;
        long start = System.nanoTime();
        Map<String, Integer> widths =
            bulk
                ? JDBCUtils.getNumWidths("width_bench", columns)
                : oldWidths(connection, "width_bench", columns);
        long elapsed = System.nanoTime() - start;
        check(widths.size() == 20 && widths.get("value0") == 6, "Benchmark widths differ");
        if (pass > 0) {
          if (bulk) bulkTime += elapsed;
          else oldTime += elapsed;
        }
      }
    }
    System.out.printf(
        Locale.ROOT,
        "%s: 100,000 rows, 20 unindexed numeric columns; mean sizing %.3f -> %.3f ms (40 -> 1 queries).%n",
        engine,
        oldTime / 4e6,
        bulkTime / 4e6);
  }

  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("nodus-dbf-widths-");
    System.setProperty("derby.stream.error.file", directory.resolve("derby.log").toString());
    Map<String, String> databases = new LinkedHashMap<>();
    databases.put("HSQLDB", "jdbc:hsqldb:mem:width_test");
    databases.put("H2", "jdbc:h2:mem:width_test");
    databases.put("SQLite", "jdbc:sqlite::memory:");
    databases.put("Derby", "jdbc:derby:memory:width_test;create=true");
    for (Map.Entry<String, String> database : databases.entrySet()) {
      try (Connection connection = DriverManager.getConnection(database.getValue())) {
        check(JDBCUtils.setConnection(connection), "Could not initialize JDBC metadata");
        checkWidths(connection);
        checkCallers(connection, directory);
        connection.setAutoCommit(false);
        compare(connection, "width samples", Collections.singletonMap("order", 0));
        connection.rollback();
        if (Arrays.asList(args).contains("--benchmark")) benchmark(connection, database.getKey());
        connection.rollback();
        System.out.println(database.getKey() + ": DBF column width checks passed.");
      } finally {
        JDBCUtils.setConnection(null);
      }
    }
    check(
        JDBCUtils.getNumWidth("missing", "missing", 0) == -1,
        "Single-column compatibility changed");
    check(
        JDBCUtils.getNumWidths("missing", Collections.emptyMap()).isEmpty(),
        "Empty input needs connection");
    try {
      JDBCUtils.getNumWidths("missing", Collections.singletonMap("missing", 0));
      throw new AssertionError("Missing connection was accepted");
    } catch (SQLException expected) {
      // Bulk callers must stop when sizing is unavailable.
    }
    Files.deleteIfExists(directory.resolve("derby.log"));
    Files.delete(directory);
  }
}
