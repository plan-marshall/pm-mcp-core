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

import java.util.Objects;

import de.planmarshall.workflow.ast.SourceLocation;

/**
 * One finding that refuses a unit (PM-IMPL-5): what kind of check refused it, the code of the rule, invariant or
 * refusal, and the file and line of the offending node. A finding is a value, never an exception.
 *
 * @param kind    the kind of check that reports the finding
 * @param code    the code: the name of the parser refusal or verification rule, the wire id of the invariant,
 *                {@value #FORMAT_OUTDATED} or {@value #FORMAT_NEWER}, {@code declaration_invalid}, the name of the
 *                pipeline rule, {@value #PROJECT_STEP_UNBOUND}, {@value #UNIT_MISSING}, {@value #UNIT_UNREADABLE}
 *                or {@code path_traversal_rejected}
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
