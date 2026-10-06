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

package edu.uclouvain.core.nodus.compute.modalsplit;

import edu.uclouvain.core.nodus.NodusC;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Immutable observed-table mapping and reference mode for standalone modal estimation.
 *
 * <p>Map keys are Nodus mode IDs, not estimator column indices. Tables are stored in ascending mode
 * order, which defines the arrays constructed by {@link LogitCalibration}. The reference intercept
 * is fixed at zero for logit/probit; the proportional reference cost factor is fixed at one. The
 * mapping supplies observations only and has no effect on the OD matrix selected for assignment.
 *
 * <p>Construction copies the map but permits incomplete drafts, including {@link #NONE}. Call
 * {@link #validate()} before computation; it checks mode ranges, table names and the mapped
 * reference, but not database schemas or model identification. Actual tables are checked when the
 * calibration reads them. All currently supported methods require a mapped reference.
 *
 * <p>{@link #encode()} and {@link #decode(String)} persist the mapping under {@link #PROPERTY} as
 * JSON, including table names containing spaces or punctuation. Decoding accepts the observed-data
 * portion of legacy settings, ignoring their former assignment/estimation flags. Model selection,
 * cost file and routing controls belong to the enclosing dialog's preferences.
 */
public final class LogitCalibrationSettings {
  /** Project property containing only the observed-table mapping and reference selection. */
  public static final String PROPERTY = "modalChoiceCalibration";
  /** Empty draft used for new projects; it intentionally fails estimation validation. */
  public static final LogitCalibrationSettings NONE =
      new LogitCalibrationSettings(1, Collections.emptyMap());

  private final int referenceMode;
  private final Map<Integer, String> tables;

  /**
   * Captures observed tables independently of assignment settings or scenarios.
   *
   * @param referenceMode mode whose intercept is fixed at zero, or proportional factor at one
   * @param tables mapping from mode IDs to observed OD table names
   */
  public LogitCalibrationSettings(int referenceMode, Map<Integer, String> tables) {
    this.referenceMode = referenceMode;
    this.tables = Collections.unmodifiableMap(new TreeMap<>(tables));
  }

  /**
   * Returns the reference alternative.
   *
   * @return the Nodus mode ID
   */
  public int getReferenceMode() {
    return referenceMode;
  }

  /**
   * Returns the observed OD table mapping.
   *
   * @return an immutable mapping ordered by mode ID
   */
  public Map<Integer, String> getTables() {
    return tables;
  }

  /**
   * Identifies the built-in modal methods with parameter estimation.
   *
   * @param method the short modal split method name
   * @return true for MNL, MNP and Proportional
   */
  public static boolean supportsMethod(String method) {
    return "MNL".equals(method) || "MNP".equals(method) || "Proportional".equals(method);
  }

  /**
   * Validates mode/table entries and requires a mapped reference for a supported model.
   *
   * @throws IllegalArgumentException if fewer than two valid mappings or no mapped reference exist
   */
  public void validate() {
    validate(true);
  }

  /**
   * Checks the observed matrices and the reference alternative.
   *
   * <p>This checks the mapping only: it does not open tables, compare their schema or test whether
   * costs and observed flows identify model parameters. The false option is retained for callers
   * validating a draft without a reference; all built-in estimation workflows require one.
   *
   * @param requireReference whether the selected reference must occur among mapped mode IDs
   * @throws IllegalArgumentException for invalid table/mode entries or reference selection
   */
  public void validate(boolean requireReference) {
    if (tables.size() < 2) {
      throw new IllegalArgumentException("Select at least two modal OD tables");
    }
    if (requireReference && !tables.containsKey(referenceMode)) {
      throw new IllegalArgumentException(
          "Select a reference mode from the observed modal OD tables");
    }
    for (Map.Entry<Integer, String> entry : tables.entrySet()) {
      if (entry.getKey() <= 0
          || entry.getKey() >= NodusC.MAXMM
          || entry.getValue() == null
          || entry.getValue().isBlank()) {
        throw new IllegalArgumentException("Each mode needs a valid ID and an observed OD table");
      }
    }
  }

  /**
   * Encodes all settings as a single project property.
   *
   * @return JSON text, including table names without delimiter restrictions
   */
  public String encode() {
    Map<String, Object> values = new TreeMap<>();
    values.put("referenceMode", referenceMode);
    Map<String, String> names = new TreeMap<>();
    tables.forEach((mode, name) -> names.put(mode.toString(), name));
    values.put("tables", names);
    return JSONObject.toJSONString(values);
  }

  /**
   * Restores project settings, including the observed-table mapping from the former workflow.
   *
   * <p>Legacy estimation and assignment-demand flags are deliberately ignored. Loading settings
   * cannot request an assignment or change its demand source.
   *
   * @param text encoded settings, or null for a new project
   * @return restored draft; blank input gives NONE and structural validation remains explicit
   * @throws IllegalArgumentException if the encoded JSON or expected fields cannot be decoded
   */
  public static LogitCalibrationSettings decode(String text) {
    if (text == null || text.isBlank()) {
      return NONE;
    }
    try {
      JSONObject values = (JSONObject) JSONValue.parseWithException(text);
      JSONObject names = (JSONObject) values.get("tables");
      Map<Integer, String> tables = new TreeMap<>();
      for (Object key : names.keySet()) {
        tables.put(Integer.valueOf(key.toString()), (String) names.get(key));
      }
      return new LogitCalibrationSettings(
          ((Number) values.get("referenceMode")).intValue(), tables);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid saved modal calibration settings", exception);
    }
  }
}
