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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

@EnableGeneratorController
@DisplayName("Registries and Records")
class RegistriesAndRecordsTest {

    @Test
    @DisplayName("EntityKind should support its components, an absent entity unit and null validation")
    void entityKind() {
        var withUnit = new EntityKind("task", "worker-task", "task.evaluate");
        assertAll(
                () -> assertEquals("task", withUnit.name(), "the kind keeps its name"),
                () -> assertEquals("worker-task", withUnit.entityUnit(), "the kind keeps its entity unit"),
                () -> assertEquals("task.evaluate", withUnit.returnEvaluationPrimitiveId(),
                        "the kind keeps its evaluation primitive"),
                () -> assertTrue(withUnit.hasEntityUnit(), "a kind with a unit reports it")
        );

        var withoutUnit = new EntityKind("screen", null, "screen.evaluate");
        assertAll(
                () -> assertFalse(withoutUnit.hasEntityUnit(), "a kind without a unit reports none"),
                () -> assertNull(withoutUnit.entityUnit(), "an absent entity unit is null")
        );

        var name = Generators.letterStrings(1, 8).next();
        var evaluation = Generators.letterStrings(1, 8).next();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new EntityKind(null, null, evaluation),
                        "a null entity kind name is refused"),
                () -> assertThrows(NullPointerException.class, () -> new EntityKind(name, null, null),
                        "a null return-evaluation primitive is refused")
        );
    }

    @Test
    @DisplayName("EntityKindRegistry should provide defaults, lookup, with and null validation")
    void entityKindRegistry() {
        var defaults = EntityKindRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.find("screen").isPresent(), "the defaults register the screen kind"),
                () -> assertTrue(defaults.find("task").isPresent(), "the defaults register the task kind"),
                () -> assertFalse(defaults.find(TestPrimitives.CHANGE_KIND).isPresent(),
                        "the kind of the tests is no default"),
                () -> assertTrue(defaults.find("decision").isPresent(), "the defaults register the decision kind"),
                () -> assertTrue(defaults.find("triage").isPresent(), "the defaults register the triage kind"),
                () -> assertTrue(defaults.find("simplify").isPresent(), "the defaults register the simplify kind"),
                () -> assertTrue(defaults.find("lessons-review").isPresent(),
                        "the defaults register the lessons-review kind"),
                () -> assertFalse(defaults.find("review").isPresent(),
                        "review is no entity kind, the kind is lessons-review"),
                () -> assertFalse(defaults.find("nonexistent").isPresent(),
                        "an unknown identifier resolves to no kind")
        );

        assertAll(
                () -> assertNull(defaults.find("screen").orElseThrow().entityUnit(), "screen runs no entity unit"),
                () -> assertEquals("worker-task", defaults.find("task").orElseThrow().entityUnit(),
                        "task runs the task protocol unit"),
                () -> assertEquals("entity", defaults.find("decision").orElseThrow().entityUnit(),
                        "decision runs the entity unit"),
                () -> assertEquals("entity", defaults.find("triage").orElseThrow().entityUnit(),
                        "triage runs the entity unit"),
                () -> assertEquals("entity", defaults.find("simplify").orElseThrow().entityUnit(),
                        "simplify runs the entity unit"),
                () -> assertEquals("entity-multi", defaults.find("lessons-review").orElseThrow().entityUnit(),
                        "lessons-review runs the multi-submit unit"),
                () -> assertEquals(Set.of("worker-task", "entity", "entity-multi"), defaults.entityUnits(),
                        "the units name no unit for screen"),
                () -> assertEquals("lessons-review.evaluate",
                        defaults.find("lessons-review").orElseThrow().returnEvaluationPrimitiveId(),
                        "lessons-review evaluates through its own primitive")
        );

        var defaultIdentifiers = Set.of("screen", "task", "decision", "triage", "simplify", "lessons-review").stream()
                .map(name -> defaults.find(name).orElseThrow())
                .flatMap(kind -> Stream.concat(Stream.of(kind.name(), kind.returnEvaluationPrimitiveId()),
                        Stream.ofNullable(kind.entityUnit())))
                .toList();
        assertTrue(defaultIdentifiers.stream().noneMatch(identifier -> identifier.startsWith("test.")),
                () -> "the defaults name a test identifier: " + defaultIdentifiers);

        var customName = "custom-" + Generators.letterStrings(1, 8).next();
        var customEvaluation = "eval-" + Generators.letterStrings(1, 8).next();
        var custom = new EntityKind(customName, null, customEvaluation);
        var updated = defaults.with(custom);
        assertTrue(updated.find(customName).isPresent(), "the registry with the custom kind finds it");
        assertFalse(defaults.find(customName).isPresent(),
                "the untouched defaults do not know the custom kind");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null),
                        "the registry refuses a null entity kind"),
                () -> assertThrows(NullPointerException.class, () -> new EntityKindRegistry(null),
                        "a null entity kind list is refused")
        );
    }

    @Test
    @DisplayName("QuestionClass should support record components and null validation")
    void questionClass() {
        var qc = new QuestionClass("init", Set.of("answered"));
        var outcomes = Set.of(Generators.letterStrings(1, 8).next());
        var name = Generators.letterStrings(1, 8).next();
        assertAll(
                () -> assertEquals("init", qc.name(), "the question class keeps its name"),
                () -> assertEquals(Set.of("answered"), qc.outcomes(), "the question class keeps its outcomes"),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClass(null, outcomes),
                        "a null question class name is refused"),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClass(name, null),
                        "a null outcome set is refused")
        );
    }

    @Test
    @DisplayName("QuestionClassRegistry should provide defaults, lookup, with and null validation")
    void questionClassRegistry() {
        var defaults = QuestionClassRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.find("init").isPresent(), "the defaults register the init class"),
                () -> assertTrue(defaults.find("clarification").isPresent(),
                        "the defaults register the clarification class"),
                () -> assertTrue(defaults.find("review").isPresent(), "the defaults register the review class"),
                () -> assertTrue(defaults.find("task").isPresent(), "the defaults register the task class"),
                () -> assertTrue(defaults.find("setup").isPresent(), "the defaults register the setup class"),
                () -> assertTrue(defaults.find("ask").isPresent(), "the defaults register the ask class"),
                () -> assertFalse(defaults.find("nonexistent").isPresent(),
                        "an unknown identifier resolves to no class")
        );

        assertAll(
                () -> assertEquals(Set.of("answered"), defaults.find("init").orElseThrow().outcomes(),
                        "init answers with the answered outcome"),
                () -> assertEquals(Set.of("answered"), defaults.find("clarification").orElseThrow().outcomes(),
                        "clarification answers with the answered outcome"),
                () -> assertEquals(Set.of("approve", "revise"), defaults.find("review").orElseThrow().outcomes(),
                        "review offers approve and revise"),
                () -> assertEquals(Set.of("answered"), defaults.find("task").orElseThrow().outcomes(),
                        "task answers with the answered outcome"),
                () -> assertEquals(Set.of("answered"), defaults.find("setup").orElseThrow().outcomes(),
                        "setup answers with the answered outcome"),
                () -> assertEquals(Set.of("answered"), defaults.find("ask").orElseThrow().outcomes(),
                        "ask answers with the answered outcome")
        );

        var customName = "custom-" + Generators.letterStrings(1, 8).next();
        var custom = new QuestionClass(customName, Set.of("yes-" + Generators.letterStrings(1, 8).next(),
                "no-" + Generators.letterStrings(1, 8).next()));
        var updated = defaults.with(custom);
        assertTrue(updated.find(customName).isPresent(), "the registry with the custom class finds it");
        assertFalse(defaults.find(customName).isPresent(),
                "the untouched defaults do not know the custom class");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null),
                        "the registry refuses a null question class"),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClassRegistry(null),
                        "a null question class list is refused")
        );
    }

    @Test
    @DisplayName("SchemaRegistry should provide defaults, contains, with and null validation")
    void schemaRegistry() {
        var defaults = SchemaRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.contains("question-envelope"),
                        "the defaults contain the question-envelope schema"),
                () -> assertTrue(defaults.contains("question-answer"),
                        "the defaults contain the question-answer schema"),
                () -> assertTrue(defaults.contains("vote-answer"),
                        "the defaults contain the vote-answer schema"),
                () -> assertTrue(defaults.contains("triage-groups"),
                        "the defaults contain the triage-groups schema"),
                () -> assertTrue(defaults.contains("simplify-proposal"),
                        "the defaults contain the simplify-proposal schema"),
                () -> assertTrue(defaults.contains("intent-record"),
                        "the defaults contain the intent-record schema"),
                () -> assertTrue(defaults.contains("outline-record"),
                        "the defaults contain the outline-record schema"),
                () -> assertTrue(defaults.contains("commit-result"),
                        "the defaults contain the commit-result schema"),
                () -> assertFalse(defaults.contains("unknown-" + Generators.letterStrings(1, 8).next()),
                        "an unknown schema name is not registered")
        );

        var schema = "new-" + Generators.letterStrings(1, 8).next();
        var updated = defaults.with(schema);
        assertTrue(updated.contains(schema), "the registry with the new schema contains it");
        assertFalse(defaults.contains(schema), "the untouched defaults do not contain the new schema");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null),
                        "the registry refuses a null schema name"),
                () -> assertThrows(NullPointerException.class, () -> new SchemaRegistry(null),
                        "a null schema name list is refused")
        );
    }

    @Test
    @DisplayName("WorkflowCatalog should support empty, of, find, contains and definitions")
    void workflowCatalog() {
        var empty = WorkflowCatalog.empty();
        assertAll(
                () -> assertFalse(empty.find("missing-" + Generators.letterStrings(1, 8).next()).isPresent(),
                        "an empty catalog finds no unit"),
                () -> assertFalse(empty.contains("missing-" + Generators.letterStrings(1, 8).next()),
                        "an empty catalog contains no unit"),
                () -> assertTrue(empty.definitions().isEmpty(), "an empty catalog has no definitions")
        );

        var unit = VerifyFixtures.parseSource("catalog-unit.pmwf", """
                ---
                name: catalog.test
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow catalog.test version 1 format 1
                scope subworkflow
                start entry

                state entry terminal done success
                """);
        var catalog = WorkflowCatalog.of(unit);
        assertAll(
                () -> assertTrue(catalog.contains("catalog.test"), "the catalog contains the parsed unit"),
                () -> assertTrue(catalog.find("catalog.test").isPresent(), "the catalog finds the parsed unit"),
                () -> assertEquals(1, catalog.definitions().size(), "the catalog holds exactly one definition"),
                () -> assertThrows(NullPointerException.class, () -> new WorkflowCatalog(null),
                        "a null unit list is refused")
        );
    }

    @Test
    @DisplayName("WorkflowValidator overloaded constructors should initialize successfully")
    void workflowValidatorConstructors() {
        var registry = TestPrimitives.registry();
        var released = List.of("verify.entry");
        var validator2 = new WorkflowValidator(registry, released);
        assertNotNull(validator2, "the two-argument constructor builds a validator");

        var validator5 = new WorkflowValidator(registry, released, EntityKindRegistry.defaults(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults());
        assertNotNull(validator5, "the five-argument constructor builds a validator");
    }
}
