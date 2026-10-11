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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.LifecycleGate.Decision;
import de.planmarshall.core.store.ProjectEnrolment.Build;

/**
 * The lifecycle gate (PM-MIG-1): a constant compiled into the release, never a setting. While it is closed only a
 * pilot project may create a plan or an epic and every other project is refused {@code lifecycle_closed}; once open,
 * {@code pilot} has no effect; a released project is refused whatever the gate. The hazard guarded here is a gate that
 * a key, an environment variable or an edited file could open: the release position is asserted as a constant, and
 * the only other position exists for this test.
 */
@DisplayName("Lifecycle gate")
class LifecycleGateTest {

    private static final Instant RELEASED_AT = Instant.parse("2026-10-12T09:30:00Z");

    private static ProjectEnrolment enrolment(boolean pilot, Instant releasedAt) {
        return new ProjectEnrolment("pm-mcp-core", "/home/dev/git/pm-mcp-core", Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, releasedAt, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    @Test
    @DisplayName("the gate compiled into this release is closed")
    void releasePosition() {
        assertFalse(LifecycleGate.RELEASE.isOpen());
    }

    static Stream<Arguments> decisions() {
        return Stream.of(
                Arguments.of("closed, not a pilot", false, false, null, Decision.LIFECYCLE_CLOSED),
                Arguments.of("closed, pilot", false, true, null, Decision.ADMITTED),
                Arguments.of("closed, released pilot", false, true, RELEASED_AT, Decision.LIFECYCLE_CLOSED),
                Arguments.of("open, not a pilot", true, false, null, Decision.ADMITTED),
                Arguments.of("open, pilot", true, true, null, Decision.ADMITTED),
                Arguments.of("open, released", true, false, RELEASED_AT, Decision.LIFECYCLE_CLOSED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decisions")
    @DisplayName("the gate admits a project or refuses it with lifecycle_closed")
    void decide(String name, boolean open, boolean pilot, Instant releasedAt, Decision expected) {
        var gate = new LifecycleGate(open);

        assertEquals(expected, gate.decide(enrolment(pilot, releasedAt)));
    }

    @Test
    @DisplayName("the release gate refuses a non-pilot project and admits a pilot one")
    void releaseGate() {
        assertAll(
                () -> assertEquals(Decision.LIFECYCLE_CLOSED, LifecycleGate.RELEASE.decide(enrolment(false, null))),
                () -> assertEquals(Decision.ADMITTED, LifecycleGate.RELEASE.decide(enrolment(true, null))),
                () -> assertEquals("lifecycle_closed", Decision.LIFECYCLE_CLOSED.code()),
                () -> assertEquals("admitted", Decision.ADMITTED.code()),
                () -> assertThrows(NullPointerException.class, () -> LifecycleGate.RELEASE.decide(null)));
    }
}
