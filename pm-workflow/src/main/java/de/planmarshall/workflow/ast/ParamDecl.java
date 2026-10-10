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
 * A sub-workflow parameter declared in the header ({@code param <name>: <type>}); it is always required, and its type
 * is an {@link ParamType.EnumOf}, an {@link ParamType.Int} or a {@link ParamType.ListOf} of an
 * {@link ParamType.EnumOf} (PM-IMPL-5, PM-WF-16).
 *
 * @param name     the name of the parameter
 * @param type     the type of the parameter
 * @param location where the declaration was read
 * @since 0.1
 */
public record ParamDecl(String name, ParamType type, SourceLocation location) {
}
