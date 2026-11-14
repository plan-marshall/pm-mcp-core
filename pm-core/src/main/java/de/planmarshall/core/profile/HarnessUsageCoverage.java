/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a harness reports of its token usage, as a harness list shows it (PM-CLIENT-5, Harness Profiles § Tokens, Not
 * Money; State Catalogue § {@code harness.choose}): {@code complete}, {@code partial} naming the figures it does not
 * report, or {@code not_reported}.
 * <p>
 * The coverage is taken from the result of the verification stage {@code usage}, which records every figure as
 * {@code reported}, {@code not_applicable} or {@code missing}; only a figure the harness does not report counts against
 * it, because a figure that does not exist for the harness by design is no gap. The start context the stage measured per
 * class and model travels beside the coverage, so the list shows what a worker costs before it does anything.
 * <p>
 * A harness list read from an unreadable enrolment store shows {@link Status#UNREADABLE} at every harness instead of an
 * empty map: the store's refusal is the fact, and an empty map would claim that nothing is reported.
 *
 * @param status        the coverage of the report
 * @param missingFigures the report figures the harness does not report, in schema order; empty unless the status is
 *                       {@code partial}
 * @param startContext  the start context per class and model the stage {@code usage} measured; empty where it recorded
 *                      none
 * @since 0.1
 */
public record HarnessUsageCoverage(Status status, List<String> missingFigures,
List<ProfileStages.StartContext> startContext) {

    /** The closed coverage states of a harness's usage report. */
    public enum Status {
        /** Every report figure is reported or not applicable. */
        COMPLETE("complete"),
        /** Some figures are reported and at least one is missing. */
        PARTIAL("partial"),
        /** No report figure is reported or not applicable. */
        NOT_REPORTED("not_reported"),
        /**
         * The enrolment store could not be read; shown instead of an empty map. The wire name is the refusal code the
         * enrolment store raises for the same fault ({@code LaunchRefusal.HARNESS_STORE_UNREADABLE}); this module cannot
         * name that constant, because {@code pm-runtime} depends on {@code pm-core} and never the reverse, so the value
         * is spelled here and the two are kept equal by the client contract.
         */
        UNREADABLE("harness_store_unreadable");

        private final String wireName;

        Status(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the status in a harness list */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the missing figures do not belong to the status, or a status that carries a
     *                                  start context claims one it cannot have
     */
    public HarnessUsageCoverage {
        Objects.requireNonNull(status, "status");
        missingFigures = List.copyOf(Objects.requireNonNull(missingFigures, "missingFigures"));
        startContext = List.copyOf(Objects.requireNonNull(startContext, "startContext"));
        if (status == Status.PARTIAL && missingFigures.isEmpty()) {
            throw new IllegalArgumentException("A partial coverage names the figures that are missing");
        }
        if (status != Status.PARTIAL && !missingFigures.isEmpty()) {
            throw new IllegalArgumentException("Only a partial coverage names missing figures: " + status);
        }
        if (!ProfileStages.REPORT_FIGURES.containsAll(missingFigures)) {
            throw new IllegalArgumentException("Not a figure of the usage report: " + missingFigures);
        }
        var inSchemaOrder = ProfileStages.REPORT_FIGURES.stream().filter(missingFigures::contains).toList();
        if (!inSchemaOrder.equals(missingFigures)) {
            throw new IllegalArgumentException("The missing figures are named in schema order: " + missingFigures);
        }
        if (status == Status.UNREADABLE && !startContext.isEmpty()) {
            throw new IllegalArgumentException("An unreadable store records no start context");
        }
    }

    /**
     * The one derivation of a harness's usage coverage from the figures it reports.
     *
     * @param statusesByFigure the status of each report figure, keyed by the profile schema's figure name
     *                         ({@code input_tokens}, {@code output_tokens}, …) and valued {@code reported},
     *                         {@code not_applicable} or {@code missing}; a figure the map does not name is
     *                         {@code missing}
     * @param startContext     the start context per class and model; empty where none was recorded
     * @return the coverage of the harness's report, with the missing figures in schema order
     * @throws NullPointerException if a component is {@code null}
     */
    public static HarnessUsageCoverage ofFigures(Map<String, String> statusesByFigure,
            List<ProfileStages.StartContext> startContext) {
        var missing = ProfileStages.REPORT_FIGURES.stream()
                .filter(figure -> "missing".equals(statusesByFigure.getOrDefault(figure, "missing")))
                .toList();
        var status = Status.PARTIAL;
        if (missing.size() == ProfileStages.REPORT_FIGURES.size()) {
            status = Status.NOT_REPORTED;
        } else if (missing.isEmpty()) {
            status = Status.COMPLETE;
        }
        return new HarnessUsageCoverage(status, status == Status.PARTIAL ? missing : List.of(), startContext);
    }

    /**
     * @return the coverage of a harness list whose enrolment store could not be read: {@code harness_store_unreadable}
     *         instead of an empty map
     */
    public static HarnessUsageCoverage unreadable() {
        return new HarnessUsageCoverage(Status.UNREADABLE, List.of(), List.of());
    }

    /**
     * @return the coverage of a harness none of whose usage was ever recorded, {@code not_reported}
     */
    public static HarnessUsageCoverage nothingRecorded() {
        return new HarnessUsageCoverage(Status.NOT_REPORTED, List.of(), List.of());
    }

    /** @return whether the coverage is a refusal of the store rather than a recording */
    public boolean unreadableStore() {
        return status == Status.UNREADABLE;
    }

    /** @return whether the coverage names figures the harness does not report */
    public boolean namesMissingFigures() {
        return status == Status.PARTIAL;
    }
}
