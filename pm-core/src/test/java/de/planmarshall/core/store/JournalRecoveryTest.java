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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.JournalRecovery.InvariantViolation;
import de.planmarshall.core.store.JournalRecovery.NothingToRecover;
import de.planmarshall.core.store.JournalRecovery.Outcome;
import de.planmarshall.core.store.JournalRecovery.RolledForward;
import de.planmarshall.core.store.ScopeTransaction.Step;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The roll-forward recovery of a scope (PM-IMPL-7): a commit stopped at any step boundary is completed by the next
 * recovery when it was stopped after its journal was written, and leaves the stores as they were when it was stopped
 * before; either way no journal and no staged file remains, every store has its content exactly once, and the sidecar
 * holds the digests of the stores.
 * <p>
 * A stop is what a killed process leaves: the step hook of the commit throws at the boundary, and nothing the commit
 * wrote is removed. The recovery then runs in a new transaction of the lock manager, as after a restart.
 */
@DisplayName("Journal recovery")
class JournalRecoveryTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "store-substrate";
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

    private JournalScope scope;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

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
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
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
            assertThrows(StopWrite.class, write::commit);
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
        return audit.events().stream().filter(event -> AuditEvent.TRANSACTION_ROLLED_FORWARD.equals(event.event()))
                .toList();
    }

    private static AuditEvent rollForwardOf(TransactionJournal journal) {
        return AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(), PLAN,
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
                assertAll(
                        () -> assertNull(journal, "no journal was written"),
                        () -> assertInstanceOf(NothingToRecover.class, outcome),
                        () -> assertEquals(target + 1, ((NothingToRecover) outcome).deletedStagedFiles().size(),
                                "every staged file was deleted"),
                        () -> assertEquals(before, after, "every store, and the sidecar, as before the commit"),
                        () -> assertEquals(List.of(), rolledForward()));
                return;
            }
            assertAll(
                    () -> assertEquals(new RolledForward(journal, applied), outcome),
                    () -> assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS))),
                    () -> assertArrayEquals(bytes(NEW_REFERENCES), Files.readAllBytes(store(REFERENCES))),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block appended exactly once"),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile())),
                    () -> assertEquals(Set.of(STATUS, REFERENCES, WORK_LOG, "mcp/digests.json"), after.keySet(),
                            "no journal and no staged file remains"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), rolledForward(),
                            "exactly one transaction_rolled_forward"),
                    () -> assertEquals(List.of(rollForwardOf(journal)), audit.events(), "nothing else recorded"));
        }

        @Test
        @DisplayName("a second recovery after a roll-forward finds nothing and records nothing")
        void recoveredOnce() throws Exception {
            stopAt(Step.JOURNAL, 0);
            recover();
            var recovered = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll(
                    () -> assertEquals(new NothingToRecover(List.of()), outcome),
                    () -> assertEquals(recovered, snapshot(scope.pathBase())),
                    () -> assertEquals(1, rolledForward().size()));
        }

        /** PM-WATCH-STORE-49, the crash half: an append torn after the journal is never applied twice. */
        @Test
        @DisplayName("an append torn after the journal is cut back to its size before and appended once")
        void tornAppend() throws Exception {
            var journal = stopAt(Step.APPLY, 1);
            var block = bytes(SECOND);
            Files.write(store(WORK_LOG), Arrays.copyOf(block, block.length / 2), StandardOpenOption.APPEND);

            var outcome = recover();

            assertAll(
                    () -> assertEquals(new RolledForward(journal, List.of(WORK_LOG)), outcome),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG))),
                    () -> assertArrayEquals(committedSidecar(), Files.readAllBytes(scope.sidecarFile())),
                    () -> assertEquals(List.of(rollForwardOf(journal)), audit.events(),
                            "the cut is part of the roll-forward, not a tail repair"));
        }

        @Test
        @DisplayName("an append completed before its staged file was deleted is not appended again")
        void appendCompletedStagedLeft() throws Exception {
            var journal = stopAt(Step.APPLY, 1);
            Files.write(store(WORK_LOG), bytes(SECOND), StandardOpenOption.APPEND);

            var outcome = recover();

            assertAll(
                    () -> assertEquals(new RolledForward(journal, List.of()), outcome),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG))),
                    () -> assertEquals(Set.of(STATUS, REFERENCES, WORK_LOG, "mcp/digests.json"),
                            snapshot(scope.pathBase()).keySet(), "the staged block was deleted"));
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

            assertAll(
                    () -> assertEquals(new InvariantViolation(journal, List.of(STATUS)), outcome),
                    () -> assertEquals(before, snapshot(scope.pathBase()), "journal and stores untouched"),
                    () -> assertTrue(Files.exists(scope.journalFile())),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("an append whose store is shorter than before the append is an invariant violation")
        void appendCutShort() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.write(store(WORK_LOG), new byte[0]);
            var before = snapshot(scope.pathBase());

            var outcome = recover();

            assertAll(
                    () -> assertEquals(new InvariantViolation(journal, List.of(WORK_LOG)), outcome),
                    () -> assertEquals(before, snapshot(scope.pathBase())),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("every violating store is named, in the order of the journal")
        void severalViolations() throws Exception {
            var journal = stopAt(Step.JOURNAL, 0);
            Files.delete(scope.resolve(journal.targets().get(1).stagedPath()));
            Files.write(store(WORK_LOG), new byte[0]);

            var outcome = recover();

            assertEquals(new InvariantViolation(journal, List.of(REFERENCES, WORK_LOG)), outcome);
        }

        @Test
        @DisplayName("an unreadable journal is refused and left in place")
        void unreadableJournal() throws Exception {
            var unreadable = bytes("{\"format_version\":1,");
            Files.write(scope.journalFile(), unreadable);

            var refusal = assertThrows(StoreRefusal.class, () -> recover());

            assertAll(
                    () -> assertEquals(scope.journalFile(), refusal.file()),
                    () -> assertArrayEquals(unreadable, Files.readAllBytes(scope.journalFile())),
                    () -> assertEquals(List.of(), audit.events()));
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

            assertInstanceOf(NothingToRecover.class, outcome);
            assertAll(
                    () -> assertEquals(staged, new HashSet<>(((NothingToRecover) outcome).deletedStagedFiles())),
                    () -> assertTrue(staged.stream().noneMatch(Files::exists)),
                    () -> assertTrue(kept.stream().allMatch(Files::exists)),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS))),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("a staged-looking file in the directory of another scope is not deleted")
        void otherScopeUntouched() throws Exception {
            var id = TransactionId.generate(CLOCK).value();
            var workspace = JournalScope.workspace(base, PROJECT);
            var ownStaged = workspace.resolve("workspace/state.json.tmp." + id);
            var pendingStaged = workspace.resolve("pending/queue.jsonl.append." + id);
            var planStaged = store(STATUS + ".tmp." + id);
            var otherPlanStaged = JournalScope.plan(base, PROJECT, "another-plan").resolve(STATUS + ".tmp." + id);
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

            assertAll(
                    () -> assertEquals(Set.of(ownStaged, pendingStaged),
                            new HashSet<>(((NothingToRecover) workspaceOutcome).deletedStagedFiles())),
                    () -> assertFalse(Files.exists(ownStaged)),
                    () -> assertFalse(Files.exists(pendingStaged)),
                    () -> assertEquals(new NothingToRecover(List.of(planStaged)), planOutcome),
                    () -> assertFalse(Files.exists(planStaged)),
                    () -> assertTrue(Files.exists(otherPlanStaged), "a sibling plan's staged file is not touched"));
        }
    }

    /** Matched control of every recovery above: a consistent scope is left byte for byte as it is. */
    @Test
    @DisplayName("recovery of a consistent scope changes no byte and records nothing")
    void consistentScope() throws Exception {
        var before = snapshot(base);

        var outcome = recover();

        assertAll(
                () -> assertEquals(new NothingToRecover(List.of()), outcome),
                () -> assertEquals(before, snapshot(base)),
                () -> assertEquals(List.of(), audit.events()));
    }

    @Test
    @DisplayName("recovery without the lock of the scope is refused and touches nothing")
    void withoutLock() throws Exception {
        stopAt(Step.JOURNAL, 0);
        var before = snapshot(scope.pathBase());

        try (var transaction = locks.openTransaction()) {
            var fault = assertThrows(InternalFaultException.class,
                    () -> JournalRecovery.recover(transaction, scope, audit));

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                    () -> assertEquals(before, snapshot(scope.pathBase())),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }
}
