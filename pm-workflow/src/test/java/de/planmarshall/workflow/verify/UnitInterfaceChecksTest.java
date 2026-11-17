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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The static reads of a unit (PM-WF-16, Rule 1): every {@code reads_config} key exists in the closed configuration and
 * names no derived fact, every {@code reads_fact} is a known kind, and every input fallback is a listed key of an
 * assignable domain, with a compiled default where the input has neither a default nor the {@code ?}. The registries are
 * arguments of the verifier, so this class supplies a fixture registry of its own; the default validator carries the
 * empty registries and refuses every declared read (fail closed).
 */
@EnableGeneratorController
@DisplayName("The static reads of a unit")
class UnitInterfaceChecksTest {

    private static final ConfigKeyRegistry CONFIG = ConfigKeyRegistry.of(List.of(
            new ConfigKeyRegistry.ConfigKey("project.review.currency",
                    new InputType.Enumeration(List.of("current", "stale")), true),
            new ConfigKeyRegistry.ConfigKey("project.review.retries", new InputType.IntRange(0, 5), true),
            new ConfigKeyRegistry.ConfigKey("local-config.mode",
                    new InputType.Enumeration(List.of("strict", "lenient")), false),
            new ConfigKeyRegistry.ConfigKey("local-config.targets",
                    new InputType.EnumList(List.of("origin", "mirror")), true)));

    private static final FactKindRegistry FACTS = FactKindRegistry.of(List.of("push_remote", "changed_files"));

    private static final WorkflowValidator VALIDATOR = new WorkflowValidator(TestPrimitives.registry(), List.of(),
            TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(),
            TestPrimitives.catalog(), CONFIG, FACTS);

    /** A plan unit whose frontmatter carries the given lines after its icon. */
    private static String source(String... declarations) {
        var frontmatter = new ArrayList<String>();
        frontmatter.add("---");
        frontmatter.add("name: verify.declared-reads");
        frontmatter.add("description: A unit written for the verifier tests.");
        frontmatter.add("type: workflow");
        frontmatter.add("icon: workflow.default");
        frontmatter.addAll(List.of(declarations));
        frontmatter.add("modification_date: 2026-01-15");
        frontmatter.add("version: 0.1.0");
        frontmatter.add("author: plan-marshall");
        frontmatter.add("---");
        return String.join("\n", frontmatter)
                + "\nworkflow verify.declared-reads version 1 format 1\nscope plan\nstart verify.entry\n\n"
                + "state verify.entry terminal done success\n";
    }

    private static List<String> rules(WorkflowDefinition definition) {
        return VALIDATOR.verify(definition).violations().stream().map(violation -> violation.rule().name()).toList();
    }

    @Test
    @DisplayName("passes a unit whose reads and inputs the fixture registries hold")
    void passesAPopulatedRegistry() {
        var definition = VerifyFixtures.parseSource("declared-reads.pmwf", source(
                "reads_config: project.review.currency",
                "reads_fact: push_remote",
                "input: mode",
                "input_type: enum[current|stale]",
                "input_fallback: project.review.currency"));

        var found = VALIDATOR.verify(definition).violations();

        assertEquals(List.of(), found, found::toString);
    }

    @Test
    @DisplayName("passes an input with a default whose fallback key has no compiled default")
    void passesADefaultedFallbackWithoutCompiledDefault() {
        var definition = VerifyFixtures.parseSource("declared-reads.pmwf", source(
                "reads_config: local-config.mode",
                "input: mode",
                "input_type: enum[strict|lenient]",
                "input_default: strict",
                "input_fallback: local-config.mode"));

        var found = VALIDATOR.verify(definition).violations();

        assertEquals(List.of(), found, found::toString);
    }

    @Test
    @DisplayName("passes a unit that declares no reads with the default empty registries")
    void passesWithoutReads() {
        var definition = VerifyFixtures.parseSource("declared-reads.pmwf", source());

        var found = new WorkflowValidator(TestPrimitives.registry(), List.of()).verify(definition).violations();

        assertEquals(List.of(), found, found::toString);
    }

    @Test
    @DisplayName("refuses a reads_config key and a reads_fact kind with the default empty registries")
    void refusesWithTheEmptyRegistries() {
        var definition = VerifyFixtures.parseSource("declared-reads.pmwf", source(
                "reads_config: project.review.currency",
                "reads_fact: push_remote"));

        var found = new WorkflowValidator(TestPrimitives.registry(), List.of()).verify(definition).violations();

        assertAll(
                () -> assertEquals(List.of("CONFIG_KEY_BINDING", "FACT_KIND_BINDING"),
                        found.stream().map(violation -> violation.rule().name()).toList(),
                        "the empty registries refuse both declared reads"),
                () -> assertTrue(found.getFirst().message().contains("not a key of the closed configuration"),
                        found.getFirst()::message),
                () -> assertTrue(found.get(1).message().contains("not a known fact kind"), found.get(1)::message));
    }

    static Stream<Arguments> refusedReads() {
        return Stream.of(
                Arguments.of("an unknown configuration key", "CONFIG_KEY_BINDING",
                        "not a key of the closed configuration", List.of("reads_config: project.unknown")),
                Arguments.of("a derived fact in reads_config", "CONFIG_KEY_BINDING", "names a derived fact",
                        List.of("reads_config: derived.push_remote")),
                Arguments.of("an unknown fact kind", "FACT_KIND_BINDING", "not a known fact kind",
                        List.of("reads_fact: unknown_kind")),
                Arguments.of("an input that shadows a fact kind", "INPUT_DECLARATION",
                        "shadows the fact kind the unit reads",
                        List.of("reads_fact: push_remote", "input: push_remote", "input_type: int")),
                Arguments.of("a fallback that reads_config does not list", "INPUT_DECLARATION",
                        "is not listed in reads_config",
                        List.of("input: retries", "input_type: int[0..5]",
                                "input_fallback: project.review.retries")),
                Arguments.of("a fallback of a domain the type does not admit", "INPUT_DECLARATION",
                        "is not assignable to its type",
                        List.of("reads_config: project.review.currency", "input: retries", "input_type: int[0..5]",
                                "input_fallback: project.review.currency")),
                Arguments.of("a fallback without a compiled default", "INPUT_DECLARATION",
                        "has no compiled default",
                        List.of("reads_config: local-config.mode", "input: mode",
                                "input_type: enum[strict|lenient]", "input_fallback: local-config.mode")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedReads")
    @DisplayName("refuses a read or an input the closed registries do not admit")
    void refusesOneRead(String name, String rule, String message, List<String> declarations) {
        var definition = VerifyFixtures.parseSource("declared-reads.pmwf",
                source(declarations.toArray(String[]::new)));

        var found = VALIDATOR.verify(definition).violations();

        assertAll(
                () -> assertEquals(List.of(rule), rules(definition), found::toString),
                () -> assertTrue(found.getFirst().message().contains(message), found.getFirst()::message));
    }

    @Nested
    @DisplayName("Configuration key registry")
    class ConfigurationKeys {

        @Test
        @DisplayName("finds a registered key and no other, and copies its entries")
        void findsRegisteredKeys() {
            assertAll(
                    () -> assertEquals(Optional.of("project.review.currency"),
                            CONFIG.find("project.review.currency").map(ConfigKeyRegistry.ConfigKey::key),
                            "a registered key is found by its effective form"),
                    () -> assertTrue(CONFIG.contains("local-config.mode"), "a registered key is contained"),
                    () -> assertFalse(CONFIG.contains("workflow.unknown"), "an unknown key is not contained"),
                    () -> assertEquals(Optional.empty(), CONFIG.empty().find("project.review.currency"),
                            "the empty registry finds nothing"));
        }

        @Test
        @DisplayName("refuses two entries of one key")
        void refusesADuplicate() {
            var key = new ConfigKeyRegistry.ConfigKey("project.review.currency",
                    new InputType.Enumeration(List.of("current")), true);
            var entries = List.of(key, key);

            assertThrows(IllegalArgumentException.class, () -> ConfigKeyRegistry.of(entries),
                    "a key registered twice must be refused");
        }

        @Test
        @DisplayName("refuses a null value in the canonical constructor")
        void refusesANullValue() {
            var keys = new HashMap<String, ConfigKeyRegistry.ConfigKey>();
            keys.put("project.review.currency", null);

            assertThrows(NullPointerException.class, () -> new ConfigKeyRegistry(keys),
                    "a null value in the map must be refused");
        }

        @Test
        @DisplayName("assigns a domain to an input type only when every value it admits fits")
        void assignsDomains() {
            assertAll(
                    () -> assertTrue(ConfigKeyRegistry.assignable(new InputType.Enumeration(List.of("a", "b", "c")),
                            new InputType.Enumeration(List.of("a", "b"))), "a wider enum admits the domain"),
                    () -> assertFalse(ConfigKeyRegistry.assignable(new InputType.Enumeration(List.of("a")),
                            new InputType.Enumeration(List.of("a", "b"))), "a narrower enum refuses the domain"),
                    () -> assertTrue(ConfigKeyRegistry.assignable(new InputType.IntRange(0, 9),
                            new InputType.IntRange(2, 5)), "a wider range admits the domain"),
                    () -> assertFalse(ConfigKeyRegistry.assignable(new InputType.IntRange(0, 9),
                            new InputType.IntRange(null, null)), "an unbounded domain overflows the range"),
                    () -> assertTrue(ConfigKeyRegistry.assignable(new InputType.IntRange(null, null),
                            new InputType.IntRange(2, 5)), "an unbounded range admits a bounded domain"),
                    () -> assertFalse(ConfigKeyRegistry.assignable(new InputType.IntRange(0, 9),
                            new InputType.IntRange(-1, 5)), "a domain below the low bound is refused"),
                    () -> assertTrue(ConfigKeyRegistry.assignable(new InputType.EnumList(List.of("a", "b")),
                            new InputType.EnumList(List.of("a"))), "a wider list admits the domain"),
                    () -> assertFalse(ConfigKeyRegistry.assignable(new InputType.EnumList(List.of("a")),
                            new InputType.Enumeration(List.of("a"))), "a scalar domain is no list domain"));
        }

        @Test
        @DisplayName("refuses a fact kind registered twice and copies the set")
        void factKinds() {
            var duplicated = List.of("push_remote", "push_remote");

            assertAll(
                    () -> assertTrue(FACTS.contains("push_remote"), "a registered kind is found"),
                    () -> assertFalse(FACTS.contains("unknown"), "an unknown kind is not found"),
                    () -> assertFalse(FactKindRegistry.empty().contains("push_remote"), "the empty set holds none"),
                    () -> assertThrows(IllegalArgumentException.class, () -> FactKindRegistry.of(duplicated),
                            "a kind registered twice must be refused"));
        }
    }
}
