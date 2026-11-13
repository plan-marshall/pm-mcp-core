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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.JournalRecovery.InvariantViolation;
import de.planmarshall.core.store.JournalRecovery.NothingToRecover;
import de.planmarshall.core.store.JournalRecovery.Outcome;
import de.planmarshall.core.store.JournalRecovery.RolledForward;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The roll-forward recovery of a scope (PM-IMPL-7): a commit stopped at any step boundary is completed by the next
 * recovery when it was stopped after its journal was written, and leaves the stores as they were when it was stopped
 * before; either way no journal and no staged file remains, every store has its content exactly once, and the sidecar
 * holds the digests of the stores.
 * <p>
 * A stop is what a killed process leaves: the step hook of the commit throws at the boundary, and nothing the commit
 * wrote is removed. The recovery then runs in a new transaction of the lock manager, as after a restart.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Journal recovery")
class JournalRecoveryTest {

    private static final String STATUS = "status.json";
    private static final String REFERENCES = "references.json";
    private static final String WORK_LOG = "logs/work.jsonl";
    private static final String OLD_STATUS = "{\"format_version\":1,\"phase\":\"4-plan\"}";
    private static final String NEW_STATUS = "{\"format_version\":1,\"phase\":\"5-execute\"}";
    private static final String NEW_REFERENCES = "{\"format_version\":1,\"files\":[]}";
    private static final String FIRST = "{\"format_version\":1,\"n\":1}\n";
    private static final String SECOND = "{\"format_version\":1,\"n\":2}\n";
    private static final List<String> TARGETS = List.of(STATUS, REFERENCES, WORK_LOG);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private String project;
    private String plan;
    private JournalScope scope;
    private RecordingLockManager locks;
    private Capture<AuditEvent> auditEvents;
    private AuditSink audit;

    /** What the step hook throws to stop a commit at a boundary. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite(Step step, int target) {
            super("stopped after " + step + " of target " + target);
        }
    }


    /**
     * Creates a consistent scope: the status and the work log are written by a commit, so the sidecar holds their
     * digests; the references do not exist yet.
     */
    @BeforeEach
    void createConsistentScope() throws IOException {
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        scope = JournalScope.plan(base, project, plan);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        auditEvents = newCapture(CaptureType.ALL);
        audit = AuditSinkDoubles.capturing(auditEvents);
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            write.replace(STATUS, bytes(OLD_STATUS));
            write.append(WORK_LOG, bytes(FIRST));
            write.commit();
        }
    }

    private static byte[] bytes(String... parts) {
        return String.join("", parts).getBytes(UTF_8);
    }

    private Path store(String relativePath) {
        return scope.resolve(relativePath);
    }

    /** @return every regular file below the root by its relative path, with its bytes in hexadecimal digits */
    private static Map<String, String> snapshot(Path root) throws IOException {
        var files = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (var file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString(), HexFormat.of().formatHex(Files.readAllBytes(file)));
            }
        }
        return files;
    }

    /**
     * Commits the status, the references and an append to the work log, and stops the commit at the boundary.
     *
     * @return the journal the stopped commit left; {@code null} if it was stopped before the journal was written
     */
    private TransactionJournal stopAt(Step stopStep, int stopTarget) {
        ScopeTransaction.StepHook hook = (step, target) -> {
            if (step == stopStep && target == stopTarget) {
                throw new StopWrite(step, target);
            }
        };
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
            write.replace(STATUS, bytes(NEW_STATUS));
            write.replace(REFERENCES, bytes(NEW_REFERENCES));
            write.append(WORK_LOG, bytes(SECOND));
            assertThrows(StopWrite.class, write::commit, "the commit stops at the boundary");
        }
        return StoreSnapshot.read(scope.journalFile()).content()
                .map(document -> TransactionJournal.read(scope.journalFile(), document)).orElse(null);
    }

    /** Recovers the scope in a new transaction, as after a restart. */
    private Outcome recover() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return JournalRecovery.recover(transaction, scope, audit);
        }
    }

    private List<AuditEvent> rolledForward() {
        return auditEvents.getValues().stream().filter(event -> AuditEvent.TRANSACTION_ROLLED_FORWARD.equals(event.event()))
                .toList();
    }

    private AuditEvent rollForwardOf(TransactionJournal journal) {
        return AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(), plan,
                journal.transactionId().value(), TARGETS);
    }

    private static byte[] committedSidecar() {
        return DigestSidecar.of(Map.of(
                STATUS, StoreDigest.of(bytes(NEW_STATUS)),
                REFERENCES, StoreDigest.of(bytes(NEW_REFERENCES)),
                WORK_LOG, StoreDigest.of(bytes(FIRST, SECOND)))).write();
    }

    /**
     * Every step boundary of the commit, in its order: the stages of the three targets, the journal, the applies of
     * the three targets, and the digests, after which only the close remains.
     *
     * @return the name of the boundary, the step and target it is after, and the targets the recovery applies; no
     *         targets list for a boundary before the journal, from which nothing is rolled forward
     */
    static Stream<Arguments> boundaries() {
        return Stream.of(
                Arguments.of("after staging the status", Step.STAGE, 0, null),
                Arguments.of("after staging the references", Step.STAGE, 1, null),
                Arguments.of("after staging the append", Step.STAGE, 2, null),
                Arguments.of("after the journal", Step.JOURNAL, 0, TARGETS),
                Arguments.of("after applying the status", Step.APPLY, 0, List.of(REFERENCES, WORK_LOG)),
                Arguments.of("after applying the references", Step.APPLY, 1, List.of(WORK_LOG)),
                Arguments.of("after applying the append", Step.APPLY, 2, List.of()),
                Arguments.of("after the digests, before the close", Step.DIGESTS, 0, List.of()));
    }

    @Nested
    @DisplayName("a commit stopped at a step boundary")
    class StoppedCommit {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.JournalRecoveryTest#boundaries")
        @DisplayName("is rolled forward after its journal and left undone before it, with nothing left behind")
        void stoppedAtBoundary(String name, Step step, int target, List<String> applied) throws Exception {
            var before = snapshot(scope.pathBase());
            var journal = stopAt(step, target);

            var outcome = recover();

            var after = snapshot(scope.pathBase());
            if (applied == null) {
                assertAll("a commit stopped before its journal",
                        () -> assertNull(journal, "no journal was written"),
                        () -> assertInstanceOf(NothingToRecover.class, outcome,
                                "nothing to recover before the journal"),
                        () -> assertEquals(target + 1, ((NothingToRecover) outcome).deletedStagedFiles().size(),
                                "every staged file was deleted"),
                        () -> assertEquals(before, after, "every store, and the sidecar, as before the commit"),
                        () -> assertEquals(List.of(), rolledForward(), "no roll-forward is recorded"));
                return;
            }
            assertAll("a commit rolled forward after its journal",
                    () -> assertEquals(new RolledForward(journal, applied), outcome,
                            "the outcome names the journal and targets"),
                    () -> assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS)),
                            "the status holds its new content"),
                    () -> assertArrayEquals(bytes(NEW_REFERENCES), Files.readAllBytes(store(REFERENCES)),
                            "the references hold their new content"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block appended exactly once"),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar holds the digests of the stores"),
                    () -> assertEquals(Set.of(STATUS, REFERENCES, WORK_LOG, "mcp/digests.json"), after.keySet(),
                            "no journal and no staged file remains"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), rolledForward(),
                            "exactly one transaction_rolled_forward"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), auditEvents.getValues(), "nothing else recorded"));
        }

        @Test
        @DisplayName("a second recovery after a roll-forward finds nothing and records nothing")
        void recoveredOnce() throws Exception {
            stopAt(Step.JOURNAL, 0);
            recover();
            var recovered = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("a second recovery after a roll-forward",
                    () -> assertEquals(new NothingToRecover(List.of()), outcome,
                            "the second recovery finds nothing"),
                    () -> assertEquals(recovered, snapshot(scope.pathBase()),
                            "no byte changes on the second recovery"),
                    () -> assertEquals(1, rolledForward().size(), "the roll-forward is recorded once"));
        }

        /**
         * A commit stopped while it wrote the sidecar leaves the temporary sidecar of its transaction id beside the
         * journal; the recovery writes the sidecar under the same id and must not fail on that file.
         */
        @Test
        @DisplayName("a temporary sidecar of the transaction left by a stop inside the digests step is replaced")
        void leftoverSidecarTemporary() throws Exception {
            var journal = stopAt(Step.APPLY, 2);
            var leftover = scope.sidecarFile().resolveSibling(journal.transactionId()
                    .stagedName(scope.sidecarFile().getFileName().toString(), TransactionJournal.Mode.REPLACE));
            Files.write(leftover, bytes(OLD_STATUS));

            var outcome = recover();

            assertAll("a stop inside the digests step",
                    () -> assertEquals(new RolledForward(journal, List.of()), outcome,
                            "the recovery rolls the commit forward"),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar holds the new digests"),
                    () -> assertFalse(Files.exists(leftover), "the temporary sidecar was consumed"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), rolledForward(),
                            "the roll-forward is recorded once"));
        }

        /** PM-WATCH-STORE-49, the crash half: an append torn after the journal is never applied twice. */
        @Test
        @DisplayName("an append torn after the journal is cut back to its size before and appended once")
        void tornAppend() throws Exception {
            var journal = stopAt(Step.APPLY, 1);
            var block = bytes(SECOND);
            Files.write(store(WORK_LOG), Arrays.copyOf(block, block.length / 2), StandardOpenOption.APPEND);

            var outcome = recover();

            assertAll("a torn append after the journal",
                    () -> assertEquals(new RolledForward(journal, List.of(WORK_LOG)), outcome,
                            "only the work log is applied again"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block is appended exactly once"),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar holds the new digests"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), auditEvents.getValues(),
                            "the cut is part of the roll-forward, not a tail repair"));
        }

        @Test
        @DisplayName("a torn append to a store of several buffer sizes is cut back and appended once, digests streamed")
        void tornAppendToLargeStore() throws Exception {
            var large = new ByteArrayOutputStream();
            while (large.size() < 4 * StoreDigest.Builder.BUFFER_SIZE + 5) {
                large.writeBytes(bytes(FIRST));
            }
            Files.write(store(WORK_LOG), large.toByteArray());
            var journal = stopAt(Step.APPLY, 1);
            var block = bytes(SECOND);
            Files.write(store(WORK_LOG), Arrays.copyOf(block, block.length / 2), StandardOpenOption.APPEND);
            large.writeBytes(block);

            var outcome = recover();

            assertAll("a torn append to a store of several buffer sizes",
                    () -> assertEquals(new RolledForward(journal, List.of(WORK_LOG)), outcome,
                            "only the work log is applied again"),
                    () -> assertArrayEquals(large.toByteArray(), Files.readAllBytes(store(WORK_LOG)),
                            "the large store is cut back and appended"),
                    () -> assertEquals(Optional.of(StoreDigest.of(large.toByteArray())),
                            DigestSidecar.load(scope.sidecarFile()).digest(WORK_LOG),
                            "the sidecar digests the streamed store"));
        }

        @Test
        @DisplayName("an append completed before its staged file was deleted is not appended again")
        void appendCompletedStagedLeft() throws Exception {
            var journal = stopAt(Step.APPLY, 1);
            Files.write(store(WORK_LOG), bytes(SECOND), StandardOpenOption.APPEND);

            var outcome = recover();

            assertAll("an append completed before its staged file was deleted",
                    () -> assertEquals(new RolledForward(journal, List.of()), outcome,
                            "the completed append is not repeated"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block stays appended exactly once"),
                    () -> assertEquals(Set.of(STATUS, REFERENCES, WORK_LOG, "mcp/digests.json"),
                            snapshot(scope.pathBase()).keySet(), "the staged block was deleted"));
        }
    }

    /**
     * A delete target is applied when its store is absent and is always appliable, so a recovery of a stopped delete
     * removes a store still present, drops its sidecar entry, and does it once however often it runs.
     */
    @Nested
    @DisplayName("a journal with a delete target")
    class DeleteTarget {

        /**
         * Deletes the status and replaces the references in a commit stopped at the boundary.
         *
         * @return the journal the stopped commit left
         */
        private TransactionJournal stopDeleteAt(Step stopStep, int stopTarget) {
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == stopStep && target == stopTarget) {
                    throw new StopWrite(step, target);
                }
            };
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
                write.delete(STATUS);
                write.replace(REFERENCES, bytes(NEW_REFERENCES));
                assertThrows(StopWrite.class, write::commit, "the commit stops at the boundary");
            }
            return TransactionJournal.read(scope.journalFile(),
                    StoreSnapshot.read(scope.journalFile()).content().orElseThrow());
        }

        private byte[] deletedSidecar() {
            return DigestSidecar.of(Map.of(
                    REFERENCES, StoreDigest.of(bytes(NEW_REFERENCES)),
                    WORK_LOG, StoreDigest.of(bytes(FIRST)))).write();
        }

        private AuditEvent rollForward(TransactionJournal journal) {
            return AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(), plan,
                    journal.transactionId().value(), List.of(STATUS, REFERENCES));
        }

        @Test
        @DisplayName("stopped after its journal, with the store still present, removes the store and drops its digest")
        void storePresent() {
            var journal = stopDeleteAt(Step.JOURNAL, 0);

            var outcome = recover();

            assertAll("a delete stopped after its journal, with the store present",
                    () -> assertEquals(new RolledForward(journal, List.of(STATUS, REFERENCES)), outcome,
                            "both targets are applied by the recovery"),
                    () -> assertFalse(Files.exists(store(STATUS)), "the store is removed"),
                    () -> assertArrayEquals(bytes(NEW_REFERENCES), Files.readAllBytes(store(REFERENCES)),
                            "the references hold their new content"),
                    () -> assertArrayEquals(deletedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar no longer digests the status"),
                    () -> assertEquals(Set.of(REFERENCES, WORK_LOG, "mcp/digests.json"),
                            snapshot(scope.pathBase()).keySet(), "no journal and no staged file remains"),
                    () -> assertEquals(List.of(rollForward(journal)), auditEvents.getValues(),
                            "the roll-forward is recorded once"));
        }

        @Test
        @DisplayName("stopped after the delete was applied, with the store absent, only drops its digest")
        void storeAbsent() {
            var journal = stopDeleteAt(Step.APPLY, 0);

            var outcome = recover();

            assertAll("a delete stopped after it was applied",
                    () -> assertEquals(new RolledForward(journal, List.of(REFERENCES)), outcome,
                            "the delete was applied already"),
                    () -> assertFalse(Files.exists(store(STATUS)), "the deleted store stays absent"),
                    () -> assertArrayEquals(deletedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar no longer digests the status"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"),
                    () -> assertEquals(List.of(rollForward(journal)), auditEvents.getValues(),
                            "the roll-forward is recorded once"));
        }

        @Test
        @DisplayName("is never an invariant violation, whatever became of its store")
        void neverViolating() throws Exception {
            var journal = stopDeleteAt(Step.JOURNAL, 0);
            Files.write(store(STATUS), bytes(NEW_STATUS));

            var outcome = recover();

            assertAll("a delete whose store reappeared after the journal",
                    () -> assertEquals(new RolledForward(journal, List.of(STATUS, REFERENCES)), outcome,
                            "both targets are applied by the recovery"),
                    () -> assertFalse(Files.exists(store(STATUS)), "a store present after the journal is removed"));
        }

        @Test
        @DisplayName("recovered again changes no byte and records no second roll-forward")
        void idempotent() throws Exception {
            stopDeleteAt(Step.JOURNAL, 0);
            recover();
            var recovered = snapshot(scope.pathBase());

            var second = recover();
            var third = recover();

            assertAll("a delete recovered three times",
                    () -> assertEquals(new NothingToRecover(List.of()), second,
                            "the second recovery finds nothing"),
                    () -> assertEquals(new NothingToRecover(List.of()), third,
                            "the third recovery finds nothing"),
                    () -> assertEquals(recovered, snapshot(scope.pathBase()),
                            "no byte changes on a repeated recovery"),
                    () -> assertEquals(1, rolledForward().size(), "the roll-forward is recorded once"));
        }

        @Test
        @DisplayName("whose journal is torn is refused and left in place, and its store is not removed")
        void tornJournal() throws Exception {
            var journal = stopDeleteAt(Step.JOURNAL, 0);
            var document = journal.write();
            var torn = Arrays.copyOf(document, document.length / 2);
            Files.write(scope.journalFile(), torn);

            var refusal = assertThrows(StoreRefusal.class, () -> recover(), "the torn journal is refused");

            assertAll("a delete whose journal is torn",
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the torn journal is unreadable"),
                    () -> assertEquals(scope.journalFile(), refusal.file(), "the refusal names the journal file"),
                    () -> assertArrayEquals(torn, Files.readAllBytes(scope.journalFile()), "the journal is kept"),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS)),
                            "nothing is deleted on a journal that cannot be read"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a refusal"));
        }

        @Test
        @DisplayName("stopped before its journal leaves the store and its digest as they were")
        void beforeJournal() throws Exception {
            var before = snapshot(scope.pathBase());
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == Step.STAGE) {
                    throw new StopWrite(step, target);
                }
            };
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
                write.delete(STATUS);
                assertThrows(StopWrite.class, write::commit, "the commit stops at the boundary");
            }

            var outcome = recover();

            assertAll("a delete stopped before its journal",
                    () -> assertEquals(new NothingToRecover(List.of()), outcome,
                            "the recovery finds nothing to do"),
                    () -> assertEquals(before, snapshot(scope.pathBase()),
                            "every file is as before the commit"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded here"));
        }
    }

    @Nested
    @DisplayName("a journal that cannot be rolled forward")
    class Violations {

        @Test
        @DisplayName("a target with neither its new digest nor its staged file is an invariant violation naming it")
        void stagedFileLost() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.delete(scope.resolve(journal.targets().getFirst().stagedPath()));
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("a target whose staged file was lost",
                    () -> assertEquals(new InvariantViolation(journal, List.of(STATUS)), outcome,
                            "the violation names the lost target"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "journal and stores untouched"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "the journal is left in place"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a violation"));
        }

        @Test
        @DisplayName("an append whose store is shorter than before the append is an invariant violation")
        void appendCutShort() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.write(store(WORK_LOG), new byte[0]);
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("an append cut short of its recorded prefix",
                    () -> assertEquals(new InvariantViolation(journal, List.of(WORK_LOG)), outcome,
                            "the violation names the work log"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "journal and stores untouched"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a violation"));
        }

        @Test
        @DisplayName("every violating store is named, in the order of the journal")
        void severalViolations() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.delete(scope.resolve(journal.targets().get(1).stagedPath()));
            Files.write(store(WORK_LOG), new byte[0]);

            var outcome = recover();

            assertEquals(new InvariantViolation(journal, List.of(REFERENCES, WORK_LOG)), outcome,
                    "every violating store is named in order");
        }

        @Test
        @DisplayName("an unreadable journal is refused and left in place")
        void unreadableJournal() throws Exception {
            var unreadable = bytes("{\"format_version\":1,");
            Files.write(scope.journalFile(), unreadable);

            var refusal = assertThrows(StoreRefusal.class, () -> recover(),
                    "the unreadable journal is refused");

            assertAll("an unreadable journal",
                    () -> assertEquals(scope.journalFile(), refusal.file(), "the refusal names the journal file"),
                    () -> assertArrayEquals(unreadable, Files.readAllBytes(scope.journalFile()),
                            "the unreadable journal is kept"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a refusal"));
        }

        @Test
        @DisplayName("a replace whose staged bytes changed after the journal is an invariant violation, nothing applied")
        void replaceStagedCorrupted() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.write(scope.resolve(journal.targets().getFirst().stagedPath()), bytes(OLD_STATUS));
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("a replace whose staged bytes changed",
                    () -> assertEquals(new InvariantViolation(journal, List.of(STATUS)), outcome,
                            "the violation names the status"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "no store modified, the journal in place"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "the journal is left in place"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a violation"));
        }

        @Test
        @DisplayName("an append whose staged block changed after the journal is an invariant violation, nothing applied")
        void appendStagedCorrupted() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.write(scope.resolve(journal.targets().get(2).stagedPath()), bytes(FIRST));
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("an append whose staged block changed",
                    () -> assertEquals(new InvariantViolation(journal, List.of(WORK_LOG)), outcome,
                            "the violation names the work log"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "no store modified, the journal in place"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "the journal is left in place"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a violation"));
        }

        @Test
        @DisplayName("an append whose store prefix changed after the journal is an invariant violation, nothing applied")
        void appendPrefixChanged() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.write(store(WORK_LOG), bytes(FIRST.replace('1', '9')));
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll("an append whose store prefix changed",
                    () -> assertEquals(new InvariantViolation(journal, List.of(WORK_LOG)), outcome,
                            "the violation names the work log"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "no store modified, the journal in place"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "the journal is left in place"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on a violation"));
        }

        @Test
        @DisplayName("an altered journal naming another file of the scope as staged path is refused and moves nothing")
        void foreignStagedPath() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            var notes = store("notes.json");
            Files.write(notes, bytes(NEW_REFERENCES));
            var altered = new String(Files.readAllBytes(scope.journalFile()), UTF_8)
                    .replace(journal.targets().getFirst().stagedPath(), "notes.json").getBytes(UTF_8);
            Files.write(scope.journalFile(), altered);
            var before = snapshot(scope.pathBase());

            var refusal = assertThrows(StoreRefusal.class, () -> recover(),
                    "the altered journal is refused");

            assertAll("an altered journal naming a foreign staged path",
                    () -> assertEquals(scope.journalFile(), refusal.file(), "the refusal names the journal file"),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "no file moved, the journal in place"),
                    () -> assertArrayEquals(bytes(NEW_REFERENCES), Files.readAllBytes(notes),
                            "the foreign file is not moved"),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS)),
                            "the status is not modified"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on the refusal"));
        }
    }

    @Nested
    @DisplayName("staged files without a journal")
    class Orphans {

        @Test
        @DisplayName("are deleted, and files that only look similar are kept")
        void orphansDeleted() throws Exception {
            var id = TransactionId.generate(CLOCK).value();
            var staged = Set.of(store(STATUS + ".tmp." + id), store(WORK_LOG + ".append." + id),
                    scope.resolve("mcp/transaction-journal.json.tmp." + id));
            var kept = Set.of(store(STATUS + ".tmp.not-a-transaction"), store(STATUS + ".bak." + id));
            for (var file : staged) {
                Files.write(file, bytes(NEW_STATUS));
            }
            for (var file : kept) {
                Files.write(file, bytes(NEW_STATUS));
            }

            var outcome = recover();

            assertInstanceOf(NothingToRecover.class, outcome, "only staged files are deleted");
            assertAll("staged files without a journal",
                    () -> assertEquals(staged, new HashSet<>(((NothingToRecover) outcome).deletedStagedFiles()),
                            "every staged file is listed as deleted"),
                    () -> assertTrue(staged.stream().noneMatch(Files::exists), "every staged file is deleted"),
                    () -> assertTrue(kept.stream().allMatch(Files::exists), "every similar file is kept"),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS)),
                            "the stored bytes are untouched"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded here"));
        }

        @Test
        @DisplayName("a staged-looking file in the directory of another scope is not deleted")
        void otherScopeUntouched() throws Exception {
            var id = TransactionId.generate(CLOCK).value();
            var workspace = JournalScope.workspace(base, project);
            var ownStaged = workspace.resolve("workspace/state.json.tmp." + id);
            var pendingStaged = workspace.resolve("pending/queue.jsonl.append." + id);
            var planStaged = store(STATUS + ".tmp." + id);
            var anotherPlan = "other-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
            var otherPlanStaged = JournalScope.plan(base, project, anotherPlan).resolve(STATUS + ".tmp." + id);
            for (var file : List.of(ownStaged, pendingStaged, planStaged, otherPlanStaged)) {
                Files.createDirectories(file.getParent());
                Files.write(file, bytes(NEW_STATUS));
            }

            Outcome workspaceOutcome;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                workspaceOutcome = JournalRecovery.recover(transaction, workspace, audit);
            }
            var planOutcome = recover();

            assertAll("a staged file in the directory of another scope",
                    () -> assertEquals(Set.of(ownStaged, pendingStaged),
                            new HashSet<>(((NothingToRecover) workspaceOutcome).deletedStagedFiles()),
                            "the workspace deletes only its own staged files"),
                    () -> assertFalse(Files.exists(ownStaged), "the workspace staged file is deleted"),
                    () -> assertFalse(Files.exists(pendingStaged), "the pending staged file is deleted"),
                    () -> assertEquals(new NothingToRecover(List.of(planStaged)), planOutcome,
                            "the plan reports its staged file"),
                    () -> assertFalse(Files.exists(planStaged), "the plan staged file is deleted"),
                    () -> assertTrue(Files.exists(otherPlanStaged), "a sibling plan's staged file is not touched"));
        }
    }

    /**
     * The audit records of a roll-forward are delivered at least once: the journal is their durable intent and is
     * closed only after the sink accepted them.
     */
    @Nested
    @DisplayName("audit delivery")
    class AuditDelivery {

        private Outcome recoverWith(AuditSink sink) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                return JournalRecovery.recover(transaction, scope, sink);
            }
        }

        @Test
        @DisplayName("a sink that refuses the record keeps the journal; the next recovery delivers it exactly once")
        void sinkRefuses() {
            var journal = stopAt(Step.JOURNAL, 0);
            Capture<AuditEvent> refused = newCapture(CaptureType.ALL);
            var refusing = AuditSinkDoubles.refusing(refused);

            var first = recoverWith(refusing);

            assertAll("a sink that refuses the roll-forward record",
                    () -> assertEquals(new RolledForward(journal, TARGETS), first, "the data outcome as before"),
                    () -> assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS)),
                            "the status holds its new content"),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar holds the new digests"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "the journal stays as the intent"),
                    () -> assertEquals(List.of(), refused.getValues(), "the refusing sink recorded nothing"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()));

            var second = recover();

            assertAll("the next recovery after the refusal",
                    () -> assertEquals(new RolledForward(journal, List.of()), second,
                            "the second recovery applies no target"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), auditEvents.getValues(), "delivered exactly once"),
                    () -> assertEquals(Set.of(STATUS, REFERENCES, WORK_LOG, "mcp/digests.json"),
                            snapshot(scope.pathBase()).keySet(), "journal and staged files closed"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block stays appended exactly once"));
        }

        @Test
        @DisplayName("a replay after the record was delivered finds nothing and delivers no second record")
        void noSecondRecord() {
            var journal = stopAt(Step.JOURNAL, 0);
            recoverWith(AuditSinkDoubles.refusing());
            recover();

            var third = recover();

            assertAll("a replay after the record was delivered",
                    () -> assertEquals(new NothingToRecover(List.of()), third,
                            "the replay finds nothing to recover"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), auditEvents.getValues(),
                            "the record was delivered exactly once"));
        }

        @Test
        @DisplayName("a process stopped between the roll-forward and the append delivers the record at the next recovery")
        void stoppedBeforeAppend() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            var stopping = AuditSinkDoubles.stopping();

            assertThrows(AuditSinkDoubles.ProcessStopped.class, () -> recoverWith(stopping),
                    "the recovery stops before the append");
            var afterStop = snapshot(scope.pathBase());
            var outcome = recover();

            assertAll("a stop between roll-forward and append",
                    () -> assertTrue(afterStop.containsKey("mcp/transaction-journal.json"), "the intent survived"),
                    () -> assertEquals(new RolledForward(journal, List.of()), outcome,
                            "the next recovery applies no target"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), auditEvents.getValues(),
                            "the record is delivered exactly once"),
                    () -> assertFalse(Files.exists(scope.journalFile()),
                            "the journal is closed after delivery"));
        }

        @Test
        @DisplayName("a record a transaction owes is delivered with it, or by the recovery when the sink refused it, "
                + "keeping its own id")
        void owedByTransaction() throws Exception {
            Capture<AuditEvent> refused = newCapture(CaptureType.ALL);
            var refusing = AuditSinkDoubles.refusing(refused);
            TransactionJournal journal;
            String recordId;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, refusing, CLOCK);
                write.replace(STATUS, bytes(NEW_STATUS));
                recordId = write.recordId().value();
                write.owe(AuditEvent.crossScopeReconciled(
                        Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT), STATUS, recordId));
                write.commit();
                journal = TransactionJournal.read(scope.journalFile(), Files.readAllBytes(scope.journalFile()));
            }
            var owed = journal.owedEvents().getFirst();
            var blocked = refusedCommit();

            var outcome = recover();

            assertAll("a record owed by a transaction",
                    () -> assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS)),
                            "the commit completed its data"),
                    () -> assertEquals(List.of(), refused.getValues(), "the refusing sink recorded nothing"),
                    () -> assertInstanceOf(IllegalStateException.class, blocked,
                            "no transaction overwrites a journal that owes a record"),
                    () -> assertEquals(new RolledForward(journal, List.of()), outcome,
                            "the recovery applies no target"),
                    () -> assertEquals(List.of(AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(),
                                    plan, journal.transactionId().value(), List.of(STATUS)), owed), auditEvents.getValues(),
                            "the roll-forward record precedes the owed record"),
                    () -> assertEquals(recordId, owed.details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            "the re-delivered record keeps the id it was owed with"),
                    () -> assertNotEquals(journal.transactionId().value(),
                            owed.details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            "the roll-forward record and the owed record carry different ids"),
                    () -> assertFalse(Files.exists(scope.journalFile()),
                            "the journal is closed after delivery"));
        }

        /** @return what a commit of the scope throws while the journal of an earlier transaction is open */
        private RuntimeException refusedCommit() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                write.replace(REFERENCES, bytes(NEW_REFERENCES));
                return assertThrows(IllegalStateException.class, write::commit,
                        "a commit over an owing journal is refused");
            }
        }
    }

    /** Matched control of every recovery above: a consistent scope is left byte for byte as it is. */
    @Test
    @DisplayName("recovery of a consistent scope changes no byte and records nothing")
    void consistentScope() throws Exception {
        var before = snapshot(base);

        var outcome = recover();

        assertAll("a consistent scope recovered",
                () -> assertEquals(new NothingToRecover(List.of()), outcome,
                        "the recovery finds nothing to recover"),
                () -> assertEquals(before, snapshot(base), "the scope is left byte for byte"),
                () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded here"));
    }

    @Test
    @DisplayName("recovery without the lock of the scope is refused and touches nothing")
    void withoutLock() throws Exception {
        stopAt(Step.JOURNAL, 0);
        var before = snapshot(scope.pathBase());

        try (var transaction = locks.openTransaction()) {
            var fault = assertThrows(InternalFaultException.class,
                    () -> JournalRecovery.recover(transaction, scope, audit),
                    "the recovery without the lock is refused");

            assertAll("a recovery without the lock of the scope",
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                            "the fault names the missing lock"),
                    () -> assertEquals(before, snapshot(scope.pathBase()),
                            "no byte changes without the lock"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded here"));
        }
    }
}
