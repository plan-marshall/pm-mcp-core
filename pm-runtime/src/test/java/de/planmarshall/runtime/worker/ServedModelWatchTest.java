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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
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

    private static final AtomicInteger SEQUENCE =
            new AtomicInteger();

    /** A fresh identifier per call: two draws never collide, so a comparison test cannot flake. */
    private static String identifier() {
        return Generators.letterStrings(3, 12).next() + "-" + SEQUENCE.incrementAndGet();
    }

    private static ServedModelWatch.Difference difference(String launch, String served) {
        return new ServedModelWatch.Difference(launch, served);
    }

    /** Feeds one line the caller has written: the decision is committed as the pump commits it. */
    private static Optional<ServedModelWatch.Difference> written(ServedModelWatch watch, @Nullable String model) {
        var change = watch.see(model);
        watch.recorded();
        return change;
    }

    @Test
    @DisplayName("the launch model on every turn writes nothing (the matched control)")
    void launchModelControl() {
        var launch = identifier();
        var watch = new ServedModelWatch(launch);

        assertAll(
                () -> assertTrue(written(watch, launch).isEmpty(), "the launch model writes no entry"),
                () -> assertTrue(written(watch, launch).isEmpty(), "a second launch-model turn writes none"),
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
                () -> assertEquals(Optional.of(difference(launch, other)), written(watch, other),
                        "the change writes its entry"),
                () -> assertTrue(written(watch, other).isEmpty(), "the same model again writes none"));
    }

    @Test
    @DisplayName("two different models in a row write two entries")
    void twoChanges() {
        var launch = identifier();
        var first = identifier();
        var second = identifier();
        var watch = new ServedModelWatch(launch);

        var entries = Stream.of(written(watch, first), written(watch, second))
                .flatMap(Optional::stream).toList();

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

        var entries = Stream.of(written(watch, first), written(watch, launch), written(watch, second))
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
        var eightDigits = new ServedModelWatch(launch);
        var isoDate = new ServedModelWatch(launch);

        assertAll(
                () -> assertTrue(written(eightDigits, launch + "-20250514").isEmpty(),
                        "an eight-digit snapshot suffix is the launch model"),
                () -> assertTrue(written(isoDate, launch + "-2025-05-14").isEmpty(),
                        "an ISO-date snapshot suffix is the launch model"));
    }

    @Test
    @DisplayName("a switch from one release of the launch alias to another is seen")
    void aliasReleaseSwitch() {
        var base = identifier();
        var first = base + "-20250514";
        var second = base + "-20250929";
        var watch = new ServedModelWatch(base);

        var entries = Stream.of(written(watch, first), written(watch, second))
                .flatMap(Optional::stream).toList();

        assertAll(
                () -> assertEquals(List.of(difference(base, second)), entries,
                        "the second release writes the one entry"),
                () -> assertEquals(ServedModels.of(List.of(first, second)), watch.served(),
                        "both reported releases are named"));
    }

    @Test
    @DisplayName("an alias report does not overwrite the concrete release it stands for")
    void aliasAfterRelease() {
        var base = identifier();
        var release = base + "-20250514";
        var laterRelease = base + "-20250929";
        var watch = new ServedModelWatch(release);

        var entries = Stream.of(written(watch, base), written(watch, laterRelease))
                .flatMap(Optional::stream).toList();

        assertEquals(List.of(difference(release, laterRelease)), entries,
                "the later release is a change after the alias");
    }

    @Test
    @DisplayName("a change stays pending until the caller records it")
    void changeStaysPendingUntilRecorded() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch);

        var first = watch.see(other);
        var retry = watch.see(other);
        watch.recorded();

        assertAll(
                () -> assertEquals(Optional.of(difference(launch, other)), first, "the change is offered once"),
                () -> assertEquals(Optional.of(difference(launch, other)), retry,
                        "an unwritten change is offered again"),
                () -> assertTrue(watch.see(other).isEmpty(), "a recorded change is not offered again"));
    }

    @Test
    @DisplayName("the two spellings of one release are one model in either order")
    void symmetricSpellings() {
        var launch = identifier();
        var release = identifier() + "-20260101";
        var shortForm = release.substring(0, release.length() - "-20260101".length());
        var longFirst = new ServedModelWatch(launch);
        var shortFirst = new ServedModelWatch(launch);

        assertAll(
                () -> {
                    written(longFirst, release);
                    assertTrue(written(longFirst, shortForm).isEmpty(), "the short spelling writes no second entry");
                },
                () -> {
                    written(shortFirst, shortForm);
                    assertTrue(written(shortFirst, release).isEmpty(), "the long spelling writes no second entry");
                });
    }

    @Test
    @DisplayName("a release switch from one release to another is seen")
    void releaseSwitch() {
        var base = identifier();
        var launch = base + "-20250514";
        var other = base + "-20250601";
        var watch = new ServedModelWatch(launch);

        assertEquals(Optional.of(difference(launch, other)), written(watch, other),
                "another release of the same model is a change");
    }

    @Test
    @DisplayName("a harness that reports no model is unmeasured and writes nothing")
    void unmeasured() {
        var watch = new ServedModelWatch(identifier());

        assertAll(
                () -> assertTrue(written(watch, null).isEmpty(), "a null model writes no entry"),
                () -> assertTrue(written(watch, "").isEmpty(), "an empty model writes no entry"),
                () -> assertTrue(watch.served().isUnmeasured(), "a row without a reported model is unmeasured"));
    }

    @Test
    @DisplayName("the comparison is a seam the caller may replace")
    void comparisonSeam() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch, (first, second) -> true);

        assertTrue(written(watch, other).isEmpty(), "the supplied comparison decides what is one model");
    }

    @Test
    @DisplayName("a model longer than the column admits is not reported")
    void overLongModel() {
        var watch = new ServedModelWatch(identifier());
        var tooLong = Generators.letterStrings(129, 129).next();

        assertAll(
                () -> assertTrue(written(watch, tooLong).isEmpty(), "an over-long model writes no entry"),
                () -> assertTrue(watch.served().isUnmeasured(), "an over-long model enters no list"));
    }

    @Test
    @DisplayName("restarting the watch forgets the previous model and the models seen")
    void restart() {
        var launch = identifier();
        var other = identifier();
        var watch = new ServedModelWatch(launch);
        written(watch, other);

        watch.restart();

        assertAll(
                () -> assertEquals(Optional.of(difference(launch, other)), written(watch, other),
                        "a new attempt records the fallback again"),
                () -> assertEquals(ServedModels.of(List.of(other)), watch.served(),
                        "a new attempt starts its own list"));
    }

    @Test
    @DisplayName("a watch that records nothing writes no entry and names no model")
    void recordingNothing() {
        var watch = ServedModelWatch.recordingNothing();

        assertAll(
                () -> assertTrue(written(watch, identifier()).isEmpty(), "a silent watch writes no entry"),
                () -> assertTrue(watch.served().isUnmeasured(), "a silent watch names no model"));
    }
}
