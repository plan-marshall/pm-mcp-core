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

import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.core.store.WorkerState.DeliveredSkill;
import de.planmarshall.core.store.WorkerState.EndReason;
import de.planmarshall.core.store.WorkerState.Form;
import de.planmarshall.core.store.WorkerState.RoleClass;
import de.planmarshall.core.store.WorkerState.State;
import de.planmarshall.core.store.WorkerState.Workdir;
import de.planmarshall.core.store.WorkerState.WorktreeMark;

/**
 * Reads and writes the {@code worker} member of a job record (PM-WORK-2, PM-WORK-3, PM-IMPL-2), the block
 * {@link JobRecord} keeps as raw text: the launch contract, the job-bound connection and the protocol state of a
 * worker, typed as {@link WorkerState}.
 * <p>
 * The reader accepts exactly one shape and never tolerates another: only the declared members, each at most once,
 * every required member (the schema marks 24 of the 25 declared ones required; {@code effort_narrowed_from} is
 * optional and every other member is required, the nullable ones included), one JSON type per member, and the closed
 * values of every enumeration and of every pattern. Whatever does not parse is a {@link StoreRefusal} naming the
 * record file, never an empty state.
 * <p>
 * The writer emits the block through {@link CanonicalJson}, so one state has one text: the members in the canonical
 * order of their names, no whitespace, and every instant as {@link Instant#toString()} writes it. That text is what
 * {@link JobRecordStore#writeWorker} stores as the record's {@code worker} raw member and what
 * {@link JobRecordCodec} re-emits byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class WorkerStateCodec {

    private static final String ROLE = "role";
    private static final String CONFIGURATION_DIGEST = "configuration_digest";
    private static final String CLASS = "class";
    private static final String FORM = "form";
    private static final String HARNESS = "harness";
    private static final String HARNESS_VERSION = "harness_version";
    private static final String MODEL = "model";
    private static final String EFFORT = "effort";
    private static final String EFFORT_NARROWED_FROM = "effort_narrowed_from";
    private static final String GENERATION_ID = "generation_id";
    private static final String WORKDIR = "workdir";
    private static final String PROMPT_DIGEST = "prompt_digest";
    private static final String JOB_TOKEN_SHA256 = "job_token_sha256";
    private static final String CONNECTED_AT = "connected_at";
    private static final String STATE = "state";
    private static final String STATE_AT = "state_at";
    private static final String TASK = "task";
    private static final String SERVED_TASKS = "served_tasks";
    private static final String WORKTREE_MARK = "worktree_mark";
    private static final String IDLE_WAKEUPS = "idle_wakeups";
    private static final String CONTEXT_TOKENS = "context_tokens";
    private static final String DELIVERED_SKILLS = "delivered_skills";
    private static final String END_REASON = "end_reason";
    private static final String START_FAILURE = "start_failure";
    private static final String HARNESS_ERROR = "harness_error";

    private static final String KIND = "kind";
    private static final String PLAN_ID = "plan_id";
    private static final String HEAD = "head";
    private static final String CHANGED_PATHS = "changed_paths";
    private static final String MARKED_AT = "marked_at";
    private static final String URI = "uri";
    private static final String DIGEST = "digest";
    private static final String DELIVERED_AT = "delivered_at";
    private static final String UNMEASURED = "unmeasured";

    /** The members of the worker block, in the order of the schema; every one of them is declared. */
    static final List<String> MEMBERS = List.of(ROLE, CONFIGURATION_DIGEST, CLASS, FORM, HARNESS, HARNESS_VERSION,
            MODEL, EFFORT, EFFORT_NARROWED_FROM, GENERATION_ID, WORKDIR, PROMPT_DIGEST, JOB_TOKEN_SHA256, CONNECTED_AT,
            STATE, STATE_AT, TASK, SERVED_TASKS, WORKTREE_MARK, IDLE_WAKEUPS, CONTEXT_TOKENS, DELIVERED_SKILLS,
            END_REASON, START_FAILURE, HARNESS_ERROR);

    /**
     * The members the schema requires, in the order of the schema: {@link #MEMBERS} without the one optional member,
     * {@code effort_narrowed_from}, which the writer emits anyway so the block has one text.
     */
    static final List<String> REQUIRED_MEMBERS =
            MEMBERS.stream().filter(member -> !EFFORT_NARROWED_FROM.equals(member)).toList();

    /** The members of the working directory, in the order of the schema. */
    static final List<String> WORKDIR_MEMBERS = List.of(KIND, PLAN_ID);

    /** The members of the worktree mark, in the order of the schema. */
    static final List<String> WORKTREE_MARK_MEMBERS = List.of(HEAD, CHANGED_PATHS, MARKED_AT);

    /** The members of a delivered skill, in the order of the schema. */
    static final List<String> DELIVERED_SKILL_MEMBERS = List.of(URI, DIGEST, DELIVERED_AT);

    /**
     * @param state the protocol state of a worker
     * @return the block, in its one canonical text
     */
    public static String write(WorkerState state) {
        Objects.requireNonNull(state, STATE);
        return CanonicalJson.write(document(state));
    }

    /**
     * @param file the record file the block was read from, named by a refusal
     * @param text the {@code worker} member of a job record
     * @return the protocol state the block holds
     * @throws StoreRefusal if the block does not have the one shape of a worker block
     */
    public static WorkerState read(Path file, String text) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        try (var parser = StrictJson.factory().createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable("'worker' must be an object");
            }
            var state = readDocument(parser);
            if (parser.nextToken() != null) {
                throw unreadable("content after the 'worker' object");
            }
            return state;
        } catch (StrictJson.Malformed e) {
            throw StoreRefusal.unreadable(file, JobRecord.FORMAT_VERSION, e.getMessage(), e.getCause());
        } catch (IOException e) {
            throw StoreRefusal.unreadable(file, JobRecord.FORMAT_VERSION, String.valueOf(e.getMessage()), e);
        }
    }

    private static WorkerState readDocument(JsonParser parser) throws IOException {
        var state = WorkerState.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case ROLE -> state.role(string(parser, field));
                case CONFIGURATION_DIGEST -> state.configurationDigest(string(parser, field));
                case CLASS -> state.roleClass(closed(parser, field, RoleClass::fromWireName));
                case FORM -> state.form(closed(parser, field, Form::fromWireName));
                case HARNESS -> state.harness(string(parser, field));
                case HARNESS_VERSION -> state.harnessVersion(string(parser, field));
                case MODEL -> state.model(string(parser, field));
                case EFFORT -> state.effort(nullableString(parser, field).orElse(null));
                case EFFORT_NARROWED_FROM -> state.effortNarrowedFrom(nullableString(parser, field).orElse(null));
                case GENERATION_ID -> state.generationId(generationId(parser, field));
                case WORKDIR -> state.workdir(readWorkdir(parser));
                case PROMPT_DIGEST -> state.promptDigest(string(parser, field));
                case JOB_TOKEN_SHA256 -> state.jobTokenSha256(nullableString(parser, field).orElse(null));
                case CONNECTED_AT -> state.connectedAt(nullableInstant(parser, field).orElse(null));
                case STATE -> state.state(closed(parser, field, State::fromWireName));
                case STATE_AT -> state.stateAt(instant(parser, field));
                case TASK -> state.task(nullableString(parser, field).orElse(null));
                case SERVED_TASKS -> state.servedTasks(stringList(parser, field));
                case WORKTREE_MARK -> state.worktreeMark(isNull(parser) ? null : readWorktreeMark(parser));
                case IDLE_WAKEUPS -> state.idleWakeups(integer(parser, field));
                case CONTEXT_TOKENS -> state.contextTokens(readContextTokens(parser, field));
                case DELIVERED_SKILLS -> state.deliveredSkills(
                        StrictJson.array(parser, field, WorkerStateCodec::readDeliveredSkill));
                case END_REASON -> state.endReason(isNull(parser) ? null : closed(parser, field,
                        EndReason::fromWireName));
                case START_FAILURE -> state.startFailure(bool(parser, field));
                case HARNESS_ERROR -> state.harnessError(nullableString(parser, field).orElse(null));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "worker block", REQUIRED_MEMBERS.toArray(String[]::new));
        try {
            return state.build();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static JobId generationId(JsonParser parser, String field) throws IOException {
        var text = string(parser, field);
        try {
            return new JobId(text);
        } catch (IllegalArgumentException e) {
            throw unreadable("'%s' is not the id of a worker generation: %s".formatted(field, text), e);
        }
    }

    private static Workdir readWorkdir(JsonParser parser) throws IOException {
        requireObject(parser, WORKDIR);
        Workdir.@Nullable Kind kind = null;
        @Nullable String planId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case KIND -> kind = closed(parser, WORKDIR + "." + KIND, Workdir.Kind::fromWireName);
                case PLAN_ID -> planId = nullableString(parser, WORKDIR + "." + PLAN_ID).orElse(null);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, WORKDIR, WORKDIR_MEMBERS.toArray(String[]::new));
        try {
            return new Workdir(kind, planId);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static WorktreeMark readWorktreeMark(JsonParser parser) throws IOException {
        requireObject(parser, WORKTREE_MARK);
        @Nullable String head = null;
        @Nullable List<String> changedPaths = null;
        @Nullable Instant markedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case HEAD -> head = string(parser, WORKTREE_MARK + "." + HEAD);
                case CHANGED_PATHS -> changedPaths = stringList(parser, WORKTREE_MARK + "." + CHANGED_PATHS);
                case MARKED_AT -> markedAt = instant(parser, WORKTREE_MARK + "." + MARKED_AT);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, WORKTREE_MARK, WORKTREE_MARK_MEMBERS.toArray(String[]::new));
        try {
            return new WorktreeMark(head, changedPaths, markedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static DeliveredSkill readDeliveredSkill(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String uri = null;
        @Nullable String digest = null;
        @Nullable Instant deliveredAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case URI -> uri = string(parser, name + "." + URI);
                case DIGEST -> digest = string(parser, name + "." + DIGEST);
                case DELIVERED_AT -> deliveredAt = instant(parser, name + "." + DELIVERED_AT);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, DELIVERED_SKILL_MEMBERS.toArray(String[]::new));
        try {
            return new DeliveredSkill(uri, digest, deliveredAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static ContextTokens readContextTokens(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            var text = string(parser, field);
            if (UNMEASURED.equals(text)) {
                return ContextTokens.NOT_MEASURED;
            }
            throw unreadable("'%s' is a non-negative count or '%s': %s".formatted(field, UNMEASURED, text));
        }
        var tokens = integer(parser, field);
        if (tokens < 0) {
            throw unreadable("'%s' must not be negative: %d".formatted(field, tokens));
        }
        return ContextTokens.measured(tokens);
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    private static boolean isNull(JsonParser parser) {
        return parser.currentToken() == JsonToken.VALUE_NULL;
    }

    private static JsonNode document(WorkerState state) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(ROLE, JsonNode.string(state.role()));
        members.put(CONFIGURATION_DIGEST, JsonNode.string(state.configurationDigest()));
        members.put(CLASS, JsonNode.string(state.roleClass().wireName()));
        members.put(FORM, JsonNode.string(state.form().wireName()));
        members.put(HARNESS, JsonNode.string(state.harness()));
        members.put(HARNESS_VERSION, JsonNode.string(state.harnessVersion()));
        members.put(MODEL, JsonNode.string(state.model()));
        members.put(EFFORT, nullable(state.effort()));
        members.put(EFFORT_NARROWED_FROM, nullable(state.effortNarrowedFrom()));
        members.put(GENERATION_ID, JsonNode.string(state.generationId().value()));
        members.put(WORKDIR, workdir(state.workdir()));
        members.put(PROMPT_DIGEST, JsonNode.string(state.promptDigest()));
        members.put(JOB_TOKEN_SHA256, nullable(state.jobTokenSha256()));
        members.put(CONNECTED_AT, instantNode(state.connectedAt()));
        members.put(STATE, JsonNode.string(state.state().wireName()));
        members.put(STATE_AT, instantNode(state.stateAt()));
        members.put(TASK, nullable(state.task()));
        members.put(SERVED_TASKS, JsonNode.array(state.servedTasks().stream().map(JsonNode::string).toList()));
        members.put(WORKTREE_MARK, state.worktreeMark() == null ? JsonNode.nullValue()
                : worktreeMark(state.worktreeMark()));
        members.put(IDLE_WAKEUPS, JsonNode.integer(state.idleWakeups()));
        members.put(CONTEXT_TOKENS, state.contextTokens().unmeasured() ? JsonNode.string(UNMEASURED)
                : JsonNode.integer(state.contextTokens().tokens()));
        members.put(DELIVERED_SKILLS, JsonNode.array(
                state.deliveredSkills().stream().map(WorkerStateCodec::deliveredSkill).toList()));
        members.put(END_REASON, state.endReason() == null ? JsonNode.nullValue()
                : JsonNode.string(state.endReason().wireName()));
        members.put(START_FAILURE, JsonNode.bool(state.startFailure()));
        members.put(HARNESS_ERROR, nullable(state.harnessError()));
        return JsonNode.object(members);
    }

    private static JsonNode workdir(Workdir workdir) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(KIND, JsonNode.string(workdir.kind().wireName()));
        members.put(PLAN_ID, nullable(workdir.planId()));
        return JsonNode.object(members);
    }

    private static JsonNode worktreeMark(WorktreeMark mark) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(HEAD, JsonNode.string(mark.head()));
        members.put(CHANGED_PATHS, JsonNode.array(mark.changedPaths().stream().map(JsonNode::string).toList()));
        members.put(MARKED_AT, instantNode(mark.markedAt()));
        return JsonNode.object(members);
    }

    private static JsonNode deliveredSkill(DeliveredSkill skill) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put(URI, JsonNode.string(skill.uri()));
        members.put(DIGEST, JsonNode.string(skill.digest()));
        members.put(DELIVERED_AT, instantNode(skill.deliveredAt()));
        return JsonNode.object(members);
    }

    private static JsonNode nullable(@Nullable String value) {
        return value == null ? JsonNode.nullValue() : JsonNode.string(value);
    }

    private static JsonNode instantNode(@Nullable Instant value) {
        return value == null ? JsonNode.nullValue() : JsonNode.string(value.toString());
    }
}
