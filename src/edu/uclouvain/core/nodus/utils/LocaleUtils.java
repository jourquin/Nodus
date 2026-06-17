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

import java.util.Locale;

/**
 * A simple helper for Locale.
 *
 * @author Bart Jourquin
 */
public class LocaleUtils {

  /** . */
  public LocaleUtils() {}

  /**
   * Parses a locale string and returns the corresponding {@link Locale}.
   *
   * <p>The input may use either a simple language code such as {@code "fr"}, the older Java-style
   * underscore format such as {@code "fr_BE"}, or the standard BCP 47 language tag format such as
   * {@code "fr-BE"}.
   *
   * <p>For backward compatibility with the previous Nodus behavior, a simple two-letter language
   * code such as {@code "fr"} is interpreted as both language and country, producing {@code fr_FR}.
   * For example, {@code "fr"} becomes {@code new Locale("fr", "FR")}.
   *
   * <p>If the supplied string is {@code null}, blank, or cannot be parsed into a valid locale with
   * a language code, the JVM default locale is returned.
   *
   * @param localeString the locale string to parse, for example {@code "fr"}, {@code "fr_BE"}, or
   *     {@code "fr-BE"}
   * @return the parsed {@link Locale}, or {@link Locale#getDefault()} if the input is missing or
   *     invalid
   */
  public static Locale parseLocale(String localeString) {
    if (localeString == null || localeString.isBlank()) {
      return Locale.getDefault();
    }

    String trimmedLocale = localeString.trim();

    // Backward compatibility with the previous implementation:
    // "fr" used to become new Locale("fr", "FR").
    if (trimmedLocale.matches("[a-zA-Z]{2}")) {
      return new Locale(trimmedLocale.toLowerCase(), trimmedLocale.toUpperCase());
    }

    String normalizedLocale = trimmedLocale.replace('_', '-');

    Locale parsedLocale = Locale.forLanguageTag(normalizedLocale);

    if (parsedLocale.getLanguage() == null || parsedLocale.getLanguage().isEmpty()) {
      return Locale.getDefault();
    }

    return parsedLocale;
  }
}
