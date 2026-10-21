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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;

/**
 * The status document of a scope (PM-ARCH-6, PM-WF-16, PM-WF-7, PM-SEC-3): the engine-owned members and the waiver
 * requests are read strictly and written in the one canonical form, every other member of the schema is re-emitted
 * byte for byte, and
 * {@code retry_counters} follows {@code counters}. A refusal names the file and the member, and our own past shapes
 * are not tolerated: a document outside the schema is refused, never read as something close to it.
 */
@DisplayName("Workflow status codec")
class WorkflowStatusCodecTest {

    static final Path FILE = Path.of("/base/projects/p/plans/workflow-engine/mcp/status.json");

    static final String COUNTERS = "\"counters\":{\"finding_id\":7,\"task_id\":3}";
    static final String METADATA = "\"metadata\":{\"planning_lane\":\"deep\",\"track\":\"complex\"}";
    static final String STEPS = "\"steps\":{\"default:verify\":{\"outcome\":\"done\",\"firings\":[{\"fired_at\":"
            + "\"2026-10-12T09:00:00Z\",\"cause\":\"first\",\"loop_back_iteration\":0,\"outcome\":\"done\"}]}}";
    static final String FACTS = "\"facts\":{\"ratio\":1.50,\"note\":\"caf\\u00e9 \\\"q\\\"\",\"flag\":true,\"none\":null}";

    /** A plan status in the canonical form, with members of other components around the engine's. */
    static final String DOCUMENT = "{\"format_version\":1,\"scope_type\":\"plan\",\"scope_id\":\"workflow-engine\","
            + "\"main_sha\":\"0123456789abcdef0123456789abcdef01234567\",\"phase\":\"phase-6-finalize\","
            + "\"state\":\"finalize.merge-sync\",\"call_stack\":[{\"caller_workflow_id\":\"finalize\","
            + "\"caller_state_id\":\"finalize.settle-commit\",\"caller_phase\":\"phase-6-finalize\","
            + "\"subworkflow_id\":\"git.commit_and_push\",\"entry\":\"commit\","
            + "\"bound_args\":{\"message_mode\":\"step_or_fallback\","
            + "\"attempts\":3,\"paths\":[\"a.txt\",\"b.txt\"]},\"pushed_at\":\"2026-10-12T10:00:00Z\"}],"
            + "\"loop_back_iteration\":2," + COUNTERS + ","
            + "\"retry_counters\":{\"sync_failed\":{\"state_id\":\"finalize.merge-sync\",\"attempts\":1}},"
            + "\"outline_loop_backs\":1,\"policy_counters\":{\"ci-failure\":{\"retry_used\":1,\"renewed_from\":"
            + "{\"retry_used\":0,\"fix_used\":1,\"replan_id\":\"RP-1\"},\"fix_used\":2,\"last_retry\":{\"reason\":"
            + "\"flaky\",\"fingerprints\":[\"" + "a".repeat(64) + "\"]}}},\"waiver_requests\":[]," + METADATA + ","
            + STEPS + ","
            + "\"active_jobs\":{\"sync\":{\"job_id\":\"g-0123456789abcdef\",\"kind\":\"git\",\"status\":\"running\","
            + "\"started_at\":\"2026-10-12T10:01:00Z\"}},\"active_job\":{\"state_id\":\"finalize.merge-sync\","
            + "\"job_id\":\"g-0123456789abcdef\"},\"active_tasks\":{\"TASK-001\":{\"queue_task_id\":"
            + "\"qt-0123456789abcdef\",\"role\":\"implement\",\"state\":\"ended\",\"outcome\":\"lost\",\"reason\":"
            + "\"role_unqualified\"}},\"pending_condition\":{\"kind\":\"guard_indeterminate\",\"remedy_attempts\":1,"
            + "\"integrity\":null,\"facts\":{\"guard\":\"result.pushed\"},\"raised_at\":\"2026-10-12T10:03:00Z\"},"
            + FACTS + ",\"updated_at\":\"2026-10-12T10:04:00Z\"}";

    /** A workspace status in the canonical form, the matched control of the scope id refusals. */
    static final String WORKSPACE = "{\"format_version\":1,\"scope_type\":\"workspace\",\"scope_id\":null,"
            + "\"phase\":\"workspace\",\"state\":\"setup.entry\",\"call_stack\":[],\"loop_back_iteration\":0,"
            + "\"retry_counters\":{},\"active_jobs\":{},\"active_job\":null,\"active_tasks\":{},"
            + "\"pending_condition\":null,\"updated_at\":\"2026-10-12T10:04:00Z\"}";

    static byte[] bytes(String document) {
        return document.getBytes(UTF_8);
    }

    static WorkflowStatus read(String document) {
        return WorkflowStatusCodec.read(FILE, bytes(document));
    }

    private static String written(WorkflowStatus status) {
        return new String(WorkflowStatusCodec.write(status), UTF_8);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a document in the canonical form is written back byte for byte")
        void byteForByte() {
            var status = read(DOCUMENT);

            assertArrayEquals(bytes(DOCUMENT), WorkflowStatusCodec.write(status));
        }

        @Test
        @DisplayName("the workspace document is written back byte for byte, without the plan-only members")
        void workspaceByteForByte() {
            assertArrayEquals(bytes(WORKSPACE), WorkflowStatusCodec.write(read(WORKSPACE)));
        }

        @Test
        @DisplayName("the engine-owned members are typed")
        void typedMembers() {
            var status = read(DOCUMENT);

            var frame = status.callStack().getFirst();
            assertAll(
                    () -> assertEquals(JournalScopeType.PLAN, status.scopeType()),
                    () -> assertEquals("workflow-engine", status.scopeId()),
                    () -> assertEquals("phase-6-finalize", status.phase()),
                    () -> assertEquals("finalize.merge-sync", status.state()),
                    () -> assertEquals("git.commit_and_push", frame.subworkflowId()),
                    () -> assertEquals("commit", frame.entry()),
                    () -> assertEquals(Map.of("message_mode", new BoundArg.StringArg("step_or_fallback"),
                                    "attempts", new BoundArg.IntegerArg(3),
                                    "paths", new BoundArg.StringSetArg(new LinkedHashSet<>(List.of("a.txt", "b.txt")))),
                            frame.boundArgs()),
                    () -> assertEquals(Instant.parse("2026-10-12T10:00:00Z"), frame.pushedAt()),
                    () -> assertEquals(2, status.loopBackIteration()),
                    () -> assertEquals(1, status.outlineLoopBacks()),
                    () -> assertEquals(2, status.policyCounters().get("ci-failure").fixUsed()),
                    () -> assertEquals(1, status.retryCounters().attemptsOf(RetryRule.SYNC_FAILED,
                            "finalize.merge-sync")),
                    () -> assertEquals("g-0123456789abcdef", status.activeJob().jobId()),
                    () -> assertEquals("running", status.activeJobs().get("sync").status()),
                    () -> assertEquals("role_unqualified", status.activeTasks().get("TASK-001").reason()),
                    () -> assertEquals("{\"guard\":\"result.pushed\"}", status.pendingCondition().facts()),
                    () -> assertEquals(Instant.parse("2026-10-12T10:04:00Z"), status.updatedAt()));
        }

        @Test
        @DisplayName("the members of other components are kept as their exact text, in schema order")
        void retainedVerbatim() {
            var status = read(DOCUMENT);

            assertEquals(List.of("main_sha", "counters", "metadata", "steps", "facts"),
                    List.copyOf(status.retained().keySet()));
            assertAll(
                    () -> assertEquals(COUNTERS, "\"counters\":" + status.retained().get("counters")),
                    () -> assertEquals(METADATA, "\"metadata\":" + status.retained().get("metadata")),
                    () -> assertEquals(STEPS, "\"steps\":" + status.retained().get("steps")),
                    () -> assertEquals(FACTS, "\"facts\":" + status.retained().get("facts")));
        }

        @Test
        @DisplayName("an engine update leaves every member of other components untouched")
        void engineUpdateKeepsRetained() {
            var status = read(DOCUMENT);

            var updated = written(status.withActiveJob(null).withActiveJobs(Map.of())
                    .withState("finalize.merge-gate").withUpdatedAt(Instant.parse("2026-10-12T11:00:00Z")));

            assertAll(
                    () -> assertTrue(updated.contains(COUNTERS), "counters untouched"),
                    () -> assertTrue(updated.contains(METADATA), "metadata untouched"),
                    () -> assertTrue(updated.contains(STEPS), "steps untouched"),
                    () -> assertTrue(updated.contains(FACTS), "facts untouched"),
                    () -> assertTrue(updated.contains("\"state\":\"finalize.merge-gate\"")),
                    () -> assertTrue(updated.contains("\"active_job\":null")));
        }

        @Test
        @DisplayName("retry_counters is written directly after counters")
        void retryCountersAfterCounters() {
            var status = read(DOCUMENT).withRetryCounters(RetryCounters.empty()
                    .attempt(RetryRule.PUSH_FAILED, "git.push-retry-wait"));

            assertTrue(written(status).contains(COUNTERS
                    + ",\"retry_counters\":{\"push_failed\":{\"state_id\":\"git.push-retry-wait\",\"attempts\":1}},"));
        }

        @Test
        @DisplayName("an absent retry_counters reads as empty and is written as {}")
        void absentRetryCounters() {
            var document = WORKSPACE.replace("\"retry_counters\":{},", "");

            var status = read(document);

            assertAll(
                    () -> assertTrue(status.retryCounters().isEmpty()),
                    () -> assertArrayEquals(bytes(WORKSPACE), WorkflowStatusCodec.write(status)));
        }

        @Test
        @DisplayName("every retry rule round-trips by its key")
        void everyRetryRule() {
            var counters = RetryCounters.empty();
            for (var rule : RetryRule.values()) {
                counters = counters.attempt(rule, "state." + rule.wireName());
            }
            var status = read(WORKSPACE).withRetryCounters(counters);

            assertEquals(counters, read(written(status)).retryCounters());
        }
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of("an unknown member", DOCUMENT.replace("\"loop_back_iteration\":2,",
                        "\"loop_back_iteration\":2,\"engine_cache\":{},"), "undeclared field 'engine_cache'"),
                Arguments.of("a member of the wrong type", DOCUMENT.replace("\"loop_back_iteration\":2",
                        "\"loop_back_iteration\":\"2\""), "'loop_back_iteration' must be an integer"),
                Arguments.of("a member twice", DOCUMENT.replace("\"loop_back_iteration\":2,",
                        "\"loop_back_iteration\":2,\"loop_back_iteration\":2,"), "field 'loop_back_iteration' twice"),
                Arguments.of("a missing required member", DOCUMENT.replace("\"state\":\"finalize.merge-sync\",", ""),
                        "field 'state' of the document is missing"),
                Arguments.of("a phase outside the schema", DOCUMENT.replace("\"phase-6-finalize\",\"state\"",
                        "\"6-finalize\",\"state\""), "'phase' has the undeclared value '6-finalize'"),
                Arguments.of("a call stack deeper than 4", DOCUMENT.replace("\"call_stack\":[", "\"call_stack\":["
                        + (frame() + ",").repeat(4)), "'call_stack' has 5 frames, at most 4 are allowed"),
                Arguments.of("a call frame without its entry", DOCUMENT.replace("\"entry\":\"commit\",", ""),
                        "field 'entry' of the call frame is missing"),
                Arguments.of("an entry of the wrong type", DOCUMENT.replace("\"entry\":\"commit\"", "\"entry\":7"),
                        "'entry' must be a string"),
                Arguments.of("a null entry", DOCUMENT.replace("\"entry\":\"commit\"", "\"entry\":null"),
                        "'entry' must be a string"),
                Arguments.of("a blank entry", DOCUMENT.replace("\"entry\":\"commit\"", "\"entry\":\" \""),
                        "'entry' must not be blank"),
                Arguments.of("a scope id for the workspace", WORKSPACE.replace("\"scope_id\":null",
                        "\"scope_id\":\"workspace\""), "'scope_id' must be null for the workspace scope"),
                Arguments.of("no scope id for a plan", DOCUMENT.replace("\"scope_id\":\"workflow-engine\"",
                        "\"scope_id\":null"), "'scope_id' must be set for a plan scope"),
                Arguments.of("a policy class outside the closed set", DOCUMENT.replace(
                                "\"policy_counters\":{\"ci-failure\":", "\"policy_counters\":{\"spend\":"),
                        "'policy_counters key' has the undeclared value 'spend'"),
                Arguments.of("a retry rule outside the closed set", DOCUMENT.replace("\"sync_failed\":",
                        "\"re_enter\":"), "'retry_counters' has the undeclared key 're_enter'"),
                Arguments.of("negative attempts", DOCUMENT.replace("\"attempts\":1}", "\"attempts\":-1}"),
                        "'attempts' of a retry counter must be at least 0: -1"),
                Arguments.of("a retry counter without state", DOCUMENT.replace(
                                "\"state_id\":\"finalize.merge-sync\",\"attempts\":1", "\"attempts\":1"),
                        "field 'state_id' of the retry counter 'sync_failed' is missing"),
                Arguments.of("a binding of another state", DOCUMENT.replace(
                        "\"active_job\":{\"state_id\":\"finalize.merge-sync\"",
                        "\"active_job\":{\"state_id\":\"finalize.merge-gate\""), "'active_job' is bound to"),
                Arguments.of("plan-only members in the workspace", WORKSPACE.replace("\"retry_counters\":{},",
                        "\"retry_counters\":{},\"outline_loop_backs\":1,"), "kept for plan scopes only"),
                Arguments.of("an integrity without its breach", DOCUMENT.replace("\"integrity\":null",
                                "\"integrity\":{\"kind\":\"store_tampered\",\"store\":\"mcp/status.json\","
                                        + "\"recorded_digest\":\"x\",\"current_digest\":null}"),
                        "'pending_condition.integrity' is required for integrity_breach and only for it"),
                Arguments.of("the lesson store as scope", WORKSPACE.replace("\"scope_type\":\"workspace\"",
                        "\"scope_type\":\"lessons\""), "'scope_type' has the undeclared value 'lessons'"));
    }

    private static String frame() {
        return "{\"caller_workflow_id\":\"w\",\"caller_state_id\":\"s\",\"caller_phase\":\"phase-6-finalize\","
                + "\"subworkflow_id\":\"x\",\"entry\":\"main\",\"bound_args\":{},"
                + "\"pushed_at\":\"2026-10-12T10:00:00Z\"}";
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("matched control: the canonical documents are read")
        void controlsAreRead() {
            assertAll(
                    () -> assertDoesNotThrow(() -> read(DOCUMENT)),
                    () -> assertDoesNotThrow(() -> read(WORKSPACE)),
                    () -> assertDoesNotThrow(() -> read(DOCUMENT.replace("\"call_stack\":[", "\"call_stack\":["
                            + (frame() + ",").repeat(3))), "a call stack of exactly 4 frames"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkflowStatusCodecTest#refusals")
        @DisplayName("a document outside the schema is refused as unreadable, naming the file and the member")
        void refusedAsUnreadable(String name, String document, String detail) {
            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains(detail), refusal.getMessage()));
        }

        @Test
        @DisplayName("another format version is refused as outdated or newer before any member is read")
        void otherFormatVersion() {
            var newerDocument = DOCUMENT.replace("\"format_version\":1", "\"format_version\":2");
            var outdatedDocument = DOCUMENT.replace("\"format_version\":1", "\"format_version\":0")
                    .replace("\"loop_back_iteration\":2", "\"loop_back_iteration\":\"2\"");

            var newer = assertThrows(StoreRefusal.class, () -> read(newerDocument));
            var outdated = assertThrows(StoreRefusal.class, () -> read(outdatedDocument));

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome()),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome()));
        }

        @Test
        @DisplayName("a retained member outside the schema is not written")
        void writeRefusesForeignRetained() {
            var status = read(WORKSPACE).withRetained(Map.of("engine_cache", "{}"));

            assertThrows(IllegalArgumentException.class, () -> WorkflowStatusCodec.write(status));
        }
    }

    /**
     * One waiver request in the canonical form.
     *
     * @param outcome  the outcome as JSON text, {@code null} for a pending request
     * @param waiverId the waiver id as JSON text
     * @param answered the answered act as JSON text
     */
    static String request(String outcome, String waiverId, String answered) {
        return "{\"request_id\":\"WR-1\",\"gate\":\"retry_budget:ci-failure\",\"policy\":\"remediation.ci-failure\","
                + "\"rationale\":\"the check fails on the runner only\",\"evidence_uri\":"
                + "\"pm://plan/workflow-engine/evidence/EV-3\",\"requested_at\":\"2026-10-12T09:30:00Z\",\"outcome\":"
                + outcome + ",\"waiver_id\":" + waiverId + ",\"answered\":" + answered
                + ",\"pr_comment_ref\":\"IC_kwDO12\"}";
    }

    static final String ANSWERED_WEB = "{\"channel\":\"web\",\"device_id\":\"dev-0123456789ab\",\"reason\":null,"
            + "\"at\":\"2026-10-12T09:40:00Z\"}";
    static final String ANSWERED_CLI = "{\"channel\":\"cli\",\"device_id\":null,\"reason\":\"not now\","
            + "\"at\":\"2026-10-12T09:40:00Z\"}";
    static final String PENDING = request("null", "null", "null");

    static String withRequests(String... requests) {
        return DOCUMENT.replace("\"waiver_requests\":[]", "\"waiver_requests\":[" + String.join(",", requests) + "]");
    }

    static Stream<Arguments> requestsPerOutcome() {
        return Stream.of(
                Arguments.of("pending", PENDING, null),
                Arguments.of("waived", request("\"waived\"", "\"W-2\"", ANSWERED_WEB), WaiverRequestOutcome.WAIVED),
                Arguments.of("raised", request("\"raised\"", "\"W-3\"", ANSWERED_WEB), WaiverRequestOutcome.RAISED),
                Arguments.of("declined", request("\"declined\"", "null", ANSWERED_CLI), WaiverRequestOutcome.DECLINED),
                Arguments.of("cancelled", request("\"cancelled\"", "null", "null"), WaiverRequestOutcome.CANCELLED));
    }

    static Stream<Arguments> requestRefusals() {
        return Stream.of(
                Arguments.of("an unknown outcome", request("\"lapsed\"", "null", "null"),
                        "'waiver_requests.outcome' has the undeclared value 'lapsed'"),
                Arguments.of("the outcome expired, which no request has", request("\"expired\"", "null", "null"),
                        "'waiver_requests.outcome' has the undeclared value 'expired'"),
                Arguments.of("a time limit member", PENDING.replace("\"pr_comment_ref\"",
                        "\"expires_at\":\"2026-10-13T09:30:00Z\",\"pr_comment_ref\""), "undeclared field 'expires_at'"),
                Arguments.of("a missing rationale", PENDING.replace(
                                "\"rationale\":\"the check fails on the runner only\",", ""),
                        "field 'rationale' of the waiver request 'waiver_requests[0]' is missing"),
                Arguments.of("an empty rationale", PENDING.replace("the check fails on the runner only", ""),
                        "The rationale of a waiver request has 1 to 2000 characters, not 0"),
                Arguments.of("a rationale over 2000 characters", PENDING.replace("the check fails on the runner only",
                        "r".repeat(2001)), "The rationale of a waiver request has 1 to 2000 characters, not 2001"),
                Arguments.of("a request id of another form", PENDING.replace("\"WR-1\"", "\"W-1\""),
                        "Not a waiver request id (WR- and digits): W-1"),
                Arguments.of("a waiver id of another form", request("\"waived\"", "\"WR-2\"", ANSWERED_WEB),
                        "Not a waiver id (W- and digits): WR-2"),
                Arguments.of("an answer through another channel", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("\"cli\"", "\"quorum\"")),
                        "'answered.channel' has the undeclared value 'quorum'"),
                Arguments.of("an answer without its device id", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("\"device_id\":null,", "")), "field 'device_id' of the answered is missing"),
                Arguments.of("an answer from a device id of another form", request("\"waived\"", "\"W-2\"",
                        ANSWERED_WEB.replace("dev-0123456789ab", "dev-XYZ")),
                        "Not a device id (dev- and 12 hex digits): dev-XYZ"),
                Arguments.of("a decline reason over 2000 characters", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("not now", "r".repeat(2001))),
                        "The reason of an answer has at most 2000 characters, not 2001"),
                Arguments.of("a rationale over 2000 supplementary characters", PENDING.replace(
                                "the check fails on the runner only", WaiverRecordCodecTest.SUPPLEMENTARY.repeat(2001)),
                        "The rationale of a waiver request has 1 to 2000 characters, not 2001"),
                Arguments.of("a decline reason over 2000 supplementary characters", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("not now", WaiverRecordCodecTest.SUPPLEMENTARY.repeat(2001))),
                        "The reason of an answer has at most 2000 characters, not 2001"),
                Arguments.of("a request that is no object", "\"WR-1\"", "'waiver_requests[0]' must be an object"));
    }

    @Nested
    @DisplayName("the waiver requests")
    class WaiverRequests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkflowStatusCodecTest#requestsPerOutcome")
        @DisplayName("a request of each outcome, and a pending one, is typed and written back byte for byte")
        void roundTripPerOutcome(String name, String request, WaiverRequestOutcome outcome) {
            var document = withRequests(request);

            var status = read(document);

            var typed = status.waiverRequests().getFirst();
            assertAll(
                    () -> assertArrayEquals(bytes(document), WorkflowStatusCodec.write(status)),
                    () -> assertEquals(1, status.waiverRequests().size()),
                    () -> assertEquals(outcome, typed.outcome()),
                    () -> assertEquals("WR-1", typed.requestId()),
                    () -> assertEquals("retry_budget:ci-failure", typed.gate()),
                    () -> assertEquals(Instant.parse("2026-10-12T09:30:00Z"), typed.requestedAt()),
                    () -> assertFalse(status.retained().containsKey("waiver_requests"), "no longer retained"));
        }

        @Test
        @DisplayName("the members of a request and of its answer are typed")
        void typedRequest() {
            var request = read(withRequests(request("\"waived\"", "\"W-2\"", ANSWERED_WEB))).waiverRequests()
                    .getFirst();

            assertAll(
                    () -> assertEquals("remediation.ci-failure", request.policy()),
                    () -> assertEquals("the check fails on the runner only", request.rationale()),
                    () -> assertEquals("pm://plan/workflow-engine/evidence/EV-3", request.evidenceUri()),
                    () -> assertEquals("W-2", request.waiverId()),
                    () -> assertEquals("IC_kwDO12", request.prCommentRef()),
                    () -> assertEquals(WaiverRecord.Channel.WEB, request.answered().channel()),
                    () -> assertEquals("dev-0123456789ab", request.answered().deviceId()),
                    () -> assertNull(request.answered().reason()),
                    () -> assertEquals(Instant.parse("2026-10-12T09:40:00Z"), request.answered().at()));
        }

        @ParameterizedTest(name = "{0} supplementary characters")
        @ValueSource(ints = {1, 1500, 2000})
        @DisplayName("a rationale and a decline reason are counted in characters, as the schema counts them")
        void textLengthInCodePoints(int characters) {
            var text = WaiverRecordCodecTest.SUPPLEMENTARY.repeat(characters);
            var declined = request("\"declined\"", "null", ANSWERED_CLI.replace("not now", text))
                    .replace("the check fails on the runner only", text);

            var request = read(withRequests(declined)).waiverRequests().getFirst();

            assertAll(
                    () -> assertEquals(text, request.rationale()),
                    () -> assertEquals(text, request.answered().reason()));
        }

        @Test
        @DisplayName("several requests keep the order of the document")
        void order() {
            var second = PENDING.replace("\"WR-1\"", "\"WR-2\"");
            var document = withRequests(request("\"declined\"", "null", ANSWERED_CLI), second);

            var status = read(document);

            assertAll(
                    () -> assertEquals(List.of("WR-1", "WR-2"),
                            status.waiverRequests().stream().map(WaiverRequest::requestId).toList()),
                    () -> assertArrayEquals(bytes(document), WorkflowStatusCodec.write(status)));
        }

        @Test
        @DisplayName("a document without waiver_requests reads as no request and is written with []")
        void absentReadsEmpty() {
            var document = DOCUMENT.replace("\"waiver_requests\":[],", "");

            var status = read(document);

            assertAll(
                    () -> assertEquals(List.of(), status.waiverRequests()),
                    () -> assertArrayEquals(bytes(DOCUMENT), WorkflowStatusCodec.write(status)));
        }

        @Test
        @DisplayName("a request with only its required members reads its other members as null")
        void requiredOnly() {
            var minimal = "{\"request_id\":\"WR-1\",\"gate\":\"ci\",\"rationale\":\"r\","
                    + "\"requested_at\":\"2026-10-12T09:30:00Z\"}";

            var request = read(withRequests(minimal)).waiverRequests().getFirst();

            assertAll(
                    () -> assertNull(request.policy()),
                    () -> assertNull(request.evidenceUri()),
                    () -> assertNull(request.outcome()),
                    () -> assertNull(request.waiverId()),
                    () -> assertNull(request.answered()),
                    () -> assertNull(request.prCommentRef()));
        }

        @Test
        @DisplayName("a second pending request is refused: at most one awaits the operator")
        void onePendingAtATime() {
            var second = PENDING.replace("\"WR-1\"", "\"WR-2\"");
            var refusal = assertThrows(StoreRefusal.class, () -> read(withRequests(PENDING, second)));
            var status = read(DOCUMENT);
            var pending = read(withRequests(PENDING)).waiverRequests().getFirst();

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains("waiver_requests"), refusal.getMessage()),
                    () -> assertThrows(IllegalArgumentException.class, () -> status.withWaiverRequests(
                            List.of(pending, pending.toBuilder().requestId("WR-2").build()))));
        }

        @Test
        @DisplayName("the same request id filed twice is refused, whatever its outcome")
        void requestIdTwice() {
            var declined = request("\"declined\"", "null", ANSWERED_CLI);
            var refusal = assertThrows(StoreRefusal.class, () -> read(withRequests(declined, PENDING)));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertTrue(refusal.getMessage().contains("WR-1"), refusal.getMessage()));
        }

        @Test
        @DisplayName("a null component is refused, never turned into an empty list")
        void nullComponentRefused() {
            var builder = read(DOCUMENT).toBuilder().waiverRequests(null);

            var thrown = assertThrows(NullPointerException.class, builder::build);

            assertEquals("waiverRequests", thrown.getMessage());
        }

        @Test
        @DisplayName("the workspace scope carries no waiver request and is written without the member")
        void planScopeOnly() {
            var workspace = read(WORKSPACE);
            var pending = read(withRequests(PENDING)).waiverRequests();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> workspace.withWaiverRequests(pending)),
                    () -> assertFalse(written(workspace).contains("waiver_requests")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkflowStatusCodecTest#requestRefusals")
        @DisplayName("a request outside its schema is refused as unreadable, naming the file and the member")
        void refused(String name, String request, String detail) {
            var document = withRequests(request);

            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains(detail), refusal.getMessage()));
        }
    }

    @Nested
    @DisplayName("the policy classes")
    class PolicyClasses {

        private static final Set<String> SIX_CLASSES = Set.of("ci-failure", "review-findings", "build-failure",
                "merge-conflict", "qgate-findings", "analysis");

        @Test
        @DisplayName("the closed set holds exactly the six remediation policy classes")
        void exactlySix() {
            assertEquals(SIX_CLASSES, WorkflowStatus.POLICY_CLASSES);
        }

        @Test
        @DisplayName("a status with counters for each of the six classes round-trips")
        void sixClassesRoundTrip() {
            var counters = new LinkedHashMap<String, WorkflowStatus.PolicyCounter>();
            SIX_CLASSES.stream().sorted().forEach(
                    policyClass -> counters.put(policyClass, new WorkflowStatus.PolicyCounter(1, 2, null, null)));
            var status = read(DOCUMENT).withPolicyCounters(counters);

            var reread = read(written(status));

            assertAll(
                    () -> assertEquals(counters, reread.policyCounters()),
                    () -> assertEquals(written(status), written(reread)));
        }
    }

    @Nested
    @DisplayName("the bound job id")
    class BoundJobId {

        private static final String STATE = "finalize.merge-sync";

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"g-0123456789abcdef", "finalize.merge-sync", "sync", "a.b.c", "a_b-c.d9"})
        @DisplayName("a job id or a dotted state identifier is accepted")
        void accepted(String jobId) {
            assertDoesNotThrow(() -> new WorkflowStatus.ActiveJob(STATE, jobId));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"", "G-0123456789abcdef", "1sync", "Sync", "sync.", ".sync", "sync..x", "sync.1x",
                "a.-b", "sync merge"})
        @DisplayName("a value of neither shape is refused")
        void refused(String jobId) {
            assertThrows(IllegalArgumentException.class, () -> new WorkflowStatus.ActiveJob(STATE, jobId));
        }

        @Test
        @DisplayName("every segment is at most 32 characters long")
        void segmentLength() {
            var longest = "a" + "b".repeat(31);
            var tooLong = "a" + "b".repeat(32);

            assertAll(
                    () -> assertDoesNotThrow(() -> new WorkflowStatus.ActiveJob(STATE, longest + "." + longest)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new WorkflowStatus.ActiveJob(STATE, longest + "." + tooLong)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new WorkflowStatus.ActiveJob(STATE, tooLong)));
        }
    }
}
