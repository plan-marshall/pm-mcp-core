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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The root of the tree: one complete definition unit, that is the plan unit that calls the phases, a plan instance, a
 * single-file
 * top-level workflow or one sub-workflow (PM-IMPL-5).
 * <p>
 * A unit without entries declares its starting point with {@code start <state>}; a unit with entries declares no start
 * and binds each entry to the state a call through it enters with {@code entry <name> -> <state>}. Which of the two a
 * unit must declare, and that the bindings match the entries its frontmatter declares, is checked by the verifier.
 * <p>
 * The lists and maps are copied into immutable collections; the maps keep the order in which their entries were
 * declared, so the printer emits them in source order.
 *
 * @param frontmatter   the frontmatter the unit opens with
 * @param id            the identifier of the unit
 * @param version       the revision of the unit, counted by its own header
 * @param scope         the scope the unit runs in
 * @param params        the parameter declarations; non-empty only for a {@link WorkflowScope#SUBWORKFLOW} unit
 * @param resultSchema  the named schema of the result record a sub-workflow returns with {@code done}
 * @param instance      the parsed {@code instance} header line of a plan instance version
 * @param declarations  the {@code declare} lines by name, each with its value and the line it was read at
 * @param decisions     the {@code decide} lines by decision kind; only in the unit {@code adjudicate.decisions}
 * @param formatVersion the DSL format every file of the unit declares
 * @param origin        whether the unit is compiled into the binary or owned by the project
 * @param derivedFrom   the default revision per file a project unit derives from; empty for a default unit
 * @param startState    the identifier of the start state; empty for a unit that binds entries instead
 * @param entryBindings the {@code entry <name> -> <state>} lines by entry name, in source order
 * @param migrations    the {@code migrate} declarations
 * @param states        the states by identifier
 * @param location      where the unit header was read
 * @since 0.1
 */
public record WorkflowDefinition(
UnitFrontmatter frontmatter,
String id,
int version,
WorkflowScope scope,
List<ParamDecl> params,
Optional<String> resultSchema,
Optional<InstanceHeader> instance,
Map<String, ValueDeclaration> declarations,
Map<String, DecisionDeclaration> decisions,
int formatVersion,
UnitOrigin origin,
Map<String, Integer> derivedFrom,
Optional<String> startState,
Map<String, EntryBinding> entryBindings,
List<StateMigration> migrations,
Map<String, StateNode> states,
SourceLocation location) {

    /**
     * Copies the lists into immutable lists and the maps into unmodifiable maps that keep declaration order.
     *
     * @param frontmatter   the frontmatter the unit opens with
     * @param id            the identifier of the unit
     * @param version       the revision of the unit
     * @param scope         the scope the unit runs in
     * @param params        the parameter declarations
     * @param resultSchema  the named schema of the result record
     * @param instance      the parsed instance header line
     * @param declarations  the {@code declare} lines by name, each with its value and line
     * @param decisions     the {@code decide} lines by decision kind
     * @param formatVersion the DSL format
     * @param origin        the origin of the unit
     * @param derivedFrom   the default revision per file
     * @param startState    the identifier of the start state, or empty
     * @param entryBindings the entry bindings by entry name
     * @param migrations    the {@code migrate} declarations
     * @param states        the states by identifier
     * @param location      where the unit header was read
     */
    public WorkflowDefinition {
        params = List.copyOf(params);
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
        decisions = Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
        derivedFrom = Collections.unmodifiableMap(new LinkedHashMap<>(derivedFrom));
        entryBindings = Collections.unmodifiableMap(new LinkedHashMap<>(entryBindings));
        migrations = List.copyOf(migrations);
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /**
     * The states control enters the unit at: the start state, followed by the state of every entry binding in source
     * order. A unit the verifier accepts has the one or the other, never both.
     *
     * @return the identifiers of the starting states, without duplicates, in that order
     */
    public List<String> startingStates() {
        return Stream.concat(startState.stream(), entryBindings.values().stream().map(EntryBinding::stateId))
                .distinct()
                .toList();
    }

    /**
     * {@code entry <name> -> <state_id>}: the state a call through the entry enters.
     *
     * @param stateId  the identifier of the bound state
     * @param location where the binding was read
     */
    public record EntryBinding(String stateId, SourceLocation location) {
    }
}
