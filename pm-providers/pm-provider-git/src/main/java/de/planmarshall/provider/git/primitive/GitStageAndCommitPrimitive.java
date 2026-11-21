/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.OutcomeReason;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.spi.validation.ContentViolation;
import de.planmarshall.provider.git.GitOperations;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.primitive.CommitMessages.Appended;
import de.planmarshall.provider.git.primitive.CommitMessages.Refused;
import de.planmarshall.provider.git.primitive.FootprintBoundary.Violated;
import de.planmarshall.provider.git.primitive.FootprintBoundary.Within;
import de.planmarshall.provider.git.primitive.StagingSets.StagingSet;

/**
 * {@code git.stage-and-commit}: stages the staging set of the scope and commits it with the resolved message
 * (PM-WF-16, Stage 4).
 * <p>
 * The order of work is fixed:
 * <ol>
 * <li>the footprint check ({@link FootprintBoundary}) over the staging set of the scope ({@link StagingSets}) and the
 * breaches the {@link CommitSite} observes; a breach is {@code failed} with the reason {@code footprint_violation}
 * before anything is staged;</li>
 * <li>the staging set narrowed by the bound {@code files}; an empty set is {@code nothing_to_commit} without a git
 * call;</li>
 * <li>the message given the resolved trailer by {@link CommitMessages#withTrailer(String, CommitTrailer)}, which
 * refuses a message that already holds a trailer line, and checked by {@link CommitMessages#validate(String)}; a
 * refusal is {@code failed} with the reason {@code message_refused};</li>
 * <li>one call of {@link GitOperations#commit(CommitInput)} with exactly the files to commit, the operator's
 * {@code commit_identity} as the author and the agent identity, else the compiled
 * {@code plan-marshall-mcp <noreply@cuioss.de>}, as the committer; the operator's git {@code user.*} settings are never
 * read.</li>
 * </ol>
 * The message reaches the repository in memory through the contract; no command line is built from a value. The
 * outcome of the contract maps to exactly one outcome: {@code OK} to {@code committed}, {@code NOTHING_TO_COMMIT} to
 * {@code nothing_to_commit}, every other, {@code CAPABILITY_MISSING} of the native variant among them, to
 * {@code failed} with the reason {@code commit_failed}. Hooks run only in the CLI variant, so only its hook refusal
 * returns {@code hook_failed}; the native variant never does.
 * <p>
 * The primitive ends a cycle through its state by an attempt cap ({@link CycleMeasure#ATTEMPT_CAP}) whose exhaustion
 * outcome is {@code attempts_exhausted}; the engine counts the attempts and returns that outcome at the cap, the
 * primitive never does.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Stage 4) and
 * {@code doc/specification/state-catalogue/05-git-commit-and-push.adoc} ({@code git.stage-and-commit}).
 *
 * @since 0.1
 */
public final class GitStageAndCommitPrimitive
        implements Primitive<GitStageAndCommitPrimitive.Params, GitStageAndCommitPrimitive.Outcome> {

    /** The id of the primitive. */
    public static final String ID = "git.stage-and-commit";

    /** The fact of {@code committed}: the id of the new commit. */
    public static final String COMMIT_FACT = "commit";

    /** The fact of {@code footprint_violation}: the offending files, bounded. */
    public static final String OFFENDING_FILES_FACT = "offending_files";

    /** The fact of {@code footprint_violation}: the number of all offending files. */
    public static final String OFFENDING_COUNT_FACT = "offending_count";

    /** The fact of {@code message_refused}: the key of the refused rule. */
    public static final String RULE_FACT = "rule";

    /** The fact of {@code message_refused}: the line the rule refused, 0 for the whole message. */
    public static final String LINE_FACT = "line";

    /** The fact of {@code commit_failed}: the outcome of the contract. */
    public static final String GIT_OUTCOME_FACT = "git_outcome";

    /** The fact of {@code commit_failed}: the detail of the contract's outcome. */
    public static final String DETAIL_FACT = "detail";

    /** The committer while no agent identity resolves. */
    static final Identity COMPILED_COMMITTER = new Identity("plan-marshall-mcp", "noreply@cuioss.de");

    private final GitOperations git;

    private final CommitSite site;

    /**
     * @param git  the git contract the commit runs through
     * @param site the commit site of the assembly
     */
    public GitStageAndCommitPrimitive(GitOperations git, CommitSite site) {
        this.git = Objects.requireNonNull(git, "git");
        this.site = Objects.requireNonNull(site, "site");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public Class<Outcome> outcomeType() {
        return Outcome.class;
    }

    @Override
    public AwaitedEvent awaitedEvent() {
        return AwaitedEvent.NONE;
    }

    @Override
    public CycleMeasure cycleMeasure() {
        return CycleMeasure.ATTEMPT_CAP;
    }

    @Override
    public Optional<String> exhaustionOutcome() {
        return Optional.of(Outcome.ATTEMPTS_EXHAUSTED.wireName());
    }

    @Override
    public PrimitiveResult<Outcome> execute(WorkflowExecutionContext context, Params parameters) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(parameters, "parameters");
        var worktree = site.worktreeRoot(context);
        var stagingSet = StagingSets.derive(site.scopeType(context), worktree, site.dirtyPaths(context),
                site.archiving(context));
        return switch (FootprintBoundary.check(stagingSet, site.pinnedFilesChanged(context),
                site.mainCheckoutBreaches(context))) {
            case Violated violated -> failed(Reason.FOOTPRINT_VIOLATION, Map.of(OFFENDING_FILES_FACT,
                    violated.offendingFiles(), OFFENDING_COUNT_FACT, violated.offendingCount()));
            case Within _ -> stageAndCommit(context, parameters, worktree, stagingSet);
        };
    }

    /**
     * Runs once the footprint check passed: narrows the staging set, checks the message and commits.
     */
    private PrimitiveResult<Outcome> stageAndCommit(WorkflowExecutionContext context, Params parameters, Path worktree,
            StagingSet stagingSet) {
        var files = parameters.files();
        var toCommit = new TreeSet<>(stagingSet.staged());
        if (files != null) {
            toCommit.retainAll(files);
        }
        if (toCommit.isEmpty()) {
            return result(Outcome.NOTHING_TO_COMMIT, Map.of());
        }
        var trailer = CommitTrailer.resolve(site.localTrailer(context).orElse(null),
                site.machineTrailer(context).orElse(null)).trailer();
        return switch (CommitMessages.withTrailer(parameters.message(), trailer)) {
            case Refused refused -> messageRefused(refused.violation());
            case Appended appended -> validated(context, worktree, parameters.message(), appended.message(),
                    List.copyOf(toCommit));
        };
    }

    /**
     * Validates the message without its trailer, so the rules judge what the caller supplied.
     */
    private PrimitiveResult<Outcome> validated(WorkflowExecutionContext context, Path worktree, String message,
            String withTrailer, List<String> paths) {
        var violations = CommitMessages.validate(message);
        if (!violations.isEmpty()) {
            return messageRefused(violations.getFirst());
        }
        return commit(context, worktree, withTrailer, paths);
    }

    private PrimitiveResult<Outcome> commit(WorkflowExecutionContext context, Path worktree,
            String message, List<String> paths) {
        var author = site.commitIdentity(context);
        if (author.isEmpty()) {
            return failed(Reason.COMMIT_FAILED, Map.of(DETAIL_FACT, "no commit_identity is configured"));
        }
        var committer = site.agentIdentity(context).orElse(COMPILED_COMMITTER);
        var input = CommitInput.builder().root(worktree).message(message).author(author.get()).committer(committer)
                .paths(paths).build();
        return map(git.commit(input));
    }

    /**
     * Maps an outcome of the contract to exactly one outcome of the primitive.
     */
    static PrimitiveResult<Outcome> map(GitResult<GitOperations.CommitInfo> result) {
        return switch (result.outcome()) {
            case OK -> result(Outcome.COMMITTED,
                    Map.of(COMMIT_FACT, Objects.requireNonNull(result.value(), "the commit of OK").id()));
            case NOTHING_TO_COMMIT -> result(Outcome.NOTHING_TO_COMMIT, Map.of());
            default -> failed(Reason.COMMIT_FAILED,
                    Map.of(GIT_OUTCOME_FACT, result.outcome().name(), DETAIL_FACT, result.detail()));
        };
    }

    private static PrimitiveResult<Outcome> messageRefused(ContentViolation violation) {
        return failed(Reason.MESSAGE_REFUSED, Map.of(RULE_FACT, violation.ruleKey(), LINE_FACT, violation.line()));
    }

    private static PrimitiveResult<Outcome> result(Outcome outcome, Map<String, Object> facts) {
        return new PrimitiveResult<>(outcome, facts, null);
    }

    private static PrimitiveResult<Outcome> failed(Reason reason, Map<String, Object> facts) {
        return new PrimitiveResult<>(Outcome.FAILED, facts, null, reason);
    }

    /**
     * The bound inputs of the call.
     *
     * @param message the final message, without the trailer
     * @param files   the files that narrow the commit to a subset of the staging set, {@code null} for the whole
     *                staging set
     * @since 0.1
     */
    public record Params(String message, @Nullable List<String> files) {

        /**
         * @param message the message
         * @param files   the files, copied
         */
        public Params {
            Objects.requireNonNull(message, "message");
            files = files == null ? null : List.copyOf(files);
        }
    }

    /**
     * The outcomes of {@code git.stage-and-commit}.
     *
     * @since 0.1
     */
    public enum Outcome implements PrimitiveOutcome {
        /** The files are committed; the fact {@value GitStageAndCommitPrimitive#COMMIT_FACT} names the commit. */
        COMMITTED("committed", OutcomeClass.SUCCESS),
        /** Nothing of the staging set is left to commit. */
        NOTHING_TO_COMMIT("nothing_to_commit", OutcomeClass.SKIP),
        /**
         * A repository hook of the CLI variant refused the commit; {@code git.commit-fix} follows. The native variant
         * runs no hook and never returns it.
         */
        HOOK_FAILED("hook_failed", OutcomeClass.FAILURE),
        /** The attempt cap of the commit is spent; returned by the engine, never by the primitive. */
        ATTEMPTS_EXHAUSTED("attempts_exhausted", OutcomeClass.FAILURE),
        /** The commit is refused or failed; the {@link Reason} says why. */
        FAILED("failed", OutcomeClass.FAILURE) {

            @Override
            public Optional<Class<? extends OutcomeReason>> reasonType() {
                return Optional.of(Reason.class);
            }
        };

        private final String wireName;

        private final OutcomeClass outcomeClass;

        Outcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /**
     * The closed reason of {@link Outcome#FAILED}.
     *
     * @since 0.1
     */
    public enum Reason implements OutcomeReason {
        /**
         * A dirty path lies outside the staging set, a pinned build file changed, or a plan changed the main checkout;
         * nothing was staged. The facts name the offending files.
         */
        FOOTPRINT_VIOLATION("footprint_violation") {

            @Override
            public Optional<String> conditionKind() {
                return Optional.of("footprint_violation");
            }
        },
        /** The message breaks a rule; the facts name the rule and the line. */
        MESSAGE_REFUSED("message_refused"),
        /** The commit itself failed; the facts name the outcome of the contract. */
        COMMIT_FAILED("commit_failed");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }
    }
}
