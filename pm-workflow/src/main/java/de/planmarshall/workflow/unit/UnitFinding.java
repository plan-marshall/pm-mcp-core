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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * One finding that refuses a unit (PM-IMPL-5): what kind of check refused it, the code of the rule, invariant or
 * refusal, and the file and line of the offending node. A finding is a value, never an exception.
 *
 * @param kind    the kind of check that reports the finding
 * @param code    the code: the name of the parser refusal or verification rule, the wire id of the invariant,
 *                {@value #FORMAT_OUTDATED} or {@value #FORMAT_NEWER}, {@code declaration_invalid}, the name of the
 *                pipeline rule, {@value #PROJECT_STEP_UNBOUND}, {@value #UNIT_MISSING}, {@value #UNIT_UNREADABLE},
 *                {@value #UNIT_TOO_LARGE}, {@value #DUPLICATE_UNIT_ID}, {@value #AUTHOR_RESERVED},
 *                {@code path_traversal_rejected} or the code of {@code DUPLICATE_UNIT_ID}
 * @param file    the file the finding is located in, as the loader names it
 * @param line    the line, counted from 1; {@code 0} for a file that could not be read
 * @param message what is wrong
 * @since 0.1
 */
public record UnitFinding(Kind kind, String code, String file, int line, String message) {

    /** The code of a unit written for an older DSL format than the compiled one. */
    public static final String FORMAT_OUTDATED = "format_outdated";

    /** The code of a unit written for a newer DSL format than the compiled one. */
    public static final String FORMAT_NEWER = "format_newer";

    /** The code of a disagreement between a unit, the declared project steps and their bindings. */
    public static final String PROJECT_STEP_UNBOUND = "project_step_unbound";

    /** The code of a unit file that does not exist. */
    public static final String UNIT_MISSING = "unit_missing";

    /** The code of a unit file that exists but cannot be read. */
    public static final String UNIT_UNREADABLE = "unit_unreadable";

    /** The code of a unit file above the size bound, which is refused before it is read whole. */
    public static final String UNIT_TOO_LARGE = "unit_too_large";

    /**
     * The code of a unit whose identifier an earlier file of the load already declares, or that declares the identifier
     * {@value Phase#PLAN_UNIT} of the plan unit; the later unit never replaces the earlier one.
     */
    public static final String DUPLICATE_UNIT_ID = "duplicate_unit_id";

    /**
     * The code of a project unit whose frontmatter names the author {@value ProjectUnitLoader#RESERVED_AUTHOR}, which
     * only a unit compiled into the binary carries: a unit placed in a project never passes as a delivered one.
     */
    public static final String AUTHOR_RESERVED = "author_reserved";

    private static final String DUPLICATE_MESSAGE = "the unit '%s' is declared by '%s' and again by '%s'";

    /**
     * Refuses a missing component.
     *
     * @param kind    the kind of check
     * @param code    the code
     * @param file    the file
     * @param line    the line
     * @param message what is wrong
     * @throws NullPointerException if a component is {@code null}
     */
    public UnitFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(message, "message");
    }

    /**
     * @param kind     the kind of check
     * @param code     the code
     * @param location the file and line of the offending node
     * @param message  what is wrong
     * @return the finding at the location
     */
    static UnitFinding at(Kind kind, String code, SourceLocation location, String message) {
        return new UnitFinding(kind, code, location.resourceName(), location.line(), message);
    }

    /**
     * Adds a parsed unit to the units of a load, keyed by its identifier, unless the identifier is taken: a unit that
     * declares the identifier of an earlier unit is a {@value #DUPLICATE_UNIT_ID} finding at its header and is left
     * out, and so is a file other than {@code planFile} that declares {@value Phase#PLAN_UNIT}. The file the plan unit
     * is located in may declare that identifier; any other file may not.
     *
     * @param units    the units of the load by identifier
     * @param unit     the parsed unit
     * @param planFile the file the plan unit is located in, as the loader names it
     * @param findings the findings of the load
     */
    static void putUnique(Map<String, WorkflowDefinition> units, WorkflowDefinition unit, String planFile,
            List<UnitFinding> findings) {
        var file = unit.location().resourceName();
        if (Phase.PLAN_UNIT.equals(unit.id())) {
            if (planFile.equals(file)) {
                units.put(unit.id(), unit);
                return;
            }
            findings.add(at(Kind.RULE, DUPLICATE_UNIT_ID, unit.location(), DUPLICATE_MESSAGE.formatted(unit.id(),
                    planFile, file)));
            return;
        }
        var earlier = units.putIfAbsent(unit.id(), unit);
        if (earlier != null) {
            findings.add(at(Kind.RULE, DUPLICATE_UNIT_ID, unit.location(), DUPLICATE_MESSAGE.formatted(unit.id(),
                    earlier.location().resourceName(), file)));
        }
    }

    /** The closed set of the kinds of check that refuse a unit. */
    public enum Kind {
        /** The parser refused the file. */
        PARSE,
        /** The file declares another DSL format than the compiled one, or the parts of the plan unit disagree. */
        FORMAT,
        /** The file does not exist or cannot be read. */
        MISSING,
        /** A static verification rule (Rules 1-12) or a rule of the merge of the plan unit is violated. */
        RULE,
        /** A compiled invariant is violated. */
        INVARIANT,
        /** A declaration lies outside its compiled bounds. */
        DECLARATION,
        /** The finalize pipeline validator refuses the project's roster. */
        PIPELINE,
        /** The unit, the declared project steps and their bindings disagree. */
        PROJECT_STEP_UNBOUND,
        /** The path of the file lies outside the workspace. */
        CONFINEMENT
    }
}
