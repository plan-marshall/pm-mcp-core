/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

/**
 * Where a workflow unit comes from (PM-IMPL-5).
 *
 * @since 0.1
 */
public enum UnitOrigin {
    /** A default unit compiled into the binary. */
    DEFAULT,
    /** A unit owned by the project, read from its configuration directory. */
    PROJECT
}
