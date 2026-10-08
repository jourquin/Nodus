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

package edu.uclouvain.core.nodus.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migrates compiled references to the unchanged Nodus 8 modal-plugin contract.
 *
 * <p>Only {@code ModalSplitMethod}, {@code Path} and {@code PathsForMode} are relocated. This is
 * not a general API converter: removed models, bundled Nodus classes, signed archives and
 * multi-release/module archives require manual rebuilding. Other resources are copied unchanged.
 *
 * <p>Class-file constant-pool UTF entries contain type names, descriptors, generic signatures and
 * literal reflection names. Rewriting those entries preserves constant-pool indexes, instructions
 * and stack-map offsets; modified UTF-8 lengths are recalculated by {@link DataOutputStream}. The
 * JVM checks the staged classes without initializing them before the original JAR is replaced.
 * Dynamically constructed class names and names stored in external resources are not converted.
 *
 * <p>The caller must obtain user consent before calling {@link #upgrade}. A byte-for-byte backup is
 * kept as {@code plugin.jar.nodus8}; an existing backup is never overwritten. Installation uses an
 * atomic replacement in the same directory. If the filesystem does not support it, the original JAR
 * is left in place and the backup is retained.
 */
final class ModalPluginUpgrader {
  private static final String OLD_PACKAGE = "edu.uclouvain.core.nodus.compute.assign.modalsplit";
  private static final String NEW_PACKAGE = "edu.uclouvain.core.nodus.compute.modalsplit";
  private static final Set<String> MOVED_CLASSES =
      Set.of("ModalSplitMethod", "Path", "PathsForMode");

  private ModalPluginUpgrader() {}

  /** Returns the original JAR's backup path. */
  static Path backup(Path jar) {
    return jar.resolveSibling(jar.getFileName() + ".nodus8");
  }

  /**
   * Detects old references and checks whether their archive can be converted, without writing
   * files.
   *
   * @param path JAR to inspect
   * @return True if the JAR uses the supported old modal-choice contract
   * @throws IOException If the archive is unreadable or needs a manual rebuild
   */
  static boolean needsUpgrade(Path path) throws IOException {
    boolean legacy = false;
    boolean unsupportedArchive = false;
    try (JarFile jar = new JarFile(path.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        String upper = name.toUpperCase(Locale.ROOT);
        unsupportedArchive |=
            name.startsWith(OLD_PACKAGE.replace('.', '/') + "/")
                || name.startsWith(NEW_PACKAGE.replace('.', '/') + "/")
                || name.startsWith("META-INF/versions/")
                || name.equals("module-info.class")
                || (upper.startsWith("META-INF/")
                    && (upper.endsWith(".SF")
                        || upper.endsWith(".RSA")
                        || upper.endsWith(".DSA")
                        || upper.endsWith(".EC")
                        || upper.startsWith("META-INF/SIG-")));
        if (!entry.isDirectory() && name.endsWith(".class")) {
          byte[] original = read(jar, entry);
          legacy |= !Arrays.equals(original, relocate(original));
        }
      }
    }
    if (legacy && unsupportedArchive) {
      throw new IOException(
          "Automatic upgrade does not support signed, modular or multi-release JARs,"
              + " or JARs containing copies of Nodus classes. Rebuild the plugin from source.");
    }
    return legacy;
  }

  /**
   * Stages, checks, backs up and installs an approved migration before any project classes load.
   *
   * @param original JAR whose supported old references should be updated
   * @param dependencies Other project JARs available when resolving plugin classes
   * @throws IOException If conversion, linkage checks, backup or atomic installation fails
   */
  static void upgrade(Path original, List<File> dependencies) throws IOException {
    if (!needsUpgrade(original)) {
      return;
    }
    Path backup = backup(original);
    if (Files.exists(backup)) {
      throw new IOException(
          "The backup already exists: " + backup + ". It will not be overwritten.");
    }
    byte[] fingerprint = fingerprint(original);
    Path staged = Files.createTempFile(original.toAbsolutePath().getParent(), ".nodus9-", ".tmp");
    try {
      List<String> classes = new ArrayList<>();
      try (JarFile source = new JarFile(original.toFile());
          JarOutputStream target = new JarOutputStream(Files.newOutputStream(staged))) {
        Enumeration<JarEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          JarEntry copy = new JarEntry(entry.getName());
          copy.setTime(entry.getTime());
          copy.setComment(entry.getComment());
          target.putNextEntry(copy);
          byte[] contents = read(source, entry);
          if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
            contents = relocate(contents);
            classes.add(entry.getName().replace('/', '.').replaceAll("\\.class$", ""));
          }
          target.write(contents);
          target.closeEntry();
        }
      }
      validate(staged, original, dependencies, classes);
      if (!Arrays.equals(fingerprint, fingerprint(original))) {
        throw new IOException("The plugin changed during conversion. Please reopen the project.");
      }
      Files.copy(original, backup);
      if (!Arrays.equals(fingerprint, fingerprint(backup))
          || !Arrays.equals(fingerprint, fingerprint(original))) {
        throw new IOException("The plugin changed while creating its backup. Upgrade cancelled.");
      }
      Files.move(
          staged, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(staged);
    }
  }

  /** Loads staged types and resolves their signatures without running plugin initialization. */
  private static void validate(
      Path staged, Path original, List<File> dependencies, List<String> classes)
      throws IOException {
    List<URL> urls = new ArrayList<>();
    urls.add(staged.toUri().toURL());
    for (File dependency : dependencies) {
      if (!dependency.toPath().equals(original)) {
        urls.add(dependency.toURI().toURL());
      }
    }
    try (URLClassLoader loader =
        new URLClassLoader(urls.toArray(new URL[0]), ModalPluginUpgrader.class.getClassLoader())) {
      for (String name : classes) {
        Class<?> type = Class.forName(name, false, loader);
        type.getDeclaredConstructors();
        type.getDeclaredMethods();
        type.getDeclaredFields();
      }
    } catch (ClassNotFoundException | LinkageError | SecurityException ex) {
      throw new IOException("The converted plugin could not be linked: " + ex.getMessage(), ex);
    }
  }

  /** Reads an archive member, closing its stream before the next entry is processed. */
  private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
    try (InputStream in = jar.getInputStream(entry)) {
      return in.readAllBytes();
    }
  }

  /** Detects changes to a JAR between preparation and installation. */
  private static byte[] fingerprint(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
          digest.update(buffer, 0, count);
        }
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by Java", ex);
    }
  }

  /** Rewrites constant-pool names without changing bytecode or constant-pool indexes. */
  private static byte[] relocate(byte[] original) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(original.length);
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(original));
        DataOutputStream out = new DataOutputStream(bytes)) {
      if (in.readInt() != 0xcafebabe) {
        throw new IOException("Invalid Java class file in plugin JAR");
      }
      out.writeInt(0xcafebabe);
      out.writeInt(in.readInt()); // Minor and major class-file version, unchanged.
      int constants = in.readUnsignedShort();
      out.writeShort(constants);
      for (int index = 1; index < constants; index++) {
        int tag = in.readUnsignedByte();
        out.writeByte(tag);
        switch (tag) {
          case 1: // Modified UTF-8, also used by descriptors, signatures and string literals.
            out.writeUTF(relocateNames(relocateNames(in.readUTF(), '.'), '/'));
            break;
          case 3: // Integer, float, member references, name/type and dynamic constants.
          case 4:
          case 9:
          case 10:
          case 11:
          case 12:
          case 17:
          case 18:
            out.writeInt(in.readInt());
            break;
          case 5: // Long and double occupy two constant-pool slots.
          case 6:
            out.writeLong(in.readLong());
            index++;
            break;
          case 7: // Class, string, method type, module and package references.
          case 8:
          case 16:
          case 19:
          case 20:
            out.writeShort(in.readUnsignedShort());
            break;
          case 15: // Method handle.
            out.writeByte(in.readUnsignedByte());
            out.writeShort(in.readUnsignedShort());
            break;
          default:
            throw new IOException("Unsupported class-file constant tag: " + tag);
        }
      }
      in.transferTo(out);
    }
    return bytes.toByteArray();
  }

  /** Updates only the three unchanged API types; any other old modal class needs manual review. */
  private static String relocateNames(String value, char separator) throws IOException {
    String prefix = OLD_PACKAGE.replace('.', separator) + separator;
    if (!value.contains(prefix)) {
      return value;
    }
    Matcher matcher =
        Pattern.compile(Pattern.quote(prefix) + "([A-Za-z_$][A-Za-z0-9_$]*)").matcher(value);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!MOVED_CLASSES.contains(name)) {
        throw new IOException("The old modal class " + name + " requires a manual plugin rebuild.");
      }
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(NEW_PACKAGE.replace('.', separator) + separator + name));
    }
    matcher.appendTail(result);
    if (result.indexOf(prefix) >= 0) {
      throw new IOException("An old modal package reference requires a manual plugin rebuild.");
    }
    return result.toString();
  }
}
