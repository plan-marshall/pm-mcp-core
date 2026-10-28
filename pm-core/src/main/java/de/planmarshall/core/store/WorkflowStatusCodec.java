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

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.WaiverRecord.Channel;
import de.planmarshall.core.store.WorkflowStatus.ActiveJob;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.core.store.WorkflowStatus.CallFrame;
import de.planmarshall.core.store.WorkflowStatus.Integrity;
import de.planmarshall.core.store.WorkflowStatus.JobEntry;
import de.planmarshall.core.store.WorkflowStatus.LastRetry;
import de.planmarshall.core.store.WorkflowStatus.PendingCondition;
import de.planmarshall.core.store.WorkflowStatus.PolicyCounter;
import de.planmarshall.core.store.WorkflowStatus.Renewal;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;

/**
 * Reads and writes the status document {@code mcp/status.json} of a scope (PM-ARCH-6, PM-IMPL-2).
 * <p>
 * The reader checks the {@code format_version} first, then reads the typed members strictly, each with its one JSON
 * type and its closed values: the members the engine owns and the waiver requests {@code waiver_requests[]}, each by
 * the closed schema of the {@link WaiverRequest}. An absent {@code waiver_requests} is read as the empty list: no
 * request was filed. Every other member the closed schema declares is captured as the raw JSON text it was read
 * with. A member the schema does not declare, a member twice, a missing required member and a value
 * outside the schema are refused as a {@link StoreRefusal} that names the file and the member; the store is never
 * rewritten by a refusal.
 * <p>
 * The writer writes every member in the order the schema declares it: the typed members from the
 * {@link WorkflowStatus}, the retained members as the raw text they were read with, so what another component wrote
 * is re-emitted byte for byte and {@code retry_counters} follows {@code counters} directly. The typed members are
 * written in one canonical form: {@code call_stack}, {@code retry_counters}, {@code active_jobs},
 * {@code active_tasks}, {@code active_job} and {@code pending_condition} always, the last two as {@code null} when
 * unset, and {@code outline_loop_backs}, {@code policy_counters} and {@code waiver_requests} (as {@code []} when no
 * request was filed) for plan scopes only. A waiver request is written with every member, an unset one as
 * {@code null}. A document in that form round-trips byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class WorkflowStatusCodec {

    /** The format version of a status document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String SCOPE_TYPE = "scope_type";
    private static final String SCOPE_ID = "scope_id";
    private static final String PHASE = "phase";
    private static final String STATE = "state";
    private static final String CALL_STACK = "call_stack";
    private static final String LOOP_BACK_ITERATION = "loop_back_iteration";
    private static final String RETRY_COUNTERS = "retry_counters";
    private static final String OUTLINE_LOOP_BACKS = "outline_loop_backs";
    private static final String POLICY_COUNTERS = "policy_counters";
    private static final String WAIVER_REQUESTS = "waiver_requests";
    private static final String ACTIVE_JOBS = "active_jobs";
    private static final String ACTIVE_JOB = "active_job";
    private static final String ACTIVE_TASKS = "active_tasks";
    private static final String PENDING_CONDITION = "pending_condition";
    private static final String UPDATED_AT = "updated_at";

    private static final String CALLER_WORKFLOW_ID = "caller_workflow_id";
    private static final String CALLER_STATE_ID = "caller_state_id";
    private static final String CALLER_PHASE = "caller_phase";
    private static final String SUBWORKFLOW_ID = "subworkflow_id";
    private static final String ENTRY = "entry";
    private static final String BOUND_ARGS = "bound_args";
    private static final String PUSHED_AT = "pushed_at";
    private static final String STATE_ID = "state_id";
    private static final String ATTEMPTS = "attempts";
    private static final String JOB_ID = "job_id";
    private static final String KIND = "kind";
    private static final String STATUS = "status";
    private static final String STARTED_AT = "started_at";
    private static final String QUEUE_TASK_ID = "queue_task_id";
    private static final String ROLE = "role";
    private static final String OUTCOME = "outcome";
    private static final String REASON = "reason";
    private static final String REMEDY_ATTEMPTS = "remedy_attempts";
    private static final String INTEGRITY = "integrity";
    private static final String FACTS = "facts";
    private static final String RAISED_AT = "raised_at";
    private static final String STORE = "store";
    private static final String RECORDED_DIGEST = "recorded_digest";
    private static final String CURRENT_DIGEST = "current_digest";
    private static final String RETRY_USED = "retry_used";
    private static final String FIX_USED = "fix_used";
    private static final String RENEWED_FROM = "renewed_from";
    private static final String LAST_RETRY = "last_retry";
    private static final String REPLAN_ID = "replan_id";
    private static final String FINGERPRINTS = "fingerprints";
    private static final String REQUEST_ID = "request_id";
    private static final String GATE = "gate";
    private static final String POLICY = "policy";
    private static final String RATIONALE = "rationale";
    private static final String EVIDENCE_URI = "evidence_uri";
    private static final String REQUESTED_AT = "requested_at";
    private static final String WAIVER_ID = "waiver_id";
    private static final String ANSWERED = "answered";
    private static final String PR_COMMENT_REF = "pr_comment_ref";
    private static final String CHANNEL = "channel";
    private static final String DEVICE_ID = "device_id";
    private static final String AT = "at";

    /** Every member of the closed schema, in the order it declares them. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, SCOPE_TYPE, SCOPE_ID, "main_sha", "origin",
            PHASE, OUTCOME, "instance_version", "setup_run", "onboard_run", "onboard_steps", STATE, CALL_STACK,
            "worktree_path", "entry_baseline", "merge_outcome", "owed", "branch_name", "branch_pushed_at",
            LOOP_BACK_ITERATION, "counters", RETRY_COUNTERS, OUTLINE_LOOP_BACKS, POLICY_COUNTERS, WAIVER_REQUESTS,
            "gaps", "replan_pending", "replans", "follow_ups", "settle_trigger", "watch_restarts", "loop_backs",
            "remediation_cause", "refine_relaunch_count", "outline_relaunch_count", "metadata", "freshness_reconcile",
            "amendments", "steps", ACTIVE_JOBS, ACTIVE_JOB, ACTIVE_TASKS, PENDING_CONDITION, FACTS, UPDATED_AT);

    /** The members that only the status of a plan scope carries. */
    private static final Set<String> PLAN_ONLY_MEMBERS = Set.of(OUTLINE_LOOP_BACKS, POLICY_COUNTERS, WAIVER_REQUESTS);

    /** The members this codec types. */
    static final Set<String> TYPED_MEMBERS = Set.of(FIELD_FORMAT_VERSION, SCOPE_TYPE, SCOPE_ID, PHASE, STATE,
            CALL_STACK, LOOP_BACK_ITERATION, RETRY_COUNTERS, OUTLINE_LOOP_BACKS, POLICY_COUNTERS, WAIVER_REQUESTS,
            ACTIVE_JOBS, ACTIVE_JOB, ACTIVE_TASKS, PENDING_CONDITION, UPDATED_AT);

    /** The members of the schema that are kept as raw JSON text. */
    static final Set<String> RETAINED_MEMBERS = MEMBERS.stream().filter(member -> !TYPED_MEMBERS.contains(member))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * @param status the status of a scope
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if a retained member is not one of the schema
     */
    public static byte[] write(WorkflowStatus status) {
        Objects.requireNonNull(status, STATUS);
        status.retained().keySet().forEach(WorkflowStatusCodec::requireRetainedMember);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            var plan = status.scopeType() == JournalScopeType.PLAN;
            for (var member : MEMBERS) {
                if (plan || !PLAN_ONLY_MEMBERS.contains(member)) {
                    writeMember(generator, member, status);
                }
            }
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Status document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document a status document, UTF-8 encoded
     * @return the status the document holds
     * @throws StoreRefusal if the document declares another format version, a member outside the schema, or is
     *                      otherwise unreadable
     */
    public static WorkflowStatus read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, parser -> {
            try {
                return readDocument(parser, document);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    /**
     * @param member the name of a member
     * @throws IllegalArgumentException if it is no retained member of the schema
     */
    static void requireRetainedMember(String member) {
        if (!RETAINED_MEMBERS.contains(member)) {
            throw new IllegalArgumentException("'%s' is no retained member of the status document".formatted(member));
        }
    }

    private static void writeMember(JsonGenerator generator, String member, WorkflowStatus status)
            throws IOException {
        switch (member) {
            case FIELD_FORMAT_VERSION -> generator.writeNumberField(member, FORMAT_VERSION);
            case SCOPE_TYPE -> generator.writeStringField(member, status.scopeType().wireName());
            case SCOPE_ID -> writeNullableString(generator, member, status.scopeId());
            case PHASE -> generator.writeStringField(member, status.phase());
            case STATE -> generator.writeStringField(member, status.state());
            case CALL_STACK -> writeCallStack(generator, status.callStack());
            case LOOP_BACK_ITERATION -> generator.writeNumberField(member, status.loopBackIteration());
            case RETRY_COUNTERS -> writeRetryCounters(generator, status.retryCounters());
            case OUTLINE_LOOP_BACKS -> generator.writeNumberField(member, status.outlineLoopBacks());
            case POLICY_COUNTERS -> writePolicyCounters(generator, status.policyCounters());
            case WAIVER_REQUESTS -> writeWaiverRequests(generator, status.waiverRequests());
            case ACTIVE_JOBS -> writeJobs(generator, status.activeJobs());
            case ACTIVE_JOB -> writeActiveJob(generator, status.activeJob());
            case ACTIVE_TASKS -> writeTasks(generator, status.activeTasks());
            case PENDING_CONDITION -> writeCondition(generator, status.pendingCondition());
            case UPDATED_AT -> writeInstant(generator, member, status.updatedAt());
            default -> {
                var raw = status.retained().get(member);
                if (raw != null) {
                    generator.writeFieldName(member);
                    generator.writeRawValue(raw);
                }
            }
        }
    }

    /** Writes the member {@code call_stack}; the pending entry record writes its bootstrap's call stack with it. */
    static void writeCallStack(JsonGenerator generator, List<CallFrame> frames) throws IOException {
        generator.writeArrayFieldStart(CALL_STACK);
        for (var frame : frames) {
            generator.writeStartObject();
            generator.writeStringField(CALLER_WORKFLOW_ID, frame.callerWorkflowId());
            generator.writeStringField(CALLER_STATE_ID, frame.callerStateId());
            generator.writeStringField(CALLER_PHASE, frame.callerPhase());
            generator.writeStringField(SUBWORKFLOW_ID, frame.subworkflowId());
            generator.writeStringField(ENTRY, frame.entry());
            generator.writeObjectFieldStart(BOUND_ARGS);
            for (var arg : frame.boundArgs().entrySet()) {
                generator.writeFieldName(arg.getKey());
                writeBoundArg(generator, arg.getValue());
            }
            generator.writeEndObject();
            writeInstant(generator, PUSHED_AT, frame.pushedAt());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeBoundArg(JsonGenerator generator, BoundArg arg) throws IOException {
        switch (arg) {
            case BoundArg.StringArg(var value) -> generator.writeString(value);
            case BoundArg.IntegerArg(var value) -> generator.writeNumber(value);
            case BoundArg.StringSetArg(var values) -> {
                generator.writeStartArray();
                for (var value : values) {
                    generator.writeString(value);
                }
                generator.writeEndArray();
            }
        }
    }

    private static void writeRetryCounters(JsonGenerator generator, RetryCounters counters) throws IOException {
        generator.writeObjectFieldStart(RETRY_COUNTERS);
        for (var entry : counters.entries().entrySet()) {
            generator.writeObjectFieldStart(entry.getKey().wireName());
            generator.writeStringField(STATE_ID, entry.getValue().stateId());
            generator.writeNumberField(ATTEMPTS, entry.getValue().attempts());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writePolicyCounters(JsonGenerator generator, Map<String, PolicyCounter> counters)
            throws IOException {
        generator.writeObjectFieldStart(POLICY_COUNTERS);
        for (var entry : counters.entrySet()) {
            var counter = entry.getValue();
            generator.writeObjectFieldStart(entry.getKey());
            generator.writeNumberField(RETRY_USED, counter.retryUsed());
            if (counter.renewedFrom() == null) {
                generator.writeNullField(RENEWED_FROM);
            } else {
                generator.writeObjectFieldStart(RENEWED_FROM);
                generator.writeNumberField(RETRY_USED, counter.renewedFrom().retryUsed());
                generator.writeNumberField(FIX_USED, counter.renewedFrom().fixUsed());
                generator.writeStringField(REPLAN_ID, counter.renewedFrom().replanId());
                generator.writeEndObject();
            }
            generator.writeNumberField(FIX_USED, counter.fixUsed());
            if (counter.lastRetry() == null) {
                generator.writeNullField(LAST_RETRY);
            } else {
                generator.writeObjectFieldStart(LAST_RETRY);
                generator.writeStringField(REASON, counter.lastRetry().reason());
                generator.writeArrayFieldStart(FINGERPRINTS);
                for (var fingerprint : counter.lastRetry().fingerprints()) {
                    generator.writeString(fingerprint);
                }
                generator.writeEndArray();
                generator.writeEndObject();
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeWaiverRequests(JsonGenerator generator, List<WaiverRequest> requests)
            throws IOException {
        generator.writeArrayFieldStart(WAIVER_REQUESTS);
        for (var request : requests) {
            generator.writeStartObject();
            generator.writeStringField(REQUEST_ID, request.requestId());
            generator.writeStringField(GATE, request.gate());
            writeNullableString(generator, POLICY, request.policy());
            generator.writeStringField(RATIONALE, request.rationale());
            writeNullableString(generator, EVIDENCE_URI, request.evidenceUri());
            writeInstant(generator, REQUESTED_AT, request.requestedAt());
            writeNullableString(generator, OUTCOME, request.outcome() == null ? null : request.outcome().wireName());
            writeNullableString(generator, WAIVER_ID, request.waiverId());
            var answered = request.answered();
            if (answered == null) {
                generator.writeNullField(ANSWERED);
            } else {
                generator.writeObjectFieldStart(ANSWERED);
                generator.writeStringField(CHANNEL, answered.channel().wireName());
                writeNullableString(generator, DEVICE_ID, answered.deviceId());
                writeNullableString(generator, REASON, answered.reason());
                writeInstant(generator, AT, answered.at());
                generator.writeEndObject();
            }
            writeNullableString(generator, PR_COMMENT_REF, request.prCommentRef());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeJobs(JsonGenerator generator, Map<String, JobEntry> jobs) throws IOException {
        generator.writeObjectFieldStart(ACTIVE_JOBS);
        for (var entry : jobs.entrySet()) {
            var job = entry.getValue();
            generator.writeObjectFieldStart(entry.getKey());
            generator.writeStringField(JOB_ID, job.jobId());
            generator.writeStringField(KIND, job.kind());
            generator.writeStringField(STATUS, job.status());
            writeInstant(generator, STARTED_AT, job.startedAt());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeActiveJob(JsonGenerator generator, ActiveJob binding) throws IOException {
        if (binding == null) {
            generator.writeNullField(ACTIVE_JOB);
            return;
        }
        generator.writeObjectFieldStart(ACTIVE_JOB);
        generator.writeStringField(STATE_ID, binding.stateId());
        generator.writeStringField(JOB_ID, binding.jobId());
        generator.writeEndObject();
    }

    private static void writeTasks(JsonGenerator generator, Map<String, TaskEntry> tasks) throws IOException {
        generator.writeObjectFieldStart(ACTIVE_TASKS);
        for (var entry : tasks.entrySet()) {
            var task = entry.getValue();
            generator.writeObjectFieldStart(entry.getKey());
            generator.writeStringField(QUEUE_TASK_ID, task.queueTaskId());
            generator.writeStringField(ROLE, task.role());
            generator.writeStringField(STATE, task.state());
            writeNullableString(generator, OUTCOME, task.outcome());
            writeNullableString(generator, REASON, task.reason());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    /** Writes the member {@code pending_condition}; the pending entry record writes its bootstrap's overlay with it. */
    static void writeCondition(JsonGenerator generator, PendingCondition condition) throws IOException {
        if (condition == null) {
            generator.writeNullField(PENDING_CONDITION);
            return;
        }
        generator.writeObjectFieldStart(PENDING_CONDITION);
        generator.writeStringField(KIND, condition.kind());
        generator.writeNumberField(REMEDY_ATTEMPTS, condition.remedyAttempts());
        var integrity = condition.integrity();
        if (integrity == null) {
            generator.writeNullField(INTEGRITY);
        } else {
            generator.writeObjectFieldStart(INTEGRITY);
            generator.writeStringField(KIND, integrity.kind());
            generator.writeStringField(STORE, integrity.store());
            generator.writeStringField(RECORDED_DIGEST, integrity.recordedDigest());
            writeNullableString(generator, CURRENT_DIGEST, integrity.currentDigest());
            generator.writeEndObject();
        }
        generator.writeFieldName(FACTS);
        generator.writeRawValue(condition.facts());
        writeInstant(generator, RAISED_AT, condition.raisedAt());
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static WorkflowStatus readDocument(JsonParser parser, byte[] document) throws IOException {
        var status = WorkflowStatus.builder().callStack(List.of()).policyCounters(Map.of()).waiverRequests(List.of())
                .retryCounters(RetryCounters.empty()).activeJobs(Map.of()).activeTasks(Map.of());
        var retained = new LinkedHashMap<String, String>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case SCOPE_TYPE -> status.scopeType(scopeType(string(parser, field)));
                case SCOPE_ID -> status.scopeId(nullableString(parser, field));
                case PHASE -> status.phase(string(parser, field));
                case STATE -> status.state(string(parser, field));
                case CALL_STACK -> status.callStack(StrictJson.array(parser, field, WorkflowStatusCodec::readFrame));
                case LOOP_BACK_ITERATION -> status.loopBackIteration(integer(parser, field));
                case RETRY_COUNTERS -> status.retryCounters(readRetryCounters(parser));
                case OUTLINE_LOOP_BACKS -> status.outlineLoopBacks(integer(parser, field));
                case POLICY_COUNTERS -> status.policyCounters(
                        StrictJson.map(parser, field, WorkflowStatusCodec::readPolicyCounter));
                case WAIVER_REQUESTS -> status.waiverRequests(
                        StrictJson.array(parser, field, WorkflowStatusCodec::readWaiverRequest));
                case ACTIVE_JOBS -> status.activeJobs(StrictJson.map(parser, field, WorkflowStatusCodec::readJob));
                case ACTIVE_JOB -> status.activeJob(isNull(parser) ? null : readActiveJob(parser));
                case ACTIVE_TASKS -> status.activeTasks(StrictJson.map(parser, field, WorkflowStatusCodec::readTask));
                case PENDING_CONDITION -> status.pendingCondition(
                        isNull(parser) ? null : readCondition(parser, document));
                case UPDATED_AT -> status.updatedAt(instant(parser, field));
                default -> {
                    if (!RETAINED_MEMBERS.contains(field)) {
                        throw undeclared(field);
                    }
                    retained.put(field, raw(parser, document));
                }
            }
        }
        requireFields(seen, "document", SCOPE_TYPE, SCOPE_ID, PHASE, STATE, LOOP_BACK_ITERATION, UPDATED_AT);
        return status.retained(retained).build();
    }

    /** Reads one call frame; the frames of a pending entry record's bootstrap have the same shape. */
    static CallFrame readFrame(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String callerWorkflowId = null;
        String callerStateId = null;
        String callerPhase = null;
        String subworkflowId = null;
        String entry = null;
        Map<String, BoundArg> boundArgs = null;
        Instant pushedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case CALLER_WORKFLOW_ID -> callerWorkflowId = string(parser, field);
                case CALLER_STATE_ID -> callerStateId = string(parser, field);
                case CALLER_PHASE -> callerPhase = string(parser, field);
                case SUBWORKFLOW_ID -> subworkflowId = string(parser, field);
                case ENTRY -> entry = string(parser, field);
                case BOUND_ARGS -> boundArgs = StrictJson.map(parser, field, WorkflowStatusCodec::readBoundArg);
                case PUSHED_AT -> pushedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "call frame", CALLER_WORKFLOW_ID, CALLER_STATE_ID, CALLER_PHASE, SUBWORKFLOW_ID, ENTRY,
                BOUND_ARGS, PUSHED_AT);
        return new CallFrame(callerWorkflowId, callerStateId, callerPhase, subworkflowId, entry, boundArgs, pushedAt);
    }

    private static BoundArg readBoundArg(JsonParser parser, String name) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_STRING -> new BoundArg.StringArg(parser.getText());
            case VALUE_NUMBER_INT -> new BoundArg.IntegerArg(integer(parser, name));
            case START_ARRAY -> {
                var values = new LinkedHashSet<String>();
                for (var value : StrictJson.stringList(parser, name)) {
                    if (!values.add(value)) {
                        throw unreadable("bound argument '%s' holds '%s' twice".formatted(name, value));
                    }
                }
                yield new BoundArg.StringSetArg(values);
            }
            case null, default -> throw unreadable(
                    "bound argument '%s' must be a string, an integer or an array of strings".formatted(name));
        };
    }

    private static RetryCounters readRetryCounters(JsonParser parser) throws IOException {
        requireObject(parser, RETRY_COUNTERS);
        var entries = new EnumMap<RetryRule, RetryCounters.Entry>(RetryRule.class);
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var key = nextField(parser, seen);
            var rule = RetryRule.ofWireName(key).orElseThrow(
                    () -> unreadable("'retry_counters' has the undeclared key '%s'".formatted(key)));
            entries.put(rule, readRetryEntry(parser, key));
        }
        return new RetryCounters(entries);
    }

    private static RetryCounters.Entry readRetryEntry(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String stateId = null;
        long attempts = 0;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case STATE_ID -> stateId = string(parser, field);
                case ATTEMPTS -> attempts = integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "retry counter '%s'".formatted(name), STATE_ID, ATTEMPTS);
        return new RetryCounters.Entry(stateId, attempts);
    }

    private static PolicyCounter readPolicyCounter(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        long retryUsed = 0;
        long fixUsed = 0;
        Renewal renewedFrom = null;
        LastRetry lastRetry = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case RETRY_USED -> retryUsed = integer(parser, field);
                case FIX_USED -> fixUsed = integer(parser, field);
                case RENEWED_FROM -> renewedFrom = isNull(parser) ? null : readRenewal(parser);
                case LAST_RETRY -> lastRetry = isNull(parser) ? null : readLastRetry(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "policy counter '%s'".formatted(name), RETRY_USED, FIX_USED);
        return new PolicyCounter(retryUsed, fixUsed, renewedFrom, lastRetry);
    }

    private static Renewal readRenewal(JsonParser parser) throws IOException {
        requireObject(parser, RENEWED_FROM);
        long retryUsed = 0;
        long fixUsed = 0;
        String replanId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case RETRY_USED -> retryUsed = integer(parser, field);
                case FIX_USED -> fixUsed = integer(parser, field);
                case REPLAN_ID -> replanId = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RENEWED_FROM, RETRY_USED, FIX_USED, REPLAN_ID);
        return new Renewal(retryUsed, fixUsed, replanId);
    }

    private static LastRetry readLastRetry(JsonParser parser) throws IOException {
        requireObject(parser, LAST_RETRY);
        String reason = null;
        List<String> fingerprints = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case REASON -> reason = string(parser, field);
                case FINGERPRINTS -> fingerprints = StrictJson.stringList(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, LAST_RETRY, REASON, FINGERPRINTS);
        return new LastRetry(reason, fingerprints);
    }

    private static WaiverRequest readWaiverRequest(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var request = WaiverRequest.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case REQUEST_ID -> request.requestId(string(parser, field));
                case GATE -> request.gate(string(parser, field));
                case POLICY -> request.policy(nullableString(parser, field));
                case RATIONALE -> request.rationale(string(parser, field));
                case EVIDENCE_URI -> request.evidenceUri(nullableString(parser, field));
                case REQUESTED_AT -> request.requestedAt(instant(parser, field));
                case OUTCOME -> request.outcome(waiverRequestOutcome(nullableString(parser, field)));
                case WAIVER_ID -> request.waiverId(nullableString(parser, field));
                case ANSWERED -> request.answered(isNull(parser) ? null : readAnswered(parser));
                case PR_COMMENT_REF -> request.prCommentRef(nullableString(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "waiver request '%s'".formatted(name), REQUEST_ID, GATE, RATIONALE, REQUESTED_AT);
        return request.build();
    }

    private static WaiverRequestOutcome waiverRequestOutcome(String wireName) {
        return wireName == null ? null
                : WaiverRecordCodec.enumValue(wireName, WaiverRequestOutcome::fromWireName, "waiver_requests.outcome");
    }

    private static WaiverRequest.Answered readAnswered(JsonParser parser) throws IOException {
        requireObject(parser, ANSWERED);
        Channel channel = null;
        String deviceId = null;
        String reason = null;
        Instant at = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case CHANNEL -> channel = WaiverRecordCodec.enumValue(string(parser, field), Channel::fromWireName,
                        "answered.channel");
                case DEVICE_ID -> deviceId = nullableString(parser, field);
                case REASON -> reason = nullableString(parser, field);
                case AT -> at = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, ANSWERED, CHANNEL, DEVICE_ID, AT);
        return new WaiverRequest.Answered(channel, deviceId, reason, at);
    }

    private static JobEntry readJob(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String jobId = null;
        String kind = null;
        String status = null;
        Instant startedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case JOB_ID -> jobId = string(parser, field);
                case KIND -> kind = string(parser, field);
                case STATUS -> status = string(parser, field);
                case STARTED_AT -> startedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "active job '%s'".formatted(name), JOB_ID, KIND, STATUS, STARTED_AT);
        return new JobEntry(jobId, kind, status, startedAt);
    }

    private static ActiveJob readActiveJob(JsonParser parser) throws IOException {
        requireObject(parser, ACTIVE_JOB);
        String stateId = null;
        String jobId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case STATE_ID -> stateId = string(parser, field);
                case JOB_ID -> jobId = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, ACTIVE_JOB, STATE_ID, JOB_ID);
        return new ActiveJob(stateId, jobId);
    }

    private static TaskEntry readTask(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String queueTaskId = null;
        String role = null;
        String state = null;
        String outcome = null;
        String reason = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case QUEUE_TASK_ID -> queueTaskId = string(parser, field);
                case ROLE -> role = string(parser, field);
                case STATE -> state = string(parser, field);
                case OUTCOME -> outcome = nullableString(parser, field);
                case REASON -> reason = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "active task '%s'".formatted(name), QUEUE_TASK_ID, ROLE, STATE, OUTCOME);
        return new TaskEntry(queueTaskId, role, state, outcome, reason);
    }

    /** Reads a condition overlay; a pending entry record's bootstrap carries one of the same shape. */
    static PendingCondition readCondition(JsonParser parser, byte[] document) throws IOException {
        requireObject(parser, PENDING_CONDITION);
        String kind = null;
        long remedyAttempts = 0;
        Integrity integrity = null;
        String facts = null;
        Instant raisedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case KIND -> kind = string(parser, field);
                case REMEDY_ATTEMPTS -> remedyAttempts = integer(parser, field);
                case INTEGRITY -> integrity = isNull(parser) ? null : readIntegrity(parser);
                case FACTS -> {
                    requireObject(parser, field);
                    facts = raw(parser, document);
                }
                case RAISED_AT -> raisedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, PENDING_CONDITION, KIND, FACTS, RAISED_AT);
        return new PendingCondition(kind, remedyAttempts, integrity, facts, raisedAt);
    }

    private static Integrity readIntegrity(JsonParser parser) throws IOException {
        requireObject(parser, INTEGRITY);
        String kind = null;
        String store = null;
        String recordedDigest = null;
        String currentDigest = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case KIND -> kind = string(parser, field);
                case STORE -> store = string(parser, field);
                case RECORDED_DIGEST -> recordedDigest = string(parser, field);
                case CURRENT_DIGEST -> currentDigest = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, INTEGRITY, KIND, STORE, RECORDED_DIGEST, CURRENT_DIGEST);
        return new Integrity(kind, store, recordedDigest, currentDigest);
    }

    private static JournalScopeType scopeType(String wireName) {
        return JournalScopeType.ofWireName(wireName).filter(type -> type != JournalScopeType.LESSONS).orElseThrow(
                () -> unreadable("'scope_type' has the undeclared value '%s'".formatted(wireName)));
    }

    private static boolean isNull(JsonParser parser) {
        return parser.currentToken() == JsonToken.VALUE_NULL;
    }

    /**
     * Captures the current value as the exact text of the document: from its first byte to the byte after its
     * last token. The pending entry record keeps its raw members the same way.
     */
    static String raw(JsonParser parser, byte[] document) throws IOException {
        var start = parser.currentTokenLocation().getByteOffset();
        parser.skipChildren();
        parser.finishToken();
        var end = parser.currentLocation().getByteOffset();
        return new String(document, Math.toIntExact(start), Math.toIntExact(end - start), StandardCharsets.UTF_8)
                .strip();
    }
}
