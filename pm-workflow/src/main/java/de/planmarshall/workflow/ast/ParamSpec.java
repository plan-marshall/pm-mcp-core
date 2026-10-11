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
 * A free parameter of a link ({@code param_decl}) (PM-IMPL-5).
 *
 * @param name     the name of the parameter
 * @param type     the type of the parameter
 * @param optional whether the parameter may be omitted
 * @param location where the parameter was read
 * @since 0.1
 */
public record ParamSpec(String name, ParamType type, boolean optional, SourceLocation location) {
}
