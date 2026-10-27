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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;
import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.DurationBasis;
import de.planmarshall.core.store.MetricsRow.Form;
import de.planmarshall.core.store.MetricsRow.Outcome;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.Population;
import de.planmarshall.core.store.MetricsRow.SpendClass;

/**
 * The metrics store (Store Schemas § Metrics &amp; Quality Report): the row of a task and of a worker's idle turns,
 * written through the scope's transaction and read back with every column, the end of the attempt it copies, and the
 * partition of the rows over the scopes it serves.
 */
@DisplayName("Metrics store")
class MetricsStoreTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T10:00:00Z");
    private static final String JOB_ID = "l-0123456789abcdef";
    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private Path file;
    private MetricsStore store;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    private static HarnessUsage usage() {
        return new HarnessUsage(Measurement.measured(1200), Measurement.measured(340), Measurement.measured(5000),
                Measurement.measured(800), Measurement.measured(150), Measurement.measured(7),
                Optional.empty(), Optional.empty());
    }

    private static MetricsRow row(String queueTaskId, HarnessUsage usage) {
        return MetricsRow.builder().queueTaskId(queueTaskId).attempt(1).role("writer").jobId(JOB_ID)
                .generationId(JOB_ID).harness("opencode").harnessVersion("1.2.3").model("deepseek-v4.1-flash")
                .effort("high").form(Form.FRESH).phase(Phase.PHASE_5_EXECUTE).stepId("step-1").envelopeId(null)
                .predictedCost(null).yieldReason(null).outcome(Outcome.SUBMITTED).attemptEnd(AttemptEnd.CLOSED)
                .continuation(null).causeClass(CauseClass.BENIGN).spendClass(SpendClass.SUBMITTED)
                .population(Population.MODEL_JOB)
                .usage(usage).durationMs(1_000L).durationBasis(DurationBasis.PROCESS).queuedMs(0L)
                .consultationOf(null).recordedAt(RECORDED_AT).build();
    }

    @BeforeEach
    void createStore() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "some-plan");
        Files.createDirectories(scope.journalFile().getParent());
        file = scope.resolve(MetricsStore.FILE_NAME);
        store = new MetricsStore(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /** Opens the scope's transaction, hands it to the write and commits it. */
    private void commit(Consumer<ScopeTransaction> write) {
        commit(scope, write);
    }

    private void commit(JournalScope targetScope, Consumer<ScopeTransaction> write) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(targetScope.lockKey());
            var scopeTransaction = ScopeTransaction.open(transaction, targetScope, audit, CLOCK);
            write.accept(scopeTransaction);
            scopeTransaction.commit();
        }
    }

    private void append(MetricsRow row) {
        commit(transaction -> store.append(transaction, row));
    }

    private void append(WorkerIdleRow row) {
        commit(transaction -> store.append(transaction, row));
    }

    private ScopeIntegrity.Verification verify(String relativePath) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return ScopeIntegrity.verify(transaction, scope, relativePath, audit);
        }
    }

    @Nested
    @DisplayName("the task row")
    class TaskRow {

        @Test
        @DisplayName("round-trips with each usage column, the derived total and the attempt end")
        void roundTrip() {
            var row = row("qt-0123456789abcdef", usage());

            append(row);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertEquals(row, read),
                    () -> assertEquals(Measurement.measured(1200), read.usage().inputTokens()),
                    () -> assertEquals(Measurement.measured(340), read.usage().outputTokens()),
                    () -> assertEquals(Measurement.measured(5000), read.usage().cacheReadInputTokens()),
                    () -> assertEquals(Measurement.measured(800), read.usage().cacheCreationInputTokens()),
                    () -> assertEquals(Measurement.measured(150), read.usage().reasoningTokens()),
                    () -> assertEquals(Measurement.measured(7), read.usage().toolUses()),
                    () -> assertEquals(OptionalLong.of(7490), read.totalTokens()),
                    () -> assertEquals(AttemptEnd.CLOSED, read.attemptEnd()));
        }

        @Test
        @DisplayName("round-trips the continuation mark of a hold and of a resume")
        void continuationMark() {
            var marked = row("qt-0123456789abcdef", usage()).toBuilder()
                    .continuation(MetricsRow.ContinuationMark.RESUME).build();

            append(marked);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertEquals(marked, read),
                    () -> assertEquals(MetricsRow.ContinuationMark.RESUME, read.continuation()));
        }

        @Test
        @DisplayName("keeps an unmeasured figure and the reason of the row")
        void unmeasured() {
            var pointers = new UsagePointers("/input", "/output", null, null, null, null, null);
            var usage = pointers.read(JsonDocument.parse(
                    "{\"input\":1,\"output\":2}".getBytes(UTF_8), "turn"));

            append(row("qt-0123456789abcdef", usage));

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertSame(Measurement.State.UNMEASURED, read.usage().cacheReadInputTokens().state()),
                    () -> assertEquals(Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                            read.unmeasuredReason()));
        }

        @Test
        @DisplayName("round-trips the envelope's predicted cost and early end, and a row outside a plan")
        void envelopeRow() {
            var predicted = new MetricsRow.PredictedCost(1200L, MetricsRow.PredictedCost.Basis.CALIBRATED);
            var row = row("qt-0123456789abcdef", usage()).withPredictedCost(predicted)
                    .withYieldReason(MetricsRow.YieldReason.QUESTION).withPhase(null).withEnvelopeId("envelope-3")
                    .withEffort(null).withConsultationOf("qt-0123456789abcdee");

            append(row);

            assertEquals(row, store.snapshot().taskRows().getFirst());
        }

        @Test
        @DisplayName("a second row for the same task and attempt is refused")
        void duplicate() {
            append(row("qt-0123456789abcdef", usage()));
            var second = row("qt-0123456789abcdef", usage());

            assertThrows(IllegalArgumentException.class, () -> append(second));
        }
    }

    @Nested
    @DisplayName("the scope transaction")
    class ScopeTransactionWrite {

        @Test
        @DisplayName("an appended row has a sidecar entry, as every store of the scope's transactions")
        void sidecarEntry() {
            append(row("qt-0123456789abcdef", usage()));

            var digest = DigestSidecar.load(scope.sidecarFile()).digest(MetricsStore.FILE_NAME);

            assertAll(
                    () -> assertEquals(Optional.of(StoreDigest.of(Files.readAllBytes(file))), digest),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"));
        }

        @Test
        @DisplayName("an out-of-band edit of the store is a Mismatch of the integrity check")
        void outOfBandEdit() throws Exception {
            append(row("qt-0123456789abcdef", usage()));
            var recorded = DigestSidecar.load(scope.sidecarFile()).digest(MetricsStore.FILE_NAME).orElseThrow();
            var edited = Files.readAllBytes(file);
            edited[edited.length - 2] = ' ';
            Files.write(file, edited);

            var outcome = verify(MetricsStore.FILE_NAME);

            assertEquals(new ScopeIntegrity.Mismatch(MetricsStore.FILE_NAME, recorded,
                    Optional.of(StoreDigest.of(edited))), outcome);
        }

        @Test
        @DisplayName("a transaction of another scope is refused")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "another-plan");
            Files.createDirectories(other.journalFile().getParent());

            assertThrows(IllegalArgumentException.class, () -> appendInTransactionOf(other));
        }

        /** Appends this store's row in a transaction of another scope. */
        private void appendInTransactionOf(JournalScope other) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var scopeTransaction = ScopeTransaction.open(transaction, other, audit, CLOCK);
                store.append(scopeTransaction, row("qt-0123456789abcdef", usage()));
            }
        }
    }

    @Nested
    @DisplayName("the row's partition over the scopes")
    class Partition {

        @Test
        @DisplayName("the epic store holds a task row with no phase and refuses one with a phase")
        void epicTaskRow() throws Exception {
            var epic = JournalScope.epic(base, PROJECT, "some-epic");
            Files.createDirectories(epic.journalFile().getParent());
            var epicStore = new MetricsStore(epic);
            var withoutPhase = row("qt-0123456789abcdef", usage()).withPhase(null);

            commit(epic, transaction -> epicStore.append(transaction, withoutPhase));
            assertThrows(IllegalArgumentException.class, () -> commit(epic,
                    transaction -> epicStore.append(transaction, row("qt-0123456789abcdee", usage()))));

            assertAll(
                    () -> assertEquals(withoutPhase, epicStore.snapshot().taskRows().getFirst()),
                    () -> assertEquals("metrics.json", epicStore.storePath()));
        }

        @Test
        @DisplayName("the workspace store holds a task row with no phase and refuses one with a phase")
        void workspaceTaskRow() throws Exception {
            var workspace = JournalScope.workspace(base, PROJECT);
            Files.createDirectories(workspace.journalFile().getParent());
            var workspaceStore = new MetricsStore(workspace);
            var withoutPhase = row("qt-0123456789abcdef", usage()).withPhase(null);

            commit(workspace, transaction -> workspaceStore.append(transaction, withoutPhase));
            assertThrows(IllegalArgumentException.class,
                    () -> commit(workspace, transaction -> workspaceStore.append(transaction,
                            row("qt-0123456789abcdee", usage()))));

            assertAll(
                    () -> assertEquals(withoutPhase, workspaceStore.snapshot().taskRows().getFirst()),
                    () -> assertEquals("workspace/metrics.json", workspaceStore.storePath()));
        }

        @Test
        @DisplayName("only the workspace store holds a worker-idle row")
        void workerIdleRow() {
            var idle = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("deepseek-v4.1-flash").usage(usage()).idleWakeups(3)
                    .recordedAt(RECORDED_AT).build();
            var epic = JournalScope.epic(base, PROJECT, "some-epic");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> append(idle)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> commit(epic, transaction -> new MetricsStore(epic).append(transaction, idle))));
        }

        @Test
        @DisplayName("the workspace store accepts a worker-idle row")
        void workspaceIdleRow() throws Exception {
            var workspace = JournalScope.workspace(base, PROJECT);
            Files.createDirectories(workspace.journalFile().getParent());
            var workspaceStore = new MetricsStore(workspace);
            var idle = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("deepseek-v4.1-flash").usage(usage()).idleWakeups(3)
                    .recordedAt(RECORDED_AT).build();

            commit(workspace, transaction -> workspaceStore.append(transaction, idle));

            assertEquals(idle, workspaceStore.snapshot().idleRows().getFirst());
        }
    }

    @Nested
    @DisplayName("the end of the attempt")
    class AttemptEnds {

        static Stream<Arguments> values() {
            return Stream.of(AttemptEnd.values()).map(value -> Arguments.of(value, value.wireName()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("values")
        @DisplayName("is copied from the queue task record for every value of the enum")
        void copiesEveryValue(AttemptEnd end, String wireName) {
            var row = row("qt-0123456789abcdef", usage()).withAttemptEnd(end);

            append(row);

            assertAll(
                    () -> assertEquals(end, store.snapshot().taskRows().getFirst().attemptEnd()),
                    () -> assertEquals(wireName, end.wireName()));
        }

        @Test
        @DisplayName("is null, with the cause class refused, for a task closed without an attempt")
        void refusedTask() {
            var refused = MetricsRow.refused("qt-0123456789abcdef", "writer", Phase.PHASE_5_EXECUTE,
                    Outcome.LOST, SpendClass.UNMEASURED, Population.MODEL_TASK, RECORDED_AT);

            append(refused);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertNull(read.attemptEnd()),
                    () -> assertNull(read.attempt()),
                    () -> assertEquals(CauseClass.REFUSED, read.causeClass()),
                    () -> assertEquals(OptionalLong.empty(), read.totalTokens()),
                    () -> assertEquals(Optional.empty(), read.unmeasuredReason()));
        }
    }

    @Nested
    @DisplayName("the store document")
    class Document {

        @Test
        @DisplayName("round-trips a worker-idle row")
        void idleRow() throws Exception {
            var workspace = JournalScope.workspace(base, PROJECT);
            Files.createDirectories(workspace.journalFile().getParent());
            var workspaceStore = new MetricsStore(workspace);
            var idle = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("deepseek-v4.1-flash").usage(usage()).idleWakeups(3)
                    .recordedAt(RECORDED_AT).build();

            commit(workspace, transaction -> workspaceStore.append(transaction, idle));

            assertEquals(idle, workspaceStore.snapshot().idleRows().getFirst());
        }

        @Test
        @DisplayName("keeps a row of another kind verbatim instead of dropping it")
        void foreignRow() throws Exception {
            var waitRow = "{\"kind\":\"wait\",\"phase\":\"phase-5-execute\",\"wait_reason\":\"build\","
                    + "\"deliberate\":false,\"duration_ms\":1200,\"started_at\":\"2026-10-22T09:00:00Z\"}";
            Files.writeString(file, "{\"format_version\":1,\"rows\":[" + waitRow + "]}", UTF_8);

            append(row("qt-0123456789abcdef", usage()));

            var document = store.snapshot();
            assertAll(
                    () -> assertEquals(1, document.taskRows().size()),
                    () -> assertEquals(List.of(waitRow), document.foreignRows()),
                    () -> assertTrue(Files.readString(file, UTF_8).contains("\"kind\":\"wait\"")));
        }

        @Test
        @DisplayName("a store in a newer format is refused and left as it is")
        void newerFormat() throws Exception {
            Files.writeString(file, "{\"format_version\":2,\"rows\":[]}", UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
            assertEquals("{\"format_version\":2,\"rows\":[]}", Files.readString(file, UTF_8));
        }

        @Test
        @DisplayName("a total that does not equal the sum of its components is refused")
        void inconsistentTotal() throws Exception {
            var inconsistent = """
                    {"format_version":1,"rows":[{"kind":"task","queue_task_id":"qt-0123456789abcdef","attempt":1,
                     "role":"writer","job_id":"l-0123456789abcdef","generation_id":"l-0123456789abcdef",
                     "harness":"opencode","harness_version":"1.2.3","model":"m","effort":null,"form":"fresh",
                     "phase":"phase-5-execute","step_id":null,"envelope_id":null,"predicted_cost":null,
                     "yield_reason":null,"outcome":"submitted","attempt_end":"closed","continuation":null,"cause_class":"benign",
                     "spend_class":"submitted","population":"model_job","total_tokens":9,
                     "input_tokens":1,"output_tokens":1,"cache_read_input_tokens":"not_applicable",
                     "cache_creation_input_tokens":"not_applicable","reasoning_tokens":"not_applicable",
                     "tool_uses":"unmeasured","cost_usd":"unmeasured","unmeasured_reason":"not_reported_by_harness",
                     "duration_ms":null,"duration_basis":"process","queued_ms":null,"consultation_of":null,
                     "recorded_at":"2026-10-22T10:00:00Z"}]}""";
            Files.writeString(file, inconsistent, UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }

        @Test
        @DisplayName("a tool-uses figure declared not_applicable is refused")
        void toolUsesNotApplicable() throws Exception {
            var notApplicable = """
                    {"format_version":1,"rows":[{"kind":"task","queue_task_id":"qt-0123456789abcdef","attempt":1,
                     "role":"writer","job_id":"l-0123456789abcdef","generation_id":"l-0123456789abcdef",
                     "harness":"opencode","harness_version":"1.2.3","model":"m","effort":null,"form":"fresh",
                     "phase":"phase-5-execute","step_id":null,"envelope_id":null,"predicted_cost":null,
                     "yield_reason":null,"outcome":"submitted","attempt_end":"closed","continuation":null,"cause_class":"benign",
                     "spend_class":"submitted","population":"model_job","total_tokens":2,
                     "input_tokens":1,"output_tokens":1,"cache_read_input_tokens":"not_applicable",
                     "cache_creation_input_tokens":"not_applicable","reasoning_tokens":"not_applicable",
                     "tool_uses":"not_applicable","cost_usd":"unmeasured","unmeasured_reason":null,
                     "duration_ms":null,"duration_basis":"process","queued_ms":null,"consultation_of":null,
                     "recorded_at":"2026-10-22T10:00:00Z"}]}""";
            Files.writeString(file, notApplicable, UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }
    }

    @Nested
    @DisplayName("the row's rules")
    class Validation {

        @Test
        @DisplayName("refuses an attempt without its end or its end without an attempt")
        void attemptAndEnd() {
            var withoutEnd = row("qt-0123456789abcdef", usage()).toBuilder().attemptEnd(null);
            var withoutAttempt = row("qt-0123456789abcdef", usage()).toBuilder().attempt(null);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, withoutEnd::build),
                    () -> assertThrows(IllegalArgumentException.class, withoutAttempt::build));
        }

        @Test
        @DisplayName("refuses a refused row that carries a worker, and a broken identifier")
        void refusedColumns() {
            var refusedWithWorker = row("qt-0123456789abcdef", usage()).toBuilder().attempt(null).attemptEnd(null)
                    .causeClass(CauseClass.REFUSED);
            var badId = row("qt-0123456789abcdef", usage()).toBuilder().queueTaskId("qt-not-an-id");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, refusedWithWorker::build),
                    () -> assertThrows(IllegalArgumentException.class, badId::build),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new MetricsRow.PredictedCost(-1L, MetricsRow.PredictedCost.Basis.UNCALIBRATED)));
        }

        @Test
        @DisplayName("refuses an idle row with a broken job, role or count")
        void idleRefusals() {
            var badJob = WorkerIdleRow.builder().jobId("not-a-job").role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("m").usage(usage()).idleWakeups(0).recordedAt(RECORDED_AT);
            var badWakeups = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("m").usage(usage()).idleWakeups(-1).recordedAt(RECORDED_AT);
            var emptyModel = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("").usage(usage()).idleWakeups(0).recordedAt(RECORDED_AT);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, badJob::build),
                    () -> assertThrows(IllegalArgumentException.class, badWakeups::build),
                    () -> assertThrows(IllegalArgumentException.class, emptyModel::build));
        }

        @Test
        @DisplayName("a task row whose outcome is JSON null is refused")
        void nullOutcome() throws Exception {
            var nullOutcome = """
                    {"format_version":1,"rows":[{"kind":"task","queue_task_id":"qt-0123456789abcdef","attempt":1,
                     "role":"writer","job_id":"l-0123456789abcdef","generation_id":"l-0123456789abcdef",
                     "harness":"opencode","harness_version":"1.2.3","model":"m","effort":null,"form":"fresh",
                     "phase":"phase-5-execute","step_id":null,"envelope_id":null,"predicted_cost":null,
                     "yield_reason":null,"outcome":null,"attempt_end":"closed","continuation":null,"cause_class":"benign",
                     "spend_class":"submitted","population":"model_job","total_tokens":2,
                     "input_tokens":1,"output_tokens":1,"cache_read_input_tokens":"not_applicable",
                     "cache_creation_input_tokens":"not_applicable","reasoning_tokens":"not_applicable",
                     "tool_uses":"unmeasured","cost_usd":"unmeasured","unmeasured_reason":null,
                     "duration_ms":null,"duration_basis":"process","queued_ms":null,"consultation_of":null,
                     "recorded_at":"2026-10-22T10:00:00Z"}]}""";
            Files.writeString(file, nullOutcome, UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot());
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the workspace store is the workspace's metrics.json")
        void workspacePath() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertAll(
                    () -> assertEquals("workspace/metrics.json", new MetricsStore(workspace).storePath()),
                    () -> assertEquals(workspace.resolve("workspace/metrics.json"),
                            new MetricsStore(workspace).store()));
        }

        @Test
        @DisplayName("the lesson store has no metrics store")
        void lessonStore() {
            var lessons = JournalScope.lessons(base, PROJECT);

            assertThrows(IllegalArgumentException.class, () -> new MetricsStore(lessons));
        }
    }
}
