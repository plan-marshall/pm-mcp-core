/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The registry through which a consumer binds a fact kind to its value schema (Store Schemas § Derived Facts Store,
 * the kind table; PM-EXT-6): registration, the duplicate refusal, the value validation of a fixture schema, the
 * operator-only flag and the empty fresh registry. No concrete kind is registered by this plan, so the test registers
 * fixture specs of its own.
 */
@EnableGeneratorController
@DisplayName("Derived fact kinds")
class DerivedFactKindsTest {

    private static final String FIXTURE_SCHEMA = "{\"type\":\"object\",\"required\":[\"name\",\"count\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"},"
            + "\"count\":{\"type\":\"integer\",\"minimum\":1}}}";

    private static DerivedFactKindSpec fixtureSpec() {
        return DerivedFactKindSpec.builder()
                .schema(JsonValue.parse(FIXTURE_SCHEMA.getBytes(UTF_8)))
                .classes(Set.of(DerivedFact.DerivationClass.LOCAL))
                .qualifierAllowed(false)
                .inputs("the descriptor tree hash of the worktree")
                .build();
    }

    private static JsonValue value(String json) {
        return JsonValue.parse(json.getBytes(UTF_8));
    }

    @Test
    @DisplayName("a registered kind answers its specification and its properties")
    void registeredKindAnswersItsSpecification() {
        var registry = new DerivedFactKinds();
        var spec = fixtureSpec();

        registry.register(DerivedFactKind.MODULE_DISCOVERY, spec);

        assertAll(
                () -> assertEquals(Optional.of(spec), registry.spec(DerivedFactKind.MODULE_DISCOVERY),
                        "The registered kind answers its specification"),
                () -> assertEquals(Set.of(DerivedFact.DerivationClass.LOCAL), spec.classes(),
                        "The class set is carried"),
                () -> assertFalse(spec.qualifierAllowed(), "The fixture kind admits no qualifier"),
                () -> assertEquals("the descriptor tree hash of the worktree", spec.inputs(),
                        "The inputs are carried"));
    }

    @Test
    @DisplayName("a second registration of the same kind is refused")
    void secondRegistrationIsRefused() {
        var registry = new DerivedFactKinds();
        registry.register(DerivedFactKind.MODULE_DISCOVERY, fixtureSpec());

        assertThrows(IllegalStateException.class,
                () -> registry.register(DerivedFactKind.MODULE_DISCOVERY, fixtureSpec()),
                "A second registration is refused");
    }

    @Test
    @DisplayName("a schema that is not a JSON object and an empty class set are refused")
    void specOutsideItsShapeIsRefused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> DerivedFactKindSpec.builder().schema(value("[]"))
                                .classes(Set.of(DerivedFact.DerivationClass.LOCAL)).inputs("inputs").build(),
                        "A schema that is not an object is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> DerivedFactKindSpec.builder().schema(value(FIXTURE_SCHEMA)).classes(Set.of())
                                .inputs("inputs").build(),
                        "An empty class set is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> DerivedFactKindSpec.builder().schema(value(FIXTURE_SCHEMA))
                                .classes(Set.of(DerivedFact.DerivationClass.LOCAL)).inputs(" ").build(),
                        "Blank inputs are refused"));
    }

    @Test
    @DisplayName("a value accepted by the fixture schema has no violation")
    void acceptedValueHasNoViolation() {
        var registry = new DerivedFactKinds();
        registry.register(DerivedFactKind.MODULE_DISCOVERY, fixtureSpec());
        var name = Generators.letterStrings(1, 20).next();

        var violations = registry.validate(DerivedFactKind.MODULE_DISCOVERY,
                value("{\"name\":\"" + name + "\",\"count\":1}"));

        assertEquals(List.of(), violations, "A value of the fixture schema is valid");
    }

    @Test
    @DisplayName("a value refused by the fixture schema reports each violation")
    void refusedValueReportsItsViolations() {
        var registry = new DerivedFactKinds();
        registry.register(DerivedFactKind.MODULE_DISCOVERY, fixtureSpec());

        assertAll(
                () -> assertFalse(registry.validate(DerivedFactKind.MODULE_DISCOVERY, value("{\"name\":\"x\"}"))
                        .isEmpty(), "A missing required member is reported"),
                () -> assertFalse(registry.validate(DerivedFactKind.MODULE_DISCOVERY,
                        value("{\"name\":\"x\",\"count\":\"one\"}")).isEmpty(), "A mistyped member is reported"),
                () -> assertFalse(registry.validate(DerivedFactKind.MODULE_DISCOVERY,
                                value("{\"name\":\"x\",\"count\":1,\"extra\":true}")).isEmpty(),
                        "An extra member is reported"),
                () -> assertFalse(registry.validate(DerivedFactKind.MODULE_DISCOVERY,
                        value("{\"name\":\"x\",\"count\":0}")).isEmpty(), "A member below its bound is reported"));
    }

    @Test
    @DisplayName("agent_identity alone is operator-only, registered or not")
    void operatorOnlyIsIndependentOfRegistration() {
        var registry = new DerivedFactKinds();

        assertAll(
                () -> assertTrue(registry.isOperatorOnly(DerivedFactKind.AGENT_IDENTITY),
                        "agent_identity is operator-only"),
                () -> assertTrue(Arrays.stream(DerivedFactKind.values())
                                .filter(kind -> kind != DerivedFactKind.AGENT_IDENTITY)
                                .noneMatch(registry::isOperatorOnly),
                        "Every other kind is not operator-only"));
    }

    @Test
    @DisplayName("a fresh registry registers nothing and validates no value")
    void freshRegistryRegistersNothing() {
        var registry = new DerivedFactKinds();

        assertAll(
                () -> assertTrue(Arrays.stream(DerivedFactKind.values())
                                .allMatch(kind -> registry.spec(kind).isEmpty()),
                        "A fresh registry registers no kind"),
                () -> assertAll(Arrays.stream(DerivedFactKind.values())
                        .map(kind -> (Executable) () -> assertThrows(IllegalStateException.class,
                                () -> registry.validate(kind, value("{}"))))));
    }
}
