/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.tools.logging.LogRecord;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.log.CoreLogMessages;

/**
 * The holder numbers every message of {@code pm-runtime} once (PM-IMPL-1): two messages with one identifier cannot be
 * told apart in a log, and an identifier outside the range of its level reads as another level.
 */
@DisplayName("RuntimeLogMessages")
class RuntimeLogMessagesTest {

    private static final String QUEUE_LOCK = "/pm-base/projects/sample-project/locks/queue.lock";
    private static final String PLAN_LOCK = "/pm-base/projects/sample-project/locks/plan-plan-one.lock";

    /** The documentation of every log message of the repository, from the module directory the tests run in. */
    private static final Path LOG_MESSAGES = Path.of("..", "doc", "LogMessages.adoc");

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix")
    void ranges(String level, int first, int last) throws Exception {
        var records = records(level);

        assertFalse(records.isEmpty(), level);
        for (LogRecord logRecord : records) {
            assertEquals(RuntimeLogMessages.PREFIX, logRecord.getPrefix());
            assertTrue(logRecord.getIdentifier() >= first && logRecord.getIdentifier() <= last,
                    logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("no identifier is used twice")
    void unique() throws Exception {
        var all = new ArrayList<LogRecord>();
        for (String level : List.of("INFO", "WARN", "ERROR")) {
            all.addAll(records(level));
        }
        var seen = new HashSet<Integer>();

        for (LogRecord logRecord : all) {
            assertTrue(seen.add(logRecord.getIdentifier()), logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("the prefix is not the prefix of the holder of pm-core")
    void prefixOwn() {
        assertNotEquals(CoreLogMessages.PREFIX, RuntimeLogMessages.PREFIX);
    }

    static Stream<Arguments> enrolmentPolicyRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.WARN.ENROLMENT_INVALID, "PM_RUNTIME-120",
                        "Enrolment of project '%s' refused with outcome 'enrolment_invalid': %s"),
                Arguments.of(RuntimeLogMessages.WARN.ENROLMENT_AUDIT_REPLAY_FAILED, "PM_RUNTIME-121",
                        "Replay of the owed audit records of unenrolled projects failed: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("enrolmentPolicyRecords")
    @DisplayName("the records of the enrolment policy and the owed-audit replay")
    void enrolmentPolicy(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    static Stream<Arguments> verifyNotResolveRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.WARN.JOB_REFUSED, "PM_RUNTIME-122",
                        "Job of project '%s' refused with reason '%s': %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("verifyNotResolveRecords")
    @DisplayName("the records of the verify-not-resolve pipeline")
    void verifyNotResolve(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    static Stream<Arguments> sessionLogAndGenerationsRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.PENDING_ENTRY_RECORDS_SWEPT, "PM_RUNTIME-23",
                        "Periodic sweep removed %s pending entry records of project '%s'"),
                Arguments.of(RuntimeLogMessages.INFO.FENCED_SESSION_GENERATIONS_SWEPT, "PM_RUNTIME-24",
                        "Periodic sweep removed %s fenced session generations of project '%s'"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sessionLogAndGenerationsRecords")
    @DisplayName("the records of the two sweep steps of the session log and the session generations")
    void sessionLogAndGenerations(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    static Stream<Arguments> jobRunnerRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.JOB_STARTED, "PM_RUNTIME-26",
                        "Job '%s' of kind '%s' started (pid %s, budget %s seconds)"),
                Arguments.of(RuntimeLogMessages.INFO.JOB_SETTLED, "PM_RUNTIME-27",
                        "Job '%s' settled with fate '%s' (outcome row %s)"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_TERMINATION_ESCALATED, "PM_RUNTIME-128",
                        "Job '%s' outlived the grace period after SIGTERM, process group %s sent SIGKILL (cause '%s')"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_LAUNCHER_NOT_STARTED, "PM_RUNTIME-129",
                        "Launcher of job '%s' not started, the job is settled as exit 127: %s"),
                Arguments.of(RuntimeLogMessages.ERROR.JOB_SETTLEMENT_STEP_FAILED, "PM_RUNTIME-207",
                        "Settlement of job '%s' stopped at step '%s': %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("jobRunnerRecords")
    @DisplayName("the records of the subprocess job runner")
    void jobRunner(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("jobRunnerRecords")
    @DisplayName("the records of the subprocess job runner are documented in doc/LogMessages.adoc")
    void jobRunnerDocumented(LogRecord logRecord, String identifier, String template) throws Exception {
        var documentation = Files.readString(LOG_MESSAGES);

        assertTrue(documentation.contains("|%s |SubprocessJobRunner |%s |".formatted(identifier, template)),
                identifier);
    }

    @Nested
    @DisplayName("locks and leases")
    class LocksAndLeases {

        @Test
        @DisplayName("the messages of locks and leases carry the identifiers 11, 107 and 202")
        void identifiers() {
            assertAll(
                    () -> assertEquals(11, RuntimeLogMessages.INFO.STALE_LEASE_SWEEP_FINISHED.getIdentifier()),
                    () -> assertEquals(107, RuntimeLogMessages.WARN.LEASE_ORPHANED.getIdentifier()),
                    () -> assertEquals(202, RuntimeLogMessages.ERROR.LOCK_ORDER_VIOLATION.getIdentifier()));
        }

        @Test
        @DisplayName("the lock order violation message names the requested and the held lock paths")
        void lockOrderViolation() {
            var message = RuntimeLogMessages.ERROR.LOCK_ORDER_VIOLATION.format(PLAN_LOCK, List.of(QUEUE_LOCK));

            assertAll(
                    () -> assertTrue(message.contains(PLAN_LOCK), message),
                    () -> assertTrue(message.contains(QUEUE_LOCK), message));
        }
    }

    private static List<LogRecord> records(String level) throws ReflectiveOperationException {
        var holder = Class.forName(RuntimeLogMessages.class.getName() + "$" + level);
        var records = new ArrayList<LogRecord>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && LogRecord.class.isAssignableFrom(field.getType())) {
                records.add((LogRecord) field.get(null));
            }
        }
        return records;
    }
}
