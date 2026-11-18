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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.config.ConfigValueDomain;
import de.planmarshall.core.config.EffectiveConfigKey;
import de.planmarshall.core.config.EffectiveConfigKeys;
import de.planmarshall.core.store.DerivedFactKind;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.verify.ConfigKeyRegistry;
import de.planmarshall.workflow.verify.InvariantFixtures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production registries: they carry exactly the compiled tables and the closed enumeration, and the context they
 * assemble holds the compiled defaults beside them. The expected sets are derived from the declarations at run time, so a
 * member added to either declaration is visible here without an edit.
 */
@EnableGeneratorController
@DisplayName("Product registries")
class ProductRegistriesTest {

    @Nested
    @DisplayName("Closed declarations")
    class ClosedDeclarations {

        @Test
        @DisplayName("Should register exactly the wire names of the derived fact kinds")
        void shouldRegisterTheDerivedFactKinds() {
            var expected = Arrays.stream(DerivedFactKind.values()).map(DerivedFactKind::wireName).toList();

            assertEquals(expected, List.copyOf(ProductRegistries.factKinds().kinds()),
                    "The fact kinds should be the derived wire names");
        }

        @Test
        @DisplayName("Should register exactly the compiled effective keys")
        void shouldRegisterTheEffectiveKeys() {
            var expected = EffectiveConfigKeys.all().stream().map(EffectiveConfigKey::key).toList();

            assertEquals(expected, List.copyOf(ProductRegistries.configKeys().keys().keySet()),
                    "The config keys should be the compiled table's keys");
        }

        @Test
        @DisplayName("Should hold at least one key of each configuration file")
        void shouldHoldOneKeyPerStem() {
            var keys = ProductRegistries.configKeys().keys().keySet();

            assertTrue(keys.size() > EffectiveConfigKeys.stems().size(),
                    "The registry should hold more keys than stems");
            for (var stem : EffectiveConfigKeys.stems()) {
                assertTrue(keys.stream().anyMatch(key -> key.startsWith(stem + ".")),
                        () -> "The registry should hold a key of the stem " + stem);
            }
        }

        @Test
        @DisplayName("Should refuse a derived fact as a configuration key")
        void shouldRefuseADerivedFactKey() {
            assertFalse(ProductRegistries.configKeys().contains("derived.push_remote"),
                    "A derived fact should be no configuration key");
            assertFalse(ProductRegistries.configKeys().contains("derived"),
                    "The bare derived stem should be no configuration key");
        }
    }

    @Nested
    @DisplayName("The production context")
    class ProductionContext {

        @Test
        @DisplayName("Should hold the compiled defaults beside the two registries")
        void shouldHoldTheCompiledDefaults() {
            var context = ProductRegistries.context(InvariantFixtures.registry(), List.of());

            assertAll("Production context",
                    () -> assertTrue(context.entityKinds().find("decision").isPresent(),
                            "The default entity kinds should be present"),
                    () -> assertTrue(context.questionClasses().find("review").isPresent(),
                            "The default question classes should be present"),
                    () -> assertTrue(context.schemas().contains("question-envelope"),
                            "The default schemas should be present"),
                    () -> assertEquals(ProductRegistries.configKeys(), context.configKeys(),
                            "The context should carry the production config keys"),
                    () -> assertEquals(ProductRegistries.factKinds(), context.factKinds(),
                            "The context should carry the production fact kinds"),
                    () -> assertTrue(context.compiledUnits().isEmpty(),
                            "The context should carry the compiled units it was given"));
        }
    }

    @Nested
    @DisplayName("Value domain conversion")
    class ValueDomainConversion {

        @Test
        @DisplayName("Should convert an unbounded range and refuse a one-sided one")
        void shouldConvertTheRangeBounds() {
            assertAll("Range conversion",
                    () -> assertEquals(Optional.of(new InputType.IntRange(null, null)),
                            ProductRegistries.inputType(new ConfigValueDomain.IntegerRange(null, null)),
                            "An unbounded range should stay a range"),
                    () -> assertEquals(Optional.empty(),
                            ProductRegistries.inputType(new ConfigValueDomain.IntegerRange(1, null)),
                            "A one-sided range should have no input type"));
        }

        @Test
        @DisplayName("Should answer no assignment for a key without a domain")
        void shouldRefuseAssignmentWithoutDomain() {
            assertFalse(ConfigKeyRegistry.assignable(new InputType.Enumeration(List.of("a")), null),
                    "A key without a domain should be assignable to nothing");
        }

        @Test
        @DisplayName("Should carry an enumeration and a two-bounded range as their input types")
        void shouldConvertTheExpressiveDomains() {
            var currency = ProductRegistries.configKeys().find("project.review.currency").orElseThrow();
            var overlap = ProductRegistries.configKeys().find("local-config.epic.overlap_max_files").orElseThrow();
            var verbs = ProductRegistries.configKeys()
                    .find("workflow.plan.phase-5-execute.per_deliverable_verbs").orElseThrow();

            assertAll("Converted domains",
                    () -> assertEquals(new InputType.Enumeration(List.of("exact_sha", "after_last_push")),
                            currency.domain(), "An enumeration should stay an enumeration"),
                    () -> assertEquals(new InputType.IntRange(0, 20), overlap.domain(),
                            "A two-bounded range should stay a bounded range"),
                    () -> assertEquals(new InputType.EnumList(List.of("clean", "compile", "test-compile",
                                    "module-tests", "integration-tests", "e2e", "coverage", "mutation", "benchmark",
                                    "quality-gate", "arch-gate", "verify", "install", "clean-install", "package")),
                            verbs.domain(), "A list of an enumeration should stay a list of it"));
        }

        @Test
        @DisplayName("Should carry no domain where no closed input type expresses it")
        void shouldCarryNoDomainForTheInexpressible() {
            var text = ProductRegistries.configKeys().find("project.base_branch").orElseThrow();
            var oneSided = ProductRegistries.configKeys()
                    .find("local-config.epic.parallelization_scope").orElseThrow();
            var union = ProductRegistries.configKeys()
                    .find("local-config.cleanup.plans_after_days").orElseThrow();
            var object = ProductRegistries.configKeys().find("local-config.commit_trailer").orElseThrow();
            var textList = ProductRegistries.configKeys()
                    .find("project.review.required_bots").orElseThrow();

            assertAll("Inexpressible domains",
                    () -> assertNull(text.domain(), "Text should be no closed input type"),
                    () -> assertNull(oneSided.domain(), "A one-sided range should be no closed input type"),
                    () -> assertNull(union.domain(), "A union should be no closed input type"),
                    () -> assertNull(object.domain(), "An object should be no closed input type"),
                    () -> assertNull(textList.domain(), "A list of text should be no closed input type"));
        }

        @Test
        @DisplayName("Should keep the compiled-default flag of every key")
        void shouldKeepTheCompiledDefaultFlag() {
            assertTrue(ProductRegistries.configKeys().find("project.review.currency").orElseThrow().compiledDefault(),
                    "The review currency key should keep its default");
            assertFalse(ProductRegistries.configKeys().find("project.base_branch").orElseThrow().compiledDefault(),
                    "The base branch key should keep its absent default");
        }
    }
}
