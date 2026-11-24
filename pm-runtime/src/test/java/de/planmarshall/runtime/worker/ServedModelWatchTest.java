/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ServedModels;

/**
 * The per-attempt watch of the model that answered a task's turns (harness-profiles/01, The Model That Answered): a
 * change is recorded once, the launch model and its alias write nothing, and the models seen are the row's
 * {@code served_models} in first-appearance order.
 */
@EnableGeneratorController
@DisplayName("Served model watch")
class ServedModelWatchTest {

    private static final java.util.concurrent.atomic.AtomicInteger SEQUENCE =
            new java.util.concurrent.atomic.AtomicInteger();

    /** A fresh identifier per call: two draws never collide, so a comparison test cannot flake. */
    private static String identifier() {
        return Generators.letterStrings(3, 12).next() + "-" + SEQUENCE.incrementAndGet();
    }

    private static ServedModelWatch.Difference difference(String launch, String served) {
        return new ServedModelWatch.Difference(launch, served);
    }

    @Test
    @DisplayName("the launch model on every turn writes nothing (the matched control)")
    void launchModelControl() {
        var launch = identifier();
        var watch = new ServedModelWatch(launch);

        assertAll(
                () -> assertTrue(watch.see(launch).isEmpty(), "the launch model writes no entry"),
                () -> assertTrue(watch.see(launch).isEmpty(), "a second launch-model turn writes none"),
                () -> assertEquals(ServedModels.of(List.of(launch)), watch.served(),
                        "the row names the launch model"));
    }

    @Test
    @DisplayName("a different model writes one entry and its second turn writes none")
    void oneEntryPerChange() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch);

        assertAll(
                () -> assertEquals(Optional.of(difference(launch, other)), watch.see(other),
                        "the change writes its entry"),
                () -> assertTrue(watch.see(other).isEmpty(), "the same model again writes none"));
    }

    @Test
    @DisplayName("two different models in a row write two entries")
    void twoChanges() {
        var launch = identifier();
        var first = identifier();
        var second = identifier();
        var watch = new ServedModelWatch(launch);

        var entries = java.util.stream.Stream.of(watch.see(first), watch.see(second)).flatMap(Optional::stream)
                .toList();

        assertEquals(List.of(difference(launch, first), difference(launch, second)), entries,
                "each change writes its own entry");
    }

    @Test
    @DisplayName("a switch, a return and a second switch write two entries and name every model in order")
    void returnAndSwitch() {
        var launch = identifier();
        var first = identifier();
        var second = identifier();
        var watch = new ServedModelWatch(launch);

        var entries = java.util.stream.Stream.of(watch.see(first), watch.see(launch), watch.see(second))
                .flatMap(Optional::stream).toList();

        assertAll(
                () -> assertEquals(List.of(difference(launch, first), difference(launch, second)), entries,
                        "the return to the launch model writes nothing"),
                () -> assertEquals(ServedModels.of(List.of(first, launch, second)), watch.served(),
                        "the models are named in first-appearance order"));
    }

    @Test
    @DisplayName("a launch alias and the full identifier it stands for are one model")
    void alias() {
        var launch = identifier();
        var watch = new ServedModelWatch(launch);

        assertAll(
                () -> assertTrue(watch.see(launch + "-20250514").isEmpty(),
                        "an eight-digit snapshot suffix is the launch model"),
                () -> assertTrue(watch.see(launch + "-2025-05-14").isEmpty(),
                        "an ISO-date snapshot suffix is the launch model"));
    }

    @Test
    @DisplayName("a switch from one release to another is seen")
    void releaseSwitch() {
        var launch = identifier() + "-20250514";
        var other = identifier() + "-20250601";
        var watch = new ServedModelWatch(launch);

        assertEquals(Optional.of(difference(launch, other)), watch.see(other),
                "another release of another model is a change");
    }

    @Test
    @DisplayName("a harness that reports no model is unmeasured and writes nothing")
    void unmeasured() {
        var watch = new ServedModelWatch(identifier());

        assertAll(
                () -> assertTrue(watch.see(null).isEmpty(), "a null model writes no entry"),
                () -> assertTrue(watch.see("").isEmpty(), "an empty model writes no entry"),
                () -> assertTrue(watch.served().isUnmeasured(), "a row without a reported model is unmeasured"));
    }

    @Test
    @DisplayName("the comparison is a seam the caller may replace")
    void comparisonSeam() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch, (first, second) -> true);

        assertTrue(watch.see(other).isEmpty(), "the supplied comparison decides what is one model");
    }

    @Test
    @DisplayName("restarting the watch forgets the previous model and the models seen")
    void restart() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch);
        watch.see(other);

        watch.restart();

        assertAll(
                () -> assertEquals(Optional.of(difference(launch, other)), watch.see(other),
                        "a new attempt records the fallback again"),
                () -> assertEquals(ServedModels.of(List.of(other)), watch.served(),
                        "a new attempt starts its own list"));
    }

    @Test
    @DisplayName("a watch that records nothing writes no entry and names no model")
    void recordingNothing() {
        var watch = ServedModelWatch.recordingNothing();

        assertAll(
                () -> assertTrue(watch.see(identifier()).isEmpty(), "a silent watch writes no entry"),
                () -> assertTrue(watch.served().isUnmeasured(), "a silent watch names no model"));
    }
}
