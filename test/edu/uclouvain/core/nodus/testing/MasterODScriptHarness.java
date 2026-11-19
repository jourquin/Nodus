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

package edu.uclouvain.core.nodus.testing;

import edu.uclouvain.core.nodus.NodusProject;
import groovy.lang.GroovyClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * Test-only adapter that compiles the delivered script without executing its desktop entry point.
 *
 * <p>This belongs to test output only; it is never shipped in the Nodus application JAR and is not
 * required to use the script. The production OD implementation lives entirely in Groovy.
 */
public final class MasterODScriptHarness implements AutoCloseable {
  private final GroovyClassLoader loader;
  private final Class<?> engine;
  private final Method extract;

  /**
   * Compiles the real script using only the test process's application/runtime dependencies.
   *
   * @param script self-contained Groovy source
   * @throws Exception if reading, compilation or engine method lookup fails
   */
  public MasterODScriptHarness(Path script) throws Exception {
    this(script, CompilerConfiguration.DEFAULT, source -> source);
  }

  /**
   * Compiles with an explicit console configuration and an optional test-only source transform.
   *
   * @param script real production source
   * @param configuration compiler configuration, including native-console interrupt checks
   * @param sourceTransform controlled fault injection; identity for ordinary tests
   * @throws Exception if reading or compilation fails
   */
  public MasterODScriptHarness(
      Path script, CompilerConfiguration configuration, UnaryOperator<String> sourceTransform)
      throws Exception {
    loader = new GroovyClassLoader(MasterODScriptHarness.class.getClassLoader(), configuration);
    try {
      loader.parseClass(
          sourceTransform.apply(Files.readString(script)), "_" + script.getFileName());
      engine = loader.loadClass("ODMatrixExtractor");
      extract =
          engine.getMethod(
              "extract", NodusProject.class, Map.class, Consumer.class, Consumer.class);
    } catch (Exception | Error failure) {
      try {
        loader.close();
      } catch (Exception closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /**
   * Calls the script-local engine and propagates its original exception, including cancellation.
   *
   * @param project isolated test project
   * @param options script options
   * @param log test progress sink
   * @param reportSink test reconciliation sink
   * @return successful reconciliation reports
   * @throws Exception the script's extraction failure
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> extract(
      NodusProject project,
      Map<String, ?> options,
      Consumer<String> log,
      Consumer<Map<String, Object>> reportSink)
      throws Exception {
    try {
      return (List<Map<String, Object>>) extract.invoke(null, project, options, log, reportSink);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new RuntimeException(cause);
    }
  }

  private static Runnable waitHook = () -> {};

  /** Sets a fault-injection hook used only by the interrupted-wait regression. */
  public static void setWaitHook(Runnable hook) {
    waitHook = hook;
  }

  /** Runs the Java hook at an injected call site, outside Groovy's interrupt instrumentation. */
  public static void beforeWorkerWait() {
    waitHook.run();
  }

  /** Returns the script-local class for assertions about class-loader and packaging ownership. */
  public Class<?> getEngineClass() {
    return engine;
  }

  /** Releases this test adapter's class loader after all extraction workers have completed. */
  @Override
  public void close() throws java.io.IOException {
    loader.close();
  }
}
