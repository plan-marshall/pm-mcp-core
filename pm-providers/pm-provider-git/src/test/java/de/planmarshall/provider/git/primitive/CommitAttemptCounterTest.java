/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two caps of the {@code git.commit-fix} loop (Timeouts &amp; Budgets § Commit Sub-Process Attempt Caps; State
 * Catalogue § {@code git.stage-and-commit}; PM-WATCH-DSL-28): the change-set counter resets with the staged tree or
 * the message, the frame counter bounds the loop regardless, and a spent cap stays spent.
 */
@EnableGeneratorController
@DisplayName("Commit attempt counter")
class CommitAttemptCounterTest {

    private static String hex(int length) {
        var text = new StringBuilder(length);
        for (var index = 0; index < length; index++) {
            text.append(Character.forDigit(Generators.integers(0, 15).next(), 16));
        }
        return text.toString();
    }

    private static String tree() {
        return hex(40);
    }

    private static String messageDigest() {
        return hex(64);
    }

    private static String frameId() {
        return "frame-" + Generators.letterStrings(3, 12).next();
    }

    @Nested
    @DisplayName("Change-set cap")
    class ChangeSetCap {

        @Test
        @DisplayName("three failed attempts of one change set spend the change-set cap")
        void threeAttemptsSpendCap() {
            var tree = tree();
            var digest = messageDigest();

            var counter = CommitAttemptCounter.empty().failedCommitAttempt(tree, digest)
                    .failedCommitAttempt(tree, digest).failedCommitAttempt(tree, digest);

            assertAll(
                    () -> assertEquals(3, counter.caps().changeSet().attempts(), "the third attempt is counted"),
                    () -> assertTrue(counter.changeSetCapSpent(), "three attempts spend the change set"),
                    () -> assertTrue(counter.exhausted(), "the spent change set exhausts the loop"));
        }

        @Test
        @DisplayName("a changed staged tree resets the change-set counter to one")
        void changedTreeResets() {
            var digest = messageDigest();

            var counter = CommitAttemptCounter.empty().failedCommitAttempt(tree(), digest)
                    .failedCommitAttempt(tree(), digest).failedCommitAttempt(tree(), digest);

            assertAll(
                    () -> assertEquals(1, counter.caps().changeSet().attempts(), "the new tree starts at one"),
                    () -> assertFalse(counter.changeSetCapSpent(), "a reset change set is not spent"));
        }

        @Test
        @DisplayName("a changed message resets the change-set counter to one")
        void changedMessageResets() {
            var tree = tree();

            var counter = CommitAttemptCounter.empty().failedCommitAttempt(tree, messageDigest())
                    .failedCommitAttempt(tree, messageDigest()).failedCommitAttempt(tree, messageDigest());

            assertAll(
                    () -> assertEquals(1, counter.caps().changeSet().attempts(), "the new message starts at one"),
                    () -> assertFalse(counter.changeSetCapSpent(), "a reset change set is not spent"));
        }
    }

    @Nested
    @DisplayName("Frame cap")
    class FrameCap {

        @Test
        @DisplayName("three fix tasks of one frame spend the frame cap")
        void threeTasksSpendCap() {
            var frame = frameId();

            var counter = CommitAttemptCounter.empty().fixTask(frame).fixTask(frame).fixTask(frame);

            assertAll(
                    () -> assertEquals(3, counter.caps().frame().tasks(), "the third task is counted"),
                    () -> assertTrue(counter.frameCapSpent(), "three tasks spend the frame"),
                    () -> assertTrue(counter.exhausted(), "the spent frame exhausts the loop"));
        }

        @Test
        @DisplayName("a fix task of another call frame starts a new count at one")
        void changedFrameResets() {
            var counter = CommitAttemptCounter.empty().fixTask(frameId()).fixTask(frameId()).fixTask(frameId());

            assertAll(
                    () -> assertEquals(1, counter.caps().frame().tasks(), "the new frame starts at one"),
                    () -> assertFalse(counter.frameCapSpent(), "a reset frame is not spent"));
        }
    }

    @Nested
    @DisplayName("Hazard PM-WATCH-DSL-28")
    class Hazard {

        @Test
        @DisplayName("a fix that changes the staged tree resets the change set but the frame cap still ends the loop")
        void frameCapBoundsAResetChangeSet() {
            var frame = frameId();

            var counter = CommitAttemptCounter.empty();
            for (var task = 0; task < CommitAttemptCounter.FRAME_CAP; task++) {
                counter = counter.failedCommitAttempt(tree(), messageDigest()).fixTask(frame);
            }

            final var ended = counter;
            assertAll(
                    () -> assertFalse(ended.changeSetCapSpent(), "each new tree resets the change set"),
                    () -> assertTrue(ended.frameCapSpent(), "the third fix task spends the frame"),
                    () -> assertTrue(ended.exhausted(), "the frame cap ends the loop alone"));
        }

        @Test
        @DisplayName("the same change set is counted to its cap without any fix task")
        void changeSetAloneEndsTheLoop() {
            var tree = tree();
            var digest = messageDigest();

            var counter = CommitAttemptCounter.empty();
            for (var attempt = 0; attempt < CommitAttemptCounter.CHANGE_SET_CAP; attempt++) {
                counter = counter.failedCommitAttempt(tree, digest);
            }

            final var ended = counter;
            assertAll(
                    () -> assertTrue(ended.changeSetCapSpent(), "three identical attempts spend the change set"),
                    () -> assertFalse(ended.frameCapSpent(), "no fix task leaves the frame open"));
        }
    }

    @Nested
    @DisplayName("Counter behaviour")
    class Behaviour {

        @Test
        @DisplayName("a spent cap stays spent when another attempt is counted")
        void spentCapStaysSpent() {
            var tree = tree();
            var digest = messageDigest();

            var counter = CommitAttemptCounter.empty().failedCommitAttempt(tree, digest)
                    .failedCommitAttempt(tree, digest).failedCommitAttempt(tree, digest)
                    .failedCommitAttempt(tree, digest);

            assertAll(
                    () -> assertEquals(3, counter.caps().changeSet().attempts(), "the cap is never exceeded"),
                    () -> assertTrue(counter.changeSetCapSpent(), "the cap stays spent"));
        }

        @Test
        @DisplayName("the counter answers the caps it was read from with a copy")
        void capsRoundTrip() {
            var caps = CommitAttemptCounter.empty().fixTask(frameId()).caps();

            assertAll(
                    () -> assertEquals(caps, CommitAttemptCounter.of(caps).caps(), "the caps round-trip"),
                    () -> assertThrows(NullPointerException.class, () -> CommitAttemptCounter.of(null),
                            "an absent value is refused"));
        }

        @Test
        @DisplayName("the two compiled caps are three")
        void capsAreThree() {
            assertAll(
                    () -> assertEquals(3, CommitAttemptCounter.CHANGE_SET_CAP, "the change-set cap is three"),
                    () -> assertEquals(3, CommitAttemptCounter.FRAME_CAP, "the frame cap is three"));
        }
    }
}
