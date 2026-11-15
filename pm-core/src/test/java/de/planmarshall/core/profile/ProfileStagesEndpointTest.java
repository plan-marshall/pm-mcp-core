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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The records of the four endpoint stages (PM-CLIENT-5, Client Profiles § Verification of an Endpoint): what each stage
 * observed, the values it settles and the status a record keeps, and the combinations a record refuses.
 */
@EnableGeneratorController
@DisplayName("Endpoint verification stages")
class ProfileStagesEndpointTest {

    private static final String EXECUTABLE = "/usr/local/bin/" + Generators.letterStrings(3, 10).next();
    private static final String VERSION = Generators.integers(1, 9).next() + ".0.0";
    private static final String MODEL = Generators.letterStrings(3, 10).next();
    private static final String WEIGHTS = "%040x".formatted(Generators.longs().next());

    private static ProfileStages.EndpointIdentity identity(boolean passed) {
        return new ProfileStages.EndpointIdentity(passed, passed ? EXECUTABLE : null, VERSION,
                List.of(MODEL), Map.of(MODEL, WEIGHTS));
    }

    @Test
    @DisplayName("a passed run settles every value the stages observed")
    void passedRun() {
        var result = new ProfileStages.EndpointVerification(identity(true),
                new ProfileStages.EndpointLaunch(true, true, "enforced", true),
                new ProfileStages.EndpointClasses(false, true), new ProfileStages.EndpointLimits(true, 8192));

        assertAll(
                () -> assertFalse(result.refusesDraft(), "passed run does not refuse the draft"),
                () -> assertTrue(result.identity().probeDeclared(), "identity probe is declared"),
                () -> assertEquals("tools_ignored", result.classes().results().get("tools"),
                        "tools class is ignored on the wire"),
                () -> assertEquals("none", result.classes().results().get("tool_call"),
                        "tool_call class has no result"),
                () -> assertEquals("passed", result.stageStatuses().get("limits"), "limits stage reports passed"),
                () -> assertEquals(8, result.values().size(), "eight values are settled"),
                () -> assertTrue(result.values().stream()
                                .allMatch(value -> value.status() == ProfileStages.EndpointValueStatus.MEASURED),
                        "every value is measured"));
    }

    @Test
    @DisplayName("a failed launch and an unsettled limit are recorded as such")
    void failedRun() {
        var result = new ProfileStages.EndpointVerification(identity(true),
                new ProfileStages.EndpointLaunch(false, false, "none", false),
                new ProfileStages.EndpointClasses(false, false), new ProfileStages.EndpointLimits(false, 0));

        assertAll(
                () -> assertTrue(result.refusesDraft(), "failed run refuses the draft"),
                () -> assertEquals("failed", result.stageStatuses().get("launch"), "launch stage reports failed"),
                () -> assertEquals("not_run", result.stageStatuses().get("limits"), "limits stage is not run"),
                () -> assertEquals(ProfileStages.EndpointValueStatus.NOT_MEASURED,
                        result.values().get(5).status(), "unsettled limit is not measured"));
    }

    @Test
    @DisplayName("a record refuses a passed stage without what it observed, and a schema outside the measured set")
    void refusals() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointIdentity(true, null, null, List.of(), Map.of()),
                        "passed identity needs an executable"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointLaunch(true, true, "unknown", true),
                        "passed launch needs a known status"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointLimits(true, 0),
                        "passed limits need a positive value"));
    }
}
