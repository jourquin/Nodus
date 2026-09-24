package edu.uclouvain.core.nodus.database.gui;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.BasicI18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.GridSwing;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Exercises export dispatch and cancellation with real temporary files and simulated dialog
 * choices.
 */
public final class SQLExportOverwriteTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = SQLConsole.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object call(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    Method method = SQLConsole.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException ex) {
      throw new AssertionError("Console method failed: " + name, ex.getCause());
    }
  }

  private static final class Project extends NodusProject {
    final Path directory;
    final Connection connection;
    NodusEsriLayer[] links = new NodusEsriLayer[0];
    NodusEsriLayer[] nodes = new NodusEsriLayer[0];

    Project(Path directory, Connection connection) {
      super(null);
      this.directory = directory;
      this.connection = connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return directory.toString() + "/";
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public NodusEsriLayer[] getLinkLayers() {
      return links;
    }

    @Override
    public NodusEsriLayer[] getNodeLayers() {
      return nodes;
    }
  }

  private static final class Console extends SQLConsole {
    int answer;
    int prompts;
    String message;
    boolean useRealDialog;
    Properties preferences;

    Console() {
      super(null);
    }

    @Override
    protected int showOverwriteDialog(String message) {
      check(SwingUtilities.isEventDispatchThread(), "Dialog was called off the EDT");
      this.message = message;
      prompts++;
      if (useRealDialog) return super.showOverwriteDialog(message);
      return answer;
    }

    boolean export(String operation) throws Exception {
      return (Boolean)
          call(
              this,
              "importExport",
              new Class<?>[] {String.class, String.class},
              operation.toLowerCase(Locale.ROOT) + " sample",
              operation);
    }
  }

  private static final class Layer extends NodusEsriLayer {
    int extractions;

    @Override
    public String getTableName() {
      return "source";
    }

    @Override
    public boolean extract(String name, String where) {
      extractions++;
      return true;
    }
  }

  private static Console console(Project project) throws Exception {
    // Skip the constructor that opens a JFrame; retain the real command parser/dispatch/exporters.
    Class<?> type = Class.forName("sun.misc.Unsafe");
    Field field = type.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Console console =
        (Console)
            type.getMethod("allocateInstance", Class.class).invoke(field.get(null), Console.class);
    NodusMapPanel panel =
        (NodusMapPanel)
            type.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), NodusMapPanel.class);
    console.preferences = new Properties();
    console.preferences.setProperty(NodusC.PROP_DISPLAY_FULL_PATH, "true");
    Field properties = NodusMapPanel.class.getDeclaredField("nodusProperties");
    properties.setAccessible(true);
    properties.set(panel, console.preferences);
    set(console, "nodusMapPanel", panel);
    set(console, "nodusProject", project);
    set(console, "jdbcConnection", project.connection);
    set(console, "gridResultArea", new GridSwing());
    set(console, "recentQueries", new String[24]);
    set(console, "menuRecent", new JMenu());
    set(console, "resultPanel", new JPanel());
    set(console, "txtResultScroll", new JScrollPane());
    return console;
  }

  private static void exports(Console console, Project project) throws Exception {
    String[] operations = {"EXPORTDBF", "EXPORTCSV", "EXPORTCSVH", "EXPORTXLS", "EXPORTXLSX"};
    String[] extensions = {".dbf", ".csv", ".csv", ".xls", ".xlsx"};
    for (int i = 0; i < operations.length; i++) {
      Path target = project.directory.resolve("sample" + extensions[i]);
      Files.deleteIfExists(target);
      int before = console.prompts;
      console.answer = JOptionPane.NO_OPTION;
      check(console.export(operations[i]), "New-file export failed: " + operations[i]);
      check(console.prompts == before, "Prompted for a new file");
      byte[] original = Files.readAllBytes(target);
      check(original.length > 0, "Empty export");
      for (int answer : new int[] {JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION}) {
        console.answer = answer;
        check(!console.export(operations[i]), "Declined export reported success");
        check(Arrays.equals(original, Files.readAllBytes(target)), "Declined export changed file");
        check(console.message.contains(target.toString()), "Prompt omitted the destination path");
      }
      console.answer = JOptionPane.YES_OPTION;
      check(console.export(operations[i]), "Confirmed export failed: " + operations[i]);
      check(console.prompts == before + 3, "Expected one prompt per overwrite");
    }
    check(
        call(
                console,
                "getExportFile",
                new Class<?>[] {String.class, String.class},
                "IMPORTCSV",
                "sample")
            == null,
        "Import incorrectly treated as a file export");

    console.answer = JOptionPane.NO_OPTION;
    check(
        !console.runBatch(new String[] {"exportcsv sample", "UPDATE sample SET num=999"}),
        "Cancelled batch reported success");
    try (Statement statement = project.connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT num FROM sample")) {
      check(rs.next() && rs.getInt(1) == 7, "Batch continued after cancelled export");
    }
    console.typeOfResultFormat = 0;
  }

  private static void extraction(Console console, Project project) throws Exception {
    Layer layer = new Layer();
    for (boolean links : new boolean[] {true, false}) {
      project.links = links ? new NodusEsriLayer[] {layer} : new NodusEsriLayer[0];
      project.nodes = links ? new NodusEsriLayer[0] : new NodusEsriLayer[] {layer};
      for (String suffix : new String[] {".shp", ".shx", ".dbf"}) {
        Path target = project.directory.resolve("extracted" + suffix);
        Files.writeString(target, "original");
        console.answer = JOptionPane.NO_OPTION;
        int before = layer.extractions;
        check(
            !(Boolean)
                call(
                    console,
                    "extractShp",
                    new Class<?>[] {String.class},
                    "EXTRACTSHP FROM source TO extracted WHERE num=7"),
            "Declined extraction succeeded");
        check(
            layer.extractions == before && Files.readString(target).equals("original"),
            "Declined extraction reached writer");
        check(console.message.contains(target.toString()), "Missing shapefile component in prompt");
      }
      int before = console.prompts;
      console.answer = JOptionPane.YES_OPTION;
      check(
          (Boolean)
              call(
                  console,
                  "extractShp",
                  new Class<?>[] {String.class},
                  "EXTRACTSHP FROM source TO extracted WHERE num=7"),
          "Confirmed extraction failed");
      check(console.prompts == before + 1, "Extraction prompted separately for each component");
      for (String suffix : new String[] {".shp", ".shx", ".dbf"}) {
        check(console.message.contains("extracted" + suffix), "Grouped prompt omitted component");
        Files.delete(project.directory.resolve("extracted" + suffix));
      }
      int extracted = layer.extractions;
      check(
          !(Boolean)
              call(
                  console,
                  "extractShp",
                  new Class<?>[] {String.class},
                  "EXTRACTSHP FROM source TO source WHERE num=7"),
          "Self-extraction accepted");
      check(layer.extractions == extracted, "Self-extraction reached writer");
      check(
          (Boolean)
              call(
                  console,
                  "extractShp",
                  new Class<?>[] {String.class},
                  "EXTRACTSHP FROM source TO fresh WHERE num=7"),
          "New extraction failed");
      check(console.prompts == before + 1, "New extraction prompted");
    }
  }

  private static void localizationAndEdt(Console console, Path directory) throws Exception {
    Path target = directory.resolve("results.txt");
    Files.writeString(target, "original");
    for (Locale locale : new Locale[] {Locale.US, Locale.FRANCE}) {
      BasicI18n translations = new BasicI18n(locale);
      set(null, "i18n", translations);
      console.answer = JOptionPane.CLOSED_OPTION;
      for (boolean fullPath : new boolean[] {true, false}) {
        console.preferences.setProperty(NodusC.PROP_DISPLAY_FULL_PATH, Boolean.toString(fullPath));
        boolean[] accepted = {true};
        SwingUtilities.invokeAndWait(
            () -> {
              try {
                accepted[0] =
                    (Boolean)
                        call(
                            console,
                            "confirmFileOverwrite",
                            new Class<?>[] {File[].class},
                            (Object) new File[] {target.toFile()});
              } catch (Exception ex) {
                throw new AssertionError(ex);
              }
            });
        check(
            !accepted[0] && Files.readString(target).equals("original"),
            "Close accepted overwrite");
        check(
            console.message.endsWith(fullPath ? target.toString() : "results.txt"),
            "Wrong file label for path preference");
        check(
            console.message.contains(directory.toString()) == fullPath,
            "Dialog did not follow full-path preference");
      }
      String expected = locale.equals(Locale.FRANCE) ? "Remplacer" : "Replace";
      check(console.message.startsWith(expected), "Missing translated message");
      check(
          translations.get(SQLConsole.class, "Replace", "missing").equals(expected),
          "Missing button translation");
      String cancel = locale.equals(Locale.FRANCE) ? "Annuler" : "Cancel";
      check(
          translations.get(SQLConsole.class, "Cancel", "missing").equals(cancel),
          "Missing cancel translation");
      String cancelled =
          MessageFormat.format(
              translations.get(SQLConsole.class, "Export_cancelled", "missing"),
              "EXPORTCSV",
              "sample");
      check(
          cancelled.contains(locale.equals(Locale.FRANCE) ? "annul\u00e9" : "cancelled")
              && cancelled.contains("sample"),
          "Missing cancellation translation");
    }
    console.useRealDialog = true;
    console.answer = JOptionPane.YES_OPTION;
    check(
        !(Boolean)
            call(
                console,
                "confirmFileOverwrite",
                new Class<?>[] {File[].class},
                (Object) new File[] {target.toFile()}),
        "Headless execution authorized overwrite");
    console.useRealDialog = false;
  }

  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("nodus-export-confirm-");
    Field translations = SQLConsole.class.getDeclaredField("i18n");
    translations.setAccessible(true);
    Object original = translations.get(null);
    try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:export_confirm")) {
      JDBCUtils.setConnection(connection);
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE sample (num INTEGER, label VARCHAR(12))");
        statement.executeUpdate("INSERT INTO sample VALUES (7, 'sample')");
      }
      Project project = new Project(directory, connection);
      Console console = console(project);
      exports(console, project);
      extraction(console, project);
      localizationAndEdt(console, directory);
      System.out.println(
          "SQL export overwrite checks passed (DBF, CSV, CSVH, XLS, XLSX, SHP; English/French; worker/EDT).");
    } finally {
      JDBCUtils.setConnection(null);
      translations.set(null, original);
      try (Stream<Path> files = Files.walk(directory)) {
        for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator)
          Files.delete(file);
      }
    }
  }
}
