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

import static de.planmarshall.core.store.QuestionRecordCodecTest.json;
import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * are not tolerated: a document outside the schema is refused, never read as something close to it. The plan and
 * workspace documents are golden wire documents, and the member names, the closed values, the refusal texts and the
 * grammar cases of the bound job id are contracts; the states and instants of an update, the waiver request ids, the
 * waiver ids, the rationale and the decline reason (with punctuation, a quoted word and a backslash, so the round trip
 * keeps its escapes), the device, the evidence, the comment reference and the policy counts are generated.
 */
@EnableGeneratorController
@DisplayName("Workflow status codec")
class WorkflowStatusCodecTest {

    // Fixture path: the file a refusal names.
    static final Path FILE = Path.of("/base/projects/p/plans/workflow-engine/mcp/status.json");

    static final String COUNTERS = "\"counters\":{\"finding_id\":7,\"task_id\":3}";
    static final String METADATA = "\"metadata\":{\"planning_lane\":\"deep\",\"track\":\"complex\"}";
    static final String STEPS = "\"steps\":{\"default:verify\":{\"outcome\":\"done\",\"firings\":[{\"fired_at\":"
            + "\"2026-10-12T09:00:00Z\",\"cause\":\"first\",\"loop_back_iteration\":0,\"outcome\":\"done\"}]}}";
    static final String FACTS = "\"facts\":{\"ratio\":1.50,\"note\":\"caf\\u00e9 \\\"q\\\"\",\"flag\":true,\"none\":null}";

    /** A plan status in the canonical form, with members of other components around the engine's (golden document). */
    static final String DOCUMENT = "{\"format_version\":1,\"scope_type\":\"plan\",\"scope_id\":\"workflow-engine\","
            + "\"main_sha\":\"0123456789abcdef0123456789abcdef01234567\",\"phase\":\"phase-6-finalize\","
            + "\"state\":\"finalize.merge-sync\",\"call_stack\":[{\"caller_workflow_id\":\"finalize\","
            + "\"caller_state_id\":\"finalize.settle-commit\","
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

    /** A workspace status in the canonical form, the matched control of the scope id refusals (golden document). */
    static final String WORKSPACE = "{\"format_version\":1,\"scope_type\":\"workspace\",\"scope_id\":null,"
            + "\"phase\":\"workspace\",\"state\":\"setup.entry\",\"call_stack\":[],\"loop_back_iteration\":0,"
            + "\"retry_counters\":{},\"active_jobs\":{},\"active_job\":null,\"active_tasks\":{},"
            + "\"pending_condition\":null,\"updated_at\":\"2026-10-12T10:04:00Z\"}";

    private static final int REQUEST_NUMBER = Generators.integers(1, 99_000).next();
    static final String REQUEST_ID = "WR-" + REQUEST_NUMBER;
    static final String SECOND_REQUEST_ID = "WR-" + (REQUEST_NUMBER + 1);
    private static final int WAIVER_NUMBER = Generators.integers(1, 99_000).next();
    private static final String WAIVED_ID = "W-" + WAIVER_NUMBER;
    private static final String RAISED_ID = "W-" + (WAIVER_NUMBER + 1);
    private static final String RATIONALE = text(3);
    private static final String DECLINE_REASON = text(2);
    private static final String DEVICE = WaiverRecordCodecTest.deviceId();
    private static final String EVIDENCE_URI = "pm://plan/" + lower() + "/evidence/EV-"
            + Generators.integers(1, 999).next();
    private static final String PR_COMMENT_REF = "IC_" + Generators.letterStrings(4, 12).next();
    private static final Instant REQUESTED_AT = instant();
    private static final Instant ANSWERED_AT = REQUESTED_AT.plusSeconds(Generators.integers(1, 86_400).next());

    static byte[] bytes(String document) {
        return document.getBytes(UTF_8);
    }

    static WorkflowStatus read(String document) {
        return WorkflowStatusCodec.read(FILE, bytes(document));
    }

    private static String written(WorkflowStatus status) {
        return new String(WorkflowStatusCodec.write(status), UTF_8);
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a dotted state identifier, the form of a state id */
    private static String stateId() {
        return lower() + "." + lower();
    }

    private static Instant instant() {
        return Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a document in the canonical form is written back byte for byte (golden document)")
        void byteForByte() {
            var status = read(DOCUMENT);

            assertArrayEquals(bytes(DOCUMENT), WorkflowStatusCodec.write(status),
                    "the plan document should be written back as read");
        }

        @Test
        @DisplayName("the workspace document is written back byte for byte, without the plan-only members (golden)")
        void workspaceByteForByte() {
            assertArrayEquals(bytes(WORKSPACE), WorkflowStatusCodec.write(read(WORKSPACE)),
                    "the workspace document should be written as read");
        }

        @Test
        @DisplayName("the engine-owned members are typed (the values of the golden document)")
        void typedMembers() {
            var status = read(DOCUMENT);

            var frame = status.callStack().getFirst();
            assertAll(
                    () -> assertEquals(JournalScopeType.PLAN, status.scopeType(), "the scope type should be plan"),
                    () -> assertEquals("workflow-engine", status.scopeId(), "the scope id should be typed"),
                    () -> assertEquals("phase-6-finalize", status.phase(), "the phase should be typed"),
                    () -> assertEquals("finalize.merge-sync", status.state(), "the state should be typed"),
                    () -> assertEquals("git.commit_and_push", frame.subworkflowId(),
                            "the frame subworkflow should be typed"),
                    () -> assertEquals("commit", frame.entry(), "the frame entry should be typed"),
                    () -> assertEquals(Map.of("message_mode", new BoundArg.StringArg("step_or_fallback"),
                                    "attempts", new BoundArg.IntegerArg(3),
                                    "paths", new BoundArg.StringSetArg(new LinkedHashSet<>(List.of("a.txt", "b.txt")))),
                            frame.boundArgs(), "the bound arguments should be typed"),
                    () -> assertEquals(Instant.parse("2026-10-12T10:00:00Z"), frame.pushedAt(),
                            "the push instant should be typed"),
                    () -> assertEquals(2, status.loopBackIteration(), "the loop-back iteration should be typed"),
                    () -> assertEquals(1, status.outlineLoopBacks(), "the outline loop-backs should be typed"),
                    () -> assertEquals(2, status.policyCounters().get("ci-failure").fixUsed(),
                            "the policy counter should be typed"),
                    () -> assertEquals(1, status.retryCounters().attemptsOf(RetryRule.SYNC_FAILED,
                            "finalize.merge-sync"), "the retry counter should be typed"),
                    () -> assertEquals("g-0123456789abcdef", status.activeJob().jobId(),
                            "the active job should be typed"),
                    () -> assertEquals("running", status.activeJobs().get("sync").status(),
                            "the active jobs should be typed"),
                    () -> assertEquals("role_unqualified", status.activeTasks().get("TASK-001").reason(),
                            "the active tasks should be typed"),
                    () -> assertEquals("{\"guard\":\"result.pushed\"}", status.pendingCondition().facts(),
                            "the condition facts should be kept as text"),
                    () -> assertEquals(Instant.parse("2026-10-12T10:04:00Z"), status.updatedAt(),
                            "the update instant should be typed"));
        }

        @Test
        @DisplayName("the members of other components are kept as their exact text, in schema order")
        void retainedVerbatim() {
            var status = read(DOCUMENT);

            assertEquals(List.of("main_sha", "counters", "metadata", "steps", "facts"),
                    List.copyOf(status.retained().keySet()), "the retained members should keep schema order");
            assertAll(
                    () -> assertEquals(COUNTERS, "\"counters\":" + status.retained().get("counters"),
                            "the counters should be kept as their text"),
                    () -> assertEquals(METADATA, "\"metadata\":" + status.retained().get("metadata"),
                            "the metadata should be kept as their text"),
                    () -> assertEquals(STEPS, "\"steps\":" + status.retained().get("steps"),
                            "the steps should be kept as their text"),
                    () -> assertEquals(FACTS, "\"facts\":" + status.retained().get("facts"),
                            "the facts should be kept as their text"));
        }

        @Test
        @DisplayName("an engine update leaves every member of other components untouched")
        void engineUpdateKeepsRetained() {
            var status = read(DOCUMENT);
            var state = stateId();

            var updated = written(status.withActiveJob(null).withActiveJobs(Map.of())
                    .withState(state).withUpdatedAt(instant()));

            assertAll(
                    () -> assertTrue(updated.contains(COUNTERS), "the counters should stay untouched"),
                    () -> assertTrue(updated.contains(METADATA), "the metadata should stay untouched"),
                    () -> assertTrue(updated.contains(STEPS), "the steps should stay untouched"),
                    () -> assertTrue(updated.contains(FACTS), "the facts should stay untouched"),
                    () -> assertTrue(updated.contains("\"state\":" + json(state)),
                            "the new state should be written"),
                    () -> assertTrue(updated.contains("\"active_job\":null"),
                            "the cleared active job should be null"));
        }

        @Test
        @DisplayName("retry_counters is written directly after counters")
        void retryCountersAfterCounters() {
            var state = stateId();
            var status = read(DOCUMENT).withRetryCounters(RetryCounters.empty()
                    .attempt(RetryRule.PUSH_FAILED, state));

            assertTrue(written(status).contains(COUNTERS
                            + ",\"retry_counters\":{\"push_failed\":{\"state_id\":" + json(state) + ",\"attempts\":1}},"),
                    "the retry counters should follow the counters");
        }

        @Test
        @DisplayName("an absent retry_counters reads as empty and is written as {}")
        void absentRetryCounters() {
            var document = WORKSPACE.replace("\"retry_counters\":{},", "");

            var status = read(document);

            assertAll(
                    () -> assertTrue(status.retryCounters().isEmpty(), "the absent counters should read empty"),
                    () -> assertArrayEquals(bytes(WORKSPACE), WorkflowStatusCodec.write(status),
                            "the empty counters should be written as {}"));
        }

        @Test
        @DisplayName("every retry rule round-trips by its key")
        void everyRetryRule() {
            var counters = RetryCounters.empty();
            for (var rule : RetryRule.values()) {
                counters = counters.attempt(rule, "state." + rule.wireName());
            }
            var status = read(WORKSPACE).withRetryCounters(counters);

            assertEquals(counters, read(written(status)).retryCounters(), "every retry rule should round-trip");
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
                Arguments.of("a call stack deeper than 5", DOCUMENT.replace("\"call_stack\":[", "\"call_stack\":["
                        + (frame() + ",").repeat(5)), "'call_stack' has 6 frames, at most 5 are allowed"),
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

    /** @return one call frame in the canonical form (wire shape) */
    private static String frame() {
        return "{\"caller_workflow_id\":\"w\",\"caller_state_id\":\"s\","
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
                    () -> assertDoesNotThrow(() -> read(DOCUMENT), "the plan document should be read"),
                    () -> assertDoesNotThrow(() -> read(WORKSPACE), "the workspace document should be read"),
                    () -> assertDoesNotThrow(() -> read(DOCUMENT.replace("\"call_stack\":[", "\"call_stack\":["
                            + (frame() + ",").repeat(3))), "a call stack of exactly 4 frames"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkflowStatusCodecTest#refusals")
        @DisplayName("a document outside the schema is refused as unreadable, naming the file and the member")
        void refusedAsUnreadable(String name, String document, String detail) {
            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "the document should be refused by the reader");

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal should be unreadable"),
                    () -> assertEquals(FILE, refusal.file(), "the refusal should name the file read"),
                    () -> assertTrue(refusal.getMessage().contains(detail),
                            "the refusal should carry the expected detail"));
        }

        @Test
        @DisplayName("another format version is refused as outdated or newer before any member is read")
        void otherFormatVersion() {
            var newerDocument = DOCUMENT.replace("\"format_version\":1", "\"format_version\":2");
            var outdatedDocument = DOCUMENT.replace("\"format_version\":1", "\"format_version\":0")
                    .replace("\"loop_back_iteration\":2", "\"loop_back_iteration\":\"2\"");

            var newer = assertThrows(StoreRefusal.class, () -> read(newerDocument),
                    "a newer document should be refused");
            var outdated = assertThrows(StoreRefusal.class, () -> read(outdatedDocument),
                    "an outdated document should be refused");

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome(), "the newer version should be reported"),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome(),
                            "the outdated version should be reported"));
        }

        @Test
        @DisplayName("a retained member outside the schema is not written")
        void writeRefusesForeignRetained() {
            var status = read(WORKSPACE).withRetained(Map.of("engine_cache", "{}"));

            assertThrows(IllegalArgumentException.class, () -> WorkflowStatusCodec.write(status),
                    "a foreign retained member should be refused");
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
        return "{\"request_id\":" + json(REQUEST_ID) + ",\"gate\":\"retry_budget:ci-failure\",\"policy\":"
                + "\"remediation.ci-failure\",\"rationale\":" + json(RATIONALE) + ",\"evidence_uri\":"
                + json(EVIDENCE_URI) + ",\"requested_at\":" + json(REQUESTED_AT.toString()) + ",\"outcome\":"
                + outcome + ",\"waiver_id\":" + waiverId + ",\"answered\":" + answered
                + ",\"pr_comment_ref\":" + json(PR_COMMENT_REF) + "}";
    }

    static final String ANSWERED_WEB = "{\"channel\":\"web\",\"device_id\":" + json(DEVICE) + ",\"reason\":null,"
            + "\"at\":" + json(ANSWERED_AT.toString()) + "}";
    static final String ANSWERED_CLI = "{\"channel\":\"cli\",\"device_id\":null,\"reason\":" + json(DECLINE_REASON)
            + ",\"at\":" + json(ANSWERED_AT.toString()) + "}";
    static final String PENDING = request("null", "null", "null");

    static String withRequests(String... requests) {
        return DOCUMENT.replace("\"waiver_requests\":[]", "\"waiver_requests\":[" + String.join(",", requests) + "]");
    }

    /** @return the text as the JSON string the codec writes, without escapes */
    private static String quoted(String text) {
        return "\"" + text + "\"";
    }

    static Stream<Arguments> requestsPerOutcome() {
        return Stream.of(
                Arguments.of("pending", PENDING, null),
                Arguments.of("waived", request("\"waived\"", json(WAIVED_ID), ANSWERED_WEB),
                        WaiverRequestOutcome.WAIVED),
                Arguments.of("raised", request("\"raised\"", json(RAISED_ID), ANSWERED_WEB),
                        WaiverRequestOutcome.RAISED),
                Arguments.of("declined", request("\"declined\"", "null", ANSWERED_CLI), WaiverRequestOutcome.DECLINED),
                Arguments.of("cancelled", request("\"cancelled\"", "null", "null"), WaiverRequestOutcome.CANCELLED));
    }

    static Stream<Arguments> requestRefusals() {
        var notARequestId = "W-" + REQUEST_NUMBER;
        var notAWaiverId = "WR-" + WAIVER_NUMBER;
        return Stream.of(
                Arguments.of("an unknown outcome", request("\"lapsed\"", "null", "null"),
                        "'waiver_requests.outcome' has the undeclared value 'lapsed'"),
                Arguments.of("the outcome expired, which no request has", request("\"expired\"", "null", "null"),
                        "'waiver_requests.outcome' has the undeclared value 'expired'"),
                Arguments.of("a time limit member", PENDING.replace("\"pr_comment_ref\"",
                                "\"expires_at\":" + json(instant().toString()) + ",\"pr_comment_ref\""),
                        "undeclared field 'expires_at'"),
                Arguments.of("a missing rationale", PENDING.replace("\"rationale\":" + json(RATIONALE) + ",", ""),
                        "field 'rationale' of the waiver request 'waiver_requests[0]' is missing"),
                Arguments.of("an empty rationale", PENDING.replace(json(RATIONALE), quoted("")),
                        "The rationale of a waiver request has 1 to 2000 characters, not 0"),
                Arguments.of("a rationale over 2000 characters", PENDING.replace(json(RATIONALE),
                                quoted("r".repeat(2001))),
                        "The rationale of a waiver request has 1 to 2000 characters, not 2001"),
                Arguments.of("a request id of another form", PENDING.replace(json(REQUEST_ID), json(notARequestId)),
                        "Not a waiver request id (WR- and digits): " + notARequestId),
                Arguments.of("a waiver id of another form", request("\"waived\"", json(notAWaiverId), ANSWERED_WEB),
                        "Not a waiver id (W- and digits): " + notAWaiverId),
                Arguments.of("an answer through another channel", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("\"cli\"", "\"quorum\"")),
                        "'answered.channel' has the undeclared value 'quorum'"),
                Arguments.of("an answer without its device id", request("\"declined\"", "null",
                        ANSWERED_CLI.replace("\"device_id\":null,", "")), "field 'device_id' of the answered is missing"),
                Arguments.of("an answer from a device id of another form", request("\"waived\"", json(WAIVED_ID),
                        ANSWERED_WEB.replace(DEVICE, "dev-XYZ")),
                        "Not a device id (dev- and 12 hex digits): dev-XYZ"),
                Arguments.of("a decline reason over 2000 characters", request("\"declined\"", "null",
                        ANSWERED_CLI.replace(json(DECLINE_REASON), quoted("r".repeat(2001)))),
                        "The reason of an answer has at most 2000 characters, not 2001"),
                Arguments.of("a rationale over 2000 supplementary characters", PENDING.replace(json(RATIONALE),
                                quoted(WaiverRecordCodecTest.SUPPLEMENTARY.repeat(2001))),
                        "The rationale of a waiver request has 1 to 2000 characters, not 2001"),
                Arguments.of("a decline reason over 2000 supplementary characters", request("\"declined\"", "null",
                        ANSWERED_CLI.replace(json(DECLINE_REASON),
                                quoted(WaiverRecordCodecTest.SUPPLEMENTARY.repeat(2001)))),
                        "The reason of an answer has at most 2000 characters, not 2001"),
                Arguments.of("a request that is no object", json(REQUEST_ID),
                        "'waiver_requests[0]' must be an object"));
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
                    () -> assertArrayEquals(bytes(document), WorkflowStatusCodec.write(status),
                            "the request should be written back byte for byte"),
                    () -> assertEquals(1, status.waiverRequests().size(), "the status should hold one request"),
                    () -> assertEquals(outcome, typed.outcome(), "the outcome should be typed"),
                    () -> assertEquals(REQUEST_ID, typed.requestId(), "the request id should be typed"),
                    () -> assertEquals("retry_budget:ci-failure", typed.gate(), "the gate should be typed"),
                    () -> assertEquals(REQUESTED_AT, typed.requestedAt(), "the request instant should be typed"),
                    () -> assertFalse(status.retained().containsKey("waiver_requests"),
                            "the requests should no longer be retained"));
        }

        @Test
        @DisplayName("the members of a request and of its answer are typed")
        void typedRequest() {
            var request = read(withRequests(request("\"waived\"", json(WAIVED_ID), ANSWERED_WEB))).waiverRequests()
                    .getFirst();

            assertAll(
                    () -> assertEquals("remediation.ci-failure", request.policy(), "the policy should be typed"),
                    () -> assertEquals(RATIONALE, request.rationale(), "the rationale should keep its escapes"),
                    () -> assertEquals(EVIDENCE_URI, request.evidenceUri(), "the evidence uri should be typed"),
                    () -> assertEquals(WAIVED_ID, request.waiverId(), "the waiver id should be typed"),
                    () -> assertEquals(PR_COMMENT_REF, request.prCommentRef(), "the comment ref should be typed"),
                    () -> assertEquals(WaiverRecord.Channel.WEB, request.answered().channel(),
                            "the answer channel should be typed"),
                    () -> assertEquals(DEVICE, request.answered().deviceId(), "the answer device should be typed"),
                    () -> assertNull(request.answered().reason(), "a waived answer should carry no reason"),
                    () -> assertEquals(ANSWERED_AT, request.answered().at(), "the answer instant should be typed"));
        }

        @ParameterizedTest(name = "{0} supplementary characters")
        @ValueSource(ints = {1, 1500, 2000})
        @DisplayName("a rationale and a decline reason are counted in characters, as the schema counts them (bounds)")
        void textLengthInCodePoints(int characters) {
            var text = WaiverRecordCodecTest.SUPPLEMENTARY.repeat(characters);
            var declined = request("\"declined\"", "null", ANSWERED_CLI.replace(json(DECLINE_REASON), quoted(text)))
                    .replace(json(RATIONALE), quoted(text));

            var request = read(withRequests(declined)).waiverRequests().getFirst();

            assertAll(
                    () -> assertEquals(text, request.rationale(), "the rationale should be read whole"),
                    () -> assertEquals(text, request.answered().reason(), "the decline reason should be read whole"));
        }

        @Test
        @DisplayName("several requests keep the order of the document")
        void order() {
            var second = PENDING.replace(json(REQUEST_ID), json(SECOND_REQUEST_ID));
            var document = withRequests(request("\"declined\"", "null", ANSWERED_CLI), second);

            var status = read(document);

            assertAll(
                    () -> assertEquals(List.of(REQUEST_ID, SECOND_REQUEST_ID),
                            status.waiverRequests().stream().map(WaiverRequest::requestId).toList(),
                            "the requests should keep the document order"),
                    () -> assertArrayEquals(bytes(document), WorkflowStatusCodec.write(status),
                            "the requests should be written back as read"));
        }

        @Test
        @DisplayName("a document without waiver_requests reads as no request and is written with []")
        void absentReadsEmpty() {
            var document = DOCUMENT.replace("\"waiver_requests\":[],", "");

            var status = read(document);

            assertAll(
                    () -> assertEquals(List.of(), status.waiverRequests(), "an absent member should read empty"),
                    () -> assertArrayEquals(bytes(DOCUMENT), WorkflowStatusCodec.write(status),
                            "the empty requests should be written as []"));
        }

        @Test
        @DisplayName("a request with only its required members reads its other members as null")
        void requiredOnly() {
            var minimal = "{\"request_id\":" + json(REQUEST_ID) + ",\"gate\":\"ci\",\"rationale\":" + json(RATIONALE)
                    + ",\"requested_at\":" + json(REQUESTED_AT.toString()) + "}";

            var request = read(withRequests(minimal)).waiverRequests().getFirst();

            assertAll(
                    () -> assertNull(request.policy(), "an absent policy should read as null"),
                    () -> assertNull(request.evidenceUri(), "an absent evidence should read as null"),
                    () -> assertNull(request.outcome(), "an absent outcome should read as null"),
                    () -> assertNull(request.waiverId(), "an absent waiver id should read as null"),
                    () -> assertNull(request.answered(), "an absent answer should read as null"),
                    () -> assertNull(request.prCommentRef(), "an absent comment ref should read as null"));
        }

        @Test
        @DisplayName("a second pending request is refused: at most one awaits the operator")
        void onePendingAtATime() {
            var second = PENDING.replace(json(REQUEST_ID), json(SECOND_REQUEST_ID));
            var document = withRequests(PENDING, second);
            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "two pending requests should be refused");
            var status = read(DOCUMENT);
            var pending = read(withRequests(PENDING)).waiverRequests().getFirst();
            var duplicated = List.of(pending, pending.toBuilder().requestId(SECOND_REQUEST_ID).build());

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal should be unreadable"),
                    () -> assertEquals(FILE, refusal.file(), "the refusal should name the file read"),
                    () -> assertTrue(refusal.getMessage().contains("waiver_requests"),
                            "the refusal should name the waiver requests"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> status.withWaiverRequests(duplicated),
                            "two pending requests should not be set"));
        }

        @Test
        @DisplayName("the same request id filed twice is refused, whatever its outcome")
        void requestIdTwice() {
            var declined = request("\"declined\"", "null", ANSWERED_CLI);
            var document = withRequests(declined, PENDING);
            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "a request id filed twice should be refused");

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal should be unreadable"),
                    () -> assertTrue(refusal.getMessage().contains(REQUEST_ID),
                            "the refusal should name the request id"));
        }

        @Test
        @DisplayName("a null component is refused, never turned into an empty list")
        void nullComponentRefused() {
            var builder = read(DOCUMENT).toBuilder().waiverRequests(null);

            var thrown = assertThrows(NullPointerException.class, builder::build,
                    "a null waiver request list should be refused");

            assertEquals("waiverRequests", thrown.getMessage(), "the refusal should name the component");
        }

        @Test
        @DisplayName("the workspace scope carries no waiver request and is written without the member")
        void planScopeOnly() {
            var workspace = read(WORKSPACE);
            var pending = read(withRequests(PENDING)).waiverRequests();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> workspace.withWaiverRequests(pending),
                            "the workspace should take no waiver request"),
                    () -> assertFalse(written(workspace).contains("waiver_requests"),
                            "the workspace should omit the member"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkflowStatusCodecTest#requestRefusals")
        @DisplayName("a request outside its schema is refused as unreadable, naming the file and the member")
        void refused(String name, String request, String detail) {
            var document = withRequests(request);

            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "the request should be refused by the reader");

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal should be unreadable"),
                    () -> assertEquals(FILE, refusal.file(), "the refusal should name the file read"),
                    () -> assertTrue(refusal.getMessage().contains(detail),
                            "the refusal should carry the expected detail"));
        }
    }

    @Nested
    @DisplayName("the policy classes")
    class PolicyClasses {

        private static final Set<String> SIX_CLASSES = Set.of("ci-failure", "review-findings", "build-failure",
                "merge-conflict", "qgate-findings", "analysis");

        @Test
        @DisplayName("the closed set holds exactly the six remediation policy classes (the closed set is the contract)")
        void exactlySix() {
            assertEquals(SIX_CLASSES, WorkflowStatus.POLICY_CLASSES, "the policy classes should be the six");
        }

        @Test
        @DisplayName("a status with counters for each of the six classes round-trips")
        void sixClassesRoundTrip() {
            var counters = new LinkedHashMap<String, WorkflowStatus.PolicyCounter>();
            SIX_CLASSES.stream().sorted().forEach(policyClass -> counters.put(policyClass,
                    new WorkflowStatus.PolicyCounter(Generators.integers(0, 20).next(),
                            Generators.integers(0, 20).next(), null, null)));
            var status = read(DOCUMENT).withPolicyCounters(counters);

            var reread = read(written(status));

            assertAll(
                    () -> assertEquals(counters, reread.policyCounters(), "the policy counters should round-trip"),
                    () -> assertEquals(written(status), written(reread),
                            "the status should be written again the same"));
        }
    }

    @Nested
    @DisplayName("the bound job id")
    class BoundJobId {

        private static final String STATE = "finalize.merge-sync";

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"g-0123456789abcdef", "finalize.merge-sync", "sync", "a.b.c", "a_b-c.d9"})
        @DisplayName("a job id or a dotted state identifier is accepted (grammar cases are the contract)")
        void accepted(String jobId) {
            assertDoesNotThrow(() -> new WorkflowStatus.ActiveJob(STATE, jobId), "the bound job id should be accepted");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"", "G-0123456789abcdef", "1sync", "Sync", "sync.", ".sync", "sync..x", "sync.1x",
                "a.-b", "sync merge"})
        @DisplayName("a value of neither shape is refused (grammar cases are the contract)")
        void refused(String jobId) {
            assertThrows(IllegalArgumentException.class, () -> new WorkflowStatus.ActiveJob(STATE, jobId),
                    "the bound job id should be refused");
        }

        @Test
        @DisplayName("every segment is at most 32 characters long (the length boundary)")
        void segmentLength() {
            var longest = "a" + "b".repeat(31);
            var tooLong = "a" + "b".repeat(32);

            assertAll(
                    () -> assertDoesNotThrow(() -> new WorkflowStatus.ActiveJob(STATE, longest + "." + longest),
                            "segments of 32 characters should be accepted"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new WorkflowStatus.ActiveJob(STATE, longest + "." + tooLong),
                            "a second segment of 33 should be refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new WorkflowStatus.ActiveJob(STATE, tooLong),
                            "a single segment of 33 should be refused"));
        }
    }
}
