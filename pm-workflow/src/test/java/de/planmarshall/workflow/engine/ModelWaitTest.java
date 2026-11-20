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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * The selection and the windows of the waits a model holds (PM-WF-8, PM-IMPL-4): a bounded wait answers at its
 * window, a progress wait leaves no gap longer than the interval, and the selection follows the profile's
 * {@code progress_resets_timeout} and the request's progress token in all four combinations. The clock is the
 * fixture's manual clock, so no test waits in wall-clock time.
 */
@EnableGeneratorController
@DisplayName("Model wait selection and windows")
class ModelWaitTest {

    private static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");

    private static final Duration BOUNDED = Duration.ofSeconds(240);

    private static final Duration IDLE = Duration.ofSeconds(1800);

    private static final String TOKEN = Generators.letterStrings(1, 8).next();

    private final EngineFixtures.ManualClock clock = new EngineFixtures.ManualClock(T0);

    @Test
    void shouldAnswerABoundedWaitAtItsWindow() {
        var wait = WaitSelection.select(connection(false), null, ModelCall.PM_WAIT, WaitSettings.defaults(),
                clock);

        var bounded = assertInstanceOf(ModelWait.Bounded.class, wait, "the selection yields a bounded wait");
        assertEquals(T0, bounded.startedAt(), "the bounded wait starts at the clock now");
        assertEquals(BOUNDED, bounded.window(), "the bounded wait uses the connection window");
        assertEquals(T0.plus(BOUNDED), bounded.answerAt(), "the bounded wait answers at the window end");
        assertFalse(bounded.answersAt(bounded.answerAt().minusMillis(1)), "one millisecond before is not enough");
        assertTrue(bounded.answersAt(bounded.answerAt()), "the answer instant is inside the window");
        assertTrue(bounded.progressFrames().isEmpty(), "a bounded wait emits no progress frame");
    }

    @Test
    void shouldEmitProgressFramesWithoutAGapLongerThanTheInterval() {
        var settings = WaitSettings.defaults();
        var wait = WaitSelection.select(connection(true), TOKEN, ModelCall.PM_BUILD, settings, clock);

        var progress = assertInstanceOf(ModelWait.Progress.class, wait, "the selection yields a progress wait");
        assertEquals(T0.plus(settings.progressWaitMax()), progress.answerAt(),
                "the progress wait answers at its maximum");
        var frames = progress.progressFrames();
        assertFalse(frames.isEmpty(), "a progress wait emits frames");
        var points = new ArrayList<Instant>();
        points.add(progress.startedAt());
        points.addAll(frames);
        points.add(progress.answerAt());
        assertGapsAtMost(points, settings.progressInterval());
        assertEquals(settings.progressInterval(), Duration.between(progress.startedAt(), frames.getFirst()),
                "the first frame comes after one interval");
    }

    @ParameterizedTest(name = "progress_resets_timeout={0}, token={1} selects {2}")
    @CsvSource({"true, true, PROGRESS", "true, false, BOUNDED", "false, true, BOUNDED", "false, false, BOUNDED"})
    void shouldSelectTheWaitFromTheProfileAndTheToken(boolean progressResetsTimeout, boolean token, String expected) {
        var tokenValue = token ? "p-1" : null;
        var wait = WaitSelection.select(connection(progressResetsTimeout), tokenValue, ModelCall.PM_BUILD,
                WaitSettings.defaults(), clock);

        switch (expected) {
            case "PROGRESS" -> assertEquals(WaitSettings.defaults().progressWaitMax(),
                    assertInstanceOf(ModelWait.Progress.class, wait, "the wait is a progress wait").window(),
                    "a token selects the progress wait");
            case "BOUNDED" -> assertEquals(BOUNDED,
                    assertInstanceOf(ModelWait.Bounded.class, wait, "the wait is a bounded wait").window(),
                    "no token selects the bounded wait");
            default -> throw new IllegalArgumentException("no such wait kind: " + expected);
        }
    }

    @Test
    void shouldKeepTheBoundedWindowOfAPmWaitThatQualifiesForProgress() {
        var settings = WaitSettings.defaults();
        var wait = WaitSelection.select(connection(true), TOKEN, ModelCall.PM_WAIT, settings, clock);

        var progress = assertInstanceOf(ModelWait.Progress.class, wait, "a pm wait qualifies for progress");
        assertEquals(BOUNDED, progress.window(), "a pm wait keeps the bounded window");
        assertEquals(T0.plus(BOUNDED), progress.answerAt(), "the pm wait answers at the window end");
        assertEquals(BOUNDED.dividedBy(settings.progressInterval()) - 1, progress.progressFrames().size(),
                "the frames split the window by interval");
        assertGapsAtMost(pointsOf(progress), settings.progressInterval());
    }

    @Test
    void shouldSelectTheSameWaitsForAPmWaitInAllFourCombinations() {
        var settings = WaitSettings.defaults();
        var progress = assertInstanceOf(ModelWait.Progress.class,
                WaitSelection.select(connection(true), TOKEN, ModelCall.PM_WAIT, settings, clock),
                "a pm wait qualifies for progress");
        assertEquals(BOUNDED, progress.window(), "a pm wait keeps the bounded window");
        assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class,
                        WaitSelection.select(connection(true), null, ModelCall.PM_WAIT, settings, clock),
                        "the wait without a token is bounded").window(),
                "without a token the pm wait is bounded");
        assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class,
                        WaitSelection.select(connection(false), TOKEN, ModelCall.PM_WAIT, settings, clock),
                        "the wait without a reset is bounded").window(),
                "without a reset the pm wait is bounded");
        assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class,
                        WaitSelection.select(connection(false), null, ModelCall.PM_WAIT, settings, clock),
                        "the wait without either is bounded").window(),
                "without token or reset it is bounded");
    }

    @Test
    void shouldHalveTheIntervalOfAWaitWhenItsWindowCannotCarryIt() {
        var shortConnection = new ConnectionSettings(Duration.ofSeconds(15), IDLE, true);
        var longInterval = new WaitSettings(Duration.ofSeconds(1800), Duration.ofSeconds(60));

        var pmWait = assertInstanceOf(ModelWait.Progress.class,
                WaitSelection.select(shortConnection, TOKEN, ModelCall.PM_WAIT, longInterval, clock),
                "a short window still allows progress");
        assertEquals(Duration.ofSeconds(15), pmWait.window(), "the progress wait takes the short window");
        assertEquals(Duration.ofMillis(7_500), pmWait.interval(), "the interval is halved to fit the window");
        assertFalse(pmWait.progressFrames().isEmpty(), "the halved interval still emits frames");
        assertGapsAtMost(pointsOf(pmWait), pmWait.interval());

        var pmBuild = assertInstanceOf(ModelWait.Progress.class, WaitSelection.select(connection(true),
                        TOKEN, ModelCall.PM_BUILD,
                        new WaitSettings(Duration.ofSeconds(60), Duration.ofSeconds(60)), clock),
                "a short pm build wait carries progress");
        assertEquals(Duration.ofSeconds(60), pmBuild.window(), "the pm build wait keeps its window");
        assertEquals(Duration.ofSeconds(30), pmBuild.interval(), "the interval shrinks to fit the window");
        assertFalse(pmBuild.progressFrames().isEmpty(), "the pm build wait emits frames");
        assertGapsAtMost(pointsOf(pmBuild), pmBuild.interval());
    }

    @Test
    void shouldTreatABlankTokenAsNoToken() {
        var wait = WaitSelection.select(connection(true), " ", ModelCall.PM_BUILD, WaitSettings.defaults(), clock);

        assertInstanceOf(ModelWait.Bounded.class, wait, "a blank token selects the bounded wait");
    }

    @Test
    void shouldTreatAnAbsentTokenAsNoToken() {
        var wait = WaitSelection.select(connection(true), null, ModelCall.PM_BUILD, WaitSettings.defaults(), clock);

        assertInstanceOf(ModelWait.Bounded.class, wait, "an absent token selects the bounded wait");
    }

    @Test
    void shouldRefuseAConnectionOutsideTheProfileBounds() {
        var tooShort = Duration.ofSeconds(14);
        var tooLong = Duration.ofSeconds(3601);
        var marginTooSmall = BOUNDED.plusSeconds(29);
        var marginExact = BOUNDED.plusSeconds(30);
        var shortest = Duration.ofSeconds(15);
        var longest = Duration.ofSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings(tooShort, IDLE, true),
                "a window below the shortest bound is refused");
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings(tooLong, IDLE, true),
                "a window above the longest bound is refused");
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(BOUNDED, marginTooSmall, false),
                "an idle margin below the minimum is refused");
        assertEquals(BOUNDED, assertInstanceOf(ModelWait.Bounded.class,
                        WaitSelection.select(new ConnectionSettings(BOUNDED, marginExact, false), null,
                                ModelCall.PM_WAIT, WaitSettings.defaults(), clock),
                        "an exact margin selects a bounded wait").window(),
                "an exact margin keeps the bounded window");
        assertDoesNotThrow(() -> new ConnectionSettings(shortest, IDLE, true),
                "the shortest window is accepted");
        assertDoesNotThrow(() -> new ConnectionSettings(longest, IDLE, true),
                "the longest window is accepted");
    }

    @Test
    void shouldRefuseProgressSettingsOutsideTheMachineBounds() {
        var tooSmall = Duration.ofSeconds(59);
        var tooLarge = Duration.ofSeconds(7201);
        var standard = Duration.ofSeconds(1800);
        var interval = Duration.ofSeconds(5);
        var subSecond = Duration.ofNanos(999_999_999);
        var tooLongInterval = Duration.ofSeconds(61);
        var smallest = Duration.ofSeconds(60);
        var shortestInterval = Duration.ofSeconds(1);
        var largest = Duration.ofSeconds(7200);
        var longestInterval = Duration.ofSeconds(60);
        assertThrows(IllegalArgumentException.class, () -> new WaitSettings(tooSmall, interval),
                "a window below the minimum is refused");
        assertThrows(IllegalArgumentException.class, () -> new WaitSettings(tooLarge, interval),
                "a window above the maximum is refused");
        assertThrows(IllegalArgumentException.class, () -> new WaitSettings(standard, subSecond),
                "an interval below one second is refused");
        assertThrows(IllegalArgumentException.class, () -> new WaitSettings(standard, tooLongInterval),
                "an interval above the maximum is refused");
        assertDoesNotThrow(() -> new WaitSettings(smallest, shortestInterval),
                "the smallest settings are accepted");
        assertDoesNotThrow(() -> new WaitSettings(largest, longestInterval),
                "the largest settings are accepted");
    }

    @Test
    void shouldRefuseAProgressWaitWhoseIntervalItsWindowCannotCarry() {
        var window = Duration.ofSeconds(60);
        var subSecond = Duration.ofNanos(1);
        var equal = Duration.ofSeconds(60);
        var shorter = Duration.ofSeconds(59);
        assertThrows(IllegalArgumentException.class,
                () -> new ModelWait.Progress(ModelCall.PM_BUILD, T0, window, subSecond),
                "an interval below one second is refused");
        assertThrows(IllegalArgumentException.class,
                () -> new ModelWait.Progress(ModelCall.PM_BUILD, T0, window, equal),
                "an interval equal to the window is refused");
        assertDoesNotThrow(() -> new ModelWait.Progress(ModelCall.PM_BUILD, T0, window, shorter),
                "an interval below the window is accepted");
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
            assertFalse(gap.compareTo(interval) > 0, "no gap between two points exceeds the interval");
        }
    }
}
