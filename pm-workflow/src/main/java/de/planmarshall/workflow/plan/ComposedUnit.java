/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.WorkflowPrinter;

/**
 * One composed file of a plan instance version (PM-IMPL-5, PM-WF-25): a phase sub-workflow or a bound axis
 * variant, as the version composes it, the content of {@code instance/<unit>.v<N>.pmwf}.
 * <p>
 * The text of the file keeps the frontmatter, id, type, entries and exits of its template, carries no {@code instance}
 * line, and repeats the version in its file name ({@link #file()}). Its digest is the SHA-256 of the text, the value
 * the version's header names it by, so a header digest that names no file and a file the header does not name are both
 * detectable.
 *
 * @param unit       the id of the composed unit
 * @param version    the instance version, from {@code 1}
 * @param definition the composed tree of the unit
 * @param text       the DSL text of the file, every line ended by a line feed
 * @param digest     the SHA-256 of {@code text}
 * @since 0.1
 */
public record ComposedUnit(String unit, int version, WorkflowDefinition definition, String text, String digest) {

    /**
     * Refuses a missing component, a unit id the tree does not carry, a version the tree disagrees with, and a digest
     * that is not the SHA-256 of the text.
     *
     * @param unit       the id of the composed unit
     * @param version    the instance version
     * @param definition the composed tree of the unit
     * @param text       the DSL text
     * @param digest     the SHA-256 of the text
     * @throws IllegalArgumentException if the digest is not the SHA-256 of the text, or the tree names another unit
     *                                  or version
     * @throws NullPointerException     if a component is {@code null}
     */
    public ComposedUnit {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(digest, "digest");
        if (!unit.equals(definition.id())) {
            throw new IllegalArgumentException("the composed unit '%s' carries the tree of '%s'"
                    .formatted(unit, definition.id()));
        }
        if (version != definition.version()) {
            throw new IllegalArgumentException("the composed unit '%s' names version %d, the tree %d"
                    .formatted(unit, version, definition.version()));
        }
        if (!digest.equals(PlanFacts.sha256(text))) {
            throw new IllegalArgumentException("the digest %s is not the SHA-256 of the text of '%s'"
                    .formatted(digest, unit));
        }
    }

    /**
     * @return the file of the unit, relative to the directory of the plan
     */
    public String file() {
        return PlanInstanceStore.unitPath(unit, version);
    }

    /**
     * @param unit       the id of the composed unit
     * @param definition the composed tree
     * @return the composed file over the printed text of the tree
     */
    public static ComposedUnit of(String unit, WorkflowDefinition definition) {
        var text = WorkflowPrinter.print(definition);
        return new ComposedUnit(unit, definition.version(), definition, text, PlanFacts.sha256(text));
    }
}
