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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.uclouvain.core.nodus.NodusC;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Runs actual listeners in separate JVMs to isolate H2's process-wide settings. */
class LocalDatabaseServerTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> localServersPreserveJdbcAccessAndHandleFailures() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int engine : new int[] {JDBCUtils.DB_HSQLDB, JDBCUtils.DB_H2}) {
      for (String mode : List.of("connect", "conflict")) {
        tests.add(DynamicTest.dynamicTest(engine + "/" + mode, () -> runChild(engine, mode)));
      }
    }
    return tests;
  }

  @Test
  void h2RejectsUnsafeInitializationAndCleansUpFailedAuthentication() throws Exception {
    runChild(JDBCUtils.DB_H2, "early-init");
    runChild(JDBCUtils.DB_H2, "authentication");
    runChild(JDBCUtils.DB_H2, "ownership");
  }

  @TestFactory
  List<DynamicTest> explicitJdbcConfigurationsNeverStartLocalServers() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String url :
        List.of(
            "jdbc:mariadb://localhost/calibratedod?sessionVariables=default_storage_engine=MYISAM",
            "jdbc:mariadb://database.example/calibratedod",
            "jdbc:mysql://database.example/project",
            "jdbc:postgresql://database.example/project",
            "jdbc:h2:tcp://database.example/project",
            "jdbc:hsqldb:hsql://localhost/project")) {
      tests.add(
          DynamicTest.dynamicTest(
              url,
              () -> {
                Properties properties = new Properties();
                properties.setProperty(NodusC.PROP_JDBC_URL, url);
                properties.setProperty(NodusC.PROP_JDBC_USERNAME, "nodus");
                properties.setProperty(NodusC.PROP_JDBC_PASSWORD, "nodus");
                Properties original = (Properties) properties.clone();
                String h2Binding = System.getProperty("h2.bindAddress");
                for (int engine : new int[] {JDBCUtils.DB_HSQLDB, JDBCUtils.DB_H2}) {
                  // Even an unusable port must be ignored when the URL specifies an external
                  // server.
                  assertNull(
                      LocalDatabaseServer.start(
                          properties, engine, directory, "project", -1, "nodus", "nodus"));
                }
                assertEquals(original, properties);
                assertEquals(h2Binding, System.getProperty("h2.bindAddress"));
                try (var files = Files.list(directory)) {
                  assertEquals(0, files.count());
                }
              }));
    }
    return tests;
  }

  private void runChild(int engine, String mode) throws Exception {
    Path work = Files.createTempDirectory(directory, "server-");
    Path output = work.resolve("output.log");
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Process child =
        new ProcessBuilder(
                java,
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                Worker.class.getName(),
                Integer.toString(engine),
                mode,
                work.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Server test timed out: " + mode);
      String diagnostics = Files.readString(output);
      assertEquals(0, child.exitValue(), diagnostics);
      if (engine == JDBCUtils.DB_HSQLDB) {
        if (mode.equals("connect")) {
          assertTrue(diagnostics.isBlank(), diagnostics);
        } else if (mode.equals("conflict")) {
          assertTrue(diagnostics.contains("java.net.BindException"), diagnostics);
        }
      }
    } finally {
      child.destroyForcibly();
      child.waitFor(5, TimeUnit.SECONDS);
    }
  }

  /** Isolated server exercise; deliberately uses TCP JDBC just like the R and Python scripts. */
  public static class Worker {
    /**
     * Runs one server scenario in a fresh JVM, with failures reported through its exit status.
     *
     * @param args engine identifier, scenario name, and temporary database directory
     * @throws Exception if the server or an assertion fails
     */
    public static void main(String[] args) throws Exception {
      int engine = Integer.parseInt(args[0]);
      String mode = args[1];
      Path directory = Path.of(args[2]);
      int port;
      try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(HOST))) {
        port = socket.getLocalPort();
        if (mode.equals("conflict")) {
          assertThrows(Exception.class, () -> start(engine, directory, port, ""));
          assertFalse(socket.isClosed());
          return;
        }
      }
      if (mode.equals("early-init")) {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:early")) {
          assertTrue(connection.isValid(1));
        }
        assertThrows(SQLException.class, () -> start(engine, directory, port, ""));
      } else if (mode.equals("authentication")) {
        LocalDatabaseServer.configureH2Binding();
        try (Connection connection =
            DriverManager.getConnection(
                "jdbc:h2:file:" + directory.resolve("project"), "", "secret")) {
          assertTrue(connection.isValid(1));
        }
        assertThrows(SQLException.class, () -> start(engine, directory, port, "wrong"));
        assertPortReleased(port);
        try (LocalDatabaseServer server = start(engine, directory, port, "secret");
            Connection connection =
                DriverManager.getConnection(
                    url(engine, directory, port, HOST), user(engine), "secret")) {
          assertTrue(connection.isValid(1));
        }
      } else if (mode.equals("ownership")) {
        try (LocalDatabaseServer first = start(engine, directory, port, "")) {
          assertThrows(Exception.class, () -> start(engine, directory, port, ""));
          try (Connection connection = connect(engine, directory, port, HOST)) {
            assertTrue(connection.isValid(1));
          }
        }
      } else {
        exercise(engine, directory, port);
      }
      assertPortReleased(port);
    }

    private static final String HOST = LocalDatabaseServer.HOST;

    private static LocalDatabaseServer start(int engine, Path directory, int port, String password)
        throws Exception {
      return LocalDatabaseServer.start(
          new Properties(),
          engine,
          directory,
          "project",
          port,
          user(engine),
          password);
    }

    private static String user(int engine) {
      return engine == JDBCUtils.DB_HSQLDB ? "SA" : "";
    }

    private static String url(int engine, Path directory, int port, String host) {
      if (engine == JDBCUtils.DB_HSQLDB) {
        return "jdbc:hsqldb:hsql://" + host + ":" + port + "/project";
      }
      return "jdbc:h2:tcp://" + host + ":" + port + "/" + directory.resolve("project");
    }

    private static Connection connect(int engine, Path directory, int port, String host)
        throws SQLException {
      return DriverManager.getConnection(url(engine, directory, port, host), user(engine), "");
    }

    private static void exercise(int engine, Path directory, int port) throws Exception {
      try (LocalDatabaseServer server = start(engine, directory, port, "")) {
        assertNotReachableOnNetworkInterfaces(port);
        try (Connection first = connect(engine, directory, port, HOST);
            Connection second = connect(engine, directory, port, "localhost");
            Statement write = first.createStatement();
            Statement read = second.createStatement()) {
          write.executeUpdate("CREATE TABLE script_input (id INTEGER)");
          write.executeUpdate("INSERT INTO script_input VALUES (42)");
          try (var rows = read.executeQuery("SELECT id FROM script_input")) {
            assertTrue(rows.next());
            assertEquals(42, rows.getInt(1));
          }
          if (engine == JDBCUtils.DB_H2) {
            String other = url(engine, directory, port, HOST).replace("/project", "/unrelated");
            assertThrows(
                SQLException.class,
                () -> {
                  try (Connection unexpected = DriverManager.getConnection(other, "", "")) {
                    fail("TCP clients must not create another database: " + unexpected);
                  }
                });
            assertFalse(Files.exists(directory.resolve("unrelated.mv.db")));
          }
          write.execute("SHUTDOWN COMPACT");
        }
      }
    }

    private static void assertNotReachableOnNetworkInterfaces(int port) throws Exception {
      for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!network.isUp() || network.isLoopback()) {
          continue;
        }
        for (InetAddress address : Collections.list(network.getInetAddresses())) {
          if (!(address instanceof java.net.Inet4Address)) {
            continue;
          }
          try (Socket socket = new Socket()) {
            assertThrows(
                java.io.IOException.class,
                () -> socket.connect(new InetSocketAddress(address, port), 500),
                "Listener accessible through " + address);
          }
        }
      }
    }

    private static void assertPortReleased(int port) throws Exception {
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(HOST, port));
      }
    }
  }
}
