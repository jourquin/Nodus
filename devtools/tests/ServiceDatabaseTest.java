package edu.uclouvain.core.nodus.services;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
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
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Real JDBC checks against disposable in-memory databases, without a project or GUI. */
public final class ServiceDatabaseTest {
  private static final String HEADER = "service headers";
  private static final String LINKS = "service links";
  private static final String STOPS = "service stops";
  private static final Map<Integer, OMGraphic> GRAPHICS = new HashMap<>();
  private static final Map<OMGraphic, Integer> IDS = new IdentityHashMap<>();

  static {
    for (int id = 101; id <= 103; id++) {
      OMGraphic graphic = new OMPoint(0, 0);
      GRAPHICS.put(id, graphic);
      IDS.put(graphic, id);
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static String q(String name) {
    return JDBCUtils.getQuotedCompliantIdentifier(name);
  }

  private static ServiceDatabase store(Connection connection) {
    return new ServiceDatabase(connection, HEADER, LINKS, STOPS);
  }

  private static Object call(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** Counts actual JDBC calls and can simulate the standard batch-result status codes. */
  private static final class Calls {
    int queries;
    int updates;
    int batches;
    int addedRows;
    int commits;
    int rollbacks;
    int autoCommitChanges;
    int statements;
    int closedStatements;
    int results;
    int closedResults;
    int batchResult = 1;
    final List<Integer> batchSizes = new ArrayList<>();

    Connection wrap(Connection connection) {
      return (Connection)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "commit":
                    commits++;
                    break;
                  case "rollback":
                    rollbacks++;
                    break;
                  case "setAutoCommit":
                    autoCommitChanges++;
                    break;
                  default:
                    break;
                }
                Object value = call(connection, method, args);
                if (value instanceof Statement) {
                  return statement((Statement) value);
                }
                return value;
              });
    }

    private Statement statement(Statement delegate) {
      statements++;
      int[] pending = {0};
      Class<?> type =
          delegate instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
      return (Statement)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {type},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "executeQuery":
                    queries++;
                    break;
                  case "executeUpdate":
                    updates++;
                    break;
                  case "addBatch":
                    pending[0]++;
                    addedRows++;
                    break;
                  case "executeBatch":
                    batches++;
                    batchSizes.add(pending[0]);
                    pending[0] = 0;
                    break;
                  case "clearBatch":
                    pending[0] = 0;
                    break;
                  case "close":
                    closedStatements++;
                    break;
                  default:
                    break;
                }
                Object value = call(delegate, method, args);
                if (value instanceof ResultSet) {
                  return result((ResultSet) value);
                }
                if (method.getName().equals("executeBatch") && batchResult != 1) {
                  int[] counts = (int[]) value;
                  Arrays.fill(counts, batchResult);
                }
                return value;
              });
    }

    private ResultSet result(ResultSet delegate) {
      results++;
      return (ResultSet)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {ResultSet.class},
              (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                  closedResults++;
                }
                return call(delegate, method, args);
              });
    }

    void closed() {
      check(statements == closedStatements, "Leaked JDBC statements");
      check(results == closedResults, "Leaked JDBC result sets");
      check(
          commits == 0 && rollbacks == 0 && autoCommitChanges == 0,
          "Persistence routine changed caller-owned transaction state");
    }
  }

  private static void tables(Connection connection, boolean modern, boolean constraints)
      throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      for (String table : new String[] {STOPS, LINKS, HEADER}) {
        if (JDBCUtils.tableExists(table)) {
          statement.executeUpdate("DROP TABLE " + q(table));
        }
      }
      statement.executeUpdate(
          "CREATE TABLE "
              + q(HEADER)
              + " ("
              + q(NodusC.DBF_ID)
              + " NUMERIC(4,0)"
              + (constraints ? " PRIMARY KEY" : "")
              + ","
              + q(NodusC.DBF_SERVICE_NAME)
              + " VARCHAR(30),"
              + q(NodusC.DBF_MODE)
              + " NUMERIC(2,0),"
              + q(NodusC.DBF_MEANS)
              + " NUMERIC(2,0),"
              + q(NodusC.DBF_FREQUENCY)
              + " NUMERIC(5,0))");
      String parent =
          constraints
              ? ", FOREIGN KEY ("
                  + q(NodusC.DBF_ID)
                  + ") REFERENCES "
                  + q(HEADER)
                  + "("
                  + q(NodusC.DBF_ID)
                  + ")"
              : "";
      statement.executeUpdate(
          "CREATE TABLE "
              + q(LINKS)
              + " ("
              + q(NodusC.DBF_ID)
              + " NUMERIC(4,0),"
              + (modern ? q(NodusC.DBF_PATH_INDEX) + " NUMERIC(8,0)," : "")
              + q(NodusC.DBF_LINK)
              + " NUMERIC(10,0)"
              + parent
              + ")");
      statement.executeUpdate(
          "CREATE TABLE "
              + q(STOPS)
              + " ("
              + q(NodusC.DBF_ID)
              + " NUMERIC(4,0),"
              + q(NodusC.DBF_STOP)
              + " NUMERIC(10,0)"
              + (constraints ? " CHECK (" + q(NodusC.DBF_STOP) + " <> 9999)" : "")
              + parent
              + ")");
    }
    connection.setAutoCommit(false);
  }

  private static void insertRows(Connection connection, String table, Object[]... rows)
      throws SQLException {
    for (Object[] row : rows) {
      String[] placeholders = new String[row.length];
      Arrays.fill(placeholders, "?");
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO " + q(table) + " VALUES (" + String.join(",", placeholders) + ")")) {
        for (int i = 0; i < row.length; i++) {
          statement.setObject(i + 1, row[i]);
        }
        statement.executeUpdate();
      }
    }
  }

  /** Original per-service loading pattern, for comparison of ordering and conversion. */
  private static List<TransportService> oldLoad(Connection connection, boolean modern)
      throws SQLException {
    List<TransportService> result = new ArrayList<>();
    try (Statement headers = connection.createStatement();
        Statement links = connection.createStatement();
        Statement stops = connection.createStatement();
        ResultSet rows = headers.executeQuery("SELECT * FROM " + q(HEADER))) {
      while (rows.next()) {
        int id = JDBCUtils.getInt(rows.getObject(1));
        TransportService service =
            new TransportService(
                id,
                rows.getString(2),
                (byte) JDBCUtils.getInt(rows.getObject(3)),
                (byte) JDBCUtils.getInt(rows.getObject(4)),
                JDBCUtils.getInt(rows.getObject(5)));
        try (ResultSet details =
            links.executeQuery(
                "SELECT "
                    + q(NodusC.DBF_LINK)
                    + " FROM "
                    + q(LINKS)
                    + " WHERE "
                    + q(NodusC.DBF_ID)
                    + "="
                    + id
                    + (modern ? " ORDER BY " + q(NodusC.DBF_PATH_INDEX) : ""))) {
          while (details.next()) {
            service.addChunk(GRAPHICS.get(JDBCUtils.getInt(details.getObject(1))));
          }
        }
        try (ResultSet details =
            stops.executeQuery(
                "SELECT "
                    + q(NodusC.DBF_STOP)
                    + " FROM "
                    + q(STOPS)
                    + " WHERE "
                    + q(NodusC.DBF_ID)
                    + "="
                    + id)) {
          while (details.next()) {
            service.addStop(JDBCUtils.getInt(details.getObject(1)));
          }
        }
        result.add(service);
      }
    }
    return result;
  }

  private static List<String> snapshot(List<TransportService> services) {
    List<String> rows = new ArrayList<>();
    for (TransportService service : services) {
      List<Integer> links = new ArrayList<>();
      for (OMGraphic graphic : service.getLinks()) {
        links.add(IDS.get(graphic));
      }
      rows.add(
          service.getId()
              + "|"
              + service.getName()
              + "|"
              + service.getMode()
              + "|"
              + service.getMeans()
              + "|"
              + service.getFrequency()
              + "|"
              + links
              + "|"
              + service.getStopNodes());
    }
    return rows;
  }

  private static void checkLoading(Connection connection, boolean modern) throws SQLException {
    tables(connection, modern, false);
    insertRows(
        connection,
        HEADER,
        new Object[] {2, "Ferry d'été", 2, -1, 365},
        new Object[] {1, "Rail", 1, 2, 100},
        new Object[] {3, "Empty", 1, 1, 0},
        new Object[] {1, "Duplicate ID", 1, 3, 20});
    int[][] details = {
      {2, 2, 101},
      {1, 2, 103},
      {1, 0, 101},
      {2, 0, 102},
      {1, 1, 102},
      {1, 3, 101},
      {2, 1, 9999},
      {9000, 0, 9998}
    };
    for (int[] row : details) {
      insertRows(
          connection,
          LINKS,
          modern ? new Object[] {row[0], row[1], row[2]} : new Object[] {row[0], row[2]});
    }
    insertRows(
        connection,
        STOPS,
        new Object[] {1, 20},
        new Object[] {2, 30},
        new Object[] {1, 20},
        new Object[] {1, 21},
        new Object[] {9000, 99});
    List<TransportService> expected = oldLoad(connection, modern);
    Calls calls = new Calls();
    List<TransportService> actual =
        store(calls.wrap(connection))
            .load(
                modern,
                id -> {
                  check(id != 9998, "Orphan service detail was resolved");
                  return GRAPHICS.get(id);
                });
    check(
        snapshot(expected).equals(snapshot(actual)), "Bulk load changed headers, stops or routes");
    check(calls.queries == 3, "Bulk load did not use exactly three data queries");
    calls.closed();

    // Check that legacy routes can be written to and loaded from the current path-index format.
    if (!modern) {
      Map<String, TransportService> unique = new TreeMap<>();
      for (TransportService service : actual) {
        if (!service.getName().equals("Duplicate ID")) {
          unique.put(service.getName(), service);
        }
      }
      tables(connection, true, false);
      store(connection).insert(unique, IDS::get, true, 2);
      List<String> migrated = snapshot(store(connection).load(true, GRAPHICS::get));
      check(
          migrated.equals(snapshot(new ArrayList<>(unique.values()))),
          "Legacy route migration changed order");
    }
    connection.rollback();
  }

  private static Map<String, TransportService> sample(int count, int links, int stops) {
    Map<String, TransportService> services = new TreeMap<>();
    for (int id = 1; id <= count; id++) {
      String name = "Line " + id + " d'été";
      TransportService service = new TransportService(id, "Old name", (byte) 2, (byte) -1, 365);
      for (int i = 0; i < links; i++) {
        service.addChunk(GRAPHICS.get(101 + i % 3));
      }
      for (int i = 0; i < stops; i++) {
        service.addStop(1000 + i);
      }
      services.put(name, service);
    }
    return services;
  }

  private static int count(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + q(table))) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private static void checkWriting(Connection connection, boolean batch, int limit)
      throws SQLException {
    tables(connection, true, true);
    Map<String, TransportService> services = sample(7, 4, 3);
    TransportService empty = new TransportService(8);
    services.put("Empty", empty);
    // Missing graphics are skipped, but their position still counts towards path indices.
    services.get("Line 1 d'été").getLinks().add(1, new OMPoint(1, 1));
    Calls calls = new Calls();
    calls.batchResult = Statement.SUCCESS_NO_INFO;
    store(calls.wrap(connection))
        .insert(services, graphic -> IDS.getOrDefault(graphic, -1), batch, limit);
    check(
        count(connection, HEADER) == 8
            && count(connection, LINKS) == 28
            && count(connection, STOPS) == 21,
        "Missing, duplicated or unflushed service rows");
    if (batch) {
      int size = Math.max(1, limit);
      check(calls.updates == 0 && calls.addedRows == 57, "Batch mode used individual inserts");
      check(
          calls.batches == (8 + size - 1) / size + (28 + size - 1) / size + (21 + size - 1) / size,
          "Incorrect full/partial batch count");
      check(
          calls.batchSizes.stream().allMatch(n -> n > 0 && n <= size), "Unbounded or empty batch");
    } else {
      check(
          calls.updates == 57 && calls.batches == 0 && calls.addedRows == 0,
          "Non-batch fallback used batching");
    }
    calls.closed();
    List<TransportService> loaded = store(connection).load(true, GRAPHICS::get);
    Map<String, TransportService> reloaded = new HashMap<>();
    for (TransportService service : loaded) {
      reloaded.put(service.getName(), service);
    }
    for (Map.Entry<String, TransportService> entry : services.entrySet()) {
      TransportService actual = reloaded.get(entry.getKey());
      TransportService expected = entry.getValue();
      check(
          actual != null
              && actual.getId() == expected.getId()
              && actual.getFrequency() == expected.getFrequency()
              && actual.getMeans() == expected.getMeans()
              && actual.getMode() == expected.getMode()
              && actual.getStopNodes().equals(expected.getStopNodes()),
          "Header or stops changed in round trip");
      List<OMGraphic> expectedLinks = new ArrayList<>(expected.getLinks());
      expectedLinks.removeIf(graphic -> !IDS.containsKey(graphic));
      check(actual.getLinks().equals(expectedLinks), "Repeated route links changed in round trip");
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT "
                    + q(NodusC.DBF_PATH_INDEX)
                    + " FROM "
                    + q(LINKS)
                    + " WHERE "
                    + q(NodusC.DBF_ID)
                    + "=1 ORDER BY "
                    + q(NodusC.DBF_PATH_INDEX))) {
      for (int expected : new int[] {0, 2, 3, 4}) {
        check(rows.next() && rows.getInt(1) == expected, "Missing link changed saved path indices");
      }
      check(!rows.next(), "Unexpected route occurrence");
    }
    connection.rollback();
  }

  private static void checkFailure(Connection connection, boolean batch, boolean returnedFailure)
      throws SQLException {
    tables(connection, true, true);
    insertRows(connection, HEADER, new Object[] {9000, "Existing", 1, 1, 1});
    Savepoint savepoint = connection.setSavepoint();
    Calls calls = new Calls();
    Map<String, TransportService> services = sample(7, 4, 3);
    if (returnedFailure) {
      calls.batchResult = Statement.EXECUTE_FAILED;
    } else {
      services
          .get("Line 3 d'été")
          .addStop(9999); // A later row violates the table's CHECK constraint.
    }
    boolean failed = false;
    try {
      store(calls.wrap(connection)).insert(services, IDS::get, batch, 2);
    } catch (SQLException expected) {
      failed = true;
      connection.rollback(savepoint); // The same insertion savepoint scope used by ServiceHandler.
    }
    check(failed, "A failed write was reported as successful");
    check(
        count(connection, HEADER) == 1
            && count(connection, LINKS) == 0
            && count(connection, STOPS) == 0,
        "Failed rows escaped the caller's rollback scope");
    calls.closed();
    connection.rollback();
  }

  private static void checkEmptyAndReadFailure(Connection connection) throws SQLException {
    tables(connection, true, false);
    Calls calls = new Calls();
    check(
        store(calls.wrap(connection)).load(true, GRAPHICS::get).isEmpty(),
        "Empty load is not empty");
    store(calls.wrap(connection)).insert(new TreeMap<>(), IDS::get, true, 1000);
    check(
        calls.queries == 1 && calls.batches == 0 && calls.updates == 0,
        "Empty service set did work");
    calls.closed();
    insertRows(connection, HEADER, new Object[] {1, "Test", 1, 1, 1});
    insertRows(connection, LINKS, new Object[] {1, 0, 101});
    Calls failed = new Calls();
    try {
      store(failed.wrap(connection))
          .load(
              true,
              id -> {
                throw new IllegalStateException("Bad graphic");
              });
      throw new AssertionError("Read failure was swallowed");
    } catch (IllegalStateException expected) {
      failed.closed();
    }
    connection.rollback();
  }

  private static void checkCallReduction(Connection connection) throws SQLException {
    tables(connection, true, true);
    Calls writes = new Calls();
    store(writes.wrap(connection)).insert(sample(200, 10, 5), IDS::get, true, 1000);
    check(
        writes.addedRows == 3200 && writes.batches == 4 && writes.updates == 0,
        "Large save did not reduce 3200 inserts to 4 batches");
    Calls oldReads = new Calls();
    List<TransportService> expected = oldLoad(oldReads.wrap(connection), true);
    Calls reads = new Calls();
    List<TransportService> actual = store(reads.wrap(connection)).load(true, GRAPHICS::get);
    check(oldReads.queries == 401 && reads.queries == 3, "Read query count did not stay constant");
    check(
        snapshot(expected).equals(snapshot(actual)),
        "Large bulk load differs from per-service queries");
    writes.closed();
    oldReads.closed();
    reads.closed();
    connection.rollback();
  }

  public static void main(String[] args) throws Exception {
    Path derbyLog = Files.createTempFile("nodus-service-db-", ".log");
    System.setProperty("derby.stream.error.file", derbyLog.toString());
    Map<String, String> databases = new LinkedHashMap<>();
    databases.put("HSQLDB", "jdbc:hsqldb:mem:nodus_service_test");
    databases.put("H2", "jdbc:h2:mem:nodus_service_test");
    databases.put("SQLite", "jdbc:sqlite::memory:");
    databases.put("Derby", "jdbc:derby:memory:nodus_service_test;create=true");
    try {
      for (Map.Entry<String, String> database : databases.entrySet()) {
        try (Connection connection = DriverManager.getConnection(database.getValue())) {
          check(JDBCUtils.setConnection(connection), "Could not initialize JDBC metadata");
          if (database.getKey().equals("SQLite")) {
            try (Statement statement = connection.createStatement()) {
              statement.execute("PRAGMA foreign_keys = ON");
            }
          }
          checkLoading(connection, true);
          checkLoading(connection, false);
          checkWriting(connection, true, 3);
          checkWriting(connection, true, 1000);
          checkWriting(connection, true, 0);
          checkWriting(connection, false, 3);
          checkFailure(connection, true, false);
          checkFailure(connection, false, false);
          checkFailure(connection, true, true);
          checkEmptyAndReadFailure(connection);
          checkCallReduction(connection);
          connection.rollback();
          System.out.println(database.getKey() + ": service database checks passed.");
        } finally {
          JDBCUtils.setConnection(null);
        }
      }
    } finally {
      Files.deleteIfExists(derbyLog);
    }
    System.out.println("200 services: load queries 401 -> 3; save executions 3200 -> 4 batches.");
  }
}
