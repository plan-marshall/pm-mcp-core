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
 * The closed set of rules the parser refuses a unit by (PM-IMPL-5). A refusal names exactly one of them in its
 * {@link Diagnostic}.
 *
 * @since 0.1
 */
public enum RefusalRule {
    /**
     * The frontmatter is missing or does not follow its closed format or its field rules (the unit frontmatter of the
     * grammar), as {@link UnitFrontmatterReader} reads it.
     */
    FRONTMATTER,
    /** The source does not follow the grammar: an unexpected, missing or unknown token or statement. */
    STRUCTURE,
    /**
     * A reserved keyword stands where the grammar expects an outcome name, a parameter name, an entity kind, a role
     * name, or a segment of a state, primitive or configuration key identifier.
     */
    RESERVED_KEYWORD,
    /** The unit declares no {@code scope} clause. */
    MISSING_SCOPE,
    /**
     * An identifier is declared twice in the file: a state, a {@code declare} name, a decision kind, a parameter, or an
     * entry bound twice by {@code entry <name> -> <state>}.
     */
    DUPLICATE_ID,
    /** The {@code instance} line is no JSON object, or it lacks a member the instance header requires. */
    INSTANCE_HEADER
}
