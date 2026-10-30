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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * One line of a {@code change-ledger.jsonl} (Store Schemas § Change Ledger Record): a {@code worktree_sha}-stamped build
 * execution, file footprint change, or asynchronous job outcome.
 * <p>
 * The digest keys every freshness gate, so the row is bound to the tree it was observed on: a row of another hash
 * version never matches the live digest, and a row whose digest is {@code unavailable} never satisfies a freshness gate
 * and never equals another {@code unavailable} row. {@code worktree_sha_failure} is non-null exactly then.
 * <p>
 * The kind decides the members that are meaningful: a {@code build} row names the gate it proves and its outcome, a
 * {@code change} row names the commit and the changed paths, a {@code task_diff} row names its task, and a
 * {@code baseline_red} row carries the proof that a red gate is red through failures on the base only.
 *
 * @param kind               the kind of the row
 * @param worktreeSha        the working-tree digest, or {@code unavailable} when a git invocation failed
 * @param worktreeShaVersion the hash version of the digest
 * @param worktreeShaFailure why the digest is unavailable; non-null exactly then
 * @param gateKind           the gate the row proves, or {@code null}
 * @param outcome            the outcome of the run, or {@code null}
 * @param durationMs         the measured duration, or {@code null}
 * @param jobId              the job that produced the row, or {@code null}
 * @param commitSha          the commit of a change row, or {@code null}
 * @param changedPaths       the paths a change row records
 * @param changeOrigin       where a change came from, or {@code null}
 * @param expect             the {@code pm_build expect} parameter, or {@code null}
 * @param expectResult       how the expectation was met, or {@code null}
 * @param baseline           the baseline proof of a {@code baseline_red} row; {@code null} for every other kind
 * @param deliverableId      the deliverable a row belongs to, or {@code null}
 * @param taskId             the task of a {@code task_diff} row, or {@code null}
 * @param recordedAt         the instant the row was recorded
 * @since 0.1
 */
@Builder(toBuilder = true)
public record ChangeLedgerRecord(Kind kind, String worktreeSha, int worktreeShaVersion,
@Nullable WorktreeShaFailure worktreeShaFailure, @Nullable String gateKind, @Nullable Outcome outcome,
@Nullable Long durationMs, @Nullable String jobId, @Nullable String commitSha, List<String> changedPaths,
@Nullable ChangeOrigin changeOrigin, @Nullable Expect expect, @Nullable ExpectResult expectResult,
@Nullable Baseline baseline, @Nullable String deliverableId, @Nullable String taskId, Instant recordedAt) {

    /** The format version of a change-ledger record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The hash version of {@code worktree_sha}. */
    public static final int WORKTREE_SHA_VERSION = 1;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern JOB_ID = Pattern.compile("[bcgp]-[0-9a-f]{16}");
    private static final Pattern GATE_KIND = Pattern.compile(
            "(compile|test-compile|module-tests|integration-tests|e2e|coverage|quality-gate|arch-gate|verify|ci_check:[\\x20-\\x7E]{1,128})");
    private static final String UNAVAILABLE = "unavailable";

    /** The kind of a change-ledger row. */
    public enum Kind {
        /** A build execution. */
        BUILD("build"),
        /** A file footprint change. */
        CHANGE("change"),
        /** An asynchronous job outcome. */
        JOB("job"),
        /** The diff of one plan task. */
        TASK_DIFF("task_diff"),
        /** The proof that a red gate is red through base failures only. */
        BASELINE_RED("baseline_red");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The outcome of a build run. */
    public enum Outcome {
        /** The run succeeded. */
        SUCCEEDED("succeeded"),
        /** The run failed. */
        FAILED("failed"),
        /** The run failed and its log could not be parsed. */
        FAILED_UNPARSED("failed_unparsed"),
        /** The run was refused before it started. */
        REFUSED("refused"),
        /** The run was cancelled. */
        CANCELLED("cancelled"),
        /** The run exceeded its budget. */
        TIMED_OUT("timed_out"),
        /** The run was killed. */
        KILLED("killed"),
        /** The evidence decides no other outcome. */
        INDETERMINATE("indeterminate");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Outcome> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** Where a change came from. */
    public enum ChangeOrigin {
        /** A deliverable's own change. */
        PLAN_CHANGE("plan_change"),
        /** A commit of a quality gate's auto-fix. */
        QUALITY_GATE_AUTOFIX("quality_gate_autofix");

        private final String wireName;

        ChangeOrigin(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ChangeOrigin> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The {@code pm_build expect} parameter of a build row. */
    public enum Expect {
        /** A green run is expected. */
        GREEN("green"),
        /** A red run is expected. */
        RED("red");

        private final String wireName;

        Expect(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Expect> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** How the expectation of a red build was met. */
    public enum ExpectResult {
        /** The run failed, as expected. */
        EXPECTED_RED("expected_red"),
        /** The run succeeded although red was expected. */
        EXPECTED_RED_WENT_GREEN("expected_red_went_green");

        private final String wireName;

        ExpectResult(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ExpectResult> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * How a git invocation that should produce the digest failed.
     *
     * @param command   the failing argument vector
     * @param failure   how it failed
     * @param exitCode  the exit code, or {@code null}
     * @param signal    the terminating signal, or {@code null}
     * @since 0.1
     */
    public record WorktreeShaFailure(List<String> command, FailureKind failure, Integer exitCode, Integer signal) {

        /** How the git invocation failed. */
        public enum FailureKind {
            /** The command exited with a code. */
            EXIT_CODE("exit_code"),
            /** The command was killed by a signal. */
            SIGNAL("signal"),
            /** The command exceeded its budget. */
            TIMED_OUT("timed_out");

            private final String wireName;

            FailureKind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<FailureKind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * @throws NullPointerException     if the command or the failure is {@code null}
         * @throws IllegalArgumentException if the command is empty
         */
        public WorktreeShaFailure {
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            Objects.requireNonNull(failure, "failure");
            if (command.isEmpty()) {
                throw new IllegalArgumentException("'worktree_sha_failure.command' must not be empty");
            }
        }
    }

    /**
     * The server's proof that a red gate is red through failures already present on the base.
     *
     * @param gateKind          the red gate
     * @param mergeBase         the merge base the proof was taken on
     * @param baseCommit        the base commit whose result proved the failures
     * @param proofSource       where the proof came from
     * @param diagnosticsDigest the SHA-256 over the sorted fingerprints
     * @param fingerprints      the accepted fingerprints
     * @since 0.1
     */
    public record Baseline(String gateKind, String mergeBase, String baseCommit, ProofSource proofSource,
    String diagnosticsDigest, List<String> fingerprints) {

        /** Where the proof came from. */
        public enum ProofSource {
            /** The provider's CI result of the same check on the base commit. */
            CI_BASE_RESULT("ci_base_result"),
            /** A build of the merge base in the baseline scratch worktree. */
            LOCAL_MERGE_BASE_BUILD("local_merge_base_build");

            private final String wireName;

            ProofSource(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<ProofSource> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the gate kind does not match, a commit or digest breaks its grammar, or
         *                                  the fingerprints are empty
         */
        public Baseline {
            Objects.requireNonNull(gateKind, "gateKind");
            if (!GATE_KIND.matcher(gateKind).matches()) {
                throw new IllegalArgumentException("'baseline.gate_kind' is not a gate: " + gateKind);
            }
            requirePattern(mergeBase, SHA1, "baseline.merge_base");
            requirePattern(baseCommit, SHA1, "baseline.base_commit");
            Objects.requireNonNull(proofSource, "proofSource");
            requirePattern(diagnosticsDigest, SHA256, "baseline.diagnostics_digest");
            fingerprints = List.copyOf(Objects.requireNonNull(fingerprints, "fingerprints"));
            if (fingerprints.isEmpty()) {
                throw new IllegalArgumentException("'baseline.fingerprints' must not be empty");
            }
            for (var fingerprint : fingerprints) {
                requirePattern(fingerprint, SHA256, "baseline.fingerprints");
            }
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value breaks its grammar or the combination breaks a rule of the schema
     */
    public ChangeLedgerRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(worktreeSha, "worktree_sha");
        Objects.requireNonNull(recordedAt, "recorded_at");
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
        if (worktreeShaVersion != WORKTREE_SHA_VERSION) {
            throw new IllegalArgumentException("'worktree_sha_version' is " + WORKTREE_SHA_VERSION);
        }
        if (UNAVAILABLE.equals(worktreeSha) != (worktreeShaFailure != null)) {
            throw new IllegalArgumentException(
                    "'worktree_sha_failure' is set exactly when 'worktree_sha' is unavailable");
        }
        if (!UNAVAILABLE.equals(worktreeSha)) {
            requirePattern(worktreeSha, SHA256, "worktree_sha");
        }
        if (gateKind != null && !GATE_KIND.matcher(gateKind).matches()) {
            throw new IllegalArgumentException("'gate_kind' is not a gate: " + gateKind);
        }
        if (jobId != null) {
            requirePattern(jobId, JOB_ID, "job_id");
        }
        if (commitSha != null) {
            requirePattern(commitSha, SHA1, "commit_sha");
        }
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("'duration_ms' must not be negative: " + durationMs);
        }
        if ((kind == Kind.BASELINE_RED) != (baseline != null)) {
            throw new IllegalArgumentException("'baseline' is set exactly for the kind 'baseline_red'");
        }
        if (kind == Kind.TASK_DIFF && taskId == null) {
            throw new IllegalArgumentException("A 'task_diff' row carries its 'task_id'");
        }
        if (kind != Kind.TASK_DIFF && taskId != null) {
            throw new IllegalArgumentException("Only a 'task_diff' row carries a 'task_id'");
        }
    }

    private static void requirePattern(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a digest: %s".formatted(name, value));
        }
    }
}
