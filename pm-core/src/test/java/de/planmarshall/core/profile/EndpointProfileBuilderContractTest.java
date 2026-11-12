/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link EndpointProfile} and of its nested records build the values the canonical constructors build,
 * and a missing required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Endpoint profile builders")
class EndpointProfileBuilderContractTest {

    @Test
    @DisplayName("the profile builder builds the constructor's value and refuses alike")
    void shouldBuildProfileLikeTheConstructorAndRefuseAlike() {
        var clientId = Generators.letterStrings(3, 12).next();
        var executable = Generators.letterStrings(3, 12).next();
        var identity = new EndpointProfile.Identity(List.of("--version"), "version ([0-9.]+)");
        var endpoint = new EndpointProfile.Endpoint("openai_chat", "http://127.0.0.1:11434", "/health",
                "/v1/models", "/data", "enforced", 32_768, 4_096, 120);
        var modelTiers = Map.of("frontier", "acme-frontier");

        var canonical = new EndpointProfile(clientId, executable, identity, null, endpoint, modelTiers, null);
        var built = EndpointProfile.builder().clientId(canonical.clientId()).executable(canonical.executable())
                .identity(canonical.identity()).updateEvidence(canonical.updateEvidence())
                .endpoint(canonical.endpoint()).modelTiers(canonical.modelTiers())
                .weightsProbe(canonical.weightsProbe()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new EndpointProfile(null, executable, identity, null, endpoint, modelTiers, null),
                            "the constructor refuses a null clientId");
                    var incomplete = EndpointProfile.builder().executable(executable).identity(identity)
                            .endpoint(endpoint).modelTiers(modelTiers);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing clientId");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent clientId with the same message");
                });
    }

    @Test
    @DisplayName("the endpoint builder builds the constructor's value")
    void shouldBuildEndpointLikeTheConstructor() {
        var canonical = new EndpointProfile.Endpoint("openai_chat", "http://127.0.0.1:11434", "/health",
                "/v1/models", "/data", "enforced", 32_768, 4_096, 120);
        var built = EndpointProfile.Endpoint.builder().protocol(canonical.protocol())
                .baseUrl(canonical.baseUrl())
                .readinessPath(canonical.readinessPath()).modelsPath(canonical.modelsPath())
                .modelsPointer(canonical.modelsPointer()).resultSchema(canonical.resultSchema())
                .contextTokens(canonical.contextTokens()).answerReserveTokens(canonical.answerReserveTokens())
                .requestTimeoutSeconds(canonical.requestTimeoutSeconds()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the weights probe builder builds the constructor's value and refuses alike")
    void shouldBuildWeightsProbeLikeTheConstructorAndRefuseAlike() {
        var argv = List.of("acme", "weights", "{{model}}");
        var pattern = "^(\\S+)$";

        var canonical = new EndpointProfile.WeightsProbe("argv", null, argv, pattern);
        var built = EndpointProfile.WeightsProbe.builder().source(canonical.source())
                .pointer(canonical.pointer()).argv(canonical.argv()).pattern(canonical.pattern()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new EndpointProfile.WeightsProbe(null, null, argv, pattern),
                            "the constructor refuses a null source");
                    var incomplete = EndpointProfile.WeightsProbe.builder().argv(argv).pattern(pattern);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing source");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent source with the same message");
                });
    }
}
