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

import static de.planmarshall.workflow.log.WorkflowLogMessages.ERROR;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * Loads the default units, the project-ownable units compiled into the release, from a classpath root (PM-IMPL-5).
 * <p>
 * The root is a constructor parameter: the assembly passes {@code workflows/}, tests pass a root of their own, so no
 * test places a fixture where the product's units live. This module carries no unit. The loader reads every named
 * file through the given class loader, parses it as a {@link UnitOrigin#DEFAULT} unit and verifies every unit with
 * Rules 1-12, Rule 5 against the released state names included. The plan unit is the unit {@code plan}; the six phase
 * sub-workflows it calls (PM-WF-16) are ordinary sub-workflow units beside it, each read on its own — no part is
 * merged into another. A file that declares the identifier of an earlier file, or {@value Phase#PLAN_UNIT}, never
 * replaces the earlier unit. A default unit that is missing, refused by the parser or by a rule, or whose identifier
 * is taken, is a {@link UnitFinding}, logged as ERROR {@code DEFAULT_UNIT_REFUSED}: a default unit that does not
 * verify is a defect of the build.
 *
 * @since 0.1
 */
public final class DefaultUnitLoader {

    /** The identifier of the plan unit, which the result carries first. */
    static final String PLAN_FILE = "plan.pmwf";

    private static final CuiLogger LOGGER = new CuiLogger(DefaultUnitLoader.class);

    private final ClassLoader classLoader;

    private final String root;

    private final List<String> unitFiles;

    private final VerificationContext context;

    private final Map<String, List<String>> releasedStates;

    /**
     * @param classLoader    the class loader the units are read through
     * @param root           the classpath root of the units, for example {@code workflows/}
     * @param unitFiles      the names of the project-ownable unit files below the root; the plan unit is
     *                       {@value #PLAN_FILE}, its phase sub-workflows are further units of the list
     * @param context        the inputs of the static verifier
     * @param releasedStates the state names of earlier releases by unit identifier (Rule 5); a unit without an entry
     *                       has none
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    public DefaultUnitLoader(ClassLoader classLoader, String root, List<String> unitFiles,
            VerificationContext context, Map<String, List<String>> releasedStates) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.root = Objects.requireNonNull(root, "root");
        this.unitFiles = List.copyOf(unitFiles);
        this.context = Objects.requireNonNull(context, "context");
        this.releasedStates = Map.copyOf(releasedStates);
    }

    /**
     * Loads and verifies the default units.
     *
     * @return the verified units by identifier, the plan unit first when the files hold one, or every finding
     */
    public UnitLoadResult load() {
        var findings = new ArrayList<UnitFinding>();
        var units = new LinkedHashMap<String, WorkflowDefinition>();
        var planFile = root + PLAN_FILE;
        for (var file : unitFiles) {
            read(file, findings).flatMap(source -> parse(file, source, findings))
                    .ifPresent(unit -> UnitFinding.putUnique(units, unit, planFile, findings));
        }
        var loaded = new LinkedHashMap<String, WorkflowDefinition>();
        var plan = units.remove(Phase.PLAN_UNIT);
        if (plan != null) {
            loaded.put(plan.id(), plan);
        }
        loaded.putAll(units);
        for (var unit : loaded.values()) {
            var validator = context.validator(units.values(), releasedStates.getOrDefault(unit.id(), List.of()));
            validator.verify(unit).violations().forEach(violation -> findings.add(UnitFinding.at(
                    UnitFinding.Kind.RULE, violation.rule().name(), violation.location(), violation.message())));
        }
        if (findings.isEmpty()) {
            return UnitLoadResult.verified(loaded);
        }
        findings.forEach(finding -> LOGGER.error(ERROR.DEFAULT_UNIT_REFUSED, finding.file(), finding.line(),
                finding.code()));
        return UnitLoadResult.refused(findings);
    }

    private Optional<String> read(String file, List<UnitFinding> findings) {
        var resource = root + file;
        try (var in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                findings.add(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_MISSING, resource, 0,
                        "the default unit '%s' is not on the classpath".formatted(resource)));
                return Optional.empty();
            }
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            findings.add(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_UNREADABLE, resource, 0,
                    "the default unit '%s' cannot be read: %s".formatted(resource, e.getMessage())));
            return Optional.empty();
        }
    }

    private Optional<WorkflowDefinition> parse(String file, String source, List<UnitFinding> findings) {
        var resource = root + file;
        return switch (WorkflowParser.parse(resource, source, UnitOrigin.DEFAULT)) {
            case ParseResult.Parsed(var definition) -> Optional.of(definition);
            case ParseResult.Refused(var diagnostic) -> {
                findings.add(new UnitFinding(UnitFinding.Kind.PARSE, diagnostic.rule().name(), resource,
                        diagnostic.line(), diagnostic.message()));
                yield Optional.empty();
            }
        };
    }
}
