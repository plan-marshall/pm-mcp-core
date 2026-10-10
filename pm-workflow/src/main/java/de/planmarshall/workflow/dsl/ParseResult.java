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

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The result of parsing a unit (PM-IMPL-5): the tree, or the one refusal that stopped the parser. A refusal is a
 * value, never an exception.
 *
 * @since 0.1
 */
public sealed interface ParseResult permits ParseResult.Parsed, ParseResult.Refused {

    /**
     * The unit follows the grammar.
     *
     * @param definition the tree of the unit
     */
    record Parsed(WorkflowDefinition definition) implements ParseResult {
    }

    /**
     * The unit was refused; the first refusal wins.
     *
     * @param diagnostic why and where the unit was refused
     */
    record Refused(Diagnostic diagnostic) implements ParseResult {
    }
}
