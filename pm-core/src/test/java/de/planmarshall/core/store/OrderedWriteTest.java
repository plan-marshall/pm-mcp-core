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
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.CrossScopeReconciler.Reconciliation;
import de.planmarshall.core.store.ScopeIntegrity.Verified;

/**
 * The write across scopes in its fixed order (PM-IMPL-7): the authoritative store first, each step in a lock
 * transaction of its own, and a crash between any two writes completed at the next start by the recovery of each
 * scope and the reconcilers, with each derived write applied exactly once and one {@code cross_scope_reconciled}
 * record per reconciliation.
 * <p>
 * The test flow writes a status of a plan (authoritative), an entry of the workspace queue and an entry of an epic,
 * each by a {@link ScopeTransaction} of its own scope; the two derived entries are the authoritative status with the
 * name of their reconciliation in front, and the test reconciler derives them from the status. The store paths are the
 * layout contract of the three scopes; the crash points are order-pinning data and stay in their declared order.
 */
@EnableGeneratorController
@DisplayName("Ordered write")
class OrderedWriteTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String STATUS = "status.json";
    private static final String QUEUE = "workspace/queue.json";
    private static final String EPIC_PLANS = "plans.json";
    private static final int NO_STOP = -1;

    @TempDir
    Path base;

    private String queueRule;
    private String epicRule;
    private String authoritativeDocument;
    private Clock clock;
    private JournalScope plan;
    private JournalScope workspace;
    private JournalScope epic;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    /** What a hook throws to stop the flow, as a killed process stops it. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite(String where) {
            super("stopped " + where);
        }
    }

    /** What the stopping audit sink throws at its first append, as a killed process stops it. */
    private static final class ProcessStopped extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        ProcessStopped() {
            super("process stopped at the audit append");
        }
    }

    /** @return an EasyMock audit sink that refuses its first append and captures every later one */
    private static AuditSink refusingSink(Capture<AuditEvent> captured) {
        AuditSink sink = createMock(AuditSink.class);
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().andThrow(new UncheckedIOException(new IOException("the audit log cannot be written")));
        sink.append(capture(captured));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
    }

    /** @return an EasyMock audit sink that stops the caller at its first append */
    private static AuditSink stoppingSink() {
        AuditSink sink = createMock(AuditSink.class);
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().andThrow(new ProcessStopped());
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
    }

    @BeforeEach
    void createScopes() throws IOException {
        queueRule = "queue-" + lowerName();
        epicRule = "epic-" + lowerName();
        // The authoritative status keeps the JSON shape of a status document; its job and state are generated.
        authoritativeDocument = "{\"format_version\":1,\"job\":\"j-%d\",\"state\":\"%s\"}".formatted(
                Generators.integers(1, 9_999).next(), lowerName());
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        plan = JournalScope.plan(base, PROJECT, "plan-" + lowerName());
        workspace = JournalScope.workspace(base, PROJECT);
        epic = JournalScope.epic(base, PROJECT, "epic-" + lowerName());
        for (var scope : List.of(plan, workspace, epic)) {
            Files.createDirectories(scope.journalFile().getParent());
        }
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static byte[] derived(String rule, byte[] authoritative) {
        var prefix = bytes(rule + ":");
        var content = new byte[prefix.length + authoritative.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(authoritative, 0, content, prefix.length, authoritative.length);
        return content;
    }

    /** @return every regular file below the base directory, by its relative path, with its bytes in hex */
    private Map<String, String> snapshot() throws IOException {
        var files = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(base)) {
            for (var file : walk.filter(Files::isRegularFile).toList()) {
                files.put(base.relativize(file).toString(), HexFormat.of().formatHex(Files.readAllBytes(file)));
            }
        }
        return files;
    }

    /** Replaces one store of a scope in a scope transaction, stopped after its journal if asked to. */
    private void replace(LockTransaction transaction, JournalScope scope, String path, byte[] content,
            boolean stopAfterJournal) {
        ScopeTransaction.StepHook hook = (step, target) -> {
            if (stopAfterJournal && step == ScopeTransaction.Step.JOURNAL) {
                throw new StopWrite("after the journal of " + path);
            }
        };
        var write = ScopeTransaction.open(transaction, scope, audit, clock, hook);
        write.replace(path, content);
        write.commit();
    }

    /**
     * @param stopAfterStep    the step after which the order is stopped; {@link #NO_STOP} for none
     * @param stopInStep       the step whose scope transaction is stopped after its journal; {@link #NO_STOP} for none
     * @param heldOtherKeys    collects, per step, whether its transaction held a key of another step
     * @return the flow of the test
     */
    private OrderedWrite flow(int stopAfterStep, int stopInStep, List<Boolean> heldOtherKeys) {
        var authoritative = bytes(authoritativeDocument);
        var keys = List.of(plan.lockKey(), workspace.lockKey(), epic.lockKey());
        var steps = List.of(
                new OrderedWrite.WriteStep(plan.lockKey(), transaction -> {
                    heldOtherKeys.add(keys.stream().filter(key -> !key.equals(plan.lockKey()))
                            .anyMatch(transaction::holds));
                    replace(transaction, plan, STATUS, authoritative, stopInStep == 0);
                }),
                new OrderedWrite.WriteStep(workspace.lockKey(), transaction -> {
                    heldOtherKeys.add(keys.stream().filter(key -> !key.equals(workspace.lockKey()))
                            .anyMatch(transaction::holds));
                    replace(transaction, workspace, QUEUE, derived(queueRule, authoritative), stopInStep == 1);
                }),
                new OrderedWrite.WriteStep(epic.lockKey(), transaction -> {
                    heldOtherKeys.add(keys.stream().filter(key -> !key.equals(epic.lockKey()))
                            .anyMatch(transaction::holds));
                    replace(transaction, epic, EPIC_PLANS, derived(epicRule, authoritative), stopInStep == 2);
                }));
        return new OrderedWrite(steps, step -> {
            if (step == stopAfterStep) {
                throw new StopWrite("after step " + step);
            }
        });
    }

    /**
     * The reconciler of the test flow: it reads the status of the plan under the plan lock and writes a derived
     * entry only where it is not in place yet, each under the lock of its own scope.
     */
    private CrossScopeReconciler reconciler() {
        return (lockManager, sink) -> {
            byte[] authoritative;
            try (var transaction = lockManager.openTransaction()) {
                transaction.acquire(plan.lockKey());
                authoritative = new AtomicStoreFile(plan.resolve(STATUS), plan.lockKey()).read(transaction)
                        .orElse(null);
            }
            if (authoritative == null) {
                return List.of();
            }
            var applied = new ArrayList<Reconciliation>();
            deriveIfMissing(lockManager, sink, workspace, queueReconciliation(),
                    derived(queueRule, authoritative), applied);
            deriveIfMissing(lockManager, sink, epic, epicReconciliation(),
                    derived(epicRule, authoritative), applied);
            return applied;
        };
    }

    private Reconciliation queueReconciliation() {
        return new Reconciliation(queueRule, QUEUE);
    }

    private Reconciliation epicReconciliation() {
        return new Reconciliation(epicRule, EPIC_PLANS);
    }

    /** Applies a derived write where it is missing, in a scope transaction that owes its reconciliation record. */
    private void deriveIfMissing(LockManager lockManager, AuditSink sink, JournalScope scope,
            Reconciliation reconciliation, byte[] content, List<Reconciliation> applied) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var current = new AtomicStoreFile(scope.resolve(reconciliation.store()), scope.lockKey())
                    .read(transaction);
            if (current.isPresent() && Arrays.equals(current.get(), content)) {
                return;
            }
            var write = ScopeTransaction.open(transaction, scope, sink, clock);
            write.replace(reconciliation.store(), content);
            OrderedWrite.oweReconciled(write, reconciliation);
            write.commit();
            applied.add(reconciliation);
        }
    }

    /** @return the cross_scope_reconciled records among the records, by rule and store, without their ids */
    private static List<AuditEvent> reconciledOf(List<AuditEvent> records) {
        return RecordingAuditSink.withoutTransactionIds(records.stream()
                .filter(event -> AuditEvent.CROSS_SCOPE_RECONCILED.equals(event.event())).toList());
    }

    private static List<AuditEvent> reconciledOf(Reconciliation... reconciliations) {
        return RecordingAuditSink.withoutTransactionIds(Arrays.stream(reconciliations)
                .map(reconciliation -> AuditEvent.crossScopeReconciled(reconciliation.name(), reconciliation.store(),
                        "unknown"))
                .toList());
    }

    /** Runs what a start runs: the recovery of every scope, then the reconcilers. */
    private List<Reconciliation> restart() {
        for (var scope : List.of(plan, workspace, epic)) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                JournalRecovery.recover(transaction, scope, audit);
            }
        }
        return OrderedWrite.reconcile(locks, List.of(reconciler()), audit);
    }

    private List<AuditEvent> reconciledRecords() {
        return reconciledOf(audit.events());
    }

    private void assertConsistent() {
        var authoritative = bytes(authoritativeDocument);
        assertAll(
                () -> assertArrayEquals(authoritative, Files.readAllBytes(plan.resolve(STATUS)),
                        "the authoritative status should be in place"),
                () -> assertArrayEquals(derived(queueRule, authoritative),
                        Files.readAllBytes(workspace.resolve(QUEUE)), "the queue entry should be derived"),
                () -> assertArrayEquals(derived(epicRule, authoritative),
                        Files.readAllBytes(epic.resolve(EPIC_PLANS)), "the epic entry should be derived"),
                () -> assertTrue(Stream.of(plan, workspace, epic)
                                .noneMatch(scope -> Files.exists(scope.journalFile())),
                        "no journal should remain in any scope"),
                () -> {
                    for (var scope : List.of(plan, workspace, epic)) {
                        try (var transaction = locks.openTransaction()) {
                            transaction.acquire(scope.lockKey());
                            assertTrue(ScopeIntegrity.verifyAll(transaction, scope, audit).stream()
                                            .allMatch(Verified.class::isInstance),
                                    "every digest of the scope should verify");
                        }
                    }
                });
    }

    /**
     * Every point a crash can stop the flow at between or inside its writes, in their order along the flow.
     *
     * @return the name of the point, the step the order stops after, the step whose transaction stops after its
     *         journal, and whether the restart applies the queue and the epic reconciliation
     */
    static Stream<Arguments> crashPoints() {
        return Stream.of(
                Arguments.of("after the authoritative write", 0, NO_STOP, true, true),
                Arguments.of("after the first derived write", 1, NO_STOP, false, true),
                Arguments.of("after the last write", 2, NO_STOP, false, false),
                Arguments.of("inside the authoritative write, after its journal", NO_STOP, 0, true, true),
                Arguments.of("inside the first derived write, after its journal", NO_STOP, 1, false, true));
    }

    @Nested
    @DisplayName("a crash")
    class Crash {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.OrderedWriteTest#crashPoints")
        @DisplayName("is completed by recovery and reconcile, each derived write applied exactly once")
        void completedByRestart(String name, int stopAfterStep, int stopInStep, boolean queueApplied,
                boolean epicApplied) {
            var expected = new ArrayList<Reconciliation>();
            if (queueApplied) {
                expected.add(queueReconciliation());
            }
            if (epicApplied) {
                expected.add(epicReconciliation());
            }
            var write = flow(stopAfterStep, stopInStep, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop at the crash point");

            var applied = restart();

            assertAll(
                    () -> assertEquals(expected, applied, "the restart should apply the missing writes"),
                    () -> assertEquals(reconciledOf(expected.toArray(Reconciliation[]::new)), reconciledRecords(),
                            "one cross_scope_reconciled per reconciliation"),
                    OrderedWriteTest.this::assertConsistent);
        }

        @Test
        @DisplayName("a second restart after the completion applies nothing and records nothing more")
        void restartedOnce() throws Exception {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop after the first step");
            restart();
            var completed = snapshot();
            var records = audit.events().size();

            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(), applied, "a second restart should apply nothing"),
                    () -> assertEquals(completed, snapshot(), "a second restart should change no file"),
                    () -> assertEquals(records, audit.events().size(),
                            "a second restart should record nothing"));
        }

        @Test
        @DisplayName("before the first write leaves nothing, and the restart derives nothing")
        void beforeFirstWrite() throws Exception {
            var before = snapshot();
            var write = new OrderedWrite(List.of(
                    new OrderedWrite.WriteStep(plan.lockKey(), _ -> {
                        throw new StopWrite("before the authoritative write");
                    }),
                    new OrderedWrite.WriteStep(workspace.lockKey(),
                            transaction -> replace(transaction, workspace, QUEUE, bytes(authoritativeDocument),
                                    false))));

            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop before its first write");
            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(), applied, "the restart should derive nothing"),
                    () -> assertEquals(before, snapshot(), "nothing should have been written"),
                    () -> assertFalse(Files.exists(workspace.resolve(QUEUE)), "the later step should not have run"),
                    () -> assertEquals(List.of(), audit.events(), "nothing should have been recorded"));
        }
    }

    @Test
    @DisplayName("runs its steps in the declared order, each in a transaction of its own that holds one key")
    void orderAndTransactions() {
        var heldOtherKeys = new ArrayList<Boolean>();

        flow(NO_STOP, NO_STOP, heldOtherKeys).run(locks);

        assertAll(
                () -> assertEquals(List.of(plan.lockKey(), workspace.lockKey(), epic.lockKey()),
                        locks.acquisitions(), "the keys should be acquired in step order"),
                () -> assertEquals(3, locks.transactions(), "each step should open a transaction"),
                () -> assertEquals(List.of(false, false, false), heldOtherKeys,
                        "no step should hold a key of another"),
                () -> assertEquals(List.of(), locks.held(), "every transaction should be closed"),
                this::assertConsistent);
    }

    /** Matched control of the crash points: a flow that ran to its end leaves the reconcilers nothing to do. */
    @Test
    @DisplayName("reconcile on a consistent set changes nothing and records nothing")
    void consistentSet() throws Exception {
        flow(NO_STOP, NO_STOP, new ArrayList<>()).run(locks);
        var before = snapshot();

        var applied = OrderedWrite.reconcile(locks, List.of(reconciler()), audit);

        assertAll(
                () -> assertEquals(List.of(), applied, "a consistent set should need no write"),
                () -> assertEquals(before, snapshot(), "a consistent set should stay unchanged"),
                () -> assertEquals(List.of(), audit.events(), "a consistent set should record nothing"));
    }

    @Test
    @DisplayName("reconcile returns the reconciliations of every reconciler in their order, and records none itself")
    void severalReconcilers() {
        var first = new Reconciliation(lowerName(), lowerName() + ".json");
        var second = new Reconciliation(lowerName(), lowerName() + ".json");
        var third = new Reconciliation(lowerName(), lowerName() + ".json");
        var sinks = new ArrayList<AuditSink>();
        List<CrossScopeReconciler> reconcilers = List.of((_, sink) -> {
            sinks.add(sink);
            return List.of(first, second);
        }, (_, _) -> List.of(), (_, _) -> List.of(third));

        var applied = OrderedWrite.reconcile(locks, reconcilers, audit);

        assertAll(
                () -> assertEquals(List.of(first, second, third), applied,
                        "the reconciliations should keep their order"),
                () -> assertEquals(List.of(audit), sinks, "the reconciler should get the sink"),
                () -> assertEquals(List.of(), audit.events(), "the records should come with the transactions"));
    }

    /** The record of a reconciliation is owed in the journal of its derived write and delivered at least once. */
    @Nested
    @DisplayName("audit delivery of a reconciliation")
    class ReconciliationDelivery {

        @Test
        @DisplayName("the record carries an id of its own, not the transaction id of the derived write")
        void ownRecordId() {
            String writeId;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock);
                write.replace(QUEUE, bytes(authoritativeDocument));
                OrderedWrite.oweReconciled(write, queueReconciliation());
                writeId = write.transactionId().value();
                write.commit();
            }

            var recordId = String.valueOf(audit.events().getFirst().details().get(AuditEvent.DETAIL_TRANSACTION_ID));
            assertAll(
                    () -> assertEquals(reconciledOf(queueReconciliation()), reconciledRecords(),
                            "the reconciliation should be recorded once"),
                    () -> assertTrue(TransactionId.ofSuffix("." + recordId).isPresent(),
                            "the record id should be a transaction id"),
                    () -> assertNotEquals(writeId, recordId, "the record should not share its write's id"));
        }

        @Test
        @DisplayName("a sink that refuses the record keeps it owed; the next restart delivers it exactly once")
        void sinkRefuses() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop after the first step");
            Capture<AuditEvent> refused = newCapture(CaptureType.ALL);
            var refusing = refusingSink(refused);

            var applied = OrderedWrite.reconcile(locks, List.of(reconciler()), refusing);
            var journalKept = Files.exists(workspace.journalFile());
            var restarted = restart();

            assertAll(
                    () -> assertEquals(List.of(queueReconciliation(), epicReconciliation()), applied,
                            "the data outcome should be as before"),
                    () -> assertTrue(journalKept, "the derived write's journal should keep it owed"),
                    () -> assertEquals(reconciledOf(epicReconciliation()), reconciledOf(refused.getValues()),
                            "the refusing sink should get the epic record"),
                    () -> assertEquals(List.of(), restarted, "the derived writes should be in place"),
                    () -> assertEquals(reconciledOf(queueReconciliation()), reconciledRecords(),
                            "the refused record should be delivered once"),
                    OrderedWriteTest.this::assertConsistent);
        }

        @Test
        @DisplayName("a replay after the record was delivered delivers no second record")
        void noSecondRecord() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop after the first step");
            OrderedWrite.reconcile(locks, List.of(reconciler()), AuditSinkDoubles.refusing());
            restart();
            var records = audit.events().size();

            restart();

            assertEquals(records, audit.events().size(), "a replay should deliver no second record");
        }

        @Test
        @DisplayName("a process stopped between the derived write and the append delivers the record at the restart")
        void stoppedBeforeAppend() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks), "the flow should stop after the first step");
            var reconcilers = List.of(reconciler());
            var stopping = stoppingSink();

            assertThrows(ProcessStopped.class,
                    () -> OrderedWrite.reconcile(locks, reconcilers, stopping),
                    "the sink should stop the reconcile");
            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(epicReconciliation()), applied,
                            "the restart should apply the epic write"),
                    () -> assertEquals(reconciledOf(queueReconciliation(), epicReconciliation()),
                            reconciledRecords(), "the stopped record and the later one, each once"),
                    OrderedWriteTest.this::assertConsistent);
        }
    }

    @Test
    @DisplayName("an ordered write has at least its authoritative step")
    void noStep() {
        List<OrderedWrite.WriteStep> none = List.of();

        assertThrows(IllegalArgumentException.class, () -> new OrderedWrite(none),
                "an ordered write without steps should fail");
    }
}
