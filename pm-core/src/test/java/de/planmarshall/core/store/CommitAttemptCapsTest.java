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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.CommitAttemptCaps.ChangeSet;
import de.planmarshall.core.store.CommitAttemptCaps.Frame;

/**
 * The two caps of the {@code git.commit-fix} loop (Timeouts &amp; Budgets § Commit Sub-Process Attempt Caps): the value
 * holds one counter per cap, is empty before the first counted event, and answers a spent cap only at the cap.
 */
@EnableGeneratorController
@DisplayName("Commit attempt caps")
class CommitAttemptCapsTest {

    private static String hex(int length) {
        var text = new StringBuilder(length);
        for (var index = 0; index < length; index++) {
            text.append(Character.forDigit(Generators.integers(0, 15).next(), 16));
        }
        return text.toString();
    }

    private static String frameId() {
        return "frame-" + Generators.letterStrings(3, 12).next();
    }

    @Test
    @DisplayName("the empty caps hold neither counter and are not exhausted")
    void emptyHoldsNeitherCounter() {
        var empty = CommitAttemptCaps.empty();

        assertAll(
                () -> assertNull(empty.changeSet(), "the empty caps hold no change set"),
                () -> assertNull(empty.frame(), "the empty caps hold no call frame"),
                () -> assertFalse(empty.changeSetSpent(), "the empty change set cap is not spent"),
                () -> assertFalse(empty.frameSpent(), "the empty frame cap is not spent"),
                () -> assertFalse(empty.exhausted(), "the empty caps are not exhausted"));
    }

    @Test
    @DisplayName("a counter below the cap is open and one at the cap is spent")
    void capDecidesSpent() {
        var below = new CommitAttemptCaps(new ChangeSet(hex(40), hex(64), CommitAttemptCaps.CAP - 1),
                new Frame(frameId(), CommitAttemptCaps.CAP - 1));
        var atCap = new CommitAttemptCaps(new ChangeSet(hex(40), hex(64), CommitAttemptCaps.CAP),
                new Frame(frameId(), CommitAttemptCaps.CAP));

        assertAll(
                () -> assertFalse(below.changeSetSpent(), "two attempts leave the change set open"),
                () -> assertFalse(below.frameSpent(), "two tasks leave the frame open"),
                () -> assertFalse(below.exhausted(), "two of each leave both caps open"),
                () -> assertTrue(atCap.changeSetSpent(), "three attempts spend the change set"),
                () -> assertTrue(atCap.frameSpent(), "three tasks spend the frame"),
                () -> assertTrue(atCap.exhausted(), "a spent cap exhausts the loop"));
    }

    @Test
    @DisplayName("the cap is the compiled three of both counters")
    void capIsThree() {
        assertEquals(3, CommitAttemptCaps.CAP, "the compiled cap is three for both counters");
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("a staged tree that is no 40-digit digest is refused")
        void treeGrammar() {
            var digest = hex(64);
            var shortTree = hex(39);

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> new ChangeSet(shortTree, digest, 1), "a short staged tree is refused");

            assertTrue(refused.getMessage().contains("staged_tree"), "the refusal names the staged tree");
        }

        @Test
        @DisplayName("a message digest that is no 64-digit digest is refused")
        void messageGrammar() {
            var tree = hex(40);
            var shortDigest = hex(63);

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> new ChangeSet(tree, shortDigest, 1), "a short message digest is refused");

            assertTrue(refused.getMessage().contains("message_digest"), "the refusal names the message digest");
        }

        @Test
        @DisplayName("attempts above the cap or below zero are refused")
        void attemptRange() {
            var tree = hex(40);
            var digest = hex(64);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ChangeSet(tree, digest, CommitAttemptCaps.CAP + 1),
                            "an attempt above the cap is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ChangeSet(tree, digest, -1), "a negative attempt is refused"));
        }

        @Test
        @DisplayName("a blank frame id and tasks above the cap are refused")
        void frameValidation() {
            var frame = frameId();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Frame(" ", 1),
                            "a blank frame id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Frame(frame, CommitAttemptCaps.CAP + 1),
                            "tasks above the cap are refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Frame(frame, -1),
                            "negative tasks are refused"));
        }
    }
}
