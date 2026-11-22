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

    @Test
    @DisplayName("a message that still names a host path is carried as the outcome's code")
    void embeddedHostPathIsACode() {
        // the shape of JGitOperations.failure(e): an exception message beside an absolute path outside the checkout
        var message = "java.io.IOException: /srv/git/worktrees/plan";

        var result = GitStageAndCommitPrimitive.map(GitResult.of(GitOutcome.FAILED, message), repo);

        assertEquals("FAILED",
                result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                "A message naming a host path is carried as the code");
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
