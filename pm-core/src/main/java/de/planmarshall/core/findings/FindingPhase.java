/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed phase a finding was filed against (member {@code phase} of the findings ledger record, PM-IMPL-1); a
 * finding of {@link FindingSourceKind#QGATE} always names one.
 *
 * @since 0.1
 */
public enum FindingPhase {
    /** The refine phase. */
    PHASE_2_REFINE("phase-2-refine"),
    /** The outline phase. */
    PHASE_3_OUTLINE("phase-3-outline"),
    /** The plan phase. */
    PHASE_4_PLAN("phase-4-plan"),
    /** The execute phase. */
    PHASE_5_EXECUTE("phase-5-execute"),
    /** The finalize phase. */
    PHASE_6_FINALIZE("phase-6-finalize");

    private final String wireName;

    FindingPhase(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in the findings store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in the findings store
     * @return the value of that name; empty if no value has it
     */
    public static Optional<FindingPhase> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
