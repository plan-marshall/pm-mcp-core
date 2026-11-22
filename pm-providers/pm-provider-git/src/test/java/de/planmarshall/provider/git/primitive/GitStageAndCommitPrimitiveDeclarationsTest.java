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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
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
