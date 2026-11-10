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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.tools.logging.LogRecord;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.log.LogMessageListing;

/**
 * The holder numbers every message of {@code pm-runtime} once (PM-IMPL-1): two messages with one identifier cannot be
 * told apart in a log, and an identifier outside the range of its level reads as another level.
 * <p>
 * The identifiers, level ranges, templates and the generated listing are the specified catalogue and stay literal; the
 * lock paths of the lock-order fixture are generated.
 */
@EnableGeneratorController
@DisplayName("RuntimeLogMessages")
class RuntimeLogMessagesTest {

    /** The generated listing of this holder, resolved from the repository root above the module. */
    private static final Path GENERATED = repositoryRoot().resolve("doc").resolve("log-messages")
            .resolve("pm-runtime.adoc");

    /** Setting this system property regenerates the committed listing instead of only comparing it. */
    private static final String REGENERATE = "planmarshall.log-messages.regenerate";

    /** Contract literals: the level holders of the catalogue. */
    private static final List<String> LEVELS = List.of("INFO", "WARN", "ERROR");

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix (the specified ranges)")
    void ranges(String level, int first, int last) throws Exception {
        var records = records(level);

        assertFalse(records.isEmpty(), "every level must hold at least one message");
        for (LogRecord logRecord : records) {
            assertEquals(RuntimeLogMessages.PREFIX, logRecord.getPrefix(), "every message carries the prefix");
            assertTrue(logRecord.getIdentifier() >= first && logRecord.getIdentifier() <= last,
                    "out of the range of its level: " + logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("no identifier is used twice")
    void unique() throws Exception {
        var all = allRecords();
        var seen = new HashSet<Integer>();

        for (LogRecord logRecord : all) {
            assertTrue(seen.add(logRecord.getIdentifier()),
                    "identifier used twice: " + logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("the prefix is not the prefix of the holder of pm-core")
    void prefixOwn() {
        assertNotEquals(CoreLogMessages.PREFIX, RuntimeLogMessages.PREFIX,
                "the runtime must not share the prefix of pm-core");
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
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
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
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> sweepRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.PENDING_ENTRY_RECORDS_SWEPT, "PM_RUNTIME-23",
                        "Periodic sweep removed %s pending entry records of project '%s'"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sweepRecords")
    @DisplayName("the record of the sweep's pending-entry step")
    void sweep(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> credentialStoreRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.CREDENTIAL_STORE_SELECTED, "PM_RUNTIME-9",
                        "Credential store '%s' recorded, service name '%s'"),
                Arguments.of(RuntimeLogMessages.INFO.CREDENTIAL_STORE_RECORDED, "PM_RUNTIME-26",
                        "Credential store '%s' recorded by %s, machine configuration revision %s"),
                Arguments.of(RuntimeLogMessages.WARN.RECORDED_OS_STORE_UNAVAILABLE, "PM_RUNTIME-128",
                        "Recorded credential store '%s' unavailable, no other store serves: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("credentialStoreRecords")
    @DisplayName("the records of the recorded credential store")
    void credentialStore(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> credentialStoreMoveRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.CREDENTIAL_STORE_MOVE_STARTED, "PM_RUNTIME-27",
                        "Credential store move from '%s' to '%s' started"),
                Arguments.of(RuntimeLogMessages.INFO.CREDENTIAL_STORE_MOVED, "PM_RUNTIME-28",
                        "Credential store moved from '%s' to '%s', %s entries"),
                Arguments.of(RuntimeLogMessages.INFO.CREDENTIAL_STORE_MOVE_RESUMED, "PM_RUNTIME-29",
                        "Credential store move from '%s' to '%s' resumed from phase '%s' and completed"),
                Arguments.of(RuntimeLogMessages.WARN.CREDENTIAL_STORE_MOVE_FAILED, "PM_RUNTIME-129",
                        "Credential store move from '%s' to '%s' failed before the switch, %s: %s"),
                Arguments.of(RuntimeLogMessages.WARN.CREDENTIAL_STORE_ASPECT_MOVE_FAILED, "PM_RUNTIME-146",
                        "The credential store move of the aspect failed: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("credentialStoreMoveRecords")
    @DisplayName("the records of the move between the credential stores")
    void credentialStoreMove(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> jobRunnerRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.JOB_STARTED, "PM_RUNTIME-30",
                        "Job '%s' of kind '%s' started (pid %s, budget %s seconds)"),
                Arguments.of(RuntimeLogMessages.INFO.JOB_SETTLED, "PM_RUNTIME-31",
                        "Job '%s' settled with fate '%s' (outcome row %s)"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_TERMINATION_ESCALATED, "PM_RUNTIME-130",
                        "Job '%s' outlived the grace period after SIGTERM, process group %s sent SIGKILL (cause '%s')"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_LAUNCHER_NOT_STARTED, "PM_RUNTIME-131",
                        "Launcher of job '%s' not started, the job had no process: %s"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_TERMINATION_FAILED, "PM_RUNTIME-132",
                        "Termination of job '%s' failed, its process is destroyed through its handle: %s"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_DESCENDANT_AUDIT_FAILED, "PM_RUNTIME-137",
                        "The surviving descendant of job '%s' could not be audited: %s"),
                Arguments.of(RuntimeLogMessages.WARN.JOB_DESCENDANT_SWEEP_FAILED, "PM_RUNTIME-138",
                        "The descendant sweep of job '%s' failed, the job is settled without it: %s"),
                Arguments.of(RuntimeLogMessages.ERROR.JOB_SETTLEMENT_STEP_FAILED, "PM_RUNTIME-207",
                        "Settlement of job '%s' stopped at step '%s': %s"),
                Arguments.of(RuntimeLogMessages.ERROR.JOB_SUPERVISION_FAILED, "PM_RUNTIME-208",
                        "Supervision of job '%s' failed: %s"),
                Arguments.of(RuntimeLogMessages.ERROR.JOB_LAST_RESORT_FAILED, "PM_RUNTIME-209",
                        "Last-resort settlement of job '%s' failed: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("jobRunnerRecords")
    @DisplayName("the records of the subprocess job runner")
    void jobRunner(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> serviceUnitRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.SERVICE_UNIT_RESTORED, "PM_RUNTIME-33",
                        "Service unit '%s' restored and loaded from the copy a reinstall kept"),
                Arguments.of(RuntimeLogMessages.INFO.SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS, "PM_RUNTIME-34",
                        "Service unit '%s' removed, the reinstall kept no previous unit"),
                Arguments.of(RuntimeLogMessages.WARN.SERVICE_UNIT_RESTORE_FAILED, "PM_RUNTIME-135",
                        "Service unit '%s' could not be restored: %s"),
                Arguments.of(RuntimeLogMessages.WARN.SERVICE_UNIT_KEPT_NOT_DROPPED, "PM_RUNTIME-136",
                        "The kept state of service unit '%s' could not be removed after a successful load: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("serviceUnitRecords")
    @DisplayName("the records of the service unit restore")
    void serviceUnit(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    static Stream<Arguments> workerSupervisorRecords() {
        return Stream.of(
                Arguments.of(RuntimeLogMessages.INFO.WORKER_GENERATION_STARTED, "PM_RUNTIME-36",
                        "Worker generation '%s' of role '%s' started on harness '%s'"),
                Arguments.of(RuntimeLogMessages.INFO.WORKER_GENERATION_ENDED, "PM_RUNTIME-37",
                        "Worker generation '%s' of role '%s' ended with cause '%s'"),
                Arguments.of(RuntimeLogMessages.INFO.WORKER_GENERATION_RECYCLED, "PM_RUNTIME-38",
                        "Worker generation '%s' of role '%s' recycled for '%s'"),
                Arguments.of(RuntimeLogMessages.WARN.WORKER_START_FAILED, "PM_RUNTIME-140",
                        "Start of a worker of role '%s' on harness '%s' failed (consecutive failure %s): %s"),
                Arguments.of(RuntimeLogMessages.WARN.WORKER_START_GUARD_HELD, "PM_RUNTIME-141",
                        "Start-failure guard of role '%s' on harness '%s' holds after %s consecutive failures"),
                Arguments.of(RuntimeLogMessages.WARN.AGENT_STARTED_DETECTED, "PM_RUNTIME-142",
                        "Agent started in the process tree of worker generation '%s' of role '%s': '%s' (%s)"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("workerSupervisorRecords")
    @DisplayName("the records of the worker supervisor")
    void workerSupervisor(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString(),
                        "the record must carry its specified identifier"),
                () -> assertEquals(template, logRecord.getTemplate(),
                        "the record must carry its specified template"));
    }

    @Test
    @DisplayName("the record of the former file-store fallback is gone and its identifier 104 is not reused")
    void fileStoreFallbackRemoved() throws Exception {
        var all = allRecords();

        assertAll(
                // Contract literals: the retired identifier and a phrase of the retired template
                () -> assertTrue(all.stream().noneMatch(logRecord -> logRecord.getIdentifier() == 104),
                        "the identifier 104 must not be reused"),
                () -> assertTrue(all.stream()
                                .noneMatch(logRecord -> logRecord.getTemplate().contains("file credential store serves")),
                        "the file-store fallback record must be gone"));
    }

    @Test
    @DisplayName("the committed listing is the holder's current generation")
    void generatedListingIsCurrent() throws Exception {
        var expected = LogMessageListing.render(RuntimeLogMessages.class).getBytes(UTF_8);
        if (Boolean.getBoolean(REGENERATE)) {
            Files.write(GENERATED, expected);
        }
        var actual = Files.readAllBytes(GENERATED);

        assertTrue(LogMessageListing.same(expected, actual), "the committed listing matches the holder");
    }

    @Test
    @DisplayName("the byte check names a file with one identifier changed")
    void byteCheckNamesAChangedListing(@TempDir Path tempDir) throws Exception {
        var expected = LogMessageListing.render(RuntimeLogMessages.class);
        var tampered = expected.replace("PM_RUNTIME-1 |", "PM_RUNTIME-99 |");
        var changed = tempDir.resolve("pm-runtime.adoc");
        Files.writeString(changed, tampered, UTF_8);

        assertAll(
                () -> assertNotEquals(expected, tampered, "the control changes one identifier"),
                () -> assertFalse(LogMessageListing.same(expected.getBytes(UTF_8), Files.readAllBytes(changed)),
                        "a changed identifier is a mismatch"));
    }

    @Nested
    @DisplayName("locks and leases")
    class LocksAndLeases {

        @Test
        @DisplayName("the messages of locks and leases carry the identifiers 11, 107 and 202")
        void identifiers() {
            assertAll(
                    () -> assertEquals(11, RuntimeLogMessages.INFO.STALE_LEASE_SWEEP_FINISHED.getIdentifier(),
                            "the stale lease sweep must carry 11"),
                    () -> assertEquals(107, RuntimeLogMessages.WARN.LEASE_ORPHANED.getIdentifier(),
                            "the orphaned lease must carry 107"),
                    () -> assertEquals(202, RuntimeLogMessages.ERROR.LOCK_ORDER_VIOLATION.getIdentifier(),
                            "the lock order violation must carry 202"));
        }

        @Test
        @DisplayName("the lock order violation message names the requested and the held lock paths")
        void lockOrderViolation() {
            var locks = "/" + lower() + "/projects/" + lower() + "/locks/";
            // Contract literals: the file names of the queue lock and of a plan lock
            var queueLock = locks + "queue.lock";
            var planLock = locks + "plan-" + lower() + ".lock";

            var message = RuntimeLogMessages.ERROR.LOCK_ORDER_VIOLATION.format(planLock, List.of(queueLock));

            assertAll(
                    () -> assertTrue(message.contains(planLock), "the message must name the requested lock"),
                    () -> assertTrue(message.contains(queueLock), "the message must name the held lock"));
        }
    }

    /** The directory above the working directory that holds {@code pm-core} and the root POM. */
    private static Path repositoryRoot() {
        var dir = Path.of("").toAbsolutePath();
        while (dir != null
                && !(Files.isDirectory(dir.resolve("pm-core")) && Files.isRegularFile(dir.resolve("pom.xml")))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "repository root not found above the working directory");
        return dir;
    }

    private static List<LogRecord> allRecords() throws ReflectiveOperationException {
        var all = new ArrayList<LogRecord>();
        for (String level : LEVELS) {
            all.addAll(records(level));
        }
        return all;
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
