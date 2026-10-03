/*
 * Copyright (c) 2020 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.asciidoc.renderer;

import static java.util.Locale.ROOT;

public final class IdGenerator {
    private IdGenerator() {
        // no-op
    }

    public static String forTitle(final String title) {
        return forTitle(title, null, null);
    }

    /**
     * Builds the id asciidoctor gives a section without an explicit one, with the rule of its
     * {@code Section.generate_id}; the numbering asciidoctor adds when the id is already used ({@code _2}) is not done
     * here. The title, in lower case, loses its tags, its character references and every character other than a
     * letter, a digit, {@code _}, a space, {@code -} and {@code .}, and the prefix is put in front of it. Then each run
     * of spaces, dots, hyphens and separators becomes one separator, a trailing separator is dropped, and so is a
     * leading one when the prefix is empty. An empty separator only removes the spaces, and a longer one keeps its
     * first character.
     *
     * @param title       the title as asciidoctor's converted title: tags and character references are removed.
     * @param idprefix    the {@code idprefix} attribute, {@code _} when {@code null}.
     * @param idseparator the {@code idseparator} attribute, {@code _} when {@code null}.
     * @return the generated id.
     */
    public static String forTitle(final String title, final String idprefix, final String idseparator) {
        final var prefix = idprefix != null ? idprefix : "_";
        // ruby's downcase turns every capital sigma into σ, java's turns one ending a word into ς
        final var text = (title.indexOf('Σ') >= 0 ? title.replace('Σ', 'σ') : title).toLowerCase(ROOT);
        final var kept = new StringBuilder(prefix.length() + text.length()).append(prefix);
        int i = 0;
        while (i < text.length()) {
            final int c = text.codePointAt(i);
            if (c == '<') {
                final int end = text.indexOf('>', i + 1);
                i = end > i + 1 ? end + 1 : i + 1;
            } else if (c == '&') {
                final int end = characterReferenceEnd(text, i + 1);
                i = end > 0 ? end : i + 1;
            } else {
                if (c == ' ' || c == '-' || c == '.' || isAsciidoctorWordCharacter(c)) {
                    kept.appendCodePoint(c);
                }
                i += Character.charCount(c);
            }
        }

        if (idseparator != null && idseparator.isEmpty()) {
            final var id = new StringBuilder(kept.length());
            for (int j = 0; j < kept.length(); j++) {
                final char c = kept.charAt(j);
                if (c != ' ') {
                    id.append(c);
                }
            }
            return id.toString();
        }

        final int separator = idseparator == null ? '_' : idseparator.codePointAt(0);
        final int separatorLength = Character.charCount(separator);
        final var id = new StringBuilder(kept.length());
        boolean inSeparatorRun = false;
        int j = 0;
        while (j < kept.length()) {
            final int c = kept.codePointAt(j);
            if (c == ' ' || c == '.' || c == '-' || c == separator) {
                if (!inSeparatorRun) {
                    id.appendCodePoint(separator);
                    inSeparatorRun = true;
                }
            } else {
                id.appendCodePoint(c);
                inSeparatorRun = false;
            }
            j += Character.charCount(c);
        }
        if (id.length() >= separatorLength && id.codePointBefore(id.length()) == separator) {
            id.setLength(id.length() - separatorLength);
        }
        if (prefix.isEmpty() && !id.isEmpty() && id.codePointAt(0) == separator) {
            id.delete(0, separatorLength);
        }
        return id.toString();
    }

    // asciidoctor's InvalidSectionIdCharsRx keeps the characters of its \p{Word} class: alphabetic characters, marks,
    // decimal digits and connector punctuation such as _
    private static boolean isAsciidoctorWordCharacter(final int codePoint) {
        if (Character.isAlphabetic(codePoint)) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
                 Character.DECIMAL_DIGIT_NUMBER, Character.CONNECTOR_PUNCTUATION -> true;
            default -> false;
        };
    }

    // the character references asciidoctor removes: &name; with an optional two-digit suffix, &#nn; up to six digits and
    // &#xhh; up to five hexadecimal digits, the text being in lower case
    private static int characterReferenceEnd(final String text, final int start) {
        int i = start;
        if (i < text.length() && text.charAt(i) == '#') {
            i++;
            final boolean hexadecimal = i < text.length() && text.charAt(i) == 'x';
            if (hexadecimal) {
                i++;
            }
            final int digits = i;
            while (i < text.length() && isDigit(text.charAt(i), hexadecimal)) {
                i++;
            }
            final int count = i - digits;
            return count >= 2 && count <= (hexadecimal ? 5 : 6) && i < text.length() && text.charAt(i) == ';' ? i + 1 : -1;
        }
        while (i < text.length() && text.charAt(i) >= 'a' && text.charAt(i) <= 'z') {
            i++;
        }
        if (i - start < 2) {
            return -1;
        }
        final int digits = i;
        while (i < text.length() && isDigit(text.charAt(i), false)) {
            i++;
        }
        return i - digits <= 2 && i < text.length() && text.charAt(i) == ';' ? i + 1 : -1;
    }

    private static boolean isDigit(final char c, final boolean hexadecimal) {
        return (c >= '0' && c <= '9') || (hexadecimal && c >= 'a' && c <= 'f');
    }
}
