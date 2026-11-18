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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.plan.CompositionEvent;
import de.planmarshall.workflow.plan.CompositionRequest;
import de.planmarshall.workflow.plan.CompositionResult;
import de.planmarshall.workflow.plan.CompositionRules;
import de.planmarshall.workflow.plan.InstancePosition;
import de.planmarshall.workflow.plan.PlanFacts;
import de.planmarshall.workflow.plan.PlanInstanceComposer;
import de.planmarshall.workflow.plan.PlanInstanceHeader;
import de.planmarshall.workflow.plan.UnitReference;
import de.planmarshall.workflow.verify.InvariantFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three verifier call sites read the production registries: a unit that declares the real key
 * {@code project.review.currency} and the real kind {@code push_remote} passes, a misspelt key, an unknown kind, a
 * derived fact named as a key and a fallback key without a compiled default are each refused under the rule that owns
 * them, and the same passing unit is refused under the empty registries of the test constructors (the control of the
 * plan's Decision 7).
 * <p>
 * The fixtures live below {@code unit-fixtures/product-registries/} and are written for this test, never copied from the
 * product. The project loader reads the passing tree a second time with CRLF line endings (R15 item 2), materialized in
 * the test's temporary project because a unit file is written by a person's editor.
 */
@EnableGeneratorController
@DisplayName("The production registries at the verifier call sites")
class ProductRegistriesWiringTest {

    private static final String ROOT = "unit-fixtures/product-registries/";
    private static final String PASSING = "declared-reads-passing.pmwf";
    private static final String UNKNOWN_KEY = "config-key-unknown.pmwf";
    private static final String UNKNOWN_FACT = "fact-kind-unknown.pmwf";
    private static final String DERIVED_KEY = "config-key-derived.pmwf";
    private static final String NO_DEFAULT = "fallback-without-compiled-default.pmwf";

    private static final VerificationContext PRODUCT =
            ProductRegistries.context(InvariantFixtures.registry(), List.of());
    private static final VerificationContext EMPTY = UnitFixtures.context();

    /** A well-formed digest for the request's template reference and config hash, which the test does not exercise. */
    private static final String DIGEST = "0".repeat(64);

    @TempDir
    Path temp;

    private Path project;

    @BeforeEach
    void projectDirectory() {
        project = temp.resolve("project");
    }

    @Nested
    @DisplayName("The default unit loader")
    class DefaultLoader {

        @Test
        @DisplayName("Should pass the unit whose reads the product registries hold")
        void shouldPassThePassingUnit() {
            var result = loadDefault(PASSING, PRODUCT);

            assertTrue(result.isVerified(), result.findings()::toString);
        }

        @Test
        @DisplayName("Should refuse the passing unit under the empty registries")
        void shouldRefuseUnderTheEmptyRegistries() {
            var result = loadDefault(PASSING, EMPTY);

            assertFalse(result.isVerified(), "The control should be refused");
            assertEquals(List.of("CONFIG_KEY_BINDING", "FACT_KIND_BINDING"), codes(result),
                    "The empty registries should refuse both declared reads");
        }

        @Test
        @DisplayName("Should refuse an unknown key, an unknown kind, a derived key and a default-less fallback")
        void shouldRefuseEachDeclaration() {
            assertEquals(List.of("CONFIG_KEY_BINDING"), codes(loadDefault(UNKNOWN_KEY, PRODUCT)),
                    "A misspelt key should be refused as a config binding");
            assertEquals(List.of("FACT_KIND_BINDING"), codes(loadDefault(UNKNOWN_FACT, PRODUCT)),
                    "An unknown kind should be refused as a fact binding");
            assertEquals(List.of("CONFIG_KEY_BINDING"), codes(loadDefault(DERIVED_KEY, PRODUCT)),
                    "A derived fact named as a key should be refused");
            assertEquals(List.of("INPUT_DECLARATION"), codes(loadDefault(NO_DEFAULT, PRODUCT)),
                    "A fallback without a compiled default should be refused");
        }
    }

    @Nested
    @DisplayName("The project unit loader")
    class ProjectLoader {

        @Test
        @DisplayName("Should pass the unit whose reads the product registries hold")
        void shouldPassThePassingUnit() {
            var result = loadProject(PASSING, read(PASSING), PRODUCT);

            assertTrue(result.isVerified(), result.findings()::toString);
        }

        @Test
        @DisplayName("Should pass the same tree with CRLF line endings")
        void shouldPassTheCrlfTwin() {
            var crlf = read(PASSING).replace("\n", "\r\n");

            var result = loadProject("declared-reads-crlf.pmwf", crlf, PASSING, PRODUCT);

            assertTrue(result.isVerified(), result.findings()::toString);
        }

        @Test
        @DisplayName("Should refuse the passing unit under the empty registries")
        void shouldRefuseUnderTheEmptyRegistries() {
            var result = loadProject(PASSING, read(PASSING), EMPTY);

            assertFalse(result.isVerified(), "The control should be refused");
            assertEquals(List.of("CONFIG_KEY_BINDING", "FACT_KIND_BINDING"), codes(result),
                    "The empty registries should refuse both declared reads");
        }

        @Test
        @DisplayName("Should refuse an unknown key, an unknown kind, a derived key and a default-less fallback")
        void shouldRefuseEachDeclaration() {
            assertEquals(List.of("CONFIG_KEY_BINDING"), codes(loadProject(UNKNOWN_KEY, read(UNKNOWN_KEY), PRODUCT)),
                    "A misspelt key should be refused as a config binding");
            assertEquals(List.of("FACT_KIND_BINDING"), codes(loadProject(UNKNOWN_FACT, read(UNKNOWN_FACT), PRODUCT)),
                    "An unknown kind should be refused as a fact binding");
            assertEquals(List.of("CONFIG_KEY_BINDING"), codes(loadProject(DERIVED_KEY, read(DERIVED_KEY), PRODUCT)),
                    "A derived fact named as a key should be refused");
            assertEquals(List.of("INPUT_DECLARATION"), codes(loadProject(NO_DEFAULT, read(NO_DEFAULT), PRODUCT)),
                    "A fallback without a compiled default should be refused");
        }
    }

    @Nested
    @DisplayName("The plan instance composer")
    class Composer {

        @Test
        @DisplayName("Should verify the composed unit against the product registries")
        void shouldCarryTheProductRegistries() {
            var product = violations(compose(PRODUCT));
            var empty = violations(compose(EMPTY));

            assertFalse(product.stream().anyMatch(violation -> violation.contains("CONFIG_KEY_BINDING")),
                    () -> "The product context should accept the declared key: " + product);
            assertTrue(empty.stream().anyMatch(violation -> violation.contains("CONFIG_KEY_BINDING")),
                    () -> "The empty registries should refuse the declared key: " + empty);
        }
    }

    private static CompositionResult compose(VerificationContext context) {
        var template = parseDefault(PASSING);
        var request = CompositionRequest.builder()
                .planId("wiring-plan")
                .event(CompositionEvent.START)
                .template(template)
                .templates(List.of(new UnitReference(template.id(), ROOT + PASSING, DIGEST, UnitOrigin.DEFAULT)))
                .subUnits(List.of())
                .facts(PlanFacts.builder().lane(PlanInstanceHeader.Lane.DEEP).posture(PlanInstanceHeader.Posture.FULL)
                        .changeType("feature").build())
                .configHash(DIGEST)
                .waivers(List.of())
                .position(InstancePosition.at("verify.entry"))
                .verificationSteps(List.of())
                .finalizeSteps(List.of())
                .build();
        var composer = new PlanInstanceComposer(context, List.of(), List.of(), noRules(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        return composer.compose(request);
    }

    private static CompositionRules noRules() {
        return ((CompositionRules.Loaded) CompositionRules.parse(
                "{\"format_version\": 1, \"revision\": \"t0\", \"rules\": []}".getBytes(StandardCharsets.UTF_8)))
                .rules();
    }

    private static List<String> violations(CompositionResult result) {
        return switch (result) {
            case CompositionResult.Refused(var refusal, var messages) -> messages;
            case CompositionResult.Composed(var composed) -> List.of();
        };
    }

    private static UnitLoadResult loadDefault(String file, VerificationContext context) {
        return new DefaultUnitLoader(ProductRegistriesWiringTest.class.getClassLoader(), ROOT, List.of(file), context,
                Map.of()).load();
    }

    private UnitLoadResult loadProject(String file, String text, VerificationContext context) {
        return loadProject(file, text, file, context);
    }

    private UnitLoadResult loadProject(String file, String text, String referenceFile, VerificationContext context) {
        var directory = project.resolve(ProjectUnitLoader.CONFIG_DIRECTORY);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(file), projectAuthor(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var reference = parseDefault(referenceFile);
        var loader = new ProjectUnitLoader(context, Map.of(reference.id(), reference), List.of(), List.of(file));
        return loader.load("wiring-project", UnitFixtures.roots(project), List.of(), Set.of());
    }

    /**
     * @param file a fixture of the root, parsed as the compiled unit the project's copy is compared with
     * @return the parsed default unit
     */
    private static WorkflowDefinition parseDefault(String file) {
        return switch (WorkflowParser.parse(ROOT + file, read(file), UnitOrigin.DEFAULT)) {
            case ParseResult.Parsed(var definition) -> definition;
            case ParseResult.Refused(var diagnostic) -> throw new IllegalStateException(
                    "the fixture should parse: " + diagnostic.message());
        };
    }

    private static String projectAuthor(String text) {
        return text.replace("author: plan-marshall", "author: wiring-author");
    }

    private static String read(String file) {
        try (var in = ProductRegistriesWiringTest.class.getClassLoader().getResourceAsStream(ROOT + file)) {
            if (in == null) {
                throw new IllegalStateException("the fixture " + ROOT + file + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> codes(UnitLoadResult result) {
        return result.findings().stream().map(UnitFinding::code).toList();
    }
}
