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
import java.util.Optional;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * Reads the units of an instance version from the plan's store (PM-IMPL-5, Workflow DSL § Versions &amp; Composition
 * Events, item 1): the plan unit of {@code instance/plan.v<N>.pmwf}, the composed file of each phase sub-workflow and
 * bound axis variant, and the stored text {@code mcp/units/<digest>.pmwf} of each sub-unit the header binds by digest.
 * <p>
 * The class is the {@link InstanceUnits} port over a {@link PlanInstanceStore}, so the engine runs a plan from the
 * version in force and never from the level's copy of a template: an edit of a project's or the machine's unit does not
 * change a running call. A file that is absent or that the parser refuses reads as empty, so the engine fails closed
 * with the name it could not resolve.
 *
 * @since 0.1
 */
public final class PlanInstanceVersions implements InstanceUnits {

    private final PlanInstanceStore store;

    /**
     * @param store the store of the plan
     * @throws NullPointerException if the store is {@code null}
     */
    public PlanInstanceVersions(PlanInstanceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<WorkflowDefinition> planUnit(int version) {
        return store.readVersion(version)
                .flatMap(text -> parsed(PlanInstanceStore.versionPath(version), text));
    }

    @Override
    public Optional<WorkflowDefinition> composedUnit(int version, String unit) {
        return store.readUnit(version, unit)
                .flatMap(text -> parsed(PlanInstanceStore.unitPath(unit, version), text));
    }

    @Override
    public Optional<WorkflowDefinition> subUnit(int version, String unit) {
        return planUnit(version).flatMap(plan -> {
            var instance = plan.instance();
            if (instance == null) {
                return Optional.empty();
            }
            var header = HeaderJson.read(instance.json());
            return header.inputs().subUnits().stream()
                    .filter(reference -> reference.unit().equals(unit))
                    .findFirst()
                    .flatMap(reference -> subUnit(reference.digest()));
        });
    }

    /**
     * @param digest the SHA-256 of a stored sub-unit text
     * @return the tree of the text, empty when no version named the digest
     */
    Optional<WorkflowDefinition> subUnit(String digest) {
        return store.readSubUnit(digest).flatMap(text -> parsed(PlanInstanceStore.subUnitPath(digest), text));
    }

    /**
     * @param path the store path the text was read from, named by a refusal
     * @param text the DSL text of a unit
     * @return the tree, empty when the parser refuses the text
     */
    static Optional<WorkflowDefinition> parsed(String path, String text) {
        return WorkflowParser.parse(path, text, UnitOrigin.DEFAULT) instanceof ParseResult.Parsed(var definition)
                ? Optional.of(definition) : Optional.empty();
    }
}
