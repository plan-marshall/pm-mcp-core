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

/**
 * The root of the tree: one complete definition unit, that is the merged plan workflow, a plan instance, a single-file
 * top-level workflow or one sub-workflow (PM-IMPL-5).
 * <p>
 * The lists and maps are copied into immutable collections; the maps keep the order in which their entries were
 * declared, so the printer emits them in source order.
 *
 * @param id            the identifier of the unit
 * @param version       the revision of the unit; of the merged plan unit the sum of its part versions
 * @param scope         the scope the unit runs in
 * @param params        the parameter declarations; non-empty only for a {@link WorkflowScope#SUBWORKFLOW} unit
 * @param resultSchema  the named schema of the result record a sub-workflow returns with {@code done}
 * @param instance      the parsed {@code instance} header line of a plan instance version
 * @param declarations  the {@code declare} lines by name
 * @param decisions     the {@code decide} lines by decision kind; only in the unit {@code adjudicate.decisions}
 * @param partVersions  the version of every part file of the merged plan unit; empty otherwise
 * @param formatVersion the DSL format every file of the unit declares
 * @param origin        whether the unit is compiled into the binary or owned by the project
 * @param derivedFrom   the default revision per file a project unit derives from; empty for a default unit
 * @param startState    the identifier of the start state
 * @param migrations    the {@code migrate} declarations
 * @param states        the states by identifier
 * @param location      where the unit header was read
 * @since 0.1
 */
public record WorkflowDefinition(
String id,
int version,
WorkflowScope scope,
List<ParamDecl> params,
Optional<String> resultSchema,
Optional<InstanceHeader> instance,
Map<String, CallValue> declarations,
Map<String, DecisionDeclaration> decisions,
Map<String, Integer> partVersions,
int formatVersion,
UnitOrigin origin,
Map<String, Integer> derivedFrom,
String startState,
List<StateMigration> migrations,
Map<String, StateNode> states,
SourceLocation location) {

    /**
     * Copies the lists into immutable lists and the maps into unmodifiable maps that keep declaration order.
     *
     * @param id            the identifier of the unit
     * @param version       the revision of the unit
     * @param scope         the scope the unit runs in
     * @param params        the parameter declarations
     * @param resultSchema  the named schema of the result record
     * @param instance      the parsed instance header line
     * @param declarations  the {@code declare} lines by name
     * @param decisions     the {@code decide} lines by decision kind
     * @param partVersions  the version of every part file
     * @param formatVersion the DSL format
     * @param origin        the origin of the unit
     * @param derivedFrom   the default revision per file
     * @param startState    the identifier of the start state
     * @param migrations    the {@code migrate} declarations
     * @param states        the states by identifier
     * @param location      where the unit header was read
     */
    public WorkflowDefinition {
        params = List.copyOf(params);
        declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
        decisions = Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
        partVersions = Collections.unmodifiableMap(new LinkedHashMap<>(partVersions));
        derivedFrom = Collections.unmodifiableMap(new LinkedHashMap<>(derivedFrom));
        migrations = List.copyOf(migrations);
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }
}
