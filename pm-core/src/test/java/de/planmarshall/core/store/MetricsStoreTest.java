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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
 * partition of the rows over the scopes it serves. The store documents written as text blocks, the store paths and the
 * column and enum names are wire contracts.
 */
@EnableGeneratorController
@DisplayName("Metrics store")
class MetricsStoreTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private Instant recordedAt;
    private Clock clock;
    private String jobId;
    private String taskId;
    private String otherTaskId;
    private String role;
    private String harness;
    private String harnessVersion;
    private String model;
    private long input;
    private long output;
    private long cacheRead;
    private long cacheCreation;
    private long reasoning;
    private long toolUses;
    private JournalScope scope;
    private Path file;
    private MetricsStore store;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    private static String hex16() {
        return "%016x".formatted(Generators.longs(Long.MIN_VALUE, Long.MAX_VALUE).next());
    }

    private static String lowerName() {
        return Generators.letterStrings(2, 20).next().toLowerCase(Locale.ROOT);
    }

    private HarnessUsage usage() {
        return new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.measured(cacheCreation), Measurement.measured(reasoning),
                Measurement.measured(toolUses), Optional.empty(), Optional.empty());
    }

    private MetricsRow row(String queueTaskId, HarnessUsage usage) {
        return MetricsRow.builder().queueTaskId(queueTaskId).attempt(Generators.integers(1, 9).next()).role(role)
                .jobId(jobId).generationId(jobId).harness(harness).harnessVersion(harnessVersion).model(model)
                .effort(lowerName()).form(Form.FRESH).phase(Phase.PHASE_5_EXECUTE).stepId("step-" + lowerName())
                .envelopeId(null).predictedCost(null).yieldReason(null).outcome(Outcome.SUBMITTED)
                .attemptEnd(AttemptEnd.CLOSED).continuation(null).causeClass(CauseClass.BENIGN)
                .spendClass(SpendClass.SUBMITTED).population(Population.MODEL_JOB).usage(usage)
                .durationMs(Generators.longs(0, 1_000_000L).next()).durationBasis(DurationBasis.PROCESS)
                .queuedMs(Generators.longs(0, 1_000_000L).next()).consultationOf(null).recordedAt(recordedAt).build();
    }

    private WorkerIdleRow.WorkerIdleRowBuilder idleRow() {
        return WorkerIdleRow.builder().jobId(jobId).role(role).harness(harness).harnessVersion(harnessVersion)
                .model(model).usage(usage()).idleWakeups(Generators.integers(0, 100).next()).recordedAt(recordedAt);
    }

    @BeforeEach
    void createStore() throws IOException {
        recordedAt = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        jobId = "l-" + hex16();
        taskId = "qt-" + hex16();
        do {
            otherTaskId = "qt-" + hex16();
        } while (otherTaskId.equals(taskId));
        role = lowerName();
        harness = lowerName();
        harnessVersion = Generators.integers(0, 9).next() + "." + Generators.integers(0, 99).next();
        model = lowerName();
        input = Generators.longs(0, 100_000L).next();
        output = Generators.longs(0, 100_000L).next();
        cacheRead = Generators.longs(0, 100_000L).next();
        cacheCreation = Generators.longs(0, 100_000L).next();
        reasoning = Generators.longs(0, 100_000L).next();
        toolUses = Generators.longs(0, 100L).next();
        scope = JournalScope.plan(base, PROJECT, "plan-" + lowerName());
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
            var scopeTransaction = ScopeTransaction.open(transaction, targetScope, audit, clock);
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
            var row = row(taskId, usage());

            append(row);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertEquals(row, read, "the task row should round-trip unchanged"),
                    () -> assertEquals(Measurement.measured(input), read.usage().inputTokens(),
                            "the input tokens should round-trip"),
                    () -> assertEquals(Measurement.measured(output), read.usage().outputTokens(),
                            "the output tokens should round-trip"),
                    () -> assertEquals(Measurement.measured(cacheRead), read.usage().cacheReadInputTokens(),
                            "the cache-read tokens should round-trip"),
                    () -> assertEquals(Measurement.measured(cacheCreation), read.usage().cacheCreationInputTokens(),
                            "the cache-creation tokens should round-trip"),
                    () -> assertEquals(Measurement.measured(reasoning), read.usage().reasoningTokens(),
                            "the reasoning tokens should round-trip"),
                    () -> assertEquals(Measurement.measured(toolUses), read.usage().toolUses(),
                            "the tool uses should round-trip"),
                    () -> assertEquals(OptionalLong.of(input + output + cacheRead + cacheCreation + reasoning),
                            read.totalTokens(), "the total should be the sum of the token columns"),
                    () -> assertEquals(AttemptEnd.CLOSED, read.attemptEnd(), "the attempt end should round-trip"));
        }

        @Test
        @DisplayName("round-trips the continuation mark of a hold and of a resume")
        void continuationMark() {
            var marked = row(taskId, usage()).toBuilder()
                    .continuation(MetricsRow.ContinuationMark.RESUME).build();

            append(marked);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertEquals(marked, read, "the marked row should round-trip unchanged"),
                    () -> assertEquals(MetricsRow.ContinuationMark.RESUME, read.continuation(),
                            "the resume mark should round-trip"));
        }

        @Test
        @DisplayName("keeps an unmeasured figure and the reason of the row; the usage pointers and turn are wire data")
        void unmeasured() {
            var pointers = new UsagePointers("/input", "/output", null, null, null, null, null);
            var usage = pointers.read(JsonDocument.parse(
                    "{\"input\":%d,\"output\":%d}".formatted(input, output).getBytes(UTF_8), "turn"));

            append(row(taskId, usage));

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertSame(Measurement.State.UNMEASURED, read.usage().cacheReadInputTokens().state(),
                            "an unreported figure should stay unmeasured"),
                    () -> assertEquals(Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                            read.unmeasuredReason(), "the unmeasured reason should be kept"));
        }

        @Test
        @DisplayName("round-trips the envelope's predicted cost and early end, and a row outside a plan")
        void envelopeRow() {
            var predicted = new MetricsRow.PredictedCost(Generators.longs(0, 1_000_000L).next(),
                    MetricsRow.PredictedCost.Basis.CALIBRATED);
            var row = row(taskId, usage()).withPredictedCost(predicted)
                    .withYieldReason(MetricsRow.YieldReason.QUESTION).withPhase(null)
                    .withEnvelopeId("envelope-" + Generators.integers(0, 999).next())
                    .withEffort(null).withConsultationOf(otherTaskId);

            append(row);

            assertEquals(row, store.snapshot().taskRows().getFirst(), "the envelope row should round-trip");
        }

        @Test
        @DisplayName("a second row for the same task and attempt is refused")
        void duplicate() {
            var first = row(taskId, usage());
            append(first);
            var second = first.toBuilder().model(lowerName()).build();

            assertThrows(IllegalArgumentException.class, () -> append(second),
                    "a second row of the same attempt should fail");
        }
    }

    @Nested
    @DisplayName("the scope transaction")
    class ScopeTransactionWrite {

        @Test
        @DisplayName("an appended row has a sidecar entry, as every store of the scope's transactions")
        void sidecarEntry() {
            append(row(taskId, usage()));

            var digest = DigestSidecar.load(scope.sidecarFile()).digest(MetricsStore.FILE_NAME);

            assertAll(
                    () -> assertEquals(Optional.of(StoreDigest.of(Files.readAllBytes(file))), digest,
                            "the sidecar should hold the store digest"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed after it"));
        }

        @Test
        @DisplayName("an out-of-band edit of the store is a Mismatch of the integrity check")
        void outOfBandEdit() throws Exception {
            append(row(taskId, usage()));
            var recorded = DigestSidecar.load(scope.sidecarFile()).digest(MetricsStore.FILE_NAME).orElseThrow();
            var edited = Files.readAllBytes(file);
            edited[edited.length - 2] = ' ';
            Files.write(file, edited);

            var outcome = verify(MetricsStore.FILE_NAME);

            assertEquals(new ScopeIntegrity.Mismatch(MetricsStore.FILE_NAME, recorded,
                    Optional.of(StoreDigest.of(edited))), outcome, "an edited store should be a mismatch");
        }

        @Test
        @DisplayName("a transaction of another scope is refused")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "other-" + lowerName());
            Files.createDirectories(other.journalFile().getParent());

            assertThrows(IllegalArgumentException.class, () -> appendInTransactionOf(other),
                    "a transaction of another scope should fail");
        }

        /** Appends this store's row in a transaction of another scope. */
        private void appendInTransactionOf(JournalScope other) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var scopeTransaction = ScopeTransaction.open(transaction, other, audit, clock);
                store.append(scopeTransaction, row(taskId, usage()));
            }
        }
    }

    @Nested
    @DisplayName("the row's partition over the scopes; the store paths are layout contracts")
    class Partition {

        @Test
        @DisplayName("the epic store holds a task row with no phase and refuses one with a phase")
        void epicTaskRow() throws Exception {
            var epic = JournalScope.epic(base, PROJECT, "epic-" + lowerName());
            Files.createDirectories(epic.journalFile().getParent());
            var epicStore = new MetricsStore(epic);
            var withoutPhase = row(taskId, usage()).withPhase(null);

            commit(epic, transaction -> epicStore.append(transaction, withoutPhase));
            assertThrows(IllegalArgumentException.class, () -> commit(epic,
                            transaction -> epicStore.append(transaction, row(otherTaskId, usage()))),
                    "a phased row in the epic store should fail");

            assertAll(
                    () -> assertEquals(withoutPhase, epicStore.snapshot().taskRows().getFirst(),
                            "the epic store should hold the phaseless row"),
                    () -> assertEquals("metrics.json", epicStore.storePath(),
                            "the epic store path should be metrics.json"));
        }

        @Test
        @DisplayName("the workspace store holds a task row with no phase and refuses one with a phase")
        void workspaceTaskRow() throws Exception {
            var workspace = JournalScope.workspace(base, PROJECT);
            Files.createDirectories(workspace.journalFile().getParent());
            var workspaceStore = new MetricsStore(workspace);
            var withoutPhase = row(taskId, usage()).withPhase(null);

            commit(workspace, transaction -> workspaceStore.append(transaction, withoutPhase));
            assertThrows(IllegalArgumentException.class,
                    () -> commit(workspace, transaction -> workspaceStore.append(transaction,
                            row(otherTaskId, usage()))),
                    "a phased row in the workspace store should fail");

            assertAll(
                    () -> assertEquals(withoutPhase, workspaceStore.snapshot().taskRows().getFirst(),
                            "the workspace store should hold the row"),
                    () -> assertEquals("workspace/metrics.json", workspaceStore.storePath(),
                            "the workspace store path should be fixed"));
        }

        @Test
        @DisplayName("only the workspace store holds a worker-idle row")
        void workerIdleRow() {
            var idle = idleRow().build();
            var epic = JournalScope.epic(base, PROJECT, "epic-" + lowerName());

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> append(idle),
                            "a plan store should refuse an idle row"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> commit(epic, transaction -> new MetricsStore(epic).append(transaction, idle)),
                            "an epic store should refuse an idle row"));
        }

        @Test
        @DisplayName("the workspace store accepts a worker-idle row")
        void workspaceIdleRow() throws Exception {
            var workspace = JournalScope.workspace(base, PROJECT);
            Files.createDirectories(workspace.journalFile().getParent());
            var workspaceStore = new MetricsStore(workspace);
            var idle = idleRow().build();

            commit(workspace, transaction -> workspaceStore.append(transaction, idle));

            assertEquals(idle, workspaceStore.snapshot().idleRows().getFirst(),
                    "the workspace store should hold the idle row");
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
            var row = row(taskId, usage()).withAttemptEnd(end);

            append(row);

            assertAll(
                    () -> assertEquals(end, store.snapshot().taskRows().getFirst().attemptEnd(),
                            "the attempt end should be copied"),
                    () -> assertEquals(wireName, end.wireName(), "the wire name should be the enum's own"));
        }

        @Test
        @DisplayName("is null, with the cause class refused, for a task closed without an attempt")
        void refusedTask() {
            var refused = MetricsRow.refused(taskId, role, Phase.PHASE_5_EXECUTE,
                    Outcome.LOST, SpendClass.UNMEASURED, Population.MODEL_TASK, recordedAt);

            append(refused);

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertNull(read.attemptEnd(), "a refused task should have no attempt end"),
                    () -> assertNull(read.attempt(), "a refused task should have no attempt"),
                    () -> assertEquals(CauseClass.REFUSED, read.causeClass(),
                            "the cause class should be refused"),
                    () -> assertEquals(OptionalLong.empty(), read.totalTokens(),
                            "a refused task should have no total"),
                    () -> assertEquals(Optional.empty(), read.unmeasuredReason(),
                            "a refused task should have no unmeasured reason"));
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
            var idle = MetricsStoreTest.this.idleRow().build();

            commit(workspace, transaction -> workspaceStore.append(transaction, idle));

            assertEquals(idle, workspaceStore.snapshot().idleRows().getFirst(),
                    "the idle row should round-trip unchanged");
        }

        @Test
        @DisplayName("keeps a row of another kind verbatim instead of dropping it; the wait row is a wire document")
        void foreignRow() throws Exception {
            var waitRow = "{\"kind\":\"wait\",\"phase\":\"phase-5-execute\",\"wait_reason\":\"build\","
                    + "\"deliberate\":false,\"duration_ms\":1200,\"started_at\":\"2026-10-22T09:00:00Z\"}";
            Files.writeString(file, "{\"format_version\":1,\"rows\":[" + waitRow + "]}", UTF_8);

            append(row(taskId, usage()));

            var document = store.snapshot();
            assertAll(
                    () -> assertEquals(1, document.taskRows().size(), "the store should hold one task row"),
                    () -> assertEquals(List.of(waitRow), document.foreignRows(),
                            "the foreign row should be kept verbatim"),
                    () -> assertTrue(Files.readString(file, UTF_8).contains("\"kind\":\"wait\""),
                            "the written store should keep the wait row"));
        }

        @Test
        @DisplayName("a store in a newer format is refused and left as it is; the document is a wire document")
        void newerFormat() throws Exception {
            var newer = "{\"format_version\":2,\"rows\":[]}";
            Files.writeString(file, newer, UTF_8);

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a newer format should be refused");
            assertEquals(newer, Files.readString(file, UTF_8), "the refused store should stay unchanged");
        }

        @Test
        @DisplayName("a total that does not equal the sum of its components is refused (wire document)")
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

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "an inconsistent total should be refused");
        }

        @Test
        @DisplayName("a tool-uses figure declared not_applicable is refused (wire document)")
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

            assertThrows(StoreRefusal.class, () -> store.snapshot(),
                    "a not-applicable tool-use figure should fail");
        }
    }

    @Nested
    @DisplayName("the row's rules")
    class Validation {

        @Test
        @DisplayName("refuses an attempt without its end or its end without an attempt")
        void attemptAndEnd() {
            var withoutEnd = row(taskId, usage()).toBuilder().attemptEnd(null);
            var withoutAttempt = row(taskId, usage()).toBuilder().attempt(null);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, withoutEnd::build,
                            "an attempt without its end should fail"),
                    () -> assertThrows(IllegalArgumentException.class, withoutAttempt::build,
                            "an end without its attempt should fail"));
        }

        @Test
        @DisplayName("refuses a refused row that carries a worker, and a broken identifier (grammar violation)")
        void refusedColumns() {
            var refusedWithWorker = row(taskId, usage()).toBuilder().attempt(null).attemptEnd(null)
                    .causeClass(CauseClass.REFUSED);
            var badId = row(taskId, usage()).toBuilder().queueTaskId("qt-not-an-id");
            var negativeCost = -Generators.longs(2, 1_000L).next();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, refusedWithWorker::build,
                            "a refused row with a worker should fail"),
                    () -> assertThrows(IllegalArgumentException.class, badId::build,
                            "a broken queue task id should fail"),
                    // -1 is the first refused cost, so it is the boundary of the contract and stays a literal
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new MetricsRow.PredictedCost(-1, MetricsRow.PredictedCost.Basis.UNCALIBRATED),
                            "the first negative predicted cost should fail"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new MetricsRow.PredictedCost(negativeCost,
                                    MetricsRow.PredictedCost.Basis.UNCALIBRATED),
                            "a negative predicted cost should fail"));
        }

        @Test
        @DisplayName("refuses an idle row with a broken job, role or count (grammar violations)")
        void idleRefusals() {
            var badJob = idleRow().jobId("not-a-job");
            // -1 is the first refused count, so it is the boundary of the contract and stays a literal
            var firstBadWakeups = idleRow().idleWakeups(-1);
            var badWakeups = idleRow().idleWakeups(-Generators.integers(2, 100).next());
            var emptyModel = idleRow().model("");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, badJob::build,
                            "a broken job id should fail the idle row"),
                    () -> assertThrows(IllegalArgumentException.class, firstBadWakeups::build,
                            "the first negative wakeup count should fail"),
                    () -> assertThrows(IllegalArgumentException.class, badWakeups::build,
                            "a negative wakeup count should fail"),
                    () -> assertThrows(IllegalArgumentException.class, emptyModel::build,
                            "an empty model should fail the idle row"));
        }

        @Test
        @DisplayName("a task row whose outcome is JSON null is refused (wire document)")
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

            assertThrows(StoreRefusal.class, () -> store.snapshot(), "a null outcome should be refused");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the workspace store is the workspace's metrics.json (layout contract)")
        void workspacePath() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertAll(
                    () -> assertEquals("workspace/metrics.json", new MetricsStore(workspace).storePath(),
                            "the workspace store path should be fixed"),
                    () -> assertEquals(workspace.resolve("workspace/metrics.json"),
                            new MetricsStore(workspace).store(), "the workspace store file should be fixed"));
        }

        @Test
        @DisplayName("the lesson store has no metrics store")
        void lessonStore() {
            var lessons = JournalScope.lessons(base, PROJECT);

            assertThrows(IllegalArgumentException.class, () -> new MetricsStore(lessons),
                    "the lesson scope should have no metrics");
        }
    }
}
