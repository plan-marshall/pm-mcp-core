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

import java.util.Optional;

/**
 * A state of a workflow unit (PM-IMPL-5).
 *
 * @param id               the identifier of the state
 * @param terminalOutcome  the outcome the state reports; present if and only if the state is terminal
 * @param skillPath        the skill the state names in its {@code skill} clause
 * @param factsPrimitiveId the primitive of its {@code facts} clause
 * @param behavior         what the state does
 * @param location         where the state was read
 * @since 0.1
 */
public record StateNode(
String id,
Optional<TerminalOutcome> terminalOutcome,
Optional<String> skillPath,
Optional<String> factsPrimitiveId,
StateBehavior behavior,
SourceLocation location) {
}
