/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.role.EffectiveRole;

/**
 * The judge configuration a grading run scores its rubric answers with, and the corpus items that check it as an
 * instrument (Model Work § Grading; § Judge Instrument Check).
 * <p>
 * The judge is validated against the graded configuration: a judge whose model is the graded model, or its sibling at
 * the default configuration, refuses the grading with {@code scorer_not_independent}. The check items are the corpus
 * topic {@code judge/}, which grades no role.
 *
 * @param role     the effective configuration of the judge
 * @param siblings the models that are the graded model's siblings at the default configuration
 * @param items    the items of the judge instrument check
 * @since 0.1
 */
public record JudgeConfiguration(EffectiveRole role, Set<String> siblings, List<JudgeCheck.Item> items) {

    /**
     * @throws NullPointerException if a component is {@code null}
     */
    public JudgeConfiguration {
        Objects.requireNonNull(role, "role");
        siblings = Set.copyOf(Objects.requireNonNull(siblings, "siblings"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /**
     * @param graded the graded configuration
     * @return {@code true} if the judge's model is neither the graded model nor one of the graded model's declared
     *         siblings at the default configuration
     */
    public boolean independentOf(EffectiveRole graded) {
        return !role.model().equals(graded.model()) && !siblings.contains(role.model());
    }
}
