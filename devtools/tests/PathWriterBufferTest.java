package edu.uclouvain.core.nodus.compute.virtual;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone buffering, concurrency and real JDBC regression checks, without project data or a GUI.
 */
public final class PathWriterBufferTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** A project supplies only properties and its disposable database connection. */
  private static final class Project extends NodusProject {
    final Connection connection;
    final String prefix;
    final int limit;

    Project(Connection connection, String prefix, int limit) {
      super(null);
      this.connection = connection;
      this.prefix = prefix;
      this.limit = limit;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return prefix;
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return prefix;
    }

    @Override
    public int getLocalProperty(String key, int fallback) {
      return key.equals(NodusC.PROP_MAX_SQL_BATCH_SIZE) ? limit : fallback;
    }
  }

  private static final class Writer extends PathWriter {
    final AtomicInteger blocks = new AtomicInteger();
    final AtomicInteger errors = new AtomicInteger();

    Writer(AssignmentParameters parameters) {
      super(parameters);
    }

    @Override
    void showWriteError(Exception error) {
      errors.incrementAndGet();
    }

    @Override
    boolean writeBuffer(PathWriterBuffer buffer) {
      check(buffer.detailCount + buffer.headers.size() <= 1000, "Unbounded worker block");
      blocks.incrementAndGet();
      return super.writeBuffer(buffer);
    }
  }

  /** Observes JDBC use and injects failures without substituting the real database. */
  private static final class Calls {
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger adds = new AtomicInteger();
    final AtomicInteger batches = new AtomicInteger();
    final AtomicInteger updates = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    final AtomicInteger prepared = new AtomicInteger();
    boolean batchSupport = true;
    volatile boolean failExecute;
    volatile boolean failedStatus;

    Connection wrap(Connection connection) {
      return (Connection)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                Object value = invoke(connection, method, args);
                if (value instanceof DatabaseMetaData) {
                  DatabaseMetaData metadata = (DatabaseMetaData) value;
                  return Proxy.newProxyInstance(
                      getClass().getClassLoader(),
                      new Class<?>[] {DatabaseMetaData.class},
                      (p, m, a) ->
                          m.getName().equals("supportsBatchUpdates")
                              ? batchSupport
                              : invoke(metadata, m, a));
                }
                if (value instanceof PreparedStatement) {
                  prepared.incrementAndGet();
                  PreparedStatement statement = (PreparedStatement) value;
                  return Proxy.newProxyInstance(
                      getClass().getClassLoader(),
                      new Class<?>[] {PreparedStatement.class},
                      (p, m, a) -> {
                        check(
                            active.incrementAndGet() == 1, "Concurrent use of the JDBC statements");
                        try {
                          String name = m.getName();
                          if (name.equals("addBatch")) adds.incrementAndGet();
                          if (name.equals("executeBatch")) batches.incrementAndGet();
                          if (name.equals("executeUpdate")) updates.incrementAndGet();
                          if (name.equals("close")) closed.incrementAndGet();
                          if (failExecute
                              && (name.equals("executeBatch") || name.equals("executeUpdate"))) {
                            throw new SQLException("Injected path write failure");
                          }
                          Object result = invoke(statement, m, a);
                          if (name.equals("executeBatch")) {
                            Arrays.fill(
                                (int[]) result,
                                failedStatus
                                    ? Statement.EXECUTE_FAILED
                                    : Statement.SUCCESS_NO_INFO);
                          }
                          return result;
                        } finally {
                          active.decrementAndGet();
                        }
                      });
                }
                return value;
              });
    }
  }

  private static Writer writer(
      Connection connection,
      String prefix,
      int limit,
      boolean paths,
      boolean details,
      boolean durations) {
    AssignmentParameters parameters =
        new AssignmentParameters(new Project(connection, prefix, limit));
    parameters.setScenario(prefix.equals("ref") ? 1 : 0);
    parameters.setSavePaths(paths);
    parameters.setDetailedPaths(details);
    parameters.setDurationFunctions(durations);
    parameters.getComputingTimes().startAssignment();
    return new Writer(parameters);
  }

  private static String table(String prefix, boolean details) {
    return JDBCUtils.getQuotedCompliantIdentifier(
        prefix
            + (prefix.equals("ref") ? "1" : "0")
            + (details ? NodusC.SUFFIX_DETAIL : NodusC.SUFFIX_HEADER));
  }

  private static VirtualLink link(int id, boolean reverse) {
    VirtualNode begin =
        new VirtualNode(1, reverse ? 20 : 10, id, (byte) 2, (byte) 3, (short) 0, 0, 0);
    VirtualNode end =
        new VirtualNode(2, reverse ? 10 : 20, id, (byte) 2, (byte) 3, (short) 0, 0, 0);
    return new VirtualLink(id, 0, 0, begin, end, VirtualLink.TYPE_MOVE);
  }

  private static PathWeights weights(int seed) {
    PathWeights w = new PathWeights();
    w.length = seed + 1.2345f;
    w.ldCost = 0.0005;
    w.ulCost = -1.2345;
    w.trCost = seed + 2.3456;
    w.tpCost = 4.5678;
    w.stpCost = 5.6789;
    w.swCost = 6.7891;
    w.mvCost = 7.8912;
    w.ldDuration = 1.2345f;
    w.ulDuration = 2.3456f;
    w.trDuration = 3.4567f;
    w.tpDuration = 4.5678f;
    w.stpDuration = 5.6789f;
    w.swDuration = 6.7891f;
    w.mvDuration = 12.6f;
    return w;
  }

  private static boolean header(PathWriterBuffer buffer, int iteration, int id, PathWeights w) {
    return buffer.savePathHeader(
        iteration,
        new ODCell(2, id + 1, id + 2, 1, 123),
        123.4567,
        w,
        (byte) 1,
        (byte) 2,
        (byte) 3,
        (byte) 4,
        5,
        id);
  }

  private static List<String> rows(Connection connection, String prefix, boolean details)
      throws SQLException {
    List<String> result = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT * FROM " + table(prefix, details))) {
      while (rows.next()) {
        StringBuilder row = new StringBuilder();
        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
          row.append(rows.getBigDecimal(i).stripTrailingZeros().toPlainString()).append('|');
        }
        result.add(row.toString());
      }
    }
    result.sort(String::compareTo);
    return result;
  }

  private static void persistence(
      Connection connection, boolean batch, int limit, boolean durations) throws Exception {
    Calls calls = new Calls();
    calls.batchSupport = batch;
    Connection monitored = calls.wrap(connection);
    check(JDBCUtils.setConnection(monitored), "JDBC metadata initialization failed");
    Writer direct = writer(monitored, "ref", limit, true, true, durations);
    Writer buffered = writer(monitored, "buf", limit, true, true, durations);
    PathWriterBuffer buffer = buffered.newBuffer();
    for (int id = 0; id < 31; id++) {
      for (VirtualLink link :
          new VirtualLink[] {link(100 + id, false), link(100 + id, true), link(100 + id, false)}) {
        direct.savePathLink(link, id);
        buffer.savePathLink(link, id);
      }
      PathWeights a = weights(id), b = weights(id);
      check(
          direct.savePathHeader(
              2,
              new ODCell(2, id + 1, id + 2, 1, 123),
              123.4567,
              a,
              (byte) 1,
              (byte) 2,
              (byte) 3,
              (byte) 4,
              5,
              id),
          "Direct header failed");
      check(header(buffer, 2, id, b), "Buffered header failed");
      check(a.mvDuration == b.mvDuration, "Legacy duration behavior changed");
      b.mvCost = 999999; // Queued rows must not retain the mutable weights object.
    }
    // Two dynamic time slices can legitimately use the same explicit path ID.
    check(header(buffer, 3, 0, weights(0)), "Repeated ID header failed");
    check(
        direct.savePathHeader(
            3,
            new ODCell(2, 1, 2, 1, 123),
            123.4567,
            weights(0),
            (byte) 1,
            (byte) 2,
            (byte) 3,
            (byte) 4,
            5,
            0),
        "Direct repeated ID failed");
    check(buffer.flush(), "Partial worker block failed");
    check(buffered.close() && direct.close() && buffered.close(), "Close failed");
    check(
        rows(connection, "buf", false).equals(rows(connection, "ref", false)),
        "Saved headers differ");
    check(
        rows(connection, "buf", true).equals(rows(connection, "ref", true)), "Saved links differ");
    check(rows(connection, "buf", true).size() == 93, "Repeated links were lost");
    check(calls.closed.get() == calls.prepared.get(), "Statements leaked on close");
    check(
        batch ? calls.updates.get() == 0 : calls.batches.get() == 0,
        "Wrong JDBC batch/fallback mode");
    check(!header(buffer, 4, 1, weights(1)), "Closed writer accepted a header");
    buffered.discard();
    direct.discard();
  }

  private static void concurrency(Connection connection) throws Exception {
    Calls calls = new Calls();
    Connection monitored = calls.wrap(connection);
    check(JDBCUtils.setConnection(monitored), "JDBC metadata initialization failed");
    Writer writer = writer(monitored, "con", 10000, true, true, true);
    ExecutorService workers = Executors.newFixedThreadPool(4);
    try {
      // Holding the JDBC lock must not block local row preparation, including decimal formatting.
      CountDownLatch prepared = new CountDownLatch(1);
      Future<?> local;
      synchronized (writer) {
        local =
            workers.submit(
                () -> {
                  PathWriterBuffer buffer = writer.newBuffer();
                  buffer.savePathLink(link(50, false));
                  check(
                      buffer.savePathHeader(
                          1,
                          new ODCell(1, 50, 51, 1),
                          1,
                          weights(0),
                          (byte) 2,
                          (byte) 3,
                          (byte) 2,
                          (byte) 3,
                          0),
                      "Local header failed");
                  prepared.countDown();
                  check(buffer.flush(), "Local flush failed");
                });
        check(
            prepared.await(5, TimeUnit.SECONDS), "Row preparation acquired the shared writer lock");
      }
      local.get(10, TimeUnit.SECONDS);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> jobs = new ArrayList<>();
      for (int worker = 0; worker < 4; worker++) {
        final int index = worker;
        jobs.add(
            workers.submit(
                () -> {
                  start.await();
                  PathWriterBuffer buffer = writer.newBuffer();
                  for (int path = 0; path < 100; path++) {
                    int origin = 1000 + 100 * index + path;
                    for (int i = 0; i < 20; i++) buffer.savePathLink(link(origin, (i & 1) != 0));
                    check(
                        buffer.savePathHeader(
                            1,
                            new ODCell(index + 1, origin, origin + 1, 1),
                            123.4567,
                            weights(path),
                            (byte) 2,
                            (byte) 3,
                            (byte) 2,
                            (byte) 3,
                            0),
                        "Concurrent header failed");
                  }
                  check(buffer.flush(), "Final worker block failed");
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> job : jobs) job.get(30, TimeUnit.SECONDS);
      check(writer.blocks.get() == 13, "Expected 13 block handoffs for 8402 rows");
      check(writer.close(), "Concurrent writer close failed");
      check(rows(connection, "con", false).size() == 401, "Concurrent headers lost");
      check(rows(connection, "con", true).size() == 8001, "Concurrent details lost");
      String path = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_PATH_INDEX);
      String origin = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN);
      String link = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_LINK);
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM "
                      + table("con", true)
                      + " d LEFT JOIN "
                      + table("con", false)
                      + " h ON d."
                      + path
                      + "=h."
                      + path
                      + " WHERE h."
                      + path
                      + " IS NULL OR ABS(d."
                      + link
                      + ")<>h."
                      + origin)) {
        result.next();
        check(result.getInt(1) == 0, "Concurrent details attached to another worker's header");
      }
      check(calls.closed.get() == calls.prepared.get(), "Concurrent writer leaked statements");
      writer.discard();
    } finally {
      workers.shutdownNow();
    }
  }

  private static void lifecycle(Connection connection) throws Exception {
    JDBCUtils.setConnection(connection);
    Writer writer = writer(connection, "life", 1000, true, false, true);
    PathWriterBuffer firstJob = writer.newBuffer();
    firstJob.savePathLink(null); // No details: no graphic resolution or JDBC call.
    check(header(firstJob, 1, 10, weights(0)) && firstJob.flush(), "First job flush failed");
    PathWriterBuffer nextJob = writer.newBuffer();
    check(header(nextJob, 2, 20, weights(0)) && nextJob.flush(), "Second job flush failed");
    // Derby does not implement the ROUND function used by the existing equilibrium SQL.
    // Check its final partial rows through close; run the unchanged split SQL on the other engines.
    if (!connection.getMetaData().getDatabaseProductName().contains("Derby")) {
      writer.splitPaths(2, 0.25);
      List<String> saved = rows(connection, "life", false);
      check(
          saved.size() == 2
              && saved.get(0).contains("|92.593|")
              && saved.get(1).contains("|30.864|"),
          "Equilibrium split did not see the final partial job blocks");
    }
    check(writer.close(), "Header-only close failed");
    check(rows(connection, "life", false).size() == 2, "Final partial rows were lost");
    writer.discard();

    Calls calls = new Calls();
    Connection monitored = calls.wrap(connection);
    JDBCUtils.setConnection(monitored);
    Writer disabled = writer(monitored, "off", 1, false, true, true);
    PathWriterBuffer off = disabled.newBuffer();
    off.savePathLink(null);
    check(
        off.savePathHeader(1, null, 1, null, (byte) 0, (byte) 0, (byte) 0, (byte) 0, 0),
        "Disabled header failed");
    check(off.flush() && disabled.close(), "Disabled close failed");
    check(
        calls.prepared.get() == 0 && disabled.blocks.get() == 0,
        "Disabled saving did database work");

    Writer canceled = writer(monitored, "cancel", 1000, true, true, true);
    PathWriterBuffer pending = canceled.newBuffer();
    check(header(pending, 1, 1, weights(0)), "Pending header failed");
    pending.savePathLink(link(10, false), 1);
    pending.clear();
    check(pending.flush() && calls.adds.get() == 0, "Cleared rows were submitted");
    check(header(pending, 1, 2, weights(0)), "Second pending header failed");
    canceled.discard();
    check(!pending.flush() && calls.adds.get() == 0, "Discard flushed local rows");
    check(!JDBCUtils.tableExists("cancel0" + NodusC.SUFFIX_HEADER), "Discard retained tables");
  }

  private static void failure(Connection connection, boolean batch, boolean status, boolean atClose)
      throws Exception {
    Calls calls = new Calls();
    calls.batchSupport = batch;
    Connection monitored = calls.wrap(connection);
    JDBCUtils.setConnection(monitored);
    Writer writer = writer(monitored, "fail", 3, true, true, true);
    PathWriterBuffer buffer = writer.newBuffer();
    check(header(buffer, 1, 1, weights(0)), "Unexpected early header failure");
    if (atClose) check(buffer.flush(), "Partial header handoff failed");
    calls.failExecute = !status;
    calls.failedStatus = status;
    if (atClose) {
      check(!writer.close(), "Final JDBC batch failure was hidden");
    } else {
      check(header(buffer, 1, 2, weights(0)), "Unexpected second header failure");
      check(!header(buffer, 1, 3, weights(0)), "Full block failure was hidden");
      check(!writer.newBuffer().flush(), "Other worker did not observe failure");
      check(!writer.close(), "Failed writer closed successfully");
    }
    check(writer.errors.get() == 1, "Failure was not reported once");
    check(calls.prepared.get() == calls.closed.get(), "Failure leaked statements");
    writer.discard();
  }

  private static void invalidHeader(Connection connection) throws Exception {
    JDBCUtils.setConnection(connection);
    Writer writer = writer(connection, "invalid", 1000, true, true, true);
    PathWriterBuffer buffer = writer.newBuffer();
    check(
        !buffer.savePathHeader(
            1,
            new ODCell(1, 1, 2, 1),
            Double.NaN,
            weights(0),
            (byte) 1,
            (byte) 1,
            (byte) 1,
            (byte) 1,
            0),
        "NaN quantity was accepted");
    check(
        writer.errors.get() == 1 && !writer.newBuffer().flush(), "Invalid row did not stop output");
    check(!writer.close(), "Invalid writer closed successfully");
    writer.discard();
  }

  /**
   * Original explicit bindings, retained independently to check numeric conversion and column
   * order.
   */
  private static void original(
      PreparedStatement p,
      DecimalFormat df,
      ODCell od,
      double quantity,
      PathWeights w,
      boolean durations)
      throws SQLException {
    if (!durations) w.mvDuration = Math.round(w.mvDuration);
    int i = 1;
    p.setInt(i++, od.getGroup());
    p.setInt(i++, od.getOriginNodeId());
    p.setInt(i++, od.getDestinationNodeId());
    p.setInt(i++, od.getStartingTime() / 60);
    p.setInt(i++, 7);
    p.setDouble(i++, Double.parseDouble(df.format(quantity)));
    p.setFloat(i++, Float.parseFloat(df.format(w.length)));
    p.setDouble(i++, Double.parseDouble(df.format(w.ldCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.ulCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.trCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.tpCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.stpCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.swCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.mvCost)));
    p.setDouble(i++, Double.parseDouble(df.format(w.ldDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.ulDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.trDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.tpDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.stpDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.swDuration)));
    p.setDouble(i++, Double.parseDouble(df.format(w.mvDuration)));
    p.setInt(i++, 1);
    p.setInt(i++, 2);
    p.setInt(i++, 3);
    p.setInt(i++, 4);
    p.setInt(i++, 5);
    p.setInt(i++, 6);
  }

  private static PreparedStatement capture(List<String> bindings) {
    return (PreparedStatement)
        Proxy.newProxyInstance(
            PathWriterBufferTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              check(method.getName().startsWith("set"), "Unexpected capture call");
              bindings.add(method.getName() + ":" + args[0] + ":" + args[1]);
              return null;
            });
  }

  private static void rounding() throws Exception {
    Locale previous = Locale.getDefault();
    try {
      for (Locale locale : new Locale[] {Locale.US, Locale.FRANCE}) {
        Locale.setDefault(locale);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat reference = new DecimalFormat("0.000", symbols);
        DecimalFormat current = PathWriterBuffer.newFormat();
        Random random = new Random(7189);
        double[] special = {
          -0.0,
          0.0,
          0.0005,
          -0.0005,
          1.2345,
          1.2355,
          9999999.9995,
          Double.MIN_VALUE,
          Double.NaN,
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY
        };
        for (int i = 0; i < 10000; i++) {
          double value = i < special.length ? special[i] : (random.nextDouble() - 0.5) * 1.0e7;
          PathWeights a = weights(i), b = weights(i);
          a.mvCost = b.mvCost = value;
          List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
          ODCell demand = new ODCell(2, 3, 4, 1, 123);
          boolean oldFailed = false, newFailed = false;
          try {
            original(capture(expected), reference, demand, 2.3456, a, (i & 1) == 0);
          } catch (NumberFormatException e) {
            oldFailed = true;
          }
          try {
            PathWriterBuffer.Header.prepare(
                    current,
                    (i & 1) == 0,
                    7,
                    demand,
                    2.3456,
                    b,
                    (byte) 1,
                    (byte) 2,
                    (byte) 3,
                    (byte) 4,
                    5,
                    6)
                .bind(capture(actual));
          } catch (NumberFormatException e) {
            newFailed = true;
          }
          check(
              oldFailed == newFailed && (oldFailed || expected.equals(actual)),
              "Numeric binding changed for " + value);
        }
      }
    } finally {
      Locale.setDefault(previous);
    }
  }

  public static void main(String[] args) throws Exception {
    rounding();
    Path derbyLog = Files.createTempFile("nodus-path-buffer-", ".log");
    System.setProperty("derby.stream.error.file", derbyLog.toString());
    String[] urls = {
      "jdbc:hsqldb:mem:nodus_path_buffer",
      "jdbc:h2:mem:nodus_path_buffer",
      "jdbc:sqlite::memory:",
      "jdbc:derby:memory:nodus_path_buffer;create=true"
    };
    try {
      for (String url : urls) {
        try (Connection connection = DriverManager.getConnection(url)) {
          connection.setAutoCommit(false);
          persistence(connection, true, 3, true);
          persistence(connection, true, 1000, false);
          persistence(connection, true, 0, true);
          persistence(connection, false, 7, true);
          concurrency(connection);
          lifecycle(connection);
          invalidHeader(connection);
          failure(connection, true, false, false);
          failure(connection, true, true, false);
          failure(connection, true, false, true);
          failure(connection, false, false, false);
          connection.rollback();
          System.out.println(url.split(":")[1] + ": path writer buffering checks passed.");
        } finally {
          JDBCUtils.setConnection(null);
        }
      }
      System.out.println("Concurrent fixture: 8,402 path rows, 13 shared-writer block handoffs.");
      System.out.println("20,000 header conversions match the previous bindings.");
    } finally {
      Files.deleteIfExists(derbyLog);
    }
  }
}
