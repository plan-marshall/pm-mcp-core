/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The grammar and the lookup of the effective configuration keys. The keys themselves are the contract (Store Schemas
 * 07, Effective Configuration and Sources), so every literal here is the value under test and no generator applies.
 */
@EnableGeneratorController
@DisplayName("Effective configuration keys")
class EffectiveConfigKeysTest {

    @Nested
    @DisplayName("Key grammar refusals")
    class GrammarRefusals {

        @ParameterizedTest
        @ValueSource(strings = {
                "config.review.currency",
                "derivd.push_remote",
                "derived.push_remote",
                "project",
                "project..currency",
                "project.a..b",
                ".project.a",
                "project.review.",
                "project.Review",
                "project.*x",
                "project.re-view.x*y"})
        @DisplayName("Should refuse a key whose effective-key form breaks the grammar")
        void shouldRefuseMalformedKey(String source) {
            var domain = new ConfigValueDomain.Text();
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> new EffectiveConfigKey(source, domain, false),
                    "Malformed effective key should be refused");

            assertNotNull(exception.getMessage(), "Refusal should state its reason");
            assertTrue(exception.getMessage().contains(source),
                    "Refusal should name the refused key");
        }

        @Test
        @DisplayName("Should name the derived-fact reading of a derived stem")
        void shouldExplainTheDerivedStem() {
            var domain = new ConfigValueDomain.Bool();
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> new EffectiveConfigKey("derived.push_remote", domain, false),
                    "A derived stem should be refused");

            assertTrue(exception.getMessage().contains("derived fact"),
                    "Refusal should say a fact is read by kind");
        }

        @Test
        @DisplayName("Should refuse a missing key or a missing domain")
        void shouldRefuseMissingComponents() {
            var domain = new ConfigValueDomain.Text();
            assertThrows(NullPointerException.class,
                    () -> new EffectiveConfigKey(null, domain, false),
                    "A missing key should be refused");
            assertThrows(NullPointerException.class,
                    () -> new EffectiveConfigKey("project.base_branch", null, false),
                    "A missing domain should be refused");
        }
    }

    @Nested
    @DisplayName("Domain refusals")
    class DomainRefusals {

        @Test
        @DisplayName("Should refuse an empty and a duplicated enumeration")
        void shouldRefuseBrokenEnumeration() {
            var empty = List.<String>of();
            var duplicated = List.of("a", "a");
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigValueDomain.Enumeration(empty),
                    "An empty value set should be refused");
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigValueDomain.Enumeration(duplicated),
                    "A duplicated value should be refused");
        }

        @Test
        @DisplayName("Should refuse a range with its bounds the wrong way round")
        void shouldRefuseInvertedRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigValueDomain.IntegerRange(9, 3),
                    "An inverted range should be refused");
        }

        @Test
        @DisplayName("Should refuse a union of fewer than two alternatives")
        void shouldRefuseShortUnion() {
            var alternatives = List.<ConfigValueDomain>of(new ConfigValueDomain.Text());
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigValueDomain.Union(alternatives),
                    "A one-member union should be refused");
        }

        @Test
        @DisplayName("Should refuse a union that repeats an alternative")
        void shouldRefuseRepeatedUnionAlternative() {
            var repeated = List.<ConfigValueDomain>of(new ConfigValueDomain.Text(), new ConfigValueDomain.Text());
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigValueDomain.Union(repeated),
                    "A repeated union alternative should be refused");
        }

        @Test
        @DisplayName("Should refuse a list without an element domain")
        void shouldRefuseListWithoutElement() {
            assertThrows(NullPointerException.class,
                    () -> new ConfigValueDomain.ListOf(null),
                    "A list without an element should be refused");
        }
    }

    @Nested
    @DisplayName("Lookup")
    class Lookup {

        @Test
        @DisplayName("Should find a key by its exact effective-key form")
        void shouldFindExactKey() {
            var found = EffectiveConfigKeys.find("project.review.currency");

            assertTrue(found.isPresent(), "The review currency key should be found");
            assertEquals(new ConfigValueDomain.Enumeration(List.of("exact_sha", "after_last_push")),
                    found.get().domain(), "The key should carry the schema enumeration");
            assertTrue(found.get().hasCompiledDefault(), "The review currency key has a default");
        }

        @Test
        @DisplayName("Should find a wildcard key from a concrete segment")
        void shouldFindWildcardKey() {
            var found = EffectiveConfigKeys.find(
                    "workflow.plan.phase-6-finalize.steps.default:mutation.lane");

            assertTrue(found.isPresent(), "A concrete map member should match its wildcard");
            assertEquals("workflow.plan.phase-6-finalize.steps.*.lane", found.get().key(),
                    "The wildcard key should be answered, not a prefix");
        }

        @Test
        @DisplayName("Should answer no key for a misspelt or over-long form")
        void shouldAnswerNoKey() {
            assertFalse(EffectiveConfigKeys.find("project.review.curency").isPresent(),
                    "A misspelt key should find none");
            assertFalse(EffectiveConfigKeys.find("local-config.commit_trailer.email.x").isPresent(),
                    "A longer form should find none");
            assertFalse(EffectiveConfigKeys.find("local-config.nothing").isPresent(),
                    "An unknown path should find none");
        }

        @Test
        @DisplayName("Should hold an object key and each of its members")
        void shouldHoldObjectAndMember() {
            var object = EffectiveConfigKeys.find("local-config.commit_trailer");
            var member = EffectiveConfigKeys.find("local-config.commit_trailer.email");

            assertTrue(object.isPresent(), "The trailer object should be a key");
            assertEquals(new ConfigValueDomain.Object(), object.get().domain(),
                    "An object node should carry the object domain");
            assertTrue(member.isPresent(), "The trailer email should be a key of its own");
            assertEquals(new ConfigValueDomain.Text(), member.get().domain(),
                    "The email member should carry the text domain");
        }
    }

    @Nested
    @DisplayName("Table invariants")
    class TableInvariants {

        @Test
        @DisplayName("Should name the three file stems and only them")
        void shouldNameTheThreeStems() {
            assertEquals(Set.of("project", "workflow", "local-config"), EffectiveConfigKeys.stems(),
                    "The stems should be the three configuration files");
        }

        @Test
        @DisplayName("Should refuse a change to the table and to the stems")
        void shouldBeImmutable() {
            var keys = EffectiveConfigKeys.all();
            var stems = EffectiveConfigKeys.stems();
            var extra = new EffectiveConfigKey("project.extra", new ConfigValueDomain.Text(), false);

            assertThrows(UnsupportedOperationException.class,
                    () -> keys.add(extra),
                    "The table should refuse an addition");
            assertThrows(UnsupportedOperationException.class,
                    () -> stems.add("derived"),
                    "The stem set should refuse an addition");
        }
    }
}
