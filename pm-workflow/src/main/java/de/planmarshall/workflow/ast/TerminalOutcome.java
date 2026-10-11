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

import de.planmarshall.core.spi.OutcomeClass;

/**
 * {@code terminal <outcome_id> <outcome_class>}: the outcome a terminal state reports and its class (PM-IMPL-5).
 *
 * @param id           the identifier of the outcome
 * @param outcomeClass the class of the outcome
 * @since 0.1
 */
public record TerminalOutcome(String id, OutcomeClass outcomeClass) {
}
