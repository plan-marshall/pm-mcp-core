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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
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
 * name of their reconciliation in front, and the test reconciler derives them from the status.
 */
@DisplayName("Ordered write")
class OrderedWriteTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String STATUS = "status.json";
    private static final String QUEUE = "workspace/queue.json";
    private static final String EPIC_PLANS = "plans.json";
    private static final String QUEUE_RULE = "queue-entry";
    private static final String EPIC_RULE = "epic-entry";
    private static final String AUTHORITATIVE = "{\"format_version\":1,\"job\":\"j-1\",\"state\":\"done\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
    private static final int NO_STOP = -1;

    @TempDir
    Path base;

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

    @BeforeEach
    void createScopes() throws IOException {
        plan = JournalScope.plan(base, PROJECT, "auth-plan");
        workspace = JournalScope.workspace(base, PROJECT);
        epic = JournalScope.epic(base, PROJECT, "the-epic");
        for (var scope : List.of(plan, workspace, epic)) {
            Files.createDirectories(scope.journalFile().getParent());
        }
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
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
        var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
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
        var authoritative = bytes(AUTHORITATIVE);
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
                    replace(transaction, workspace, QUEUE, derived(QUEUE_RULE, authoritative), stopInStep == 1);
                }),
                new OrderedWrite.WriteStep(epic.lockKey(), transaction -> {
                    heldOtherKeys.add(keys.stream().filter(key -> !key.equals(epic.lockKey()))
                            .anyMatch(transaction::holds));
                    replace(transaction, epic, EPIC_PLANS, derived(EPIC_RULE, authoritative), stopInStep == 2);
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
            deriveIfMissing(lockManager, sink, workspace, new Reconciliation(QUEUE_RULE, QUEUE),
                    derived(QUEUE_RULE, authoritative), applied);
            deriveIfMissing(lockManager, sink, epic, new Reconciliation(EPIC_RULE, EPIC_PLANS),
                    derived(EPIC_RULE, authoritative), applied);
            return applied;
        };
    }

    /** Applies a derived write where it is missing, in a scope transaction that owes its reconciliation record. */
    private static void deriveIfMissing(LockManager lockManager, AuditSink sink, JournalScope scope,
            Reconciliation reconciliation, byte[] content, List<Reconciliation> applied) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var current = new AtomicStoreFile(scope.resolve(reconciliation.store()), scope.lockKey())
                    .read(transaction);
            if (current.isPresent() && Arrays.equals(current.get(), content)) {
                return;
            }
            var write = ScopeTransaction.open(transaction, scope, sink, CLOCK);
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
        var authoritative = bytes(AUTHORITATIVE);
        assertAll(
                () -> assertArrayEquals(authoritative, Files.readAllBytes(plan.resolve(STATUS))),
                () -> assertArrayEquals(derived(QUEUE_RULE, authoritative),
                        Files.readAllBytes(workspace.resolve(QUEUE))),
                () -> assertArrayEquals(derived(EPIC_RULE, authoritative),
                        Files.readAllBytes(epic.resolve(EPIC_PLANS))),
                () -> assertTrue(Stream.of(plan, workspace, epic)
                        .noneMatch(scope -> Files.exists(scope.journalFile())), "no journal remains"),
                () -> {
                    for (var scope : List.of(plan, workspace, epic)) {
                        try (var transaction = locks.openTransaction()) {
                            transaction.acquire(scope.lockKey());
                            assertTrue(ScopeIntegrity.verifyAll(transaction, scope, audit).stream()
                                    .allMatch(Verified.class::isInstance), "every digest of " + scope.type());
                        }
                    }
                });
    }

    /**
     * Every point a crash can stop the flow at between or inside its writes.
     *
     * @return the name of the point, the step the order stops after, the step whose transaction stops after its
     *         journal, and the reconciliations the restart applies
     */
    static Stream<Arguments> crashPoints() {
        return Stream.of(
                Arguments.of("after the authoritative write", 0, NO_STOP,
                        List.of(new Reconciliation(QUEUE_RULE, QUEUE), new Reconciliation(EPIC_RULE, EPIC_PLANS))),
                Arguments.of("after the first derived write", 1, NO_STOP,
                        List.of(new Reconciliation(EPIC_RULE, EPIC_PLANS))),
                Arguments.of("after the last write", 2, NO_STOP, List.of()),
                Arguments.of("inside the authoritative write, after its journal", NO_STOP, 0,
                        List.of(new Reconciliation(QUEUE_RULE, QUEUE), new Reconciliation(EPIC_RULE, EPIC_PLANS))),
                Arguments.of("inside the first derived write, after its journal", NO_STOP, 1,
                        List.of(new Reconciliation(EPIC_RULE, EPIC_PLANS))));
    }

    @Nested
    @DisplayName("a crash")
    class Crash {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.OrderedWriteTest#crashPoints")
        @DisplayName("is completed by recovery and reconcile, each derived write applied exactly once")
        void completedByRestart(String name, int stopAfterStep, int stopInStep, List<Reconciliation> expected) {
            var write = flow(stopAfterStep, stopInStep, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks));

            var applied = restart();

            assertAll(
                    () -> assertEquals(expected, applied),
                    () -> assertEquals(reconciledOf(expected.toArray(Reconciliation[]::new)), reconciledRecords(),
                            "one cross_scope_reconciled per applied reconciliation"),
                    OrderedWriteTest.this::assertConsistent);
        }

        @Test
        @DisplayName("a second restart after the completion applies nothing and records nothing more")
        void restartedOnce() throws Exception {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks));
            restart();
            var completed = snapshot();
            var records = audit.events().size();

            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(), applied),
                    () -> assertEquals(completed, snapshot()),
                    () -> assertEquals(records, audit.events().size()));
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
                            transaction -> replace(transaction, workspace, QUEUE, bytes(AUTHORITATIVE), false))));

            assertThrows(StopWrite.class, () -> write.run(locks));
            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(), applied),
                    () -> assertEquals(before, snapshot(), "nothing was written"),
                    () -> assertFalse(Files.exists(workspace.resolve(QUEUE)), "the later step was not run"),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }

    @Test
    @DisplayName("runs its steps in the declared order, each in a transaction of its own that holds one key")
    void orderAndTransactions() {
        var heldOtherKeys = new ArrayList<Boolean>();

        flow(NO_STOP, NO_STOP, heldOtherKeys).run(locks);

        assertAll(
                () -> assertEquals(List.of(plan.lockKey(), workspace.lockKey(), epic.lockKey()),
                        locks.acquisitions()),
                () -> assertEquals(3, locks.transactions()),
                () -> assertEquals(List.of(false, false, false), heldOtherKeys, "no step held a key of another"),
                () -> assertEquals(List.of(), locks.held(), "every transaction was closed"),
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
                () -> assertEquals(List.of(), applied),
                () -> assertEquals(before, snapshot()),
                () -> assertEquals(List.of(), audit.events()));
    }

    @Test
    @DisplayName("reconcile returns the reconciliations of every reconciler in their order, and records none itself")
    void severalReconcilers() {
        var first = new Reconciliation("first-rule", "a.json");
        var second = new Reconciliation("second-rule", "b.json");
        var third = new Reconciliation("third-rule", "c.json");
        var sinks = new ArrayList<AuditSink>();
        List<CrossScopeReconciler> reconcilers = List.of((_, sink) -> {
            sinks.add(sink);
            return List.of(first, second);
        }, (_, _) -> List.of(), (_, _) -> List.of(third));

        var applied = OrderedWrite.reconcile(locks, reconcilers, audit);

        assertAll(
                () -> assertEquals(List.of(first, second, third), applied),
                () -> assertEquals(List.of(audit), sinks, "the reconciler gets the sink of its transactions"),
                () -> assertEquals(List.of(), audit.events(), "the records come with the transactions"));
    }

    /** The record of a reconciliation is owed in the journal of its derived write and delivered at least once. */
    @Nested
    @DisplayName("audit delivery of a reconciliation")
    class ReconciliationDelivery {

        @Test
        @DisplayName("a sink that refuses the record keeps it owed; the next restart delivers it exactly once")
        void sinkRefuses() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks));
            var refusing = FailingAuditSink.refusing(1);

            var applied = OrderedWrite.reconcile(locks, List.of(reconciler()), refusing);
            var journalKept = Files.exists(workspace.journalFile());
            var restarted = restart();

            assertAll(
                    () -> assertEquals(List.of(new Reconciliation(QUEUE_RULE, QUEUE),
                            new Reconciliation(EPIC_RULE, EPIC_PLANS)), applied, "the data outcome as before"),
                    () -> assertTrue(journalKept, "the journal of the derived write keeps the record owed"),
                    () -> assertEquals(reconciledOf(new Reconciliation(EPIC_RULE, EPIC_PLANS)),
                            reconciledOf(refusing.events())),
                    () -> assertEquals(List.of(), restarted, "the derived writes were in place"),
                    () -> assertEquals(reconciledOf(new Reconciliation(QUEUE_RULE, QUEUE)), reconciledRecords(),
                            "the refused record delivered exactly once"),
                    OrderedWriteTest.this::assertConsistent);
        }

        @Test
        @DisplayName("a replay after the record was delivered delivers no second record")
        void noSecondRecord() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks));
            OrderedWrite.reconcile(locks, List.of(reconciler()), FailingAuditSink.refusing(1));
            restart();
            var records = audit.events().size();

            restart();

            assertEquals(records, audit.events().size());
        }

        @Test
        @DisplayName("a process stopped between the derived write and the append delivers the record at the restart")
        void stoppedBeforeAppend() {
            var write = flow(0, NO_STOP, new ArrayList<>());
            assertThrows(StopWrite.class, () -> write.run(locks));
            var reconcilers = List.of(reconciler());
            var stopping = FailingAuditSink.stopping(1);

            assertThrows(FailingAuditSink.ProcessStopped.class,
                    () -> OrderedWrite.reconcile(locks, reconcilers, stopping));
            var applied = restart();

            assertAll(
                    () -> assertEquals(List.of(new Reconciliation(EPIC_RULE, EPIC_PLANS)), applied),
                    () -> assertEquals(reconciledOf(new Reconciliation(QUEUE_RULE, QUEUE),
                            new Reconciliation(EPIC_RULE, EPIC_PLANS)), reconciledRecords(),
                            "the stopped record and the later one, each once"),
                    OrderedWriteTest.this::assertConsistent);
        }
    }

    @Test
    @DisplayName("an ordered write has at least its authoritative step")
    void noStep() {
        List<OrderedWrite.WriteStep> none = List.of();

        assertThrows(IllegalArgumentException.class, () -> new OrderedWrite(none));
    }
}
