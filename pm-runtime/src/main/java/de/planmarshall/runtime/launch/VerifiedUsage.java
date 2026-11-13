/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageReport;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageSource;

/**
 * The seam that hands the result of a profile verification's stage {@code usage} to the enrolment's usage report
 * (PM-CLIENT-5, Harness Profiles § Tokens, Not Money; Store Schemas § Harness Enrolment Store): {@link UsageReport#of}
 * is the mapping boundary — it names {@code input_tokens} as {@code input} and {@code output_tokens} as {@code output} —
 * and this class feeds it from the stage's own result, the start context included, while leaving the cost out: the cost
 * is recorded data and never part of the report, and the report's constructor refuses it.
 * <p>
 * The figures are trusted only after the evaluation was repeated: the stage runs on one worker run, and a new harness or
 * a new harness version is measured again before its numbers are believed, because a single run can report a figure the
 * harness does not report in general. {@link #trusted} states that condition, and {@link #install} refuses to carry an
 * untrusted report into the enrolment rather than writing a number the run did not earn. The start context is part of
 * what must agree: a context exactly one run measured is not a repeated measurement.
 * <p>
 * The install path itself — the operator verb that binds the report to a harness and version, and writes the enrolment,
 * the profile and the report under the store's lock — belongs to {@code plan-marshall-mcp}; what is built here is the
 * seam it calls.
 *
 * @since 0.1
 */
@UtilityClass
public class VerifiedUsage {

    /** The number of evaluations a new harness's or version's figures are trusted after. */
    public static final int REQUIRED_EVALUATIONS = 2;

    /**
     * @param evaluations the results of the stage {@code usage} of one harness and version, in the order they ran
     * @return whether the figures are trusted: the evaluation was repeated and every run recorded the same figure
     *         statuses and the same start context (class, model and first-turn tokens)
     * @throws NullPointerException if the list is {@code null}
     */
    public static boolean trusted(List<ProfileStages.UsageResult> evaluations) {
        if (evaluations.size() < REQUIRED_EVALUATIONS) {
            return false;
        }
        var first = statuses(evaluations.getFirst());
        var firstStartContext = startContext(evaluations.getFirst());
        return evaluations.stream().allMatch(result -> statuses(result).equals(first)
                && startContext(result).equals(firstStartContext));
    }

    /**
     * @param evaluations the results of the stage {@code usage} of one harness and version, in the order they ran
     * @return the usage report the enrolment keeps, with the start context the stage measured
     * @throws UsageTrustRefusal if the figures are not trusted yet
     */
    public static UsageReport install(List<ProfileStages.UsageResult> evaluations) {
        if (evaluations.size() < REQUIRED_EVALUATIONS) {
            throw new UsageTrustRefusal(UsageTrustRefusal.NOT_REPEATED,
                    "The usage evaluation of the harness ran %d time(s), and its figures are trusted after %d"
                            .formatted(evaluations.size(), REQUIRED_EVALUATIONS));
        }
        if (!trusted(evaluations)) {
            throw new UsageTrustRefusal(UsageTrustRefusal.DISAGREES,
                    "The repeated usage evaluations of the harness disagree on the figures it reports");
        }
        var result = evaluations.getFirst();
        return UsageReport.of(UsageSource.VERIFIED, statuses(result), startContext(result));
    }

    private static List<UsageReport.StartContext> startContext(ProfileStages.UsageResult result) {
        return result.startContext().stream()
                .map(context -> new UsageReport.StartContext(context.workerClass(), context.model(), context.tokens()))
                .toList();
    }

    private static Map<String, String> statuses(ProfileStages.UsageResult result) {
        var figures = new LinkedHashMap<String, String>();
        result.figures().forEach((figure, status) -> {
            if (ProfileStages.REPORT_FIGURES.contains(figure)) {
                figures.put(figure, status.wireName());
            }
        });
        return figures;
    }
}
