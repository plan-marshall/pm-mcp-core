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

import lombok.experimental.UtilityClass;

/**
 * The entry state of each plan lane and the gate of the light lane (PM-IMPL-5), as the specification states them
 * (Phase Workflows Specification, Planning Lanes): the {@code deep} lane enters the refine loop at
 * {@code refine.analyze}, the {@code light} lane enters the collapsed light envelope {@code outline.light} after the
 * split at {@code init.ready}, and every accepted light outline passes the server light gate
 * {@code outline.light-gate} before {@code outline.gate}. This is the one definition of the three ids; it lives in
 * this package because {@code workflow.plan} depends on {@code workflow.verify}, and the reverse placement would make
 * the two packages depend on each other.
 *
 * @since 0.1
 */
@UtilityClass
public final class LaneEntryIds {

    /** The entry of the deep lane, which the deep exit of the split and the escalation of the light lane enter. */
    public static final String DEEP_ENTRY = "refine.analyze";

    /** The entry of the light lane, the collapsed light envelope, which the light exit of the split enters. */
    public static final String LIGHT_ENTRY = "outline.light";

    /** The server light gate, which every path of the light lane passes before {@code outline.gate}. */
    public static final String LIGHT_GATE = "outline.light-gate";
}
