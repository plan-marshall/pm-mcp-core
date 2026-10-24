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
import static de.planmarshall.core.store.StrictJson.stringList;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ChangeLedgerRecord.Baseline;
import de.planmarshall.core.store.ChangeLedgerRecord.ChangeOrigin;
import de.planmarshall.core.store.ChangeLedgerRecord.Expect;
import de.planmarshall.core.store.ChangeLedgerRecord.ExpectResult;
import de.planmarshall.core.store.ChangeLedgerRecord.Kind;
import de.planmarshall.core.store.ChangeLedgerRecord.Outcome;
import de.planmarshall.core.store.ChangeLedgerRecord.WorktreeShaFailure;

/**
 * Reads and writes one line of a {@code change-ledger.jsonl} (Store Schemas § Change Ledger Record).
 * <p>
 * One record is one JSON object; the codec reads exactly the declared fields, each at most once, and writes every one
 * of them, {@code null} where the kind does not use it, so a line this codec wrote is read back to the same record.
 * The file format version is checked by the append-only store before the record is read.
 *
 * @since 0.1
 */
@UtilityClass
public class ChangeLedgerCodec {

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_WORKTREE_SHA = "worktree_sha";
    private static final String FIELD_WORKTREE_SHA_VERSION = "worktree_sha_version";
    private static final String FIELD_WORKTREE_SHA_FAILURE = "worktree_sha_failure";
    private static final String FIELD_GATE_KIND = "gate_kind";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_DURATION_MS = "duration_ms";
    private static final String FIELD_JOB_ID = "job_id";
    private static final String FIELD_COMMIT_SHA = "commit_sha";
    private static final String FIELD_CHANGED_PATHS = "changed_paths";
    private static final String FIELD_CHANGE_ORIGIN = "change_origin";
    private static final String FIELD_EXPECT = "expect";
    private static final String FIELD_EXPECT_RESULT = "expect_result";
    private static final String FIELD_BASELINE = "baseline";
    private static final String FIELD_DELIVERABLE_ID = "deliverable_id";
    private static final String FIELD_TASK_ID = "task_id";
    private static final String FIELD_RECORDED_AT = "recorded_at";
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_FAILURE = "failure";
    private static final String FIELD_EXIT_CODE = "exit_code";
    private static final String FIELD_SIGNAL = "signal";
    private static final String FIELD_MERGE_BASE = "merge_base";
    private static final String FIELD_BASE_COMMIT = "base_commit";
    private static final String FIELD_PROOF_SOURCE = "proof_source";
    private static final String FIELD_DIAGNOSTICS_DIGEST = "diagnostics_digest";
    private static final String FIELD_FINGERPRINTS = "fingerprints";

    /**
     * @param row the record
     * @return the record as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(ChangeLedgerRecord row) {
        Objects.requireNonNull(row, "row");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, ChangeLedgerRecord.FORMAT_VERSION);
            generator.writeStringField(FIELD_KIND, row.kind().wireName());
            generator.writeStringField(FIELD_WORKTREE_SHA, row.worktreeSha());
            generator.writeNumberField(FIELD_WORKTREE_SHA_VERSION, row.worktreeShaVersion());
            writeWorktreeShaFailure(generator, row.worktreeShaFailure());
            writeNullableString(generator, FIELD_GATE_KIND, row.gateKind());
            writeNullableString(generator, FIELD_OUTCOME, row.outcome() == null ? null
                    : row.outcome().wireName());
            writeNullableLong(generator, FIELD_DURATION_MS, row.durationMs());
            writeNullableString(generator, FIELD_JOB_ID, row.jobId());
            writeNullableString(generator, FIELD_COMMIT_SHA, row.commitSha());
            generator.writeArrayFieldStart(FIELD_CHANGED_PATHS);
            for (var path : row.changedPaths()) {
                generator.writeString(path);
            }
            generator.writeEndArray();
            writeNullableString(generator, FIELD_CHANGE_ORIGIN, row.changeOrigin() == null ? null
                    : row.changeOrigin().wireName());
            writeNullableString(generator, FIELD_EXPECT, row.expect() == null ? null : row.expect().wireName());
            writeNullableString(generator, FIELD_EXPECT_RESULT,
                    row.expectResult() == null ? null : row.expectResult().wireName());
            writeBaseline(generator, row.baseline());
            writeNullableString(generator, FIELD_DELIVERABLE_ID, row.deliverableId());
            writeNullableString(generator, FIELD_TASK_ID, row.taskId());
            writeInstant(generator, FIELD_RECORDED_AT, row.recordedAt());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Change-ledger record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the ledger file the record was read from, named by a refusal
     * @param document the bytes of one line, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the line is unreadable
     */
    public static ChangeLedgerRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, ChangeLedgerRecord.FORMAT_VERSION, ChangeLedgerCodec::readRecord);
    }

    private static void writeWorktreeShaFailure(JsonGenerator generator, WorktreeShaFailure failure) throws IOException {
        if (failure == null) {
            generator.writeNullField(FIELD_WORKTREE_SHA_FAILURE);
            return;
        }
        generator.writeObjectFieldStart(FIELD_WORKTREE_SHA_FAILURE);
        generator.writeArrayFieldStart(FIELD_COMMAND);
        for (var argument : failure.command()) {
            generator.writeString(argument);
        }
        generator.writeEndArray();
        generator.writeStringField(FIELD_FAILURE, failure.failure().wireName());
        writeNullableInteger(generator, FIELD_EXIT_CODE, failure.exitCode());
        writeNullableInteger(generator, FIELD_SIGNAL, failure.signal());
        generator.writeEndObject();
    }

    private static void writeBaseline(JsonGenerator generator, Baseline baseline) throws IOException {
        if (baseline == null) {
            generator.writeNullField(FIELD_BASELINE);
            return;
        }
        generator.writeObjectFieldStart(FIELD_BASELINE);
        generator.writeStringField(FIELD_GATE_KIND, baseline.gateKind());
        generator.writeStringField(FIELD_MERGE_BASE, baseline.mergeBase());
        generator.writeStringField(FIELD_BASE_COMMIT, baseline.baseCommit());
        generator.writeStringField(FIELD_PROOF_SOURCE, baseline.proofSource().wireName());
        generator.writeStringField(FIELD_DIAGNOSTICS_DIGEST, baseline.diagnosticsDigest());
        generator.writeArrayFieldStart(FIELD_FINGERPRINTS);
        for (var fingerprint : baseline.fingerprints()) {
            generator.writeString(fingerprint);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static void writeNullableLong(JsonGenerator generator, String field, Long value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    private static void writeNullableInteger(JsonGenerator generator, String field, Integer value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    private static ChangeLedgerRecord readRecord(JsonParser parser) throws IOException {
        Kind kind = null;
        String worktreeSha = null;
        Integer worktreeShaVersion = null;
        WorktreeShaFailure failure = null;
        String gateKind = null;
        Outcome outcome = null;
        Long durationMs = null;
        String jobId = null;
        String commitSha = null;
        List<String> changedPaths = List.of();
        ChangeOrigin changeOrigin = null;
        Expect expect = null;
        ExpectResult expectResult = null;
        Baseline baseline = null;
        String deliverableId = null;
        String taskId = null;
        Instant recordedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_KIND -> kind = enumValue(parser, field, Kind::fromWireName);
                case FIELD_WORKTREE_SHA -> worktreeSha = string(parser, field);
                case FIELD_WORKTREE_SHA_VERSION -> worktreeShaVersion = Math.toIntExact(integer(parser, field));
                case FIELD_WORKTREE_SHA_FAILURE -> failure = nullableWorktreeShaFailure(parser, field);
                case FIELD_GATE_KIND -> gateKind = nullableString(parser, field);
                case FIELD_OUTCOME -> outcome = nullableEnum(parser, field, Outcome::fromWireName);
                case FIELD_DURATION_MS -> durationMs = nullableLong(parser, field);
                case FIELD_JOB_ID -> jobId = nullableString(parser, field);
                case FIELD_COMMIT_SHA -> commitSha = nullableString(parser, field);
                case FIELD_CHANGED_PATHS -> changedPaths = stringList(parser, field);
                case FIELD_CHANGE_ORIGIN -> changeOrigin = nullableEnum(parser, field, ChangeOrigin::fromWireName);
                case FIELD_EXPECT -> expect = nullableEnum(parser, field, Expect::fromWireName);
                case FIELD_EXPECT_RESULT -> expectResult = nullableEnum(parser, field, ExpectResult::fromWireName);
                case FIELD_BASELINE -> baseline = nullableBaseline(parser, field);
                case FIELD_DELIVERABLE_ID -> deliverableId = nullableString(parser, field);
                case FIELD_TASK_ID -> taskId = nullableString(parser, field);
                case FIELD_RECORDED_AT -> recordedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "change-ledger record", FIELD_KIND, FIELD_WORKTREE_SHA, FIELD_WORKTREE_SHA_VERSION,
                FIELD_RECORDED_AT);
        try {
            return ChangeLedgerRecord.builder().kind(kind).worktreeSha(worktreeSha)
                    .worktreeShaVersion(worktreeShaVersion).worktreeShaFailure(failure).gateKind(gateKind)
                    .outcome(outcome).durationMs(durationMs).jobId(jobId).commitSha(commitSha)
                    .changedPaths(changedPaths).changeOrigin(changeOrigin).expect(expect).expectResult(expectResult)
                    .baseline(baseline).deliverableId(deliverableId).taskId(taskId).recordedAt(recordedAt).build();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static WorktreeShaFailure nullableWorktreeShaFailure(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        requireObject(parser, field);
        List<String> command = null;
        WorktreeShaFailure.FailureKind failure = null;
        Integer exitCode = null;
        Integer signal = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = nextField(parser, seen);
            switch (name) {
                case FIELD_COMMAND -> command = stringList(parser, name);
                case FIELD_FAILURE -> failure = enumValue(parser, name,
                        WorktreeShaFailure.FailureKind::fromWireName);
                case FIELD_EXIT_CODE -> exitCode = nullableInteger(parser, name);
                case FIELD_SIGNAL -> signal = nullableInteger(parser, name);
                default -> throw undeclared(name);
            }
        }
        requireFields(seen, field, FIELD_COMMAND, FIELD_FAILURE);
        return new WorktreeShaFailure(command, failure, exitCode, signal);
    }

    private static Baseline nullableBaseline(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        requireObject(parser, field);
        String gateKind = null;
        String mergeBase = null;
        String baseCommit = null;
        Baseline.ProofSource proofSource = null;
        String diagnosticsDigest = null;
        List<String> fingerprints = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = nextField(parser, seen);
            switch (name) {
                case FIELD_GATE_KIND -> gateKind = string(parser, name);
                case FIELD_MERGE_BASE -> mergeBase = string(parser, name);
                case FIELD_BASE_COMMIT -> baseCommit = string(parser, name);
                case FIELD_PROOF_SOURCE -> proofSource = enumValue(parser, name, Baseline.ProofSource::fromWireName);
                case FIELD_DIAGNOSTICS_DIGEST -> diagnosticsDigest = string(parser, name);
                case FIELD_FINGERPRINTS -> fingerprints = stringList(parser, name);
                default -> throw undeclared(name);
            }
        }
        requireFields(seen, field, FIELD_GATE_KIND, FIELD_MERGE_BASE, FIELD_BASE_COMMIT, FIELD_PROOF_SOURCE,
                FIELD_DIAGNOSTICS_DIGEST, FIELD_FINGERPRINTS);
        return new Baseline(gateKind, mergeBase, baseCommit, proofSource, diagnosticsDigest, fingerprints);
    }

    private static Integer nullableInteger(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : Math.toIntExact(integer(parser, field));
    }

    private static Long nullableLong(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
    }

    private static <T> T enumValue(JsonParser parser, String field, Function<String, Optional<T>> of)
            throws IOException {
        var name = string(parser, field);
        return of.apply(name)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, name)));
    }

    private static <T> T nullableEnum(JsonParser parser, String field,
            Function<String, Optional<T>> of) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : enumValue(parser, field, of);
    }
}
