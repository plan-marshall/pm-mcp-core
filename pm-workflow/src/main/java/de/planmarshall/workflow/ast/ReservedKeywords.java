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

import java.util.Set;

import lombok.experimental.UtilityClass;

/**
 * The reserved keywords of the workflow DSL: the one set the parser and the verifier read.
 * <p>
 * A reserved keyword may be no outcome name, parameter name, job entity kind, role name, or segment of a state,
 * primitive or configuration-key identifier. The set is the specification's
 * (link:../../../../doc/specification/workflow-dsl/01-grammar.adoc#dsl-reserved-keywords[Workflow DSL § Reserved
 * Keywords]): a word the grammar leaves positional — {@code decide}, {@code phase}, {@code inherit}, {@code entry},
 * {@code quorum} — is not reserved and stays usable as an identifier segment. Two sets drifted apart once; one holder
 * removes the drift (link:../adr/workflow-verify/041-the-reserved-words-are-one-set.adoc[ADR workflow-verify/041]).
 *
 * @since 0.1
 */
@UtilityClass
public class ReservedKeywords {

    /** Every reserved keyword of the grammar. */
    public static final Set<String> ALL = Set.of("workflow", "version", "format", "derived_from",
            "scope", "param", "result", "declare", "start", "migrate", "state", "terminal",
            "skill", "facts", "auto", "job", "composite_job", "call", "question",
            "link", "guard", "depends_on_config", "do", "llm", "role", "each", "consult",
            "completes", "as", "and", "or", "not", "in_worktree", "has_open_findings", "all_tasks_completed",
            "predicate", "text", "int", "enum", "schema", "list",
            "slot", "fills", "from");
}
