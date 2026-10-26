/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License,
 * version 3 or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot of the public, mutable NodusC settings (scalars, strings and enums). */
final class NodusRuntimeSettings {
  private final Map<Field, Object> values;

  private NodusRuntimeSettings(Map<Field, Object> values) {
    this.values = values;
  }

  /** Includes new mutable settings automatically, while leaving final constants untouched. */
  static NodusRuntimeSettings capture() {
    final Map<Field, Object> values = new LinkedHashMap<>();
    try {
      for (Field field : NodusC.class.getFields()) {
        if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
          values.put(field, field.get(null));
        }
      }
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot read public NodusC settings", failure);
    }
    return new NodusRuntimeSettings(values);
  }

  void restore() {
    try {
      for (Map.Entry<Field, Object> entry : values.entrySet()) {
        entry.getKey().set(null, entry.getValue());
      }
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot restore public NodusC settings", failure);
    }
  }
}
