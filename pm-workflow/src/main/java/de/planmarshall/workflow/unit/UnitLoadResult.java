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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The result of loading a set of units (PM-IMPL-5): the verified units by identifier when there is no finding,
 * otherwise every finding and no unit. A result with findings never carries a unit, so no default unit stands in
 * for a refused one; it returns the {@code config_invalid} refusal ({@link #refusal()}) that the assembly
 * ({@code pm-mcp-server} in plan-marshall-mcp) applies to the plan lifecycle at plan start and at resume.
 *
 * @param units    the verified units by identifier, in load order; empty when there is a finding
 * @param findings every finding, in the order the checks report them; empty when every unit verified
 * @since 0.1
 */
public record UnitLoadResult(Map<String, WorkflowDefinition> units, List<UnitFinding> findings) {

    /** The code of the refusal of the plan lifecycle when a unit is refused. */
    public static final String CONFIG_INVALID = "config_invalid";

    /**
     * Copies the units and findings and refuses a result with both.
     *
     * @param units    the verified units by identifier
     * @param findings every finding
     * @throws IllegalArgumentException if the result carries units and findings
     * @throws NullPointerException     if an argument or an element is {@code null}
     */
    public UnitLoadResult {
        units = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(units, "units")));
        findings = List.copyOf(findings);
        if (!units.isEmpty() && !findings.isEmpty()) {
            throw new IllegalArgumentException("a result with findings carries no unit");
        }
    }

    /**
     * @param units the verified units by identifier
     * @return the result of a load without findings
     */
    public static UnitLoadResult verified(Map<String, WorkflowDefinition> units) {
        return new UnitLoadResult(units, List.of());
    }

    /**
     * @param findings every finding; at least one
     * @return the result of a refused load
     * @throws IllegalArgumentException if there is no finding
     */
    public static UnitLoadResult refused(List<UnitFinding> findings) {
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("a refused load has at least one finding");
        }
        return new UnitLoadResult(Map.of(), findings);
    }

    /**
     * @return {@code true} when every unit verified
     */
    public boolean isVerified() {
        return findings.isEmpty();
    }

    /**
     * Returns the refusal the assembly applies to the plan lifecycle at plan start and at resume; this record only
     * carries it.
     *
     * @return the refusal {@value #CONFIG_INVALID} naming the file and line of the first finding; empty when every
     *         unit verified
     */
    public Optional<Refusal> refusal() {
        return findings.stream().findFirst()
                .map(first -> new Refusal(CONFIG_INVALID, first.file(), first.line(), first.message()));
    }

    /**
     * The refusal of the plan lifecycle by a refused unit.
     *
     * @param code    {@value UnitLoadResult#CONFIG_INVALID}
     * @param file    the file of the first finding
     * @param line    the line of the first finding
     * @param message the message of the first finding
     */
    public record Refusal(String code, String file, int line, String message) {
    }
}
