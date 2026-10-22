/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The selection and the windows of the waits a model holds (PM-WF-8, PM-IMPL-4): a bounded wait answers at its
 * window, a progress wait leaves no gap longer than the interval, and the selection follows the profile's
 * {@code progress_resets_timeout} and the request's progress token in all four combinations. The clock is the
 * fixture's manual clock, so no test waits in wall-clock time.
 */
class ModelWaitTest {

    private static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");

    private static final Duration BOUNDED = Duration.ofSeconds(240);

    private static final Duration IDLE = Duration.ofSeconds(1800);

    private final EngineFixtures.ManualClock clock = new EngineFixtures.ManualClock(T0);

    @Test
    void shouldAnswerABoundedWaitAtItsWindow() {
        var wait = WaitSelection.select(connection(false), Optional.empty(), ModelCall.PM_WAIT, WaitSettings.defaults(),
                clock);

        var bounded = assertInstanceOf(ModelWait.Bounded.class, wait);
        assertEquals(T0, bounded.startedAt());
        assertEquals(BOUNDED, bounded.window());
        assertEquals(T0.plus(BOUNDED), bounded.answerAt());
        assertFalse(bounded.answersAt(bounded.answerAt().minusMillis(1)));
        assertTrue(bounded.answersAt(bounded.answerAt()));
        assertTrue(bounded.progressFrames().isEmpty());
    }

    @Test
    void shouldEmitProgressFramesWithoutAGapLongerThanTheInterval() {
        var settings = WaitSettings.defaults();
        var wait = WaitSelection.select(connection(true), Optional.of("p-1"), ModelCall.PM_BUILD, settings, clock);

        var progress = assertInstanceOf(ModelWait.Progress.class, wait);
        assertEquals(T0.plus(settings.progressWaitMax()), progress.answerAt());
        var frames = progress.progressFrames();
        assertFalse(frames.isEmpty());
        var points = new ArrayList<Instant>();
        points.add(progress.startedAt());
        points.addAll(frames);
        points.add(progress.answerAt());
        assertGapsAtMost(points, settings.progressInterval());
        assertEquals(settings.progressInterval(), Duration.between(progress.startedAt(), frames.getFirst()));
    }

    @ParameterizedTest(name = "progress_resets_timeout={0}, token={1} selects {2}")
    @CsvSource({"true, true, PROGRESS", "true, false, BOUNDED", "false, true, BOUNDED", "false, false, BOUNDED"})
    void shouldSelectTheWaitFromTheProfileAndTheToken(boolean progressResetsTimeout, boolean token, String expected) {
        Optional<String> tokenValue = token ? Optional.of("p-1") : Optional.empty();
        var wait = WaitSelection.select(connection(progressResetsTimeout), tokenValue, ModelCall.PM_BUILD,
                WaitSettings.defaults(), clock);

        switch (expected) {
            case "PROGRESS" -> assertEquals(WaitSettings.defaults().progressWaitMax(),
                    assertInstanceOf(ModelWait.Progress.class, wait).window());
            case "BOUNDED" -> assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class, wait).window());
            default -> throw new IllegalArgumentException("no such wait kind: " + expected);
        }
    }

    @Test
    void shouldKeepTheBoundedWindowOfAPmWaitThatQualifiesForProgress() {
        var wait = WaitSelection.select(connection(true), Optional.of("p-1"), ModelCall.PM_WAIT, WaitSettings.defaults(),
                clock);

        var progress = assertInstanceOf(ModelWait.Progress.class, wait);
        assertEquals(BOUNDED, progress.window());
        assertEquals(T0.plus(BOUNDED), progress.answerAt());
        assertEquals(47, progress.progressFrames().size());
        assertGapsAtMost(pointsOf(progress), WaitSettings.defaults().progressInterval());
    }

    @Test
    void shouldTreatABlankTokenAsNoToken() {
        var wait = WaitSelection.select(connection(true), Optional.of(" "), ModelCall.PM_BUILD, WaitSettings.defaults(),
                clock);

        assertInstanceOf(ModelWait.Bounded.class, wait);
    }

    @Test
    void shouldRefuseAConnectionOutsideTheProfileBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(Duration.ofSeconds(14), IDLE, true));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(Duration.ofSeconds(3601), IDLE, true));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(BOUNDED, BOUNDED.plusSeconds(29), false));
        assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class,
                WaitSelection.select(new ConnectionSettings(BOUNDED, BOUNDED.plusSeconds(30), false), Optional.empty(),
                        ModelCall.PM_WAIT, WaitSettings.defaults(), clock)).window());
    }

    @Test
    void shouldRefuseProgressSettingsOutsideTheMachineBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaitSettings(Duration.ofSeconds(59), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitSettings(Duration.ofSeconds(7201), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitSettings(Duration.ofSeconds(1800), Duration.ofSeconds(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitSettings(Duration.ofSeconds(1800), Duration.ofSeconds(61)));
    }

    private static ConnectionSettings connection(boolean progressResetsTimeout) {
        return new ConnectionSettings(BOUNDED, IDLE, progressResetsTimeout);
    }

    private static List<Instant> pointsOf(ModelWait.Progress progress) {
        var points = new ArrayList<Instant>();
        points.add(progress.startedAt());
        points.addAll(progress.progressFrames());
        points.add(progress.answerAt());
        return List.copyOf(points);
    }

    private static void assertGapsAtMost(List<Instant> points, Duration interval) {
        for (var index = 1; index < points.size(); index++) {
            var previous = points.get(index - 1);
            var point = points.get(index);
            var gap = Duration.between(previous, point);
            assertFalse(gap.compareTo(interval) > 0,
                    () -> "a gap of " + gap + " before " + point + " exceeds " + interval);
        }
    }
}
