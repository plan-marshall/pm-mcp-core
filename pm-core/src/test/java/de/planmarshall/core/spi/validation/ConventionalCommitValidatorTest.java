/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Validator of a submitted commit message")
class ConventionalCommitValidatorTest {

    private static final String HEADER = "feat(core): add the registry";

    private final ConventionalCommitValidator validator = new ConventionalCommitValidator();

    private static List<String> keysAndLines(List<ContentViolation> violations) {
        return violations.stream().map(violation -> violation.ruleKey() + "@" + violation.line()).toList();
    }

    static Stream<Arguments> acceptedMessages() {
        return Stream.of(
                Arguments.of("a type and a description", "feat: add the registry"),
                Arguments.of("a scope", HEADER),
                Arguments.of("a breaking change mark after the scope", "feat(core)!: drop the old registry"),
                Arguments.of("a breaking change mark without a scope", "chore!: drop the old registry"),
                Arguments.of("a line end after the header", HEADER + "\n"),
                Arguments.of("a body after one blank line", HEADER + "\n\nThe registry refuses a second name."),
                Arguments.of("a body of several paragraphs and a footer",
                        HEADER + "\n\nFirst paragraph.\n\nSecond paragraph.\n\nBREAKING CHANGE: the old name is gone"),
                Arguments.of("a body that mentions the trailer inside a sentence",
                        HEADER + "\n\nThe server appends Co-Authored-By: itself."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedMessages")
    @DisplayName("accepts a message with")
    void acceptsMessage(String label, String message) {
        var violations = validator.validate(message);

        assertTrue(violations.isEmpty(), () -> label + " must be accepted, but: " + violations);
    }

    static Stream<Arguments> rejectedMessages() {
        return Stream.of(
                Arguments.of("an empty message", "", ConventionalCommitValidator.RULE_BLANK, 0),
                Arguments.of("white space only", " \n\t\n", ConventionalCommitValidator.RULE_BLANK, 0),
                Arguments.of("a header without a colon", "add the registry",
                        ConventionalCommitValidator.RULE_HEADER_FORM, 1),
                Arguments.of("an empty scope", "feat(): add the registry",
                        ConventionalCommitValidator.RULE_HEADER_FORM, 1),
                Arguments.of("a type that is not made of letters", "feat-2: add the registry",
                        ConventionalCommitValidator.RULE_HEADER_FORM, 1),
                Arguments.of("no space after the colon", "feat:add the registry",
                        ConventionalCommitValidator.RULE_HEADER_FORM, 1),
                Arguments.of("two spaces after the colon", "feat:  add the registry",
                        ConventionalCommitValidator.RULE_HEADER_FORM, 1),
                Arguments.of("no type", ": add the registry", ConventionalCommitValidator.RULE_TYPE_EMPTY, 1),
                Arguments.of("a scope without a type", "(core): add the registry",
                        ConventionalCommitValidator.RULE_TYPE_EMPTY, 1),
                Arguments.of("nothing after the colon", "feat:",
                        ConventionalCommitValidator.RULE_DESCRIPTION_EMPTY, 1),
                Arguments.of("white space only after the colon", "feat(core):  ",
                        ConventionalCommitValidator.RULE_DESCRIPTION_EMPTY, 1),
                Arguments.of("a body directly below the header", HEADER + "\nThe registry refuses a second name.",
                        ConventionalCommitValidator.RULE_BODY_SEPARATION, 2),
                Arguments.of("two blank lines before the body", HEADER + "\n\n\nThe registry refuses a second name.",
                        ConventionalCommitValidator.RULE_BODY_SEPARATION, 3),
                Arguments.of("a trailer below the body",
                        HEADER + "\n\nThe registry refuses a second name.\n\nCo-Authored-By: Someone <someone@example.org>",
                        ConventionalCommitValidator.RULE_CO_AUTHORED_BY, 5),
                Arguments.of("a trailer in another spelling", HEADER + "\n\nBody.\n\n  co-authored-by : someone",
                        ConventionalCommitValidator.RULE_CO_AUTHORED_BY, 5),
                Arguments.of("a trailer between two other trailers",
                        HEADER + "\n\nRefs: #12\nCo-Authored-By: Someone <someone@example.org>\nReviewed-by: Other",
                        ConventionalCommitValidator.RULE_CO_AUTHORED_BY, 4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedMessages")
    @DisplayName("rejects a message with")
    void rejectsMessage(String label, String message, String ruleKey, int line) {
        var violations = validator.validate(message);

        assertEquals(List.of(ruleKey + "@" + line), keysAndLines(violations), label);
    }

    @Nested
    @DisplayName("Co-Authored-By trailer")
    class CoAuthoredBy {

        @Test
        @DisplayName("is rejected with its line when it is the only trailer")
        void onlyTrailer() {
            var message = HEADER + "\n\nCo-Authored-By: plan-marshall <noreply@cuioss.de>";

            var violations = validator.validate(message);

            assertEquals(List.of(ConventionalCommitValidator.RULE_CO_AUTHORED_BY + "@3"), keysAndLines(violations));
        }

        @Test
        @DisplayName("is rejected once for every line that supplies it")
        void everyLine() {
            var message = HEADER + "\n\nCo-Authored-By: One <one@example.org>\nCo-Authored-By: Two <two@example.org>";

            var violations = validator.validate(message);

            assertEquals(List.of(ConventionalCommitValidator.RULE_CO_AUTHORED_BY + "@3",
                    ConventionalCommitValidator.RULE_CO_AUTHORED_BY + "@4"), keysAndLines(violations));
        }

        @Test
        @DisplayName("is named in the message of the violation")
        void namedInMessage() {
            var trailer = "Co-Authored-By: Someone <someone@example.org>";

            var violations = validator.validate(HEADER + "\n\n" + trailer);

            assertTrue(violations.getFirst().message().contains(trailer), violations.getFirst().message());
        }
    }

    @Test
    @DisplayName("returns every violation of a message, in the order of the lines")
    void severalViolations() {
        var message = ": \nCo-Authored-By: Someone <someone@example.org>";

        var violations = validator.validate(message);

        assertEquals(List.of(ConventionalCommitValidator.RULE_TYPE_EMPTY + "@1",
                ConventionalCommitValidator.RULE_DESCRIPTION_EMPTY + "@1",
                ConventionalCommitValidator.RULE_BODY_SEPARATION + "@2",
                ConventionalCommitValidator.RULE_CO_AUTHORED_BY + "@2"), keysAndLines(violations));
    }

    @Test
    @DisplayName("refuses null instead of returning a violation")
    void nullText() {
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }
}
