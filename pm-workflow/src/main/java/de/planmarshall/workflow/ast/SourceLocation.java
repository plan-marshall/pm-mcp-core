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
 * Where a node of the tree was read (PM-IMPL-5).
 *
 * @param resourceName the resource the unit was read from
 * @param line         the line of the node
 * @param column       the column of the node
 * @since 0.1
 */
public record SourceLocation(String resourceName, int line, int column) {
}
