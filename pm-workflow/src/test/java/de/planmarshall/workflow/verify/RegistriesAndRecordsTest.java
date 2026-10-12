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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Registries and Records")
class RegistriesAndRecordsTest {

    @Test
    @DisplayName("EntityKind should support constructors, accessors and null validation")
    void testEntityKind() {
        var withUnit = new EntityKind("task", Optional.of("worker-task"), "task.evaluate");
        assertAll(
                () -> assertEquals("task", withUnit.name()),
                () -> assertEquals(Optional.of("worker-task"), withUnit.entityUnit()),
                () -> assertEquals("task.evaluate", withUnit.returnEvaluationPrimitiveId()),
                () -> assertTrue(withUnit.hasEntityUnit())
        );

        var convWithUnit = new EntityKind("task", "worker-task", "task.evaluate");
        assertTrue(convWithUnit.hasEntityUnit());

        var convWithoutUnit = new EntityKind("screen", (String) null, "screen.evaluate");
        assertFalse(convWithoutUnit.hasEntityUnit());
        assertEquals(Optional.empty(), convWithoutUnit.entityUnit());

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new EntityKind(null, Optional.empty(), "eval")),
                () -> assertThrows(NullPointerException.class, () -> new EntityKind("k", (Optional<String>) null, "eval")),
                () -> assertThrows(NullPointerException.class, () -> new EntityKind("k", Optional.empty(), null))
        );
    }

    @Test
    @DisplayName("EntityKindRegistry should provide defaults, lookup, with and null validation")
    void testEntityKindRegistry() {
        var defaults = EntityKindRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.find("screen").isPresent()),
                () -> assertTrue(defaults.find("task").isPresent()),
                () -> assertTrue(defaults.find("change").isPresent()),
                () -> assertTrue(defaults.find("decision").isPresent()),
                () -> assertTrue(defaults.find("triage").isPresent()),
                () -> assertTrue(defaults.find("simplify").isPresent()),
                () -> assertTrue(defaults.find("review").isPresent()),
                () -> assertFalse(defaults.find("nonexistent").isPresent())
        );

        var custom = new EntityKind("custom", Optional.empty(), "custom.eval");
        var updated = defaults.with(custom);
        assertTrue(updated.find("custom").isPresent());
        assertFalse(defaults.find("custom").isPresent());

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null)),
                () -> assertThrows(NullPointerException.class, () -> new EntityKindRegistry(null))
        );
    }

    @Test
    @DisplayName("QuestionClass should support record components and null validation")
    void testQuestionClass() {
        var qc = new QuestionClass("init", Set.of("answered", "assumed"));
        var outcomes = Set.of("a");
        assertAll(
                () -> assertEquals("init", qc.name()),
                () -> assertEquals(Set.of("answered", "assumed"), qc.outcomes()),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClass(null, outcomes)),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClass("q", null))
        );
    }

    @Test
    @DisplayName("QuestionClassRegistry should provide defaults, lookup, with and null validation")
    void testQuestionClassRegistry() {
        var defaults = QuestionClassRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.find("init").isPresent()),
                () -> assertTrue(defaults.find("clarification").isPresent()),
                () -> assertTrue(defaults.find("review").isPresent()),
                () -> assertTrue(defaults.find("task").isPresent()),
                () -> assertTrue(defaults.find("setup").isPresent()),
                () -> assertFalse(defaults.find("nonexistent").isPresent())
        );

        var custom = new QuestionClass("custom", Set.of("yes", "no"));
        var updated = defaults.with(custom);
        assertTrue(updated.find("custom").isPresent());
        assertFalse(defaults.find("custom").isPresent());

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null)),
                () -> assertThrows(NullPointerException.class, () -> new QuestionClassRegistry(null))
        );
    }

    @Test
    @DisplayName("SchemaRegistry should provide defaults, contains, with and null validation")
    void testSchemaRegistry() {
        var defaults = SchemaRegistry.defaults();
        assertAll(
                () -> assertTrue(defaults.contains("question-envelope")),
                () -> assertTrue(defaults.contains("question-answer")),
                () -> assertTrue(defaults.contains("vote-answer")),
                () -> assertTrue(defaults.contains("triage-groups")),
                () -> assertTrue(defaults.contains("simplify-proposal")),
                () -> assertTrue(defaults.contains("commit-result")),
                () -> assertFalse(defaults.contains("unknown-schema"))
        );

        var updated = defaults.with("new-schema");
        assertTrue(updated.contains("new-schema"));
        assertFalse(defaults.contains("new-schema"));

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> defaults.with(null)),
                () -> assertThrows(NullPointerException.class, () -> new SchemaRegistry(null))
        );
    }

    @Test
    @DisplayName("WorkflowCatalog should support empty, of, find, contains and definitions")
    void testWorkflowCatalog() {
        var empty = WorkflowCatalog.empty();
        assertAll(
                () -> assertFalse(empty.find("foo").isPresent()),
                () -> assertFalse(empty.contains("foo")),
                () -> assertTrue(empty.definitions().isEmpty())
        );

        var unit = VerifyFixtures.parseSource("catalog-unit.pmwf", """
                workflow catalog.test version 1 format 1
                scope subworkflow
                start entry

                state entry terminal done success
                    phase verify
                """);
        var catalog = WorkflowCatalog.of(unit);
        assertAll(
                () -> assertTrue(catalog.contains("catalog.test")),
                () -> assertTrue(catalog.find("catalog.test").isPresent()),
                () -> assertEquals(1, catalog.definitions().size()),
                () -> assertThrows(NullPointerException.class, () -> new WorkflowCatalog(null))
        );
    }

    @Test
    @DisplayName("WorkflowValidator overloaded constructors should initialize successfully")
    void testWorkflowValidatorConstructors() {
        var registry = TestPrimitives.registry();
        var released = List.of("verify.entry");
        var validator2 = new WorkflowValidator(registry, released);
        assertNotNull(validator2);

        var validator5 = new WorkflowValidator(registry, released, EntityKindRegistry.defaults(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults());
        assertNotNull(validator5);
    }
}
