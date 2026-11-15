package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.dbf.LayerAttributeException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_ERR)
class NodusProjectOpenErrorTest {
  @Test
  void invalidLayerDataUsesTheErrorDialogWithoutPrintingAStackTrace() throws Exception {
    checkReport(
        new LayerAttributeException("iww_points.dbf: Record: 2; field: MODE is empty"), false);
  }

  @Test
  void unexpectedProgrammingFailuresKeepTheirDiagnosticStackTrace() throws Exception {
    checkReport(new IllegalStateException("Unexpected loader failure"), true);
  }

  private static void checkReport(RuntimeException error, boolean expectTrace) throws Exception {
    List<String> dialogs = new ArrayList<>();
    NodusProject project =
        new NodusProject(null) {
          @Override
          void showProjectOpenErrorDialog(String message) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            dialogs.add(message);
          }
        };
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (PrintStream capture = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      try {
        SwingUtilities.invokeAndWait(() -> project.reportProjectOpenError(error));
      } finally {
        System.setErr(original);
      }
    }
    assertEquals(List.of(error.getMessage()), dialogs);
    if (expectTrace)
      assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains(error.toString()));
    else assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
  }
}
