/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.envelope.TaskCostEstimator.TaskSignals;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The cost sizing of a plan task: the score, its bands, the profile weights and the size-to-token table.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("TaskCostEstimator")
class TaskCostEstimatorTest {

    private static final Set<String> LISTED_PROFILES = Set.of("implementation", "module_testing", "verification");

    private static TaskSignals signals(int steps, String profile, int skills, int files) {
        return TaskSignals.builder().steps(steps).profile(profile).skills(skills).files(files).build();
    }

    private static TaskSignals generatedSignals() {
        return signals(Generators.integers(0, 80).next(), "implementation", Generators.integers(0, 20).next(),
                Generators.integers(0, 40).next());
    }

    private static String unlistedProfile() {
        String profile;
        do {
            profile = Generators.letterStrings(3, 16).next();
        } while (LISTED_PROFILES.contains(profile));
        return profile;
    }

    @Nested
    @DisplayName("the score")
    class Score {

        @Test
        @DisplayName("weighs steps by 10, skills by 3 and files by 4, contract weights")
        void weighsTheSignals() {
            int steps = Generators.integers(0, 80).next();
            int skills = Generators.integers(0, 20).next();
            int files = Generators.integers(0, 40).next();

            long score = TaskCostEstimator.score(signals(steps, "implementation", skills, files));

            assertEquals(10L * steps + 12 + 3L * skills + 4L * files, score,
                    "The score follows the weighted sum of the signals");
        }

        @ParameterizedTest
        @CsvSource({"implementation,12", "module_testing,12", "verification,4", "integration_testing,8",
                "documentation,8"})
        @DisplayName("adds the weight of the profile, contract profiles and weights")
        void addsTheProfileWeight(String profile, long weight) {
            assertEquals(weight, TaskCostEstimator.score(signals(0, profile, 0, 0)),
                    "A task without counts scores its profile weight");
        }

        @Test
        @DisplayName("weighs an unlisted profile with the default weight 8, contract weight")
        void weighsAnUnlistedProfileWithTheDefault() {
            assertEquals(8L, TaskCostEstimator.score(signals(0, unlistedProfile(), 0, 0)),
                    "An unlisted profile weighs the default of 8");
        }
    }

    @Nested
    @DisplayName("the bands")
    class Bands {

        @ParameterizedTest
        @CsvSource({"0,XS", "29,XS", "30,S", "59,S", "60,M", "149,M", "150,L", "299,L", "300,XL", "699,XL",
                "700,XXL"})
        @DisplayName("map each band edge to its size, contract bands")
        void mapEachBandEdge(long score, CostSize size) {
            assertEquals(size, TaskCostEstimator.sizeOf(score), "The score falls into the band of its size");
        }

        @Test
        @DisplayName("map every score from 700 on to XXL, contract band")
        void mapEveryLargeScoreToXxl() {
            long score = 700L + Generators.integers(0, Integer.MAX_VALUE - 1).next();

            assertEquals(CostSize.XXL, TaskCostEstimator.sizeOf(score), "A score of 700 or more is XXL");
        }

        @Test
        @DisplayName("refuse a negative score")
        void refuseANegativeScore() {
            long score = -1L - Generators.integers(0, 1000).next();

            assertThrows(IllegalArgumentException.class, () -> TaskCostEstimator.sizeOf(score),
                    "A negative score has no band and is refused");
        }

        @Test
        @DisplayName("never lower the size when one count of a task is raised")
        void neverLowerTheSizeWhenACountIsRaised() {
            var base = generatedSignals();
            int raise = Generators.integers(1, 100).next();
            List<UnaryOperator<TaskSignals>> raises = List.of(
                    s -> signals(s.steps() + raise, s.profile(), s.skills(), s.files()),
                    s -> signals(s.steps(), s.profile(), s.skills() + raise, s.files()),
                    s -> signals(s.steps(), s.profile(), s.skills(), s.files() + raise));
            var before = TaskCostEstimator.sizeOf(TaskCostEstimator.score(base));

            for (var raiseOne : raises) {
                var after = TaskCostEstimator.sizeOf(TaskCostEstimator.score(raiseOne.apply(base)));
                assertTrue(after.compareTo(before) >= 0, "Raising a count never lowers the cost size");
            }
        }
    }

    @Nested
    @DisplayName("the estimate")
    class Estimate {

        @Test
        @DisplayName("looks the size up in the compiled defaults with basis uncalibrated")
        void looksTheSizeUpInTheCompiledDefaults() {
            var signals = generatedSignals();
            var size = TaskCostEstimator.sizeOf(TaskCostEstimator.score(signals));

            var estimate = TaskCostEstimator.estimate(signals, SizeTokenTable.COMPILED_DEFAULTS);

            assertAll("The estimate from the compiled defaults",
                    () -> assertEquals(size, estimate.costSize(), "The estimate carries the size of the score"),
                    () -> assertEquals(SizeTokenTable.COMPILED_DEFAULTS.tokensFor(size),
                            estimate.predictedCostTokens(), "The tokens are the default of the size"),
                    () -> assertEquals(SizeTokenTable.Basis.UNCALIBRATED, estimate.basis(),
                            "The default table carries basis uncalibrated"));
        }

        @Test
        @DisplayName("carries the values and the basis of the table the caller passes")
        void carriesTheValuesAndBasisOfTheGivenTable() {
            var tokens = new EnumMap<CostSize, Long>(CostSize.class);
            for (CostSize size : CostSize.values()) {
                tokens.put(size, Generators.longs(0, 1_000_000).next());
            }
            var table = new SizeTokenTable(tokens, SizeTokenTable.Basis.CALIBRATED);
            var signals = generatedSignals();
            var size = TaskCostEstimator.sizeOf(TaskCostEstimator.score(signals));

            var estimate = TaskCostEstimator.estimate(signals, table);

            assertAll("The estimate from a calibrated table",
                    () -> assertEquals(tokens.get(size), estimate.predictedCostTokens(),
                            "The tokens are looked up in the given table"),
                    () -> assertEquals(SizeTokenTable.Basis.CALIBRATED, estimate.basis(),
                            "The estimate carries the basis of the table"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"steps", "skills", "files"})
        @DisplayName("refuses a negative count")
        void refusesANegativeCount(String count) {
            int negative = -1 - Generators.integers(0, 1000).next();

            assertThrows(IllegalArgumentException.class, () -> signals(
                    "steps".equals(count) ? negative : 0, "implementation",
                    "skills".equals(count) ? negative : 0,
                    "files".equals(count) ? negative : 0), "A negative count of the task is refused");
        }
    }

    @Nested
    @DisplayName("the size-to-token table")
    class Table {

        @ParameterizedTest
        @CsvSource({"XS,5000", "S,25000", "M,60000", "L,130000", "XL,260000", "XXL,520000"})
        @DisplayName("holds the compiled defaults, contract values")
        void holdsTheCompiledDefaults(CostSize size, long tokens) {
            assertEquals(tokens, SizeTokenTable.COMPILED_DEFAULTS.tokensFor(size),
                    "The compiled default of the size is as specified");
        }

        @Test
        @DisplayName("labels the compiled defaults uncalibrated")
        void labelsTheCompiledDefaultsUncalibrated() {
            assertEquals(SizeTokenTable.Basis.UNCALIBRATED, SizeTokenTable.COMPILED_DEFAULTS.basis(),
                    "The compiled defaults are not yet calibrated");
        }

        @ParameterizedTest
        @EnumSource(CostSize.class)
        @DisplayName("refuses a table without a value for a size")
        void refusesATableWithoutASize(CostSize missing) {
            var tokens = new EnumMap<>(SizeTokenTable.COMPILED_DEFAULTS.tokens());
            tokens.remove(missing);

            assertThrows(IllegalArgumentException.class,
                    () -> new SizeTokenTable(tokens, SizeTokenTable.Basis.CALIBRATED),
                    "A table must name every cost size");
        }

        @Test
        @DisplayName("refuses a negative value")
        void refusesANegativeValue() {
            var tokens = new EnumMap<>(SizeTokenTable.COMPILED_DEFAULTS.tokens());
            tokens.put(Generators.enumValues(CostSize.class).next(), -1L - Generators.longs(0, 1000).next());

            assertThrows(IllegalArgumentException.class,
                    () -> new SizeTokenTable(tokens, SizeTokenTable.Basis.CALIBRATED),
                    "A table value below zero is refused");
        }
    }
}
