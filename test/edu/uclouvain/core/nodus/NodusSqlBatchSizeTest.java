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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Checks effective batch sizes without opening project files or a graphical window. */
class NodusSqlBatchSizeTest {

  @Test
  void projectOverridesGlobalAndGlobalChangesApplyToSubsequentLookups() throws Exception {
    Panel panel = new Panel();
    Properties overrides = new Properties();
    NodusProject project = project(panel, overrides);
    assertEquals(1000, project.getSqlBatchSize());
    panel.getNodusProperties().setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "750");
    assertEquals(750, project.getSqlBatchSize());
    overrides.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "300");
    assertEquals(300, project.getSqlBatchSize());
    panel.getNodusProperties().setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "250");
    assertEquals(300, project.getSqlBatchSize());
    overrides.remove(NodusC.PROP_MAX_SQL_BATCH_SIZE);
    assertEquals(250, project.getSqlBatchSize());
    assertEquals(250, panel.getSqlBatchSize());
  }

  @Test
  void invalidProjectValuesUseGlobalAndInvalidGlobalValuesUseBuiltInDefault() throws Exception {
    Panel panel = new Panel();
    Properties overrides = new Properties();
    NodusProject project = project(panel, overrides);
    panel.getNodusProperties().setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "20");
    for (String invalid : new String[] {"", "abc", "0", "-1", "1.5", "2147483648"}) {
      overrides.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, invalid);
      assertEquals(20, project.getSqlBatchSize(), invalid);
    }
    overrides.remove(NodusC.PROP_MAX_SQL_BATCH_SIZE);
    for (String invalid : new String[] {"", "abc", "0", "-1", "1.5", "2147483648"}) {
      panel.getNodusProperties().setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, invalid);
      assertEquals(1000, project.getSqlBatchSize(), invalid);
      assertEquals(1000, panel.getSqlBatchSize(), invalid);
    }
    overrides.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "1");
    assertEquals(1, project.getSqlBatchSize());
    overrides.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "2147483647");
    assertEquals(Integer.MAX_VALUE, project.getSqlBatchSize());
  }

  @Test
  void projectWithoutPanelUsesProjectValueOrBuiltInDefault() throws Exception {
    Properties overrides = new Properties();
    NodusProject project = project(null, overrides);
    assertEquals(1000, project.getSqlBatchSize());
    overrides.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "5");
    assertEquals(5, project.getSqlBatchSize());
  }

  private static NodusProject project(Panel panel, Properties overrides) throws Exception {
    NodusProject project = new NodusProject(panel);
    Field field = NodusProject.class.getDeclaredField("localProperties");
    field.setAccessible(true);
    field.set(project, overrides);
    return project;
  }

  private static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
  }
}
