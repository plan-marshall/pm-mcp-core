/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The immutable tree the parser of the workflow language produces: the source location of a node, the scopes a unit
 * runs in ({@link de.planmarshall.workflow.ast.WorkflowScope}) and the closed link relations and link forms ({@link
 * de.planmarshall.workflow.ast.LinkRelation}, {@link de.planmarshall.workflow.ast.LinkForm}).
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/04-ast-node-model.adoc}.
 */
package de.planmarshall.workflow.ast;
