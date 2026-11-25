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

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Reason;

/**
 * {@code git.stage-and-commit}: the mapping of every {@link GitOutcome} with its facts, the registration with the
 * attempt cap, and the conditions of the closed reason set.
 */
@EnableGeneratorController
@DisplayName("GitStageAndCommitPrimitive: declarations")
class GitStageAndCommitPrimitiveDeclarationsTest extends GitStageAndCommitPrimitiveFixtures {

    @ParameterizedTest(name = "{0}")
    @EnumSource(GitOutcome.class)
    @DisplayName("every git outcome maps to one outcome and its facts, never hook_failed")
    void everyGitOutcomeMaps(GitOutcome gitOutcome) {
        var detail = Generators.letterStrings(1, 30).next();
        var info = commitInfo();
        var result = GitStageAndCommitPrimitive.map(gitOutcome == GitOutcome.OK ? GitResult.ok(info)
                : GitResult.of(gitOutcome, detail), repo);

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

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.CAPABILITY_MISSING, detail), repo);

        assertAll("capability missing",
                () -> assertEquals(Outcome.FAILED, result.outcome(), "the missing capability fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "the reason is commit_failed"),
                // the wire name of the git outcome is the fact the specification names
                () -> assertEquals("CAPABILITY_MISSING",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.GIT_OUTCOME_FACT),
                        "the facts name the git outcome"));
    }

    @Test
    @DisplayName("a detail that names the checkout root is carried as the repository-relative root")
    void checkoutRootDetailIsRelative() {
        var root = repo.toAbsolutePath().normalize().toString();

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.NOT_A_REPOSITORY, root), repo);

        assertEquals(".",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "The checkout root is carried as the relative root");
    }

    @Test
    @DisplayName("a detail below the checkout keeps its repository-relative name")
    void detailBelowTheCheckoutIsRelative() {
        var absolute = repo.resolve("src/Main.java").toAbsolutePath().toString();

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.PATH_REFUSED, absolute), repo);

        assertEquals("src/Main.java",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "The path below the checkout is carried relatively");
    }

    @Test
    @DisplayName("an absolute host path outside the checkout is carried as the outcome's code")
    void hostPathOutsideTheCheckoutIsACode() {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.NOT_A_REPOSITORY, "/etc/passwd"), repo);

        assertEquals("NOT_A_REPOSITORY",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A host path outside the checkout becomes a code");
    }

    // the message shapes are the contract: the detail of JGitOperations.failure(e) and a refusal detail, each beside a
    // host path the fact must never carry; several name an introducer a positive list of characters missed, and the tilde
    // is the punctuation the fail-closed direction keeps as an introducer
    @ParameterizedTest(name = "detail \"{0}\"")
    @ValueSource(strings = {
            "java.io.IOException: /srv/git/worktrees/plan",
            "IOException: path=/srv/git/worktrees/plan",
            "refused ]/srv/git/worktrees/plan",
            "refused }/srv/git/worktrees/plan",
            "refused >/srv/git/worktrees/plan",
            "refused `/srv/private/key`",
            "refused {/srv/git/worktrees/plan",
            "refused \u201c/srv/private/key\u201d",
            "refused \u00a0/srv/git/worktrees/plan",
            "refused ~/srv/private/key",
            "refused \\\\server\\share\\private",
            "refused C:\\Users\\plan\\checkout",
            "refused {C:\\Users\\plan\\checkout",
            "refused {\\\\server\\share\\private",
            "refused {]/srv/git/worktrees/plan",
            "refused \u0001/srv/git/worktrees/plan"})
    @DisplayName("a message that still names an absolute host path is carried as the outcome's code")
    void messageNamingAHostPathIsACode(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertAll("refusal of " + message,
                () -> assertEquals(Outcome.FAILED, result.outcome(), "A message naming a host path fails"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The refusal carries commit_failed"),
                () -> assertEquals("FAILED",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "A message naming a host path becomes the code"));
    }

    @Test
    @DisplayName("the checkout's root inside a message is made the relative root")
    void embeddedCheckoutRootIsRelative() {
        var root = repo.toAbsolutePath().normalize().toString();

        var result = GitStageAndCommitPrimitive.map(
                GitResult.of(GitOutcome.FAILED, "java.io.IOException: " + root + "/x"), repo);

        assertEquals("java.io.IOException: ./x",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "The root inside a message becomes the relative root");
    }

    @Test
    @DisplayName("a detail without text is carried as the outcome's code")
    void blankDetailIsACode() {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, " "), repo);

        assertEquals("FAILED",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A blank detail becomes the code");
    }

    @ParameterizedTest(name = "relative name in \"{0}\"")
    @ValueSource(strings = {"file src/Main.java is not a file", "file src/main/x is refused", "refused a/b"})
    @DisplayName("a relative name inside a message stays as the message states it")
    void relativePathInAMessageIsKept(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertEquals(message,
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A relative path inside a message is kept");
    }

    // the pattern's stated residual: the introducer is the complement of the path-name characters, so a host path glued
    // behind one reaches the fact; these cases pin the residual instead of closing it (ADR provider-git-primitive 011)
    @ParameterizedTest(name = "residual \"{0}\"")
    @ValueSource(strings = {
            "refused -/srv/git/worktrees/plan",
            "refused 1/srv/git/worktrees/plan",
            "refused (x)/srv/git/worktrees/plan",
            "refused ]/srv"})
    @DisplayName("a host path glued behind a path-name character stays as the stated residual")
    void gluedHostPathStaysAsStated(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertEquals(message,
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "The glued host path is the pattern's stated residual");
    }

    // DRIVE_PATH and UNC_PATH share HOST_PREFIX, so a drive or UNC path glued behind a name character is the same
    // residual as the POSIX shapes above: the reason is asserted with the kept detail (ADR provider-git-primitive 011)
    @ParameterizedTest(name = "glued shape \"{0}\"")
    @ValueSource(strings = {"refused xC:\\Users\\plan", "refused 1C:\\Users\\plan",
            "refused x\\\\server\\share\\private"})
    @DisplayName("a drive or UNC path glued behind a name character stays as the stated residual")
    void gluedDriveAndUncStayAsStated(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertAll("glued shape " + message,
                () -> assertEquals(Outcome.FAILED, result.outcome(), "The glued shape still fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The refusal carries commit_failed"),
                () -> assertEquals(message,
                        result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "The glued host path is the pattern's stated residual"));
    }

    // a supplementary-plane letter is not a name character to the one-unit lookbehind: the separator after it is read
    // as absolute, so the name becomes the code (the leak ADR provider-git-primitive 011 and modelDetail state)
    @ParameterizedTest(name = "supplementary name \"{0}\"")
    @ValueSource(strings = {"refused \uD801\uDC00/x", "file \uD801\uDC01/spec.adoc is refused",
            "refused \uD801\uDC02/y"})
    @DisplayName("a relative name that starts with a supplementary letter leaks as the outcome's code")
    void relativeNameWithASupplementaryLetterIsACode(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertAll("supplementary name " + message,
                () -> assertEquals(Outcome.FAILED, result.outcome(), "The refused name fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The refusal carries commit_failed"),
                () -> assertEquals("FAILED",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "A supplementary letter before the separator leaks a code"));
    }

    // a segment that ends in a character that is no path-name character: its separator is read as absolute (ADR 011)
    @ParameterizedTest(name = "segment end \"{0}\"")
    @ValueSource(strings = {
            "refused dir{/x",
            "refused dir\\/x",
            "refused dir\u0301/x",
            "refused dir$/x",
            "refused dir+/x",
            "refused dir@/x",
            "refused dir~/x",
            "refused c++/src/main.cpp",
            "refused dir\uD83D\uDE00/x"})
    @DisplayName("a relative name whose segment ends in no path-name character is carried as the code")
    void relativeNameEndingInANonPathCharacterIsACode(String message) {
        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertAll("refused name " + message,
                () -> assertEquals(Outcome.FAILED, result.outcome(), "The refused name fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The refusal carries commit_failed"),
                () -> assertEquals("FAILED",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "A name ending outside the path characters becomes the code"));
    }

    // a segment that ends in a hyphen is a name character before the separator, as `.` and `_` are
    @Test
    @DisplayName("a relative name whose segment ends in a hyphen stays as the message states it")
    void relativeHyphenatedNameIsKept() {
        var message = "refused dir-/x.txt";

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertEquals(message,
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A hyphen before the separator is no introducer");
    }

    // the bracket shapes are the contract ADR workflow-input/006 admits: a closing bracket before a one-segment
    // separator is no absolute-path introducer
    @ParameterizedTest(name = "relative name \"{0}\"")
    @ValueSource(strings = {"app/[slug]/page.tsx", "(group)/x", "app/{slug}/page.tsx"})
    @DisplayName("a relative name with a bracket stays as the message states it")
    void relativeBracketNameIsKept(String name) {
        var message = "file " + name + " is refused";

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertEquals(message,
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A relative name with a bracket is kept");
    }

    @Test
    @DisplayName("a drive-qualified path inside a message is carried as the outcome's code")
    void drivePathIsACode() {
        var result = GitStageAndCommitPrimitive.map(
                GitResult.of(GitOutcome.NOT_A_REPOSITORY, "refused C:\\Users\\plan\\checkout"), repo);

        assertEquals("NOT_A_REPOSITORY",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A drive-qualified path becomes a code");
    }

    @Test
    @DisplayName("a host path beside a relativized checkout path is carried as the outcome's code")
    void hostPathBesideARelativizedPathIsACode() {
        var root = repo.toAbsolutePath().normalize().toString();

        var result = GitStageAndCommitPrimitive.map(
                GitResult.of(GitOutcome.FAILED, root + "/a: /home/operator/private"), repo);

        assertEquals("FAILED",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "The relativized rest is scanned for a host path");
    }

    @Test
    @DisplayName("a traversal out of the checkout is carried as the outcome's code")
    void traversalOutOfTheCheckoutIsACode() {
        var root = repo.toAbsolutePath().normalize().toString();

        var result = GitStageAndCommitPrimitive.map(
                GitResult.of(GitOutcome.FAILED, "IOException: " + root + "/../private/key"), repo);

        assertEquals("FAILED",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A traversal out of the checkout becomes the code");
    }

    @Test
    @DisplayName("a sibling directory whose name extends the root is carried as the code, not mangled")
    void siblingOfTheRootIsACode() {
        var root = repo.toAbsolutePath().normalize().toString();

        var result = GitStageAndCommitPrimitive.map(
                GitResult.of(GitOutcome.FAILED, "at " + root + "sitory/x"), repo);

        assertEquals("FAILED",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A sibling of the root becomes the code");
    }

    @Test
    @DisplayName("a checkout that is no repository carries its root as the relative root")
    void repositorylessCheckoutIsRelative() throws Exception {
        var plain = Files.createDirectories(temp.resolve(names.next()));
        expectScope(plain, ScopeType.PLAN, false, List.of("x.txt"));
        expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
        expect(site.agentIdentity(context)).andReturn(Optional.of(agent)).anyTimes();
        expectTrailers();

        var result = run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), null));

        assertAll("repositoryless checkout",
                () -> assertEquals(Outcome.FAILED, result.outcome(), "The commit of a plain tree fails"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The reason is commit_failed"),
                () -> assertEquals("NOT_A_REPOSITORY",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.GIT_OUTCOME_FACT),
                        "The facts name the git outcome"),
                () -> assertEquals(".", result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "The absolute root is carried relatively"));
    }

    @Test
    @DisplayName("the primitive registers with an attempt cap and its exhaustion outcome")
    void registers() {
        var primitive = new GitStageAndCommitPrimitive(git, site);

        var registry = new PrimitiveRegistry(List.of(primitive));

        // the exhaustion outcome is the wire name the state catalogue routes
        var exhaustion = Arrays.stream(Outcome.values())
                .filter(outcome -> "attempts_exhausted".equals(outcome.wireName())).findFirst();
        assertAll("registration",
                () -> assertTrue(registry.find(GitStageAndCommitPrimitive.PRIMITIVE_ID).isPresent(),
                        "the registry holds git.stage-and-commit"),
                () -> assertEquals(CycleMeasure.ATTEMPT_CAP, primitive.cycleMeasure(),
                        "the cycle measure is the attempt cap"),
                () -> assertEquals(Optional.of("attempts_exhausted"), primitive.exhaustionOutcome(),
                        "the exhaustion outcome is attempts_exhausted"),
                () -> assertEquals(Optional.of(OutcomeClass.FAILURE), exhaustion.map(Outcome::outcomeClass),
                        "the exhaustion outcome is a declared failure"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Reason.class)
    @DisplayName("only footprint_violation maps to a condition")
    void reasonConditions(Reason reason) {
        // the condition kind is the closed table of the specification
        var expected = reason == Reason.FOOTPRINT_VIOLATION ? Optional.of("footprint_violation")
                : Optional.<String>empty();

        assertEquals(expected, reason.conditionKind(), "only footprint_violation raises a condition");
    }
}
