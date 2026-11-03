/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>Nodus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with Nodus. If not,
 * see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;

/**
 * Runs Nodus scripts while allowing a script file and an explicitly declared class to share a name.
 *
 * <p>Groovy compiles top-level executable statements into a generated {@link groovy.lang.Script}
 * subclass named after the logical source filename. Consequently, a file named {@code
 * ARGBConverter.groovy} containing both {@code class ARGBConverter} and {@code new
 * ARGBConverter(nodusMapPanel)} would normally define two classes named {@code ARGBConverter}.
 * This shell supplies {@code _ARGBConverter.groovy} as the logical filename, giving the generated
 * wrapper the name {@code _ARGBConverter}. The physical file and the explicitly declared class
 * keep their original names. Existing scripts using a different class name remain compatible.
 *
 * <p>The two overrides cover Nodus's interactive and lifecycle entry points:
 *
 * <ul>
 *   <li>{@link #evaluate(File)} is used by {@link ScriptRunner} for application/project hooks and
 *       by the legacy Nodus script editor.
 *   <li>{@link #run(String, String, String[])} handles the native Groovy console's editor text.
 *       Groovy's inherited {@code run(String, String, List)} overload delegates to this method.
 * </ul>
 *
 * <p>Callers supply the original filename; each override adds exactly one leading underscore.
 * The corresponding prefixed class name is reserved for the wrapper: this convention does not
 * resolve a script that explicitly declares that name too. Other inherited entry points, such as
 * {@code parse(File)} and {@code evaluate(String)}, retain Groovy's standard naming behavior.
 * Compilation diagnostics and stack traces show the logical prefixed filename, while source line
 * numbers are unchanged because the script text is not rewritten.
 *
 * <p>Variables are supplied through the normal Groovy {@link Binding}, for example using {@link
 * #setVariable(String, Object)} to expose {@code nodusMapPanel}. This shell does not inject Nodus
 * objects itself. Execution is synchronous on the calling thread; background execution,
 * cancellation, window handling and displaying return values are responsibilities of its callers.
 */
public final class NodusGroovyShell extends GroovyShell {
  /** Source encoding captured from the compiler configuration when this shell is constructed. */
  private final String sourceEncoding;

  /**
   * Creates a shell with a fresh, empty binding and Groovy's default compiler configuration.
   *
   * <p>Application/project hooks and the legacy editor use this constructor, then populate the
   * binding with the Nodus objects needed by their scripts.
   */
  public NodusGroovyShell() {
    this(null, new Binding(), CompilerConfiguration.DEFAULT);
  }

  /**
   * Creates a shell retaining the native console's binding, classpath and compiler customizers.
   *
   * <p>The native console recreates its shell when its context or interruption settings change.
   * Passing its current configuration through to Groovy preserves customizers such as {@code
   * ThreadInterrupt}. File evaluation uses the source encoding captured here.
   *
   * @param parent parent class loader, or {@code null} to let Groovy choose its default
   * @param binding non-null binding shared with the caller and exposed to the script
   * @param configuration non-null compiler configuration, including classpath, source encoding
   *     and any interruption customizers
   */
  public NodusGroovyShell(
      ClassLoader parent, Binding binding, CompilerConfiguration configuration) {
    super(parent, binding, configuration);
    sourceEncoding = configuration.getSourceEncoding();
  }

  /**
   * Evaluates a file without reserving its base name for the generated script class.
   *
   * <p>The file is opened using the configured source encoding and passed to Groovy's {@code
   * evaluate(Reader, String)} overload with {@code "_" + file.getName()} as its logical filename.
   * The reader is closed on success and on compilation or execution failure. The shell's existing
   * binding remains available to the script, and Groovy's normal return-value semantics apply.
   *
   * @param file physical script file, whose name and contents are left unchanged
   * @return script result, normally the value of its last expression or explicit {@code return}
   * @throws IOException if opening or closing the script file fails
   * @throws org.codehaus.groovy.control.CompilationFailedException if the script cannot be compiled
   */
  @Override
  public Object evaluate(File file) throws IOException {
    // Use the same reader and configured encoding as Groovy's file-based evaluation.
    try (Reader reader = ResourceGroovyMethods.newReader(file, sourceEncoding)) {
      return super.evaluate(reader, "_" + file.getName());
    }
  }

  /**
   * Applies the same naming rule to source text executed by the native Groovy console.
   *
   * <p>The console executes text from its editor rather than reading the file again. This override
   * prefixes only the logical filename and delegates to Groovy's runner, preserving its binding,
   * argument handling and entry-point selection. It also receives calls through Groovy's inherited
   * list-argument overload, so the console keeps this convention without changing its Run action.
   *
   * @param text script source, passed to the compiler unchanged
   * @param fileName original logical filename supplied by the console, without a Nodus prefix
   * @param arguments script arguments, exposed according to Groovy's normal runner semantics
   * @return result returned by Groovy's runner
   * @throws org.codehaus.groovy.control.CompilationFailedException if the source cannot be compiled
   */
  @Override
  public Object run(String text, String fileName, String[] arguments) {
    return super.run(text, "_" + fileName, arguments);
  }
}
