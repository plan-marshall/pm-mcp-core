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
 * A {@code migrate <old> -> <new>} declaration: a state name of an earlier release and the state that replaces it
 * (PM-IMPL-5).
 *
 * @param oldStateId the state name of the earlier release
 * @param newStateId the state that replaces it
 * @param location   where the declaration was read
 * @since 0.1
 */
public record StateMigration(String oldStateId, String newStateId, SourceLocation location) {
}
