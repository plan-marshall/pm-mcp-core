/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * The {@code commit_trailer} patterns of the operator's local configuration against the code that reads them. The
 * patterns refuse what the trailer refuses — an angle bracket and the Unicode line separators for the name, an angle
 * bracket and white space for the email — and, stricter than the code, also every C0 and C1 control, so a value that
 * passes the store's schema check carries no NUL or ESC. The unpaired surrogate is the one rule no ECMA-262 pattern
 * expresses; the prose of the specification states it beside the schema.
 * <p>
 * The patterns are compiled here with the regex engine of the platform, as a JSON-schema validator on the JVM compiles
 * them, not with ECMA-262's; the two differ in {@code \s}, which is why the email pattern names U+0085 and the Unicode
 * white space explicitly, so the asserted code points answer the same in either dialect.
 */
@EnableGeneratorController
@DisplayName("CommitTrailer schema parity")
class CommitTrailerSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/config/local-config.schema.json";

    private static final char NEL = (char) 0x0085;
    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;
    private static final char NO_BREAK_SPACE = (char) 0x00a0;
    private static final char NUL = '\u0000';
    private static final char ESC = '\u001b';
    private static final char DEL = '\u007f';
    private static final char C1_LAST = '\u009f';
    private static final String SUPPLEMENTARY = new String(Character.toChars(0x1F600));

    // the code points the trailer itself refuses: the angle brackets and every line break it treats as one
    private static final char[] TRAILER_REFUSED = {'<', '>', '\r', '\n', NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR};

    // the C0 and C1 controls the pattern refuses beyond the code, so a stored NUL or ESC cannot reach the trailer line
    private static final char[] PATTERN_ONLY_REFUSED = {NUL, ESC, DEL, C1_LAST};

    @Nested
    @DisplayName("name")
    class Name {

        @Test
        @DisplayName("the pattern and the code refuse an angle bracket and every line break")
        void refusesWhatTheCodeRefuses() {
            var pattern = pattern("name");

            for (var refused : TRAILER_REFUSED) {
                var candidate = "a" + refused + "b";
                assertAll("code point " + (int) refused,
                        () -> assertFalse(pattern.matcher(candidate).matches(), "The pattern refuses the code point"),
                        () -> assertFalse(CommitTrailer.isValidName(candidate), "The code refuses the code point"));
            }
        }

        @Test
        @DisplayName("the pattern also refuses the C0 and C1 controls the code would pass")
        void refusesControls() {
            var pattern = pattern("name");

            for (var refused : PATTERN_ONLY_REFUSED) {
                assertFalse(pattern.matcher("a" + refused + "b").matches(),
                        "The pattern refuses the control character");
            }
        }

        @Test
        @DisplayName("the pattern admits a generated name and one with a supplementary character")
        void admitsValidNames() {
            var pattern = pattern("name");
            var generated = Generators.letterStrings(1, 20).next();

            assertAll("valid names",
                    () -> assertTrue(pattern.matcher(generated).matches(), "The pattern admits a generated name"),
                    () -> assertTrue(CommitTrailer.isValidName(generated), "The code admits the same name"),
                    () -> assertTrue(pattern.matcher("a" + SUPPLEMENTARY).matches(),
                            "The pattern admits a supplementary character"));
        }
    }

    @Nested
    @DisplayName("email")
    class Email {

        @Test
        @DisplayName("the pattern and the code refuse an angle bracket and white space")
        void refusesWhatTheCodeRefuses() {
            var pattern = pattern("email");
            var refused = new char[] {'<', '>', ' ', '\r', '\n', NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR,
                    NO_BREAK_SPACE};

            for (var codePoint : refused) {
                var local = "a" + codePoint + "b@c.de";
                var domain = "a@b" + codePoint + "c.de";
                assertAll("code point " + (int) codePoint,
                        () -> assertFalse(pattern.matcher(local).matches(), "The pattern refuses it in the local part"),
                        () -> assertFalse(CommitTrailer.isValidEmail(local), "The code refuses it in the local part"),
                        () -> assertFalse(pattern.matcher(domain).matches(), "The pattern refuses it in the domain"),
                        () -> assertFalse(CommitTrailer.isValidEmail(domain), "The code refuses it in the domain"));
            }
        }

        @Test
        @DisplayName("the pattern also refuses the C0 and C1 controls the code would pass")
        void refusesControls() {
            var pattern = pattern("email");

            for (var refused : PATTERN_ONLY_REFUSED) {
                assertFalse(pattern.matcher("a" + refused + "@b.de").matches(),
                        "The pattern refuses the control character");
            }
        }

        @Test
        @DisplayName("the pattern admits a plain address, as the code does")
        void admitsPlainAddress() {
            var pattern = pattern("email");

            assertAll("plain address",
                    () -> assertTrue(pattern.matcher("noreply@cuioss.de").matches(),
                            "The pattern admits a plain address"),
                    () -> assertTrue(CommitTrailer.isValidEmail("noreply@cuioss.de"),
                            "The code admits the same address"));
        }
    }

    private static Pattern pattern(String half) {
        var definitions = SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs"));
        var settings = SchemaResource.object(definitions.get("settings"));
        var trailer = SchemaResource.object(SchemaResource.object(settings.get("properties")).get("commit_trailer"));
        var declared = SchemaResource.object(SchemaResource.object(trailer.get("properties")).get(half));
        return Pattern.compile(String.valueOf(declared.get("pattern")));
    }
}
