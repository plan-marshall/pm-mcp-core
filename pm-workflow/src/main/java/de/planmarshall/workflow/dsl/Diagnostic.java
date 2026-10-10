/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

/**
 * Why and where the parser refused a unit (PM-IMPL-5).
 *
 * @param rule         the rule the unit violates
 * @param resourceName the resource the unit was read from
 * @param line         the 1-based line of the violation
 * @param column       the 1-based column of the violation
 * @param message      what is wrong, in words
 * @since 0.1
 */
public record Diagnostic(RefusalRule rule, String resourceName, int line, int column, String message) {
}
