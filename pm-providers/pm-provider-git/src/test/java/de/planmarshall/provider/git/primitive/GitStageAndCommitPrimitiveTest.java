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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.spi.validation.ConventionalCommitValidator;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOperations;
import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.JGitOperations;
import de.planmarshall.provider.git.RemotePolicy;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Reason;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * {@code git.stage-and-commit} over an EasyMock {@link CommitSite} and the real {@link JGitOperations} on a temporary
 * repository: the staging set per scope, the footprint check before any git call, the trailer, author and committer
 * read back from the history, the message refusal, and the mapping of every {@link GitOutcome} with its facts.
 */
@EnableGeneratorController
@DisplayName("GitStageAndCommitPrimitive")
class GitStageAndCommitPrimitiveTest {

    /** The directory of the epic records, a path of the staging-set contract. */
    private static final String EPICS = ".marshall/epics/";

    /** The directory of the archived epic records, a path of the staging-set contract. */
    private static final String ARCHIVED_EPICS = ".marshall/archived-epics/";

    @TempDir
    Path temp;

    private final TypedGenerator<String> names =
            Generators.uniqueValues(Generators.strings("ghijklmnopqrstuvwxyz", 4, 12));

    private final TypedGenerator<String> messages = GitGenerators.commitMessages();

    private JGitOperations git;

    private Path repo;

    private Identity operator;

    private Identity agent;

    private CommitSite site;

    private WorkflowExecutionContext context;

    @BeforeEach
    void initRepository() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture path of the test repository
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, names.next()).isOk(), "the test repository is initialised");
        operator = identity();
        agent = identity();
        write("README.md");
        assertTrue(git.commit(CommitInput.builder().root(repo).message(messages.next()).author(operator)
                .committer(agent).build()).isOk(), "the fixture commit is recorded");
        site = createMock(CommitSite.class);
        context = createMock(WorkflowExecutionContext.class);
    }

    @Nested
    @DisplayName("staging set")
    class StagingSetCases {

        @Test
        @DisplayName("a plan commits exactly the listed files and leaves an unrelated dirty file")
        void planCommitsListedFiles() throws Exception {
            var listed = file();
            var unrelated = file();
            write(listed);
            write(unrelated);
            stub(ScopeType.PLAN, false, List.of(listed, unrelated), Optional.of(agent));

            var result = run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), List.of(listed)));

            var commit = committed(result);
            assertAll("plan commit",
                    () -> assertTrue(inCommit(commit, listed), "the listed file is committed"),
                    () -> assertFalse(inCommit(commit, unrelated), "the unrelated file is not committed"),
                    () -> assertTrue(untracked().contains(unrelated), "the unrelated file stays unstaged"));
        }

        @Test
        @DisplayName("an epic stages the archived epics while it archives")
        void epicArchiving() throws Exception {
            var record = EPICS + file();
            var archived = ARCHIVED_EPICS + file();
            write(record);
            write(archived);
            stub(ScopeType.EPIC, true, List.of(record, archived), Optional.of(agent));

            var commit = committed(run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), null)));

            assertAll("archiving epic commit",
                    () -> assertTrue(inCommit(commit, record), "the epic record is committed"),
                    () -> assertTrue(inCommit(commit, archived), "the archived record is committed"));
        }

        @Test
        @DisplayName("an epic that does not archive refuses an archived epic before any git call")
        void epicNotArchiving() throws Exception {
            var record = EPICS + file();
            var archived = ARCHIVED_EPICS + file();
            write(record);
            write(archived);
            stub(ScopeType.EPIC, false, List.of(record, archived), Optional.of(agent));

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

            assertFailed(result, Reason.FOOTPRINT_VIOLATION,
                    Map.of(GitStageAndCommitPrimitive.OFFENDING_FILES_FACT, List.of(archived),
                            GitStageAndCommitPrimitive.OFFENDING_COUNT_FACT, 1));
        }

        @Test
        @DisplayName("a path outside the worktree is a footprint violation before any staging")
        void pathOutsideTheWorktree() throws Exception {
            var inside = file();
            write(inside);
            // a parent-relative name is the contract's example of a path outside the worktree
            var outside = "../" + file();
            stub(ScopeType.PLAN, false, List.of(inside, outside), Optional.of(agent));

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

            assertAll("footprint violation",
                    () -> assertFailed(result, Reason.FOOTPRINT_VIOLATION,
                            Map.of(GitStageAndCommitPrimitive.OFFENDING_FILES_FACT, List.of(outside),
                                    GitStageAndCommitPrimitive.OFFENDING_COUNT_FACT, 1)),
                    () -> assertTrue(untracked().contains(inside), "nothing inside the worktree is staged"));
        }

        @Test
        @DisplayName("files that name nothing of the staging set are nothing to commit without a git call")
        void nothingToCommit() throws Exception {
            var dirty = file();
            write(dirty);
            stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site),
                    new Params(messages.next(), List.of(file())));

            assertAll("nothing to commit",
                    () -> assertEquals(Outcome.NOTHING_TO_COMMIT, result.outcome(), "an empty set commits nothing"),
                    () -> assertNull(result.reason(), "nothing_to_commit carries no reason"));
        }
    }

    @Nested
    @DisplayName("message and identities")
    class MessageAndIdentities {

        @Test
        @DisplayName("the commit carries exactly one trailer and the operator and agent identities")
        void trailerAuthorCommitter() throws Exception {
            var dirty = file();
            write(dirty);
            var trailer = GitGenerators.trailers().next();
            expectScope(ScopeType.PLAN, false, List.of(dirty));
            expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
            expect(site.agentIdentity(context)).andReturn(Optional.of(agent)).anyTimes();
            expect(site.localTrailer(context))
                    .andReturn(Optional.of(new CommitTrailer.Stored(trailer.name(), trailer.email()))).anyTimes();
            expect(site.machineTrailer(context)).andReturn(Optional.empty()).anyTimes();
            var message = messages.next();

            var info = head(committed(run(new GitStageAndCommitPrimitive(git, site), new Params(message, null))));

            var expected = ((CommitMessages.Appended) CommitMessages.withTrailer(message, trailer)).message();
            assertAll("read back from the history",
                    () -> assertEquals(expected, info.message(), "the message carries the resolved trailer"),
                    () -> assertEquals(1, trailerLines(info.message()), "the message carries exactly one trailer"),
                    () -> assertEquals(operator, info.author(), "the author is the operator's identity"),
                    () -> assertEquals(agent, info.committer(), "the committer is the agent identity"));
        }

        @Test
        @DisplayName("without an agent identity the compiled committer and default trailer are used")
        void compiledCommitter() throws Exception {
            var dirty = file();
            write(dirty);
            stub(ScopeType.PLAN, false, List.of(dirty), Optional.empty());

            var info = head(committed(run(new GitStageAndCommitPrimitive(git, site),
                    new Params(messages.next(), null))));

            var compiled = CommitTrailer.compiled();
            assertAll("compiled values",
                    // the compiled committer of the specification
                    () -> assertEquals(new Identity("plan-marshall-mcp", "noreply@cuioss.de"), info.committer(),
                            "the committer is the compiled identity"),
                    () -> assertTrue(info.message().endsWith("Co-Authored-By: %s <%s>\n"
                            .formatted(compiled.name(), compiled.email())), "the trailer is the compiled one"));
        }

        @Test
        @DisplayName("a message with a trailer line is refused naming the rule before any git call")
        void messageWithTrailer() throws Exception {
            var dirty = file();
            write(dirty);
            stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));
            var trailer = GitGenerators.trailers().next();
            var message = "%s\n\nCo-Authored-By: %s <%s>".formatted(messages.next(), trailer.name(), trailer.email());

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(message, null));

            assertAll("message refused",
                    () -> assertEquals(Outcome.FAILED, result.outcome(), "the refused message fails the commit"),
                    () -> assertEquals(Reason.MESSAGE_REFUSED, result.reason(), "the reason is message_refused"),
                    () -> assertEquals(ConventionalCommitValidator.RULE_CO_AUTHORED_BY,
                            result.updatedFacts().get(GitStageAndCommitPrimitive.RULE_FACT),
                            "the facts name the trailer rule"));
        }

        @Test
        @DisplayName("a message without a type is refused naming the header rule before any git call")
        void messageWithoutType() throws Exception {
            var dirty = file();
            write(dirty);
            stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site),
                    new Params(": " + names.next(), null));

            assertAll("header refused",
                    () -> assertEquals(Reason.MESSAGE_REFUSED, result.reason(), "the reason is message_refused"),
                    () -> assertEquals(1, result.updatedFacts().get(GitStageAndCommitPrimitive.LINE_FACT),
                            "the facts name the header line"));
        }

        @Test
        @DisplayName("without a commit identity the commit fails without a git call")
        void noCommitIdentity() throws Exception {
            var dirty = file();
            write(dirty);
            expectScope(ScopeType.PLAN, false, List.of(dirty));
            expect(site.commitIdentity(context)).andReturn(Optional.empty()).anyTimes();
            expectTrailers();

            var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

            assertAll("no commit identity",
                    () -> assertEquals(Outcome.FAILED, result.outcome(), "a missing identity fails the commit"),
                    () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "the reason is commit_failed"));
        }
    }

    @Nested
    @DisplayName("declarations")
    class Declarations {

        @ParameterizedTest(name = "{0}")
        @EnumSource(GitOutcome.class)
        @DisplayName("every git outcome maps to one outcome and its facts, never hook_failed")
        void everyGitOutcomeMaps(GitOutcome gitOutcome) {
            var detail = Generators.letterStrings(1, 30).next();
            var info = commitInfo();
            var result = GitStageAndCommitPrimitive.map(gitOutcome == GitOutcome.OK ? GitResult.ok(info)
                    : GitResult.of(gitOutcome, detail));

            // the native variant runs no hook: CAPABILITY_MISSING is commit_failed like every other refusal
            var expected = switch (gitOutcome) {
                case OK -> Outcome.COMMITTED;
                case NOTHING_TO_COMMIT -> Outcome.NOTHING_TO_COMMIT;
                default -> Outcome.FAILED;
            };
            Map<String, Object> expectedFacts = switch (gitOutcome) {
                case OK -> Map.of(GitStageAndCommitPrimitive.COMMIT_FACT, info.id());
                case NOTHING_TO_COMMIT -> Map.of();
                default -> Map.of(GitStageAndCommitPrimitive.GIT_OUTCOME_FACT, gitOutcome.name(),
                        GitStageAndCommitPrimitive.DETAIL_FACT, detail);
            };
            assertAll("mapping of " + gitOutcome,
                    () -> assertEquals(expected, result.outcome(), "the git outcome maps to its outcome"),
                    () -> assertEquals(expectedFacts, result.updatedFacts(),
                            "the outcome carries its documented facts"),
                    () -> assertEquals(expected == Outcome.FAILED ? Reason.COMMIT_FAILED : null, result.reason(),
                            "only failed carries the reason commit_failed"));
        }

        @Test
        @DisplayName("CAPABILITY_MISSING of the native variant is failed with commit_failed")
        void capabilityMissingIsCommitFailed() {
            var detail = Generators.letterStrings(1, 30).next();

            var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.CAPABILITY_MISSING, detail));

            assertAll("capability missing",
                    () -> assertEquals(Outcome.FAILED, result.outcome(), "the missing capability fails the commit"),
                    () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "the reason is commit_failed"),
                    // the wire name of the git outcome is the fact the specification names
                    () -> assertEquals("CAPABILITY_MISSING",
                            result.updatedFacts().get(GitStageAndCommitPrimitive.GIT_OUTCOME_FACT),
                            "the facts name the git outcome"));
        }

        @Test
        @DisplayName("the primitive registers with an attempt cap and its exhaustion outcome")
        void registers() {
            var primitive = new GitStageAndCommitPrimitive(git, site);

            var registry = new PrimitiveRegistry(List.of(primitive));

            // the exhaustion outcome is the wire name the state catalogue routes
            var exhaustion = Arrays.stream(Outcome.values())
                    .filter(outcome -> outcome.wireName().equals("attempts_exhausted")).findFirst();
            assertAll("registration",
                    () -> assertTrue(registry.find(GitStageAndCommitPrimitive.ID).isPresent(),
                            "the registry holds git.stage-and-commit"),
                    () -> assertEquals(CycleMeasure.ATTEMPT_CAP, primitive.cycleMeasure(),
                            "the cycle measure is the attempt cap"),
                    () -> assertEquals(Optional.of("attempts_exhausted"), primitive.exhaustionOutcome(),
                            "the exhaustion outcome is attempts_exhausted"),
                    () -> assertEquals(Optional.of(OutcomeClass.FAILURE), exhaustion.map(Outcome::outcomeClass),
                            "the exhaustion outcome is a declared failure"));
        }

        @Test
        @DisplayName("only footprint_violation maps to a condition")
        void reasonConditions() {
            // the condition kind is the closed table of the specification
            assertAll("reason conditions",
                    () -> assertEquals(Optional.of("footprint_violation"), Reason.FOOTPRINT_VIOLATION.conditionKind(),
                            "footprint_violation maps to its condition"),
                    () -> assertEquals(Optional.empty(), Reason.MESSAGE_REFUSED.conditionKind(),
                            "message_refused maps to no condition"),
                    () -> assertEquals(Optional.empty(), Reason.COMMIT_FAILED.conditionKind(),
                            "commit_failed maps to no condition"));
        }
    }

    private void stub(ScopeType scope, boolean archiving, List<String> dirty, Optional<Identity> agentIdentity) {
        expectScope(scope, archiving, dirty);
        expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
        expect(site.agentIdentity(context)).andReturn(agentIdentity).anyTimes();
        expectTrailers();
    }

    private void expectScope(ScopeType scope, boolean archiving, List<String> dirty) {
        expect(site.worktreeRoot(context)).andReturn(repo).anyTimes();
        expect(site.scopeType(context)).andReturn(scope).anyTimes();
        expect(site.archiving(context)).andReturn(archiving).anyTimes();
        expect(site.dirtyPaths(context)).andReturn(dirty).anyTimes();
        expect(site.pinnedFilesChanged(context)).andReturn(List.of()).anyTimes();
        expect(site.mainCheckoutBreaches(context)).andReturn(List.of()).anyTimes();
    }

    private void expectTrailers() {
        expect(site.localTrailer(context)).andReturn(Optional.empty()).anyTimes();
        expect(site.machineTrailer(context)).andReturn(Optional.empty()).anyTimes();
    }

    private PrimitiveResult<Outcome> run(GitStageAndCommitPrimitive primitive, Params parameters) {
        replay(site, context);
        return primitive.execute(context, parameters);
    }

    /**
     * A strict double the primitive must not call: a refusal before any git call leaves the repository untouched.
     */
    private static GitOperations untouchedGit() {
        GitOperations untouched = createMock(GitOperations.class);
        replay(untouched);
        return untouched;
    }

    private static String committed(PrimitiveResult<Outcome> result) {
        assertEquals(Outcome.COMMITTED, result.outcome(), "the primitive commits the staging set");
        return (String) result.updatedFacts().get(GitStageAndCommitPrimitive.COMMIT_FACT);
    }

    private static void assertFailed(PrimitiveResult<Outcome> result, Reason reason, Map<String, Object> facts) {
        assertAll("failed " + reason.wireName(),
                () -> assertEquals(Outcome.FAILED, result.outcome(), "the commit is refused as failed"),
                () -> assertEquals(reason, result.reason(), "the reason names the refusal"),
                () -> assertEquals(facts, result.updatedFacts(), "the facts name what was refused"));
    }

    private CommitInfo head(String commit) {
        var history = git.log(repo, commit, 1);
        assertTrue(history.isOk(), "the history of the commit is read");
        return history.value().getFirst();
    }

    private static long trailerLines(String message) {
        // the trailer key is the line the specification counts
        return message.lines().filter(line -> line.toLowerCase(Locale.ROOT).startsWith("co-authored-by:")).count();
    }

    private boolean inCommit(String commit, String path) throws IOException {
        try (var jgit = Git.open(repo.toFile()); var walk = new RevWalk(jgit.getRepository())) {
            var tree = walk.parseCommit(jgit.getRepository().resolve(commit)).getTree();
            try (@Nullable TreeWalk found = TreeWalk.forPath(jgit.getRepository(), path, tree)) {
                return found != null;
            }
        }
    }

    private Set<String> untracked() throws IOException, GitAPIException {
        try (var jgit = Git.open(repo.toFile())) {
            return jgit.status().call().getUntracked();
        }
    }

    private String file() {
        return names.next() + ".txt";
    }

    private void write(String path) throws IOException {
        var file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Generators.letterStrings(1, 40).next(), StandardCharsets.UTF_8);
    }

    private static Identity identity() {
        return new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
    }

    private CommitInfo commitInfo() {
        var now = Instant.now();
        return CommitInfo.builder().id(names.next()).parents(List.of()).author(operator).authoredAt(now)
                .committer(agent).committedAt(now).message(messages.next()).build();
    }
}
