/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;
import de.planmarshall.workflow.verify.VerificationRule;

/**
 * Merges the seven phase files of the plan workflow into the one unit {@value #PLAN_UNIT} (PM-IMPL-5).
 * <p>
 * The states and migrations of the merged unit are the union of those of its parts; its optional start state, entry
 * bindings, header location, scope, format and origin are those of the first part, the init file, and so is its
 * frontmatter, with the type {@code workflow}; its version is the sum of the part versions,
 * each recorded in {@link WorkflowDefinition#partVersions()} under the file it was read from; and
 * {@link WorkflowDefinition#derivedFrom()} holds the default revision every project file derives from, by file.
 * <p>
 * Every part must declare scope {@code plan}; its header names the part, for example {@code workflow finalize}, and
 * identifies the file only, so the merged unit takes the identifier {@value #PLAN_UNIT} whatever its parts are named.
 * The parts must declare one DSL format and one origin, so a project never mixes a default file into its own plan
 * unit; and a state is declared in one part only. A part carries no {@code declare} line, no {@code decide} line and no
 * {@code instance} line: the merged unit has none of them, so such a line would have no effect and is refused
 * ({@value #DECLARATION_IN_PLAN_PART}) instead of being dropped; the finding of an {@code instance} line names the
 * header line it directly follows, since the instance header carries no location of its own. Every violation is a {@link UnitFinding} with file and
 * line, and a merge with findings yields no unit.
 *
 * @since 0.1
 */
public final class PlanUnitMerger {

    /** The identifier of the merged plan unit. */
    public static final String PLAN_UNIT = "plan";

    /** The phase files of the plan unit, in merge order. */
    public static final List<String> PHASE_FILES = List.of("init.pmwf", "refine.pmwf", "outline.pmwf", "plan.pmwf",
            "execute.pmwf", "land.pmwf", "finalize.pmwf");

    /** The code of a part that is no part of the plan unit: a file of another scope than {@code plan}. */
    public static final String NOT_A_PLAN_PART = "not_a_plan_part";

    /** The code of a part whose DSL format differs from that of the first part. */
    public static final String MIXED_FORMATS = "mixed_formats";

    /** The code of a part whose origin differs from that of the first part. */
    public static final String MIXED_ORIGINS = "mixed_origins";

    /**
     * The code of a {@code declare}, {@code decide} or {@code instance} line in a part: the merged plan unit carries
     * none of them, so the line would have no effect.
     */
    public static final String DECLARATION_IN_PLAN_PART = "declaration_in_plan_part";

    private PlanUnitMerger() {
    }

    /**
     * Merges the parsed phase files.
     *
     * @param parts the parsed phase files, in merge order; the first is the init file
     * @return the merged unit, or every finding
     * @throws IllegalArgumentException if there is no part
     * @throws NullPointerException     if the list or an element is {@code null}
     */
    public static Merge merge(List<WorkflowDefinition> parts) {
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("the plan unit has at least one part");
        }
        var first = Objects.requireNonNull(parts.getFirst(), "part");
        var findings = new ArrayList<UnitFinding>();
        var states = new LinkedHashMap<String, StateNode>();
        var migrations = new ArrayList<StateMigration>();
        var partVersions = new LinkedHashMap<String, Integer>();
        var derivedFrom = new LinkedHashMap<String, Integer>();
        var version = 0;
        for (var part : parts) {
            Objects.requireNonNull(part, "part");
            checkPart(first, part, findings);
            var file = part.location().resourceName();
            for (var state : part.states().values()) {
                if (states.putIfAbsent(state.id(), state) != null) {
                    findings.add(UnitFinding.at(UnitFinding.Kind.RULE, VerificationRule.STATE_ID_UNIQUE.name(),
                            state.location(), "the state '%s' is declared in two phase files, last in '%s'"
                            .formatted(state.id(), file)));
                }
            }
            migrations.addAll(part.migrations());
            partVersions.put(file, part.version());
            part.derivedFrom().values().forEach(revision -> derivedFrom.put(file, revision));
            version += part.version();
        }
        if (!findings.isEmpty()) {
            return new Merge(Optional.empty(), findings);
        }
        return new Merge(Optional.of(new WorkflowDefinition(workflowFrontmatter(first.frontmatter()), PLAN_UNIT,
                version, WorkflowScope.PLAN, List.of(), Optional.empty(), Optional.empty(), Map.of(), Map.of(),
                partVersions, first.formatVersion(), first.origin(), derivedFrom, first.startState(),
                first.entryBindings(), migrations, states, first.location())), List.of());
    }

    /** The frontmatter of the merged unit: the first part's, with the type {@link UnitType#WORKFLOW}. */
    private static UnitFrontmatter workflowFrontmatter(UnitFrontmatter first) {
        return new UnitFrontmatter(first.name(), first.description(), UnitType.WORKFLOW, first.entries(),
                first.exits(), first.modificationDate(), first.version(), first.author(), first.signature(),
                first.location());
    }

    private static void checkPart(WorkflowDefinition first, WorkflowDefinition part, List<UnitFinding> findings) {
        var file = part.location().resourceName();
        if (part.scope() != WorkflowScope.PLAN) {
            findings.add(UnitFinding.at(UnitFinding.Kind.RULE, NOT_A_PLAN_PART, part.location(),
                    "'%s' declares the unit '%s' of scope %s, not a part of the unit '%s' of scope PLAN"
                            .formatted(file, part.id(), part.scope(), PLAN_UNIT)));
        }
        if (part.formatVersion() != first.formatVersion()) {
            findings.add(UnitFinding.at(UnitFinding.Kind.FORMAT, MIXED_FORMATS, part.location(),
                    "'%s' declares format %d, '%s' format %d".formatted(file, part.formatVersion(),
                            first.location().resourceName(), first.formatVersion())));
        }
        if (part.origin() != first.origin()) {
            findings.add(UnitFinding.at(UnitFinding.Kind.RULE, MIXED_ORIGINS, part.location(),
                    "'%s' is a %s file, '%s' a %s file; a plan unit never mixes them".formatted(file, part.origin(),
                            first.location().resourceName(), first.origin())));
        }
        part.declarations().forEach((name, declaration) -> findings.add(UnitFinding.at(UnitFinding.Kind.RULE,
                DECLARATION_IN_PLAN_PART, declaration.location(),
                "'%s' declares '%s'; a part of the unit '%s' carries no declare line".formatted(file, name,
                        PLAN_UNIT))));
        part.decisions().forEach((kind, decision) -> findings.add(UnitFinding.at(UnitFinding.Kind.RULE,
                DECLARATION_IN_PLAN_PART, decision.location(),
                "'%s' decides '%s'; a part of the unit '%s' carries no decide line".formatted(file, kind,
                        PLAN_UNIT))));
        // the instance header carries no location of its own; the finding names the header line it directly follows
        part.instance().ifPresent(_ -> findings.add(UnitFinding.at(UnitFinding.Kind.RULE, DECLARATION_IN_PLAN_PART,
                part.location(), "'%s' carries an instance line after its header; a part of the unit '%s' carries none"
                .formatted(file, PLAN_UNIT))));
    }

    /**
     * The result of a merge.
     *
     * @param unit     the merged plan unit; empty when there is a finding
     * @param findings every finding; empty when the parts merged
     */
    public record Merge(Optional<WorkflowDefinition> unit, List<UnitFinding> findings) {

        /**
         * Copies the findings.
         *
         * @param unit     the merged plan unit
         * @param findings every finding
         */
        public Merge {
            Objects.requireNonNull(unit, "unit");
            findings = List.copyOf(findings);
        }
    }
}
