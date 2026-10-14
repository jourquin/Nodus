package com.bbn.openmap.layer.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Network labels use the same explicit charset as database imports, even on a CP1252 JVM. */
@ResourceLock("JDBCUtils")
class NodusEsriLayerEncodingTest {
  @TempDir Path directory;

  @Test
  void loadsAndSavesCp1252AttributesAndRetainsUppercaseCpgFilename() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, true)) {
      var model = project.layer.getModel();
      String label = "Été – coût €";
      for (int row = 0; row < model.getRowCount(); row++) {
        model.setValueAt(label, row, 9);
      }
      Path file = directory.resolve("features.dbf");
      Charset cp1252 = Charset.forName("windows-1252");
      assertTrue(ExportDBF.exportFile(file, model, cp1252));
      Files.deleteIfExists(directory.resolve("features.cpg"));
      Files.writeString(directory.resolve("features.CPG"), "1252");
      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
      try {
        reloaded.setProject(project, "features");
        assertEquals(label, reloaded.getModel().getValueAt(0, 9));
        reloaded.getModel().setValueAt("Nouvel été", 0, 9);
        reloaded.setDirtyDbf(true);
        assertTrue(reloaded.saveChanges());
        assertEquals("windows-1252\n", Files.readString(directory.resolve("features.CPG")));
        assertEquals("features.CPG", DbfFiles.cpg(file).getFileName().toString());
        var saved = DbfFiles.readModel(file, null);
        assertEquals("Nouvel été", saved.model.getValueAt(0, 9));
        assertEquals(cp1252, saved.charset);
      } finally {
        reloaded.dispose();
      }
    }
  }

  @Test
  void utf8NetworkLabelsRoundTripRegardlessOfJvmDefaultEncoding() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      project.layer.getModel().setValueAt("Été 東京", 0, 3);
      project.layer.setDirtyDbf(true);
      assertTrue(project.layer.saveChanges());
      LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
      try {
        reloaded.setProject(project, "features");
        assertEquals("Été 東京", reloaded.getModel().getValueAt(0, 3));
        assertEquals("UTF-8\n", Files.readString(directory.resolve("features.cpg")));
      } finally {
        reloaded.dispose();
      }
    }
  }
}
