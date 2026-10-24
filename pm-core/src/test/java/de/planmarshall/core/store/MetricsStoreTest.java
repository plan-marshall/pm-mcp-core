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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
 * written and read back with every column, and the end of the attempt it copies.
 */
@DisplayName("Metrics store")
class MetricsStoreTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T10:00:00Z");
    private static final String JOB_ID = "l-0123456789abcdef";

    @TempDir
    Path base;

    private Path file;
    private MetricsStore store;

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
                .causeClass(CauseClass.BENIGN).spendClass(SpendClass.SUBMITTED).population(Population.MODEL_JOB)
                .usage(usage).durationMs(1_000L).durationBasis(DurationBasis.PROCESS).queuedMs(0L)
                .consultationOf(null).recordedAt(RECORDED_AT).build();
    }

    @BeforeEach
    void createStore() throws IOException {
        file = Files.createDirectories(base.resolve("plans").resolve("some-plan")).resolve("metrics.json");
        store = new MetricsStore(new AtomicStoreFile(file, LockKey.plan(base, "some-plan", "some-plan")),
                new RecordingLockManager());
    }

    @Nested
    @DisplayName("the task row")
    class TaskRow {

        @Test
        @DisplayName("round-trips with each usage column, the derived total and the attempt end")
        void roundTrip() {
            var row = row("qt-0123456789abcdef", usage());

            store.append(row);

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
        @DisplayName("keeps an unmeasured figure and the reason of the row")
        void unmeasured() {
            var pointers = new UsagePointers("/input", "/output", null, null, null, null, null);
            var usage = pointers.read(JsonDocument.parse(
                    "{\"input\":1,\"output\":2}".getBytes(UTF_8), "turn"));

            store.append(row("qt-0123456789abcdef", usage));

            var read = store.snapshot().taskRows().getFirst();
            assertAll(
                    () -> assertSame(Measurement.State.UNMEASURED, read.usage().cacheReadInputTokens().state()),
                    () -> assertEquals(Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                            read.unmeasuredReason()));
        }

        @Test
        @DisplayName("a second row for the same task and attempt is refused")
        void duplicate() {
            store.append(row("qt-0123456789abcdef", usage()));

            assertThrows(IllegalArgumentException.class,
                    () -> store.append(row("qt-0123456789abcdef", usage())));
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

            store.append(row);

            assertAll(
                    () -> assertEquals(end, store.snapshot().taskRows().getFirst().attemptEnd()),
                    () -> assertEquals(wireName, end.wireName()));
        }

        @Test
        @DisplayName("is null, with the cause class refused, for a task closed without an attempt")
        void refusedTask() {
            var refused = MetricsRow.refused("qt-0123456789abcdef", "writer", Phase.PHASE_5_EXECUTE,
                    Outcome.LOST, SpendClass.UNMEASURED, Population.MODEL_TASK, RECORDED_AT);

            store.append(refused);

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
        void idleRow() {
            var idle = WorkerIdleRow.builder().jobId(JOB_ID).role("writer").harness("opencode")
                    .harnessVersion("1.2.3").model("deepseek-v4.1-flash").usage(usage()).idleWakeups(3)
                    .recordedAt(RECORDED_AT).build();

            store.append(idle);

            assertEquals(idle, store.snapshot().idleRows().getFirst());
        }

        @Test
        @DisplayName("keeps a row of another kind verbatim instead of dropping it")
        void foreignRow() throws Exception {
            var waitRow = "{\"kind\":\"wait\",\"phase\":\"phase-5-execute\",\"wait_reason\":\"build\","
                    + "\"deliberate\":false,\"duration_ms\":1200,\"started_at\":\"2026-10-22T09:00:00Z\"}";
            Files.writeString(file, "{\"format_version\":1,\"rows\":[" + waitRow + "]}", UTF_8);

            store.append(row("qt-0123456789abcdef", usage()));

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
                     "yield_reason":null,"outcome":"submitted","attempt_end":"closed","cause_class":"benign",
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
                     "yield_reason":null,"outcome":"submitted","attempt_end":"closed","cause_class":"benign",
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
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a lock that is not the lock of a scope is refused")
        void otherLockLevel() {
            assertThrows(IllegalArgumentException.class, () -> new MetricsStore(
                    new AtomicStoreFile(file, LockKey.buildSlots(base)), new RecordingLockManager()));
        }
    }
}
