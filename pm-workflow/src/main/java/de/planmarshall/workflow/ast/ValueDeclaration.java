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
 * A {@code declare <name> = <value>} line of a unit: the declared value and where the line was read (PM-IMPL-5). The
 * name is the key under which {@link WorkflowDefinition#declarations()} holds it.
 *
 * @param value    the declared value
 * @param location where the {@code declare} line was read
 * @since 0.1
 */
public record ValueDeclaration(CallValue value, SourceLocation location) {
}
