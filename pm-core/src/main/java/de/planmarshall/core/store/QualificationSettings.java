/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The operator's level for role qualification (Store Schemas § Operator Local Configuration,
 * {@code settings.qualification}; Model Work § Qualification Records).
 * <p>
 * The two levels are read at every validity check, so a changed level applies to the records that exist already: a
 * record whose measurements fall below the current agreement level, or whose warm-fresh gap exceeds the current
 * allowed gap, is not qualified. The two skip flags exclude a role or the quorum calibration from the setup run's
 * grading; a skipped role stays unqualified unless a valid record exists.
 *
 * @param agreementLevel the percent of the items of each topic a configuration must answer right, {@code 0} to
 *                       {@code 100}; the compiled default is {@value #DEFAULT_AGREEMENT_LEVEL}
 * @param allowedGap     the largest allowed warm-fresh gap in percentage points, {@code 0} to {@code 100}; the
 *                       compiled default is {@value #DEFAULT_ALLOWED_GAP}
 * @param skipRoles      the roles the setup run does not grade
 * @param skipDecisions  whether the setup run calibrates no quorum declaration
 * @since 0.1
 */
public record QualificationSettings(int agreementLevel, int allowedGap, List<String> skipRoles,
boolean skipDecisions) {

    /** The compiled default of {@code agreement_level}. */
    public static final int DEFAULT_AGREEMENT_LEVEL = 80;

    /** The compiled default of {@code allowed_gap}. */
    public static final int DEFAULT_ALLOWED_GAP = 5;

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    /**
     * @throws NullPointerException     if the roles are {@code null}
     * @throws IllegalArgumentException if a level lies outside {@code 0} to {@code 100}, a role breaks its grammar, or
     *                                  a role is skipped twice
     */
    public QualificationSettings {
        skipRoles = List.copyOf(Objects.requireNonNull(skipRoles, "skipRoles"));
        if (agreementLevel < 0 || agreementLevel > 100) {
            throw new IllegalArgumentException("'agreement_level' lies outside 0 to 100: " + agreementLevel);
        }
        if (allowedGap < 0 || allowedGap > 100) {
            throw new IllegalArgumentException("'allowed_gap' lies outside 0 to 100: " + allowedGap);
        }
        for (var role : skipRoles) {
            if (!ROLE.matcher(role).matches()) {
                throw new IllegalArgumentException("Not a role name: " + role);
            }
        }
        if (new HashSet<>(skipRoles).size() != skipRoles.size()) {
            throw new IllegalArgumentException("A role is skipped twice: " + skipRoles);
        }
    }

    /** @return the settings of a project that set none */
    public static QualificationSettings defaults() {
        return new QualificationSettings(DEFAULT_AGREEMENT_LEVEL, DEFAULT_ALLOWED_GAP, List.of(), false);
    }
}
