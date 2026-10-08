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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NodusProjectConnectionLossTest {
  @Test
  void lostSessionWarnsOnceAndNeverReconnectsOrSavesDbfLayers() throws Exception {
    AtomicBoolean valid = new AtomicBoolean(true);
    AtomicInteger pings = new AtomicInteger();
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("isValid")) {
                    pings.incrementAndGet();
                    return valid.get();
                  }
                  if (method.getName().equals("isClosed")) {
                    return !valid.get();
                  }
                  throw new AssertionError("Unexpected JDBC method: " + method.getName());
                });
    AtomicInteger warnings = new AtomicInteger();
    NodusProject project =
        new NodusProject(null) {
          @Override
          void showJdbcConnectionLostWarning() {
            warnings.incrementAndGet();
          }

          @Override
          public boolean isDirty() {
            return true;
          }

          @Override
          protected int confirmLayerSaveOnClose() {
            throw new AssertionError("Lost connection must not offer to save network edits");
          }
        };
    setField(project, "jdbcConnection", connection);
    setField(project, "monitorMainConnection", true);
    setField(project, "isOpen", true);
    CountDownLatch bridgeStopped = new CountDownLatch(1);
    AtomicInteger shutdowns = new AtomicInteger();
    project.registerProjectCleanup(
        "test bridge",
        () -> {
          shutdowns.incrementAndGet();
          bridgeStopped.countDown();
        });

    Method start = NodusProject.class.getDeclaredMethod("startJdbcKeepAlive");
    start.setAccessible(true);
    start.invoke(project);
    Timer timer = (Timer) getField(project, "jdbcKeepAliveTimer");
    assertTrue(timer != null);

    valid.set(false);
    assertSame(connection, project.getMainJDBCConnection());
    SwingUtilities.invokeAndWait(() -> {});
    assertTrue(bridgeStopped.await(5, TimeUnit.SECONDS));

    assertEquals(1, warnings.get());
    assertNull(getField(project, "jdbcKeepAliveTimer"));
    assertSame(connection, project.getMainJDBCConnection());
    assertFalse(project.saveEsriLayersSafely());
    NodusEsriLayer layer =
        new NodusEsriLayer() {
          @Override
          public boolean isDirty() {
            return true;
          }
        };
    Field owner = NodusEsriLayer.class.getDeclaredField("nodusProject");
    owner.setAccessible(true);
    owner.set(layer, project);
    assertFalse(layer.saveChanges(), "Direct layer saves must not change DBF files either");
    Method beforeClose = NodusProject.class.getDeclaredMethod("saveModifiedLayersBeforeClose");
    beforeClose.setAccessible(true);
    assertTrue((Boolean) beforeClose.invoke(project));
    assertEquals(1, warnings.get());
    assertEquals(1, pings.get());
    assertEquals(1, shutdowns.get());
  }

  private static void setField(NodusProject project, String name, Object value) throws Exception {
    Field field = NodusProject.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(project, value);
  }

  private static Object getField(NodusProject project, String name) throws Exception {
    Field field = NodusProject.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(project);
  }
}
