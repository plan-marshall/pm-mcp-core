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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The typed projection of a profile's worker and connection values (PM-WORK-3, PM-CLIENT-2): the compiled values of the
 * two eligible hosts, the derived silence limit, the values an eligible host must declare, and the absence an ineligible
 * host or an unmeasured deadline legitimately carries.
 */
@DisplayName("Worker parameters of a client profile")
class WorkerParametersTest {

    private static LoadedProfile shipped(String clientId) {
        var document = ShippedProfiles.read().shipped().get(clientId).document();
        var tree = ProfileValidator.validate(document, "profiles/" + clientId + ".json", ProfileRules.none());
        return new LoadedProfile(clientId, ProfileSetKind.SHIPPED, tree, document, LoadedProfile.State.VERIFIED, null);
    }

    private static LoadedProfile loaded(ProfileFixtures.ProfileFixture fixture) {
        var bytes = fixture.bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        return new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes, LoadedProfile.State.VERIFIED, null);
    }

    @Nested
    @DisplayName("the compiled profiles")
    class CompiledProfiles {

        @Test
        @DisplayName("claude is eligible and declares its silence grace and idle wake-ups, but no acknowledgement deadline")
        void claude() {
            var parameters = WorkerParameters.of(shipped("claude"));

            assertAll("claude worker parameters",
                    () -> assertTrue(parameters.eligible(), "claude may host workers"),
                    () -> assertTrue(parameters.ackDeadlineSeconds().isEmpty(),
                            "claude declares no acknowledgement deadline"),
                    () -> assertFalse(parameters.ackInformational(), "claude's acknowledgement is not informational"),
                    () -> assertEquals(30, parameters.silenceGraceSeconds(), "claude's silence grace is 30 seconds"),
                    () -> assertEquals(3, parameters.idleWakeups(), "claude recycles after 3 idle wake-ups"),
                    () -> assertEquals(240, parameters.boundedWaitSeconds(), "claude's bounded wait is 240 seconds"),
                    () -> assertTrue(parameters.progressResetsTimeout(), "claude's progress resets its timeout"),
                    () -> assertEquals(270, parameters.silenceLimitSeconds(),
                            "claude's silence limit is the bounded wait plus the grace"));
        }

        @Test
        @DisplayName("opencode is eligible with an informational acknowledgement deadlined at its silence limit")
        void opencode() {
            var parameters = WorkerParameters.of(shipped("opencode"));

            assertAll("opencode worker parameters",
                    () -> assertTrue(parameters.eligible(), "opencode may host workers"),
                    () -> assertEquals(270, parameters.ackDeadlineSeconds().orElseThrow(),
                            "opencode's acknowledgement deadline is 270 seconds"),
                    () -> assertTrue(parameters.ackInformational(),
                            "opencode's acknowledgement is informational"),
                    () -> assertEquals(30, parameters.silenceGraceSeconds(), "opencode's silence grace is 30 seconds"),
                    () -> assertEquals(3, parameters.idleWakeups(), "opencode recycles after 3 idle wake-ups"),
                    () -> assertEquals(240, parameters.boundedWaitSeconds(),
                            "opencode's bounded wait is 240 seconds"),
                    () -> assertTrue(parameters.progressResetsTimeout(), "opencode's progress resets its timeout"),
                    () -> assertEquals(270, parameters.silenceLimitSeconds(),
                            "opencode's silence limit is the bounded wait plus the grace"));
        }
    }

    @Nested
    @DisplayName("the informational acknowledgement")
    class InformationalAcknowledgement {

        @Test
        @DisplayName("an informational acknowledgement deadlines at the silence limit")
        void informationalDeadlineIsTheSilenceLimit() {
            var parameters = WorkerParameters.of(shipped("opencode"));

            assertEquals(parameters.silenceLimitSeconds(), parameters.ackDeadlineSeconds().orElseThrow(),
                    "an informational acknowledgement is deadlined at the silence limit");
        }

        @Test
        @DisplayName("an operator override of the bounded wait moves the silence limit with it")
        void overrideMovesTheSilenceLimit() {
            var parameters = WorkerParameters.of(
                    loaded(ProfileFixtures.valid().set("/connection/bounded_wait_seconds", 120)));

            assertAll("overridden worker parameters",
                    () -> assertEquals(120, parameters.boundedWaitSeconds(), "the bounded wait is the overridden one"),
                    () -> assertEquals(150, parameters.silenceLimitSeconds(),
                            "the silence limit follows the overridden bounded wait"));
        }
    }

    @Nested
    @DisplayName("a missing value an eligible host requires")
    class MissingRequiredValue {

        @Test
        @DisplayName("an eligible host without a silence grace is refused at its pointer")
        void silenceGrace() {
            var profile = loaded(ProfileFixtures.valid().set("/worker/silence_grace_seconds", null));

            var refusal = assertThrows(ProfileRefusal.class, () -> WorkerParameters.of(profile));

            assertAll("silence grace refusal",
                    () -> assertEquals("/worker/silence_grace_seconds", refusal.pointer(),
                            "the refusal names the missing silence grace"),
                    () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                            "the missing value refuses the profile"));
        }

        @Test
        @DisplayName("an eligible host without idle wake-ups is refused at its pointer")
        void idleWakeups() {
            var profile = loaded(ProfileFixtures.valid().set("/worker/idle_wakeups", null));

            var refusal = assertThrows(ProfileRefusal.class, () -> WorkerParameters.of(profile));

            assertAll("idle wake-ups refusal",
                    () -> assertEquals("/worker/idle_wakeups", refusal.pointer(),
                            "the refusal names the missing idle wake-ups"),
                    () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                            "the missing value refuses the profile"));
        }

        @Test
        @DisplayName("an eligible host without an acknowledgement form is refused at its pointer")
        void ackInformational() {
            var profile = loaded(ProfileFixtures.valid().set("/worker/ack_informational", null));

            var refusal = assertThrows(ProfileRefusal.class, () -> WorkerParameters.of(profile));

            assertAll("acknowledgement form refusal",
                    () -> assertEquals("/worker/ack_informational", refusal.pointer(),
                            "the refusal names the missing acknowledgement form"),
                    () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                            "the missing value refuses the profile"));
        }
    }

    @Nested
    @DisplayName("an ineligible host")
    class IneligibleHost {

        @Test
        @DisplayName("a declared value does not make an ineligible host eligible")
        void declaredValuesHaveNoEffect() {
            var profile = loaded(ProfileFixtures.valid()
                    .set("/worker/eligible", false)
                    .set("/worker/ack_deadline_seconds", 600)
                    .set("/worker/ack_informational", false)
                    .set("/worker/silence_grace_seconds", 600)
                    .set("/worker/idle_wakeups", 100));

            var parameters = WorkerParameters.of(profile);

            assertAll("declared values of an ineligible host",
                    () -> assertFalse(parameters.eligible(), "a declared value does not make the host eligible"),
                    () -> assertEquals(600, parameters.ackDeadlineSeconds().orElseThrow(),
                            "the declared deadline is carried as declared"),
                    () -> assertEquals(600, parameters.silenceGraceSeconds(),
                            "the declared grace is carried as declared"),
                    () -> assertEquals(100, parameters.idleWakeups(),
                            "the declared idle wake-ups are carried as declared"));
        }

        @Test
        @DisplayName("a host that declares no worker value is projected without refusal")
        void absentValuesProjectWithoutRefusal() {
            var profile = loaded(ProfileFixtures.valid()
                    .set("/worker/eligible", false)
                    .set("/worker/ack_deadline_seconds", null)
                    .set("/worker/ack_informational", null)
                    .set("/worker/silence_grace_seconds", null)
                    .set("/worker/idle_wakeups", null));

            var parameters = WorkerParameters.of(profile);

            assertAll("ineligible host parameters",
                    () -> assertFalse(parameters.eligible(), "the host is ineligible"),
                    () -> assertTrue(parameters.ackDeadlineSeconds().isEmpty(),
                            "the host declares no acknowledgement deadline"),
                    () -> assertFalse(parameters.ackInformational(), "the host declares no acknowledgement form"),
                    () -> assertEquals(0, parameters.silenceGraceSeconds(), "the host declares no silence grace"),
                    () -> assertEquals(0, parameters.idleWakeups(), "the host declares no idle wake-ups"),
                    () -> assertEquals(240, parameters.boundedWaitSeconds(),
                            "the connection's bounded wait is declared regardless of eligibility"));
        }
    }
}
