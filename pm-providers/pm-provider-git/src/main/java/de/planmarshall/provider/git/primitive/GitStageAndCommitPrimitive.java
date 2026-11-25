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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
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
import de.planmarshall.provider.git.GitOutcome;
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
 * call. The narrowing never refuses: a bound entry inside the staging set is committed and one outside it is dropped,
 * because the bind owns the rule that a {@code files} entry names a path of the staging set
 * ({@code input_refused}, the rule {@code path.staging});</li>
 * <li>the message given the resolved trailer by {@link CommitMessages#withTrailer(String, CommitTrailer)}, which
 * refuses a message that already holds a trailer line, and checked by {@link CommitMessages#validate(String)}; a
 * refusal is {@code failed} with the reason {@code message_refused}. The message may be absent: the clean-tree route of
 * {@code none_assert_clean} resolves {@code ready} without one, so the empty staging set above is
 * {@code nothing_to_commit} before the message is read; with a non-empty staging set an absent message is
 * {@code failed} with the reason {@code commit_failed} and the {@value #DETAIL_FACT} {@value #NO_MESSAGE_DETAIL};</li>
 * <li>one call of {@link GitOperations#commit(CommitInput)} with exactly the files to commit, the operator's
 * {@code commit_identity} as the author and the agent identity, else the compiled
 * {@code plan-marshall-mcp <noreply@cuioss.de>}, as the committer; the operator's git {@code user.*} settings are never
 * read.</li>
 * </ol>
 * The message reaches the repository in memory through the contract; no command line is built from a value. The
 * outcome of the contract maps to exactly one outcome: {@code OK} to {@code committed}, {@code NOTHING_TO_COMMIT} to
 * {@code nothing_to_commit}, every other, {@code CAPABILITY_MISSING} of the native variant among them, to
 * {@code failed} with the reason {@code commit_failed}. The {@value #DETAIL_FACT} of that failure is the contract's
 * detail with the checkout's absolute root made repository-relative, or the code of the git outcome where the contract
 * states no detail or a detail that still names an absolute path: no absolute path of the server behind an introducer
 * reaches a fact, while a path glued behind a path-name character stays as the text states it, the residual
 * {@link #modelDetail} names.
 * Hooks run only in the CLI variant, so only its hook refusal returns {@code hook_failed}; the native variant never does.
 * A {@code committed} result also carries the provenance of the resolved trailer as the fact
 * {@value #TRAILER_SOURCE_FACT}, for the commit's audit record; no other outcome carries it.
 * <p>
 * The primitive ends a cycle through its state by an attempt cap ({@link CycleMeasure#ATTEMPT_CAP}) whose exhaustion
 * outcome is {@code attempts_exhausted}; the engine is to count the attempts and return that outcome at the cap, the
 * primitive never does. The engine counts no attempts of this primitive yet (an external hand-off, ADR
 * provider-git-primitive 005), so no runtime path returns that outcome until it does.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Stage 4) and
 * {@code doc/specification/state-catalogue/05-git-commit-and-push.adoc} ({@code git.stage-and-commit}).
 *
 * @since 0.1
 */
public final class GitStageAndCommitPrimitive
        implements Primitive<GitStageAndCommitPrimitive.Params, GitStageAndCommitPrimitive.Outcome> {

    /** The id of the primitive. */
    public static final String PRIMITIVE_ID = "git.stage-and-commit";

    /** The fact of {@code committed}: the id of the new commit. */
    public static final String COMMIT_FACT = "commit";

    /**
     * The fact of {@code committed}: the provenance of the resolved trailer, {@code {name, email}} with a
     * {@link CommitTrailer.TrailerSource} wire name per half ({@link CommitTrailer.Resolution#sourceFact()}). It is
     * returned for the commit's audit record and is never rendered in facts: it carries no value of the trailer and
     * no state names it in a {@code facts} clause.
     */
    public static final String TRAILER_SOURCE_FACT = "trailer_source";

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

    /**
     * The fact of {@code commit_failed}: the detail of the failure — the contract's outcome made repository-relative,
     * the code of the git outcome for a host path outside the checkout, or the primitive's own detail.
     */
    public static final String DETAIL_FACT = "detail";

    /** The detail of a call whose staging set is not empty but whose message was never resolved. */
    static final String NO_MESSAGE_DETAIL = "no commit message is resolved";

    /**
     * The characters a relative name legitimately precedes a separator with: the segment characters of a path name and
     * the closing brackets. A path starts at every position that follows anything else, so the pattern fails closed on
     * an introducer no list can enumerate — an opening brace, a non-ASCII quote, a no-break space, punctuation, a
     * control character — instead of naming the introducers it knows. A closing bracket is admitted here because a
     * relative name carries one before a separator ({@code app/[slug]/page.tsx}); {@link #BRACKET_PATH} covers the host
     * path that follows a stray closing bracket. The punctuation a name may carry but a separator rarely follows —
     * {@code +}, {@code @}, {@code ~} — is no segment character here, so a separator after it is read as absolute: the
     * fail-closed direction, chosen so that {@code ~/srv/private/key} stays a code.
     */
    private static final String NAME_CHARACTER = "[\\p{L}\\p{N}._\\-\\]})]";

    /** The introducer-anchored prefix every host-path pattern starts with: anything but a path-name character. */
    private static final String HOST_PREFIX = "(?<!" + NAME_CHARACTER + ")";

    /**
     * A token that starts an absolute path inside a detail text: a {@code /} that no path-name character precedes. A
     * closing bracket is a path-name character, so a relative name such as {@code app/[slug]/page.tsx} or
     * {@code (group)/x} stays a name and {@link #BRACKET_PATH} covers the host path after a stray closing bracket; a
     * separator that follows any other punctuation is read as absolute, the fail-closed direction.
     */
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(HOST_PREFIX + "/[^\\s)\"']*");

    /**
     * A token that starts a drive-qualified absolute path inside a detail text: the same introducers before a drive
     * letter and a separator, so a Windows path {@code C:\...} or {@code C:/...} is refused whatever the host.
     */
    private static final Pattern DRIVE_PATH =
            Pattern.compile(HOST_PREFIX + "[A-Za-z]:[\\\\/][^\\s)\"']*");

    /**
     * A token that starts a UNC path inside a detail text: the same introducers before two backslashes, a server and a
     * share, so {@code \\server\share\...} is refused whatever the host.
     */
    private static final Pattern UNC_PATH =
            Pattern.compile(HOST_PREFIX + "\\x5c\\x5c[^\\s)\"']+\\x5c[^\\s)\"']*");

    /**
     * A closing bracket that follows an introducer and is followed by a {@code /} of a token of at least two segments:
     * a host path after a stray closing bracket holds further separators ({@code ]/srv/git/worktrees/plan}), while a
     * relative name with a closing bracket before a one-segment tail ({@code app/[slug]/page.tsx}, {@code (group)/x})
     * stays a name. A closing bracket that sits after a path character is no introducer, so the name survives; a
     * two-segment relative name after a stray closing bracket ({@code refused ]/a/b.txt}) is read as absolute, the
     * fail-closed direction.
     */
    private static final Pattern BRACKET_PATH =
            Pattern.compile(HOST_PREFIX + "[\\])}]/[^\\s)\"'/]*/[^\\s)\"']*");

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
        return PRIMITIVE_ID;
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
            case Violated(var offendingFiles, var offendingCount) -> failed(Reason.FOOTPRINT_VIOLATION,
                    Map.of(OFFENDING_FILES_FACT, offendingFiles, OFFENDING_COUNT_FACT, offendingCount));
            case Within _ -> stageAndCommit(context, parameters, worktree, stagingSet);
        };
    }

    /**
     * Runs once the footprint check passed: narrows the staging set by the bound files, never refusing an entry outside
     * it, checks the message and commits.
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
        var message = parameters.message();
        if (message == null) {
            return failed(Reason.COMMIT_FAILED, Map.of(DETAIL_FACT, NO_MESSAGE_DETAIL));
        }
        var resolution = CommitTrailer.resolve(site.localTrailer(context).orElse(null),
                site.machineTrailer(context).orElse(null));
        return switch (CommitMessages.withTrailer(message, resolution.trailer())) {
            case Refused(var violation) -> messageRefused(violation);
            case Appended(var withTrailer) -> validated(context, worktree, message, withTrailer,
                    List.copyOf(toCommit), resolution);
        };
    }

    /**
     * Validates the message without its trailer, so the rules judge what the caller supplied, then commits and adds
     * the provenance of the trailer to a {@code committed} result.
     */
    private PrimitiveResult<Outcome> validated(WorkflowExecutionContext context, Path worktree, String message,
            String withTrailer, List<String> paths, CommitTrailer.Resolution resolution) {
        var violations = CommitMessages.validate(message);
        if (!violations.isEmpty()) {
            return messageRefused(violations.getFirst());
        }
        return withTrailerSource(commit(context, worktree, withTrailer, paths), resolution);
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
        return map(git.commit(input), worktree);
    }

    /**
     * Adds the provenance of the trailer to a {@code committed} result, the one outcome whose commit carries it.
     */
    private static PrimitiveResult<Outcome> withTrailerSource(PrimitiveResult<Outcome> result,
            CommitTrailer.Resolution resolution) {
        if (result.outcome() != Outcome.COMMITTED) {
            return result;
        }
        var facts = new LinkedHashMap<>(result.updatedFacts());
        facts.put(TRAILER_SOURCE_FACT, resolution.sourceFact());
        return result(Outcome.COMMITTED, facts);
    }

    /**
     * Maps an outcome of the contract to exactly one outcome of the primitive.
     *
     * @param result   the outcome of the contract
     * @param worktree the root of the checkout, against which the detail of a failure is made repository-relative
     */
    static PrimitiveResult<Outcome> map(GitResult<GitOperations.CommitInfo> result, Path worktree) {
        return switch (result.outcome()) {
            case OK -> result(Outcome.COMMITTED,
                    Map.of(COMMIT_FACT, Objects.requireNonNull(result.value(), "the commit of OK").id()));
            case NOTHING_TO_COMMIT -> result(Outcome.NOTHING_TO_COMMIT, Map.of());
            default -> failed(Reason.COMMIT_FAILED,
                    Map.of(GIT_OUTCOME_FACT, result.outcome().name(), DETAIL_FACT,
                            modelDetail(result.outcome(), result.detail(), worktree)));
        };
    }

    /**
     * The detail of a failed commit as the model-facing fact carries it. The contract's detail names the checkout in
     * some of its outcomes, so every occurrence of the absolute root is made the repository-relative root {@code .}; an
     * absolute path outside the checkout — a {@code /}, a drive-qualified token or a UNC token that follows an
     * introducer, which is every position a path-name character does not precede — a text that still names one after
     * that replacement, and a text that carries a traversal segment are replaced by the code of the git outcome, because
     * the primitive cannot tell whether a host path of the server is safe to render; and a contract that states no
     * detail is carried as that code. The introducer is the complement of the path-name characters, not a list of the
     * characters a host path may follow, so an opening brace, a non-ASCII quote and a no-break space introduce a host
     * path as white space does: no multi-segment absolute path, no drive-qualified path and no UNC path behind such a
     * character reaches a fact. A closing bracket that follows an introducer and is followed by at least one further
     * separator is such an introducer too ({@code refused ]/a/b.txt} becomes the code), while
     * {@code app/[slug]/page.tsx} and {@code (group)/x} stay names; a single-segment fragment after a stray closing
     * bracket ({@code ]/srv}) and a path glued to a preceding path-name character stay as the text states them
     * ({@code refused -/srv/git/worktrees/plan}, {@code refused 1/srv/git/worktrees/plan} and
     * {@code refused (x)/srv/git/worktrees/plan} carry a multi-segment host path through), and a name whose segment ends
     * in {@code +}, {@code @} or {@code ~} ({@code c++/src/main.cpp}) becomes the code because that punctuation is no
     * path-name character here: these are the pattern's stated residual cost. The same reading refuses a relative name
     * whose segment ends in a character that is no path-name character — an opening brace, a backslash, a combining
     * mark, a symbol such as {@code +} or {@code $}, or an emoji — because its separator is then read as absolute. A
     * supplementary-plane letter is not seen by the lookbehind either: its one-unit character class cannot consume the
     * surrogate pair, so a relative name that starts with such a letter has its separator read as absolute and becomes
     * the code as well.
     */
    static String modelDetail(GitOutcome outcome, @Nullable String detail, Path worktree) {
        if (detail == null || detail.isBlank()) {
            return outcome.name();
        }
        var root = worktree.toAbsolutePath().normalize();
        Path named;
        try {
            named = Path.of(detail);
        } catch (InvalidPathException _) {
            return outcome.name();
        }
        if (named.isAbsolute()) {
            var normal = named.normalize();
            if (normal.equals(root)) {
                return ".";
            }
            if (normal.startsWith(root)) {
                var relative = root.relativize(normal).toString();
                return carriesAbsolutePath(relative) ? outcome.name() : relative;
            }
            return outcome.name();
        }
        var absoluteRoot = root.toString();
        var rootWithSeparator = absoluteRoot + "/";
        var scrubbed = detail.contains(rootWithSeparator) ? detail.replace(rootWithSeparator, "./") : detail;
        if (scrubbed.contains("../") || scrubbed.contains("..\\")) {
            return outcome.name();
        }
        if (carriesAbsolutePath(scrubbed)) {
            return outcome.name();
        }
        return scrubbed;
    }

    /**
     * @param text a detail text
     * @return whether it still names a POSIX absolute path, a drive-qualified path, a UNC path or a host path after a
     *         stray closing bracket
     */
    private static boolean carriesAbsolutePath(String text) {
        return ABSOLUTE_PATH.matcher(text).find() || DRIVE_PATH.matcher(text).find()
                || UNC_PATH.matcher(text).find() || BRACKET_PATH.matcher(text).find();
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
     * @param message the final message, without the trailer; {@code null} when the call resolved none, which a clean
     *                staging set answers as {@code nothing_to_commit} and which a non-empty set refuses as
     *                {@code failed} with the reason {@code commit_failed}
     * @param files   the files that narrow the commit to a subset of the staging set, {@code null} for the whole
     *                staging set; an entry outside the staging set is dropped, because the bind refuses such an entry
     *                ({@code path.staging}) and this primitive narrows only
     * @since 0.1
     */
    public record Params(@Nullable String message, @Nullable List<String> files) {

        /**
         * @param message the message
         * @param files   the files, copied
         */
        public Params {
            files = files == null ? null : List.copyOf(files);
        }
    }

    /**
     * The outcomes of {@code git.stage-and-commit}.
     *
     * @since 0.1
     */
    public enum Outcome implements PrimitiveOutcome {
        /**
         * The files are committed; the fact {@value GitStageAndCommitPrimitive#COMMIT_FACT} names the commit and
         * {@value GitStageAndCommitPrimitive#TRAILER_SOURCE_FACT} the provenance of its trailer.
         */
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
