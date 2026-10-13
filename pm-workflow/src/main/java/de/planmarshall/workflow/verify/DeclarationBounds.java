/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.DecisionDeclaration;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The compiled declaration schemas of the units that take a decision with declared parameters, and the check of a
 * unit's {@code declare} and {@code decide} lines against them (PM-IMPL-5, PM-WF-17, PM-WF-18).
 * <p>
 * A schema is the closed set of the names a unit declares, each with its type, its bounds and its shipped value. The
 * schemas exist for {@value #ADJUDICATION_UNIT} and {@value #REPLAN_UNIT}; a unit declares every name of its schema
 * exactly once, and a unit without a schema declares nothing. The bounds admit the shipped value and stricter values
 * only. The unit {@value #ADJUDICATION_UNIT} alone also carries the {@code decide} lines: each of the closed decision
 * kinds ({@link DecisionDeclaration#DECISION_KINDS}, the one set the parser reads as well) at most once, {@code ask} or a quorum of {@value #MIN_QUORUM_ROLES} to {@value #MAX_QUORUM_ROLES} distinct
 * roles with a score of {@value #MIN_QUORUM_SCORE} to {@value #MAX_QUORUM_SCORE}.
 * <p>
 * A missing, unknown or ill-typed declaration, a value outside its bounds, a declaration in a unit without a schema,
 * a {@code decide} line outside {@value #ADJUDICATION_UNIT}, and a quorum outside its bounds each yield a
 * {@link Finding} with the code {@value #DECLARATION_INVALID}, the file, the line and the bound. A finding names the
 * line of the offending {@code declare} or {@code decide} line; a missing declaration, which has no line, names the
 * line of the unit header. The parser already refuses a name or a decision kind declared twice and a decision kind
 * outside the closed set. The check lives beside the invariants because {@link InvariantId#INV_ADJ_1} and
 * {@link InvariantId#INV_REPLAN_1} use it, and the loader of the project units calls it.
 *
 * @since 0.1
 */
public final class DeclarationBounds {

    /** The code of every finding of this check. */
    public static final String DECLARATION_INVALID = "declaration_invalid";

    /** The adjudication unit, which carries the decision declarations. */
    public static final String ADJUDICATION_UNIT = "adjudicate.decisions";

    /** The re-planning unit. */
    public static final String REPLAN_UNIT = "replan";

    /** The fewest roles of a quorum. */
    public static final int MIN_QUORUM_ROLES = 1;

    /** The most roles of a quorum. */
    public static final int MAX_QUORUM_ROLES = 3;

    /** The lowest score of a quorum. */
    public static final int MIN_QUORUM_SCORE = 50;

    /** The highest score of a quorum. */
    public static final int MAX_QUORUM_SCORE = 100;

    private static final String QUORUM_BOUND = "quorum of %d to %d distinct roles with a score of %d to %d"
            .formatted(MIN_QUORUM_ROLES, MAX_QUORUM_ROLES, MIN_QUORUM_SCORE, MAX_QUORUM_SCORE);

    private static final Map<String, List<Declaration>> SCHEMAS = Map.of(
            ADJUDICATION_UNIT, List.of(
                    new Declaration("vote_wait_seconds", new IntRange(60, 1800), new CallValue.IntValue(900)),
                    new Declaration("max_false_auto_percent", new IntRange(0, 10), new CallValue.IntValue(5)),
                    new Declaration("kind_recipe_candidate", new DecisionKind(), new CallValue.EnumValue("approach")),
                    new Declaration("kind_sibling_collision", new DecisionKind(), new CallValue.EnumValue("scope")),
                    new Declaration("kind_domain_choice", new DecisionKind(), new CallValue.EnumValue("placement")),
                    new Declaration("kind_outline_review", new DecisionKind(), new CallValue.EnumValue("scope"))),
            REPLAN_UNIT, List.of(
                    new Declaration("replan_budget", new IntRange(0, 5), new CallValue.IntValue(3)),
                    new Declaration("base_overlap_min_files", new IntRange(1, 50), new CallValue.IntValue(5)),
                    new Declaration("conflict_min_files", new IntRange(1, 50), new CallValue.IntValue(10)),
                    new Declaration("prerequisite_wait_seconds", new IntRange(3600, 604800),
                            new CallValue.IntValue(604800))));

    private DeclarationBounds() {
    }

    /**
     * @param unitId the identifier of a unit
     * @return the declarations of the unit's compiled schema in schema order; empty when the unit has no schema
     */
    public static Optional<List<Declaration>> schema(String unitId) {
        return Optional.ofNullable(SCHEMAS.get(unitId));
    }

    /**
     * Checks the {@code declare} and {@code decide} lines of a unit against the compiled schema of its identifier.
     *
     * @param unit the unit
     * @return every finding, the {@code declare} lines first in schema order, then the {@code decide} lines in
     *         declaration order; empty when the unit's declarations lie within their bounds
     * @throws NullPointerException if the unit is {@code null}
     */
    public static List<Finding> check(WorkflowDefinition unit) {
        Objects.requireNonNull(unit, "unit");
        var findings = new ArrayList<Finding>();
        var schema = SCHEMAS.get(unit.id());
        if (schema == null) {
            unit.declarations().forEach((name, declaration) -> findings.add(new Finding(name,
                    declaration.location(), "no declaration schema",
                    "the unit '%s' has no declaration schema, so it declares no '%s'".formatted(unit.id(), name))));
        } else {
            checkDeclarations(unit, schema, findings);
        }
        checkDecisions(unit, findings);
        return List.copyOf(findings);
    }

    private static void checkDeclarations(WorkflowDefinition unit, List<Declaration> schema, List<Finding> findings) {
        var known = new HashSet<String>();
        for (var declaration : schema) {
            known.add(declaration.name());
            var bound = declaration.bound().describe();
            var declared = unit.declarations().get(declaration.name());
            if (declared == null) {
                findings.add(new Finding(declaration.name(), unit.location(), bound,
                        "the unit '%s' does not declare '%s'".formatted(unit.id(), declaration.name())));
            } else if (!declaration.bound().admits(declared.value())) {
                findings.add(new Finding(declaration.name(), declared.location(), bound,
                        "the value %s of '%s' lies outside %s".formatted(describe(declared.value()),
                                declaration.name(), bound)));
            }
        }
        unit.declarations().forEach((name, declared) -> {
            if (!known.contains(name)) {
                findings.add(new Finding(name, declared.location(), "the schema of '%s'".formatted(unit.id()),
                        "'%s' is not a declaration of the schema of '%s'".formatted(name, unit.id())));
            }
        });
    }

    private static void checkDecisions(WorkflowDefinition unit, List<Finding> findings) {
        for (var entry : unit.decisions().entrySet()) {
            var location = entry.getValue().location();
            var name = "decide " + entry.getKey();
            if (!ADJUDICATION_UNIT.equals(unit.id())) {
                findings.add(new Finding(name, location, "decide lines only in " + ADJUDICATION_UNIT,
                        "the unit '%s' carries the decision declaration '%s'".formatted(unit.id(), entry.getKey())));
                continue;
            }
            if (!DecisionDeclaration.DECISION_KINDS.contains(entry.getKey())) {
                findings.add(new Finding(name, location, "a decision kind of " + DecisionDeclaration.DECISION_KINDS,
                        "'%s' is not a decision kind".formatted(entry.getKey())));
            }
            if (entry.getValue() instanceof DecisionDeclaration.Quorum quorum) {
                checkQuorum(name, location, quorum, findings);
            }
        }
    }

    private static void checkQuorum(String name, SourceLocation location, DecisionDeclaration.Quorum quorum,
            List<Finding> findings) {
        var roles = quorum.roles();
        if (roles.size() < MIN_QUORUM_ROLES || roles.size() > MAX_QUORUM_ROLES) {
            findings.add(new Finding(name, location, QUORUM_BOUND,
                    "the quorum lists %d roles".formatted(roles.size())));
        }
        if (new HashSet<>(roles).size() != roles.size()) {
            findings.add(new Finding(name, location, QUORUM_BOUND, "the quorum lists a role twice: " + roles));
        }
        if (quorum.score() < MIN_QUORUM_SCORE || quorum.score() > MAX_QUORUM_SCORE) {
            findings.add(new Finding(name, location, QUORUM_BOUND,
                    "the quorum's score %d lies outside %d to %d".formatted(quorum.score(), MIN_QUORUM_SCORE,
                            MAX_QUORUM_SCORE)));
        }
    }

    private static String describe(CallValue value) {
        return switch (value) {
            case CallValue.IntValue intValue -> Integer.toString(intValue.value());
            case CallValue.EnumValue enumValue -> "'%s'".formatted(enumValue.value());
            case CallValue.EnumList list -> list.values().toString();
        };
    }

    /**
     * The type and the bounds of a declared value.
     */
    public sealed interface Bound permits IntRange, DecisionKind {

        /**
         * @param value a declared value
         * @return {@code true} when the value has the bound's type and lies within it
         */
        boolean admits(CallValue value);

        /**
         * @return the bound as it is named in a finding, for example {@code int[60..1800]}
         */
        String describe();
    }

    /**
     * An integer from {@code min} to {@code max}, both included.
     *
     * @param min the least value
     * @param max the greatest value
     */
    public record IntRange(int min, int max) implements Bound {

        @Override
        public boolean admits(CallValue value) {
            return value instanceof CallValue.IntValue(var number) && number >= min && number <= max;
        }

        @Override
        public String describe() {
            return "int[%d..%d]".formatted(min, max);
        }
    }

    /**
     * One of the closed decision kinds ({@link DecisionDeclaration#DECISION_KINDS}), the set the parser admits on a
     * {@code decide} line.
     */
    public record DecisionKind() implements Bound {

        @Override
        public boolean admits(CallValue value) {
            return value instanceof CallValue.EnumValue(var kind) && DecisionDeclaration.DECISION_KINDS.contains(kind);
        }

        @Override
        public String describe() {
            return "enum[%s]".formatted(String.join("|", DecisionDeclaration.DECISION_KINDS));
        }
    }

    /**
     * One declaration of a schema.
     *
     * @param name    the name the unit declares
     * @param bound   its type and bounds
     * @param shipped the value of the default unit
     */
    public record Declaration(String name, Bound bound, CallValue shipped) {

        /**
         * Refuses a missing component.
         *
         * @param name    the name the unit declares
         * @param bound   its type and bounds
         * @param shipped the value of the default unit
         * @throws NullPointerException if a component is {@code null}
         */
        public Declaration {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(bound, "bound");
            Objects.requireNonNull(shipped, "shipped");
        }
    }

    /**
     * One finding {@value DeclarationBounds#DECLARATION_INVALID}, a value and never an exception.
     *
     * @param name     the declared name, or {@code decide <kind>} for a decision declaration
     * @param location the file and the line of the offending {@code declare} or {@code decide} line; of the unit
     *                 header for a missing declaration
     * @param bound    the bound the declaration violates
     * @param message  what is wrong
     */
    public record Finding(String name, SourceLocation location, String bound, String message) {

        /**
         * Refuses a missing component.
         *
         * @param name     the declared name
         * @param location the file and the line
         * @param bound    the bound
         * @param message  what is wrong
         * @throws NullPointerException if a component is {@code null}
         */
        public Finding {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(bound, "bound");
            Objects.requireNonNull(message, "message");
        }

        /**
         * @return {@value DeclarationBounds#DECLARATION_INVALID}, the code of every finding of the check
         */
        public String code() {
            return DECLARATION_INVALID;
        }
    }
}
