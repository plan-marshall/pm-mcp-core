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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of the endpoint stage results of {@link ProfileStages} build the values the canonical constructors
 * build, and a missing required component is refused with the same refusal.
 */
@DisplayName("Profile stage builders")
class ProfileStagesBuilderContractTest {

    @Test
    @DisplayName("the identity builder builds the constructor's value")
    void shouldBuildIdentityLikeTheConstructor() {
        var canonical = new ProfileStages.EndpointIdentity(true, "/usr/local/bin/acme", "1.2.3", List.of("acme-1"),
                Map.of("acme-1", "sha256:abc"));
        var built = ProfileStages.EndpointIdentity.builder().passed(canonical.passed())
                .executablePath(canonical.executablePath()).version(canonical.version())
                .models(canonical.models()).weights(canonical.weights()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the launch builder builds the constructor's value and refuses alike")
    void shouldBuildLaunchLikeTheConstructorAndRefuseAlike() {
        var probes = Map.of("two_options_ascending", "valid");

        var canonical = new ProfileStages.EndpointLaunch(true, true, "enforced", true, probes);
        var built = ProfileStages.EndpointLaunch.builder().passed(canonical.passed())
                .readiness(canonical.readiness()).resultSchema(canonical.resultSchema())
                .streamingOff(canonical.streamingOff()).probes(canonical.probes()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ProfileStages.EndpointLaunch(true, true, null, true, probes),
                            "the constructor refuses a null resultSchema");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ProfileStages.EndpointLaunch.builder().passed(true).readiness(true)
                                    .streamingOff(true).probes(probes).build(),
                            "the builder refuses a missing resultSchema");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent resultSchema with the same message");
                });
    }

    @Test
    @DisplayName("the verification builder builds the constructor's value and refuses alike")
    void shouldBuildVerificationLikeTheConstructorAndRefuseAlike() {
        var identity = new ProfileStages.EndpointIdentity(true, "/usr/local/bin/acme", "1.2.3", List.of("acme-1"),
                Map.of());
        var launch = new ProfileStages.EndpointLaunch(true, true, "enforced", true, Map.of());
        var classes = new ProfileStages.EndpointClasses(true, false);
        var limits = new ProfileStages.EndpointLimits(true, 32_768);

        var canonical = new ProfileStages.EndpointVerification(identity, launch, classes, limits,
                ProfileStages.ENDPOINT_STAGES);
        var built = ProfileStages.EndpointVerification.builder().identity(canonical.identity())
                .launch(canonical.launch()).classes(canonical.classes()).limits(canonical.limits())
                .stages(canonical.stages()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ProfileStages.EndpointVerification(null, launch, classes, limits,
                                    ProfileStages.ENDPOINT_STAGES),
                            "the constructor refuses a null identity");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ProfileStages.EndpointVerification.builder().launch(launch).classes(classes)
                                    .limits(limits).stages(ProfileStages.ENDPOINT_STAGES).build(),
                            "the builder refuses a missing identity");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent identity with the same message");
                });
    }
}
