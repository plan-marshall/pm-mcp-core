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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * The patterns of the operator's stored identity halves — {@code commit_trailer} and {@code commit_identity} of
 * {@code local-config.schema.json} — against the code that reads them. Both keys refuse an angle bracket and the line
 * separators of the name half, and the email half refuses white space; unlike {@link CommitTrailer}, the schema refuses
 * every C0 and C1 control as well, so a value that passes the schema check carries no NUL or ESC. The unpaired surrogate
 * is the one rule no ECMA-262 pattern expresses; the prose of the specification states it beside the schema.
 * <p>
 * The patterns are compiled here with {@code java.util.regex}, the engine of this test, and the whole C0/C1 and white
 * space classes are checked code point by code point rather than sampled. No validator runs in this module: the store's
 * schema check is the assembly's, and nothing in this repository enforces the schema (a Watch records it). U+FEFF is the
 * one deliberate exception of the white-space class: ECMA-262's {@code \s} names it and {@code java.util.regex}'s does
 * not, so the pattern admits it — as {@link CommitTrailer} does — and the test names that rather than sampling around
 * it.
 */
@EnableGeneratorController
@DisplayName("The stored identity schema patterns")
class CommitTrailerSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/config/local-config.schema.json";

    private static final String TRAILER = "commit_trailer";

    private static final String IDENTITY = "commit_identity";

    /** The Zero Width No-Break Space, the one white-space code point of ECMA-262 that {@code \s} misses on the JVM. */
    private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xfeff;

    /** The angle brackets, refused by both halves of both keys. */
    private static final String[] ANGLE_BRACKETS = {"<", ">"};

    /** The Unicode line separators the trailer's own rule refuses; U+0085 is a C1 control as well. */
    private static final int[] LINE_SEPARATORS = {0x0085, 0x2028, 0x2029};

    /** The Unicode White_Space code points above the C1 range; the name half admits them, the email half refuses them. */
    private static final int[] WHITE_SPACE_ABOVE_C1 = {0x00a0, 0x1680, 0x202f, 0x205f, 0x3000};

    private static final String SUPPLEMENTARY = new String(Character.toChars(0x1F600));

    @Nested
    @DisplayName("commit_trailer name")
    class TrailerName {

        @Test
        @DisplayName("the pattern refuses every C0 and C1 control, the line separators and the angle brackets")
        void refusesTheWholeControlClass() {
            var pattern = pattern(TRAILER, "name");

            var admitted = admitted(pattern, nameValues(controls()));
            admitted.addAll(admitted(pattern, nameValues(LINE_SEPARATORS)));
            admitted.addAll(admitted(pattern, names(ANGLE_BRACKETS)));

            assertNotEquals(0, controls().length, "The control population is not empty");
            assertEquals(List.of(), admitted, "The pattern refuses every listed code point");
        }

        @Test
        @DisplayName("the code refuses the angle brackets and the line breaks the trailer carries")
        void codeRefusesTheLineBreaks() {
            var refused = new ArrayList<Integer>(Arrays.stream(LINE_SEPARATORS).boxed().toList());

            assertFalse(CommitTrailer.isValidName("a<b"), "The code refuses the angle bracket");
            for (var codePoint : refused) {
                assertFalse(CommitTrailer.isValidName("a" + Character.toString(codePoint) + "b"),
                        "The code refuses the line separator " + codePointName(codePoint));
            }
        }

        @Test
        @DisplayName("the pattern admits the white-space class above C1, as the code does")
        void admitsWhiteSpaceAboveC1() {
            var pattern = pattern(TRAILER, "name");

            var rejected = rejected(pattern, nameValues(WHITE_SPACE_ABOVE_C1));
            rejected.addAll(rejected(pattern, nameValues(new int[]{ZERO_WIDTH_NO_BREAK_SPACE})));

            assertNotEquals(0, WHITE_SPACE_ABOVE_C1.length, "The white-space population is not empty");
            assertEquals(List.of(), rejected, "The name half admits the white-space class");
        }

        @Test
        @DisplayName("the pattern admits a generated name and one with a supplementary character")
        void admitsValidNames() {
            var pattern = pattern(TRAILER, "name");
            var generated = Generators.letterStrings(1, 20).next();

            var rejected = rejected(pattern, List.of(generated, "a" + SUPPLEMENTARY));
            if (!CommitTrailer.isValidName(generated)) {
                rejected.add(generated);
            }

            assertEquals(List.of(), rejected, "The pattern admits a name the code admits");
        }
    }

    @Nested
    @DisplayName("commit_trailer email")
    class TrailerEmail {

        @Test
        @DisplayName("the pattern refuses every C0 and C1 control, the white-space class, @ and the angle brackets")
        void refusesTheWholeControlAndSpaceClasses() {
            var pattern = pattern(TRAILER, "email");

            var admitted = admitted(pattern, emailValues(controls()));
            admitted.addAll(admitted(pattern, emailValues(WHITE_SPACE_ABOVE_C1)));
            admitted.addAll(admitted(pattern, emailValues(new int[]{0x20})));
            admitted.addAll(admitted(pattern, emails(ANGLE_BRACKETS)));
            admitted.addAll(admitted(pattern, emails(new String[]{"@"})));

            assertNotEquals(0, controls().length + WHITE_SPACE_ABOVE_C1.length + 3,
                    "The refused population is not empty");
            assertEquals(List.of(), admitted, "The pattern refuses every code point of the classes");
        }

        @Test
        @DisplayName("the pattern admits U+FEFF, the one white-space code point ECMA-262 names and Java does not")
        void admitsTheZeroWidthNoBreakSpace() {
            var pattern = pattern(TRAILER, "email");
            var address = "a" + Character.toString(ZERO_WIDTH_NO_BREAK_SPACE) + "b@c.de";

            assertEquals(List.of(), rejected(pattern, List.of(address)),
                    "The pattern admits U+FEFF as the code does");
        }

        @Test
        @DisplayName("the pattern admits a plain address, as the code does")
        void admitsPlainAddress() {
            var pattern = pattern(TRAILER, "email");
            var address = "noreply@cuioss.de";

            var rejected = rejected(pattern, List.of(address));
            if (!CommitTrailer.isValidEmail(address)) {
                rejected.add(address);
            }

            assertEquals(List.of(), rejected, "The pattern admits a plain address the code admits");
        }
    }

    @Nested
    @DisplayName("commit_identity name")
    class IdentityName {

        @Test
        @DisplayName("the pattern refuses every C0 and C1 control, the line separators and the angle brackets")
        void refusesTheWholeControlClass() {
            var pattern = pattern(IDENTITY, "name");

            var admitted = admitted(pattern, nameValues(controls()));
            admitted.addAll(admitted(pattern, nameValues(LINE_SEPARATORS)));
            admitted.addAll(admitted(pattern, names(ANGLE_BRACKETS)));

            assertEquals(List.of(), admitted, "The pattern refuses every listed code point");
        }

        @Test
        @DisplayName("the pattern admits a generated name")
        void admitsAGeneratedName() {
            var pattern = pattern(IDENTITY, "name");
            var generated = Generators.letterStrings(1, 20).next();

            assertEquals(List.of(), rejected(pattern, List.of(generated)),
                    "The pattern admits a generated identity name");
        }
    }

    @Nested
    @DisplayName("commit_identity email")
    class IdentityEmail {

        @Test
        @DisplayName("the pattern refuses every C0 and C1 control, the white-space class, @ and the angle brackets")
        void refusesTheWholeControlAndSpaceClasses() {
            var pattern = pattern(IDENTITY, "email");

            var admitted = admitted(pattern, emailValues(controls()));
            admitted.addAll(admitted(pattern, emailValues(WHITE_SPACE_ABOVE_C1)));
            admitted.addAll(admitted(pattern, emailValues(new int[]{0x20})));
            admitted.addAll(admitted(pattern, emails(ANGLE_BRACKETS)));
            admitted.addAll(admitted(pattern, emails(new String[]{"@"})));

            assertEquals(List.of(), admitted, "The pattern refuses every code point of the classes");
        }

        @Test
        @DisplayName("the pattern admits a plain address")
        void admitsPlainAddress() {
            var pattern = pattern(IDENTITY, "email");
            var address = "operator@example.org";

            assertEquals(List.of(), rejected(pattern, List.of(address)),
                    "The pattern admits a plain identity address");
        }
    }

    /** The candidates the pattern did admit, so one assertion reports all of them rather than the first. */
    private static List<String> admitted(Pattern pattern, List<String> candidates) {
        var admitted = new ArrayList<String>();
        for (var candidate : candidates) {
            if (pattern.matcher(candidate).matches()) {
                admitted.add(candidate);
            }
        }
        return admitted;
    }

    /** The candidates the pattern did not admit, for a half that must accept them. */
    private static List<String> rejected(Pattern pattern, List<String> candidates) {
        var rejected = new ArrayList<String>();
        for (var candidate : candidates) {
            if (!pattern.matcher(candidate).matches()) {
                rejected.add(candidate);
            }
        }
        return rejected;
    }

    private static int[] controls() {
        return IntStream.concat(IntStream.rangeClosed(0x00, 0x1f), IntStream.rangeClosed(0x7f, 0x9f)).toArray();
    }

    private static List<String> nameValues(int[] codePoints) {
        return Arrays.stream(codePoints).mapToObj(codePoint -> "a" + Character.toString(codePoint) + "b").toList();
    }

    private static List<String> names(String[] values) {
        return Arrays.stream(values).map(value -> "a" + value + "b").toList();
    }

    private static List<String> emailValues(int[] codePoints) {
        var values = new ArrayList<String>();
        for (var codePoint : codePoints) {
            var character = Character.toString(codePoint);
            values.add("a" + character + "b@c.de");
            values.add("a@b" + character + "c.de");
        }
        return values;
    }

    private static List<String> emails(String[] values) {
        var candidates = new ArrayList<String>();
        for (var value : values) {
            candidates.add("a" + value + "b@c.de");
            candidates.add("a@b" + value + "c.de");
        }
        return candidates;
    }

    private static String codePointName(int codePoint) {
        return String.format("U+%04X", codePoint);
    }

    private static Pattern pattern(String key, String half) {
        var definitions = SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs"));
        var settings = SchemaResource.object(definitions.get("settings"));
        var stored = SchemaResource.object(SchemaResource.object(settings.get("properties")).get(key));
        var declared = SchemaResource.object(SchemaResource.object(stored.get("properties")).get(half));
        return Pattern.compile(String.valueOf(declared.get("pattern")));
    }
}
