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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;

/**
 * The fixture projects of the unit-loader tests (PM-IMPL-5). A fixture project is a directory, a {@code @TempDir} of
 * the test, whose {@code .marshall/config/} holds the units of {@code unit-fixtures/defaults/}, which are written for
 * the tests and never copied from the shipped content of the product. The same units, read from that classpath root,
 * are the default units the project units are verified against.
 * <p>
 * A refusing project is the passing one with named {@link Mutation}s: a text that occurs exactly once in one file,
 * replaced by another. The named mutations of this class are the ones several tests share.
 */
final class UnitFixtures {

    /** The project-ownable unit files of the tests: the phase files of the plan unit, then the sub-workflows. */
    static final List<String> UNIT_FILES = unitFiles();

    /** The header of the phase files of the plan unit. */
    static final String PLAN_HEADER = "workflow plan version 1 format 1";

    /** The header of the re-planning unit. */
    static final String REPLAN_HEADER = "workflow replan version 1 format 1";

    /** The file of the finalize phase. */
    static final String FINALIZE_FILE = "finalize.pmwf";

    /** The author of a default unit, which the frontmatter of every fixture file names. */
    static final String DEFAULT_AUTHOR = "plan-marshall";

    /** The author of the fixture units written into a project, a person. */
    static final String PROJECT_AUTHOR = "Erika Mustermann";

    /** The project step of the binding tests. */
    static final String PROJECT_STEP = "project:lint";

    /** A terminal of the finalize phase reports another outcome class than in the default unit: INV-GEN-1. */
    static final Mutation INVARIANT_VIOLATION = new Mutation("a terminal reports another outcome class",
            FINALIZE_FILE, "state returned terminal returned failure", "state returned terminal returned success");

    /** A transition of the finalize phase targets a state no file declares: a violation of Rules 1-12. */
    static final Mutation RULE_VIOLATION = new Mutation("a transition targets an undeclared state", FINALIZE_FILE,
            "merged -> finalize.land", "merged -> finalize.nowhere");

    /** The vote wait of the adjudication unit lies below its bound of 60 seconds. */
    static final Mutation DECLARATION_OUT_OF_BOUNDS = new Mutation("the vote wait lies below its bound",
            InvariantFixtures.ADJUDICATION_FILE, "declare vote_wait_seconds = 900", "declare vote_wait_seconds = 30");

    /** The re-planning unit declares the DSL format before the compiled one. */
    static final Mutation OLDER_FORMAT = new Mutation("an older format", InvariantFixtures.REPLAN_FILE,
            REPLAN_HEADER, "workflow replan version 1 format 0");

    /** The re-planning unit declares a DSL format after the compiled one. */
    static final Mutation NEWER_FORMAT = new Mutation("a newer format", InvariantFixtures.REPLAN_FILE,
            REPLAN_HEADER, "workflow replan version 1 format 2");

    /** The header of the re-planning unit misspells {@code version}: the parser refuses the file. */
    static final Mutation UNPARSABLE = new Mutation("an unparsable unit", InvariantFixtures.REPLAN_FILE,
            REPLAN_HEADER, "workflow replan revision 1 format 1");

    /** The re-planning file declares the identifier of the adjudication unit, which an earlier file declares. */
    static final Mutation DUPLICATE_UNIT_ID = new Mutation("a sub-unit file declares the id of another",
            InvariantFixtures.REPLAN_FILE, REPLAN_HEADER, "workflow adjudicate.decisions version 1 format 1");

    /** The re-planning file declares the identifier of the merged plan unit. */
    static final Mutation PLAN_UNIT_ID = new Mutation("a sub-unit file declares the id plan",
            InvariantFixtures.REPLAN_FILE, REPLAN_HEADER, PLAN_HEADER);

    /** The state that completes {@code default:create-pr} completes the project step as well. */
    static final Mutation COMPLETES_PROJECT_STEP = new Mutation("the create-pr state completes the project step",
            FINALIZE_FILE, "            completes default:create-pr as done",
            "            completes default:create-pr as done\n            completes " + PROJECT_STEP + " as done");

    private UnitFixtures() {
    }

    private static List<String> unitFiles() {
        var files = new ArrayList<>(InvariantFixtures.PLAN_FILES);
        files.addAll(InvariantFixtures.SUB_UNIT_FILES);
        return List.copyOf(files);
    }

    /**
     * @return the inputs of the static verifier over the registries of the fixtures and the entity unit
     */
    static VerificationContext context() {
        return new VerificationContext(InvariantFixtures.registry(), InvariantFixtures.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), List.of(InvariantFixtures.entityUnit()));
    }

    /**
     * @return the loader of the default units of {@code unit-fixtures/defaults/}, read through the class loader of
     *         the tests, without released state names
     */
    static DefaultUnitLoader defaultLoader() {
        return new DefaultUnitLoader(UnitFixtures.class.getClassLoader(), InvariantFixtures.ROOT, UNIT_FILES,
                context(), Map.of());
    }

    /**
     * @return the verified default units of {@code unit-fixtures/defaults/}
     */
    static Map<String, WorkflowDefinition> defaultUnits() {
        var result = defaultLoader().load();
        assertTrue(result.isVerified(), () -> "the default fixtures are refused: " + result.findings());
        return result.units();
    }

    /**
     * @return the loader of the project units with the fixture defaults as reference and the default roster of the
     *         invariant tests
     */
    static ProjectUnitLoader projectLoader() {
        return new ProjectUnitLoader(context(), defaultUnits(), InvariantFixtures.defaultRoster(), UNIT_FILES);
    }

    /**
     * Writes every fixture unit into a directory as a project unit, each mutation applied to its file. A project unit
     * is made by a person, so the author {@value #DEFAULT_AUTHOR} of the default unit becomes {@value #PROJECT_AUTHOR};
     * the line count stays.
     *
     * @param directory the directory, created when missing
     * @param mutations the changes of the passing units
     * @return the directory
     */
    static Path writeUnits(Path directory, Mutation... mutations) {
        var sources = new LinkedHashMap<String, String>();
        UNIT_FILES.forEach(file -> sources.put(file, InvariantFixtures.read(file)
                .replace("\nauthor: " + DEFAULT_AUTHOR + "\n", "\nauthor: " + PROJECT_AUTHOR + "\n")));
        for (var mutation : mutations) {
            sources.put(mutation.file(), mutation.apply(sources.get(mutation.file())));
        }
        try {
            Files.createDirectories(directory);
            for (var entry : sources.entrySet()) {
                Files.writeString(directory.resolve(entry.getKey()), entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return directory;
    }

    /**
     * Builds a fixture project: every fixture unit in its {@code .marshall/config/}, each mutation applied.
     *
     * @param project   the project root, a {@code @TempDir} of the test
     * @param mutations the changes of the passing units
     * @return the project root
     */
    static Path writeProject(Path project, Mutation... mutations) {
        writeUnits(project.resolve(ProjectUnitLoader.CONFIG_DIRECTORY), mutations);
        return project;
    }

    /**
     * @param project the project root
     * @return the workspace of the project, without linked worktrees
     */
    static WorkspaceRoots roots(Path project) {
        return new WorkspaceRoots(project, Set.of());
    }

    /**
     * Loads the units of a fixture project.
     *
     * @param projectId    the project, named in the log
     * @param project      the project root
     * @param projectSteps the declared project steps
     * @param boundStepIds the ids of the bound project steps
     * @return the result of the load
     */
    static UnitLoadResult load(String projectId, Path project, List<FinalizeStepDescriptor> projectSteps,
            Set<String> boundStepIds) {
        return projectLoader().load(projectId, roots(project), projectSteps, boundStepIds);
    }

    /**
     * @param file the name of a unit file
     * @return the path the project-unit loader names the file by
     */
    static String configPath(String file) {
        return ProjectUnitLoader.CONFIG_DIRECTORY + file;
    }

    /**
     * @param name  the step id
     * @param order the order of the step
     * @return a prunable project step that declares nothing else
     */
    static FinalizeStepDescriptor projectStep(String name, int order) {
        return InvariantFixtures.step(name, order, LaneClass.PRUNABLE, false, List.of(), List.of());
    }

    /**
     * @param file   the name of a fixture file
     * @param anchor a text that occurs exactly once in the passing file
     * @return the line, counted from 1, on which the anchor begins
     */
    static int lineOf(String file, String anchor) {
        return InvariantFixtures.lineOf(InvariantFixtures.read(file), anchor);
    }

    /**
     * @param findings the findings of a load
     * @param kind     the kind of check
     * @param code     the code
     * @return the first finding of the kind and code
     */
    static Optional<UnitFinding> find(List<UnitFinding> findings, UnitFinding.Kind kind, String code) {
        return findings.stream().filter(finding -> finding.kind() == kind && finding.code().equals(code)).findFirst();
    }

    /**
     * One named change of a passing fixture: a text that occurs exactly once in the file, replaced by another.
     *
     * @param name    what the change does to the unit
     * @param file    the fixture file it changes
     * @param find    the text it replaces
     * @param replace the text it puts in its place
     */
    record Mutation(String name, String file, String find, String replace) {

        /**
         * @param source the text of the file
         * @return the changed text
         * @throws IllegalArgumentException if the text to replace does not occur exactly once
         */
        String apply(String source) {
            var index = source.indexOf(find);
            if (index < 0 || source.indexOf(find, index + 1) >= 0) {
                throw new IllegalArgumentException("'%s' does not occur exactly once in %s".formatted(find, file));
            }
            return source.substring(0, index) + replace + source.substring(index + find.length());
        }

        /**
         * @param anchor a text that occurs exactly once in the changed file
         * @return the line, counted from 1, on which the anchor begins in the changed file
         */
        int lineOf(String anchor) {
            return InvariantFixtures.lineOf(apply(InvariantFixtures.read(file)), anchor);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
