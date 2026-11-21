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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.validation.ContentViolation;
import de.planmarshall.core.spi.validation.ConventionalCommitValidator;
import de.planmarshall.provider.git.primitive.CommitMessages.Appended;
import de.planmarshall.provider.git.primitive.CommitMessages.Refused;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The rules of a commit message: validation with the bound and the lone-surrogate and control-character refusals,
 * and the one writer of the
 * trailer line, which refuses a message that already holds one.
 */
@EnableGeneratorController
@DisplayName("CommitMessages")
class CommitMessagesTest {

    /** The key of the trailer line (the wire format of a git trailer). */
    private static final String TRAILER_KEY = "Co-Authored-By";

    private final TypedGenerator<String> messages = GitGenerators.commitMessages();

    private final TypedGenerator<CommitTrailer> trailers = GitGenerators.trailers();

    @Nested
    @DisplayName("validate")
    class Validate {

        @RepeatedTest(20)
        @DisplayName("a valid Conventional Commit message breaks no rule")
        void validMessage() {
            assertEquals(List.of(), CommitMessages.validate(messages.next()), "A valid message has no violation");
        }

        @Test
        @DisplayName("a message without type is refused on line 1")
        void withoutType() {
            var violations = CommitMessages.validate(": " + subjectOf(messages.next()));

            assertEquals(List.of(ConventionalCommitValidator.RULE_TYPE_EMPTY), rules(violations),
                    "A header without type is refused");
            assertEquals(1, violations.getFirst().line(), "The violation names the header line");
        }

        @Test
        @DisplayName("a blank message is refused for the whole text")
        void blank() {
            var violations = CommitMessages.validate("  \n ");

            assertEquals(List.of(ConventionalCommitValidator.RULE_BLANK), rules(violations),
                    "A blank message is refused as blank");
            assertEquals(0, violations.getFirst().line(), "The blank violation names the whole text");
        }

        @Test
        @DisplayName("a message of 2000 characters is accepted, one of 2001 refused")
        void lengthBound() {
            var header = messages.next().lines().findFirst().orElseThrow() + "\n\n";
            var longest = header + "b".repeat(CommitMessages.MAX_LENGTH - header.length());

            var violations = CommitMessages.validate(longest + "b");

            assertAll("length bound",
                    () -> assertEquals(List.of(), CommitMessages.validate(longest),
                            "A message of the largest length passes"),
                    () -> assertEquals(List.of(CommitMessages.RULE_LENGTH), rules(violations),
                            "A message beyond the bound is refused"),
                    () -> assertEquals(0, violations.getFirst().line(), "The length violation names the text"));
        }

        @Test
        @DisplayName("a lone surrogate is refused naming its line, a paired one is accepted")
        void loneSurrogate() {
            var header = messages.next().lines().findFirst().orElseThrow();

            var violations = CommitMessages.validate(header + "\n\nbody \uD83D tail");

            assertAll("surrogates",
                    () -> assertEquals(List.of(CommitMessages.RULE_LONE_SURROGATE), rules(violations),
                            "A lone high surrogate is refused"),
                    () -> assertEquals(3, violations.getFirst().line(), "The violation names the body line"),
                    () -> assertEquals(List.of(), CommitMessages.validate(header + "\n\nbody 😀 tail"),
                            "A paired surrogate is a valid character"),
                    () -> assertEquals(List.of(CommitMessages.RULE_LONE_SURROGATE),
                            rules(CommitMessages.validate(header + " \uDE00")),
                            "A lone low surrogate is refused too"));
        }

        // the control characters the input type commit_message refuses: C0 but the line breaks, DEL and C1
        @ParameterizedTest(name = "U+{0}")
        @ValueSource(ints = {0x00, 0x09, 0x1b, 0x1f, 0x7f, 0x85, 0x9f})
        @DisplayName("a control character is refused naming its line")
        void controlCharacter(int codePoint) {
            var header = messages.next().lines().findFirst().orElseThrow();

            var violations = CommitMessages.validate(header + "\n\nbody " + Character.toString(codePoint) + " tail");

            assertAll("control character",
                    () -> assertTrue(rules(violations).contains(CommitMessages.RULE_CONTROL),
                            "The control character is refused"),
                    () -> assertTrue(violations.stream().anyMatch(violation -> violation.line() == 3),
                            "The violation names the body line"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.provider.git.primitive.CommitMessagesTest#trailerLines")
        @DisplayName("a trailer line in any case and position is refused naming its line")
        void trailerLineIsRefused(String trailerLine, int position) {
            var message = withLineAt(messages.next(), trailerLine, position);

            var violations = CommitMessages.validate(message.text());

            assertTrue(violations.stream().anyMatch(violation -> isTrailerViolation(violation, message.line())),
                    "The trailer line is refused by its line");
        }
    }

    @Nested
    @DisplayName("withTrailer")
    class WithTrailer {

        @RepeatedTest(20)
        @DisplayName("appends exactly one trailer after one blank line and ends with one line feed")
        void appendsOneTrailer() {
            var message = messages.next();
            var trailer = trailers.next();

            var outcome = CommitMessages.withTrailer(message + "\n\n\n", trailer);

            var text = assertInstanceOf(Appended.class, outcome, "A message without trailer is appended").message();
            var trailerLine = TRAILER_KEY + ": " + trailer.name() + " <" + trailer.email() + ">";
            assertAll("rendered message",
                    () -> assertEquals(message + "\n\n" + trailerLine + "\n", text,
                            "The trailer follows one blank line"),
                    () -> assertEquals(1, text.lines().filter(line -> line.startsWith(TRAILER_KEY)).count(),
                            "Exactly one trailer line is in the result"),
                    () -> assertFalse(text.endsWith("\n\n"), "The result ends with a single line feed"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.provider.git.primitive.CommitMessagesTest#trailerLines")
        @DisplayName("refuses a message that already holds a trailer line, naming the line")
        void refusesAnExistingTrailer(String trailerLine, int position) {
            var message = withLineAt(messages.next(), trailerLine, position);

            var outcome = CommitMessages.withTrailer(message.text(), trailers.next());

            var refused = assertInstanceOf(Refused.class, outcome, "A message with a trailer line is refused");
            assertTrue(isTrailerViolation(refused.violation(), message.line()),
                    "The refusal names the trailer rule and line");
        }

        @Test
        @DisplayName("refuses a trailer identical to the resolved value")
        void refusesTheResolvedTrailer() {
            var compiled = CommitTrailer.compiled();
            var header = messages.next().lines().findFirst().orElseThrow();
            var trailerLine = TRAILER_KEY + ": " + compiled.name() + " <" + compiled.email() + ">";

            var outcome = CommitMessages.withTrailer(header + "\n\n" + trailerLine, compiled);

            var refused = assertInstanceOf(Refused.class, outcome, "The resolved trailer itself is refused");
            assertEquals(3, refused.violation().line(), "The refusal names the trailer line");
        }
    }

    /**
     * A trailer line, in different cases and with leading white space, and the position it is put at: after the
     * header's blank line (3) or as the last line of a long body (7).
     */
    static Stream<Arguments> trailerLines() {
        return Stream.of(
                Arguments.of(TRAILER_KEY + ": someone <someone@example.org>", 3),
                Arguments.of("co-authored-by: someone <someone@example.org>", 7),
                Arguments.of("CO-AUTHORED-BY: someone <someone@example.org>", 3),
                Arguments.of("  Co-authored-by : someone", 7));
    }

    private static boolean isTrailerViolation(ContentViolation violation, int line) {
        return ConventionalCommitValidator.RULE_CO_AUTHORED_BY.equals(violation.ruleKey()) && violation.line() == line;
    }

    /** Puts the line at the position: line 3 after the header and a blank line, or after a body of three lines. */
    private static Placed withLineAt(String generated, String line, int position) {
        var header = generated.lines().findFirst().orElseThrow();
        if (position == 3) {
            return new Placed(header + "\n\n" + line, 3);
        }
        return new Placed(header + "\n\nfirst body line\nsecond body line\nthird body line\n\n" + line, 7);
    }

    private static String subjectOf(String message) {
        var header = message.lines().findFirst().orElseThrow();
        return header.substring(header.indexOf(": ") + 2);
    }

    private static List<String> rules(List<ContentViolation> violations) {
        return violations.stream().map(ContentViolation::ruleKey).toList();
    }

    private record Placed(String text, int line) {
    }
}
