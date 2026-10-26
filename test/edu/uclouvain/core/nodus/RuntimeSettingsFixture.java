/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License,
 * version 3 or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus;

import java.lang.reflect.Field;

/** Restores both live settings and the application baseline after isolated lifecycle tests. */
final class RuntimeSettingsFixture implements AutoCloseable {
  private final NodusRuntimeSettings liveSettings = NodusRuntimeSettings.capture();
  private final Field applicationSettings;
  private final Object baseline;

  RuntimeSettingsFixture() throws ReflectiveOperationException {
    applicationSettings = NodusC.class.getDeclaredField("applicationSettings");
    applicationSettings.setAccessible(true);
    baseline = applicationSettings.get(null);
  }

  @Override
  public void close() throws IllegalAccessException {
    applicationSettings.set(null, baseline);
    liveSettings.restore();
  }
}
