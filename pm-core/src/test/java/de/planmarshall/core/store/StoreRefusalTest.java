/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The one refusal of a store document or record (PM-IMPL-2): a closed set of outcomes, each naming the file; the
 * outcomes of the reader contract name the format version found and the current one. The outcome codes, the message
 * layout and the operator command are contracts, and so are the format versions around the current one; the store
 * file, the project id and the details of a refusal are generated.
 */
@EnableGeneratorController
@DisplayName("Store refusal")
class StoreRefusalTest {

    private static final int CURRENT = 3;

    private Path file;

    @BeforeEach
    void createFile() {
        file = Path.of("/base/projects", lowerName(), "plans", "plan-" + lowerName(), "mcp", lowerName() + ".json");
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a detail with the quotes and spaces a parser puts into its reason */
    private static String detail() {
        return "field '" + lowerName() + "' " + lowerName();
    }

    static Stream<Arguments> outcomes() {
        BiFunction<Path, String, StoreRefusal> absent = (file, detail) -> StoreRefusal.absent(file, CURRENT);
        BiFunction<Path, String, StoreRefusal> unreadable =
                (file, detail) -> StoreRefusal.unreadable(file, CURRENT, detail, null);
        BiFunction<Path, String, StoreRefusal> outdated = (file, detail) -> StoreRefusal.ofVersion(file, 2, CURRENT);
        BiFunction<Path, String, StoreRefusal> newer = (file, detail) -> StoreRefusal.ofVersion(file, 4, CURRENT);
        return Stream.of(
                Arguments.of(absent, Outcome.ABSENT, "absent", OptionalInt.empty()),
                Arguments.of(unreadable, Outcome.UNREADABLE, "unreadable", OptionalInt.empty()),
                Arguments.of(outdated, Outcome.FORMAT_OUTDATED, "format_outdated", OptionalInt.of(2)),
                Arguments.of(newer, Outcome.FORMAT_NEWER, "format_newer", OptionalInt.of(4)));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("outcomes")
    @DisplayName("every outcome carries the file, the version found and the current version (codes are wire contract)")
    void outcome(BiFunction<Path, String, StoreRefusal> factory, Outcome outcome, String code, OptionalInt found) {
        var refusal = factory.apply(file, detail());

        assertAll(
                () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry its outcome"),
                () -> assertEquals(code, refusal.outcome().code(), "the outcome should carry its wire code"),
                () -> assertEquals(file, refusal.file(), "the refusal should name the file"),
                () -> assertEquals(found, refusal.found(), "the refusal should name the version found"),
                () -> assertEquals(CURRENT, refusal.current(), "the refusal should name the current version"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("outcomes")
    @DisplayName("the message names the file, the outcome and both versions (message layout is the contract)")
    void message(BiFunction<Path, String, StoreRefusal> factory, Outcome outcome, String code, OptionalInt found) {
        var message = factory.apply(file, detail()).getMessage();
        var version = found.isPresent() ? Integer.toString(found.getAsInt()) : "none";

        assertAll(
                () -> assertTrue(message.startsWith(file + ": " + code), "the message should start with file and code"),
                () -> assertTrue(message.contains("format_version found " + version),
                        "the message should name the version found"),
                () -> assertTrue(message.contains("current " + CURRENT),
                        "the message should name the current version"));
    }

    @Test
    @DisplayName("the outcomes are exactly the four of the reader contract, the three of the enrolment and the conflict")
    void closedOutcomes() {
        var codes = Stream.of(Outcome.values()).map(Outcome::code).toList();

        assertEquals(List.of("absent", "unreadable", "format_outdated", "format_newer", "enrolment_tampered",
                        "enrolment_insecure", "enrolment_invalid", "conflict"), codes,
                "the outcome codes should be the closed set");
    }

    /**
     * The integrity refusals of an enrolment file (PM-EXEC-2, PM-SEC-4, PM-SEC-5): a tampered file names the operator
     * command that re-baselines it, an insecure one the per-use check it failed; neither names a version found.
     */
    @Nested
    @DisplayName("enrolment integrity")
    class EnrolmentIntegrity {

        // Fixture value of a project id that leaves its directory.
        private static final String ESCAPING_ID = "../x";

        private String project;
        private Path enrolment;
        private String reason;

        @BeforeEach
        void createEnrolment() {
            project = "pm-" + lowerName();
            enrolment = Path.of("/base/enrolments", project + ".json");
            reason = lowerName() + ": " + lowerName().toUpperCase(Locale.ROOT) + " is a credential variable";
        }

        @Test
        @DisplayName("a tampered enrolment names the file, the detail and the accept-edit command of its project")
        void tampered() {
            var detail = detail();
            var refusal = StoreRefusal.enrolmentTampered(enrolment, project, detail);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome(),
                            "the outcome should be ENROLMENT_TAMPERED"),
                    () -> assertEquals(enrolment, refusal.file(), "the refusal should name the enrolment"),
                    () -> assertEquals(OptionalInt.empty(), refusal.found(), "no version found should be named"),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current(),
                            "the current version should be the enrolment's"),
                    () -> assertTrue(refusal.getMessage().startsWith(enrolment + ": enrolment_tampered: " + detail),
                            "the message should start with file and detail"),
                    // the operator command is the contract
                    () -> assertTrue(refusal.getMessage().contains("'pm-operator accept-edit enrolment " + project + "'"),
                            "the message should name the accept-edit command"));
        }

        @Test
        @DisplayName("an insecure enrolment names the file and the reason, and no command")
        void insecure() {
            var refusal = StoreRefusal.enrolmentInsecure(enrolment, reason);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome(),
                            "the outcome should be ENROLMENT_INSECURE"),
                    () -> assertEquals(OptionalInt.empty(), refusal.found(), "no version found should be named"),
                    () -> assertEquals(enrolment + ": enrolment_insecure: " + reason, refusal.getMessage(),
                            "the message should name file, code and reason"));
        }

        @Test
        @DisplayName("an invalid enrolment names the file, the code and the reason, and no version found")
        void invalid() {
            var refusal = StoreRefusal.enrolmentInvalid(enrolment, project, reason);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INVALID, refusal.outcome(),
                            "the outcome should be ENROLMENT_INVALID"),
                    () -> assertEquals("enrolment_invalid", refusal.outcome().code(),
                            "the wire code should be enrolment_invalid"),
                    () -> assertEquals(enrolment, refusal.file(), "the refusal should name the enrolment"),
                    () -> assertEquals(OptionalInt.empty(), refusal.found(), "no version found should be named"),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current(),
                            "the current version should be the enrolment's"),
                    () -> assertEquals(enrolment + ": enrolment_invalid: " + reason, refusal.getMessage(),
                            "the message should name file, code and reason"));
        }

        @Test
        @DisplayName("an invalid enrolment without a file, a project or a reason is refused, and so is an invalid id")
        void invalidMissingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(null, project, reason),
                            "a refusal without a file should fail"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(enrolment, null, reason),
                            "a refusal without a project should fail"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(enrolment, project, null),
                            "a refusal without a reason should fail"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> StoreRefusal.enrolmentInvalid(enrolment, ESCAPING_ID, reason),
                            "an escaping project id should be refused"));
        }

        @Test
        @DisplayName("a refusal without a file, a project, a detail or a reason is refused, and so is an invalid id")
        void missingArguments() {
            var detail = detail();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(null, project, detail),
                            "a refusal without a file should fail"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(enrolment, null, detail),
                            "a refusal without a project should fail"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(enrolment, project, null),
                            "a refusal without a detail should fail"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> StoreRefusal.enrolmentTampered(enrolment, ESCAPING_ID, detail),
                            "an escaping project id should be refused"),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.enrolmentInsecure(null, reason),
                            "an insecure refusal needs a file"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInsecure(enrolment, null),
                            "an insecure refusal needs a reason"));
        }
    }

    @Nested
    @DisplayName("format version")
    class FormatVersion {

        @Test
        @DisplayName("an older and a newer format version are told apart, the current one is no refusal (boundaries)")
        void olderNewerAndCurrent() {
            assertAll(
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, StoreRefusal.ofVersion(file, 0, 1).outcome(),
                            "an older version should be FORMAT_OUTDATED"),
                    () -> assertEquals(Outcome.FORMAT_NEWER, StoreRefusal.ofVersion(file, 2, 1).outcome(),
                            "a newer version should be FORMAT_NEWER"),
                    () -> assertThrows(IllegalArgumentException.class, () -> StoreRefusal.ofVersion(file, 1, 1),
                            "the current version should be no refusal"));
        }
    }

    @Nested
    @DisplayName("unreadable content")
    class Unreadable {

        @Test
        @DisplayName("the detail follows the file and versions, and the failure of the parser is the cause")
        void detailAndCause() {
            var cause = new IOException(lowerName());
            var detail = "'" + lowerName() + "' must be an array";

            var refusal = StoreRefusal.unreadable(file, 1, detail, cause);

            assertAll(
                    () -> assertTrue(refusal.getMessage().endsWith(": " + detail),
                            "the message should end with the detail"),
                    () -> assertSame(cause, refusal.getCause(), "the parser failure should be the cause"));
        }

        @Test
        @DisplayName("an absent file has no detail and no cause")
        void absentWithoutCause() {
            var refusal = StoreRefusal.absent(file, 1);

            assertAll(
                    () -> assertNull(refusal.getCause(), "an absent file should have no cause"),
                    () -> assertTrue(refusal.getMessage().endsWith("current 1)"),
                            "the message should end with the version"));
        }

        @Test
        @DisplayName("a refusal without a file or a detail is refused")
        void missingArguments() {
            var detail = detail();

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.absent(null, 1),
                            "an absent refusal needs a file"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.unreadable(null, 1, detail, null),
                            "an unreadable refusal needs a file"),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.unreadable(file, 1, null, null),
                            "an unreadable refusal needs a detail"),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.ofVersion(null, 2, 1),
                            "a version refusal needs a file"));
        }
    }
}
