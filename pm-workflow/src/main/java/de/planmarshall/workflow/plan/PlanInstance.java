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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One version of a plan instance (PM-IMPL-5): its number, its header, its plan unit and the set of composed files the
 * version writes, the content of {@code instance/plan.v<N>.pmwf} and of {@code instance/<unit>.v<N>.pmwf} per composed
 * phase sub-workflow and bound axis variant.
 * <p>
 * The plan unit's text is the printed tree: the {@code workflow plan version <N>} header, the {@code instance} line
 * with the canonical header JSON, and the composed states. The composed units are the files the header's
 * {@code inputs.templates} names, each with the digest of its text; the version is always referred to by the plan
 * unit's file.
 * <p>
 * The composer returns only versions whose plan unit is verified: Rules 1-12 over a catalog of the compiled units and
 * the request's sub-units, Rules 13 and 14 over the effective role set and the project's skill catalogue, the finalize
 * pipeline over the kept steps, the compiled invariants over the call-resolved graph and the header schema. The composed
 * files are the pruned bodies of those sub-units; the composer does not run the verifier over each of them on its own,
 * and ADR workflow-plan 001's guarantee is scoped to the plan unit. A version the composer returned carries the
 * {@link Verified} mark, and {@link PlanInstanceStore} writes no version without one: the constructor checks that the
 * version, the header and the unit agree on the version number and nothing more, so a value built outside the composer
 * carries no mark and is refused where it would be stored.
 *
 * @param version  the instance version, from {@code 1}
 * @param header   the instance header
 * @param unit     the composed plan unit, its {@code instance} header included
 * @param text     the DSL text of the plan unit, every line ended by a line feed
 * @param units    the composed phase sub-workflows and bound axis variants of the version, in header order
 * @param verified the mark of the composer; {@code null} for a version the composer did not return
 * @since 0.1
 */
@Builder
public record PlanInstance(int version, PlanInstanceHeader header, WorkflowDefinition unit, String text,
List<ComposedUnit> units, @Nullable Verified verified) {

    /**
     * Refuses a missing component and a version, header or unit that disagree on the version number.
     *
     * @param version the instance version
     * @param header  the instance header
     * @param unit    the composed plan unit
     * @param text    the DSL text of the plan unit
     * @param units   the composed units of the version
     * @throws IllegalArgumentException if the header or the unit names another version, or a composed unit does
     */
    public PlanInstance {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(text, "text");
        units = List.copyOf(units);
        if (header.version() != version || unit.version() != version) {
            throw new IllegalArgumentException("the version %d disagrees with the header (%d) or the unit (%d)"
                    .formatted(version, header.version(), unit.version()));
        }
        units.stream().filter(composed -> composed.version() != version).findFirst().ifPresent(composed -> {
            throw new IllegalArgumentException("the composed unit '%s' names version %d, the instance %d"
                    .formatted(composed.unit(), composed.version(), version));
        });
    }

    /**
     * @param unitId a composed unit id
     * @return the composed file of the unit, if the version composes it
     */
    public Optional<ComposedUnit> composed(String unitId) {
        return units.stream().filter(composed -> composed.unit().equals(unitId)).findFirst();
    }
}
