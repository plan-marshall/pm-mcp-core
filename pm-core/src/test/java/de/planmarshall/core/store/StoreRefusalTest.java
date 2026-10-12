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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The one refusal of a store document or record (PM-IMPL-2): a closed set of outcomes, each naming the file; the
 * outcomes of the reader contract name the format version found and the current one.
 */
@DisplayName("Store refusal")
class StoreRefusalTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/mcp/status.json");
    private static final int CURRENT = 3;

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(StoreRefusal.absent(FILE, CURRENT), Outcome.ABSENT, "absent", OptionalInt.empty()),
                Arguments.of(StoreRefusal.unreadable(FILE, CURRENT, "field 'x' twice", null), Outcome.UNREADABLE,
                        "unreadable", OptionalInt.empty()),
                Arguments.of(StoreRefusal.ofVersion(FILE, 2, CURRENT), Outcome.FORMAT_OUTDATED, "format_outdated",
                        OptionalInt.of(2)),
                Arguments.of(StoreRefusal.ofVersion(FILE, 4, CURRENT), Outcome.FORMAT_NEWER, "format_newer",
                        OptionalInt.of(4)));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("outcomes")
    @DisplayName("every outcome carries the file, the version found and the current version")
    void outcome(StoreRefusal refusal, Outcome outcome, String code, OptionalInt found) {
        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(code, refusal.outcome().code()),
                () -> assertEquals(FILE, refusal.file()),
                () -> assertEquals(found, refusal.found()),
                () -> assertEquals(CURRENT, refusal.current()));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("outcomes")
    @DisplayName("the message names the file, the outcome and both versions")
    void message(StoreRefusal refusal, Outcome outcome, String code, OptionalInt found) {
        var message = refusal.getMessage();
        var version = found.isPresent() ? Integer.toString(found.getAsInt()) : "none";

        assertAll(
                () -> assertTrue(message.startsWith(FILE + ": " + code), message),
                () -> assertTrue(message.contains("format_version found " + version), message),
                () -> assertTrue(message.contains("current " + CURRENT), message));
    }

    @Test
    @DisplayName("the outcomes are exactly the four of the reader contract and the three of the enrolment")
    void closedOutcomes() {
        var codes = Stream.of(Outcome.values()).map(Outcome::code).toList();

        assertEquals(List.of("absent", "unreadable", "format_outdated", "format_newer", "enrolment_tampered",
                "enrolment_insecure", "enrolment_invalid"), codes);
    }

    /**
     * The integrity refusals of an enrolment file (PM-EXEC-2, PM-SEC-4, PM-SEC-5): a tampered file names the operator
     * command that re-baselines it, an insecure one the per-use check it failed; neither names a version found.
     */
    @Nested
    @DisplayName("enrolment integrity")
    class EnrolmentIntegrity {

        private static final Path ENROLMENT = Path.of("/base/enrolments/pm-mcp-core.json");
        private static final String PROJECT = "pm-mcp-core";
        private static final String REASON = "build_environment: GH_TOKEN is a credential variable";

        @Test
        @DisplayName("a tampered enrolment names the file, the detail and the accept-edit command of its project")
        void tampered() {
            var refusal = StoreRefusal.enrolmentTampered(ENROLMENT, "pm-mcp-core", "HMAC mismatch");

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome()),
                    () -> assertEquals(ENROLMENT, refusal.file()),
                    () -> assertEquals(OptionalInt.empty(), refusal.found()),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current()),
                    () -> assertTrue(refusal.getMessage().startsWith(ENROLMENT + ": enrolment_tampered: HMAC mismatch"),
                            refusal.getMessage()),
                    () -> assertTrue(refusal.getMessage().contains("'pm-operator accept-edit enrolment pm-mcp-core'"),
                            refusal.getMessage()));
        }

        @Test
        @DisplayName("an insecure enrolment names the file and the reason, and no command")
        void insecure() {
            var refusal = StoreRefusal.enrolmentInsecure(ENROLMENT, "mode 0644 is broader than 0600");

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                    () -> assertEquals(OptionalInt.empty(), refusal.found()),
                    () -> assertEquals(ENROLMENT + ": enrolment_insecure: mode 0644 is broader than 0600",
                            refusal.getMessage()));
        }

        @Test
        @DisplayName("an invalid enrolment names the file, the code and the reason, and no version found")
        void invalid() {
            var refusal = StoreRefusal.enrolmentInvalid(ENROLMENT, PROJECT, REASON);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INVALID, refusal.outcome()),
                    () -> assertEquals("enrolment_invalid", refusal.outcome().code()),
                    () -> assertEquals(ENROLMENT, refusal.file()),
                    () -> assertEquals(OptionalInt.empty(), refusal.found()),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current()),
                    () -> assertEquals(ENROLMENT + ": enrolment_invalid: " + REASON, refusal.getMessage()));
        }

        @Test
        @DisplayName("an invalid enrolment without a file, a project or a reason is refused, and so is an invalid id")
        void invalidMissingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(null, PROJECT, REASON)),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(ENROLMENT, null, REASON)),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInvalid(ENROLMENT, PROJECT, null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> StoreRefusal.enrolmentInvalid(ENROLMENT, "../x", REASON)));
        }

        @Test
        @DisplayName("a refusal without a file, a project, a detail or a reason is refused, and so is an invalid id")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(null, "pm-mcp-core", "d")),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(ENROLMENT, null, "d")),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentTampered(ENROLMENT, "pm-mcp-core", null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> StoreRefusal.enrolmentTampered(ENROLMENT, "../x", "d")),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.enrolmentInsecure(null, "r")),
                    () -> assertThrows(NullPointerException.class,
                            () -> StoreRefusal.enrolmentInsecure(ENROLMENT, null)));
        }
    }

    @Nested
    @DisplayName("format version")
    class FormatVersion {

        @Test
        @DisplayName("an older and a newer format version are told apart, the current one is no refusal")
        void olderNewerAndCurrent() {
            assertAll(
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, StoreRefusal.ofVersion(FILE, 0, 1).outcome()),
                    () -> assertEquals(Outcome.FORMAT_NEWER, StoreRefusal.ofVersion(FILE, 2, 1).outcome()),
                    () -> assertThrows(IllegalArgumentException.class, () -> StoreRefusal.ofVersion(FILE, 1, 1)));
        }
    }

    @Nested
    @DisplayName("unreadable content")
    class Unreadable {

        @Test
        @DisplayName("the detail follows the file and versions, and the failure of the parser is the cause")
        void detailAndCause() {
            var cause = new IOException("Unexpected character");

            var refusal = StoreRefusal.unreadable(FILE, 1, "'leases' must be an array", cause);

            assertAll(
                    () -> assertTrue(refusal.getMessage().endsWith(": 'leases' must be an array"),
                            refusal.getMessage()),
                    () -> assertSame(cause, refusal.getCause()));
        }

        @Test
        @DisplayName("an absent file has no detail and no cause")
        void absentWithoutCause() {
            var refusal = StoreRefusal.absent(FILE, 1);

            assertAll(
                    () -> assertNull(refusal.getCause()),
                    () -> assertTrue(refusal.getMessage().endsWith("current 1)"), refusal.getMessage()));
        }

        @Test
        @DisplayName("a refusal without a file or a detail is refused")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.absent(null, 1)),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.unreadable(null, 1, "d", null)),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.unreadable(FILE, 1, null, null)),
                    () -> assertThrows(NullPointerException.class, () -> StoreRefusal.ofVersion(null, 2, 1)));
        }
    }
}
