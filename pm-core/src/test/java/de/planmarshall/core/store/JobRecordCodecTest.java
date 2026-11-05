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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.ClampedValue;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.SelfReportContradiction;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The job record codec and the rules of the record (PM-EXEC-4, PM-IMPL-2): every status and fate round-trips byte for
 * byte, {@code worker} and {@code gate_facts} are copied verbatim, and every document outside the one shape of the
 * record is refused. The child wall time is recorded only for an observed exit and never exceeds the job's wall time
 * (PM-EXEC-4, bullet 7, record side; the watch hazard of child wall times without an observed exit).
 */
@EnableGeneratorController
@DisplayName("Job record codec")
class JobRecordCodecTest {

    static final JobId BUILD_ID = new JobId("b-0123456789abcdef");
    static final Scope PLAN_SCOPE = new Scope(Scope.Type.PLAN, "plan-one");
    static final Instant CREATED_AT = Instant.parse("2026-10-17T10:00:00Z");
    static final Instant SPAWNED_AT = CREATED_AT.plusSeconds(5);
    static final Instant PROCESS_STARTED_AT = SPAWNED_AT.plusMillis(20);
    static final Instant ENDED_AT = SPAWNED_AT.plusSeconds(60);
    static final long CHILD_WALL_MS = 59_000L;
    static final String ARGV_DIGEST = "%064x".formatted(Generators.longs().next());

    private static final Path FILE = Path.of("/pm-base/projects/sample/jobs/b-0123456789abcdef.json");

    /** The builder of the record of a process job that waits for a slot. */
    static JobRecord.JobRecordBuilder base(JobId jobId, Scope scope) {
        return JobRecord.builder().jobId(jobId).execution(Execution.PROCESS).scope(scope).budgetKey("build:verify")
                .budgetSeconds(600)
                .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.LEARNED, Generators.longs(0, 20).next()))
                .argvDigest(ARGV_DIGEST).status(Status.QUEUED).confinement(Confinement.UNAVAILABLE)
                .createdAt(CREATED_AT).clampedValues(List.of());
    }

    /** The record of a job that waits for a slot. */
    static JobRecord queued(JobId jobId, Scope scope) {
        return base(jobId, scope).build();
    }

    /** The write-ahead record of a worker with its worker block. */
    static JobRecord workerAhead(JobId jobId, Scope scope, String worker) {
        return base(jobId, scope).status(Status.RUNNING).worker(worker).build();
    }

    /** The write-ahead record of the job: running, neither PID nor process start. */
    static JobRecord writeAhead(JobId jobId, Scope scope) {
        return queued(jobId, scope).withStatus(Status.RUNNING);
    }

    /** The post-spawn record of the job: PID, process start and spawn instant from the launch report. */
    static JobRecord postSpawn(JobId jobId, Scope scope) {
        return writeAhead(jobId, scope).toBuilder().pid(Generators.longs(1, 1_000_000).next())
                .processStartedAt(PROCESS_STARTED_AT).spawnedAt(SPAWNED_AT).confinement(Confinement.CONFINED)
                .landlockAbi(Generators.integers(2, 10).next()).build();
    }

    /** The settled record of the job, with its observed exit. */
    static JobRecord ended(JobId jobId, Scope scope, Fate fate, int outcomeRow) {
        return postSpawn(jobId, scope).toBuilder().status(Status.ENDED).fate(fate).outcomeRow(outcomeRow)
                .endedAt(ENDED_AT).childWallMs(CHILD_WALL_MS).exitCode(0L).build();
    }

    /** The only record of a job refused before spawn. */
    static JobRecord refused(JobId jobId, Scope scope) {
        return queued(jobId, scope).toBuilder().status(Status.REFUSED).fate(Fate.REFUSED).outcomeRow(1)
                .refusalReason("executable_digest_mismatch").endedAt(CREATED_AT).build();
    }

    static Stream<Arguments> records() {
        var worker = new JobId("l-00000000000000ff");
        var probe = new JobId("v-00000000000000aa");
        var git = new JobId("g-00000000000000bb");
        var script = new JobId("p-00000000000000cc");
        return Stream.of(
                Arguments.of("queued", queued(BUILD_ID, PLAN_SCOPE)),
                Arguments.of("write-ahead", writeAhead(BUILD_ID, PLAN_SCOPE)),
                Arguments.of("post-spawn", postSpawn(BUILD_ID, PLAN_SCOPE)),
                Arguments.of("succeeded", ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7)),
                Arguments.of("failed", ended(BUILD_ID, PLAN_SCOPE, Fate.FAILED, 8)),
                Arguments.of("failed_unparsed", ended(BUILD_ID, PLAN_SCOPE, Fate.FAILED_UNPARSED, 10)),
                Arguments.of("timed_out", ended(BUILD_ID, PLAN_SCOPE, Fate.TIMED_OUT, 3).withSignal("SIGKILL")
                        .withExitCode(null)),
                Arguments.of("cancelled", ended(BUILD_ID, PLAN_SCOPE, Fate.CANCELLED, 2).withCancelBeforeGroup(true)),
                Arguments.of("killed without an observed exit", ended(BUILD_ID, PLAN_SCOPE, Fate.KILLED, 4)
                        .withChildWallMs(null).withExitCode(null)),
                Arguments.of("indeterminate", ended(BUILD_ID, PLAN_SCOPE, Fate.INDETERMINATE, 13)),
                Arguments.of("refused", refused(BUILD_ID, PLAN_SCOPE)),
                Arguments.of("in-process operation", JobRecord.builder().jobId(git).execution(Execution.IN_PROCESS)
                        .scope(new Scope(Scope.Type.WORKSPACE, null)).budgetKey("git:fetch").budgetSeconds(30)
                        .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                        .status(Status.ENDED).fate(Fate.SUCCEEDED).outcomeRow(11)
                        .confinement(Confinement.NOT_APPLICABLE).createdAt(CREATED_AT).endedAt(ENDED_AT)
                        .clampedValues(List.of()).build()),
                Arguments.of("worker", workerAhead(worker, new Scope(Scope.Type.PROJECT, null),
                        "{ \"role\" : \"implementer\",\"state\":\"waiting\" }")),
                Arguments.of("probe of an entry", ended(probe, new Scope(Scope.Type.ENTRY, "ent-00000011"),
                        Fate.SUCCEEDED, 7)),
                Arguments.of("build with gate facts", ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7)
                        .withGateFacts("{\"gate_coverage\":  \"COMPLETE\", \"degraded\":[ ]}")),
                Arguments.of("project script with contradiction and clamped values",
                        ended(script, new Scope(Scope.Type.EPIC, "epic-one"), Fate.FAILED, 6)
                                .withBudgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.OPERATOR, null))
                                .withSelfReportContradiction(new SelfReportContradiction("passed",
                                        Generators.longs(1, 10).next(), "failed"))
                                .withClampedValues(List.of(
                                        new ClampedValue("build.timeout", "5", new BigDecimal("30"),
                                                ClampedValue.Source.MACHINE_CONFIG),
                                        new ClampedValue("jobs.grace", "\"soon\"", new BigDecimal("5.5"),
                                                ClampedValue.Source.INVALID),
                                        new ClampedValue("jobs.cap", null, new BigDecimal("1"),
                                                ClampedValue.Source.UNREADABLE)))));
    }

    private static byte[] bytes(String document) {
        return document.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(JobRecord job) {
        return new String(JobRecordCodec.write(job), StandardCharsets.UTF_8);
    }

    private static StoreRefusal refusalOf(String document) {
        var content = bytes(document);
        return assertThrows(StoreRefusal.class, () -> JobRecordCodec.read(FILE, content),
                "the document is refused by the job record codec");
    }

    /** Replaces one fragment of a document, failing when the fragment is not there. */
    private static String replace(String document, String fragment, String replacement) {
        assertTrue(document.contains(fragment), "the fixture must contain " + fragment);
        return document.replace(fragment, replacement);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.JobRecordCodecTest#records")
        @DisplayName("a record is read back equal and written again byte for byte")
        void roundTrip(String name, JobRecord job) {
            var document = JobRecordCodec.write(job);

            var read = JobRecordCodec.read(FILE, document);

            assertAll("a record round-trips through the codec",
                    () -> assertEquals(job, read, "the record is read back equal to the written one"),
                    () -> assertArrayEquals(document, JobRecordCodec.write(read),
                            "the rewritten record is the document byte for byte"));
        }

        @Test
        @DisplayName("the members are written in the order of the schema")
        void memberOrder() {
            var document = text(ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7));

            var positions = JobRecordCodec.MEMBERS.stream().map(member -> document.indexOf("\"" + member + "\":"))
                    .toList();

            assertAll("the member positions follow the schema order",
                    () -> assertTrue(positions.stream().allMatch(position -> position >= 0), positions.toString()),
                    () -> assertEquals(positions.stream().sorted().toList(), positions,
                            "the members appear in the schema's order"));
        }

        @Test
        @DisplayName("worker and gate_facts are copied verbatim, whitespace included")
        void remainderVerbatim() {
            var workerText = "{ \"role\" : \"implementer\",\n  \"served_tasks\":[ ] }";
            var gateText = "{\"gate_coverage\" :\"PARTIAL\" ,\"degraded\":[{\"dimension\":\"tests\"}]}";
            var worker = workerAhead(new JobId("l-00000000000000ff"), new Scope(Scope.Type.MACHINE, null),
                    workerText);
            var build = ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7).withGateFacts(gateText);

            var readWorker = JobRecordCodec.read(FILE, JobRecordCodec.write(worker));
            var readBuild = JobRecordCodec.read(FILE, JobRecordCodec.write(build));

            assertAll("worker and gate facts are copied verbatim",
                    () -> assertEquals(workerText, readWorker.worker(),
                            "the worker block is copied without any change"),
                    () -> assertTrue(text(readWorker).contains("\"worker\":" + workerText),
                            "the worker block is written as the original text"),
                    () -> assertEquals(gateText, readBuild.gateFacts(),
                            "the gate facts are copied without any change"),
                    () -> assertTrue(text(readBuild).contains("\"gate_facts\":" + gateText),
                            "the gate facts are written as the original text"));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        private final String running = text(writeAhead(BUILD_ID, PLAN_SCOPE));
        private final String settled = text(ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7));

        static Stream<Arguments> malformed() {
            var running = text(writeAhead(BUILD_ID, PLAN_SCOPE));
            var settled = text(ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7));
            return Stream.of(
                    Arguments.of("an unknown member", replace(running, "\"job_id\"", "\"extra\":1,\"job_id\"")),
                    Arguments.of("a missing member", replace(running, "\"signal\":null,", "")),
                    Arguments.of("a member twice", replace(running, "\"signal\":null,",
                            "\"signal\":null,\"signal\":null,")),
                    Arguments.of("an undeclared status", replace(running, "\"status\":\"running\"",
                            "\"status\":\"waiting\"")),
                    Arguments.of("an undeclared confinement", replace(running, "\"confinement\":\"unavailable\"",
                            "\"confinement\":\"partial\"")),
                    Arguments.of("an undeclared scope type", replace(running, "\"type\":\"plan\"",
                            "\"type\":\"session\"")),
                    Arguments.of("a kind other than the one the id names", replace(running, "\"kind\":\"build\"",
                            "\"kind\":\"ci\"")),
                    Arguments.of("a fate while running", replace(running, "\"fate\":null",
                            "\"fate\":\"succeeded\"")),
                    Arguments.of("an outcome row while running", replace(running, "\"outcome_row\":null",
                            "\"outcome_row\":7")),
                    Arguments.of("an outcome row outside 1 to 13", replace(settled, "\"outcome_row\":7",
                            "\"outcome_row\":14")),
                    Arguments.of("a refusal reason without the fate refused", replace(settled,
                            "\"refusal_reason\":null", "\"refusal_reason\":\"argument_rejected\"")),
                    Arguments.of("a worker block on a build job", replace(running, "\"worker\":null",
                            "\"worker\":{}")),
                    Arguments.of("a worker block that is no object", replace(running, "\"worker\":null",
                            "\"worker\":[]")),
                    Arguments.of("a child wall time while running", replace(running, "\"child_wall_ms\":null",
                            "\"child_wall_ms\":0")),
                    Arguments.of("a child wall time beyond the job's wall time", replace(settled,
                            "\"child_wall_ms\":" + CHILD_WALL_MS, "\"child_wall_ms\":60001")),
                    Arguments.of("a PID without the process start", replace(settled,
                            "\"process_started_at\":\"" + PROCESS_STARTED_AT + "\"",
                            "\"process_started_at\":null")),
                    Arguments.of("an instant in another spelling", replace(settled,
                            "\"created_at\":\"2026-10-17T10:00:00Z\"",
                            "\"created_at\":\"2026-10-17T10:00:00+00:00\"")),
                    Arguments.of("trailing content", settled + "{}"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("a document outside the one shape of a job record is refused as unreadable")
        void malformed(String name, String document) {
            var refusal = refusalOf(document);

            assertAll("the refusal is unreadable for the file",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a document outside the record shape is unreadable"),
                    () -> assertEquals(FILE, refusal.file(), "the refusal names the file of the document"));
        }

        @Test
        @DisplayName("another format version is refused as newer or outdated")
        void otherFormatVersion() {
            var newer = refusalOf(replace(running, "\"format_version\":1", "\"format_version\":2"));
            var outdated = refusalOf(replace(running, "\"format_version\":1", "\"format_version\":0"));

            assertAll("the format version decides the refusal",
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome(),
                            "a newer format version is refused as newer"),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome(),
                            "an older format version is refused as outdated"));
        }

        @Test
        @DisplayName("the unchanged documents of the fixtures are read, so each refusal is caused by its change")
        void controls() {
            assertAll("both unchanged control documents are read",
                    () -> assertDoesNotThrow(() -> JobRecordCodec.read(FILE, bytes(running)),
                            "the unchanged running document is read"),
                    () -> assertDoesNotThrow(() -> JobRecordCodec.read(FILE, bytes(settled)),
                            "the unchanged settled document is read"));
        }
    }

    @Nested
    @DisplayName("rules of the record")
    class Rules {

        private final JobRecord settled = ended(BUILD_ID, PLAN_SCOPE, Fate.SUCCEEDED, 7);

        @Test
        @DisplayName("a child wall time equal to the job's wall time is admitted, one millisecond more is not")
        void childWallBound() {
            var wall = ENDED_AT.toEpochMilli() - SPAWNED_AT.toEpochMilli();

            assertAll("the child wall time is bounded by the job's",
                    () -> assertEquals(wall, settled.withChildWallMs(wall).childWallMs(),
                            "the child wall time equal to the job's is admitted"),
                    () -> assertThrows(IllegalArgumentException.class, () -> settled.withChildWallMs(wall + 1),
                            "one millisecond more than the job's is refused"));
        }

        @Test
        @DisplayName("a child wall time is null for an in-process operation, a refused job and an unsettled job")
        void childWallNullRules() {
            var inProcess = JobRecord.builder().jobId(new JobId("c-0000000000000001"))
                    .execution(Execution.IN_PROCESS).scope(PLAN_SCOPE).budgetKey("ci:status").budgetSeconds(30)
                    .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                    .status(Status.ENDED).fate(Fate.SUCCEEDED).outcomeRow(11).confinement(Confinement.NOT_APPLICABLE)
                    .createdAt(CREATED_AT).endedAt(ENDED_AT).clampedValues(List.of());

            var inProcessWithWall = inProcess.childWallMs(0L);
            var refusedJob = refused(BUILD_ID, PLAN_SCOPE);
            var runningJob = postSpawn(BUILD_ID, PLAN_SCOPE);

            assertAll("the child wall time is null where it does not apply",
                    () -> assertThrows(IllegalArgumentException.class, inProcessWithWall::build,
                            "an in-process operation has no child wall time"),
                    () -> assertThrows(IllegalArgumentException.class, () -> refusedJob.withChildWallMs(0L),
                            "a refused job has no child wall time"),
                    () -> assertThrows(IllegalArgumentException.class, () -> runningJob.withChildWallMs(0L),
                            "an unsettled job has no child wall time"),
                    () -> assertThrows(IllegalArgumentException.class, () -> settled.withSpawnedAt(null),
                            "a settled job keeps its spawn instant"));
        }

        @Test
        @DisplayName("a worker carries its block and a probe belongs to the machine or an entry")
        void kindRules() {
            var worker = new JobId("l-0000000000000001");
            var probe = new JobId("v-0000000000000001");
            var projectScope = new Scope(Scope.Type.PROJECT, null);
            var machineScope = new Scope(Scope.Type.MACHINE, null);
            var contradiction = new SelfReportContradiction("passed", null, "failed");

            assertAll("the kind decides the worker block and the scope",
                    () -> assertThrows(IllegalArgumentException.class, () -> writeAhead(worker, projectScope),
                            "a worker is no plain write-ahead job"),
                    () -> assertThrows(IllegalArgumentException.class, () -> workerAhead(worker, PLAN_SCOPE, "{}"),
                            "a worker does not belong to a plan scope"),
                    () -> assertDoesNotThrow(() -> workerAhead(worker, projectScope, "{}"),
                            "a worker belongs to a project scope"),
                    () -> assertThrows(IllegalArgumentException.class, () -> writeAhead(probe, PLAN_SCOPE),
                            "a probe is no plain write-ahead job"),
                    () -> assertDoesNotThrow(() -> writeAhead(probe, machineScope),
                            "a probe belongs to the machine scope"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> settled.withSelfReportContradiction(contradiction),
                            "a build job carries no self-report contradiction"));
        }

        @Test
        @DisplayName("a queued or refused job was never spawned, and the status refused goes with the fate refused")
        void settlementRules() {
            var queuedJob = queued(BUILD_ID, PLAN_SCOPE);
            var refusedJob = refused(BUILD_ID, PLAN_SCOPE);

            assertAll("the settlement rules hold for a job",
                    () -> assertThrows(IllegalArgumentException.class, () -> queuedJob.withSpawnedAt(SPAWNED_AT),
                            "a queued job was never spawned"),
                    () -> assertThrows(IllegalArgumentException.class, () -> refusedJob.withStatus(Status.ENDED),
                            "a refused job is never settled as ended"),
                    () -> assertThrows(IllegalArgumentException.class, () -> settled.withFate(Fate.REFUSED),
                            "an ended job is never refused"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "{\"a\":1},\"fate\":\"succeeded\",\"x\":{}",
                "{\"a\":1}{\"b\":2}",
                "{\"a\":1} trailing",
                "{\"a\":1}}",
                "{\"a\":",
                "[1]",
                "\"text\"",
                "",
                "   "})
        @DisplayName("a worker block or gate facts that is not exactly one JSON object cannot be built")
        void rawObjectMembersAreOneObject(String raw) {
            var worker = new JobId("l-0000000000000001");
            var projectScope = new Scope(Scope.Type.PROJECT, null);

            var workerRefusal = assertThrows(IllegalArgumentException.class,
                    () -> workerAhead(worker, projectScope, raw), "a worker block must be one JSON object");
            var gateFactsRefusal = assertThrows(IllegalArgumentException.class, () -> settled.withGateFacts(raw),
                    "the gate facts must be one JSON object");

            assertAll("both raw members are refused with their name",
                    () -> assertTrue(workerRefusal.getMessage().contains("'worker'"), workerRefusal.getMessage()),
                    () -> assertTrue(gateFactsRefusal.getMessage().contains("'gate_facts'"),
                            gateFactsRefusal.getMessage()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{}", "{ \"a\" : [1, {\"b\": null}, \"}\"] }", " {\"a\":\"x,y\"} "})
        @DisplayName("a worker block or gate facts that is one JSON object is admitted, whatever it holds")
        void rawObjectMembersAdmitOneObject(String raw) {
            var worker = new JobId("l-0000000000000001");
            var projectScope = new Scope(Scope.Type.PROJECT, null);

            assertAll("each admitted raw member is stored unchanged",
                    () -> assertEquals(raw, workerAhead(worker, projectScope, raw).worker(),
                            "the admitted worker block keeps the raw text"),
                    () -> assertEquals(raw, settled.withGateFacts(raw).gateFacts(),
                            "the admitted gate facts keep the raw text"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"a\",1",
                "1 2",
                "\"a\" \"b\"",
                "\"a\"x",
                "\"unterminated",
                "{}",
                "[1]",
                "true",
                "null",
                "01",
                "1.",
                "",
                "   "})
        @DisplayName("a configured value that is not exactly one JSON number or string cannot be built")
        void configuredIsOneNumberOrString(String raw) {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new ClampedValue("build.timeout", raw, BigDecimal.ONE, ClampedValue.Source.INVALID),
                    "a configured value must be one JSON number or string");

            assertTrue(refusal.getMessage().contains("'clamped_values.configured'"), refusal.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"5", "-1.5e3", "0", "\"soon\"", "\"a \\\" b\"", "\"\""})
        @DisplayName("a configured value that is one JSON number or string is admitted")
        void configuredAdmitsOneNumberOrString(String raw) {
            var value = new ClampedValue("build.timeout", raw, BigDecimal.ONE, ClampedValue.Source.MACHINE_CONFIG);

            assertEquals(raw, value.configured(), "the admitted configured value keeps the raw text");
        }

        @Test
        @DisplayName("a scope carries an id exactly for a plan, an epic and an entry")
        void scopeIds() {
            assertAll("a scope carries its id exactly by kind",
                    () -> assertThrows(IllegalArgumentException.class, () -> new Scope(Scope.Type.PLAN, null),
                            "a plan scope carries an id"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Scope(Scope.Type.MACHINE, "m-1"),
                            "a machine scope carries no id"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Scope(Scope.Type.EPIC, "../x"),
                            "an epic id must be no path"),
                    () -> assertEquals("ent-00000011", new Scope(Scope.Type.ENTRY, "ent-00000011").id(),
                            "an entry scope keeps its entry id"));
        }
    }
}
