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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The records of the four endpoint stages (PM-CLIENT-5, Client Profiles § Verification of an Endpoint): what each stage
 * observed, the values it settles and the status a record keeps, and the combinations a record refuses.
 */
@DisplayName("Endpoint verification stages")
class ProfileStagesEndpointTest {

    private static ProfileStages.EndpointIdentity identity(boolean passed) {
        return new ProfileStages.EndpointIdentity(passed, passed ? "/usr/local/bin/runner" : null, "1.0.0",
                List.of("mid"), Map.of("mid", "abc"));
    }

    @Test
    @DisplayName("a passed run settles every value the stages observed")
    void passedRun() {
        var result = new ProfileStages.EndpointVerification(identity(true),
                new ProfileStages.EndpointLaunch(true, true, "enforced", true),
                new ProfileStages.EndpointClasses(false, true), new ProfileStages.EndpointLimits(true, 8192));

        assertAll(
                () -> assertFalse(result.refusesDraft()),
                () -> assertTrue(result.identity().probeDeclared()),
                () -> assertEquals("tools_ignored", result.classes().results().get("tools")),
                () -> assertEquals("none", result.classes().results().get("tool_call")),
                () -> assertEquals("passed", result.stageStatuses().get("limits")),
                () -> assertEquals(8, result.values().size()),
                () -> assertTrue(result.values().stream()
                        .allMatch(value -> value.status() == ProfileStages.EndpointValueStatus.MEASURED)));
    }

    @Test
    @DisplayName("a failed launch and an unsettled limit are recorded as such")
    void failedRun() {
        var result = new ProfileStages.EndpointVerification(identity(true),
                new ProfileStages.EndpointLaunch(false, false, "none", false),
                new ProfileStages.EndpointClasses(false, false), new ProfileStages.EndpointLimits(false, 0));

        assertAll(
                () -> assertTrue(result.refusesDraft()),
                () -> assertEquals("failed", result.stageStatuses().get("launch")),
                () -> assertEquals("not_run", result.stageStatuses().get("limits")),
                () -> assertEquals(ProfileStages.EndpointValueStatus.NOT_MEASURED,
                        result.values().get(5).status()));
    }

    @Test
    @DisplayName("a record refuses a passed stage without what it observed, and a schema outside the measured set")
    void refusals() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointIdentity(true, null, null, List.of(), Map.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointLaunch(true, true, "unknown", true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.EndpointLimits(true, 0)));
    }
}
