package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Exercises the actual AssignmentWorker job loop, including its final flush and cancellation paths.
 */
public final class AssignmentPathBufferWorkerTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static final class Project extends NodusProject {
    private final Connection connection;

    Project(Connection connection) {
      super(null);
      this.connection = connection;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return "worker";
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return "worker";
    }

    @Override
    public int getLocalProperty(String key, int fallback) {
      return fallback;
    }
  }

  private static final class Job extends Assignment {
    final PathWriter output;
    final VirtualNetwork network;
    AssignmentWorker[] workers;

    Job(AssignmentParameters parameters, VirtualNetwork network) {
      super(parameters);
      this.network = network;
      output = new PathWriter(parameters);
    }

    @Override
    public boolean assign() {
      throw new AssertionError("No real assignment is needed");
    }

    @Override
    public PathWriter getPathWriter() {
      return output;
    }

    @Override
    public VirtualNetwork getVNet() {
      return network;
    }

    @Override
    public AssignmentWorker[] getAssignmentWorkers() {
      return workers;
    }
  }

  private static final class Worker extends AssignmentWorker {
    final int outcome;
    int jobs;
    long databaseTime;

    Worker(WorkQueue queue, int outcome) {
      super(queue);
      this.outcome = outcome;
    }

    @Override
    boolean doAssignment() {
      jobs++;
      PathWeights weights = new PathWeights();
      weights.mvCost = 1.23456;
      check(
          pathBuffer.savePathHeader(
              iteration,
              new ODCell(1, jobs, jobs + 1, 1),
              1,
              weights,
              (byte) 1,
              (byte) 1,
              (byte) 1,
              (byte) 1,
              0),
          "Header preparation failed");
      if (outcome == 2) requestCancel();
      if (outcome == 3) throw new IllegalStateException("Expected worker exception");
      return outcome != 1;
    }

    @Override
    public void run() {
      super.run();
      databaseTime = assignmentParameters.getComputingTimes().getThreadDatabaseTime();
    }

    boolean flushAfterCompletion() {
      return pathBuffer.flush();
    }
  }

  private static VirtualNetwork network() throws Exception {
    // Bypass project/layer initialization: this test only needs the group list read by the job
    // loop.
    Class<?> allocatorClass = Class.forName("sun.misc.Unsafe");
    Field field = allocatorClass.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Object allocator = field.get(null);
    VirtualNetwork network =
        (VirtualNetwork)
            allocatorClass
                .getMethod("allocateInstance", Class.class)
                .invoke(allocator, VirtualNetwork.class);
    Field groups = VirtualNetwork.class.getDeclaredField("groups");
    groups.setAccessible(true);
    groups.set(network, new byte[] {1});
    return network;
  }

  private static void run(Connection connection, int outcome) throws Exception {
    AssignmentParameters parameters = new AssignmentParameters(new Project(connection));
    parameters.setScenario(0);
    parameters.setSavePaths(true);
    parameters.setDurationFunctions(true);
    parameters.getComputingTimes().startAssignment();
    Job job = new Job(parameters, network());
    WorkQueue queue = new WorkQueue();
    Worker worker = new Worker(queue, outcome);
    job.workers = new AssignmentWorker[] {worker};
    queue.addWork(new AssignmentWorkerParameters(job, (byte) 0, (byte) 0, 1));
    if (outcome == 0) queue.addWork(new AssignmentWorkerParameters(job, (byte) 0, (byte) 0, 2));
    queue.addWork(WorkQueue.NO_MORE_WORK);
    PrintStream previousError = System.err;
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    try (PrintStream captured = new PrintStream(error)) {
      if (outcome == 3) System.setErr(captured);
      worker.start();
      worker.join(10000);
    } finally {
      System.setErr(previousError);
    }
    check(!worker.isAlive(), "Worker did not terminate");
    check(worker.jobs == (outcome == 0 ? 2 : 1), "Wrong job count");
    check(worker.isCancelled() == (outcome != 0), "Cancellation was not propagated");
    check(worker.databaseTime > 0, "Buffered output was not included in database timing");
    if (outcome == 3)
      check(error.toString().contains("Expected worker exception"), "Exception was swallowed");
    // A failed worker's finally block must have removed its unfinished row before another flush.
    check(worker.flushAfterCompletion(), "Unexpected writer failure");
    check(job.output.close(), "Writer close failed");
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT COUNT(*) FROM "
                    + JDBCUtils.getQuotedCompliantIdentifier("worker0" + NodusC.SUFFIX_HEADER))) {
      rows.next();
      check(
          rows.getInt(1) == (outcome == 0 ? 2 : 0),
          "Job boundary lost or incorrectly flushed rows");
    }
    job.output.discard();
  }

  public static void main(String[] args) throws Exception {
    boolean previousAudit = NodusC.displayComputingTimes;
    NodusC.displayComputingTimes = true;
    try (Connection connection =
        DriverManager.getConnection("jdbc:hsqldb:mem:nodus_path_workers")) {
      connection.setAutoCommit(false);
      JDBCUtils.setConnection(connection);
      for (int outcome = 0; outcome < 4; outcome++) run(connection, outcome);
      connection.rollback();
    } finally {
      NodusC.displayComputingTimes = previousAudit;
      JDBCUtils.setConnection(null);
    }
    System.out.println("Assignment worker path-buffer lifecycle checks passed.");
  }
}
