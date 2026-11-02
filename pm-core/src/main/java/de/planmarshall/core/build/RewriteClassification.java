/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

/**
 * Whether an OpenRewrite recipe finding was newly detected in this run or already present before it.
 * <p>
 * The classification is keyed off the structured log identifier the recipe emits — {@code CUI_REWRITE-100} for a
 * newly-detected finding and {@code CUI_REWRITE-101} for a pre-existing one — never off the human verb of the message.
 *
 * @since 0.1
 */
public enum RewriteClassification {

    /** The recipe detected the finding in this run. */
    NEWLY_DETECTED,

    /** The finding was already present before this run. */
    PRE_EXISTING
}
