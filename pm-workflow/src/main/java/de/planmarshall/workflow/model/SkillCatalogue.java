/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

/**
 * The port through which a composition reads the project's skill catalogue (PM-SKILL-5, Workflow DSL § Static AST
 * Verification Rules, Rule 14).
 * <p>
 * A composition checks that every skill a {@code skill} clause names resolves: a compiled skill, or an extension skill
 * under {@code skill://pm/ext/} that is present and accepted at that read. The implementation belongs to the assembly
 * that owns the project's skill catalogue; no class of this package reads a file or a classpath of its own.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface SkillCatalogue {

    /**
     * @param uri a skill URI of a {@code skill} clause, {@code skill://pm/.../SKILL.md}
     * @return whether the project resolves the skill at this read
     */
    boolean resolves(String uri);
}
