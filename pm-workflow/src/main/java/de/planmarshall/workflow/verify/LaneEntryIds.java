/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

/**
 * The entry state of each plan lane (PM-IMPL-5): the state the {@code deep} lane enters and the one the {@code light}
 * lane enters after the split at {@code init.ready}. This is the one definition of both ids; it lives in this package
 * because {@code workflow.plan} depends on {@code workflow.verify}, and the reverse placement would make the two
 * packages depend on each other.
 *
 * @since 0.1
 */
public final class LaneEntryIds {

    /** The entry of the deep lane, which the deep exit of the split enters. */
    public static final String DEEP_ENTRY = "refine.baseline-reconcile";

    /** The entry of the light lane, which the light exit of the split enters. */
    public static final String LIGHT_ENTRY = "init.light-gate";

    private LaneEntryIds() {
    }
}
