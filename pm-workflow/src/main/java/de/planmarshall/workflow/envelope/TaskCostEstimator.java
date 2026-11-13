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

import java.util.Objects;

import lombok.Builder;
import lombok.experimental.UtilityClass;

/**
 * The cost sizing of a plan task ({@code derive-cost-size}): a weighted score of four signals of the task record,
 * mapped through monotone bands to a {@link CostSize} and looked up in a {@link SizeTokenTable}.
 * <p>
 * The score is {@code 10 * steps + W_profile + 3 * skills + 4 * files}, where the profile weight is 12 for
 * {@code implementation} and {@code module_testing}, 4 for {@code verification} and 8 for every other profile. The
 * bands are {@code XS < 30}, {@code S < 60}, {@code M < 150}, {@code L < 300}, {@code XL < 700} and {@code XXL} from
 * 700 on, so raising a signal never lowers the size. The computation is pure: it reads no store and no profile type.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}, § Cost Sizing, items 1
 * to 3.
 */
@UtilityClass
public class TaskCostEstimator {

    private static final int STEP_WEIGHT = 10;
    private static final int SKILL_WEIGHT = 3;
    private static final int FILE_WEIGHT = 4;

    private static final int WEIGHT_IMPLEMENTATION = 12;
    private static final int WEIGHT_MODULE_TESTING = 12;
    private static final int WEIGHT_VERIFICATION = 4;
    private static final int WEIGHT_OTHER_PROFILE = 8;

    private static final long BAND_S = 30;
    private static final long BAND_M = 60;
    private static final long BAND_L = 150;
    private static final long BAND_XL = 300;
    private static final long BAND_XXL = 700;

    /**
     * The four signals of a task record the score is computed from.
     *
     * @param steps   the number of the task's steps
     * @param profile the task's profile, for example {@code implementation}
     * @param skills  the number of the task's skills
     * @param files   the number of the task's distinct target files
     */
    @Builder
    public record TaskSignals(int steps, String profile, int skills, int files) {

        /**
         * Refuses a negative count.
         *
         * @param steps   the number of the task's steps
         * @param profile the task's profile
         * @param skills  the number of the task's skills
         * @param files   the number of the task's distinct target files
         * @throws IllegalArgumentException if a count is negative
         */
        public TaskSignals {
            Objects.requireNonNull(profile, "profile");
            requireNotNegative(steps, "steps");
            requireNotNegative(skills, "skills");
            requireNotNegative(files, "files");
        }

        private static void requireNotNegative(int count, String name) {
            if (count < 0) {
                throw new IllegalArgumentException("The count of " + name + " must not be negative: " + count);
            }
        }
    }

    /**
     * The cost estimate of a plan task.
     *
     * @param costSize            the cost size the score maps to
     * @param predictedCostTokens the tokens of the size in the table that was used
     * @param basis               the basis of that table
     */
    public record CostEstimate(CostSize costSize, long predictedCostTokens, SizeTokenTable.Basis basis) {
    }

    /**
     * Estimates the cost of a plan task.
     *
     * @param signals the four signals of the task record
     * @param table   the size-to-token table to look the predicted tokens up in
     * @return the cost size, the predicted tokens and the basis of the table
     */
    public static CostEstimate estimate(TaskSignals signals, SizeTokenTable table) {
        Objects.requireNonNull(table, "table");
        var size = sizeOf(score(signals));
        return new CostEstimate(size, table.tokensFor(size), table.basis());
    }

    /**
     * Computes the weighted score of a plan task.
     *
     * @param signals the four signals of the task record
     * @return {@code 10 * steps + W_profile + 3 * skills + 4 * files}
     */
    public static long score(TaskSignals signals) {
        Objects.requireNonNull(signals, "signals");
        return (long) STEP_WEIGHT * signals.steps()
                + profileWeight(signals.profile())
                + (long) SKILL_WEIGHT * signals.skills()
                + (long) FILE_WEIGHT * signals.files();
    }

    /**
     * Maps a score to its cost size through the bands.
     *
     * @param score the score, zero or more
     * @return the cost size of the band the score falls in
     * @throws IllegalArgumentException if the score is negative
     */
    public static CostSize sizeOf(long score) {
        if (score < 0) {
            throw new IllegalArgumentException("The score must not be negative: " + score);
        }
        if (score < BAND_S) {
            return CostSize.XS;
        }
        if (score < BAND_M) {
            return CostSize.S;
        }
        if (score < BAND_L) {
            return CostSize.M;
        }
        if (score < BAND_XL) {
            return CostSize.L;
        }
        if (score < BAND_XXL) {
            return CostSize.XL;
        }
        return CostSize.XXL;
    }

    private static int profileWeight(String profile) {
        return switch (profile) {
            case "implementation" -> WEIGHT_IMPLEMENTATION;
            case "module_testing" -> WEIGHT_MODULE_TESTING;
            case "verification" -> WEIGHT_VERIFICATION;
            default -> WEIGHT_OTHER_PROFILE;
        };
    }
}
